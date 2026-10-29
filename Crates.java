package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Power Crates (v9): the new way to get powers. A crate plus a key (right-click the crate) spins a
 * roulette and lands on a random power, weighted by rarity - Commons are common, Mythics are rare.
 * You win the power as a power star, the same item the shops hand out. Crates and keys come from
 * boss fights, the Galaxy Shop, quests and rebirths.
 */
final class Crates implements Listener {

    static final class CrateHolder implements InventoryHolder {
        final Player owner;
        final AbilitySMP.Power result;
        Inventory inv;
        int step;          // how many roulette moves have happened
        int wait;          // ticks until the next move (grows = the wheel slows down)
        boolean done;

        CrateHolder(Player owner, AbilitySMP.Power result) {
            this.owner = owner;
            this.result = result;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey crateKey;
    private final NamespacedKey keyKey;
    private final Random random = new Random();

    Crates(AbilitySMP plugin) {
        this.plugin = plugin;
        this.crateKey = new NamespacedKey(plugin, "power_crate");
        this.keyKey = new NamespacedKey(plugin, "crate_key");
        loadChests();
    }

    // =====================================================================
    // The items
    // =====================================================================

    ItemStack crateItem(int amount) {
        ItemStack it = new ItemStack(Material.CHEST);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Power Crate", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Right-click with a Power Crate Key in your inventory", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("to spin for a random power. Rarer powers are rarer!", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        it.setAmount(Math.max(1, amount));
        return it;
    }

    ItemStack keyItem(int amount) {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Power Crate Key", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Opens one Power Crate. Right-click the crate!", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(keyKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        it.setAmount(Math.max(1, amount));
        return it;
    }

    boolean isCrate(ItemStack it) {
        return it != null && it.getType() == Material.CHEST && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(crateKey, PersistentDataType.STRING);
    }

    boolean isKey(ItemStack it) {
        return it != null && it.getType() == Material.TRIPWIRE_HOOK && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyKey, PersistentDataType.STRING);
    }

    void giveSet(Player p, int n) {
        plugin.give(p, crateItem(n));
        plugin.give(p, keyItem(n));
        p.sendMessage(Component.text("You got " + n + " Power Crate" + (n > 1 ? "s" : "") + " and " + n + " key"
                + (n > 1 ? "s" : "") + "! Right-click a crate to open it.", NamedTextColor.GOLD));
    }

    void giveKeys(Player p, int n) {
        plugin.give(p, keyItem(n));
        p.sendMessage(Component.text("You got " + n + " Power Crate Key" + (n > 1 ? "s" : "") + "!", NamedTextColor.AQUA));
    }

    int count(Player p, boolean crates) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (crates ? isCrate(it) : isKey(it)) n += it.getAmount();
        }
        return n;
    }

    /** Boss loot: keys (and sometimes a crate) drop from boss victories. */
    void rewardForBoss(Player p, String bossId) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("crates.boss-drops");
        double keyChance = c == null ? 0.3 : c.getDouble(bossId + "-key-chance", 0.3);
        double crateChance = c == null ? 0.0 : c.getDouble(bossId + "-crate-chance", 0.0);
        if (random.nextDouble() < keyChance) giveKeys(p, 1);
        if (random.nextDouble() < crateChance) {
            plugin.give(p, crateItem(1));
            p.sendMessage(Component.text("The boss dropped a Power Crate!", NamedTextColor.GOLD));
        }
    }

    // =====================================================================
    // Rolling
    // =====================================================================

    private int weight(AbilitySMP.Rarity r) {
        ConfigurationSection w = plugin.getConfig().getConfigurationSection("crates.weights");
        int def = switch (r) {
            case COMMON -> 50;
            case RARE -> 30;
            case EPIC -> 13;
            case LEGENDARY -> 5;
            case MYTHIC -> 2;
        };
        return w == null ? def : Math.max(0, w.getInt(r.name().toLowerCase(), def));
    }

    /** A random enabled power the player does not own yet, weighted by rarity. Null if they own them all. */
    AbilitySMP.Power rollPower(Player p) {
        List<AbilitySMP.Power> pool = new ArrayList<>();
        for (AbilitySMP.Power pw : AbilitySMP.Power.values()) {
            if (pw.isHalloween() || pw == AbilitySMP.Power.WIND) continue; // v10: retired powers and the one-of-a-kind Wind never drop
            if (plugin.isEnabled(pw) && !plugin.profile.owns(p, pw)) pool.add(pw);
        }
        if (pool.isEmpty()) return null;
        // pick a rarity first (only rarities that still have un-owned powers), then a power inside it
        List<AbilitySMP.Rarity> rarities = new ArrayList<>();
        int total = 0;
        for (AbilitySMP.Rarity r : AbilitySMP.Rarity.values()) {
            boolean any = false;
            for (AbilitySMP.Power pw : pool) if (pw.rarity() == r) { any = true; break; }
            if (any && weight(r) > 0) {
                rarities.add(r);
                total += weight(r);
            }
        }
        if (rarities.isEmpty()) return pool.get(random.nextInt(pool.size()));
        int roll = random.nextInt(total);
        AbilitySMP.Rarity picked = rarities.get(rarities.size() - 1);
        for (AbilitySMP.Rarity r : rarities) {
            roll -= weight(r);
            if (roll < 0) {
                picked = r;
                break;
            }
        }
        List<AbilitySMP.Power> inRarity = new ArrayList<>();
        for (AbilitySMP.Power pw : pool) if (pw.rarity() == picked) inRarity.add(pw);
        return inRarity.get(random.nextInt(inRarity.size()));
    }

    private ItemStack icon(AbilitySMP.Power pw) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(AbilitySMP.pretty(pw.key()) + " Power", pw.rarity().color)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(pw.rarity().display, pw.rarity().color).decoration(TextDecoration.ITALIC, false)));
        if (pw.rarity() == AbilitySMP.Rarity.LEGENDARY || pw.rarity() == AbilitySMP.Rarity.MYTHIC) {
            m.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(m);
        return it;
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        it.setItemMeta(m);
        return it;
    }

    // =====================================================================
    // Opening: right-click a crate while you hold a key
    // =====================================================================

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() != null && isCrateChest(e.getClickedBlock())) return; // the spawn crate chest handles itself
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        if (isKey(hand)) {
            e.setCancelled(true);
            plugin.bar(p, "That key opens a Power Crate - right-click the crate!", NamedTextColor.AQUA);
            return;
        }
        if (!isCrate(hand)) return;
        e.setCancelled(true);
        open(p, hand);
    }

    /** Shared checks before any spin: not mid-fight, not already spinning. */
    private boolean readyToSpin(Player p) {
        if (plugin.fightTotem.isProtected(p) || plugin.duel.isBusy(p) || plugin.horseman.isBusy(p)
                || plugin.hallow.isBusy(p) || plugin.trading.isBusy(p) || plugin.ranked.isBusy(p)) {
            plugin.bar(p, "You can't open a crate in the middle of a fight.", NamedTextColor.RED);
            return false;
        }
        return !(p.getOpenInventory().getTopInventory().getHolder() instanceof CrateHolder); // already spinning
    }

    private int findKey(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (isKey(contents[i])) return i;
        return -1;
    }

    private void consumeKey(Player p, int slot) {
        ItemStack keyStack = p.getInventory().getItem(slot);
        if (keyStack == null) return;
        keyStack.setAmount(keyStack.getAmount() - 1);
        if (keyStack.getAmount() <= 0) p.getInventory().setItem(slot, null);
    }

    private void noKey(Player p) {
        plugin.bar(p, "You need a Power Crate Key! Get keys from bosses, quests and the Galaxy Shop.",
                NamedTextColor.RED);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /** Opening a crate ITEM: costs one key and the crate itself. */
    private void open(Player p, ItemStack hand) {
        if (!readyToSpin(p)) return;
        int keySlot = findKey(p);
        if (keySlot < 0) {
            noKey(p);
            return;
        }
        AbilitySMP.Power result = rollPower(p);
        if (result == null) {
            plugin.bar(p, "You already own every power! The crate stays closed.", NamedTextColor.GRAY);
            return;
        }
        consumeKey(p, keySlot);
        hand.setAmount(hand.getAmount() - 1);
        startWheel(p, result);
    }

    /** Opening a spawn crate CHEST (v11): the chest stays forever, it only costs one key. */
    void openChestCrate(Player p) {
        if (!readyToSpin(p)) return;
        int keySlot = findKey(p);
        if (keySlot < 0) {
            noKey(p);
            return;
        }
        AbilitySMP.Power result = rollPower(p);
        if (result == null) {
            plugin.bar(p, "You already own every power! The crate stays closed.", NamedTextColor.GRAY);
            return;
        }
        consumeKey(p, keySlot);
        startWheel(p, result);
    }

    private void startWheel(Player p, AbilitySMP.Power result) {
        plugin.unbound.addProgress(p, "OPEN_CRATE", 1);

        CrateHolder holder = new CrateHolder(p, result);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Power Crate", NamedTextColor.GOLD));
        holder.inv = inv;
        // the wheel: 9 visible slots (9-17), the winner lands in the middle (13)
        List<AbilitySMP.Power> wheel = new ArrayList<>();
        List<AbilitySMP.Power> anyPool = new ArrayList<>();
        for (AbilitySMP.Power pw : AbilitySMP.Power.values()) if (plugin.isEnabled(pw)) anyPool.add(pw);
        int totalSteps = 26 + random.nextInt(6);
        for (int i = 0; i < totalSteps; i++) wheel.add(anyPool.get(random.nextInt(anyPool.size())));
        int resultIndex = wheel.size();
        wheel.add(result);
        for (int i = 0; i < 4; i++) wheel.add(anyPool.get(random.nextInt(anyPool.size()))); // padding right of the winner
        ItemStack pane = filler();
        for (int s = 0; s < 27; s++) inv.setItem(s, pane);
        ItemStack marker = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta mm = marker.getItemMeta();
        mm.displayName(Component.text("▼ your prize lands here ▼", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        marker.setItemMeta(mm);
        inv.setItem(4, marker);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.1f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (holder.done) {
                    cancel();
                    return;
                }
                ticks++;
                if (ticks < holder.wait) return;
                ticks = 0;
                holder.step++;
                // the wheel slows down: moves get further and further apart
                holder.wait = holder.step < 12 ? 2 : holder.step < 18 ? 3 : holder.step < 22 ? 5 : holder.step < 25 ? 8 : 12;
                int offset = holder.step;
                for (int i = 0; i < 9; i++) {
                    int idx = offset + i - 4;
                    AbilitySMP.Power pw = idx >= 0 && idx < wheel.size() ? wheel.get(idx) : wheel.get(wheel.size() - 1);
                    inv.setItem(9 + i, icon(pw));
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
                if (holder.step >= resultIndex) {
                    finish(holder);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finish(CrateHolder h) {
        if (h.done) return;
        h.done = true;
        Player p = h.owner;
        AbilitySMP.Power result = h.result;
        if (p.isOnline()) {
            p.closeInventory();
            plugin.give(p, plugin.powerItem(result));
            p.sendMessage(Component.text("The crate gave you the " + AbilitySMP.pretty(result.key()) + " power ("
                    + result.rarity().display + ")! Right-click the star to unlock it.", result.rarity().color));
            p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(AbilitySMP.pretty(result.key()) + "!", result.rarity().color),
                    Component.text(result.rarity().display + " power from the Power Crate", NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                            java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.9f);
            p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.5, 0.4, 0.1);
            plugin.ranks.evaluate(p);
        }
    }

    // =====================================================================
    // The roulette menu is view-only; closing it early pays out right away
    // =====================================================================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof CrateHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof CrateHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof CrateHolder h && !h.done) {
            // closed mid-spin: the prize is already decided, so hand it over now (nothing is ever lost)
            Bukkit.getScheduler().runTask(plugin, () -> finish(h));
        }
    }

    // =====================================================================
    // Spawn crate chests (v11): real chest blocks at spawn, DonutSMP style.
    // Right-click one with a Power Crate Key in your inventory to spin.
    // The chest is never used up - only the key is. Set one with /crate set
    // while looking at a chest, remove it with /crate unset (or break it as an OP).
    // =====================================================================

    private final java.util.Set<String> crateChests = new java.util.HashSet<>();

    private static String blockId(org.bukkit.block.Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void loadChests() {
        crateChests.clear();
        crateChests.addAll(plugin.data.getStringList("crate-chests"));
    }

    private void saveChests() {
        plugin.data.set("crate-chests", new ArrayList<>(crateChests));
        plugin.saveData();
    }

    boolean isCrateChest(org.bukkit.block.Block b) {
        return b != null && (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST)
                && crateChests.contains(blockId(b));
    }

    boolean addCrateChest(org.bukkit.block.Block b) {
        if (!crateChests.add(blockId(b))) return false;
        saveChests();
        return true;
    }

    boolean removeCrateChest(org.bukkit.block.Block b) {
        if (!crateChests.remove(blockId(b))) return false;
        saveChests();
        return true;
    }

    @EventHandler
    public void onChestClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isCrateChest(e.getClickedBlock())) return;
        e.setCancelled(true); // the chest itself never opens
        openChestCrate(e.getPlayer());
    }

    @EventHandler
    public void onChestBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (!isCrateChest(e.getBlock())) return;
        if (e.getPlayer().hasPermission("abilitysmp.admin")) {
            removeCrateChest(e.getBlock());
            e.getPlayer().sendMessage(Component.text("Spawn crate removed.", NamedTextColor.GRAY));
        } else {
            e.setCancelled(true);
            plugin.bar(e.getPlayer(), "That is a spawn crate - it can't be broken.", NamedTextColor.RED);
        }
    }

    @EventHandler
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        e.blockList().removeIf(this::isCrateChest);
    }

    @EventHandler
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent e) {
        e.blockList().removeIf(this::isCrateChest);
    }

    // =====================================================================
    // Command: /crate   /crate give <player> [amount]   /crate key <player> [amount]
    //          /crate set   /crate unset   (admins: turn the chest you are looking at into a spawn crate)
    // =====================================================================

    void command(CommandSender s, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("unset"))
                && s.hasPermission("abilitysmp.admin")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Players only.");
                return;
            }
            org.bukkit.block.Block b = p.getTargetBlockExact(6);
            if (b == null || (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST)) {
                s.sendMessage("Look at a chest (within 6 blocks) and try again.");
                return;
            }
            if (args[0].equalsIgnoreCase("set")) {
                s.sendMessage(addCrateChest(b)
                        ? "That chest is now a spawn Power Crate - right-click it with a key to spin!"
                        : "That chest is already a spawn crate.");
            } else {
                s.sendMessage(removeCrateChest(b) ? "That chest is a normal chest again."
                        : "That chest is not a spawn crate.");
            }
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give") && s.hasPermission("abilitysmp.admin")) {
            Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
            if (t == null) {
                s.sendMessage("Usage: /crate give <player> [amount]");
                return;
            }
            int n = 1;
            if (args.length >= 3) {
                try {
                    n = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                } catch (NumberFormatException ignored) {
                }
            }
            giveSet(t, n);
            s.sendMessage("Gave " + n + " Power Crate set(s) to " + t.getName() + ".");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("key") && s.hasPermission("abilitysmp.admin")) {
            Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
            if (t == null) {
                s.sendMessage("Usage: /crate key <player> [amount]");
                return;
            }
            int n = 1;
            if (args.length >= 3) {
                try {
                    n = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                } catch (NumberFormatException ignored) {
                }
            }
            giveKeys(t, n);
            s.sendMessage("Gave " + n + " Power Crate Key(s) to " + t.getName() + ".");
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only. Admins: /crate give <player> [amount]");
            return;
        }
        p.sendMessage(Component.text("===== Power Crates =====", NamedTextColor.GOLD));
        p.sendMessage(Component.text("You have " + count(p, true) + " crate(s) and " + count(p, false)
                + " key(s). Right-click a crate to open it - or use a crate chest at /spawn, "
                + "where only the key is used up!", NamedTextColor.YELLOW));
        p.sendMessage(Component.text("Odds: Common " + weight(AbilitySMP.Rarity.COMMON) + " / Rare "
                + weight(AbilitySMP.Rarity.RARE) + " / Epic " + weight(AbilitySMP.Rarity.EPIC) + " / Legendary "
                + weight(AbilitySMP.Rarity.LEGENDARY) + " / Mythic " + weight(AbilitySMP.Rarity.MYTHIC),
                NamedTextColor.GRAY));
        p.sendMessage(Component.text("Get them from: boss fights, the Galaxy Shop (/gshop), rebirths (/rebirth).",
                NamedTextColor.GRAY));
    }
}
