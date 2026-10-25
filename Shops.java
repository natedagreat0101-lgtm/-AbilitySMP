package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * /power shop: reroll tokens, random powers, and one stocked power that changes every few minutes
 * (better powers cost more and show up less). The Halloween Shop spends Halloween Coins and only
 * rarely stocks a Halloween power. Shopkeeper villagers open these shops.
 */
final class Shops implements Listener {

    private static final class Slot {
        final AbilitySMP.Power power;
        int discount;
        boolean pinned;

        Slot(AbilitySMP.Power power, int discount, boolean pinned) {
            this.power = power;
            this.discount = discount;
            this.pinned = pinned;
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AbilitySMP plugin;
    private final File file;
    private final NamespacedKey eggKey;
    private final NamespacedKey keeperKey;
    private final List<Slot> pinned = new ArrayList<>();
    private final Map<String, Integer> bought = new HashMap<>();
    private final Random random = new Random();
    private Slot current;
    private Slot hwRare;
    private long nextRestock;

    Shops(AbilitySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        this.eggKey = new NamespacedKey(plugin, "shopkeeper_egg");
        this.keeperKey = new NamespacedKey(plugin, "shopkeeper");
        load();
        if (current == null || System.currentTimeMillis() >= nextRestock) restock(false);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private static String span(long secs) {
        if (secs < 0) secs = 0;
        long d = secs / 86400, h = (secs % 86400) / 3600, m = (secs % 3600) / 60, s = secs % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + s + "s";
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =====================================================================
    // Saving
    // =====================================================================

    private static String enc(Slot s) {
        return s == null ? "" : s.power.name() + ";" + s.discount + ";" + s.pinned;
    }

    private static Slot dec(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] a = text.split(";");
        AbilitySMP.Power p = a.length >= 3 ? AbilitySMP.Power.parse(a[0]) : null;
        return p == null ? null : new Slot(p, num(a[1]), Boolean.parseBoolean(a[2]));
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextRestock = y.getLong("next-restock", 0L);
        current = dec(y.getString("current"));
        hwRare = dec(y.getString("hw-rare"));
        for (String s : y.getStringList("pinned")) {
            Slot slot = dec(s);
            if (slot != null) pinned.add(slot);
        }
        for (String s : y.getStringList("bought")) {
            String[] a = s.split(";");
            if (a.length == 3) bought.put(a[0] + ";" + a[1], num(a[2]));
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-restock", nextRestock);
        y.set("current", enc(current));
        y.set("hw-rare", enc(hwRare));
        List<String> ps = new ArrayList<>();
        for (Slot s : pinned) ps.add(enc(s));
        y.set("pinned", ps);
        List<String> bs = new ArrayList<>();
        for (Map.Entry<String, Integer> e : bought.entrySet()) bs.add(e.getKey() + ";" + e.getValue());
        y.set("bought", bs);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save shop.yml: " + ex.getMessage());
        }
    }

    // =====================================================================
    // Prices and stocking
    // =====================================================================

    private int coinPrice(AbilitySMP.Power p) {
        return cfg("halloween-shop.power-prices." + p.key(), 0);
    }

    /** Unbound Coins for normal powers, Halloween Coins for Halloween powers. */
    int basePrice(AbilitySMP.Power p) {
        return p.isHalloween() ? coinPrice(p) : cfg("shop.prices." + p.key(), 0);
    }

    private int finalPrice(Slot s) {
        return Math.max(1, (int) Math.ceil(basePrice(s.power) * (100 - s.discount) / 100.0));
    }

    private List<AbilitySMP.Power> pool(boolean halloween) {
        List<AbilitySMP.Power> out = new ArrayList<>();
        for (AbilitySMP.Power p : AbilitySMP.Power.values()) {
            if (plugin.isEnabled(p) && p.isHalloween() == halloween && basePrice(p) > 0) out.add(p);
        }
        return out;
    }

    private void tick() {
        if (System.currentTimeMillis() >= nextRestock) restock(true);
    }

    /** One new power is stocked. Cheaper powers come up more often. A Halloween power is rare. */
    void restock(boolean announce) {
        List<AbilitySMP.Power> normal = pool(false);
        AbilitySMP.Power pick = null;
        if (!normal.isEmpty()) {
            double total = 0;
            for (AbilitySMP.Power p : normal) total += 1.0 / basePrice(p);
            double r = random.nextDouble() * total;
            for (AbilitySMP.Power p : normal) {
                r -= 1.0 / basePrice(p);
                if (r <= 0) {
                    pick = p;
                    break;
                }
            }
            if (pick == null) pick = normal.get(normal.size() - 1);
            if (current != null && pick == current.power && normal.size() > 1) {
                pick = normal.get((normal.indexOf(pick) + 1) % normal.size()); // not the same power twice in a row
            }
        }
        current = pick == null ? null : new Slot(pick, 0, false);

        boolean rareAppeared = false;
        if (hwRare == null || !hwRare.pinned) {
            hwRare = null;
            List<AbilitySMP.Power> rare = pool(true);
            if (!rare.isEmpty() && random.nextDouble() < plugin.getConfig().getDouble("halloween-shop.power-chance", 0.10)) {
                hwRare = new Slot(rare.get(random.nextInt(rare.size())), 0, false);
                rareAppeared = true;
            }
        }
        nextRestock = System.currentTimeMillis() + Math.max(1, cfg("shop.restock-minutes", 5)) * 60_000L;
        save();

        if (!announce) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (current != null) {
                p.sendActionBar(Component.text(AbilitySMP.pretty(current.power.key()) + " was stocked in the shop! /power shop",
                        NamedTextColor.GOLD));
            }
            if (rareAppeared) {
                p.showTitle(Title.title(Component.text("A rare " + AbilitySMP.pretty(hwRare.power.key()) + " power appeared!",
                        NamedTextColor.GOLD), Component.text("In the Halloween Shop: /halloweenshop", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
            }
        }
    }

    private Slot findPinned(AbilitySMP.Power p) {
        for (Slot s : pinned) if (s.power == p) return s;
        return null;
    }

    /** Admin: stock a power (optionally discounted) and tell everyone at the top of the screen. */
    void stockPower(CommandSender who, AbilitySMP.Power pw, int percent) {
        if (basePrice(pw) <= 0) {
            who.sendMessage("That power has no price in config.yml (shop.prices or halloween-shop.power-prices).");
            return;
        }
        int pct = Math.max(0, Math.min(90, percent));
        if (pw.isHalloween()) {
            hwRare = new Slot(pw, pct, true);
        } else {
            Slot s = findPinned(pw);
            if (s == null) pinned.add(0, new Slot(pw, pct, true));
            else s.discount = pct;
            if (current != null && current.power == pw) current.discount = pct;
        }
        save();
        String name = AbilitySMP.pretty(pw.key());
        String where = pw.isHalloween() ? "the Halloween Shop" : "the shop";
        String cmd = pw.isHalloween() ? "/halloweenshop" : "/power shop";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Component.text(name + " has been restocked to " + where + "!", NamedTextColor.GOLD),
                    Component.text(pct > 0 ? pct + "% OFF - type " + cmd : "Type " + cmd, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
            p.sendActionBar(Component.text(name + " has been restocked to " + where + "!", NamedTextColor.GOLD));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        who.sendMessage("Stocked " + name + (pct > 0 ? " at " + pct + "% off." : "."));
    }

    private void unstock(CommandSender who, AbilitySMP.Power pw) {
        Slot s = findPinned(pw);
        if (s != null) pinned.remove(s);
        if (hwRare != null && hwRare.power == pw) hwRare = null;
        if (current != null && current.power == pw) current.discount = 0;
        save();
        who.sendMessage("Removed the admin stock of " + AbilitySMP.pretty(pw.key()) + ".");
    }

    // =====================================================================
    // /power shop
    // =====================================================================

    private Menus.Entry slotEntry(Player pl, Slot s, String tag) {
        String name = AbilitySMP.pretty(s.power.key());
        boolean coins = s.power.isHalloween();
        if (plugin.profile.owns(pl, s.power)) {
            return new Menus.Entry(Menus.icon(Material.NETHER_STAR, name + "  [OWNED]", true,
                    PowerMenus.describe(s.power), "Equip it in /powers"), null);
        }
        String unit = coins ? " Halloween Coins" : " Unbound Coins";
        String price = s.discount > 0
                ? "Price: " + finalPrice(s) + unit + " (was " + basePrice(s.power) + ", " + s.discount + "% OFF)"
                : "Price: " + finalPrice(s) + unit;
        return new Menus.Entry(Menus.icon(coins ? Material.NETHER_STAR : Material.CHEST,
                name + (s.discount > 0 ? "  [SALE]" : ""), s.discount > 0 || coins,
                PowerMenus.describe(s.power), tag, price, "Click to buy"), (pp, t) -> buySlot(pp, s));
    }

    void openPowerShop(Player p) {
        new Menus.Paged("Power Shop") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                long left = (nextRestock - System.currentTimeMillis()) / 1000L;
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD, "You have " + plugin.unbound.points(pl) + " Unbound Coins",
                        false, "A new power is stocked in " + span(left),
                        "Rerolls are how you get new powers."), null));
                out.add(new Menus.Entry(Menus.icon(Material.AMETHYST_SHARD, "Reroll Token", true,
                        "Right-click it for a random power you don't own.",
                        "Price: " + cfg("shop.reroll-price", 60) + " Unbound Coins", "Click to buy"), (pp, t) -> buyReroll(pp)));
                out.add(new Menus.Entry(Menus.icon(Material.NETHER_STAR, "Random Power", true,
                        "Unlocks and equips a random power you don't own, right now.",
                        "Price: " + cfg("shop.random-power-price", 90) + " Unbound Coins", "Click to buy"), (pp, t) -> buyRandom(pp)));
                if (current != null) out.add(slotEntry(pl, current, "Stocked right now (changes every few minutes)"));
                for (Slot s : new ArrayList<>(pinned)) out.add(slotEntry(pl, s, "Stocked by an admin"));
                return out;
            }
        }.open(p);
    }

    private void buyReroll(Player p) {
        int price = cfg("shop.reroll-price", 60);
        if (!plugin.unbound.spend(p, price)) {
            p.sendMessage(Component.text("You need " + price + " Unbound Coins. You have " + plugin.unbound.points(p) + ".",
                    NamedTextColor.RED));
            return;
        }
        plugin.giveReward(p, "reroll:1");
        p.sendMessage(Component.text("You bought a Reroll Token. Right-click it to get a random power!", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    private void buyRandom(Player p) {
        int price = cfg("shop.random-power-price", 90);
        AbilitySMP.Power r = plugin.randomUnowned(p);
        if (r == null) {
            p.sendMessage(Component.text("You already own every power that can be rolled.", NamedTextColor.GRAY));
            return;
        }
        if (!plugin.unbound.spend(p, price)) {
            p.sendMessage(Component.text("You need " + price + " Unbound Coins. You have " + plugin.unbound.points(p) + ".",
                    NamedTextColor.RED));
            return;
        }
        plugin.unlockAndEquip(p, r);
    }

    private void buySlot(Player p, Slot s) {
        if (plugin.profile.owns(p, s.power)) {
            p.sendMessage(Component.text("You already own that power.", NamedTextColor.GRAY));
            return;
        }
        boolean coins = s.power.isHalloween();
        boolean stillThere = s == current || s == hwRare || pinned.contains(s);
        if (!stillThere) {
            p.sendMessage(Component.text("That power was just restocked away. Check the shop again.", NamedTextColor.RED));
            return;
        }
        int price = finalPrice(s);
        int have = coins ? plugin.tot.countCoins(p) : plugin.unbound.points(p);
        if (have < price) {
            p.sendMessage(Component.text("You need " + price + (coins ? " Halloween Coins" : " Unbound Coins")
                    + ". You have " + have + ".", NamedTextColor.RED));
            return;
        }
        if (coins) plugin.tot.removeCoins(p, price);
        else plugin.unbound.spend(p, price);
        plugin.profile.addOwned(p.getUniqueId(), s.power);
        plugin.profile.save();
        plugin.ranks.evaluate(p);
        p.sendMessage(Component.text("You bought the " + AbilitySMP.pretty(s.power.key()) + " power! Equip it in /powers.",
                NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
    }

    void openStockManager(Player admin) {
        new Menus.Paged("Power Shop Manager") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                out.add(new Menus.Entry(Menus.icon(Material.BOOK, "How to use", false,
                        "Left-click: stock at full price", "Right-click: stock at 25% off",
                        "Shift + left-click: stock at 50% off", "Shift + right-click: remove the admin stock",
                        "Halloween powers go in the Halloween Shop.",
                        "Everyone sees it at the top of the screen."), null));
                for (AbilitySMP.Power pw : AbilitySMP.Power.values()) {
                    if (!plugin.isEnabled(pw) || basePrice(pw) <= 0) continue;
                    Slot s = pw.isHalloween() ? (hwRare != null && hwRare.power == pw ? hwRare : null) : findPinned(pw);
                    String state = s == null ? (current != null && current.power == pw ? "Stocked by the timer right now"
                            : "Not stocked") : "Stocked by an admin" + (s.discount > 0 ? ", " + s.discount + "% off" : "");
                    out.add(new Menus.Entry(Menus.icon(Material.NETHER_STAR, AbilitySMP.pretty(pw.key()), s != null,
                            "Base price: " + basePrice(pw) + (pw.isHalloween() ? " Halloween Coins" : " Unbound Coins"), state),
                            (pp, type) -> {
                                switch (type) {
                                    case LEFT -> stockPower(pp, pw, 0);
                                    case RIGHT -> stockPower(pp, pw, 25);
                                    case SHIFT_LEFT -> stockPower(pp, pw, 50);
                                    case SHIFT_RIGHT -> unstock(pp, pw);
                                    default -> { }
                                }
                            }));
                }
                return out;
            }
        }.open(admin);
    }

    // =====================================================================
    // Halloween Shop (Halloween Coins). Halloween powers are rare here.
    // =====================================================================

    private ZonedDateTime limitedUntil() {
        try {
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("halloween-shop.timezone", "America/Toronto"));
            return LocalDateTime.parse(plugin.getConfig().getString("halloween-shop.limited.until", "2026-11-02 00:00"), FMT)
                    .atZone(zone);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean limitedActive() {
        ZonedDateTime u = limitedUntil();
        return u != null && ZonedDateTime.now(u.getZone()).isBefore(u);
    }

    private int boughtCount(Player p, String id) {
        return bought.getOrDefault(p.getUniqueId() + ";" + id, 0);
    }

    void openHalloweenShop(Player p) {
        new Menus.Paged("Halloween Shop") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                boolean limited = limitedActive();
                ZonedDateTime until = limitedUntil();
                out.add(new Menus.Entry(Menus.icon(Material.GOLD_NUGGET, "You have " + plugin.tot.countCoins(pl) + " Halloween Coins",
                        true, "Earn coins from Trick or Treat Night and the Headless Horseman.",
                        limited && until != null ? "Limited-time items end in "
                                + span(Duration.between(ZonedDateTime.now(until.getZone()), until).getSeconds())
                                : "No limited-time items right now"), null));
                if (hwRare != null) {
                    out.add(slotEntry(pl, hwRare, "RARE: it will not be here for long"));
                } else {
                    out.add(new Menus.Entry(Menus.icon(Material.BARRIER, "No rare Halloween power right now", false,
                            "Ghost and Vampire are rare. Each restock has a small chance to stock one.",
                            "Next restock in " + span((nextRestock - System.currentTimeMillis()) / 1000L)), null));
                }
                if (limited) addItems(out, pl, "halloween-shop.limited.items", true);
                addItems(out, pl, "halloween-shop.items", false);
                return out;
            }
        }.open(p);
    }

    private void addItems(List<Menus.Entry> out, Player pl, String path, boolean limited) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection q = sec.getConfigurationSection(id);
            if (q == null) continue;
            int price = q.getInt("price", 10);
            int limit = q.getInt("limit", 0);
            int have = boughtCount(pl, id);
            Material icon = Material.matchMaterial(q.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;
            String name = q.getString("name", id) + (limited ? "  [LIMITED TIME]" : "");
            boolean soldOut = limit > 0 && have >= limit;
            out.add(new Menus.Entry(Menus.icon(soldOut ? Material.BARRIER : icon, name, limited && !soldOut,
                    "Price: " + price + " Halloween Coins",
                    limit > 0 ? "You can buy this " + limit + " time(s). Bought: " + have : "No purchase limit",
                    soldOut ? "You already bought the maximum" : "Click to buy"),
                    soldOut ? null : (pp, t) -> buyHalloween(pp, id, q)));
        }
    }

    private void buyHalloween(Player p, String id, ConfigurationSection q) {
        int price = q.getInt("price", 10);
        int limit = q.getInt("limit", 0);
        if (limit > 0 && boughtCount(p, id) >= limit) {
            p.sendMessage(Component.text("You already bought the maximum of that.", NamedTextColor.GRAY));
            return;
        }
        if (plugin.tot.countCoins(p) < price) {
            p.sendMessage(Component.text("You need " + price + " Halloween Coins. You have " + plugin.tot.countCoins(p) + ".",
                    NamedTextColor.RED));
            return;
        }
        plugin.tot.removeCoins(p, price);
        plugin.giveReward(p, q.getString("reward", ""));
        bought.merge(p.getUniqueId() + ";" + id, 1, Integer::sum);
        save();
        plugin.ranks.evaluate(p);
        p.sendMessage(Component.text("You bought " + q.getString("name", id) + "!", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.1f);
    }

    // =====================================================================
    // Shopkeeper spawn eggs and villagers
    // =====================================================================

    private ItemStack egg(String kind) {
        boolean hw = kind.equals("halloween");
        ItemStack it = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta m = it.getItemMeta();
        m.displayName(Menus.white(hw ? "Halloween Shopkeeper Spawn Egg" : "Shopkeeper Spawn Egg"));
        m.lore(List.of(Menus.white(hw ? "Places a villager that opens the Halloween Shop." : "Places a villager that opens the Power Shop."),
                Menus.white("Right-click the ground to place it.")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, kind);
        it.setItemMeta(m);
        return it;
    }

    private String eggKind(ItemStack it) {
        if (it == null || it.getType() != Material.VILLAGER_SPAWN_EGG || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
    }

    private void spawnKeeper(Location at, String kind, Location lookAt) {
        boolean hw = kind.equals("halloween");
        Vector face = lookAt.toVector().subtract(at.toVector()).setY(0);
        if (face.lengthSquared() > 1.0E-6) at.setDirection(face);
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setProfession(hw ? Villager.Profession.CLERIC : Villager.Profession.LIBRARIAN);
            v.customName(Component.text(hw ? "Halloween Shopkeeper" : "Shopkeeper", hw ? NamedTextColor.GOLD : NamedTextColor.GREEN));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(keeperKey, PersistentDataType.STRING, kind);
        });
    }

    @EventHandler
    public void onEgg(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        String kind = eggKind(e.getItem());
        if (kind == null) return;
        e.setCancelled(true); // no ordinary villager
        Player p = e.getPlayer();
        Location at = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        spawnKeeper(at, kind, p.getLocation());
        if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
    }

    @EventHandler
    public void onKeeper(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        String kind = v.getPersistentDataContainer().get(keeperKey, PersistentDataType.STRING);
        if (kind == null) return;
        e.setCancelled(true); // no villager trading menu
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (kind.equals("halloween")) openHalloweenShop(e.getPlayer());
        else openPowerShop(e.getPlayer());
    }

    /** /shopkeeper egg <power|halloween> [player] [amount]   and   /shopkeeper remove */
    void keeperCommand(CommandSender s, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("egg")) {
            String kind = args[1].equalsIgnoreCase("halloween") ? "halloween" : "power";
            Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player pl ? pl : null);
            if (target == null) {
                s.sendMessage("Player not found.");
                return;
            }
            ItemStack e = egg(kind);
            e.setAmount(Math.max(1, Math.min(64, args.length > 3 ? num(args[3]) : 1)));
            plugin.give(target, e);
            s.sendMessage("Gave a " + kind + " shopkeeper egg to " + target.getName() + ".");
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("remove") && s instanceof Player p) {
            for (Entity en : p.getNearbyEntities(6, 6, 6)) {
                if (en instanceof Villager v && v.getPersistentDataContainer().has(keeperKey, PersistentDataType.STRING)) {
                    v.remove();
                    p.sendMessage("Removed the nearest shopkeeper.");
                    return;
                }
            }
            p.sendMessage("No shopkeeper within 6 blocks.");
        } else {
            s.sendMessage("Usage: /shopkeeper egg <power|halloween> [player] [amount]   or   /shopkeeper remove");
        }
    }

    // =====================================================================
    // /give shopkeeper_spawn_egg   and   /give halloween_shopkeeper_spawn_egg
    // =====================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGivePlayer(PlayerCommandPreprocessEvent e) {
        if (handleGive(e.getPlayer(), e.getMessage().substring(1))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGiveConsole(ServerCommandEvent e) {
        if (handleGive(e.getSender(), e.getCommand())) e.setCancelled(true);
    }

    private boolean handleGive(CommandSender sender, String line) {
        String[] a = line.trim().split("\\s+");
        if (a.length < 3) return false;
        String cmd = a[0].toLowerCase();
        if (cmd.startsWith("minecraft:")) cmd = cmd.substring(10);
        if (!cmd.equals("give")) return false;
        String item = a[2].toLowerCase().replace("abilitysmp:", "").replace("minecraft:", "");
        String kind = item.equals("shopkeeper_spawn_egg") ? "power"
                : item.equals("halloween_shopkeeper_spawn_egg") ? "halloween" : null;
        if (kind == null || !sender.hasPermission("abilitysmp.admin")) return false;

        int count = Math.max(1, Math.min(64, a.length > 3 ? num(a[3]) : 1));
        List<Entity> targets;
        try {
            targets = Bukkit.selectEntities(sender, a[1]);
        } catch (IllegalArgumentException ex) {
            Player named = Bukkit.getPlayerExact(a[1]);
            targets = named == null ? new ArrayList<>() : new ArrayList<>(List.of(named));
        }
        int given = 0;
        for (Entity t : targets) {
            if (t instanceof Player pl) {
                ItemStack e = egg(kind);
                e.setAmount(count);
                plugin.give(pl, e);
                given++;
            }
        }
        sender.sendMessage(given == 0 ? "No player found." : "Gave " + count + " " + item + " to " + given + " player(s).");
        return true;
    }
}
