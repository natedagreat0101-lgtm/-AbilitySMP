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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Item Shop (v12) - /itemshop. Two halves:
 *  - The legendary weapons from AltarSMP (see Weapons.java): ALWAYS in stock, forever.
 *    The weapons themselves never leave the game.
 *  - Daily deals: a rotating handful of normal items that restock every day
 *    (a fresh set is picked at midnight, America/Toronto). Stock counts down as
 *    players buy, then tomorrow brings a new set.
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

    /** Rolls a fresh set of daily deals whenever the (Toronto) date changes. */
    private void ensureDay() {
        String now = today();
        if (now.equals(day)) return;
        day = now;
        deals.clear();
        stock.clear();
        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("itemshop.daily");
        if (pool != null) {
            List<String> ids = new ArrayList<>(pool.getKeys(false));
            Collections.shuffle(ids, random);
            int want = plugin.getConfig().getInt("itemshop.daily-count", 6);
            for (String id : ids) {
                if (deals.size() >= want) break;
                if (!pool.getBoolean(id + ".enabled", true)) continue;
                deals.add(id);
                stock.put(id, Math.max(1, pool.getInt(id + ".stock", 3)));
            }
        }
        save();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("The Item Shop has new daily deals! /itemshop", NamedTextColor.GOLD));
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

    void open(Player p) {
        ensureDay();
        new Menus.Paged("Item Shop") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD,
                        "You have " + plugin.unbound.points(pl) + " Unbound Coins", false,
                        "The legendary weapons are ALWAYS in stock.",
                        "The daily deals restock every day (midnight Toronto time)."), null));

                out.add(new Menus.Entry(Menus.icon(Material.NETHERITE_SWORD, "⚔ LEGENDARY WEAPONS ⚔", true,
                        "From AltarSMP. They never leave the shop - or the game.",
                        "Right-click = ability 1, crouch + right-click = ability 2."), null));
                for (Weapons.WDef d : Weapons.WDef.values()) {
                    int price = d.price(plugin);
                    List<String> lore = new ArrayList<>();
                    lore.add("✦ LEGENDARY WEAPON ✦");
                    for (String l : d.lore) lore.add(l);
                    lore.add("Price: " + price + " Unbound Coins");
                    lore.add("Always in stock - click to buy");
                    out.add(new Menus.Entry(Menus.icon(d.material, d.display, true, lore.toArray(new String[0])),
                            (pp, t) -> buyWeapon(pp, d, price)));
                }

                out.add(new Menus.Entry(Menus.icon(Material.CLOCK, "📅 TODAY'S DEALS 📅", false,
                        "Restocks tomorrow. When it's gone, it's gone (until it rotates back)."), null));
                ConfigurationSection pool = plugin.getConfig().getConfigurationSection("itemshop.daily");
                if (pool == null || deals.isEmpty()) {
                    out.add(new Menus.Entry(Menus.icon(Material.BARRIER, "No deals today", false), null));
                } else {
                    for (String id : new ArrayList<>(deals)) {
                        ConfigurationSection q = pool.getConfigurationSection(id);
                        if (q == null) continue;
                        int price = q.getInt("price", 25);
                        int left = stock.getOrDefault(id, 0);
                        Material icon = Material.matchMaterial(q.getString("icon", "PAPER"));
                        if (icon == null) icon = Material.PAPER;
                        String name = q.getString("name", id);
                        if (left <= 0) {
                            out.add(new Menus.Entry(Menus.icon(Material.BARRIER, name + "  [SOLD OUT]", false,
                                    "Price: " + price + " Unbound Coins", "Back in a future daily rotation"), null));
                        } else {
                            out.add(new Menus.Entry(Menus.icon(icon, name, q.getBoolean("glint", false),
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

    private void buyWeapon(Player p, Weapons.WDef d, int price) {
        if (!plugin.unbound.spend(p, price)) {
            p.sendMessage(Component.text("You need " + price + " Unbound Coins. You have "
                    + plugin.unbound.points(p) + ".", NamedTextColor.RED));
            return;
        }
        plugin.give(p, plugin.weapons.create(d));
        p.sendMessage(Component.text("You bought the " + d.display + "! It is yours forever.", d.color));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
        open(p);
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
