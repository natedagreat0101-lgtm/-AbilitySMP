package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Unbound points: earned from quests (they reset by themselves when you finish them) and from playtime.
 * Spent at an upgrade station to level up your power (shorter cooldown, stronger range and damage).
 */
final class Unbound implements Listener {

    static final class PlayerData {
        int points;
        int minutes;
        int completed;
        final Map<String, Integer> progress = new HashMap<>();
        final Map<String, Integer> levels = new HashMap<>();
    }

    static final class UpgradeHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int UPGRADE_SLOT = 13;

    private final AbilitySMP plugin;
    private final File file;
    private YamlConfiguration yaml;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Set<String> stations = new HashSet<>();
    private boolean dirty;

    Unbound(AbilitySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "unbound.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::minuteTick, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::stationFx, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 600L, 600L);
    }

    // =====================================================================
    // Saving
    // =====================================================================

    void load() {
        data.clear();
        stations.clear();
        yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("players");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    PlayerData d = new PlayerData();
                    d.points = sec.getInt(id + ".points");
                    d.minutes = sec.getInt(id + ".minutes");
                    d.completed = sec.getInt(id + ".completed");
                    ConfigurationSection pr = sec.getConfigurationSection(id + ".progress");
                    if (pr != null) for (String k : pr.getKeys(false)) d.progress.put(k, pr.getInt(k));
                    ConfigurationSection lv = sec.getConfigurationSection(id + ".levels");
                    if (lv != null) for (String k : lv.getKeys(false)) d.levels.put(k, lv.getInt(k));
                    data.put(uuid, d);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        stations.addAll(yaml.getStringList("stations"));
    }

    void save() {
        yaml.set("players", null);
        for (Map.Entry<UUID, PlayerData> e : data.entrySet()) {
            String base = "players." + e.getKey();
            PlayerData d = e.getValue();
            yaml.set(base + ".points", d.points);
            yaml.set(base + ".minutes", d.minutes);
            yaml.set(base + ".completed", d.completed);
            for (Map.Entry<String, Integer> q : d.progress.entrySet()) yaml.set(base + ".progress." + q.getKey(), q.getValue());
            for (Map.Entry<String, Integer> l : d.levels.entrySet()) yaml.set(base + ".levels." + l.getKey(), l.getValue());
        }
        yaml.set("stations", new ArrayList<>(stations));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save unbound.yml: " + ex.getMessage());
        }
    }

    private PlayerData pd(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
    }

    // =====================================================================
    // Points
    // =====================================================================

    int points(Player p) {
        return pd(p).points;
    }

    void addPoints(Player p, int amount, String reason) {
        PlayerData d = pd(p);
        d.points = Math.max(0, d.points + amount);
        dirty = true;
        if (amount > 0) {
            p.sendActionBar(Component.text("+" + amount + " Unbound points (" + reason + ")", NamedTextColor.AQUA));
        }
    }

    private void minuteTick() {
        int need = Math.max(1, plugin.getConfig().getInt("points.play-minutes", 20));
        int reward = plugin.getConfig().getInt("points.play-reward", 10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = pd(p);
            d.minutes++;
            dirty = true;
            if (d.minutes >= need) {
                d.minutes = 0;
                addPoints(p, reward, "playtime");
                p.sendMessage(Component.text("You played for " + need + " minutes: +" + reward
                        + " Unbound points!", NamedTextColor.AQUA));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            }
        }
    }

    // =====================================================================
    // Power levels (upgrades)
    // =====================================================================

    int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("upgrade.max-level", 5));
    }

    int level(Player p, AbilitySMP.Power pw) {
        if (pw == null) return 1;
        return Math.max(1, Math.min(maxLevel(), pd(p).levels.getOrDefault(pw.name(), 1)));
    }

    int level(Player p) {
        return level(p, plugin.powerOf(p));
    }

    /** Range, damage and duration multiplier from the current power's level. */
    double powerMult(Player p) {
        return 1.0 + plugin.getConfig().getDouble("upgrade.power-per-level", 0.10) * (level(p) - 1);
    }

    /** Cooldown multiplier from the current power's level (lower is faster). */
    double cooldownMult(Player p) {
        return Math.max(0.3, 1.0 - plugin.getConfig().getDouble("upgrade.cooldown-per-level", 0.10) * (level(p) - 1));
    }

    private int costFor(int currentLevel) {
        List<Integer> costs = plugin.getConfig().getIntegerList("upgrade.costs");
        if (costs.isEmpty()) return 50 * currentLevel;
        if (currentLevel - 1 < costs.size()) return costs.get(currentLevel - 1);
        return costs.get(costs.size() - 1) * 2;
    }

    // =====================================================================
    // Quests (repeatable: they reset by themselves when you finish them)
    // =====================================================================

    private ConfigurationSection quests() {
        return plugin.getConfig().getConfigurationSection("quests");
    }

    void addProgress(Player p, String type, int amount) {
        ConfigurationSection qs = quests();
        if (qs == null || amount <= 0) return;
        PlayerData d = pd(p);
        for (String id : qs.getKeys(false)) {
            ConfigurationSection q = qs.getConfigurationSection(id);
            if (q == null || !type.equalsIgnoreCase(q.getString("type", ""))) continue;
            int target = Math.max(1, q.getInt("target", 10));
            int now = d.progress.getOrDefault(id, 0) + amount;
            int done = 0;
            while (now >= target) {
                now -= target;
                done++;
            }
            d.progress.put(id, now); // progress starts over by itself
            dirty = true;
            if (done > 0) {
                int reward = q.getInt("reward", 10) * done;
                d.completed += done;
                addPoints(p, reward, "quest");
                p.showTitle(Title.title(
                        Component.text("Quest complete!", NamedTextColor.GREEN),
                        Component.text(q.getString("name", id) + "  +" + reward + " Unbound points", NamedTextColor.AQUA),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
                p.sendMessage(Component.text("Quest complete: " + q.getString("name", id) + " (+" + reward
                        + " Unbound points). It has reset, so you can do it again!", NamedTextColor.GREEN));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) return;
        if (dead instanceof Player) addProgress(killer, "KILL_PLAYERS", 1);
        else if (dead instanceof Enemy) addProgress(killer, "KILL_MOBS", 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (stations.remove(keyOf(b))) {
            dirty = true;
            e.getPlayer().sendMessage(Component.text("Upgrade station removed.", NamedTextColor.GRAY));
            return;
        }
        Material m = b.getType();
        Player p = e.getPlayer();
        if (Tag.LOGS.isTagged(m)) addProgress(p, "BREAK_LOGS", 1);
        else if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) addProgress(p, "MINE_ORES", 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (dirty) save();
    }

    // =====================================================================
    // Upgrade stations
    // =====================================================================

    private static String keyOf(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    void addStation(Player p, Block b) {
        if (b == null || b.getType().isAir()) {
            p.sendMessage(Component.text("Look at a block (within 6 blocks) first.", NamedTextColor.RED));
            return;
        }
        if (stations.add(keyOf(b))) {
            dirty = true;
            save();
            p.sendMessage(Component.text("That " + b.getType().name().toLowerCase().replace('_', ' ')
                    + " is now an Upgrade Station. Right-click it to upgrade powers.", NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("That block is already an Upgrade Station.", NamedTextColor.GRAY));
        }
    }

    private void stationFx() {
        for (String key : stations) {
            String[] parts = key.split(";");
            if (parts.length != 4) continue;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) continue;
            try {
                int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                w.spawnParticle(Particle.END_ROD, new Location(w, x + 0.5, y + 1.3, z + 0.5), 3, 0.2, 0.2, 0.2, 0.01);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @EventHandler
    public void onStationUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !stations.contains(keyOf(b))) return;
        e.setCancelled(true);
        openUpgrade(e.getPlayer());
    }

    // =====================================================================
    // Upgrade menu
    // =====================================================================

    private ItemStack item(Material m, String name, NamedTextColor color, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    void openUpgrade(Player p) {
        UpgradeHolder holder = new UpgradeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Power Upgrade Station", NamedTextColor.DARK_PURPLE));
        holder.inv = inv;
        fillUpgrade(inv, p);
        p.openInventory(inv);
    }

    private void fillUpgrade(Inventory inv, Player p) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK);
        for (int s = 0; s < 27; s++) inv.setItem(s, filler);

        AbilitySMP.Power pw = plugin.powerOf(p);
        int lvl = level(p, pw);
        int max = maxLevel();
        double pct = plugin.getConfig().getDouble("upgrade.power-per-level", 0.10) * 100;
        double cd = plugin.getConfig().getDouble("upgrade.cooldown-per-level", 0.10) * 100;

        if (pw == null) {
            inv.setItem(11, item(Material.BARRIER, "You have no power", NamedTextColor.RED));
            inv.setItem(UPGRADE_SLOT, item(Material.BARRIER, "Nothing to upgrade", NamedTextColor.RED));
        } else {
            inv.setItem(11, item(Material.NETHER_STAR, pw.key().toUpperCase().replace('-', ' ') + "  -  Level " + lvl + "/" + max,
                    NamedTextColor.GOLD,
                    "Each level: -" + (int) cd + "% cooldown,",
                    "+" + (int) pct + "% range, damage and duration"));
            if (lvl >= max) {
                inv.setItem(UPGRADE_SLOT, item(Material.NETHERITE_INGOT, "Max level reached!", NamedTextColor.GREEN));
            } else {
                int cost = costFor(lvl);
                boolean can = points(p) >= cost;
                inv.setItem(UPGRADE_SLOT, item(can ? Material.ANVIL : Material.BARRIER,
                        "Upgrade to level " + (lvl + 1), can ? NamedTextColor.GREEN : NamedTextColor.RED,
                        "Cost: " + cost + " Unbound points",
                        "You have: " + points(p),
                        can ? "Click to upgrade!" : "Not enough points yet"));
            }
        }
        inv.setItem(15, item(Material.EMERALD, "Your Unbound points: " + points(p), NamedTextColor.AQUA,
                "Earn them from quests (/quests)",
                "and every " + plugin.getConfig().getInt("points.play-minutes", 20) + " minutes you play."));
    }

    @EventHandler
    public void onUpgradeClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof UpgradeHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (e.getRawSlot() == UPGRADE_SLOT) {
            tryUpgrade(p);
            fillUpgrade(h.inv, p);
        }
    }

    @EventHandler
    public void onUpgradeDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof UpgradeHolder) e.setCancelled(true);
    }

    private void tryUpgrade(Player p) {
        AbilitySMP.Power pw = plugin.powerOf(p);
        if (pw == null) {
            p.sendMessage(Component.text("You don't have a power to upgrade.", NamedTextColor.RED));
            return;
        }
        int lvl = level(p, pw);
        if (lvl >= maxLevel()) {
            p.sendMessage(Component.text("Your power is already at max level.", NamedTextColor.GRAY));
            return;
        }
        int cost = costFor(lvl);
        PlayerData d = pd(p);
        if (d.points < cost) {
            p.sendMessage(Component.text("Not enough Unbound points: you need " + cost + ".", NamedTextColor.RED));
            return;
        }
        d.points -= cost;
        d.levels.put(pw.name(), lvl + 1);
        dirty = true;
        save();
        p.sendMessage(Component.text("Your " + pw.key().replace('-', ' ') + " power is now level " + (lvl + 1) + "!",
                NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.05);
    }

    // =====================================================================
    // Commands: /points  /quests  /station  /upgrade
    // =====================================================================

    void command(CommandSender s, String name, String[] args) {
        switch (name) {
            case "points" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
                p.sendMessage(Component.text("You have " + points(p) + " Unbound points. Your power is level "
                        + level(p) + "/" + maxLevel() + ".", NamedTextColor.AQUA));
            }
            case "quests" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
                PlayerData d = pd(p);
                p.sendMessage(Component.text("===== Quests (they reset when you finish them) =====", NamedTextColor.GOLD));
                ConfigurationSection qs = quests();
                if (qs != null) {
                    for (String id : qs.getKeys(false)) {
                        ConfigurationSection q = qs.getConfigurationSection(id);
                        if (q == null) continue;
                        p.sendMessage(Component.text(" - " + q.getString("name", id) + ": "
                                + d.progress.getOrDefault(id, 0) + "/" + q.getInt("target", 10)
                                + "  (+" + q.getInt("reward", 10) + " pts)  " + q.getString("description", ""),
                                NamedTextColor.YELLOW));
                    }
                }
                int need = plugin.getConfig().getInt("points.play-minutes", 20);
                p.sendMessage(Component.text("Playtime: " + d.minutes + "/" + need + " minutes to your next +"
                        + plugin.getConfig().getInt("points.play-reward", 10) + " points. Quests done so far: "
                        + d.completed, NamedTextColor.GRAY));
            }
            case "upgrade" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
                openUpgrade(p);
            }
            case "station" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
                String sub = args.length > 0 ? args[0].toLowerCase() : "add";
                switch (sub) {
                    case "add" -> addStation(p, p.getTargetBlockExact(6));
                    case "remove" -> {
                        Block b = p.getTargetBlockExact(6);
                        if (b != null && stations.remove(keyOf(b))) {
                            dirty = true;
                            save();
                            p.sendMessage(Component.text("Upgrade station removed.", NamedTextColor.GRAY));
                        } else {
                            p.sendMessage(Component.text("Look at an upgrade station first.", NamedTextColor.RED));
                        }
                    }
                    case "list" -> p.sendMessage(Component.text("Stations: " + stations.size()
                            + (stations.isEmpty() ? "" : "  " + String.join("  |  ", stations)), NamedTextColor.GRAY));
                    default -> p.sendMessage("Usage: /station <add|remove|list> (look at a block)");
                }
            }
            default -> { }
        }
    }
}
