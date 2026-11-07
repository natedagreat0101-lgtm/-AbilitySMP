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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * v15.5 THE CAVE UPDATE: /caveshop - spend Cave Coins on cave weapons
 * and cave loot. One page, click to buy; your balance is in the
 * title and on every price tag.
 */
final class CaveShop implements Listener {

    static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record Entry(ItemStack item, int price, String label) {
    }

    private final AbilitySMP plugin;

    CaveShop(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (CaveWeapons.CWDef d : CaveWeapons.CWDef.values()) {
            out.add(new Entry(plugin.caveWeapons.create(d), d.price, d.display));
        }
        out.add(new Entry(new ItemStack(Material.ECHO_SHARD), 40, "Echo Shard"));
        out.add(new Entry(new ItemStack(Material.SCULK, 4), 15, "Sculk x4"));
        out.add(new Entry(new ItemStack(Material.AMETHYST_SHARD, 4), 10, "Amethyst Shard x4"));
        out.add(new Entry(new ItemStack(Material.GLOW_BERRIES, 8), 8, "Glow Berries x8"));
        out.add(new Entry(new ItemStack(Material.TORCH, 16), 5, "Torches x16"));
        out.add(new Entry(new ItemStack(Material.IRON_PICKAXE), 30, "Iron Pickaxe"));
        out.add(new Entry(new ItemStack(Material.DIAMOND, 2), 90, "Diamonds x2"));
        out.add(new Entry(new ItemStack(Material.EXPERIENCE_BOTTLE, 8), 25, "Bottles o' Enchanting x8"));
        out.add(new Entry(new ItemStack(Material.GOLDEN_CARROT, 8), 20, "Golden Carrots x8"));
        out.add(new Entry(new ItemStack(Material.ENDER_PEARL, 4), 35, "Ender Pearls x4"));
        return out;
    }

    void open(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27,
                Component.text("⛏ Cave Shop - " + plugin.cave.coins(p) + " Cave Coins",
                        NamedTextColor.GOLD));
        List<Entry> entries = entries();
        for (int i = 0; i < entries.size() && i < 27; i++) {
            Entry en = entries.get(i);
            ItemStack show = en.item().clone();
            ItemMeta m = show.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (m.hasLore()) lore.addAll(m.lore());
            lore.add(Component.text("Price: " + en.price() + " Cave Coins", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to buy", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            show.setItemMeta(m);
            inv.setItem(i, show);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getSlot();
        List<Entry> entries = entries();
        if (slot < 0 || slot >= entries.size()) return;
        Entry en = entries.get(slot);
        if (!plugin.cave.spend(p, en.price())) {
            p.sendMessage(Component.text("Not enough Cave Coins - that costs " + en.price()
                    + " and you have " + plugin.cave.coins(p) + ". Earn more in /cavequests, "
                    + "or mine ores below Y=" + plugin.getConfig().getInt("cave.max-ore-y", 20)
                    + ".", NamedTextColor.RED));
            return;
        }
        plugin.give(p, en.item().clone());
        p.sendMessage(Component.text("Bought " + en.label() + " for " + en.price()
                + " Cave Coins. You have " + plugin.cave.coins(p) + " left.", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        open(p); // refresh the balance in the title
    }
}
