package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v15: the two Wind rules Nathan set for the ONE OF A KIND power.
 *
 * 1) The Wind power star can never be stored. Chests, ender chests, shulker
 *    boxes, barrels, hoppers, furnaces - putting the star into ANY container
 *    is blocked (clicking it in, shift-clicking it in, dragging it in, and
 *    hoppers sucking it in). Taking it back OUT is always fine.
 *
 * 2) The Wind owner has to actually play: at least 60 minutes a week
 *    (config wind.weekly-minutes). Minutes are counted for everyone while
 *    they're online; when a 7-day week ends, an owner under the line loses
 *    Wind, and it passes to the most-played other player that week.
 */
final class WindGuard implements Listener {

    private final AbilitySMP plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final Set<InventoryType> STORAGE = EnumSet.of(
            InventoryType.CHEST, InventoryType.ENDER_CHEST, InventoryType.SHULKER_BOX,
            InventoryType.BARREL, InventoryType.HOPPER, InventoryType.DISPENSER,
            InventoryType.DROPPER, InventoryType.FURNACE, InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER, InventoryType.BREWING, InventoryType.CRAFTER);

    WindGuard(AbilitySMP plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::minuteTick, 1200L, 1200L);
    }

    private boolean isWindStar(ItemStack it) {
        return it != null && plugin.powerOfStar(it) == AbilitySMP.Power.WIND;
    }

    private boolean isStorage(Inventory inv) {
        return inv != null && STORAGE.contains(inv.getType());
    }

    private void warn(Player p) {
        long now = System.currentTimeMillis();
        if (now - lastWarn.getOrDefault(p.getUniqueId(), 0L) < 3000) return;
        lastWarn.put(p.getUniqueId(), now);
        plugin.bar(p, "The Wind power refuses to be stored. It stays with its owner.", NamedTextColor.RED);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!isStorage(top)) return;
        if (e.getClickedInventory() == top) {
            // placing the star INTO the container (cursor carries it in)
            if (isWindStar(e.getCursor())) {
                e.setCancelled(true);
                warn(p);
                return;
            }
            // number-key swap pulling the star from the hotbar into the container
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    && isWindStar(p.getInventory().getItem(e.getHotbarButton()))) {
                e.setCancelled(true);
                warn(p);
            }
        } else if (e.getClickedInventory() == p.getInventory() && e.isShiftClick()
                && isWindStar(e.getCurrentItem())) {
            // shift-click would throw it up into the container
            e.setCancelled(true);
            warn(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!isStorage(top)) return;
        if (!isWindStar(e.getOldCursor())) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                warn(p);
                return;
            }
        }
    }

    @EventHandler
    public void onMoveItem(InventoryMoveItemEvent e) {
        if (isWindStar(e.getItem())) e.setCancelled(true); // hoppers can't steal it either
    }

    // ------------------------------------------------------------ the weekly rule

    private void minuteTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String path = "wind-week." + p.getUniqueId();
            plugin.data.set(path, plugin.data.getInt(path, 0) + 1);
        }
        plugin.saveData();
        long start = plugin.data.getLong("wind.week-start", 0L);
        long now = System.currentTimeMillis();
        if (start == 0L) {
            plugin.data.set("wind.week-start", now);
            plugin.saveData();
            return;
        }
        if (now - start >= WEEK_MS) endWeek(now);
    }

    private void endWeek(long now) {
        int need = plugin.getConfig().getInt("wind.weekly-minutes", 60);
        UUID owner = plugin.profile.ownerOf(AbilitySMP.Power.WIND);
        if (owner != null) {
            int played = plugin.data.getInt("wind-week." + owner, 0);
            if (played < need) transfer(owner, played, need);
        }
        plugin.data.set("wind-week", null);
        plugin.data.set("wind.week-start", now);
        plugin.saveData();
        plugin.profile.save();
    }

    /** Takes Wind from an inactive owner and gives it to the week's most-played other player. */
    private void transfer(UUID owner, int played, int need) {
        UUID best = null;
        int bestMinutes = 0;
        ConfigurationSection players = plugin.data.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (Exception e) {
                    continue;
                }
                if (id.equals(owner)) continue;
                int mins = plugin.data.getInt("wind-week." + id, 0);
                if (mins > bestMinutes) {
                    bestMinutes = mins;
                    best = id;
                }
                if (best == null) best = id; // fallback: any other known player
            }
        }
        String ownerName = nameOf(owner);
        plugin.profile.removeOwned(owner, AbilitySMP.Power.WIND);
        Player old = Bukkit.getPlayer(owner);
        if (old != null) {
            if (plugin.powerOf(old) == AbilitySMP.Power.WIND) plugin.setPower(old, null);
            old.sendMessage(Component.text("You only played " + played + " of the " + need
                    + " minutes needed this week - the Wind power has left you!", NamedTextColor.RED));
            old.showTitle(Title.title(Component.text("WIND HAS LEFT YOU", NamedTextColor.RED),
                    Component.text("Play " + need + " minutes a week to keep it", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        if (best != null) {
            plugin.profile.addOwned(best, AbilitySMP.Power.WIND);
            Player nw = Bukkit.getPlayer(best);
            if (nw != null) {
                nw.sendMessage(Component.text("The Wind power chose YOU! " + ownerName + " didn't play enough this week. "
                        + "Keep it by playing " + need + " minutes every week - and it can never go in a chest.",
                        NamedTextColor.AQUA));
                nw.showTitle(Title.title(Component.text("YOU HAVE THE WIND POWER", NamedTextColor.AQUA),
                        Component.text("One of a kind - don't lose it!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
                nw.playSound(nw.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
            Bukkit.broadcast(Component.text("The Wind power left " + ownerName + " (only " + played + "/" + need
                    + " minutes played this week) and now belongs to " + nameOf(best) + "!", NamedTextColor.AQUA));
        } else {
            Bukkit.broadcast(Component.text("The Wind power left " + ownerName + " (only " + played + "/" + need
                    + " minutes played this week). It now waits for a new owner.", NamedTextColor.AQUA));
        }
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "someone";
    }
}
