package me.abilitysmp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The Galaxy Burst (v14): a /panel event that opens a portal to a whole new
 * SPACE dimension - a starry void world (End sky) with a platform and
 * floating islands. Players jump in with /burst, fight special space mobs
 * and mine burst crystals, and everything pays Galaxy Coins. When the timer
 * runs out, everyone is sent home. Dying in space loses nothing
 * (the space world has keep-inventory on).
 */
final class SpaceBurst implements Listener {

    static final String WORLD_NAME = "abilitysmp_space";

    /** An empty void generator: the space world is just stars until we build in it. */
    static final class VoidGen extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    private record MobDef(EntityType type, String name, double hp, int coins, NamedTextColor color) {
    }

    private static final List<MobDef> MOBS = List.of(
            new MobDef(EntityType.VEX, "Star Wraith", 20, 6, NamedTextColor.AQUA),
            new MobDef(EntityType.HUSK, "Meteor Husk", 30, 8, NamedTextColor.GOLD),
            new MobDef(EntityType.PHANTOM, "Comet Phantom", 24, 7, NamedTextColor.LIGHT_PURPLE),
            new MobDef(EntityType.ENDERMAN, "Void Stalker", 40, 10, NamedTextColor.DARK_PURPLE));

    private final AbilitySMP plugin;
    private final Random random = new Random();
    private final NamespacedKey mobKey;
    private boolean running;
    private long endAt;
    private long totalMs;
    private final Map<UUID, Location> participants = new HashMap<>();
    private final Set<UUID> joinedThisEvent = new HashSet<>();
    private final List<Location> crystalSpots = new ArrayList<>();
    private BossBar bar;
    private BukkitTask waveTask;
    private BukkitTask tickTask;

    SpaceBurst(AbilitySMP plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "burst_mob");
    }

    boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------- world

    private World world() {
        World w = Bukkit.getWorld(WORLD_NAME);
        if (w != null) return w;
        w = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.THE_END)
                .generator(new VoidGen())
                .generateStructures(false)
                .createWorld();
        if (w != null) {
            w.setGameRule(GameRule.KEEP_INVENTORY, true);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setDifficulty(org.bukkit.Difficulty.NORMAL);
        }
        return w;
    }

    private Location spawnPoint(World w) {
        return new Location(w, 0.5, 67, 0.5);
    }

    /** Builds the platform + islands once; the spots where burst crystals grow are remembered. */
    private void buildLayout(World w) {
        if (plugin.data.getBoolean("burst-built", false)) {
            loadCrystalSpots(w);
            return;
        }
        int y = 64;
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 9.5) continue;
                Material m = dist > 8.2 ? Material.PURPUR_BLOCK : dist < 2.5 ? Material.GLOWSTONE : Material.END_STONE;
                w.getBlockAt(dx, y, dz).setType(m);
                if (dist > 8.2) w.getBlockAt(dx, y + 1, dz).setType(Material.PURPUR_PILLAR);
            }
        }
        int[][] islands = {{24, 58, 10, 4}, {-26, 70, -14, 5}, {12, 76, -28, 3}, {-18, 54, 26, 4}, {30, 66, 30, 3}};
        List<String> spots = new ArrayList<>();
        for (int[] isl : islands) {
            int cx = isl[0], cy = isl[1], cz = isl[2], r = isl[3];
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.END_STONE);
                    if (Math.abs(dx) + Math.abs(dz) == r - 1) w.getBlockAt(cx + dx, cy - 1, cz + dz).setType(Material.END_STONE);
                }
            }
            w.getBlockAt(cx, cy + 1, cz).setType(Material.BUDDING_AMETHYST);
            spots.add(cx + ";" + (cy + 1) + ";" + cz);
            int cx2 = cx + r - 1, cz2 = cz - r + 2;
            w.getBlockAt(cx2, cy + 1, cz2).setType(Material.AMETHYST_CLUSTER);
            spots.add(cx2 + ";" + (cy + 1) + ";" + cz2);
        }
        plugin.data.set("burst-crystals", spots);
        plugin.data.set("burst-built", true);
        plugin.saveData();
        loadCrystalSpots(w);
    }

    private void loadCrystalSpots(World w) {
        crystalSpots.clear();
        for (String s : plugin.data.getStringList("burst-crystals")) {
            String[] parts = s.split(";");
            if (parts.length == 3) {
                crystalSpots.add(new Location(w, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }
    }

    private void restockCrystals() {
        for (Location l : crystalSpots) {
            Block b = l.getBlock();
            if (b.getType() == Material.AIR) {
                b.setType(b.getLocation().getBlockY() % 2 == 0 ? Material.AMETHYST_CLUSTER : Material.BUDDING_AMETHYST);
            }
        }
    }

    // ------------------------------------------------------------- event flow

    void toggleFromPanel(Player admin) {
        if (running) stop();
        else start();
    }

    void start() {
        if (running) return;
        World w = world();
        if (w == null) {
            plugin.getLogger().warning("Could not create the space world for the Galaxy Burst.");
            return;
        }
        buildLayout(w);
        restockCrystals();
        running = true;
        joinedThisEvent.clear();
        int minutes = plugin.getConfig().getInt("galaxy-burst.minutes", 5);
        totalMs = minutes * 60_000L;
        endAt = System.currentTimeMillis() + totalMs;
        bar = BossBar.bossBar(Component.text("🌌 GALAXY BURST", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(Component.text("🌌 THE GALAXY BURST HAS BEGUN! 🌌", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("A portal to SPACE is open for " + minutes + " minutes! Type /burst to jump in - "
                + "space mobs and burst crystals pay Galaxy Coins, and dying up there loses nothing.", NamedTextColor.GRAY));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        waveTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnWave();
            }
        }.runTaskTimer(plugin, 200L, 400L);
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long left = endAt - System.currentTimeMillis();
                if (left <= 0) {
                    stop();
                    return;
                }
                if (bar != null) {
                    bar.progress(Math.max(0f, Math.min(1f, (float) left / totalMs)));
                    long s = left / 1000;
                    bar.name(Component.text("🌌 GALAXY BURST — " + (s / 60) + ":" + String.format("%02d", s % 60) + " left",
                            NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    void stop() {
        if (!running) return;
        running = false;
        if (waveTask != null) waveTask.cancel();
        if (tickTask != null) tickTask.cancel();
        World main = Bukkit.getWorlds().get(0);
        for (Map.Entry<UUID, Location> e : new ArrayList<>(participants.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            if (bar != null) p.hideBossBar(bar);
            Location back = e.getValue() != null ? e.getValue() : main.getSpawnLocation();
            p.teleport(back);
            p.sendMessage(Component.text("The Galaxy Burst is over - welcome back to solid ground!", NamedTextColor.LIGHT_PURPLE));
        }
        participants.clear();
        World w = Bukkit.getWorld(WORLD_NAME);
        if (w != null) {
            for (Entity en : w.getEntities()) {
                if (en.getPersistentDataContainer().has(mobKey, PersistentDataType.INTEGER)) en.remove();
            }
        }
        bar = null;
        Bukkit.broadcast(Component.text("🌌 The Galaxy Burst has ended. The portal to space is closed... until next time.", NamedTextColor.LIGHT_PURPLE));
    }

    private void spawnWave() {
        if (!running || participants.isEmpty()) return;
        World w = Bukkit.getWorld(WORLD_NAME);
        if (w == null) return;
        long alive = w.getEntities().stream()
                .filter(en -> en.getPersistentDataContainer().has(mobKey, PersistentDataType.INTEGER)).count();
        if (alive >= 25) return;
        int mult = Math.max(1, plugin.getConfig().getInt("galaxy-burst.coin-multiplier", 1));
        for (UUID id : new ArrayList<>(participants.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || p.getWorld() != w) continue;
            int n = 1 + random.nextInt(2);
            for (int i = 0; i < n; i++) {
                MobDef def = MOBS.get(random.nextInt(MOBS.size()));
                Location at = p.getLocation().clone().add(random.nextInt(17) - 8, 2, random.nextInt(17) - 8);
                at = w.getHighestBlockAt(at).getLocation().add(0, 1.5, 0);
                if (at.getY() < 20 || at.getY() > 120) at = p.getLocation().clone().add(3, 1, 3);
                Entity e = w.spawnEntity(at, def.type());
                if (e instanceof LivingEntity le) {
                    le.customName(Component.text(def.name(), def.color(), TextDecoration.BOLD));
                    le.setCustomNameVisible(true);
                    le.setGlowing(true);
                    if (le.getAttribute(Attribute.MAX_HEALTH) != null) {
                        le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(def.hp());
                        le.setHealth(def.hp());
                    }
                    le.getPersistentDataContainer().set(mobKey, PersistentDataType.INTEGER, def.coins() * mult);
                    if (le instanceof Mob mob) mob.setTarget(p);
                }
            }
        }
    }

    // ------------------------------------------------------------- joining

    void command(Player p, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            if (!p.isOp()) {
                p.sendMessage(Component.text("Only an OP can start or stop the Galaxy Burst (it's a /panel event).", NamedTextColor.RED));
                return;
            }
            if (args[0].equalsIgnoreCase("start")) {
                start();
                p.sendMessage(Component.text("Galaxy Burst started!", NamedTextColor.LIGHT_PURPLE));
            } else {
                stop();
                p.sendMessage(Component.text("Galaxy Burst stopped.", NamedTextColor.LIGHT_PURPLE));
            }
            return;
        }
        join(p);
    }

    void join(Player p) {
        if (!running) {
            p.sendMessage(Component.text("The Galaxy Burst isn't running right now. An admin starts it from /panel (Server Events), "
                    + "and then /burst will take you to space!", NamedTextColor.GRAY));
            return;
        }
        if (participants.containsKey(p.getUniqueId())) {
            p.sendMessage(Component.text("You're already in the Burst! Fight space mobs, mine the purple crystals.", NamedTextColor.GRAY));
            return;
        }
        World w = world();
        buildLayout(w);
        participants.put(p.getUniqueId(), p.getLocation().clone());
        p.teleport(spawnPoint(w));
        if (bar != null) p.showBossBar(bar);
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.2f);
        p.sendMessage(Component.text("🌌 Welcome to SPACE! Star mobs drop Galaxy Coins, the purple crystals pay coins too, "
                + "and dying here loses NOTHING. You go home when the timer ends.", NamedTextColor.LIGHT_PURPLE));
        if (joinedThisEvent.add(p.getUniqueId())) {
            String path = "players." + p.getUniqueId() + ".";
            plugin.data.set(path + "burst-visits", plugin.data.getInt(path + "burst-visits", 0) + 1);
            plugin.saveData();
            plugin.ranks.evaluate(p);
        }
    }

    // ------------------------------------------------------------- rewards & safety

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        Integer coins = e.getEntity().getPersistentDataContainer().get(mobKey, PersistentDataType.INTEGER);
        if (coins == null) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        plugin.shops.giveGalaxyCoins(killer, coins);
        addBurstCoins(killer, coins);
        killer.sendActionBar(Component.text("+" + coins + " Galaxy Coins", NamedTextColor.AQUA));
    }

    @EventHandler
    public void onCrystalBreak(BlockBreakEvent e) {
        if (!e.getBlock().getWorld().getName().equals(WORLD_NAME)) return;
        if (e.getBlock().getType() != Material.AMETHYST_CLUSTER && e.getBlock().getType() != Material.LARGE_AMETHYST_BUD) return;
        plugin.shops.giveGalaxyCoins(e.getPlayer(), 3);
        addBurstCoins(e.getPlayer(), 3);
        e.getPlayer().sendActionBar(Component.text("+3 Galaxy Coins (burst crystal)", NamedTextColor.AQUA));
    }

    private void addBurstCoins(Player p, int coins) {
        String path = "players." + p.getUniqueId() + ".";
        plugin.data.set(path + "burst-coins", plugin.data.getInt(path + "burst-coins", 0) + coins);
        plugin.saveData();
        plugin.ranks.evaluate(p);
    }

    int burstCoins(Player p) {
        return plugin.data.getInt("players." + p.getUniqueId() + ".burst-coins", 0);
    }

    int burstVisits(Player p) {
        return plugin.data.getInt("players." + p.getUniqueId() + ".burst-visits", 0);
    }

    @EventHandler
    public void onNaturalSpawn(CreatureSpawnEvent e) {
        if (e.getLocation().getWorld() != null && e.getLocation().getWorld().getName().equals(WORLD_NAME)
                && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (participants.containsKey(e.getPlayer().getUniqueId())) {
            World w = Bukkit.getWorld(WORLD_NAME);
            if (w != null) e.setRespawnLocation(spawnPoint(w));
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (e.getFrom().getWorld() != null && e.getFrom().getWorld().getName().equals(WORLD_NAME)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        participants.remove(e.getPlayer().getUniqueId());
        if (bar != null) e.getPlayer().hideBossBar(bar);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld().getName().equals(WORLD_NAME) && !running) {
            // They logged out in space and the Burst is over: send them home.
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            p.sendMessage(Component.text("The Galaxy Burst ended while you were away - back on solid ground!", NamedTextColor.LIGHT_PURPLE));
        }
    }
}
