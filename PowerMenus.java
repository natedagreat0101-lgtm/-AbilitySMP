package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** The power menu, quest menu and power shop. All use the shared scrollable black/white menu. */
final class PowerMenus {

    private final AbilitySMP plugin;

    PowerMenus(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    static String describe(AbilitySMP.Power p) {
        return switch (p) {
            case FLAME -> "Idle: fire resistance. Super: shoots a fireball.";
            case SPEED -> "Idle: Speed I. Super: Speed III burst.";
            case TANK -> "Idle: extra hearts. Super: stuns nearby enemies.";
            case THOR -> "Idle: Strength. Super: lightning on the nearest enemy.";
            case SHADOW -> "Idle: night vision. Super: teleport behind an enemy and blind them.";
            case HEALER -> "Idle: Regeneration. Super: full heal and absorption.";
            case FEATHER -> "Idle: no fall damage. Super: big launch upward.";
            case STEALER -> "Idle: Haste. Super: weaken an enemy and boost yourself.";
            case AQUA -> "Idle: water breathing. Super: water jet dash.";
            case HUNTER -> "Idle: enemies glow. Super: pull an enemy to you.";
            case GHOST -> "Halloween. Idle: Speed I, enemies glow. Super: invisible, wither, steal hearts.";
            case VAMPIRE -> "Halloween. Idle: night vision. Super: latch on, drain and wither.";
            case TELEPORTER -> "Idle: Speed III. Super: dash, freeze, fling for 4 hearts.";
            case VOID -> "Idle: Strength III. Super: wither and 7 hearts of damage.";
        };
    }

    // ------------------------------------------------------------ shop prices

    int pointPrice(AbilitySMP.Power p) {
        return plugin.getConfig().getInt("shop.prices." + p.key(), 0);
    }

    int coinPrice(AbilitySMP.Power p) {
        return plugin.getConfig().getInt("shop.coin-prices." + p.key(), 0);
    }

    private String priceText(AbilitySMP.Power p) {
        if (pointPrice(p) > 0) return "Price: " + pointPrice(p) + " Unbound Coins";
        return "Not sold in the shop";
    }

    // ------------------------------------------------------------ power menu

    void openPowers(Player p, boolean atStation) {
        new Menus.Paged("Powers") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                AbilitySMP.Power cur = plugin.powerOf(pl);
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD, "Your Unbound Coins: " + plugin.unbound.points(pl),
                        false, "Earn them from quests and playtime.",
                        atStation ? "Right-click a power to upgrade it." : "Upgrade powers at an upgrade station."), null));
                for (AbilitySMP.Power pw : AbilitySMP.Power.values()) {
                    if (!plugin.isEnabled(pw)) continue;
                    boolean owned = plugin.profile.owns(pl, pw);
                    boolean equipped = pw == cur;
                    int lvl = plugin.unbound.level(pl, pw);
                    int max = plugin.unbound.maxLevel();
                    String name = AbilitySMP.pretty(pw.key()) + (equipped ? "  [EQUIPPED]" : "");
                    if (owned) {
                        List<String> lore = new ArrayList<>();
                        lore.add(describe(pw));
                        lore.add("Level " + lvl + " / " + max);
                        lore.add(equipped ? "Currently equipped" : "Left-click to equip");
                        if (atStation) {
                            lore.add(lvl >= max ? "Max level" : "Right-click to upgrade: "
                                    + plugin.unbound.costFor(lvl) + " points");
                        }
                        out.add(new Menus.Entry(Menus.icon(Material.NETHER_STAR, name, equipped, lore.toArray(new String[0])),
                                (pp, type) -> {
                                    if (type.isRightClick()) {
                                        if (atStation) plugin.unbound.upgrade(pp, pw);
                                        else pp.sendMessage(Component.text("Upgrade powers at an upgrade station.",
                                                NamedTextColor.GRAY));
                                    } else {
                                        plugin.equipPower(pp, pw);
                                    }
                                }));
                    } else {
                        out.add(new Menus.Entry(Menus.icon(Material.BARRIER, name, false, describe(pw), "LOCKED",
                                priceText(pw), "Click to open the shop"), (pp, type) -> openShop(pp)));
                    }
                }
                return out;
            }
        }.open(p);
    }

    // ------------------------------------------------------------ quest menu

    private static Material questIcon(String type) {
        return switch (type.toUpperCase()) {
            case "KILL_MOBS" -> Material.IRON_SWORD;
            case "KILL_PLAYERS" -> Material.DIAMOND_SWORD;
            case "BREAK_LOGS" -> Material.OAK_LOG;
            case "MINE_ORES" -> Material.DIAMOND_PICKAXE;
            case "USE_POWER" -> Material.NETHER_STAR;
            case "COLLECT_CANDY" -> Material.COOKIE;
            case "KILL_HAUNTED" -> Material.JACK_O_LANTERN;
            case "HAUNTED_MINUTES" -> Material.CLOCK;
            case "DUEL_WIN" -> Material.GOLDEN_SWORD;
            case "BOSS_KILL" -> Material.WITHER_SKELETON_SKULL;
            default -> Material.BOOK;
        };
    }

    private static String bar(int now, int target) {
        int filled = Math.min(10, (int) Math.round(10.0 * now / Math.max(1, target)));
        return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "] " + now + " / " + target;
    }

    void openQuests(Player p) {
        new Menus.Paged("Quests") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                int need = plugin.getConfig().getInt("points.play-minutes", 20);
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD, "Your Unbound Coins: " + plugin.unbound.points(pl),
                        false, "Quests start over by themselves when you finish them.",
                        "Playtime: " + plugin.unbound.playMinutes(pl) + " / " + need + " minutes to the next reward",
                        "Quests completed so far: " + plugin.unbound.completed(pl)), null));
                ConfigurationSection qs = plugin.unbound.quests();
                for (String id : qs.getKeys(false)) {
                    ConfigurationSection q = qs.getConfigurationSection(id);
                    if (q == null) continue;
                    int target = Math.max(1, q.getInt("target", 10));
                    out.add(new Menus.Entry(Menus.icon(questIcon(q.getString("type", "")), q.getString("name", id), false,
                            q.getString("description", ""), bar(plugin.unbound.progress(pl, id), target),
                            "Reward: " + q.getInt("reward", 10) + " Unbound Coins"), null));
                }
                return out;
            }
        }.open(p);
    }

    // ------------------------------------------------------------ power shop (see Shops)

    void openShop(Player p) {
        plugin.shops.openPowerShop(p);
    }
}
