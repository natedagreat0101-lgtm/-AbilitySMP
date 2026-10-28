package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Trick or Treat Night: little huts appear around players. Right-click the trick-or-treater at the door
 * for candy (or a harmless trick). Sneak + right-click to trade candy for Halloween Coins.
 * Everything is removed again when the event ends.
 */
final class TrickOrTreat implements Listener {

    private static final class Hut {
        final Location center;
        final Map<Location, BlockData> old = new LinkedHashMap<>();
        UUID npc;
        UUID keeper;

        Hut(Location center) {
            this.center = center;
        }
    }

    private static final Set<Material> GROUND = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.SAND, Material.RED_SAND, Material.STONE, Material.MYCELIUM, Material.ROOTED_DIRT);

    private final AbilitySMP plugin;
    private final NamespacedKey npcKey;
    private final NamespacedKey candyKey;
    private final NamespacedKey coinKey;
    private final List<Hut> huts = new ArrayList<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Random random = new Random();
    private boolean active;
    private BukkitTask task;

    TrickOrTreat(AbilitySMP plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "treat_npc");
        this.candyKey = new NamespacedKey(plugin, "candy");
        this.coinKey = new NamespacedKey(plugin, "halloween_coin");
    }

    boolean isActive() {
        return active;
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    // =====================================================================
    // Start / stop
    // =====================================================================

    void start() {
        if (active) return;
        active = true;
        long interval = Math.max(5, cfg("trickortreat.interval-seconds", 20)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnHuts, 40L, interval);
        Bukkit.broadcast(Component.text("Trick or Treat Night has begun! Find the little huts and right-click the "
                + "trick-or-treater at the door!", NamedTextColor.GOLD));
    }

    void stop(boolean announce) {
        boolean was = active;
        active = false;
        if (task != null) task.cancel();
        task = null;
        for (Hut h : huts) removeHut(h);
        huts.clear();
        if (was && announce) {
            Bukkit.broadcast(Component.text("Trick or Treat Night is over. The huts are gone!", NamedTextColor.GRAY));
        }
    }

    // =====================================================================
    // Huts
    // =====================================================================

    private void spawnHuts() {
        int max = cfg("trickortreat.max-huts", 20);
        int per = cfg("trickortreat.huts-per-player", 2);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (huts.size() >= max) return;
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL) continue;

            int near = 0;
            for (Hut h : huts) {
                if (h.center.getWorld() == w && h.center.distanceSquared(p.getLocation()) < 45 * 45) near++;
            }
            if (near >= per) continue;

            for (int attempt = 0; attempt < 8; attempt++) {
                double ang = random.nextDouble() * Math.PI * 2;
                int dist = 12 + random.nextInt(14);
                int x = p.getLocation().getBlockX() + (int) Math.round(Math.cos(ang) * dist);
                int z = p.getLocation().getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
                Block ground = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (canBuild(ground)) {
                    build(ground, p.getLocation());
                    break;
                }
            }
        }
    }

    private static boolean isClear(Material m) {
        return m.isAir() || m == Material.SHORT_GRASS || m == Material.TALL_GRASS || m == Material.FERN
                || m == Material.LARGE_FERN || m == Material.DEAD_BUSH || m == Material.SNOW || Tag.FLOWERS.isTagged(m);
    }

    /** Only flat, natural ground with nothing built on it, so player bases are never touched. */
    private boolean canBuild(Block ground) {
        World w = ground.getWorld();
        int cx = ground.getX(), gy = ground.getY(), cz = ground.getZ();
        if (gy < w.getMinHeight() + 5 || gy > w.getMaxHeight() - 10) return false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!GROUND.contains(w.getBlockAt(cx + dx, gy, cz + dz).getType())) return false;
                for (int y = 1; y <= 6; y++) {
                    if (!isClear(w.getBlockAt(cx + dx, gy + y, cz + dz).getType())) return false;
                }
            }
        }
        return true;
    }

    private void set(Hut hut, Block b, Material m) {
        hut.old.putIfAbsent(b.getLocation(), b.getBlockData());
        b.setType(m, false);
    }

    private void face(Block b, BlockFace f) {
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(f);
            b.setBlockData(d, false);
        }
    }

    private void build(Block ground, Location playerLoc) {
        World w = ground.getWorld();
        int cx = ground.getX(), gy = ground.getY(), cz = ground.getZ();
        Hut hut = new Hut(new Location(w, cx + 0.5, gy + 1, cz + 0.5));

        // the door faces the player
        Vector toP = playerLoc.toVector().subtract(new Vector(cx, gy, cz));
        int sx = 0, sz = 0;
        if (Math.abs(toP.getX()) > Math.abs(toP.getZ())) sx = toP.getX() > 0 ? 1 : -1;
        else sz = toP.getZ() > 0 ? 1 : -1;
        BlockFace doorFace = sx == 1 ? BlockFace.EAST : sx == -1 ? BlockFace.WEST : sz == 1 ? BlockFace.SOUTH : BlockFace.NORTH;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                set(hut, w.getBlockAt(cx + dx, gy, cz + dz), Material.SPRUCE_PLANKS);
                for (int y = 1; y <= 6; y++) set(hut, w.getBlockAt(cx + dx, gy + y, cz + dz), Material.AIR);
                if (edge) {
                    for (int y = 1; y <= 3; y++) {
                        set(hut, w.getBlockAt(cx + dx, gy + y, cz + dz),
                                corner ? Material.DARK_OAK_LOG : Material.DARK_OAK_PLANKS);
                    }
                }
                set(hut, w.getBlockAt(cx + dx, gy + 4, cz + dz), Material.ORANGE_TERRACOTTA);
            }
        }

        // door opening (2 high)
        for (int y = 1; y <= 2; y++) set(hut, w.getBlockAt(cx + sx * 2, gy + y, cz + sz * 2), Material.AIR);

        // decorations: jack-o-lantern on the roof, two inside, a cobweb
        Block roofPumpkin = w.getBlockAt(cx, gy + 5, cz);
        set(hut, roofPumpkin, Material.JACK_O_LANTERN);
        face(roofPumpkin, doorFace);
        int bx = cx - sx, bz = cz - sz;
        int px = sz != 0 ? 1 : 0, pz = sx != 0 ? 1 : 0;
        Block l1 = w.getBlockAt(bx + px, gy + 1, bz + pz);
        Block l2 = w.getBlockAt(bx - px, gy + 1, bz - pz);
        set(hut, l1, Material.JACK_O_LANTERN);
        face(l1, doorFace);
        set(hut, l2, Material.JACK_O_LANTERN);
        face(l2, doorFace);
        set(hut, w.getBlockAt(bx, gy + 3, bz), Material.COBWEB);

        // the trick-or-treater
        Location npcLoc = new Location(w, cx + 0.5, gy + 1, cz + 0.5);
        npcLoc.setDirection(new Vector(sx, 0, sz));
        Villager v = w.spawn(npcLoc, Villager.class, vil -> {
            vil.setAI(false);
            vil.setInvulnerable(true);
            vil.setSilent(true);
            vil.setPersistent(false);
            vil.customName(Component.text("Right-click: Trick or Treat!", NamedTextColor.GOLD, TextDecoration.BOLD));
            vil.setCustomNameVisible(true);
            vil.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "1");
        });
        hut.npc = v.getUniqueId();
        // a Halloween Shopkeeper stands beside the trick-or-treater
        Location keeperLoc = new Location(w, cx + px + 0.5, gy + 1, cz + pz + 0.5);
        hut.keeper = plugin.shops.spawnKeeper(keeperLoc, "halloween", npcLoc.clone().add(sx * 3, 0, sz * 3), false).getUniqueId();
        huts.add(hut);
        w.spawnParticle(Particle.SOUL, npcLoc.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
    }

    private void removeHut(Hut h) {
        Entity e = h.npc == null ? null : Bukkit.getEntity(h.npc);
        if (e != null) e.remove();
        Entity k = h.keeper == null ? null : Bukkit.getEntity(h.keeper);
        if (k != null) k.remove();
        for (Map.Entry<Location, BlockData> en : h.old.entrySet()) {
            en.getKey().getBlock().setBlockData(en.getValue(), false);
        }
    }

    // =====================================================================
    // Candy and coins
    // =====================================================================

    private ItemStack candy() {
        ItemStack it = new ItemStack(Material.COOKIE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Halloween Candy", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Sneak + right-click a trick-or-treater", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("to trade candy for Halloween Coins.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(candyKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack coin() {
        ItemStack it = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Halloween Coin", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(coinKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    private boolean isCandy(ItemStack it) {
        return it != null && it.getType() == Material.COOKIE && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(candyKey, PersistentDataType.STRING);
    }

    // =====================================================================
    // Interaction
    // =====================================================================

    // ---- used by the shop, the Haunted Forest and the boss rewards

    ItemStack candyItem() {
        return candy();
    }

    ItemStack coinItem() {
        return coin();
    }

    private boolean isCoin(ItemStack it) {
        return it != null && it.getType() == Material.GOLD_NUGGET && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(coinKey, PersistentDataType.STRING);
    }

    /** Halloween Coins are a saved balance (like Unbound Coins), not an item. */
    void giveCoins(Player p, int amount) {
        if (amount <= 0) return;
        plugin.profile.of(p).halloweenCoins += amount;
        plugin.profile.touch();
        p.sendActionBar(Component.text("+" + amount + " Halloween Coins (you have " + countCoins(p) + ")", NamedTextColor.YELLOW));
    }

    int countCoins(Player p) {
        return plugin.profile.of(p).halloweenCoins;
    }

    boolean removeCoins(Player p, int amount) {
        Profile.Data d = plugin.profile.of(p);
        if (d.halloweenCoins < amount) return false;
        d.halloweenCoins -= amount;
        plugin.profile.save();
        return true;
    }

    /** Coin items from earlier versions are turned into balance when you join. */
    private void convertLegacy(Player p) {
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isCoin(it)) {
                total += it.getAmount();
                it.setAmount(0);
            }
        }
        if (total > 0) giveCoins(p, total);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        convertLegacy(e.getPlayer());
    }

    /** A trick-or-treater. Persistent ones (Haunted Forest huts) stay; event ones vanish on restart. */
    Villager spawnNpc(World w, Location loc, Vector dir, boolean persistent) {
        loc.setDirection(dir);
        return w.spawn(loc, Villager.class, vil -> {
            vil.setAI(false);
            vil.setInvulnerable(true);
            vil.setSilent(true);
            vil.setPersistent(persistent);
            vil.customName(Component.text("Right-click: Trick or Treat!", NamedTextColor.GOLD, TextDecoration.BOLD));
            vil.setCustomNameVisible(true);
            vil.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "1");
        });
    }

    @EventHandler
    public void onNpc(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        if (!v.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) return;
        e.setCancelled(true); // no villager trade menu
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (p.isSneaking()) trade(p);
        else trickOrTreat(p);
    }

    private void trickOrTreat(Player p) {
        long now = System.currentTimeMillis();
        long until = cooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            p.sendActionBar(Component.text("Come back in " + ((until - now + 999) / 1000) + "s for more!",
                    NamedTextColor.GRAY));
            return;
        }
        cooldown.put(p.getUniqueId(), now + cfg("trickortreat.cooldown-seconds", 20) * 1000L);

        if (random.nextDouble() < plugin.getConfig().getDouble("trickortreat.treat-chance", 0.7)) {
            int n = 1 + random.nextInt(3);
            ItemStack c = candy();
            c.setAmount(n);
            plugin.give(p, c);
            plugin.unbound.addProgress(p, "COLLECT_CANDY", n);
            p.sendMessage(Component.text("Treat! You got " + n + " candy. Sneak + right-click to trade candy for coins.",
                    NamedTextColor.GOLD));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        } else {
            switch (random.nextInt(3)) {
                case 0 -> p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                case 1 -> p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0));
                default -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.03);
            p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 0.6f, 1.4f);
            p.sendMessage(Component.text("TRICK! A ghost got you... (harmless, it wears off)", NamedTextColor.DARK_PURPLE));
        }
    }

    private void trade(Player p) {
        int perCoin = Math.max(1, cfg("trickortreat.candy-per-coin", 5));
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isCandy(it)) total += it.getAmount();
        }
        int coins = total / perCoin;
        if (coins <= 0) {
            p.sendMessage(Component.text("You need " + perCoin + " candy for a Halloween Coin. You have " + total + ".",
                    NamedTextColor.GRAY));
            return;
        }
        int toRemove = coins * perCoin;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack it = contents[i];
            if (!isCandy(it)) continue;
            int take = Math.min(toRemove, it.getAmount());
            it.setAmount(it.getAmount() - take);
            toRemove -= take;
        }
        giveCoins(p, coins);
        p.sendMessage(Component.text("Traded " + (coins * perCoin) + " candy for " + coins + " Halloween Coin"
                + (coins == 1 ? "" : "s") + "!", NamedTextColor.YELLOW));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
    }
}
