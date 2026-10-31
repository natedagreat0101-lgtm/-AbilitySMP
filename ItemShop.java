package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Item Shop (v14) - /itemshop. The whole shop is a random daily shelf:
 * every day (midnight, America/Toronto) a fresh set of items is picked from
 * the config pool by weight - mostly mid stuff, sometimes good stuff, and
 * once in a while something OP (an elytra, a beacon, a notch apple...).
 * Stock is limited and counts down; tomorrow is a brand new roll.
 * Everything is priced in Unbound Coins. State lives in itemshop.yml.
 */
final class ItemShop implements Listener {

    private final AbilitySMP plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Random random = new Random();
    private String day = "";
    private final List<String> deals = new ArrayList<>(); // pool ids on sale today
    private final Map<String, Integer> stock = new HashMap<>(); // pool id -> left today

    ItemShop(AbilitySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "itemshop.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.day = data.getString("day", "");
        this.deals.addAll(data.getStringList("deals"));
        ConfigurationSection sec = data.getConfigurationSection("stock");
        if (sec != null) for (String id : sec.getKeys(false)) stock.put(id, sec.getInt(id));
        ensureDay();
    }

    private static String today() {
        return LocalDate.now(ZoneId.of("America/Toronto")).toString();
    }

    /** Rolls a fresh weighted-random shelf whenever the (Toronto) date changes. */
    private void ensureDay() {
        String now = today();
        if (now.equals(day)) return;
        day = now;
        deals.clear();
        stock.clear();
        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("itemshop.daily");
        if (pool != null) {
            List<String> ids = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            for (String id : pool.getKeys(false)) {
                if (!pool.getBoolean(id + ".enabled", true)) continue;
                ids.add(id);
                weights.add(Math.max(1, pool.getInt(id + ".weight", 50)));
            }
            int want = plugin.getConfig().getInt("itemshop.daily-count", 8);
            while (!ids.isEmpty() && deals.size() < want) {
                int total = 0;
                for (int w : weights) total += w;
                int roll = random.nextInt(total);
                int idx = 0;
                while (roll >= weights.get(idx)) {
                    roll -= weights.get(idx);
                    idx++;
                }
                String id = ids.remove(idx);
                weights.remove(idx);
                deals.add(id);
                int lo = Math.max(1, pool.getInt(id + ".min-stock", 1));
                int hi = Math.max(lo, pool.getInt(id + ".max-stock", pool.getInt(id + ".stock", 3)));
                stock.put(id, lo + random.nextInt(hi - lo + 1));
            }
        }
        save();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("The Item Shop rolled a new shelf for today! /itemshop", NamedTextColor.GOLD));
        }
    }

    private void save() {
        data.set("day", day);
        data.set("deals", deals);
        data.set("stock", null);
        for (Map.Entry<String, Integer> e : stock.entrySet()) data.set("stock." + e.getKey(), e.getValue());
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save itemshop.yml: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ensureDay();
    }

    private static NamedTextColor tierColor(String tier) {
        return switch (tier == null ? "mid" : tier.toLowerCase()) {
            case "op" -> NamedTextColor.GOLD;
            case "good" -> NamedTextColor.AQUA;
            default -> NamedTextColor.GRAY;
        };
    }

    void open(Player p) {
        ensureDay();
        new Menus.Paged("Item Shop") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD,
                        "You have " + plugin.unbound.points(pl) + " Unbound Coins", false,
                        "A brand new random shelf every day (midnight Toronto).",
                        "Mostly mid stuff, sometimes good stuff,",
                        "and once in a while something OP.",
                        "Stock is limited - when it's gone, it's gone."), null));
                ConfigurationSection pool = plugin.getConfig().getConfigurationSection("itemshop.daily");
                if (pool == null || deals.isEmpty()) {
                    out.add(new Menus.Entry(Menus.icon(Material.BARRIER, "Nothing on the shelf today", false), null));
                } else {
                    for (String id : new ArrayList<>(deals)) {
                        ConfigurationSection q = pool.getConfigurationSection(id);
                        if (q == null) continue;
                        int price = q.getInt("price", 25);
                        int left = stock.getOrDefault(id, 0);
                        Material icon = Material.matchMaterial(q.getString("icon", "PAPER"));
                        if (icon == null) icon = Material.PAPER;
                        String name = q.getString("name", id);
                        String tier = q.getString("tier", "mid");
                        if (left <= 0) {
                            out.add(new Menus.Entry(Menus.icon(Material.BARRIER, name + "  [SOLD OUT]", false,
                                    "Price: " + price + " Unbound Coins", "Back in a future daily rotation"), null));
                        } else {
                            out.add(new Menus.Entry(Menus.icon(icon,
                                    "✦ " + tier.toUpperCase() + " ✦  " + name, tier.equalsIgnoreCase("op"),
                                    q.getString("blurb", ""), "Price: " + price + " Unbound Coins",
                                    "In stock today: " + left, "Click to buy"),
                                    (pp, t) -> buyDeal(pp, id, q, price)));
                        }
                    }
                }
                return out;
            }
        }.open(p);
    }

    private void buyDeal(Player p, String id, ConfigurationSection q, int price) {
        ensureDay();
        if (stock.getOrDefault(id, 0) <= 0) {
            p.sendMessage(Component.text("Sold out for today!", NamedTextColor.RED));
            return;
        }
        if (!plugin.unbound.spend(p, price)) {
            p.sendMessage(Component.text("You need " + price + " Unbound Coins. You have "
                    + plugin.unbound.points(p) + ".", NamedTextColor.RED));
            return;
        }
        stock.put(id, stock.get(id) - 1);
        save();
        plugin.giveReward(p, q.getString("reward", ""));
        p.sendMessage(Component.text("You bought " + q.getString("name", id) + "!", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.1f);
        open(p);
    }
}
