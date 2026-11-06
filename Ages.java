package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * v16 - the 1 Month Celebration season. Galaxy is gone; instead the whole
 * server plays through five weekly "ages" that keep everyone balanced:
 *
 *   Week 1  IRON AGE        - best gear anyone can use is iron
 *   Week 2  DIAMOND AGE     - diamond gear unlocks
 *   Week 3  NETHERITE AGE   - netherite gear unlocks
 *   Week 4  ADVANCES WEEK   - enchanting (and anvil combining) unlocks
 *   Week 5  FIGHT WEEK      - PvP turns on, and the celebration lands
 *
 * Gear above the week's cap cannot be crafted, smithed, swung, mined with
 * or worn (a sweep keeps taking it off). The world gets a border so the
 * server plays smaller, and every NEW chunk hides one little underground
 * base whose chest loot matches the week it was found in.
 *
 * The season starts the first day v16 boots (stored in data.yml as
 * celebration.start); /season set 1-5 (OP) overrides the week.
 */
public class Ages implements Listener {

    private final AbilitySMP plugin;
    private int lastWeek = -1;
    private final Map<UUID, Long> lastMsg = new HashMap<>();

    Ages(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Season math
    // ------------------------------------------------------------------

    static final ZoneId ZONE = ZoneId.of("America/Toronto");

    LocalDate today() {
        return LocalDate.now(ZONE);
    }

    LocalDate startDate() {
        String s = plugin.data.getString("celebration.start", "");
        if (s == null || s.isBlank()) {
            s = today().toString();
            plugin.data.set("celebration.start", s);
            plugin.saveData();
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception ex) {
            return today();
        }
    }

    int week() {
        int override = plugin.data.getInt("celebration.week-override", 0);
        if (override >= 1 && override <= 5) return override;
        long days = ChronoUnit.DAYS.between(startDate(), today());
        if (days < 0) days = 0;
        return (int) Math.min(5, days / 7 + 1);
    }

    boolean weekOverridden() {
        int o = plugin.data.getInt("celebration.week-override", 0);
        return o >= 1 && o <= 5;
    }

    /** Days left in the current week (7 on the first day); -1 when overridden. */
    int daysLeftInWeek() {
        if (weekOverridden()) return -1;
        long days = Math.max(0, ChronoUnit.DAYS.between(startDate(), today()));
        return (int) (7 - (days % 7));
    }

    String ageName(int w) {
        return switch (w) {
            case 1 -> "Iron Age";
            case 2 -> "Diamond Age";
            case 3 -> "Netherite Age";
            case 4 -> "Advances Week";
            default -> "Fight Week";
        };
    }

    /** Highest gear tier usable this week: 2 iron, 3 diamond, 4 netherite. */
    int cap() {
        int w = week();
        return w <= 1 ? 2 : w == 2 ? 3 : 4;
    }

    String capName(int tier) {
        return switch (tier) {
            case 2 -> "iron";
            case 3 -> "diamond";
            case 4 -> "netherite";
            default -> "basic";
        };
    }

    private String unlockText(int tier) {
        return tier >= 4 ? "Week 3 (Netherite Age)" : "Week 2 (Diamond Age)";
    }

    // ------------------------------------------------------------------
    // Gear tiers
    // ------------------------------------------------------------------

    /** Gear tier of an item: 0 wood/leather, 1 stone/gold, 2 iron, 3 diamond,
     *  4 netherite; -1 when the item is not tiered gear at all. */
    static int tierOf(ItemStack it) {
        if (it == null) return -1;
        return tierOf(it.getType());
    }

    static int tierOf(Material m) {
        String n = m.name();
        switch (n) {
            case "TRIDENT" -> { return 3; }
            case "MACE" -> { return 4; }
            case "ELYTRA" -> { return 3; }
            case "SHIELD" -> { return 2; }
            case "TURTLE_HELMET" -> { return 2; }
            default -> { }
        }
        boolean gear = n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE")
                || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
        if (!gear) return -1;
        if (n.startsWith("NETHERITE_")) return 4;
        if (n.startsWith("DIAMOND_")) return 3;
        if (n.startsWith("IRON_") || n.startsWith("CHAINMAIL_")) return 2;
        if (n.startsWith("GOLDEN_") || n.startsWith("STONE_")) return 1;
        return 0; // wooden / leather
    }

    boolean overCap(ItemStack it) {
        int t = tierOf(it);
        return t > cap();
    }

    private void msg(Player p, String text) {
        long now = System.currentTimeMillis();
        Long last = lastMsg.get(p.getUniqueId());
        if (last != null && now - last < 2500) return;
        lastMsg.put(p.getUniqueId(), now);
        p.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    private void tooAdvanced(Player p, ItemStack it) {
        msg(p, "That " + pretty(it) + " is too advanced for the " + ageName(week())
                + " - it unlocks in " + unlockText(tierOf(it)) + "!");
    }

    private static String pretty(ItemStack it) {
        if (it == null) return "item";
        String n = it.getType().name().toLowerCase().replace('_', ' ');
        return n.isEmpty() ? "item" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ------------------------------------------------------------------
    // Enforcement: crafting, smithing, enchanting, anvils
    // ------------------------------------------------------------------

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getRecipe() == null ? null : e.getRecipe().getResult();
        if (result != null && overCap(result)) {
            e.setCancelled(true);
            tooAdvanced(p, result);
        }
    }

    @EventHandler
    public void onSmith(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result != null && overCap(result)) e.setResult(null);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        if (week() < 4) {
            e.setCancelled(true);
            msg(e.getEnchanter(), "Enchanting is locked until Week 4 - Advances Week!");
        }
    }

    private Map<Enchantment, Integer> enchantsOf(ItemStack it) {
        Map<Enchantment, Integer> out = new HashMap<>();
        if (it == null || !it.hasItemMeta()) return out;
        ItemMeta meta = it.getItemMeta();
        out.putAll(meta.getEnchants());
        if (meta instanceof EnchantmentStorageMeta stored) out.putAll(stored.getStoredEnchants());
        return out;
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (week() >= 4) return;
        ItemStack result = e.getResult();
        if (result == null) return;
        var inv = e.getInventory();
        int inTotal = Math.max(totalLevels(enchantsOf(inv.getItem(0))), totalLevels(enchantsOf(inv.getItem(1))));
        if (totalLevels(enchantsOf(result)) > inTotal) {
            e.setResult(null); // combining to GAIN enchants waits for Advances Week
        }
    }

    private int totalLevels(Map<Enchantment, Integer> enchants) {
        int total = 0;
        for (int lvl : enchants.values()) total += lvl;
        return total;
    }

    // ------------------------------------------------------------------
    // Enforcement: fighting, mining, wearing
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // PvP stays off until Fight Week (ranked duels are exempt).
        if (e.getEntity() instanceof Player victim && week() < 5
                && plugin.getConfig().getBoolean("celebration.pvp-until-fight-week", true)) {
            Player attacker = e.getDamager() instanceof Player dp ? dp
                    : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sp ? sp : null;
            if (attacker != null && !attacker.equals(victim)
                    && (plugin.duel == null || !plugin.duel.isBusy(victim))) {
                e.setCancelled(true);
                msg(attacker, "PvP is off until Week 5 - Fight Week! For now, everyone builds up together.");
                return;
            }
        }
        // Weapons above the week's cap simply do not work yet.
        if (e.getDamager() instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (overCap(hand)) {
                e.setCancelled(true);
                tooAdvanced(p, hand);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        if (overCap(hand)) {
            e.setCancelled(true);
            tooAdvanced(e.getPlayer(), hand);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && overCap(it) && isArmor(it.getType())) {
            e.setCancelled(true);
            tooAdvanced(e.getPlayer(), it);
        }
    }

    private boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || n.equals("ELYTRA");
    }

    /** Every 5 seconds: armour above the cap comes off and goes back in the bag. */
    private void armorSweep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            ItemStack[] armor = inv.getArmorContents();
            boolean changed = false;
            for (int i = 0; i < armor.length; i++) {
                ItemStack piece = armor[i];
                if (piece != null && overCap(piece)) {
                    armor[i] = null;
                    changed = true;
                    HashMap<Integer, ItemStack> leftover = inv.addItem(piece);
                    for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
                    msg(p, "Your " + pretty(piece) + " is too advanced for the " + ageName(week())
                            + " - it unlocks in " + unlockText(tierOf(piece)) + "!");
                }
            }
            if (changed) inv.setArmorContents(armor);
        }
    }

    // ------------------------------------------------------------------
    // Chunk bases: one little underground base in every new chunk
    // ------------------------------------------------------------------

    private record Loot(Material mat, int min, int max, int weight) { }

    private List<Loot> lootPool(int w) {
        List<Loot> pool = new ArrayList<>();
        switch (w) {
            case 1 -> {
                pool.add(new Loot(Material.IRON_INGOT, 2, 5, 5));
                pool.add(new Loot(Material.COAL, 4, 10, 5));
                pool.add(new Loot(Material.TORCH, 4, 8, 4));
                pool.add(new Loot(Material.BREAD, 1, 3, 4));
                pool.add(new Loot(Material.ARROW, 4, 10, 3));
                pool.add(new Loot(Material.OAK_LOG, 4, 8, 3));
                pool.add(new Loot(Material.STRING, 2, 4, 2));
                pool.add(new Loot(Material.IRON_PICKAXE, 1, 1, 1));
            }
            case 2 -> {
                pool.add(new Loot(Material.DIAMOND, 1, 2, 4));
                pool.add(new Loot(Material.GOLD_INGOT, 2, 5, 4));
                pool.add(new Loot(Material.IRON_INGOT, 3, 6, 4));
                pool.add(new Loot(Material.LAPIS_LAZULI, 4, 8, 3));
                pool.add(new Loot(Material.REDSTONE, 4, 8, 3));
                pool.add(new Loot(Material.BREAD, 1, 3, 3));
                pool.add(new Loot(Material.ARROW, 4, 10, 2));
                pool.add(new Loot(Material.DIAMOND_PICKAXE, 1, 1, 1));
            }
            case 3 -> {
                pool.add(new Loot(Material.ANCIENT_DEBRIS, 1, 1, 3));
                pool.add(new Loot(Material.NETHERITE_SCRAP, 1, 2, 3));
                pool.add(new Loot(Material.DIAMOND, 1, 3, 4));
                pool.add(new Loot(Material.GOLD_INGOT, 3, 6, 4));
                pool.add(new Loot(Material.OBSIDIAN, 4, 8, 3));
                pool.add(new Loot(Material.ENDER_PEARL, 1, 2, 2));
                pool.add(new Loot(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1, 1));
            }
            case 4 -> {
                pool.add(new Loot(Material.EXPERIENCE_BOTTLE, 3, 8, 4));
                pool.add(new Loot(Material.LAPIS_LAZULI, 6, 12, 4));
                pool.add(new Loot(Material.BOOK, 2, 4, 3));
                pool.add(new Loot(Material.DIAMOND, 1, 2, 3));
                pool.add(new Loot(Material.IRON_INGOT, 2, 5, 3));
                pool.add(new Loot(Material.ENCHANTED_BOOK, 1, 1, 2));
            }
            default -> {
                pool.add(new Loot(Material.GOLDEN_APPLE, 1, 2, 3));
                pool.add(new Loot(Material.TNT, 2, 4, 3));
                pool.add(new Loot(Material.ENDER_PEARL, 2, 3, 3));
                pool.add(new Loot(Material.FIREWORK_ROCKET, 3, 6, 3));
                pool.add(new Loot(Material.DIAMOND, 1, 3, 3));
                pool.add(new Loot(Material.COOKED_BEEF, 2, 4, 3));
                pool.add(new Loot(Material.ARROW, 6, 12, 2));
                pool.add(new Loot(Material.TOTEM_OF_UNDYING, 1, 1, 1));
            }
        }
        return pool;
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        if (!plugin.getConfig().getBoolean("celebration.chunk-bases", true)) return;
        World world = e.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        Chunk chunk = e.getChunk();
        Random rnd = new Random(world.getSeed() ^ ((long) chunk.getX() << 32) ^ (chunk.getZ() & 0xffffffffL)
                ^ 0x1CEB00DAL);
        int bx = chunk.getX() * 16 + 3 + rnd.nextInt(10);
        int bz = chunk.getZ() * 16 + 3 + rnd.nextInt(10);
        int minY = world.getMinHeight();
        for (int attempt = 0; attempt < 6; attempt++) {
            int y = minY + 6 + rnd.nextInt(26); // deep underground
            Block centre = world.getBlockAt(bx, y + 1, bz);
            if (!centre.getType().isSolid() || centre.getType() == Material.BEDROCK) continue;
            buildBase(world, bx, y, bz, rnd);
            return;
        }
    }

    private void buildBase(World world, int bx, int by, int bz, Random rnd) {
        Material wall = by < 0 ? Material.DEEPSLATE_BRICKS : Material.STONE_BRICKS;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(world, bx + dx, by, bz + dz, wall);          // floor
                set(world, bx + dx, by + 3, bz + dz, wall);      // ceiling
                for (int dy = 1; dy <= 2; dy++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    set(world, bx + dx, by + dy, bz + dz, edge ? wall : Material.AIR);
                }
            }
        }
        set(world, bx, by + 2, bz, Material.SOUL_LANTERN);       // one little light
        Block chestBlock = world.getBlockAt(bx, by + 1, bz);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            List<Loot> pool = lootPool(week());
            int totalWeight = pool.stream().mapToInt(Loot::weight).sum();
            int rolls = 2 + rnd.nextInt(3);
            for (int i = 0; i < rolls; i++) {
                int roll = rnd.nextInt(totalWeight);
                Loot pick = pool.get(0);
                for (Loot l : pool) {
                    if (roll < l.weight()) { pick = l; break; }
                    roll -= l.weight();
                }
                ItemStack stack;
                if (pick.mat() == Material.ENCHANTED_BOOK) {
                    stack = randomBook(rnd);
                } else {
                    int amount = pick.min() + (pick.max() > pick.min() ? rnd.nextInt(pick.max() - pick.min() + 1) : 0);
                    stack = new ItemStack(pick.mat(), amount);
                }
                chest.getBlockInventory().addItem(stack);
            }
        }
    }

    private void set(World world, int x, int y, int z, Material mat) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() != Material.BEDROCK) b.setType(mat, false);
    }

    private ItemStack randomBook(Random rnd) {
        Enchantment[] choices = { Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.EFFICIENCY,
                Enchantment.UNBREAKING, Enchantment.FORTUNE, Enchantment.POWER };
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(choices[rnd.nextInt(choices.length)], 2 + rnd.nextInt(2), true);
            book.setItemMeta(meta);
        }
        return book;
    }

    // ------------------------------------------------------------------
    // Border, week rollover, tasks
    // ------------------------------------------------------------------

    void startTasks() {
        startDate(); // stamps the season start on first boot
        World world = Bukkit.getWorld("world");
        if (world != null) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(world.getSpawnLocation().getBlockX() + 0.5, world.getSpawnLocation().getBlockZ() + 0.5);
            border.setSize(plugin.getConfig().getInt("celebration.border-size", 2000));
            plugin.getLogger().info("Celebration: world border set to " + (int) border.getSize()
                    + " blocks for the smaller server.");
        }
        lastWeek = week();
        Bukkit.getScheduler().runTaskTimer(plugin, this::armorSweep, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int w = week();
            if (lastWeek != -1 && w != lastWeek) {
                lastWeek = w;
                announceWeek(w);
            }
        }, 1200L, 1200L);
    }

    private void announceWeek(int w) {
        Component line = Component.text("The season moves on: Week " + w + " - " + ageName(w) + "!",
                NamedTextColor.GOLD);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
            p.showTitle(Title.title(
                    Component.text("WEEK " + w, NamedTextColor.GOLD),
                    Component.text(ageName(w), NamedTextColor.YELLOW)));
        }
        plugin.getLogger().info("Celebration season: week " + w + " (" + ageName(w) + ") has begun.");
    }

    // ------------------------------------------------------------------
    // /season
    // ------------------------------------------------------------------

    boolean command(Player p, String[] args) {
        boolean op = p.isOp();
        if (op && args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                p.sendMessage(Component.text("Usage: /season set <1-5> | /season clear | /season start <yyyy-mm-dd>", NamedTextColor.RED));
                return true;
            }
            int w;
            try {
                w = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                w = 0;
            }
            if (w < 1 || w > 5) {
                p.sendMessage(Component.text("The week must be 1 to 5.", NamedTextColor.RED));
                return true;
            }
            plugin.data.set("celebration.week-override", w);
            plugin.saveData();
            lastWeek = w;
            p.sendMessage(Component.text("Season week set to Week " + w + " - " + ageName(w) + ".", NamedTextColor.GREEN));
            announceWeek(w);
            return true;
        }
        if (op && args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
            plugin.data.set("celebration.week-override", null);
            plugin.saveData();
            lastWeek = week();
            p.sendMessage(Component.text("Week override cleared - back to the calendar (Week " + week() + ").", NamedTextColor.GREEN));
            return true;
        }
        if (op && args.length >= 2 && args[0].equalsIgnoreCase("start")) {
            try {
                LocalDate d = LocalDate.parse(args[1].trim());
                plugin.data.set("celebration.start", d.toString());
                plugin.data.set("celebration.week-override", null);
                plugin.saveData();
                lastWeek = week();
                p.sendMessage(Component.text("Season start set to " + d + " - now Week " + week() + " (" + ageName(week()) + ").", NamedTextColor.GREEN));
            } catch (Exception ex) {
                p.sendMessage(Component.text("Use a date like 2026-11-05.", NamedTextColor.RED));
            }
            return true;
        }
        int w = week();
        p.sendMessage(Component.text("=== 1 MONTH CELEBRATION ===", NamedTextColor.GOLD));
        p.sendMessage(Component.text("Week " + w + " of 5: " + ageName(w)
                + (weekOverridden() ? " (set by an admin)" : daysLeftInWeek() > 0 ? " - " + daysLeftInWeek() + " day(s) left" : ""), NamedTextColor.YELLOW));
        p.sendMessage(Component.text("Best gear right now: " + capName(cap())
                + (w < 3 ? " (better gear unlocks as the weeks pass)" : " - the best there is"), NamedTextColor.WHITE));
        p.sendMessage(Component.text("Enchanting: " + (w >= 4 ? "UNLOCKED" : "locked until Week 4 (Advances Week)"), NamedTextColor.WHITE));
        p.sendMessage(Component.text("PvP: " + (w >= 5 ? "ON - it's Fight Week!" : "off until Week 5 (Fight Week)"), NamedTextColor.WHITE));
        p.sendMessage(Component.text("Every new chunk hides a little underground base with loot for this week. "
                + "The world border keeps the server small: " + plugin.getConfig().getInt("celebration.border-size", 2000) + " blocks.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Weeks: 1 Iron Age - 2 Diamond Age - 3 Netherite Age - 4 Advances Week - 5 Fight Week & Celebration", NamedTextColor.GRAY));
        return true;
    }
}
