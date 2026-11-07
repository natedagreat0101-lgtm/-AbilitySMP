package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v15: the Player Shop. Players list the item in their hand for Unbound or
 * Galaxy Coins (/playershop sell <price> [galaxy]); everyone browses and
 * buys with /playershop. Items sit in escrow inside playershop.yml, and if
 * the seller is offline when something sells, the coins wait for them and
 * are paid out the next time they join.
 */
final class PlayerShop implements Listener {

    private final AbilitySMP plugin;
    private final File file;
    private final YamlConfiguration data;

    PlayerShop(AbilitySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playershop.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save playershop.yml: " + e.getMessage());
        }
    }

    void command(Player p, String[] args) {
        if (args.length == 0) {
            open(p, false);
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "sell" -> sell(p, args);
            case "mine" -> open(p, true);
            default -> {
                p.sendMessage(Component.text("Player Shop:", NamedTextColor.GOLD));
                p.sendMessage(Component.text("  /playershop - browse everything for sale", NamedTextColor.GRAY));
                p.sendMessage(Component.text("  /playershop mine - your listings (click one to take it back)", NamedTextColor.GRAY));
                p.sendMessage(Component.text("  /playershop sell <price> [unbound|galaxy] - sell the item in your hand", NamedTextColor.GRAY));
            }
        }
    }

    private void sell(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("Usage: /playershop sell <price> [unbound|galaxy] - hold the item you want to sell.", NamedTextColor.RED));
            return;
        }
        int price;
        try {
            price = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            price = 0;
        }
        if (price <= 0) {
            p.sendMessage(Component.text("The price has to be at least 1.", NamedTextColor.RED));
            return;
        }
        String currency = args.length > 2 && args[2].toLowerCase(java.util.Locale.ROOT).startsWith("galaxy")
                ? "galaxy" : "unbound";
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            p.sendMessage(Component.text("Hold the item you want to sell in your hand first.", NamedTextColor.RED));
            return;
        }
        if (plugin.isNeedle(hand) || plugin.isScythe(hand) || plugin.isActivator(hand)) {
            p.sendMessage(Component.text("Soulbound gear can't be sold in the Player Shop.", NamedTextColor.RED));
            return;
        }
        if (plugin.powerOfStar(hand) != null) {
            p.sendMessage(Component.text("Power needles can't be sold in the Player Shop.", NamedTextColor.RED));
            return;
        }
        int max = plugin.getConfig().getInt("playershop.max-listings", 10);
        if (countListings(p.getUniqueId()) >= max) {
            p.sendMessage(Component.text("You already have " + max + " things for sale. That's the max.", NamedTextColor.RED));
            return;
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        String base = "listings." + id;
        data.set(base + ".seller", p.getUniqueId().toString());
        data.set(base + ".seller-name", p.getName());
        data.set(base + ".item", hand.clone());
        data.set(base + ".price", price);
        data.set(base + ".currency", currency);
        save();
        p.getInventory().setItemInMainHand(null);
        p.sendMessage(Component.text("Listed for " + price + " " + (currency.equals("galaxy") ? "Galaxy" : "Unbound")
                + " Coins! See it with /playershop mine.", NamedTextColor.GREEN));
    }

    private int countListings(UUID seller) {
        int n = 0;
        if (data.isConfigurationSection("listings")) {
            for (String id : data.getConfigurationSection("listings").getKeys(false)) {
                if (seller.toString().equals(data.getString("listings." + id + ".seller"))) n++;
            }
        }
        return n;
    }

    void open(Player p, boolean mine) {
        new Menus.Paged(mine ? "My Listings" : "Player Shop") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD,
                        "Unbound Coins: " + plugin.unbound.points(pl) + "   Galaxy Coins: " + plugin.shops.countGalaxyCoins(pl),
                        false,
                        mine ? "Click one of your listings to take it back." : "Click an item to buy it.",
                        "Sell the item in your hand: /playershop sell <price> [galaxy]"), null));
                if (data.isConfigurationSection("listings")) {
                    for (String id : data.getConfigurationSection("listings").getKeys(false)) {
                        String base = "listings." + id;
                        UUID seller = UUID.fromString(data.getString(base + ".seller", ""));
                        if (mine && !seller.equals(pl.getUniqueId())) continue;
                        ItemStack item = data.getItemStack(base + ".item");
                        if (item == null) continue;
                        int price = data.getInt(base + ".price");
                        String currency = data.getString(base + ".currency", "unbound");
                        String sellerName = data.getString(base + ".seller-name", "?");
                        boolean own = seller.equals(pl.getUniqueId());
                        ItemStack icon = item.clone();
                        ItemMeta m = icon.getItemMeta();
                        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                        lore.add(Component.text("Price: " + price + " " + (currency.equals("galaxy") ? "Galaxy" : "Unbound") + " Coins",
                                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                        lore.add(Component.text("Seller: " + sellerName, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
                        lore.add(Component.text(own ? "Click to take it back" : "Click to buy", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false));
                        m.lore(lore);
                        icon.setItemMeta(m);
                        out.add(new Menus.Entry(icon, (pp, type) -> {
                            if (own) takeBack(pp, id);
                            else buy(pp, id);
                        }));
                    }
                }
                return out;
            }
        }.open(p);
    }

    private void takeBack(Player p, String id) {
        String base = "listings." + id;
        ItemStack item = data.getItemStack(base + ".item");
        if (item == null) {
            p.sendMessage(Component.text("That listing is already gone.", NamedTextColor.GRAY));
            return;
        }
        data.set("listings." + id, null);
        save();
        plugin.give(p, item);
        p.sendMessage(Component.text("Took your listing back.", NamedTextColor.GREEN));
        open(p, true);
    }

    private void buy(Player buyer, String id) {
        String base = "listings." + id;
        ItemStack item = data.getItemStack(base + ".item");
        if (item == null) {
            buyer.sendMessage(Component.text("Somebody just bought that one!", NamedTextColor.RED));
            open(buyer, false);
            return;
        }
        int price = data.getInt(base + ".price");
        String currency = data.getString(base + ".currency", "unbound");
        UUID seller = UUID.fromString(data.getString(base + ".seller", ""));
        if (currency.equals("galaxy")) {
            if (plugin.shops.countGalaxyCoins(buyer) < price) {
                buyer.sendMessage(Component.text("You need " + price + " Galaxy Coins (you have "
                        + plugin.shops.countGalaxyCoins(buyer) + ").", NamedTextColor.RED));
                return;
            }
            plugin.shops.removeGalaxyCoins(buyer, price);
        } else {
            if (!plugin.unbound.spend(buyer, price)) {
                buyer.sendMessage(Component.text("You need " + price + " Unbound Coins (you have "
                        + plugin.unbound.points(buyer) + ").", NamedTextColor.RED));
                return;
            }
        }
        data.set("listings." + id, null);
        save();
        plugin.give(buyer, item);
        buyer.sendMessage(Component.text("Bought it for " + price + " "
                + (currency.equals("galaxy") ? "Galaxy" : "Unbound") + " Coins!", NamedTextColor.GREEN));
        // pay the seller now if they're online, otherwise it waits for them
        Player online = Bukkit.getPlayer(seller);
        if (online != null) {
            payOut(online, currency, price);
            online.sendMessage(Component.text(buyer.getName() + " bought your listing for " + price + " "
                    + (currency.equals("galaxy") ? "Galaxy" : "Unbound") + " Coins!", NamedTextColor.GREEN));
        } else {
            String path = "payouts." + seller + "." + currency;
            data.set(path, data.getInt(path, 0) + price);
            save();
        }
        open(buyer, false);
    }

    private void payOut(Player p, String currency, int amount) {
        if (currency.equals("galaxy")) plugin.shops.giveGalaxyCoins(p, amount);
        else plugin.unbound.addPoints(p, amount, "playershop");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String base = "payouts." + p.getUniqueId();
        int unbound = data.getInt(base + ".unbound", 0);
        int galaxy = data.getInt(base + ".galaxy", 0);
        if (unbound <= 0 && galaxy <= 0) return;
        data.set(base, null);
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (unbound > 0) {
                plugin.unbound.addPoints(p, unbound, "playershop");
                p.sendMessage(Component.text("While you were gone, your Player Shop listings sold for "
                        + unbound + " Unbound Coins!", NamedTextColor.GREEN));
            }
            if (galaxy > 0) {
                plugin.shops.giveGalaxyCoins(p, galaxy);
                p.sendMessage(Component.text("While you were gone, your Player Shop listings sold for "
                        + galaxy + " Galaxy Coins!", NamedTextColor.AQUA));
            }
        }, 40L);
    }
}
