package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /sell - a sell box. Put items in the top two rows, then hit the Unbound
 * button or the Galaxy button to sell them. v15.1: ANY vanilla item sells -
 * the config tables (sell.unbound / sell.galaxy) set the good prices, and
 * anything not listed sells for sell.default-price Unbound Coins each
 * (enchanted and renamed gear included). The only things that never sell
 * are the plugin's own items (power stars, Galaxy Coins, legendary
 * weapons, crate keys...) - they carry abilitysmp data, and they just stay
 * in the box and come back to you.
 *
 * v15.2: Nathan asked for LITERALLY anything to sell - so the plugin-item
 * exclusion is gone too. If you sell a power star or a legendary weapon
 * for pocket change, that's on you.
 *
 * v15.3: the better the item, the more it pays. Config-listed prices win;
 * everything else is valued automatically: gear by material tier
 * (netherite > diamond > gold > iron > ...), +10 per enchantment level,
 * rare items priced by name, and damaged gear pays less than mint gear.
 * v16: Unbound Coins are the only currency - Galaxy Coins are retired.
 */
final class SellShop implements Listener {

    private final AbilitySMP plugin;

    SellShop(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Sell Items"));
        holder.inv = inv;
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 18; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(21, button(Material.EMERALD, "SELL FOR UNBOUND COINS",
                "Sells EVERYTHING above - and the better the item,",
                "the more it pays: netherite and enchanted gear",
                "pay big, plain junk pays 1 coin."));
        p.openInventory(inv);
        p.sendMessage(Component.text("Put items in the box, then press a SELL button. "
                + "Anything that can't be sold comes back to you.", NamedTextColor.GRAY));
    }

    private ItemStack button(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String s : lore) {
            lines.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        m.lore(lines);
        it.setItemMeta(m);
        return it;
    }

    /** What one of this stack sells for in Unbound Coins (v16: the only currency). */
    private int valueOf(ItemStack it) {
        int listed = plugin.getConfig().getInt("sell.unbound." + it.getType().name(), 0);
        if (listed > 0) return listed;
        int auto = autoValue(it);
        return Math.max(auto, Math.max(0, plugin.getConfig().getInt("sell.default-price", 1)));
    }

    private static final java.util.Map<String, Integer> SPECIAL = java.util.Map.ofEntries(
            java.util.Map.entry("ELYTRA", 400), java.util.Map.entry("BEACON", 500),
            java.util.Map.entry("TOTEM_OF_UNDYING", 150), java.util.Map.entry("ENCHANTED_GOLDEN_APPLE", 300),
            java.util.Map.entry("MACE", 250), java.util.Map.entry("HEAVY_CORE", 200),
            java.util.Map.entry("DRAGON_EGG", 1000), java.util.Map.entry("NETHER_STAR", 250),
            java.util.Map.entry("SHULKER_BOX", 60), java.util.Map.entry("END_CRYSTAL", 50),
            java.util.Map.entry("WITHER_SKELETON_SKULL", 40),
            java.util.Map.entry("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 200),
            java.util.Map.entry("VILLAGER_SPAWN_EGG", 30), java.util.Map.entry("EXPERIENCE_BOTTLE", 3),
            java.util.Map.entry("TRIDENT", 90), java.util.Map.entry("CROSSBOW", 30),
            java.util.Map.entry("BOW", 15), java.util.Map.entry("SHIELD", 25),
            java.util.Map.entry("FISHING_ROD", 10));

    /** Automatic value: rare items by name, gear by tier, enchantments and condition on top. */
    private int autoValue(ItemStack it) {
        String name = it.getType().name();
        int base = SPECIAL.getOrDefault(name, 0);
        if (base == 0 && name.endsWith("_SPAWN_EGG")) base = 15;
        if (base == 0) {
            String[] tiers = {"NETHERITE_", "DIAMOND_", "GOLDEN_", "IRON_", "CHAINMAIL_", "LEATHER_", "STONE_", "WOODEN_"};
            int[] values = {150, 80, 30, 25, 18, 8, 6, 4};
            String[] gearSuffix = {"_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE",
                    "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"};
            for (int i = 0; i < tiers.length; i++) {
                if (!name.startsWith(tiers[i])) continue;
                for (String suf : gearSuffix) {
                    if (name.endsWith(suf)) {
                        base = values[i];
                        break;
                    }
                }
                break;
            }
        }
        int enchLevels = 0;
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
            for (int lvl : esm.getStoredEnchants().values()) enchLevels += lvl;
        } else {
            for (int lvl : it.getEnchantments().values()) enchLevels += lvl;
        }
        int value = base + enchLevels * 10;
        if (value <= 0) return 0;
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg
                && it.getType().getMaxDurability() > 0) {
            double health = 1.0 - (double) dmg.getDamage() / it.getType().getMaxDurability();
            value = Math.max(1, (int) Math.round(value * (0.25 + 0.75 * health)));
        }
        return value;
    }

    /** The plugin's own items (stars, coins, weapons, keys) carry abilitysmp data - those never sell. */
    private boolean isPluginItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        for (org.bukkit.NamespacedKey k : it.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (k.getNamespace().equals("abilitysmp")) return true;
        }
        return false;
    }

    private void doSell(Player p, Inventory inv) {
        int total = 0, count = 0;
        for (int i = 0; i < 18; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue; // v15.2: anything sells. Anything.
            // v18: ...except the soulbound gear the plugin re-creates for free
            // every 2 seconds (switch needles) or on respawn (the scythe) -
            // selling those would be an infinite-coin machine.
            if (plugin.isNeedle(it) || plugin.isScythe(it) || plugin.isActivator(it)) continue;
            int unit = valueOf(it);
            if (unit <= 0) continue;
            total += unit * it.getAmount();
            count += it.getAmount();
            inv.setItem(i, null);
        }
        if (total <= 0) {
            p.sendMessage(Component.text("Nothing in the box can be sold.", NamedTextColor.RED));
            return;
        }
        plugin.unbound.addPoints(p, total, "sell");
        p.sendMessage(Component.text("Sold " + count + " items for " + total + " Unbound Coins!", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getInventory();
        if (e.getClickedInventory() == top) {
            int slot = e.getSlot();
            if (slot >= 18) {
                e.setCancelled(true);
                if (slot == 21) doSell(p, top);
            }
        } else if (e.isShiftClick()) {
            e.setCancelled(true); // shift-clicks could land on the buttons row
            p.sendMessage(Component.text("Click items into the box one stack at a time.", NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw >= 18 && raw < 27) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < 18; i++) {
            ItemStack it = e.getInventory().getItem(i);
            if (it != null) {
                e.getInventory().setItem(i, null);
                plugin.give(p, it);
            }
        }
    }
}
