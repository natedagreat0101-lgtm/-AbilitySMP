package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player trading (v9): /trade <player>, they /trade accept, and a shared trade window opens.
 * The left side is yours, the right side is theirs. Nothing moves until BOTH players press their
 * Confirm button, and touching your offer again un-confirms both sides - so nobody can sneak an
 * item out at the last second. Closing the window gives everything back. Soulbound items (the
 * Power Activator and Fight Totems) can never be traded.
 */
final class Trading implements Listener {

    static final class TradeHolder implements InventoryHolder {
        Session session;
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class Session {
        final Player a;
        final Player b;
        final TradeHolder holder = new TradeHolder();
        Inventory inv;
        boolean confA;
        boolean confB;
        boolean done;
        BukkitTask timeout;

        Session(Player a, Player b) {
            this.a = a;
            this.b = b;
            holder.session = this;
        }

        Player other(Player p) {
            return p.getUniqueId().equals(a.getUniqueId()) ? b : a;
        }

        boolean isA(Player p) {
            return p.getUniqueId().equals(a.getUniqueId());
        }
    }

    /** Your offer area: the left 4 columns. Their offer area: the right 4 columns. */
    private static final Set<Integer> AREA_A = new HashSet<>();
    private static final Set<Integer> AREA_B = new HashSet<>();
    private static final int CONFIRM_A = 45;
    private static final int CONFIRM_B = 53;

    static {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) AREA_A.add(row * 9 + col);
            for (int col = 5; col < 9; col++) AREA_B.add(row * 9 + col);
        }
    }

    private final AbilitySMP plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, UUID> requests = new HashMap<>(); // target -> requester
    private final Map<UUID, Long> requestAt = new HashMap<>();

    Trading(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    boolean isBusy(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    // =====================================================================
    // Commands
    // =====================================================================

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length == 0) {
            p.sendMessage(Component.text("Usage: /trade <player>   /trade accept   /trade deny", NamedTextColor.GRAY));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "accept" -> accept(p);
            case "deny", "decline" -> deny(p);
            case "cancel" -> {
                Session ses = sessions.get(p.getUniqueId());
                if (ses != null) abort(ses, "The trade was cancelled.");
                else p.sendMessage(Component.text("You are not trading.", NamedTextColor.GRAY));
            }
            default -> request(p, args[0]);
        }
    }

    private boolean busyEither(Player x, Player y) {
        for (Player p : new Player[]{x, y}) {
            if (isBusy(p) || plugin.duel.isBusy(p) || plugin.horseman.isBusy(p) || plugin.hallow.isBusy(p)
                    || plugin.ranked.isBusy(p) || plugin.fightTotem.isProtected(p)) return true;
        }
        return false;
    }

    private void request(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || t.equals(p)) {
            p.sendMessage(Component.text("Pick another player who is online.", NamedTextColor.RED));
            return;
        }
        if (busyEither(p, t)) {
            p.sendMessage(Component.text("One of you is busy right now (a fight or another trade).", NamedTextColor.RED));
            return;
        }
        requests.put(t.getUniqueId(), p.getUniqueId());
        requestAt.put(t.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(Component.text("Trade request sent to " + t.getName() + ".", NamedTextColor.GOLD));
        t.sendMessage(Component.text(p.getName() + " wants to trade with you! ", NamedTextColor.GOLD)
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/trade accept")))
                .append(Component.text("  "))
                .append(Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/trade deny"))));
    }

    private void deny(Player p) {
        UUID rid = requests.remove(p.getUniqueId());
        if (rid == null) {
            p.sendMessage(Component.text("You have no trade request.", NamedTextColor.GRAY));
            return;
        }
        Player r = Bukkit.getPlayer(rid);
        if (r != null) r.sendMessage(Component.text(p.getName() + " declined your trade.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Trade declined.", NamedTextColor.GRAY));
    }

    private void accept(Player p) {
        UUID rid = requests.remove(p.getUniqueId());
        Long at = requestAt.remove(p.getUniqueId());
        Player r = rid == null ? null : Bukkit.getPlayer(rid);
        if (r == null || at == null || System.currentTimeMillis() - at > cfg("trading.request-seconds", 60) * 1000L) {
            p.sendMessage(Component.text("You have no (valid) trade request.", NamedTextColor.GRAY));
            return;
        }
        if (busyEither(p, r)) {
            p.sendMessage(Component.text("Someone is busy right now.", NamedTextColor.RED));
            return;
        }
        start(r, p);
    }

    // =====================================================================
    // The trade window (ONE inventory that both players look at)
    // =====================================================================

    private ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private void start(Player a, Player b) {
        Session ses = new Session(a, b);
        Inventory inv = Bukkit.createInventory(ses.holder, 54,
                Component.text("Trade: " + a.getName() + " ⇄ " + b.getName(), NamedTextColor.GOLD));
        ses.holder.inv = inv;
        ses.inv = inv;
        ItemStack divider = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int row = 0; row < 4; row++) inv.setItem(row * 9 + 4, divider);
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 36; s < 45; s++) inv.setItem(s, filler);
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);
        inv.setItem(49, pane(Material.PAPER, "Left = " + a.getName() + ", right = " + b.getName()
                + ". Both players must confirm!"));
        refreshButtons(ses);
        sessions.put(a.getUniqueId(), ses);
        sessions.put(b.getUniqueId(), ses);
        a.openInventory(inv);
        b.openInventory(inv);
        int secs = Math.max(30, cfg("trading.timeout-seconds", 120));
        ses.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!ses.done) abort(ses, "The trade timed out - everything was returned.");
        }, secs * 20L);
        a.sendMessage(Component.text("Trading with " + b.getName() + ". Put your items on YOUR side, then press your Confirm button.",
                NamedTextColor.GOLD));
        b.sendMessage(Component.text("Trading with " + a.getName() + ". Put your items on YOUR side, then press your Confirm button.",
                NamedTextColor.GOLD));
    }

    private void refreshButtons(Session ses) {
        ses.inv.setItem(CONFIRM_A, pane(ses.confA ? Material.LIME_WOOL : Material.RED_WOOL,
                ses.a.getName() + (ses.confA ? ": CONFIRMED ✔ (click to undo)" : ": click to CONFIRM")));
        ses.inv.setItem(CONFIRM_B, pane(ses.confB ? Material.LIME_WOOL : Material.RED_WOOL,
                ses.b.getName() + (ses.confB ? ": CONFIRMED ✔ (click to undo)" : ": click to CONFIRM")));
    }

    /** Any change to an offer un-confirms BOTH sides, so last-second swaps are impossible. */
    private void unconfirm(Session ses) {
        if (!ses.confA && !ses.confB) return;
        ses.confA = false;
        ses.confB = false;
        refreshButtons(ses);
        for (Player p : new Player[]{ses.a, ses.b}) {
            if (p.isOnline()) {
                plugin.bar(p, "The offer changed - both sides must confirm again!", NamedTextColor.YELLOW);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }
    }

    // =====================================================================
    // Clicks
    // =====================================================================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof TradeHolder h)) return;
        Session ses = h.session;
        if (ses == null || ses.done) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        boolean mine = ses.isA(p);
        Set<Integer> myArea = mine ? AREA_A : AREA_B;
        int raw = e.getRawSlot();
        boolean clickedTop = raw >= 0 && raw < 54;
        if (e.getClick() == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true); // double-click would vacuum items out of BOTH sides of the window
            return;
        }
        if (clickedTop) {
            if (raw == (mine ? CONFIRM_A : CONFIRM_B)) {
                e.setCancelled(true);
                if (mine) ses.confA = !ses.confA;
                else ses.confB = !ses.confB;
                refreshButtons(ses);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                if (ses.confA && ses.confB) complete(ses);
                return;
            }
            if (myArea.contains(raw)) {
                // your own offer area: free to move items, but it resets both confirmations
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ses.done) unconfirm(ses);
                });
                return; // not cancelled: the item move happens normally
            }
            e.setCancelled(true); // the other side, the divider and the buttons are off limits
            return;
        }
        // clicked their own inventory below: fine, except shift-clicks (they would spray items
        // into the whole window) and hotbar swaps aimed at the top window
        if (e.isShiftClick() || e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            plugin.bar(p, "Click items one by one to move them into your side of the trade.", NamedTextColor.GRAY);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof TradeHolder h)) return;
        Session ses = h.session;
        if (ses == null || ses.done || !(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        Set<Integer> myArea = ses.isA(p) ? AREA_A : AREA_B;
        for (int raw : e.getRawSlots()) {
            if (raw < 54 && !myArea.contains(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!ses.done) unconfirm(ses);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof TradeHolder h)) return;
        Session ses = h.session;
        if (ses == null || ses.done) return;
        abort(ses, e.getPlayer().getName() + " closed the trade - everything was returned.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        requests.remove(e.getPlayer().getUniqueId());
        Session ses = sessions.get(e.getPlayer().getUniqueId());
        if (ses != null && !ses.done) abort(ses, e.getPlayer().getName() + " logged out - the trade was cancelled.");
    }

    // =====================================================================
    // Completing and cancelling
    // =====================================================================

    private List<ItemStack> collect(Session ses, Set<Integer> area) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot : area) {
            ItemStack it = ses.inv.getItem(slot);
            if (it != null && it.getType() != Material.AIR) {
                out.add(it.clone());
                ses.inv.setItem(slot, null);
            }
        }
        return out;
    }

    private boolean hasForbidden(List<ItemStack> items) {
        for (ItemStack it : items) {
            if (plugin.isActivator(it) || plugin.fightTotem.isFightTotem(it) || plugin.locker.isCostume(it)) return true;
        }
        return false;
    }

    private void complete(Session ses) {
        List<ItemStack> fromA = collect(ses, AREA_A);
        List<ItemStack> fromB = collect(ses, AREA_B);
        if (hasForbidden(fromA) || hasForbidden(fromB)) {
            // put everything back where it was is complex; simplest safe path: return all and end
            giveBack(ses, fromA, fromB);
            endSession(ses);
            for (Player p : new Player[]{ses.a, ses.b}) {
                if (p.isOnline()) {
                    p.sendMessage(Component.text("Trade cancelled: the Power Activator, Fight Totems and worn costumes can never be traded.",
                            NamedTextColor.RED));
                    p.closeInventory();
                }
            }
            return;
        }
        endSession(ses);
        for (ItemStack it : fromB) if (ses.a.isOnline()) plugin.give(ses.a, it);
        for (ItemStack it : fromA) if (ses.b.isOnline()) plugin.give(ses.b, it);
        for (Player p : new Player[]{ses.a, ses.b}) {
            if (!p.isOnline()) continue;
            p.sendMessage(Component.text("Trade complete! You traded with " + ses.other(p).getName() + ".", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.1f);
            p.closeInventory();
            plugin.unbound.addProgress(p, "TRADE", 1);
        }
        plugin.getLogger().info("Trade: " + ses.a.getName() + " gave " + fromA.size() + " stack(s), "
                + ses.b.getName() + " gave " + fromB.size() + " stack(s).");
    }

    private void giveBack(Session ses, List<ItemStack> fromA, List<ItemStack> fromB) {
        for (ItemStack it : fromA) if (ses.a.isOnline()) plugin.give(ses.a, it);
        for (ItemStack it : fromB) if (ses.b.isOnline()) plugin.give(ses.b, it);
    }

    private void abort(Session ses, String message) {
        if (ses.done) return;
        List<ItemStack> fromA = collect(ses, AREA_A);
        List<ItemStack> fromB = collect(ses, AREA_B);
        endSession(ses);
        giveBack(ses, fromA, fromB);
        for (Player p : new Player[]{ses.a, ses.b}) {
            if (!p.isOnline()) continue;
            p.sendMessage(Component.text(message, NamedTextColor.GRAY));
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof TradeHolder) p.closeInventory();
        }
    }

    private void endSession(Session ses) {
        ses.done = true;
        if (ses.timeout != null) ses.timeout.cancel();
        sessions.remove(ses.a.getUniqueId());
        sessions.remove(ses.b.getUniqueId());
    }

    void shutdown() {
        for (Session ses : new HashSet<>(sessions.values())) abort(ses, "The server is restarting - the trade was cancelled.");
        sessions.clear();
    }
}
