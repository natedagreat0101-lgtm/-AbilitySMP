package me.abilitysmp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The two new Halloween bosses, started with /boss fight Mimic and /boss fight Ghost.
 * Both use the same separate-arena + boss party + Fight Totem rules as the Headless Horseman:
 * every fight gets its OWN arena copy, a friend in your boss party joins you, nobody can die -
 * a popped Fight Totem knocks that player out of the fight instead.
 *
 * MIMIC (the chest monster from the picture: a huge chest head, sharp teeth, glowing red cracks
 * and a long pink tongue). In vanilla he is an invisible giant zombie wearing a chest for a head
 * and dark red leather, glowing red. Phases by health:
 *   100-75%  Tongue: lashes his tongue out, drags you to him and bites you.
 *   75-50%   Chomper: his bite does 4 hearts and poisons you, and he weakens you for 5 seconds.
 *   50-25%   Mimicer: spawns a copy of you, wearing your gear, that hunts you.
 *   25-0%    ULTRA MIMIC: spawns 2 GIANT copies of you (6 hearts a hit, but any weapon kills
 *            them in 2 hits) and uses every attack at once.
 * Reward: 100 Halloween Coins, 0 Unbound Coins.
 *
 * GHOST (the purple ghost from the picture: see-through, glowing purple eyes). In vanilla he is a
 * big glowing Vex. He is an easy boss with 20 hearts. He is visible for 5 seconds, then turns
 * invisible for 3 seconds, sneaks behind you and hits you, then appears again - over and over.
 * Reward: 10 Halloween Coins, 5 Unbound Coins.
 */
final class HallowBosses implements Listener {

    enum Kind { MIMIC, GHOST }

    private static final class Fight {
        final Kind kind;
        final List<Player> players = new ArrayList<>();
        final Map<UUID, Location> backs = new HashMap<>();
        final Set<UUID> out = new HashSet<>();
        final List<LivingEntity> minions = new ArrayList<>();
        final Map<UUID, Integer> giantHits = new HashMap<>();
        Location center;
        int slot;
        LivingEntity boss;
        BossBar bar;
        int phase = 1;
        long tick;
        boolean ending;
        boolean replicaSpawned;
        boolean giantsSpawned;
        boolean ghostInvisible;
        int ghostCycle;

        Fight(Kind kind) {
            this.kind = kind;
        }

        List<Player> active() {
            List<Player> list = new ArrayList<>();
            for (Player p : players) if (!out.contains(p.getUniqueId()) && p.isOnline()) list.add(p);
            return list;
        }

        Player nearestTo(LivingEntity e) {
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player p : active()) {
                if (p.getWorld() != e.getWorld()) continue;
                double d = p.getLocation().distanceSquared(e.getLocation());
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            return best;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;
    private final Random random = new Random();
    private final Map<UUID, Fight> fights = new HashMap<>(); // every participant -> their fight
    private final Map<Kind, Set<Integer>> usedSlots = new HashMap<>();

    HallowBosses(AbilitySMP plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "hallow_boss");
        this.minionKey = new NamespacedKey(plugin, "mimic_minion");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 1L);
    }

    boolean isBusy(Player p) {
        return fights.containsKey(p.getUniqueId());
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private double cfgD(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private Team glowTeam(String name, NamedTextColor color) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(name);
        if (t == null) t = sb.registerNewTeam(name);
        t.color(color);
        return t;
    }

    // =====================================================================
    // Command
    // =====================================================================

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        String who = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase() : "";
        if (who.contains("mimic")) {
            start(p, Kind.MIMIC);
            return;
        }
        if (who.contains("ghost")) {
            start(p, Kind.GHOST);
            return;
        }
        p.sendMessage(Component.text("Usage: /boss fight Mimic   or   /boss fight Ghost", NamedTextColor.GRAY));
    }

    /** /boss leave while in one of these fights: that player is knocked out. */
    boolean leave(Player p) {
        Fight f = fights.get(p.getUniqueId());
        if (f == null) return false;
        playerOut(f, p, false);
        return true;
    }

    // =====================================================================
    // Arena: every fight gets its own copy, so nobody ever has to wait
    // =====================================================================

    private Location centerFor(Fight f) {
        String base = f.kind == Kind.MIMIC ? "mimic" : "ghost-boss";
        World w = Bukkit.getWorlds().get(0);
        int cx = cfg(base + ".arena-x", 0) + f.slot * cfg("boss.arena-slot-spacing", 100);
        int y = cfg(base + ".arena-y", 200);
        int cz = cfg(base + ".arena-z", f.kind == Kind.MIMIC ? -8000 : 8000);
        if (!Arenas.built(w, cx, y, cz)) {
            if (f.kind == Kind.MIMIC) {
                Arenas.build(w, cx, y, cz, 20, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, 12);
                for (int i = 0; i < 6; i++) { // treasure chests and redstone torches around his lair
                    double a = Math.PI * 2 * i / 6 + 0.4;
                    int x = cx + (int) Math.round(Math.cos(a) * 16), z = cz + (int) Math.round(Math.sin(a) * 16);
                    w.getBlockAt(x, y + 1, z).setType(Material.CHEST, false);
                    w.getBlockAt(x, y + 2, z).setType(Material.REDSTONE_TORCH, false);
                }
            } else {
                Arenas.build(w, cx, y, cz, 18, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, 12);
                for (int i = 0; i < 6; i++) { // soul lanterns around the ghost's graveyard
                    double a = Math.PI * 2 * i / 6;
                    int x = cx + (int) Math.round(Math.cos(a) * 15), z = cz + (int) Math.round(Math.sin(a) * 15);
                    for (int h = 1; h <= 2; h++) w.getBlockAt(x, y + h, z).setType(Material.DEEPSLATE_BRICK_WALL, false);
                    w.getBlockAt(x, y + 3, z).setType(Material.SOUL_LANTERN, false);
                }
            }
        }
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    // =====================================================================
    // Starting a fight
    // =====================================================================

    private void start(Player host, Kind kind) {
        if (!plugin.getConfig().getBoolean("halloween.enabled", true)) {
            host.sendMessage(Component.text("The Halloween content is closed right now.", NamedTextColor.GRAY));
            return;
        }
        if (isBusy(host) || plugin.horseman.isBusy(host) || plugin.duel.isBusy(host)) {
            host.sendMessage(Component.text("Finish your current fight first.", NamedTextColor.RED));
            return;
        }
        if (plugin.ranked.isBusy(host) || plugin.trading.isBusy(host)) {
            host.sendMessage(Component.text("Finish what you are doing first.", NamedTextColor.RED));
            return;
        }
        String cdId = kind == Kind.MIMIC ? "mimic" : "ghost";
        long cd = plugin.bossCooldownLeft(host, cdId);
        if (cd > 0) {
            host.sendMessage(Component.text((kind == Kind.MIMIC ? "The Mimic" : "The Ghost")
                    + " is hiding! You can fight " + (kind == Kind.MIMIC ? "him" : "it") + " again in "
                    + AbilitySMP.spanShort(cd) + ".", NamedTextColor.RED));
            return;
        }
        // your boss party friend comes with you; anyone busy with something else is left behind
        List<Player> fighters = new ArrayList<>();
        fighters.add(host);
        for (Player m : plugin.party.members(host)) {
            if (m.equals(host)) continue;
            if (isBusy(m) || plugin.horseman.isBusy(m) || plugin.duel.isBusy(m)
                    || plugin.ranked.isBusy(m) || plugin.trading.isBusy(m)) {
                host.sendMessage(Component.text(m.getName() + " is busy and can't join this fight.", NamedTextColor.GRAY));
                continue;
            }
            fighters.add(m);
        }
        for (Player p : fighters) plugin.startBossCooldown(p, cdId);

        Fight f = new Fight(kind);
        Set<Integer> slots = usedSlots.computeIfAbsent(kind, k -> new HashSet<>());
        int slot = 0;
        while (slots.contains(slot)) slot++;
        slots.add(slot);
        f.slot = slot;
        f.players.addAll(fighters);
        f.center = centerFor(f);
        for (Player p : fighters) {
            f.backs.put(p.getUniqueId(), p.getLocation().clone());
            fights.put(p.getUniqueId(), f);
        }

        String bossName = kind == Kind.MIMIC ? "The Mimic" : "The Ghost";
        for (Player p : fighters) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0);
            p.setFoodLevel(20);
            p.setFireTicks(0);
            int off = fighters.indexOf(p) * 3;
            p.teleport(new Location(f.center.getWorld(), f.center.getX(), f.center.getY() + 1, f.center.getZ() + 12 + off, 180f, 0f));
            p.setPlayerTime(13000L, false);
            plugin.fightTotem.protect(p, bossName, popped -> playerOut(f, popped, true));
        }
        for (Player p : fighters) {
            p.showTitle(Title.title(
                    Component.text(bossName, kind == Kind.MIMIC ? NamedTextColor.DARK_RED : NamedTextColor.LIGHT_PURPLE),
                    Component.text(kind == Kind.MIMIC ? "a chest should not have teeth..." : "it is already angry...",
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(600))));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!f.ending && !f.active().isEmpty()) spawnBoss(f);
        }, 80L);
    }

    private void spawnBoss(Fight f) {
        World w = f.center.getWorld();
        Location bl = f.center.clone().add(0, 1, -8);
        w.strikeLightningEffect(bl);
        if (f.kind == Kind.MIMIC) spawnMimic(f, bl);
        else spawnGhost(f, bl);

        f.bar = BossBar.bossBar(
                Component.text(f.kind == Kind.MIMIC ? "Mimic" : "Ghost",
                        f.kind == Kind.MIMIC ? NamedTextColor.DARK_RED : NamedTextColor.LIGHT_PURPLE),
                1f, f.kind == Kind.MIMIC ? BossBar.Color.RED : BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        for (Player p : f.active()) p.showBossBar(f.bar);
        w.playSound(bl, Sound.ENTITY_WITHER_SPAWN, 1f, f.kind == Kind.MIMIC ? 0.5f : 0.9f);
    }

    private void spawnMimic(Fight f, Location bl) {
        double hp = cfgD("mimic.health", 300);
        Zombie z = bl.getWorld().spawn(bl, Zombie.class, boss -> {
            boss.setBaby(false);
            boss.setShouldBurnInDay(false);
            boss.setCanBreakDoors(false);
            boss.setRemoveWhenFarAway(false);
            boss.setCanPickupItems(false);
            boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, "MIMIC");
            boss.customName(Component.text("Mimic", NamedTextColor.DARK_RED));
            boss.setCustomNameVisible(true);
            // invisible body: only the chest head, dark red armor and glow show - a living chest
            boss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            boss.setGlowing(true);
            EntityEquipment eq = boss.getEquipment();
            eq.setHelmet(new ItemStack(Material.CHEST)); // the chest lid / head
            eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.fromRGB(120, 40, 30)));
            eq.setLeggings(leather(Material.LEATHER_LEGGINGS, Color.fromRGB(70, 30, 20)));
            eq.setBoots(leather(Material.LEATHER_BOOTS, Color.fromRGB(50, 20, 15)));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            attr(boss, Attribute.MAX_HEALTH, hp);
            boss.setHealth(hp);
            attr(boss, Attribute.SCALE, 2.4); // giant, like the picture
            attr(boss, Attribute.ATTACK_DAMAGE, cfgD("mimic.bite-hearts", 3) * 2.0);
            attr(boss, Attribute.MOVEMENT_SPEED, 0.24);
            attr(boss, Attribute.KNOCKBACK_RESISTANCE, 0.9);
            attr(boss, Attribute.FOLLOW_RANGE, 60);
        });
        glowTeam("mimic_glow", NamedTextColor.RED).addEntry(z.getUniqueId().toString());
        f.boss = z;
        Player t = f.nearestTo(z);
        if (t != null) z.setTarget(t);
    }

    private void spawnGhost(Fight f, Location bl) {
        double hp = cfgD("ghost-boss.health", 40); // 20 hearts: an easy boss
        Vex v = bl.getWorld().spawn(bl.clone().add(0, 2, 0), Vex.class, boss -> {
            boss.setRemoveWhenFarAway(false);
            boss.setCanPickupItems(false);
            boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, "GHOST");
            boss.customName(Component.text("Ghost", NamedTextColor.LIGHT_PURPLE));
            boss.setCustomNameVisible(true);
            boss.setGlowing(true);
            attr(boss, Attribute.MAX_HEALTH, hp);
            boss.setHealth(hp);
            attr(boss, Attribute.SCALE, 2.2); // a big ghost, like the picture
            attr(boss, Attribute.ATTACK_DAMAGE, cfgD("ghost-boss.damage-hearts", 2.5) * 2.0);
            attr(boss, Attribute.FOLLOW_RANGE, 60);
        });
        glowTeam("ghost_glow", NamedTextColor.LIGHT_PURPLE).addEntry(v.getUniqueId().toString());
        f.boss = v;
        Player t = f.nearestTo(v);
        if (t != null) v.setTarget(t);
    }

    private static ItemStack leather(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(c);
        it.setItemMeta(lm);
        return it;
    }

    // =====================================================================
    // The fight loop
    // =====================================================================

    private void tickAll() {
        for (Fight f : new HashSet<>(new ArrayList<>(fights.values()))) tick(f);
    }

    private void tick(Fight f) {
        if (f.ending) return;
        if (f.active().isEmpty()) {
            end(f, false, false);
            return;
        }
        LivingEntity b = f.boss;
        if (b == null || !b.isValid()) return;
        f.tick++;

        String base = f.kind == Kind.MIMIC ? "mimic" : "ghost-boss";
        double max = cfgD(base + ".health", f.kind == Kind.MIMIC ? 300 : 40);
        double frac = Math.max(0.0, Math.min(1.0, b.getHealth() / max));
        if (f.bar != null) f.bar.progress((float) frac);

        // keep the boss inside its own arena
        if (b.getLocation().distanceSquared(f.center) > 30 * 30) {
            b.teleport(f.center.clone().add(0, 1, 0));
        }
        Player target = f.nearestTo(b);
        if (target == null) return;
        if (f.tick % 20 == 0 && b instanceof Mob mob) mob.setTarget(target);

        if (f.kind == Kind.MIMIC) mimicTick(f, target, frac);
        else ghostTick(f, target);
    }

    // ------------------------------------------------------------ Mimic

    private void mimicTick(Fight f, Player target, double frac) {
        LivingEntity b = f.boss;
        int phase = frac > 0.75 ? 1 : frac > 0.50 ? 2 : frac > 0.25 ? 3 : 4;
        if (phase != f.phase) {
            f.phase = phase;
            onMimicPhase(f, phase);
        }
        // red glow cracks and dripping particles, like the picture
        if (f.tick % 6 == 0) {
            b.getWorld().spawnParticle(Particle.DUST, b.getLocation().add(0, b.getHeight() * 0.6, 0), 6, 0.7, 0.7, 0.7, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.6f));
        }
        double dist = b.getLocation().distance(target.getLocation());

        // Phase 1+: the tongue. It lashes out, sticks to you and drags you to his mouth.
        int tongueEvery = f.phase >= 4 ? 70 : 100;
        if (f.tick % tongueEvery == 0 && dist > 3.5 && dist < 19) tonguePull(f, target);

        if (f.phase >= 2) {
            // Chomper: a bite that does 4 hearts and poisons you
            if (f.tick % 45 == 0 && dist < 4.5) chomp(f, target);
            // and every so often he makes you weaker for 5 seconds
            if (f.tick % 170 == 0) weaken(f, target);
        }
    }

    private void onMimicPhase(Fight f, int phase) {
        LivingEntity b = f.boss;
        switch (phase) {
            case 2 -> {
                titleAll(f, "CHOMPER!", "His bites do 4 hearts and poison you");
                soundAll(f, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f);
            }
            case 3 -> {
                titleAll(f, "MIMICER!", "He made a copy of you...");
                soundAll(f, Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f);
                if (!f.replicaSpawned) {
                    f.replicaSpawned = true;
                    Player src = f.nearestTo(b);
                    if (src != null) spawnCopy(f, src, false);
                }
            }
            case 4 -> {
                titleAll(f, "ULTRA MIMIC!", "Two GIANT copies of you! Every attack at once!");
                soundAll(f, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f);
                attr(b, Attribute.MOVEMENT_SPEED, 0.30);
                if (!f.giantsSpawned) {
                    f.giantsSpawned = true;
                    Player src = f.nearestTo(b);
                    if (src != null) {
                        spawnCopy(f, src, true);
                        spawnCopy(f, src, true);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** The long pink tongue: a particle rope from his mouth to you, then you get pulled in and bitten. */
    private void tonguePull(Fight f, Player target) {
        LivingEntity b = f.boss;
        Location mouth = b.getLocation().add(0, b.getHeight() * 0.55, 0);
        Location chest = target.getLocation().add(0, 1, 0);
        Vector step = chest.toVector().subtract(mouth.toVector());
        int pts = 18;
        step.multiply(1.0 / pts);
        Location cur = mouth.clone();
        for (int i = 0; i <= pts; i++) {
            cur.getWorld().spawnParticle(Particle.DUST, cur, 2, 0.06, 0.06, 0.06, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 130), 2.0f));
            cur.add(step);
        }
        b.getWorld().playSound(mouth, Sound.ENTITY_FISHING_BOBBER_THROW, 1.2f, 0.6f);
        plugin.bar(target, "The Mimic's tongue grabs you!", NamedTextColor.RED);

        Vector pull = b.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() > 1.0E-6) pull.normalize();
        pull.multiply(1.7).setY(0.35);
        target.setVelocity(pull);

        // once you land in front of him, he bites
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (f.ending || !b.isValid() || !target.isOnline()) return;
            if (b.getLocation().distance(target.getLocation()) < 5) {
                target.damage(cfgD("mimic.tongue-bite-hearts", 2) * 2.0, b);
                b.getWorld().playSound(b.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.2f, 0.7f);
            }
        }, 14L);
    }

    /** Phase 2 bite: 4 hearts of damage plus poison. */
    private void chomp(Fight f, Player target) {
        LivingEntity b = f.boss;
        target.damage(cfgD("mimic.chomp-hearts", 4) * 2.0, b);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, cfg("mimic.poison-seconds", 5) * 20, 0));
        Location l = target.getLocation().add(0, 1, 0);
        l.getWorld().spawnParticle(Particle.CRIT, l, 18, 0.3, 0.3, 0.3, 0.1);
        l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EAT, 1.4f, 0.5f);
        plugin.bar(target, "CHOMP! The Mimic bit you and poisoned you!", NamedTextColor.DARK_RED);
    }

    /** Phase 2 curse: Weakness for 5 seconds. */
    private void weaken(Fight f, Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, cfg("mimic.weaken-seconds", 5) * 20, 0));
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 0.7f);
        plugin.bar(target, "The Mimic makes you feel weaker...", NamedTextColor.DARK_PURPLE);
    }

    /** A copy of the player: their head (or helmet), their armor, their weapon - hunting them. */
    private void spawnCopy(Fight f, Player src, boolean giant) {
        LivingEntity b = f.boss;
        Location loc = b.getLocation().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
        double dmg = giant ? cfgD("mimic.giant-damage-hearts", 6) * 2.0 : 7.0;
        Zombie z = loc.getWorld().spawn(loc, Zombie.class, copy -> {
            copy.setBaby(false);
            copy.setShouldBurnInDay(false);
            copy.setCanBreakDoors(false);
            copy.setRemoveWhenFarAway(false);
            copy.setCanPickupItems(false);
            copy.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, giant ? "giant" : "replica");
            copy.customName(Component.text(giant ? "GIANT " + src.getName() : src.getName() + " (Mimic Copy)",
                    giant ? NamedTextColor.DARK_RED : NamedTextColor.RED));
            copy.setCustomNameVisible(true);
            copy.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            EntityEquipment eq = copy.getEquipment();
            ItemStack helmet = src.getInventory().getHelmet();
            if (helmet != null && helmet.getType() != Material.AIR) eq.setHelmet(helmet.clone());
            else eq.setHelmet(playerHead(src));
            ItemStack chest = src.getInventory().getChestplate();
            if (chest != null) eq.setChestplate(chest.clone());
            ItemStack legs = src.getInventory().getLeggings();
            if (legs != null) eq.setLeggings(legs.clone());
            ItemStack boots = src.getInventory().getBoots();
            if (boots != null) eq.setBoots(boots.clone());
            ItemStack main = src.getInventory().getItemInMainHand();
            if (main != null && main.getType() != Material.AIR) eq.setItemInMainHand(main.clone());
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);
            attr(copy, Attribute.MAX_HEALTH, giant ? 100 : Math.max(20,
                    src.getAttribute(Attribute.MAX_HEALTH) != null ? src.getAttribute(Attribute.MAX_HEALTH).getValue() : 20));
            copy.setHealth(copy.getAttribute(Attribute.MAX_HEALTH).getValue());
            attr(copy, Attribute.SCALE, giant ? 3.0 : 1.0);
            attr(copy, Attribute.ATTACK_DAMAGE, dmg); // giants hit for 6 hearts
            attr(copy, Attribute.MOVEMENT_SPEED, giant ? 0.26 : 0.31);
            attr(copy, Attribute.KNOCKBACK_RESISTANCE, giant ? 0.8 : 0.2);
            attr(copy, Attribute.FOLLOW_RANGE, 60);
        });
        z.setTarget(src);
        f.minions.add(z);
        if (giant) f.giantHits.put(z.getUniqueId(), 0);
        src.sendMessage(Component.text(giant ? "A GIANT copy of you appears! Two hits with any weapon will drop it."
                : "A copy of you appears, wearing your gear!", NamedTextColor.RED));
    }

    private static ItemStack playerHead(Player src) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(src);
        head.setItemMeta(sm);
        return head;
    }

    /** Giants die in exactly 2 hits from any weapon - the first hit never hurts them, the second drops them. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGiantDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        Fight f = fightsFindByMinion(le);
        if (f == null || !f.giantHits.containsKey(le.getUniqueId())) return;
        Entity d = e.getDamager();
        boolean byPlayer = d instanceof Player
                || (d instanceof Projectile pr && pr.getShooter() instanceof Player);
        if (!byPlayer) {
            e.setCancelled(true); // only players can hurt the giant copies
            return;
        }
        int hits = f.giantHits.merge(le.getUniqueId(), 1, Integer::sum);
        if (hits >= 2) {
            e.setDamage(9999.0);
            le.getWorld().spawnParticle(Particle.EXPLOSION, le.getLocation().add(0, 2, 0), 2, 0.5, 0.5, 0.5, 0);
            le.getWorld().playSound(le.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        } else {
            e.setDamage(0.0); // first hit: a crack appears, but it takes one more
            le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 2, 0), 24, 0.6, 1.0, 0.6, 0.1);
            le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.7f);
        }
    }

    private Fight fightsFindByMinion(LivingEntity le) {
        for (Fight f : new HashSet<>(fights.values())) {
            for (LivingEntity m : f.minions) if (m.getUniqueId().equals(le.getUniqueId())) return f;
        }
        return null;
    }

    // ------------------------------------------------------------ Ghost

    /** Visible for 5 seconds, invisible for 3 seconds (he sneaks behind you and hits you), repeat. */
    private void ghostTick(Fight f, Player target) {
        LivingEntity b = f.boss;
        int visibleTicks = cfg("ghost-boss.visible-seconds", 5) * 20;
        int invisibleTicks = cfg("ghost-boss.invisible-seconds", 3) * 20;
        int cycle = visibleTicks + invisibleTicks;
        int pos = (int) (f.tick % cycle);
        boolean shouldBeInvisible = pos >= visibleTicks;

        b.getWorld().spawnParticle(Particle.SOUL, b.getLocation().add(0, 1, 0), f.ghostInvisible ? 1 : 4, 0.4, 0.6, 0.4, 0.02);
        if (f.tick % 10 == 0) {
            b.getWorld().spawnParticle(Particle.DUST, b.getLocation().add(0, 1.2, 0), 4, 0.4, 0.6, 0.4, 0,
                    new Particle.DustOptions(Color.fromRGB(170, 100, 255), 1.4f));
        }

        if (shouldBeInvisible && !f.ghostInvisible) {
            f.ghostInvisible = true;
            b.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisibleTicks + 20, 0, false, false, false));
            b.setGlowing(false); // truly hidden: no outline either
            // sneak up behind the target...
            Location behind = target.getLocation().clone()
                    .subtract(target.getLocation().getDirection().setY(0).normalize().multiply(1.6));
            behind.setY(target.getLocation().getY() + 0.5);
            b.teleport(behind);
            b.getWorld().spawnParticle(Particle.SOUL, behind, 14, 0.3, 0.5, 0.3, 0.03);
            // ...and hit them a moment later
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (f.ending || !b.isValid() || !target.isOnline() || f.out.contains(target.getUniqueId())) return;
                if (b.getWorld() == target.getWorld() && b.getLocation().distance(target.getLocation()) < 5) {
                    target.damage(cfgD("ghost-boss.damage-hearts", 2.5) * 2.0, b);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.7f);
                    plugin.bar(target, "The Ghost strikes from nowhere!", NamedTextColor.LIGHT_PURPLE);
                }
            }, 12L);
        } else if (!shouldBeInvisible && f.ghostInvisible) {
            f.ghostInvisible = false;
            b.removePotionEffect(PotionEffectType.INVISIBILITY);
            b.setGlowing(true);
            b.getWorld().spawnParticle(Particle.SOUL, b.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.05);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1.2f, 0.6f);
            if (b instanceof Mob mob) mob.setTarget(target);
        }
    }

    // =====================================================================
    // Death, victory, defeat
    // =====================================================================

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING)) {
            e.getDrops().clear(); // copies never drop your real gear
            e.setDroppedExp(0);
            Fight f = fightsFindByMinion(le);
            if (f != null) {
                f.minions.remove(le);
                f.giantHits.remove(le.getUniqueId());
            }
            return;
        }
        if (!le.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING)) return;
        for (Fight f : new HashSet<>(fights.values())) {
            if (f.boss != null && f.boss.getUniqueId().equals(le.getUniqueId())) {
                e.getDrops().clear();
                e.setDroppedExp(0);
                victory(f);
                return;
            }
        }
    }

    private void victory(Fight f) {
        if (f.ending) return;
        String base = f.kind == Kind.MIMIC ? "mimic" : "ghost-boss";
        int coins = cfg(base + ".reward-coins", f.kind == Kind.MIMIC ? 100 : 10);
        int points = cfg(base + ".reward-points", f.kind == Kind.MIMIC ? 0 : 5);
        String bossName = f.kind == Kind.MIMIC ? "Mimic" : "Ghost";
        List<String> names = new ArrayList<>();
        for (Player p : f.active()) {
            names.add(p.getName());
            if (points > 0) plugin.unbound.addPoints(p, points, bossName);
            if (coins > 0) plugin.tot.giveCoins(p, coins);
            plugin.crates.rewardForBoss(p, f.kind == Kind.MIMIC ? "mimic" : "ghost");
            Profile.Data d = plugin.profile.of(p);
            if (f.kind == Kind.MIMIC) d.mimicDefeated = true;
            else d.ghostDefeated = true;
            d.bossKills++;
            plugin.profile.save();
            plugin.unbound.addProgress(p, "BOSS_KILL", 1);
            plugin.ranks.evaluate(p);
            p.showTitle(Title.title(Component.text("VICTORY!", NamedTextColor.GREEN),
                    Component.text(rewardText(points, coins), NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(800))));
        }
        Bukkit.broadcast(Component.text(String.join(" and ", names) + " defeated the " + bossName + "!",
                f.kind == Kind.MIMIC ? NamedTextColor.DARK_RED : NamedTextColor.LIGHT_PURPLE));
        end(f, true, false);
    }

    private static String rewardText(int points, int coins) {
        List<String> parts = new ArrayList<>();
        if (points > 0) parts.add("+" + points + " Unbound Coins");
        if (coins > 0) parts.add("+" + coins + " Halloween Coins");
        return parts.isEmpty() ? "No coins - just glory" : String.join(", ", parts);
    }

    /** A player's Fight Totem popped (or they left): they are out; the fight ends if nobody is left. */
    private void playerOut(Fight f, Player p, boolean popped) {
        if (f.ending || f.out.contains(p.getUniqueId())) return;
        f.out.add(p.getUniqueId());
        if (f.bar != null) p.hideBossBar(f.bar);
        plugin.fightTotem.cleanup(p);
        if (p.isOnline()) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0);
            p.setFireTicks(0);
            for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.POISON, PotionEffectType.WEAKNESS,
                    PotionEffectType.WITHER, PotionEffectType.SLOWNESS}) {
                p.removePotionEffect(t);
            }
            Location back = f.backs.get(p.getUniqueId());
            if (back != null) p.teleport(back);
            plugin.admin.applySky(p);
            if (!popped) p.sendMessage(Component.text("You left the boss fight.", NamedTextColor.GRAY));
        }
        for (Player other : f.active()) {
            other.sendMessage(Component.text(p.getName() + " is out of the fight!", NamedTextColor.RED));
        }
        if (f.active().isEmpty()) end(f, false, false);
    }

    private void end(Fight f, boolean win, boolean immediate) {
        if (f.ending) return;
        f.ending = true;
        for (Player p : f.players) fights.remove(p.getUniqueId());
        Set<Integer> slots = usedSlots.get(f.kind);
        if (slots != null) slots.remove(f.slot);
        if (f.bar != null) for (Player p : f.players) p.hideBossBar(f.bar);
        for (LivingEntity m : new ArrayList<>(f.minions)) if (m.isValid()) m.remove();
        if (f.boss != null && f.boss.isValid()) {
            if (f.kind == Kind.MIMIC) glowTeam("mimic_glow", NamedTextColor.RED).removeEntry(f.boss.getUniqueId().toString());
            else glowTeam("ghost_glow", NamedTextColor.LIGHT_PURPLE).removeEntry(f.boss.getUniqueId().toString());
            f.boss.remove();
        }
        List<Player> stillIn = f.active();
        Runnable back = () -> {
            for (Player p : stillIn) {
                plugin.fightTotem.cleanup(p); // win: the Fight Totem disappears here
                if (!p.isOnline()) continue;
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(max != null ? max.getValue() : 20.0);
                p.setFireTicks(0);
                Location b = f.backs.get(p.getUniqueId());
                if (b != null) p.teleport(b);
                plugin.admin.applySky(p);
            }
        };
        if (immediate) back.run();
        else Bukkit.getScheduler().runTaskLater(plugin, back, win ? 100L : 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Fight f = fights.get(e.getPlayer().getUniqueId());
        if (f == null) return;
        Location back = f.backs.get(e.getPlayer().getUniqueId());
        if (back != null) e.getPlayer().teleport(back);
        playerOut(f, e.getPlayer(), false);
    }

    void shutdown() {
        for (Fight f : new HashSet<>(fights.values())) end(f, false, true);
        fights.clear();
    }

    private void titleAll(Fight f, String title, String sub) {
        for (Player p : f.active()) {
            p.showTitle(Title.title(Component.text(title, NamedTextColor.DARK_RED),
                    Component.text(sub, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
        }
    }

    private void soundAll(Fight f, Sound s, float pitch) {
        for (Player p : f.active()) p.playSound(p.getLocation(), s, 1.2f, pitch);
    }
}
