package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Mixing Pot (v14): a cauldron wrapped in chains. Craft it (4 chains
 * around a cauldron), place it, right-click it, and put two powers you own
 * into the pot - if those two mix, you get a brand new MIXED power.
 * Mixed powers are hidden in /powers until you own them. Mixing costs
 * Galaxy Coins (config: mixing.cost-galaxy-coins) and never eats the
 * two powers you put in.
 */
final class MixingPot implements Listener {

    private final AbilitySMP plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;
    private final NamespacedKey recipeKey;

    MixingPot(AbilitySMP plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "mixing_pot_item");
        this.displayKey = new NamespacedKey(plugin, "mixing_pot_display");
        this.recipeKey = new NamespacedKey(plugin, "mixing_pot");
    }

    // ------------------------------------------------------------- the item

    ItemStack potItem() {
        ItemStack it = new ItemStack(Material.CAULDRON);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Mixing Pot", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Place it, right-click it, and mix two powers", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("into a brand new MIXED power.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isPotItem(ItemStack it) {
        return it != null && it.getType() == Material.CAULDRON && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    void registerRecipe() {
        ShapedRecipe r = new ShapedRecipe(recipeKey, potItem());
        r.shape(" C ", "CXC", " C ");
        r.setIngredient('C', Material.IRON_CHAIN);
        r.setIngredient('X', Material.CAULDRON);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
    }

    // ------------------------------------------------------------- registry

    private String locKey(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private boolean isPot(Block b) {
        if (b.getType() != Material.CAULDRON) return false;
        return plugin.data.getStringList("mixing-pots").contains(locKey(b.getLocation()));
    }

    private void register(Block b) {
        List<String> pots = new ArrayList<>(plugin.data.getStringList("mixing-pots"));
        String k = locKey(b.getLocation());
        if (!pots.contains(k)) {
            pots.add(k);
            plugin.data.set("mixing-pots", pots);
            plugin.saveData();
        }
        spawnChains(b.getLocation());
    }

    private void unregister(Block b) {
        List<String> pots = new ArrayList<>(plugin.data.getStringList("mixing-pots"));
        pots.remove(locKey(b.getLocation()));
        plugin.data.set("mixing-pots", pots);
        plugin.saveData();
        removeChains(b.getLocation());
    }

    /** The chains on the pot: a chain item floating over the cauldron (a display entity, no world changes). */
    private void spawnChains(Location blockLoc) {
        Location at = blockLoc.clone().add(0.5, 1.25, 0.5);
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class);
        d.setItemStack(new ItemStack(Material.IRON_CHAIN));
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setBillboard(Display.Billboard.FIXED);
        d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f(0, 0, 0, 1)));
        d.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void removeChains(Location blockLoc) {
        Location c = blockLoc.clone().add(0.5, 1, 0.5);
        for (Entity e : c.getWorld().getNearbyEntities(c, 1.5, 2, 1.5)) {
            if (e instanceof ItemDisplay
                    && e.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
    }

    // ------------------------------------------------------------- events

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!isPotItem(e.getItemInHand())) return;
        register(e.getBlockPlaced());
        e.getPlayer().sendMessage(Component.text("Mixing Pot placed! Right-click it to mix powers.", NamedTextColor.LIGHT_PURPLE));
        e.getPlayer().playSound(e.getBlockPlaced().getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1f);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!isPot(e.getBlock())) return;
        unregister(e.getBlock());
        e.setDropItems(false);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), potItem());
    }

    @EventHandler
    public void onExplodeBlocks(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isPot);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isPot);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!isPot(e.getClickedBlock())) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    // ------------------------------------------------------------- the GUI

    static final class PotHolder implements InventoryHolder {
        final Player player;
        AbilitySMP.Power selA;
        AbilitySMP.Power selB;
        final Map<Integer, AbilitySMP.Power> icons = new HashMap<>();
        private Inventory inv;

        PotHolder(Player player) {
            this.player = player;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private ItemStack powerIcon(AbilitySMP.Power pw, boolean selected) {
        ItemStack it = new ItemStack(Material.IRON_SWORD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(AbilitySMP.pretty(pw.key()), plugin.colorFor(pw), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(selected ? "In the pot - click to take it out" : "Click to put it in the pot",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.setCustomModelData(AbilitySMP.modelDataFor(pw));
        if (selected) m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack pane(String text) {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    void open(Player p) {
        PotHolder h = new PotHolder(p);
        Inventory inv = Bukkit.createInventory(h, 54,
                Component.text("Mixing Pot", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        h.inv = inv;
        render(h);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.1f);
    }

    private void render(PotHolder h) {
        Inventory inv = h.inv;
        Player p = h.player;
        inv.clear();
        h.icons.clear();
        int cost = plugin.getConfig().getInt("mixing.cost-galaxy-coins", 250);
        inv.setItem(3, h.selA == null ? pane("Slot 1: pick a power below") : powerIcon(h.selA, true));
        inv.setItem(5, h.selB == null ? pane("Slot 2: pick a power below") : powerIcon(h.selB, true));
        if (h.selA != null && h.selB != null) {
            ItemStack mix = new ItemStack(Material.NETHER_STAR);
            ItemMeta m = mix.getItemMeta();
            m.displayName(Component.text("MIX THEM!", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(
                    Component.text("Cost: " + cost + " Galaxy Coins", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Your two powers stay yours - mixing never eats them.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("If these two mix, something new is born...", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)));
            m.setEnchantmentGlintOverride(true);
            mix.setItemMeta(m);
            inv.setItem(4, mix);
        } else {
            inv.setItem(4, pane("Pick two powers to mix"));
        }
        ItemStack info = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("Your Galaxy Coins: " + plugin.shops.countGalaxyCoins(p), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(Component.text("Mixed powers discovered: " + Fusions.countOwned(plugin, p) + "/" + Fusions.total(),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(im);
        inv.setItem(8, info);
        int slot = 9;
        for (AbilitySMP.Power pw : AbilitySMP.Power.values()) {
            if (pw.isMixed() || !plugin.profile.owns(p, pw)) continue;
            if (slot >= 54) break;
            inv.setItem(slot, powerIcon(pw, pw == h.selA || pw == h.selB));
            h.icons.put(slot, pw);
            slot++;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PotHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 4 && h.selA != null && h.selB != null) {
            doMix(h);
            return;
        }
        AbilitySMP.Power pw = h.icons.get(slot);
        if (pw == null) {
            if (slot == 3) h.selA = null;
            if (slot == 5) h.selB = null;
            if (slot == 3 || slot == 5) render(h);
            return;
        }
        if (pw == h.selA) h.selA = null;
        else if (pw == h.selB) h.selB = null;
        else if (h.selA == null) h.selA = pw;
        else if (h.selB == null) h.selB = pw;
        else h.selB = pw;
        p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.4f);
        render(h);
    }

    private void doMix(PotHolder h) {
        Player p = h.player;
        AbilitySMP.Power result = Fusions.mix(h.selA, h.selB);
        if (result == null) {
            p.sendMessage(Component.text("Those two powers don't mix into anything. The pot just burps sadly.", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
            h.selA = null;
            h.selB = null;
            render(h);
            return;
        }
        if (plugin.profile.owns(p, result)) {
            p.sendMessage(Component.text("You already discovered " + AbilitySMP.pretty(result.key())
                    + " - no charge. Try another pair!", NamedTextColor.GRAY));
            h.selA = null;
            h.selB = null;
            render(h);
            return;
        }
        int cost = plugin.getConfig().getInt("mixing.cost-galaxy-coins", 250);
        if (plugin.shops.countGalaxyCoins(p) < cost) {
            p.sendMessage(Component.text("Mixing costs " + cost + " Galaxy Coins - you have "
                    + plugin.shops.countGalaxyCoins(p) + ". Galaxy bosses and the Galaxy Burst pay them!", NamedTextColor.RED));
            return;
        }
        plugin.shops.removeGalaxyCoins(p, cost);
        plugin.profile.addOwned(p.getUniqueId(), result);
        plugin.profile.save();
        plugin.ranks.evaluate(p);
        p.closeInventory();
        p.showTitle(Title.title(
                Component.text("NEW POWER!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(AbilitySMP.pretty(result.key()), Fusions.colorOf(result), TextDecoration.BOLD)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.3f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.2, 0), 40, 0.6, 1, 0.6, 0.2);
        p.sendMessage(Component.text("The pot bubbles over... you mixed "
                + AbilitySMP.pretty(h.selA.key()) + " + " + AbilitySMP.pretty(h.selB.key()) + " into "
                + AbilitySMP.pretty(result.key()) + "! It's in your /powers now.", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text(p.getName() + " discovered a MIXED power: "
                + AbilitySMP.pretty(result.key()) + "! (" + Fusions.countOwned(plugin, p) + "/" + Fusions.total() + ")",
                NamedTextColor.LIGHT_PURPLE));
    }

    // ------------------------------------------------------------- command

    void info(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!p.isOp()) {
                p.sendMessage(Component.text("Only an OP can do that.", NamedTextColor.RED));
                return;
            }
            plugin.give(p, potItem());
            p.sendMessage(Component.text("Here's a Mixing Pot.", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        int cost = plugin.getConfig().getInt("mixing.cost-galaxy-coins", 250);
        p.sendMessage(Component.text("THE MIXING POT", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        p.sendMessage(Component.text("Craft it: 4 chains around a cauldron. Place it and right-click it.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Put in any 2 powers you own - if they mix, you get a MIXED power (" + cost + " Galaxy Coins per discovery).", NamedTextColor.GRAY));
        p.sendMessage(Component.text("There are " + Fusions.total() + " mixed powers hiding. You've found "
                + Fusions.countOwned(plugin, p) + ". Your two powers are never eaten.", NamedTextColor.GRAY));
    }
}
