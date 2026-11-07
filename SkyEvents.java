package me.abilitysmp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * v17 world events: the sky, snow and cave events Nathan asked for.
 *
 * SKY events - AURORA SKY (a green-and-purple night), BLOOD SKY (a red
 * sunset, monsters hit harder) and GOLDEN SKY (warm light that heals).
 * A plugin cannot repaint the real sky (there is no API for the sky's
 * colour), so each sky event drives the closest real levers: the time of
 * day, the weather, a coloured boss bar across the top of the screen,
 * particles in the air and a gameplay effect.
 *
 * SNOW event - BLIZZARD: a storm rolls in, snow particles fall everywhere
 * and players caught outside slowly freeze (vanilla freezing).
 *
 * CAVE events - CRYSTAL SURGE (ores mined deep down pay bonus Unbound
 * Coins) and DEEP RUMBLE (the caves shake, sparkle and whisper).
 *
 * Events start on their own every sky-events.minutes-between-events
 * minutes, and OPs can start one any time with /skyevent.
 */
final class SkyEvents implements Listener {

    enum Kind { AURORA, BLOOD_SKY, GOLDEN_SKY, BLIZZARD, CRYSTAL_SURGE, DEEP_RUMBLE }

    private final AbilitySMP plugin;
    private final Random random = new Random();

    private Kind active;
    private long startedAt;
    private long endsAt;
    private BossBar bar;
    private final Set<UUID> barShown = new HashSet<>();
    private BukkitTask tickTask;
    private BukkitTask autoTask;
    private long lastEventEnd = System.currentTimeMillis();
    private int ticks;

    // the world state each event found, so it can be put back afterwards
    private final Map<String, Long> savedTime = new HashMap<>();
    private final Map<String, Boolean> savedStorm = new HashMap<>();
    private final Map<String, Boolean> savedThunder = new HashMap<>();

    SkyEvents(AbilitySMP plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void startTasks() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maybeAuto, 1200L, 1200L);
    }

    void shutdown() {
        stopSilently();
        if (tickTask != null) tickTask.cancel();
        if (autoTask != null) autoTask.cancel();
    }

    // ------------------------------------------------------------ texts

    private static String titleOf(Kind k) {
        return switch (k) {
            case AURORA -> "AURORA SKY";
            case BLOOD_SKY -> "BLOOD SKY";
            case GOLDEN_SKY -> "GOLDEN SKY";
            case BLIZZARD -> "BLIZZARD";
            case CRYSTAL_SURGE -> "CRYSTAL SURGE";
            case DEEP_RUMBLE -> "DEEP RUMBLE";
        };
    }

    private static String messageOf(Kind k) {
        return switch (k) {
            case AURORA -> "AURORA SKY! The night sky ripples green and purple...";
            case BLOOD_SKY -> "BLOOD SKY! The sky burns red - and monsters hit HARDER!";
            case GOLDEN_SKY -> "GOLDEN SKY! Warm light washes over the world and heals everyone.";
            case BLIZZARD -> "BLIZZARD! A snowstorm covers the world - get under a roof or you will FREEZE!";
            case CRYSTAL_SURGE -> "CRYSTAL SURGE! Ores mined deep underground pay bonus Unbound Coins!";
            case DEEP_RUMBLE -> "DEEP RUMBLE! Something is waking up in the deep caves...";
        };
    }

    private static BossBar.Color colorOf(Kind k) {
        return switch (k) {
            case AURORA -> BossBar.Color.GREEN;
            case BLOOD_SKY -> BossBar.Color.RED;
            case GOLDEN_SKY -> BossBar.Color.YELLOW;
            case BLIZZARD -> BossBar.Color.WHITE;
            case CRYSTAL_SURGE -> BossBar.Color.PURPLE;
            case DEEP_RUMBLE -> BossBar.Color.BLUE;
        };
    }

    private static NamedTextColor textColorOf(Kind k) {
        return switch (k) {
            case AURORA -> NamedTextColor.GREEN;
            case BLOOD_SKY -> NamedTextColor.RED;
            case GOLDEN_SKY -> NamedTextColor.GOLD;
            case BLIZZARD -> NamedTextColor.WHITE;
            case CRYSTAL_SURGE -> NamedTextColor.LIGHT_PURPLE;
            case DEEP_RUMBLE -> NamedTextColor.BLUE;
        };
    }

    private static Sound soundOf(Kind k) {
        return switch (k) {
            case AURORA -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case BLOOD_SKY -> Sound.ENTITY_WITHER_AMBIENT;
            case GOLDEN_SKY -> Sound.ENTITY_PLAYER_LEVELUP;
            case BLIZZARD -> Sound.WEATHER_RAIN_ABOVE;
            case CRYSTAL_SURGE -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
            case DEEP_RUMBLE -> Sound.AMBIENT_CAVE;
        };
    }

    // ------------------------------------------------------------ lifecycle

    boolean isActive() {
        return active != null;
    }

    void start(Kind k) {
        if (active != null) stopSilently();
        active = k;
        startedAt = System.currentTimeMillis();
        long durationMs = Math.max(30, plugin.getConfig().getInt("sky-events.duration-seconds", 240)) * 1000L;
        endsAt = startedAt + durationMs;
        applyWorldStart(k);
        bar = BossBar.bossBar(Component.text(titleOf(k), textColorOf(k)), 1f, colorOf(k), BossBar.Overlay.PROGRESS);
        barShown.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            barShown.add(p.getUniqueId());
            p.playSound(p.getLocation(), soundOf(k), 0.8f, k == Kind.DEEP_RUMBLE ? 0.6f : 1f);
        }
        Bukkit.broadcast(Component.text(messageOf(k), textColorOf(k)));
    }

    private void stopSilently() {
        if (active == null) return;
        restoreWorlds();
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            bar = null;
        }
        barShown.clear();
        active = null;
        lastEventEnd = System.currentTimeMillis();
    }

    private void endNaturally() {
        Kind k = active;
        stopSilently();
        Bukkit.broadcast(Component.text("The " + titleOf(k) + " has ended.", NamedTextColor.GRAY));
    }

    /** Save the sky each event found, then bend it. */
    private void applyWorldStart(Kind k) {
        for (World w : Bukkit.getWorlds()) {
            savedTime.put(w.getName(), w.getTime());
            savedStorm.put(w.getName(), w.hasStorm());
            savedThunder.put(w.getName(), w.isThundering());
            switch (k) {
                case AURORA -> { w.setTime(18000); w.setStorm(false); w.setThundering(false); }
                case BLOOD_SKY -> { w.setTime(12500); w.setStorm(false); w.setThundering(false); }
                case GOLDEN_SKY -> { w.setTime(1000); w.setStorm(false); w.setThundering(false); }
                case BLIZZARD -> { w.setStorm(true); w.setThundering(false); }
                case CRYSTAL_SURGE, DEEP_RUMBLE -> { /* the caves don't care about the sky */ }
            }
        }
    }

    private void restoreWorlds() {
        for (World w : Bukkit.getWorlds()) {
            Long t = savedTime.get(w.getName());
            if (t != null) w.setTime(t);
            Boolean s = savedStorm.get(w.getName());
            if (s != null) w.setStorm(s);
            Boolean th = savedThunder.get(w.getName());
            if (th != null) w.setThundering(th);
        }
        savedTime.clear();
        savedStorm.clear();
        savedThunder.clear();
    }

    // ------------------------------------------------------------ ticking

    private void maybeAuto() {
        if (active != null) return;
        if (!plugin.getConfig().getBoolean("sky-events.enabled", true)) return;
        long betweenMs = Math.max(1, plugin.getConfig().getInt("sky-events.minutes-between-events", 30)) * 60_000L;
        if (System.currentTimeMillis() - lastEventEnd < betweenMs) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        Kind[] all = Kind.values();
        start(all[random.nextInt(all.length)]);
    }

    private void tick() {
        ticks++;
        if (active == null) return;
        long now = System.currentTimeMillis();
        if (now >= endsAt) {
            endNaturally();
            return;
        }
        // boss bar countdown + late joiners
        long leftSec = Math.max(0, (endsAt - now) / 1000);
        if (bar != null) {
            bar.progress((float) Math.max(0.0, Math.min(1.0, (endsAt - now) / (double) (endsAt - startedAt))));
            bar.name(Component.text(titleOf(active) + " - " + (leftSec / 60) + ":" + String.format("%02d", leftSec % 60),
                    textColorOf(active)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (barShown.add(p.getUniqueId())) p.showBossBar(bar);
            }
        }
        switch (active) {
            case AURORA -> tickAurora();
            case BLOOD_SKY -> tickBloodSky();
            case GOLDEN_SKY -> tickGoldenSky();
            case BLIZZARD -> tickBlizzard();
            case CRYSTAL_SURGE -> tickCrystalSurge();
            case DEEP_RUMBLE -> tickDeepRumble();
        }
    }

    /** Ribbons of light high above every player. */
    private void tickAurora() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location base = p.getLocation();
            for (int n = 0; n < 3; n++) {
                Location l = base.clone().add(random.nextDouble() * 16 - 8, 9 + random.nextDouble() * 4,
                        random.nextDouble() * 16 - 8);
                p.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0.01);
                if (random.nextInt(3) == 0) p.getWorld().spawnParticle(Particle.FIREWORK, l, 1, 0, 0, 0, 0.01);
            }
        }
    }

    /** Monsters near players hit harder; embers drift down. */
    private void tickBloodSky() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation().clone().add(random.nextDouble() * 10 - 5, 4, random.nextDouble() * 10 - 5);
            p.getWorld().spawnParticle(Particle.FLAME, l, 1, 0, 0.05, 0, 0.01);
        }
        if (ticks % 100 != 0) return; // refresh the buff every 5 seconds
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (org.bukkit.entity.Entity e : p.getNearbyEntities(40, 20, 40)) {
                if (e instanceof Monster m && m.isValid() && !m.isDead()) {
                    m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 140, 0));
                }
            }
        }
    }

    /** Warm light: a gentle heal for everyone, every 5 seconds. */
    private void tickGoldenSky() {
        if (ticks % 100 != 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0));
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 3, 0.4, 0.4, 0.4, 0.01);
        }
    }

    /** Snow everywhere; stand outside and vanilla freezing creeps in. */
    private void tickBlizzard() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location base = p.getLocation();
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 2.5, 0), 6, 5, 1.5, 5, 0.01);
        }
        if (ticks % 60 != 0) return; // a freeze nudge every 3 seconds
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            boolean exposed = l.getWorld().getHighestBlockYAt(l) <= l.getBlockY();
            if (exposed && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + 25));
            }
        }
    }

    /** Sparkles around anyone deep enough to feel the surge. */
    private void tickCrystalSurge() {
        int maxY = plugin.getConfig().getInt("sky-events.crystal-surge-max-y", 20);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getLocation().getBlockY() > maxY) continue;
            p.getWorld().spawnParticle(Particle.GLOW, p.getLocation().add(0, 1, 0), 4, 0.8, 0.8, 0.8, 0.02);
        }
    }

    /** Every few seconds the deep caves shake, sparkle and whisper. */
    private void tickDeepRumble() {
        if (ticks % 140 != 0) return; // every 7 seconds
        int maxY = plugin.getConfig().getInt("sky-events.deep-rumble-max-y", 40);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            if (l.getBlockY() > maxY) continue;
            p.playSound(l, Sound.AMBIENT_CAVE, 0.7f, 0.6f);
            plugin.bar(p, "The cave rumbles...", NamedTextColor.BLUE);
            l.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0, 3, 0), 12, 1.5, 0.4, 1.5, 0.0,
                    Material.STONE.createBlockData());
            l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(0, 1, 0), 3, 0.8, 0.8, 0.8, 0.02);
        }
    }

    /** CRYSTAL SURGE pays out here: ores mined deep down earn bonus coins. */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (active != Kind.CRYSTAL_SURGE) return;
        Player p = e.getPlayer();
        int maxY = plugin.getConfig().getInt("sky-events.crystal-surge-max-y", 20);
        if (e.getBlock().getLocation().getBlockY() > maxY) return;
        String n = e.getBlock().getType().name();
        if (!n.endsWith("_ORE") && !n.equals("ANCIENT_DEBRIS")) return;
        int bonus = Math.max(0, plugin.getConfig().getInt("sky-events.crystal-surge-bonus-coins", 3));
        if (bonus <= 0) return;
        plugin.unbound.addPoints(p, bonus, "crystal surge");
        plugin.bar(p, "+" + bonus + " Unbound Coins (Crystal Surge!)", NamedTextColor.LIGHT_PURPLE);
        e.getBlock().getWorld().spawnParticle(Particle.GLOW, e.getBlock().getLocation().add(0.5, 0.5, 0.5),
                8, 0.3, 0.3, 0.3, 0.02);
    }

    // ------------------------------------------------------------ /skyevent

    void command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abilitysmp.admin")) {
            sender.sendMessage(Component.text("OPs only.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text(active == null
                    ? "No sky event is running. They start on their own every "
                      + plugin.getConfig().getInt("sky-events.minutes-between-events", 30) + " minutes."
                    : "Running now: " + titleOf(active), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Start one: /skyevent <aurora|blood|golden|blizzard|crystal|rumble|stop>",
                    NamedTextColor.GRAY));
            return;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("stop")) {
            if (active == null) {
                sender.sendMessage(Component.text("No sky event is running.", NamedTextColor.GRAY));
            } else {
                Kind k = active;
                stopSilently();
                Bukkit.broadcast(Component.text("The " + titleOf(k) + " was ended early by an admin.", NamedTextColor.GRAY));
            }
            return;
        }
        Kind k = switch (a) {
            case "aurora" -> Kind.AURORA;
            case "blood" -> Kind.BLOOD_SKY;
            case "golden" -> Kind.GOLDEN_SKY;
            case "blizzard" -> Kind.BLIZZARD;
            case "crystal" -> Kind.CRYSTAL_SURGE;
            case "rumble" -> Kind.DEEP_RUMBLE;
            default -> null;
        };
        if (k == null) {
            sender.sendMessage(Component.text("Unknown event. Use: aurora, blood, golden, blizzard, crystal, rumble, stop",
                    NamedTextColor.RED));
            return;
        }
        start(k);
    }
}
