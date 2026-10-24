package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
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

    Locker(AbilitySMP plugin) {
        this.plugin = plugin;
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
                return out;
            }
        });

        // Titles and cosmetics: ready for future content. Add entries here, or register a new Category.
        register(new Category() {
            public String id() { return "title"; }
            public String title() { return "Titles"; }
            public Material icon() { return Material.OAK_SIGN; }
            public List<Entry> entries() { return new ArrayList<>(); }
        });
        register(new Category() {
            public String id() { return "cosmetic"; }
            public String title() { return "Cosmetics"; }
            public Material icon() { return Material.LEATHER_HELMET; }
            public List<Entry> entries() { return new ArrayList<>(); }
        });
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
                default -> null;
            };
            if (part != null) p.getWorld().spawnParticle(part, p.getLocation().add(0, 0.1, 0), 2, 0.2, 0.05, 0.2, 0.01);
        }
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
