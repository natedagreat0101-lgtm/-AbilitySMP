package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared menu system: black background, white text, and page scrolling. The power menu, quest menu,
 * shop and locker are all built on {@link Paged}, so they look and behave the same.
 */
final class Menus implements Listener {

    interface Click {
        void run(Player p, ClickType type);
    }

    static final class Entry {
        final ItemStack icon;
        final Click click;

        Entry(ItemStack icon, Click click) {
            this.icon = icon;
            this.click = click;
        }
    }

    static Component white(String s) {
        return Component.text(s, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack icon(Material m, String name, boolean glow, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(white(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(white(s));
            meta.lore(l);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    static abstract class Paged implements InventoryHolder {
        final String title;
        Inventory inv;
        int page;
        Runnable back;

        Paged(String title) {
            this.title = title;
        }

        abstract List<Entry> entries(Player p);

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void open(Player p) {
            inv = Bukkit.createInventory(this, 54, Component.text(title, NamedTextColor.WHITE));
            render(p);
            p.openInventory(inv);
        }

        void render(Player p) {
            List<Entry> es = entries(p);
            int pages = Math.max(1, (es.size() + 44) / 45);
            page = Math.max(0, Math.min(page, pages - 1));
            ItemStack pane = icon(Material.BLACK_STAINED_GLASS_PANE, " ", false);
            for (int s = 0; s < 54; s++) inv.setItem(s, pane);
            for (int k = 0; k < 45; k++) {
                int idx = page * 45 + k;
                if (idx >= es.size()) break;
                inv.setItem(k, es.get(idx).icon);
            }
            if (back != null) inv.setItem(45, icon(Material.ARROW, "Back", false));
            if (page > 0) inv.setItem(48, icon(Material.SPECTRAL_ARROW, "Scroll up", false, "Previous page"));
            inv.setItem(49, icon(Material.PAPER, "Page " + (page + 1) + " / " + pages, false));
            if (page < pages - 1) inv.setItem(50, icon(Material.SPECTRAL_ARROW, "Scroll down", false, "Next page"));
            inv.setItem(53, icon(Material.BARRIER, "Close", false));
        }

        void handle(Player p, int slot, ClickType type) {
            if (slot == 53) {
                p.closeInventory();
                return;
            }
            if (slot == 45 && back != null) {
                back.run();
                return;
            }
            if (slot == 48) {
                page--;
                render(p);
                return;
            }
            if (slot == 50) {
                page++;
                render(p);
                return;
            }
            if (slot < 0 || slot >= 45) return;
            List<Entry> es = entries(p);
            int idx = page * 45 + slot;
            if (idx >= es.size() || es.get(idx).click == null) return;
            es.get(idx).click.run(p, type);
            if (inv.getViewers().contains(p)) render(p); // refresh, unless the click opened another menu
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Paged m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        m.handle(p, e.getRawSlot(), e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Paged) e.setCancelled(true);
    }
}
