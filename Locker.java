package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * /locker: a permanent collection of unlockables. New categories are added with {@link #register(Category)}
 * and need no changes anywhere else. Unlocks and equipped items are stored in profile.yml.
 */
final class Locker implements Listener {

    /** One unlockable item. Its condition is checked now and then; once true the unlock is saved forever. */
    static final class Entry {
        final String id;
        final String name;
        final Material icon;
        final String requirement;
        final Predicate<Player> condition;

        Entry(String id, String name, Material icon, String requirement, Predicate<Player> condition) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.requirement = requirement;
            this.condition = condition;
        }
    }

    interface Category {
        String id();

        String title();

        Material icon();

        List<Entry> entries();

        /** Called after something in this category is equipped or unequipped. */
        default void onChange(Player p) {
        }
    }

    private final AbilitySMP plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final NamespacedKey cosmeticKey;
    /** Real helmet/chestplate a player was wearing before a costume covered it (restored on unequip). */
    private final Map<UUID, ItemStack> savedHelmet = new HashMap<>();
    private final Map<UUID, ItemStack> savedChest = new HashMap<>();

    Locker(AbilitySMP plugin) {
        this.plugin = plugin;
        this.cosmeticKey = new NamespacedKey(plugin, "cosmetic_item");
        registerBuiltIn();
        Bukkit.getScheduler().runTaskTimer(plugin, this::trailTick, 10L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) checkUnlocks(p);
        }, 1200L, 1200L);
    }

    void register(Category c) {
        categories.put(c.id(), c);
    }

    // ------------------------------------------------------------ built-in categories

    private void registerBuiltIn() {
        // Ranks: normal and Halloween. Ranks are unlocked by the rank system, the locker only equips them.
        register(new Category() {
            public String id() { return "rank"; }
            public String title() { return "Ranks"; }
            public Material icon() { return Material.NAME_TAG; }
            public List<Entry> entries() {
                List<Entry> out = new ArrayList<>();
                for (Ranks.Rank r : plugin.ranks.all()) {
                    out.add(new Entry(r.id, r.name, r.halloween ? Material.CARVED_PUMPKIN : Material.NAME_TAG,
                            r.requirement, p -> false));
                }
                return out;
            }
            public void onChange(Player p) { plugin.ranks.refresh(p); }
        });

        // Trails: particles behind you. Unlocked by reaching ranks.
        register(new Category() {
            public String id() { return "trail"; }
            public String title() { return "Trails"; }
            public Material icon() { return Material.BLAZE_POWDER; }
            public List<Entry> entries() {
                List<Entry> out = new ArrayList<>();
                out.add(rankTrail("soul", "Soul Trail", Material.SOUL_LANTERN, "hw-power-king", "Halloween Power King"));
                out.add(rankTrail("flame", "Flame Trail", Material.BLAZE_POWDER, "unbound-pro", "Unbound Pro"));
                out.add(rankTrail("end-rod", "Star Trail", Material.END_ROD, "unbound-expert", "Unbound Expert"));
                out.add(rankTrail("heart", "Heart Trail", Material.RED_DYE, "unbound-king", "Unbound King"));
                out.add(rankTrail("witch", "Witch Trail", Material.BLAZE_ROD, "mimic-bane", "Chest Breaker"));
                out.add(rankTrail("note", "Music Trail", Material.NOTE_BLOCK, "ranked-star", "Ranked Star"));
                out.add(rankTrail("snow", "Snow Trail", Material.SNOWBALL, "time-lord", "Time Lord"));
                return out;
            }
        });

        // Titles: shown after your name in chat and the tab list. Earned by beating things.
        register(new Category() {
            public String id() { return "title"; }
            public String title() { return "Titles"; }
            public Material icon() { return Material.OAK_SIGN; }
            public List<Entry> entries() {
                List<Entry> out = new ArrayList<>();
                out.add(new Entry("boss-slayer", "Boss Slayer", Material.WITHER_SKELETON_SKULL,
                        "Defeat any boss", p -> plugin.profile.of(p).bossKills >= 1));
                out.add(new Entry("horseman-slayer", "Horseman Slayer", Material.JACK_O_LANTERN,
                        "Defeat the Headless Horseman", p -> plugin.profile.of(p).horsemanDefeated));
                out.add(new Entry("mimic-slayer", "Mimic Slayer", Material.CHEST,
                        "Defeat the Mimic", p -> plugin.profile.of(p).mimicDefeated));
                out.add(new Entry("ghost-buster", "Ghost Buster", Material.SOUL_LANTERN,
                        "Defeat the Ghost", p -> plugin.profile.of(p).ghostDefeated));
                out.add(new Entry("boss-hunter", "Boss Hunter", Material.NETHERITE_SWORD,
                        "Defeat 25 bosses", p -> plugin.profile.of(p).bossKills >= 25));
                out.add(new Entry("duelist", "Duelist", Material.GOLDEN_SWORD,
                        "Win 10 friendly duels", p -> plugin.profile.of(p).duelWins >= 10));
                out.add(new Entry("quest-hero", "Quest Hero", Material.BOOK,
                        "Complete 50 quests", p -> plugin.unbound.completed(p) >= 50));
                out.add(new Entry("the-reborn", "The Reborn", Material.BEACON,
                        "Rebirth at least once (/rebirth)", p -> plugin.profile.of(p).rebirths >= 1));
                out.add(new Entry("ranked-royalty", "Ranked Royalty", Material.GOLDEN_HELMET,
                        "Reach 2000 ranked rating (/ranked)", p -> plugin.profile.of(p).rankedRating >= 2000));
                out.add(new Entry("unbound-king", "Unbound King", Material.NETHER_STAR,
                        "Reach 2400 ranked rating - the best rank there is", p -> plugin.profile.of(p).rankedRating >= 2400));
                out.add(new Entry("the-completionist", "The Completionist", Material.DRAGON_EGG,
                        "Own every power", p -> plugin.profile.of(p).owned.size() >= AbilitySMP.Power.values().length));
                return out;
            }
            public void onChange(Player p) { plugin.ranks.refresh(p); }
        });

        // Cosmetics: Halloween costumes you actually wear (a hat, or the Vampire Cape).
        // Your real armor is kept safe and given back when you take the costume off.
        register(new Category() {
            public String id() { return "cosmetic"; }
            public String title() { return "Cosmetics"; }
            public Material icon() { return Material.LEATHER_HELMET; }
            public List<Entry> entries() {
                List<Entry> out = new ArrayList<>();
                out.add(new Entry("pumpkin-head", "Pumpkin Costume", Material.CARVED_PUMPKIN,
                        "Defeat the Headless Horseman", p -> plugin.profile.of(p).horsemanDefeated));
                out.add(new Entry("ghost-sheet", "Ghost Costume", Material.WHITE_WOOL,
                        "Defeat the Ghost", p -> plugin.profile.of(p).ghostDefeated));
                out.add(new Entry("skeleton-suit", "Skeleton Costume", Material.SKELETON_SKULL,
                        "Defeat 5 bosses", p -> plugin.profile.of(p).bossKills >= 5));
                out.add(new Entry("zombie-suit", "Zombie Costume", Material.ZOMBIE_HEAD,
                        "Play for 120 minutes in total", p -> plugin.profile.of(p).playMinutes >= 120));
                out.add(new Entry("wither-suit", "Wither Costume", Material.WITHER_SKELETON_SKULL,
                        "Defeat the Mimic", p -> plugin.profile.of(p).mimicDefeated));
                out.add(new Entry("witch-hat", "Witch Costume", Material.LEATHER_HELMET,
                        "Own the Witch power", p -> plugin.profile.owns(p, AbilitySMP.Power.WITCH)));
                out.add(new Entry("vampire-cape", "Vampire Costume", Material.LEATHER_CHESTPLATE,
                        "Own the Vampire power", p -> plugin.profile.owns(p, AbilitySMP.Power.VAMPIRE)));
                out.add(new Entry("dragon-suit", "Dragon Costume", Material.DRAGON_HEAD,
                        "Own every power", p -> plugin.profile.of(p).owned.size() >= AbilitySMP.Power.values().length));
                return out;
            }
            public void onChange(Player p) { applyCosmetics(p); }
        });
    }

    List<Entry> entriesOf(String categoryId) {
        Category c = categories.get(categoryId);
        return c == null ? List.of() : c.entries();
    }

    /** The display name of the player's equipped title, or null if they have none. */
    String titleName(UUID id) {
        String eq = plugin.profile.equipped(id, "title");
        if (eq == null) return null;
        for (Entry e : entriesOf("title")) if (e.id.equals(eq)) return e.name;
        return null;
    }

    private Entry rankTrail(String id, String name, Material icon, String rankId, String rankName) {
        return new Entry(id, name, icon, "Unlock the " + rankName + " rank",
                p -> plugin.profile.isUnlocked(p.getUniqueId(), "rank:" + rankId));
    }

    private void trailTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String t = plugin.profile.equipped(p.getUniqueId(), "trail");
            if (t == null || p.isSneaking()) continue;
            Particle part = switch (t) {
                case "soul" -> Particle.SOUL;
                case "flame" -> Particle.FLAME;
                case "end-rod" -> Particle.END_ROD;
                case "heart" -> Particle.HEART;
                case "witch" -> Particle.WITCH;
                case "note" -> Particle.NOTE;
                case "snow" -> Particle.SNOWFLAKE;
                default -> null;
            };
            if (part != null) p.getWorld().spawnParticle(part, p.getLocation().add(0, 0.1, 0), 2, 0.2, 0.05, 0.2, 0.01);
        }
    }

    // ------------------------------------------------------------ costumes (they really go on your body)

    private ItemStack costumeItem(String cosmeticId, Material m, String name, Integer rgb) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof LeatherArmorMeta lm && rgb != null) lm.setColor(Color.fromRGB(rgb));
        meta.displayName(Menus.white(name));
        meta.lore(java.util.List.of(Menus.white("Costume - equip or remove it in /locker")));
        meta.getPersistentDataContainer().set(cosmeticKey, PersistentDataType.STRING, cosmeticId);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack costumeHelmet(String id) {
        return switch (id) {
            case "pumpkin-head" -> costumeItem(id, Material.CARVED_PUMPKIN, "Pumpkin Head", null);
            case "ghost-sheet" -> costumeItem(id, Material.LEATHER_HELMET, "Ghost Sheet", 0xF2F2F2);
            case "skeleton-suit" -> costumeItem(id, Material.SKELETON_SKULL, "Skeleton Head", null);
            case "zombie-suit" -> costumeItem(id, Material.ZOMBIE_HEAD, "Zombie Head", null);
            case "wither-suit" -> costumeItem(id, Material.WITHER_SKELETON_SKULL, "Wither Head", null);
            case "witch-hat" -> costumeItem(id, Material.LEATHER_HELMET, "Witch Hat", 0x2A0A4A);
            case "dragon-suit" -> costumeItem(id, Material.DRAGON_HEAD, "Dragon Head", null);
            default -> null;
        };
    }

    private ItemStack costumeChest(String id) {
        return switch (id) {
            case "vampire-cape" -> costumeItem(id, Material.LEATHER_CHESTPLATE, "Vampire Cape", 0x6E0E0E);
            default -> null;
        };
    }

    private String costumeTag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(cosmeticKey, PersistentDataType.STRING);
    }

    boolean isCostume(ItemStack it) {
        return costumeTag(it) != null;
    }

    /** Puts the equipped costume on (or takes it off). Real armor is saved and restored. */
    void applyCosmetics(Player p) {
        String id = plugin.profile.equipped(p.getUniqueId(), "cosmetic");
        applySlot(p, true, id == null ? null : costumeHelmet(id), id);
        applySlot(p, false, id == null ? null : costumeChest(id), id);
    }

    private void applySlot(Player p, boolean helmet, ItemStack want, String wantId) {
        Map<UUID, ItemStack> saved = helmet ? savedHelmet : savedChest;
        ItemStack cur = helmet ? p.getInventory().getHelmet() : p.getInventory().getChestplate();
        String curTag = costumeTag(cur);
        if (want == null) { // costume off: give the real armor back
            if (curTag != null) setSlot(p, helmet, saved.remove(p.getUniqueId()));
            else saved.remove(p.getUniqueId());
            return;
        }
        if (wantId != null && wantId.equals(curTag)) return; // already wearing this costume piece
        if (curTag == null) {
            saved.put(p.getUniqueId(), cur == null ? new ItemStack(Material.AIR) : cur.clone());
        }
        setSlot(p, helmet, want);
    }

    private void setSlot(Player p, boolean helmet, ItemStack it) {
        if (it != null && it.getType() == Material.AIR) it = null;
        if (helmet) p.getInventory().setHelmet(it);
        else p.getInventory().setChestplate(it);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // costumes never drop: they come back on when you respawn
        e.getDrops().removeIf(this::isCostume);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) applyCosmetics(e.getPlayer());
        }, 10L);
    }

    // ------------------------------------------------------------ unlocking

    private static String key(Category c, Entry e) {
        return c.id() + ":" + e.id;
    }

    boolean isUnlocked(Player p, Category c, Entry e) {
        return plugin.profile.isUnlocked(p.getUniqueId(), key(c, e));
    }

    /** Saves every unlock the player now qualifies for. Unlocks are never removed again. */
    void checkUnlocks(Player p) {
        UUID id = p.getUniqueId();
        for (Category c : categories.values()) {
            for (Entry e : c.entries()) {
                if (plugin.profile.isUnlocked(id, key(c, e)) || !e.condition.test(p)) continue;
                plugin.profile.unlock(id, key(c, e));
                p.sendMessage(Component.text("Locker: you unlocked " + e.name + " (" + c.title() + ")!", NamedTextColor.GOLD));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        checkUnlocks(e.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) applyCosmetics(e.getPlayer());
        }, 20L);
    }

    // ------------------------------------------------------------ menus

    void open(Player p) {
        checkUnlocks(p);
        new Menus.Paged("Locker") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                for (Category c : categories.values()) {
                    int total = c.entries().size();
                    int have = 0;
                    for (Entry e : c.entries()) if (isUnlocked(pl, c, e)) have++;
                    String eq = plugin.profile.equipped(pl.getUniqueId(), c.id());
                    out.add(new Menus.Entry(Menus.icon(c.icon(), c.title(), false,
                            total == 0 ? "Coming soon" : "Unlocked: " + have + " / " + total,
                            eq == null ? "Nothing equipped" : "Equipped: " + eq, "Click to open"),
                            (pp, t) -> openCategory(pp, c)));
                }
                return out;
            }
        }.open(p);
    }

    void openCategory(Player p, Category c) {
        Menus.Paged menu = new Menus.Paged("Locker - " + c.title()) {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                String eq = plugin.profile.equipped(pl.getUniqueId(), c.id());
                if (c.entries().isEmpty()) {
                    out.add(new Menus.Entry(Menus.icon(Material.BARRIER, "Nothing here yet", false,
                            "New " + c.title().toLowerCase() + " will appear here."), null));
                }
                for (Entry e : c.entries()) {
                    boolean unlocked = isUnlocked(pl, c, e);
                    boolean equipped = e.id.equals(eq);
                    if (unlocked) {
                        out.add(new Menus.Entry(Menus.icon(e.icon, e.name, equipped,
                                equipped ? "EQUIPPED" : "UNLOCKED",
                                equipped ? "Click to unequip" : "Click to equip"),
                                (pp, t) -> toggle(pp, c, e)));
                    } else {
                        out.add(new Menus.Entry(Menus.icon(Material.BARRIER, e.name, false, "LOCKED",
                                "How to unlock: " + e.requirement),
                                (pp, t) -> pp.sendMessage(Component.text(e.name + " is locked. " + e.requirement + ".",
                                        NamedTextColor.GRAY))));
                    }
                }
                return out;
            }
        };
        menu.back = () -> open(p);
        menu.open(p);
    }

    private void toggle(Player p, Category c, Entry e) {
        UUID id = p.getUniqueId();
        if (e.id.equals(plugin.profile.equipped(id, c.id()))) {
            plugin.profile.setEquipped(id, c.id(), null);
            p.sendMessage(Component.text("Unequipped " + e.name + ".", NamedTextColor.GRAY));
        } else {
            plugin.profile.setEquipped(id, c.id(), e.id);
            p.sendMessage(Component.text("Equipped " + e.name + ".", NamedTextColor.GREEN));
        }
        c.onChange(p);
    }
}
