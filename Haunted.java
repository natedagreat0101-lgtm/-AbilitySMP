package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The Haunted Forest: a separate Halloween dimension reached through a jack-o'-lantern portal lit with
 * flint and steel. It has custom Halloween mobs, costumed mobs, trick-or-treat huts and loot chests.
 * Everything here is switched off by halloween.enabled: false. Ranks and progress live in Profile and stay.
 */
final class Haunted implements Listener {

    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final AbilitySMP plugin;
    private final NamespacedKey mobKey;
    private final Set<String> portals = new HashSet<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final Map<UUID, Integer> portalTicks = new HashMap<>();
    private final Map<UUID, Long> grace = new HashMap<>();
    private final Map<UUID, Integer> minuteCounter = new HashMap<>();
    private final File file;
    private final Random random = new Random();
    private World world;
    private Location hfSpawn;

    Haunted(AbilitySMP plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "haunted_mob");
        this.file = new File(plugin.getDataFolder(), "haunted.yml");
        portals.addAll(YamlConfiguration.loadConfiguration(file).getStringList("portals"));
        Bukkit.getScheduler().runTaskTimer(plugin, this::portalTick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::secondTick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::holdNight, 100L, 100L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (enabled() && new File(Bukkit.getWorldContainer(), worldName()).isDirectory()) world();
        });
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("halloween.enabled", true);
    }

    private String worldName() {
        return plugin.getConfig().getString("haunted.world-name", "haunted_forest");
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private boolean inForest(World w) {
        return world != null && w != null && w.equals(world);
    }

    // =====================================================================
    // The world
    // =====================================================================

    /** Every part of this world is Dark Forest, so it is full of trees. */
    private static final class DarkForest extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.DARK_FOREST;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.DARK_FOREST);
        }
    }

    World world() {
        if (world != null) return world;
        World w = Bukkit.getWorld(worldName());
        if (w == null) {
            try {
                w = new WorldCreator(worldName()).environment(World.Environment.NORMAL)
                        .biomeProvider(new DarkForest()).createWorld();
            } catch (Exception ex) {
                plugin.getLogger().warning("Dark forest biomes failed (" + ex.getMessage() + "), using normal terrain.");
                w = new WorldCreator(worldName()).environment(World.Environment.NORMAL).createWorld();
            }
        }
        if (w == null) return null;
        w.setTime(18000L);
        w.setStorm(false);
        world = w;
        return w;
    }

    private void holdNight() {
        if (world == null) return;
        world.setTime(18000L);
        world.setStorm(false);
    }

    private Location spawn() {
        if (hfSpawn != null) return hfSpawn;
        World w = world();
        if (w == null) return null;
        w.getChunkAt(0, 0).load(true);
        int y = w.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        for (int dx = -3; dx <= 8; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                w.getBlockAt(dx, y - 1, dz).setType(Material.SPRUCE_PLANKS, false);
                for (int h = 0; h <= 6; h++) w.getBlockAt(dx, y + h, dz).setType(Material.AIR, false);
            }
        }
        buildPortal(w, 4, y, 0, true);
        hfSpawn = new Location(w, 0.5, y, 0.5, -90f, 0f);
        return hfSpawn;
    }

    // =====================================================================
    // Portal: a frame of jack-o'-lanterns, lit with flint and steel
    // =====================================================================

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void savePortals() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("portals", new ArrayList<>(portals));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save haunted.yml: " + ex.getMessage());
        }
    }

    private static boolean air(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType().isAir();
    }

    private static boolean jack(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType() == Material.JACK_O_LANTERN;
    }

    /** Finds a closed jack-o'-lantern frame (at least 2 wide and 3 tall inside) around an air block. */
    private int[] detect(Block start, boolean axisX) {
        if (!start.getType().isAir()) return null;
        World w = start.getWorld();
        int x = start.getX(), y = start.getY(), z = start.getZ();
        int dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;

        int y0 = y;
        while (air(w, x, y0 - 1, z) && y - y0 < 21) y0--;
        if (!jack(w, x, y0 - 1, z)) return null;

        int l = 0;
        while (air(w, x - dx * (l + 1), y0, z - dz * (l + 1)) && l < 21) l++;
        int r = 0;
        while (air(w, x + dx * (r + 1), y0, z + dz * (r + 1)) && r < 21) r++;
        int width = l + r + 1;
        if (width < 2 || width > 21) return null;
        int lx = x - dx * l, lz = z - dz * l;

        int height = 0;
        while (air(w, lx, y0 + height, lz) && height < 22) height++;
        if (height < 3 || height > 21) return null;

        for (int i = 0; i < width; i++) {
            int cx = lx + dx * i, cz = lz + dz * i;
            if (!jack(w, cx, y0 - 1, cz) || !jack(w, cx, y0 + height, cz)) return null;
            for (int j = 0; j < height; j++) if (!air(w, cx, y0 + j, cz)) return null;
        }
        for (int j = 0; j < height; j++) {
            if (!jack(w, lx - dx, y0 + j, lz - dz) || !jack(w, lx + dx * width, y0 + j, lz + dz * width)) return null;
        }
        return new int[]{lx, y0, lz, width, height};
    }

    private void fill(World w, int[] f, boolean axisX) {
        int dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;
        for (int i = 0; i < f[3]; i++) {
            for (int j = 0; j < f[4]; j++) {
                Block b = w.getBlockAt(f[0] + dx * i, f[1] + j, f[2] + dz * i);
                b.setType(Material.NETHER_PORTAL, false);
                if (b.getBlockData() instanceof Orientable o) {
                    o.setAxis(axisX ? Axis.X : Axis.Z);
                    b.setBlockData(o, false);
                }
                portals.add(key(b));
            }
        }
        savePortals();
    }

    /** Builds a ready-made 2x3 portal (used for the return portal in the Haunted Forest). */
    private void buildPortal(World w, int x, int y, int z, boolean axisX) {
        int dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;
        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 3; j++) {
                if (i == -1 || i == 2 || j == -1 || j == 3) {
                    w.getBlockAt(x + dx * i, y + j, z + dz * i).setType(Material.JACK_O_LANTERN, false);
                }
            }
        }
        fill(w, new int[]{x, y, z, 2, 3}, axisX);
    }

    private boolean tryActivate(Block start) {
        for (boolean axisX : new boolean[]{true, false}) {
            int[] f = detect(start, axisX);
            if (f != null) {
                fill(start.getWorld(), f, axisX);
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onIgnite(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getItem() == null || e.getItem().getType() != Material.FLINT_AND_STEEL) return;
        if (e.getClickedBlock().getType() != Material.JACK_O_LANTERN) return;
        Block start = e.getClickedBlock().getRelative(e.getBlockFace());
        if (!enabled()) {
            if (detect(start, true) != null || detect(start, false) != null) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(Component.text("The Halloween dimension is closed right now.", NamedTextColor.GRAY));
            }
            return;
        }
        if (tryActivate(start)) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            p.sendMessage(Component.text("The jack-o'-lanterns flare up... a portal to the Haunted Forest opens!",
                    NamedTextColor.GOLD));
            start.getWorld().playSound(start.getLocation(), org.bukkit.Sound.ITEM_FLINTANDSTEEL_USE, 1f, 0.6f);
        }
    }

    @EventHandler
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NETHER_PORTAL && portals.contains(key(e.getBlock()))) e.setCancelled(true);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (inPortal(e.getPlayer())) e.setCancelled(true); // never send people to the Nether from our portals
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (portals.remove(key(e.getBlock()))) savePortals();
    }

    private boolean inPortal(Player p) {
        Location l = p.getLocation();
        return portals.contains(key(l.getBlock())) || portals.contains(key(l.clone().add(0, 1, 0).getBlock()));
    }

    private void portalTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (now < grace.getOrDefault(id, 0L) || !inPortal(p)) {
                portalTicks.remove(id);
                continue;
            }
            if (portalTicks.merge(id, 1, Integer::sum) >= 2) {
                portalTicks.remove(id);
                if (inForest(p.getWorld())) leave(p);
                else enter(p);
            }
        }
    }

    private Location backOf(Player p) {
        Location l = p.getLocation().clone();
        Vector d = l.getDirection().setY(0);
        if (d.lengthSquared() > 1.0E-6) l.subtract(d.normalize().multiply(2.5));
        l.setYaw(l.getYaw() + 180f);
        return l;
    }

    void enter(Player p) {
        if (!enabled()) {
            p.sendMessage(Component.text("The Halloween dimension is closed right now.", NamedTextColor.GRAY));
            return;
        }
        Location sp = spawn();
        if (sp == null) {
            p.sendMessage(Component.text("The Haunted Forest could not be created. Ask an admin to check the console.",
                    NamedTextColor.RED));
            return;
        }
        returnLoc.put(p.getUniqueId(), backOf(p));
        grace.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
        p.teleport(sp);
        Profile.Data d = plugin.profile.of(p);
        d.hauntedVisits++;
        plugin.profile.touch();
        p.showTitle(Title.title(Component.text("The Haunted Forest", NamedTextColor.GOLD),
                Component.text("Every mob you defeat drops Halloween Coins", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(800))));
        plugin.ranks.evaluate(p);
    }

    void leave(Player p) {
        Location back = returnLoc.get(p.getUniqueId());
        if (back == null || back.getWorld() == null) back = Bukkit.getWorlds().get(0).getSpawnLocation();
        grace.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
        p.teleport(back);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (inForest(p.getWorld())) p.setPlayerTime(13000L, false);
        else plugin.admin.applySky(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!inForest(p.getWorld())) return;
        if (!enabled()) leave(p);
        else p.setPlayerTime(13000L, false);
    }

    // =====================================================================
    // Survival time and coins per kill
    // =====================================================================

    private void secondTick() {
        if (world == null || !enabled()) return;
        int need = cfg("haunted.survive-minutes", 20) * 60;
        for (Player p : world.getPlayers()) {
            Profile.Data d = plugin.profile.of(p);
            d.survivalSeconds++;
            plugin.profile.touch();
            if (d.survivalSeconds == need) {
                p.showTitle(Title.title(Component.text("You survived!", NamedTextColor.GREEN),
                        Component.text(need / 60 + " minutes in the Haunted Forest", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
                plugin.ranks.evaluate(p);
            }
            if (minuteCounter.merge(p.getUniqueId(), 1, Integer::sum) >= 60) {
                minuteCounter.put(p.getUniqueId(), 0);
                plugin.unbound.addProgress(p, "HAUNTED_MINUTES", 1);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (inForest(e.getEntity().getWorld())) {
            plugin.profile.of(e.getEntity()).survivalSeconds = 0; // survival time starts over when you die
            plugin.profile.touch();
        }
    }

    private boolean tagged(LivingEntity e) {
        return e.getPersistentDataContainer().has(mobKey, PersistentDataType.STRING);
    }

    private String type(LivingEntity e) {
        String t = e.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        return t == null ? "" : t;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || !inForest(dead.getWorld()) || !enabled()) return;
        boolean ours = tagged(dead);
        if (!ours && !(dead instanceof Enemy)) return;
        Player killer = plugin.unbound.killerOf(dead);
        if (killer == null) return;
        if (ours) e.getDrops().clear();
        int coins = cfg("haunted.coins-per-kill", 5);
        plugin.tot.giveCoins(killer, coins);
        killer.sendActionBar(Component.text("+" + coins + " Halloween Coins", NamedTextColor.YELLOW));
        plugin.profile.of(killer).hauntedKills++;
        plugin.profile.touch();
        plugin.unbound.addProgress(killer, "KILL_HAUNTED", 1);
        plugin.ranks.evaluate(killer);
    }

    @EventHandler
    public void onMobHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof LivingEntity att) || !tagged(att)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        switch (type(att)) {
            case "vampire" -> {
                AttributeInstance max = att.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : 20.0;
                att.setHealth(Math.min(cap, att.getHealth() + e.getFinalDamage() * 0.5)); // drains health
            }
            case "jack" -> victim.setFireTicks(80);
            default -> { }
        }
    }

    // =====================================================================
    // Mobs
    // =====================================================================

    static ItemStack leather(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(c);
        it.setItemMeta(lm);
        return it;
    }

    static void dress(LivingEntity e, ItemStack helmet, Color color) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) return;
        if (helmet != null) eq.setHelmet(helmet);
        if (color != null) {
            eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, color));
            eq.setLeggings(leather(Material.LEATHER_LEGGINGS, color));
            eq.setBoots(leather(Material.LEATHER_BOOTS, color));
        }
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    private <T extends LivingEntity> T mob(Location loc, Class<T> cls, String type, String name, NamedTextColor color,
                                           double hp, double speed) {
        T e = loc.getWorld().spawn(loc, cls);
        e.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, type);
        e.customName(Component.text(name, color));
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(true);
        e.setCanPickupItems(false);
        AttributeInstance h = e.getAttribute(Attribute.MAX_HEALTH);
        if (h != null) {
            h.setBaseValue(hp);
            e.setHealth(hp);
        }
        AttributeInstance s = e.getAttribute(Attribute.MOVEMENT_SPEED);
        if (s != null && speed > 0) s.setBaseValue(speed);
        return e;
    }

    /** The custom Halloween mobs: skeleton, zombie, vampire, jack, ghost, witch, spider, brute. */
    LivingEntity spawnCustom(Location loc, String type) {
        return switch (type) {
            case "skeleton" -> {
                Skeleton s = mob(loc, Skeleton.class, type, "Haunted Skeleton", NamedTextColor.GRAY, 30, 0);
                dress(s, new ItemStack(Material.CARVED_PUMPKIN), null);
                s.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                yield s;
            }
            case "vampire" -> {
                Zombie z = mob(loc, Zombie.class, type, "Vampire", NamedTextColor.DARK_RED, 36, 0.31);
                dress(z, leather(Material.LEATHER_HELMET, Color.fromRGB(15, 10, 20)), Color.fromRGB(15, 10, 20));
                yield z;
            }
            case "jack" -> {
                Zombie z = mob(loc, Zombie.class, type, "Jack O' Lantern", NamedTextColor.GOLD, 40, 0.26);
                dress(z, new ItemStack(Material.JACK_O_LANTERN), Color.ORANGE);
                yield z;
            }
            case "ghost" -> mob(loc, Vex.class, type, "Ghost", NamedTextColor.WHITE, 18, 0);
            case "witch" -> mob(loc, Witch.class, type, "Haunted Witch", NamedTextColor.DARK_PURPLE, 36, 0);
            case "spider" -> mob(loc, Spider.class, type, "Cursed Spider", NamedTextColor.DARK_GREEN, 24, 0);
            case "brute" -> {
                Zombie z = mob(loc, Zombie.class, type, "Pumpkin Brute", NamedTextColor.GOLD, 70, 0.22);
                dress(z, new ItemStack(Material.CARVED_PUMPKIN), Color.fromRGB(120, 50, 0));
                AttributeInstance scale = z.getAttribute(Attribute.SCALE);
                if (scale != null) scale.setBaseValue(1.35);
                yield z;
            }
            default -> {
                Zombie z = mob(loc, Zombie.class, "zombie", "Rotten Zombie", NamedTextColor.GOLD, 34, 0);
                dress(z, null, Color.ORANGE);
                yield z;
            }
        };
    }

    /** A normal Minecraft mob wearing a Halloween costume. */
    private LivingEntity spawnCostumed(Location loc) {
        Class<? extends LivingEntity> cls = switch (random.nextInt(4)) {
            case 0 -> Zombie.class;
            case 1 -> Skeleton.class;
            case 2 -> Husk.class;
            default -> Stray.class;
        };
        LivingEntity e = mob(loc, cls, "costumed", "Costumed " + AbilitySMP.pretty(cls.getSimpleName()),
                NamedTextColor.LIGHT_PURPLE, 26, 0);
        Material[] hats = {Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN, Material.WITHER_SKELETON_SKULL,
                Material.ZOMBIE_HEAD, Material.SKELETON_SKULL, Material.CREEPER_HEAD};
        Color[] colors = {Color.ORANGE, Color.PURPLE, Color.BLACK, Color.WHITE};
        dress(e, new ItemStack(hats[random.nextInt(hats.length)]), colors[random.nextInt(colors.length)]);
        if (e instanceof AbstractSkeleton) e.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        return e;
    }

    private void spawnTick() {
        if (world == null || !enabled()) return;
        int cap = cfg("haunted.mob-cap", 12);
        String[] types = {"skeleton", "zombie", "vampire", "jack", "ghost", "witch", "spider", "brute"};
        for (Player p : world.getPlayers()) {
            int near = 0;
            for (LivingEntity le : world.getNearbyLivingEntities(p.getLocation(), 48)) if (tagged(le)) near++;
            if (near >= cap) continue;
            for (int tries = 0; tries < 6; tries++) {
                double ang = random.nextDouble() * Math.PI * 2;
                int dist = 14 + random.nextInt(15);
                int x = p.getLocation().getBlockX() + (int) Math.round(Math.cos(ang) * dist);
                int z = p.getLocation().getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
                Block g = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (!g.getType().isSolid()) continue;
                Location loc = g.getLocation().add(0.5, 1, 0.5);
                if (random.nextDouble() < 0.35) spawnCostumed(loc);
                else spawnCustom(loc, types[random.nextInt(types.length)]);
                break;
            }
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        LivingEntity le = e.getEntity();
        if (!inForest(le.getWorld()) || tagged(le)) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        boolean natural = r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || r == CreatureSpawnEvent.SpawnReason.PATROL || r == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS;
        if (!natural) return;
        if (le instanceof Enemy) {
            e.setCancelled(true); // only our custom and costumed mobs spawn here
        } else if (le instanceof Animals) {
            le.customName(Component.text("Costumed " + AbilitySMP.pretty(le.getType().name()), NamedTextColor.GOLD));
            le.setCustomNameVisible(true);
        }
    }

    // =====================================================================
    // Huts and loot chests, added to every newly generated chunk
    // =====================================================================

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk() || !inForest(e.getWorld()) || !enabled()) return;
        Chunk c = e.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> decorate(c));
    }

    private void decorate(Chunk c) {
        if (!c.isLoaded() || world == null) return;
        Random r = new Random(world.getSeed() ^ (c.getX() * 341873128712L) ^ (c.getZ() * 132897987541L));
        int cx = c.getX() * 16 + 8, cz = c.getZ() * 16 + 8;
        if (Math.abs(cx) < 24 && Math.abs(cz) < 24) return;
        double hut = plugin.getConfig().getDouble("haunted.hut-chance", 0.12);
        double chest = plugin.getConfig().getDouble("haunted.chest-chance", 0.20);
        double roll = r.nextDouble();
        if (roll < hut) buildHut(cx, cz, r);
        else if (roll < hut + chest) loneChest(c.getX() * 16 + 3 + r.nextInt(10), c.getZ() * 16 + 3 + r.nextInt(10), r);
    }

    private void buildHut(int cx, int cz, Random r) {
        int gy = world.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!world.getBlockAt(cx, gy, cz).getType().isSolid()) return;
        int sx = 0, sz = 0;
        switch (r.nextInt(4)) {
            case 0 -> sx = 1;
            case 1 -> sx = -1;
            case 2 -> sz = 1;
            default -> sz = -1;
        }
        BlockFace doorFace = sx == 1 ? BlockFace.EAST : sx == -1 ? BlockFace.WEST : sz == 1 ? BlockFace.SOUTH : BlockFace.NORTH;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = gy - 1; y >= gy - 8; y--) { // fill holes under the floor
                    Block b = world.getBlockAt(cx + dx, y, cz + dz);
                    if (b.getType().isSolid()) break;
                    b.setType(Material.DIRT, false);
                }
                for (int y = gy + 1; y <= gy + 8; y++) world.getBlockAt(cx + dx, y, cz + dz).setType(Material.AIR, false);
                if (Math.abs(dx) > 2 || Math.abs(dz) > 2) continue;
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                world.getBlockAt(cx + dx, gy, cz + dz).setType(Material.SPRUCE_PLANKS, false);
                if (edge) {
                    for (int y = 1; y <= 3; y++) {
                        world.getBlockAt(cx + dx, gy + y, cz + dz).setType(corner ? Material.DARK_OAK_LOG : Material.DARK_OAK_PLANKS, false);
                    }
                }
                world.getBlockAt(cx + dx, gy + 4, cz + dz).setType(Material.ORANGE_TERRACOTTA, false);
            }
        }
        for (int y = 1; y <= 2; y++) world.getBlockAt(cx + sx * 2, gy + y, cz + sz * 2).setType(Material.AIR, false);

        Block roof = world.getBlockAt(cx, gy + 5, cz);
        roof.setType(Material.JACK_O_LANTERN, false);
        face(roof, doorFace);
        int bx = cx - sx, bz = cz - sz;
        int px = sz != 0 ? 1 : 0, pz = sx != 0 ? 1 : 0;
        Block lamp = world.getBlockAt(bx - px, gy + 1, bz - pz);
        lamp.setType(Material.JACK_O_LANTERN, false);
        face(lamp, doorFace);
        world.getBlockAt(bx, gy + 3, bz).setType(Material.COBWEB, false);

        Block cb = world.getBlockAt(bx + px, gy + 1, bz + pz);
        cb.setType(Material.CHEST, false);
        face(cb, doorFace);
        if (cb.getState() instanceof Chest ch) fillLoot(ch.getBlockInventory(), r);

        plugin.tot.spawnNpc(world, new Location(world, cx + 0.5, gy + 1, cz + 0.5), new Vector(sx, 0, sz), true);
    }

    private void loneChest(int x, int z, Random r) {
        int gy = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!world.getBlockAt(x, gy, z).getType().isSolid()) return;
        Block b = world.getBlockAt(x, gy + 1, z);
        if (!b.getType().isAir() && b.getType() != Material.SHORT_GRASS) return;
        b.setType(Material.CHEST, false);
        face(b, FACES[r.nextInt(4)]);
        if (b.getState() instanceof Chest ch) fillLoot(ch.getBlockInventory(), r);
        world.getBlockAt(x + 1, gy + 1, z).setType(Material.JACK_O_LANTERN, false);
    }

    private void face(Block b, BlockFace f) {
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(f);
            b.setBlockData(d, false);
        }
    }

    private void fillLoot(Inventory inv, Random r) {
        List<ItemStack> loot = new ArrayList<>();
        ItemStack candy = plugin.tot.candyItem();
        candy.setAmount(3 + r.nextInt(8));
        loot.add(candy);
        if (r.nextDouble() < 0.6) {
            ItemStack coin = plugin.tot.coinItem();
            coin.setAmount(1 + r.nextInt(4));
            loot.add(coin);
        }
        Material[] extras = {Material.PUMPKIN_PIE, Material.GOLDEN_APPLE, Material.COOKED_BEEF, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.EMERALD, Material.EXPERIENCE_BOTTLE, Material.ENDER_PEARL, Material.BREAD};
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) loot.add(new ItemStack(extras[r.nextInt(extras.length)], 1 + r.nextInt(6)));
        if (r.nextDouble() < 0.10) loot.add(new ItemStack(Material.DIAMOND, 1 + r.nextInt(2)));
        if (r.nextDouble() < 0.03) {
            List<AbilitySMP.Power> pool = new ArrayList<>();
            for (AbilitySMP.Power pw : AbilitySMP.Power.values()) if (plugin.isEnabled(pw)) pool.add(pw);
            if (!pool.isEmpty()) loot.add(plugin.powerItem(pool.get(r.nextInt(pool.size()))));
        }
        if (r.nextDouble() < 0.02) loot.add(plugin.rerollItem());
        for (ItemStack it : loot) {
            for (int tries = 0; tries < 10; tries++) {
                int slot = r.nextInt(inv.getSize());
                if (inv.getItem(slot) == null) {
                    inv.setItem(slot, it);
                    break;
                }
            }
        }
    }

    // =====================================================================
    // /halloween (admin): enter, leave, status
    // =====================================================================

    void command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";
        if (sub.equals("status")) {
            s.sendMessage(Component.text("Halloween content: " + (enabled() ? "ON" : "OFF")
                    + "   Haunted Forest loaded: " + (world != null) + "   portals: " + portals.size(), NamedTextColor.GOLD));
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        switch (sub) {
            case "enter" -> enter(p);
            case "leave" -> leave(p);
            default -> s.sendMessage("Usage: /halloween <enter|leave|status>");
        }
    }

    void shutdown() {
        savePortals();
    }
}
