package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * /duel <player>: a friendly one-on-one fight in an arena. Nobody dies and nobody loses items:
 * a killing blow ends the duel instead. The winner earns Unbound Coins, the loser gets nothing.
 */
final class Duel implements Listener {

    private static final class Match {
        final Player a;
        final Player b;
        final Location backA;
        final Location backB;
        boolean fighting;
        boolean over;
        int countdown;
        long start;
        BukkitTask task;

        Match(Player a, Player b, int countdown) {
            this.a = a;
            this.b = b;
            this.backA = a.getLocation().clone();
            this.backB = b.getLocation().clone();
            this.countdown = countdown;
        }

        Player other(Player p) {
            return p.getUniqueId().equals(a.getUniqueId()) ? b : a;
        }

        Location backOf(Player p) {
            return p.getUniqueId().equals(a.getUniqueId()) ? backA : backB;
        }
    }

    private final AbilitySMP plugin;
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, Long> requestAt = new HashMap<>();
    private final Map<String, Long> rewardAt = new HashMap<>();

    Duel(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    boolean isBusy(Player p) {
        return matches.containsKey(p.getUniqueId());
    }

    boolean blocksPowers(Player p) {
        return matches.containsKey(p.getUniqueId()) && !plugin.getConfig().getBoolean("duel.allow-powers", false);
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    // ------------------------------------------------------------ commands

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length == 0) {
            p.sendMessage(Component.text("Usage: /duel <player>   /duel accept   /duel deny", NamedTextColor.GRAY));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "accept" -> accept(p);
            case "deny", "decline" -> deny(p);
            default -> request(p, args[0]);
        }
    }

    private void request(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || t.equals(p)) {
            p.sendMessage(Component.text("Pick another player who is online.", NamedTextColor.RED));
            return;
        }
        if (isBusy(p) || isBusy(t) || plugin.horseman.isBusy(p) || plugin.horseman.isBusy(t)) {
            p.sendMessage(Component.text("One of you is already in a duel or a boss fight.", NamedTextColor.RED));
            return;
        }
        requests.put(t.getUniqueId(), p.getUniqueId());
        requestAt.put(t.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(Component.text("Duel request sent to " + t.getName() + ".", NamedTextColor.GOLD));
        t.sendMessage(Component.text(p.getName() + " challenged you to a friendly duel! ", NamedTextColor.GOLD)
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/duel accept")))
                .append(Component.text("  "))
                .append(Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/duel deny"))));
    }

    private void deny(Player p) {
        UUID rid = requests.remove(p.getUniqueId());
        if (rid == null) {
            p.sendMessage(Component.text("You have no duel request.", NamedTextColor.GRAY));
            return;
        }
        Player r = Bukkit.getPlayer(rid);
        if (r != null) r.sendMessage(Component.text(p.getName() + " declined your duel.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Duel declined.", NamedTextColor.GRAY));
    }

    private void accept(Player p) {
        UUID rid = requests.remove(p.getUniqueId());
        Long at = requestAt.remove(p.getUniqueId());
        Player r = rid == null ? null : Bukkit.getPlayer(rid);
        if (r == null || at == null || System.currentTimeMillis() - at > cfg("duel.request-seconds", 60) * 1000L) {
            p.sendMessage(Component.text("You have no (valid) duel request.", NamedTextColor.GRAY));
            return;
        }
        if (isBusy(p) || isBusy(r) || plugin.horseman.isBusy(p) || plugin.horseman.isBusy(r)) {
            p.sendMessage(Component.text("Someone is already busy.", NamedTextColor.RED));
            return;
        }
        start(r, p);
    }

    // ------------------------------------------------------------ the arena

    private Location center() {
        World w = Bukkit.getWorlds().get(0);
        int cx = cfg("duel.arena-x", 0), y = cfg("duel.arena-y", 220), cz = cfg("duel.arena-z", 4000);
        if (!Arenas.built(w, cx, y, cz)) {
            Arenas.build(w, cx, y, cz, 14, Material.STONE_BRICKS, Material.POLISHED_ANDESITE, 6);
        }
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    private void heal(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
    }

    private void start(Player a, Player b) {
        Location c = center();
        Match m = new Match(a, b, Math.max(1, cfg("duel.countdown-seconds", 5)));
        matches.put(a.getUniqueId(), m);
        matches.put(b.getUniqueId(), m);

        for (Player p : new Player[]{a, b}) {
            for (PotionEffect e : new HashSet<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
            heal(p);
            p.setPlayerTime(6000L, false);
        }
        a.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() - 9, 0f, 0f));
        b.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 9, 180f, 0f));
        a.sendMessage(Component.text("Friendly duel vs " + b.getName() + ". Nobody dies and nobody loses items.",
                NamedTextColor.GOLD));
        b.sendMessage(Component.text("Friendly duel vs " + a.getName() + ". Nobody dies and nobody loses items.",
                NamedTextColor.GOLD));
        m.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(m), 0L, 20L);
    }

    private void tick(Match m) {
        if (m.over) {
            if (m.task != null) m.task.cancel();
            return;
        }
        if (!m.fighting) {
            if (m.countdown > 0) {
                for (Player p : new Player[]{m.a, m.b}) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10, true, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 128, true, false, false));
                    p.showTitle(Title.title(Component.text(String.valueOf(m.countdown), NamedTextColor.GOLD),
                            Component.text("Duel vs " + m.other(p).getName(), NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
                }
                m.countdown--;
            } else {
                for (Player p : new Player[]{m.a, m.b}) {
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                    p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    p.showTitle(Title.title(Component.text("FIGHT!", NamedTextColor.RED), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))));
                }
                m.fighting = true;
                m.start = System.currentTimeMillis();
            }
        } else if (System.currentTimeMillis() - m.start > cfg("duel.max-minutes", 3) * 60_000L) {
            finish(m, null, null);
        }
    }

    // ------------------------------------------------------------ ending

    private void finish(Match m, Player winner, Player loser) {
        if (m.over) return;
        m.over = true;
        if (m.task != null) m.task.cancel();
        for (Player p : new Player[]{m.a, m.b}) {
            if (!p.isOnline()) continue;
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            heal(p);
        }
        if (winner != null && loser != null) {
            reward(m, winner, loser);
        } else {
            for (Player p : new Player[]{m.a, m.b}) {
                if (p.isOnline()) p.sendMessage(Component.text("The duel ended in a draw. No rewards.", NamedTextColor.GRAY));
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : new Player[]{m.a, m.b}) {
                if (p.isOnline()) {
                    p.teleport(m.backOf(p));
                    plugin.admin.applySky(p);
                }
            }
            matches.remove(m.a.getUniqueId());
            matches.remove(m.b.getUniqueId());
        }, 60L);
    }

    private void reward(Match m, Player winner, Player loser) {
        String key = winner.getUniqueId().compareTo(loser.getUniqueId()) < 0
                ? winner.getUniqueId() + ":" + loser.getUniqueId() : loser.getUniqueId() + ":" + winner.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = cfg("duel.reward-cooldown-minutes", 10) * 60_000L;
        boolean tooQuick = now - m.start < 10_000L;
        boolean repeat = now - rewardAt.getOrDefault(key, 0L) < cooldown;
        winner.sendMessage(Component.text("You won the duel against " + loser.getName() + "!", NamedTextColor.GREEN));
        loser.sendMessage(Component.text(winner.getName() + " won the duel. Good fight!", NamedTextColor.GRAY));
        if (tooQuick || repeat) {
            winner.sendMessage(Component.text("No reward: this duel was very short or you fought this player recently.",
                    NamedTextColor.GRAY));
            return;
        }
        rewardAt.put(key, now);
        int points = cfg("duel.win-points", 15);
        plugin.unbound.addPoints(winner, points, "duel win");
        plugin.profile.of(winner).duelWins++;
        plugin.profile.touch();
        plugin.unbound.addProgress(winner, "DUEL_WIN", 1);
        winner.sendMessage(Component.text("+" + points + " Unbound Coins!", NamedTextColor.AQUA));
    }

    // ------------------------------------------------------------ events

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player s) return s;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Match m = matches.get(victim.getUniqueId());
        if (m == null) return;
        if (m.over || !m.fighting) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ed) {
            Player attacker = attackerOf(ed.getDamager());
            if (attacker == null || !attacker.equals(m.other(victim))) {
                e.setCancelled(true); // only your opponent can hurt you
                return;
            }
        }
        if (e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true); // nobody dies in a duel
            finish(m, m.other(victim), victim);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        requests.remove(p.getUniqueId());
        Match m = matches.get(p.getUniqueId());
        if (m == null || m.over) return;
        p.teleport(m.backOf(p)); // so they don't log back in inside the arena
        finish(m, m.other(p), p);
    }

    void shutdown() {
        for (Match m : new HashSet<>(matches.values())) {
            m.over = true;
            if (m.task != null) m.task.cancel();
            for (Player p : new Player[]{m.a, m.b}) {
                if (!p.isOnline()) continue;
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.teleport(m.backOf(p));
                plugin.admin.applySky(p);
            }
        }
        matches.clear();
    }
}
