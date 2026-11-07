package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v15.5 THE CAVE UPDATE: Cave Coins, cave quests and the /daily reward.
 *
 * Cave Coins are the cave economy: you earn them by mining ores down in the
 * caves (below Y=20), finishing cave quests, beating cave bosses and claiming
 * /daily, and you spend them in the Cave Shop (/caveshop) on cave weapons
 * and cave loot. Balances, quest progress and daily claims live in
 * cave.yml. Placed ores do not pay out (the plugin remembers blocks you
 * placed while the server is running), so coins have to come from real caving.
 */
final class Cave implements Listener {

    // ------------------------------------------------------------ quests

    record Quest(String id, String name, String verb, int target, int reward) {
    }

    static final List<Quest> QUEST_POOL = List.of(
            new Quest("deepslate", "Deep Delver", "Mine deepslate blocks", 200, 40),
            new Quest("coal", "Coal Runner", "Mine coal ore", 48, 25),
            new Quest("iron", "Iron Hunter", "Mine iron ore", 24, 35),
            new Quest("gold", "Gold Rush", "Mine gold ore", 12, 45),
            new Quest("diamond", "Diamond Diver", "Mine diamond ore", 4, 60),
            new Quest("spiders", "Spider Slayer", "Kill spiders", 15, 35),
            new Quest("zombies", "Cave Cleaner", "Kill zombies", 20, 30),
            new Quest("boss", "Boss Hunter", "Beat a cave boss", 1, 80),
            new Quest("earn", "Cave Rich", "Earn Cave Coins", 120, 50));

    private static final Map<Material, Integer> ORE_PAY = new HashMap<>();

    static {
        ORE_PAY.put(Material.COAL_ORE, 1);
        ORE_PAY.put(Material.DEEPSLATE_COAL_ORE, 1);
        ORE_PAY.put(Material.COPPER_ORE, 1);
        ORE_PAY.put(Material.DEEPSLATE_COPPER_ORE, 1);
        ORE_PAY.put(Material.REDSTONE_ORE, 1);
        ORE_PAY.put(Material.DEEPSLATE_REDSTONE_ORE, 1);
        ORE_PAY.put(Material.IRON_ORE, 2);
        ORE_PAY.put(Material.DEEPSLATE_IRON_ORE, 2);
        ORE_PAY.put(Material.LAPIS_ORE, 2);
        ORE_PAY.put(Material.DEEPSLATE_LAPIS_ORE, 2);
        ORE_PAY.put(Material.GOLD_ORE, 3);
        ORE_PAY.put(Material.DEEPSLATE_GOLD_ORE, 3);
        ORE_PAY.put(Material.NETHER_GOLD_ORE, 3);
        ORE_PAY.put(Material.DIAMOND_ORE, 6);
        ORE_PAY.put(Material.DEEPSLATE_DIAMOND_ORE, 6);
        ORE_PAY.put(Material.EMERALD_ORE, 10);
        ORE_PAY.put(Material.DEEPSLATE_EMERALD_ORE, 10);
        ORE_PAY.put(Material.ANCIENT_DEBRIS, 15);
    }

    private final AbilitySMP plugin;
    private final File file;
    private YamlConfiguration yaml;
    private boolean dirty;
    /** Blocks placed by players this session - placed ores never pay out. */
    private final Set<String> placed = new HashSet<>();

    Cave(AbilitySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cave.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 600L, 600L);
    }

    private void load() {
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    void save() {
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save cave.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ balances

    int coins(UUID id) {
        return yaml.getInt("balances." + id, 0);
    }

    int coins(Player p) {
        return coins(p.getUniqueId());
    }

    void addCoins(Player p, int amount, String reason) {
        if (amount == 0) return;
        UUID id = p.getUniqueId();
        yaml.set("balances." + id, Math.max(0, coins(id) + amount));
        dirty = true;
        if (amount > 0) {
            p.sendActionBar(Component.text("+" + amount + " Cave Coins (" + reason + ")",
                    NamedTextColor.GOLD));
            progress(p, "earn", amount, false);
        }
    }

    void addCoins(UUID id, int amount) {
        yaml.set("balances." + id, Math.max(0, coins(id) + amount));
        dirty = true;
    }

    boolean spend(Player p, int cost) {
        if (cost <= 0) return true;
        if (coins(p) < cost) return false;
        yaml.set("balances." + p.getUniqueId(), coins(p) - cost);
        dirty = true;
        return true;
    }

    // ------------------------------------------------------------ mining

    private static String key(org.bukkit.block.Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (ORE_PAY.containsKey(e.getBlock().getType())) placed.add(key(e.getBlock()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Material m = e.getBlock().getType();
        boolean wasPlaced = placed.remove(key(e.getBlock()));
        if (m == Material.DEEPSLATE || m == Material.COBBLED_DEEPSLATE
                || m == Material.POLISHED_DEEPSLATE || m == Material.DEEPSLATE_BRICKS) {
            progress(p, "deepslate", 1, true);
        }
        Integer pay = ORE_PAY.get(m);
        if (pay == null || wasPlaced) return;
        if (e.getBlock().getY() > plugin.getConfig().getInt("cave.max-ore-y", 20)) return;
        addCoins(p, pay, "mining");
        switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> progress(p, "coal", 1, true);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> progress(p, "iron", 1, true);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> progress(p, "gold", 1, true);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> progress(p, "diamond", 1, true);
            default -> {
            }
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        EntityType t = e.getEntityType();
        if (t == EntityType.SPIDER || t == EntityType.CAVE_SPIDER) progress(killer, "spiders", 1, true);
        if (t == EntityType.ZOMBIE || t == EntityType.ZOMBIE_VILLAGER || t == EntityType.HUSK
                || t == EntityType.DROWNED) {
            progress(killer, "zombies", 1, true);
        }
    }

    // ------------------------------------------------------------ daily quests

    private String today() {
        return LocalDate.now().toString();
    }

    /** Today's 3 quests for this player (same 3 all day, different per player). */
    List<Quest> dailyQuests(Player p) {
        List<Quest> out = new ArrayList<>();
        int seed = (p.getUniqueId().hashCode() ^ today().hashCode());
        List<Quest> pool = new ArrayList<>(QUEST_POOL);
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
            out.add(pool.remove(r.nextInt(pool.size())));
        }
        return out;
    }

    private String qpath(Player p, Quest q, String field) {
        return "quests." + p.getUniqueId() + "." + today() + "." + q.id() + "." + field;
    }

    void progress(Player p, String questId, int amount, boolean countEarn) {
        for (Quest q : dailyQuests(p)) {
            if (!q.id().equals(questId)) continue;
            if (yaml.getBoolean(qpath(p, q, "done"), false)) continue;
            int now = yaml.getInt(qpath(p, q, "progress"), 0) + amount;
            yaml.set(qpath(p, q, "progress"), now);
            dirty = true;
            if (now >= q.target()) {
                yaml.set(qpath(p, q, "done"), true);
                dirty = true;
                p.sendMessage(Component.text("Cave quest done: " + q.name() + " - +" + q.reward()
                        + " Cave Coins!", NamedTextColor.GOLD));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                addCoins(p, q.reward(), "cave quest");
            }
        }
        if (countEarn) {
            // 'earn' is fed from addCoins itself, nothing extra to do here
        }
    }

    // ------------------------------------------------------------ commands

    void hubCommand(Player p) {
        p.sendMessage(Component.text("===== ⛏ THE CAVE UPDATE ⛏ =====", NamedTextColor.GOLD));
        p.sendMessage(Component.text("Cave Coins: " + coins(p), NamedTextColor.YELLOW));
        p.sendMessage(Component.text("Earn them: mine ores below Y="
                + plugin.getConfig().getInt("cave.max-ore-y", 20)
                + ", do /cavequests, beat /caveboss, claim /daily.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/caveshop - spend Cave Coins on cave weapons and cave loot",
                NamedTextColor.YELLOW));
        p.sendMessage(Component.text("/caveboss - wake a cave boss deep down for big Cave Coins",
                NamedTextColor.YELLOW));
        p.sendMessage(Component.text("/caveweapons - see the custom cave weapons", NamedTextColor.YELLOW));
    }

    void coinsCommand(Player p) {
        p.sendMessage(Component.text("You have " + coins(p) + " Cave Coins. "
                + "Spend them in /caveshop.", NamedTextColor.GOLD));
    }

    void questsCommand(Player p) {
        p.sendMessage(Component.text("===== ⛏ TODAY'S CAVE QUESTS ⛏ =====", NamedTextColor.GOLD));
        for (Quest q : dailyQuests(p)) {
            boolean done = yaml.getBoolean(qpath(p, q, "done"), false);
            int prog = Math.min(q.target(), yaml.getInt(qpath(p, q, "progress"), 0));
            if (done) {
                p.sendMessage(Component.text(" ✔ " + q.name() + " - DONE (+" + q.reward() + " Cave Coins)",
                        NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text(" ✦ " + q.name() + ": " + q.verb() + " - " + prog + "/"
                        + q.target() + " (+" + q.reward() + " Cave Coins)", NamedTextColor.YELLOW));
            }
        }
        p.sendMessage(Component.text("New quests every day. Your Cave Coins: " + coins(p),
                NamedTextColor.GRAY));
    }

    void dailyCommand(Player p) {
        String path = "daily." + p.getUniqueId();
        if (today().equals(yaml.getString(path, ""))) {
            p.sendMessage(Component.text("You already claimed today's /daily. Come back tomorrow!",
                    NamedTextColor.RED));
            return;
        }
        yaml.set(path, today());
        dirty = true;
        int unboundAmt = plugin.getConfig().getInt("cave.daily-unbound", 50);
        int caveAmt = plugin.getConfig().getInt("cave.daily-cave-coins", 25);
        plugin.unbound.addPoints(p, unboundAmt, "daily");
        addCoins(p, caveAmt, "daily");
        p.sendMessage(Component.text("Daily reward: " + unboundAmt + " Unbound Coins and "
                + caveAmt + " Cave Coins. See you tomorrow!", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }
}
