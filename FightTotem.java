package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Fight Totem: when a player starts a boss fight or a duel, they are handed a special totem in
 * their off hand. It is named "Fight Totem" and carries a hidden tag, so it is NOT a normal
 * Totem of Undying:
 *
 *  - If a hit would kill the player, the Fight Totem pops instead: the player does not die,
 *    does not drop anything, and LOSES the fight (the fight's lose handler runs).
 *  - If the player wins, the Fight Totem is removed again.
 *  - Only items with the fight-totem tag are ever removed. A player's own normal totems are
 *    never touched: whatever was in the off hand before the fight is put back afterwards.
 *
 * The pop is handled here at HIGHEST priority, before vanilla totem logic, because the old bug was
 * that some lethal hits (boss projectiles, fire, poison, phase-3 double damage) slipped past the
 * boss fight's own damage check and the player really died. This listener is the single safety net
 * for every protected fight, and a PlayerDeathEvent backup below catches anything that still
 * somehow gets through.
 */
final class FightTotem implements Listener {

    /** Runs when a player's Fight Totem pops. The fight should end for that player as a loss. */
    interface LoseHandler {
        void onTotemPop(Player p);
    }

    private static final class Entry {
        final ItemStack savedOffHand; // whatever the player held before, restored at the end (may be null/air)
        final String fightName;
        final LoseHandler handler;
        boolean popped;
        boolean deathPending; // the death-event backup fired; run the handler after respawn

        Entry(ItemStack savedOffHand, String fightName, LoseHandler handler) {
            this.savedOffHand = savedOffHand;
            this.fightName = fightName;
            this.handler = handler;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey key;
    private final Map<UUID, Entry> active = new HashMap<>();

    FightTotem(AbilitySMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "fight_totem");
    }

    // =====================================================================
    // The item
    // =====================================================================

    ItemStack totemItem() {
        ItemStack it = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Fight Totem", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(java.util.List.of(
                Component.text("You only have this for this fight.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("If it pops, you lose the fight - but you never die.", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Win, and it disappears. Your own totems are safe.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    boolean isFightTotem(ItemStack it) {
        return it != null && it.getType() == Material.TOTEM_OF_UNDYING && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    boolean isProtected(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    // =====================================================================
    // Start / end of a protected fight
    // =====================================================================

    /** Give the player their Fight Totem. Their old off-hand item is saved and restored by cleanup(). */
    void protect(Player p, String fightName, LoseHandler handler) {
        if (active.containsKey(p.getUniqueId())) cleanup(p);
        ItemStack old = p.getInventory().getItemInOffHand();
        ItemStack saved = (old == null || old.getType() == Material.AIR || isFightTotem(old)) ? null : old.clone();
        if (saved != null) {
            // keep the player's real off-hand item safe inside their inventory if there is room,
            // otherwise we hold it here and give it back in cleanup()
            Map<Integer, ItemStack> left = p.getInventory().addItem(saved.clone());
            if (left.isEmpty()) saved = null; // it is safely in their inventory now
        }
        active.put(p.getUniqueId(), new Entry(saved, fightName, handler));
        p.getInventory().setItemInOffHand(totemItem());
        p.sendMessage(Component.text("You hold a Fight Totem for this fight: if it pops you lose, "
                + "but you will not die.", NamedTextColor.GOLD));
    }

    /**
     * The fight is over (win, lose, draw, quit). Removes ONLY the Fight Totem (never normal totems)
     * and gives back the off-hand item that was saved in protect(), if we are still holding it.
     */
    void cleanup(Player p) {
        Entry e = active.remove(p.getUniqueId());
        removeFightTotems(p);
        if (e != null && e.savedOffHand != null) {
            ItemStack cur = p.getInventory().getItemInOffHand();
            if (cur == null || cur.getType() == Material.AIR) {
                p.getInventory().setItemInOffHand(e.savedOffHand);
            } else {
                plugin.give(p, e.savedOffHand);
            }
        }
    }

    /** Removes every tagged Fight Totem from the player's hands and inventory. Plain totems stay. */
    private void removeFightTotems(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isFightTotem(contents[i])) p.getInventory().setItem(i, null);
        }
        if (isFightTotem(p.getInventory().getItemInOffHand())) p.getInventory().setItemInOffHand(null);
        if (isFightTotem(p.getInventory().getItemInMainHand())) p.getInventory().setItemInMainHand(null);
    }

    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (active.containsKey(p.getUniqueId())) cleanup(p);
        }
        active.clear();
    }

    // =====================================================================
    // The pop: a lethal hit loses the fight instead of killing the player
    // =====================================================================

    private void pop(Player p, Entry e) {
        if (e.popped) return;
        e.popped = true;
        removeFightTotems(p); // consume the Fight Totem, and only the Fight Totem
        p.setFireTicks(0);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? Math.max(1.0, Math.min(max.getValue(), 10.0)) : 10.0);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.4, 0.6, 0.4, 0.3);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        p.showTitle(Title.title(Component.text("Your Fight Totem popped!", NamedTextColor.RED),
                Component.text("You lose this fight - but you are alive", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
        e.handler.onTotemPop(p);
    }

    /** The safety net: ANY lethal damage to a protected player pops the Fight Totem instead. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Entry en = active.get(p.getUniqueId());
        if (en == null || en.popped || p.getHealth() <= 0 || e.isCancelled()) return;
        if (e.getFinalDamage() >= p.getHealth()) {
            e.setCancelled(true);
            pop(p, en);
        }
    }

    /** Backup: if a totem resurrection still happens somehow, count it as a pop and a loss. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Entry en = active.get(p.getUniqueId());
        if (en == null || en.popped) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entry cur = active.get(p.getUniqueId());
            if (cur != null && !cur.popped && p.isOnline()) pop(p, cur);
        });
    }

    /** Last-resort backup: a protected player who somehow still dies keeps everything and loses. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Entry en = active.get(p.getUniqueId());
        if (en == null || en.popped) return;
        en.popped = true;
        en.deathPending = true;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        plugin.getLogger().warning("Fight Totem backup: " + p.getName() + " died during " + en.fightName
                + " despite protection; treating it as a totem pop (loss, items kept).");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Entry en = active.get(p.getUniqueId());
        if (en == null || !en.deathPending) return;
        en.deathPending = false;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && active.containsKey(p.getUniqueId())) en.handler.onTotemPop(p);
        }, 5L);
    }

    // =====================================================================
    // The Fight Totem cannot be dropped, stashed or swapped away mid-fight
    // =====================================================================

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isFightTotem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            plugin.bar(e.getPlayer(), "You can't drop your Fight Totem during a fight.", NamedTextColor.GRAY);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (isFightTotem(e.getMainHandItem()) || isFightTotem(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (isFightTotem(e.getCurrentItem()) || isFightTotem(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (isFightTotem(e.getOldCursor())) e.setCancelled(true);
    }
}
