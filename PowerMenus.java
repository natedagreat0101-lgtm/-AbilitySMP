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
            case PUMPKIN -> "Halloween. Super: launches a huge exploding pumpkin bomb.";
            case WITCH -> "Halloween. Idle: night vision. Super: poison and slow all nearby enemies, heal yourself.";
            case SKELETON -> "Halloween. Super: shoots a volley of 8 arrows at the nearest enemy.";
            case SPIDER -> "Halloween. Super: traps the nearest enemy in webs and poisons them.";
            case SCARECROW -> "Halloween. Super: terrify nearby enemies - nausea, weakness, knocked back.";
            case REAPER -> "Halloween. Super: blink to the nearest enemy, strike for 5 hearts and heal.";
            case CANDY -> "Halloween. Super: candy rush - speed, strength, healing and cookies.";
            case PHANTOM -> "Halloween. Super: phantom dive; your screech hurts and blinds nearby enemies.";
            case WEREWOLF -> "Halloween. Idle: Speed I and night vision. Super: howl - speed and strength burst.";
            case CURSE -> "Halloween. Super: curse the nearest enemy with wither, weakness and slowness.";
            case SUPERNOVA -> "Galactic. Idle: fire resistance. Super: shoots 3 fireballs at the nearest enemy.";
            case EARTH -> "Galactic. Idle: Resistance II. Super: trap your enemy in a stone box, gain Strength III and send minions after them for 1 minute.";
            case WIND -> "Galactic. ONE OF A KIND - commands only. Idle: no fall damage. Super: charge in the air for 2s, then slam down for 15 hearts to everyone within 15 blocks.";
            case FROST -> "Galactic. Idle: Frost Walker - water freezes under your feet. Super: freeze every enemy within 30 blocks for 5s, then frost damage for 20s.";
            case STARLIGHT -> "Galactic. Idle: night vision. Super: a beam of starlight hits the nearest enemy.";
            case COMET -> "Galactic. Idle: Speed I. Super: a freezing dash that frostbites enemies you pass.";
            case METEOR -> "Galactic. Super: a meteor falls out of the sky onto the nearest enemy.";
            case LUNAR -> "Galactic. Idle: Jump Boost I. Super: moon gravity - you bounce high and enemies float.";
            case PULSAR -> "Galactic. Super: three shockwave pulses that shove and hurt everyone close.";
            case NEBULA -> "Galactic. Super: vanish into a star cloud that blinds nearby enemies.";
            case SOLAR_FLARE -> "Galactic. Idle: Strength I. Super: blind and burn every enemy around you.";
            case ECLIPSE -> "Galactic. Super: darkness and weakness for enemies, strength and speed for you.";
            case ASTEROID -> "Galactic. Idle: rocks orbit you and hurt anything close. Super: a giant asteroid crashes onto the nearest enemy.";
            case ZERO_GRAVITY -> "Galactic. Super: every enemy floats up... then crashes down.";
            case WORMHOLE -> "Galactic. Super: swap places with the nearest enemy.";
            case QUASAR -> "Galactic. Super: a huge beam of starlight in the direction you look.";
            case BLACK_HOLE -> "Galactic. Super: a black hole drags every enemy in, then bursts.";
            case ANTIMATTER -> "Galactic. Super: charge up, then a huge explosion centred on you (it never hurts you).";
            case ROCKET -> "Galactic. Super: blast off into the sky!";
            case HACKER -> "Unlocked by a 7-day quest streak. Super: dupe the best sword of the nearest player - the copy is yours for 30 seconds. Cooldown: 1 minute.";
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
        if (p == AbilitySMP.Power.WIND) return "One of a kind - only an admin command can give it";
        if (p == AbilitySMP.Power.HACKER) return "Finish a quest 7 days in a row to unlock it (quest streak)";
        if (p.isHalloween()) return "Retired: owners keep it, but it is not sold anymore";
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
                AbilitySMP.Power st = plugin.standbyOf(pl);
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD, "Your Unbound Coins: " + plugin.unbound.points(pl),
                        false, "Earn them from quests and playtime.",
                        "QUICK SWITCH: equip TWO powers - slot 1 and slot 2.",
                        "In game, press F or crouch + right-click to switch.",
                        atStation ? "Right-click a power to upgrade it." : "Right-click a power to put it in slot 2."), null));
                // v12: your two power slots are pinned at the TOP of the menu (they also
                // show at the top of your screen in game, above everything else)
                out.add(new Menus.Entry(slotIcon(pl, 1, cur), null));
                out.add(new Menus.Entry(slotIcon(pl, 2, st), null));
                for (AbilitySMP.Power pw : AbilitySMP.Power.values()) {
                    if (!plugin.isEnabled(pw)) continue;
                    boolean owned = plugin.profile.owns(pl, pw);
                    boolean equipped = pw == cur;
                    boolean inSlot2 = pw == st;
                    int lvl = plugin.unbound.level(pl, pw);
                    int max = plugin.unbound.maxLevel();
                    String name = AbilitySMP.pretty(pw.key()) + (equipped ? "  [SLOT 1]" : inSlot2 ? "  [SLOT 2]" : "");
                    if (owned) {
                        List<String> lore = new ArrayList<>();
                        lore.add(describe(pw));
                        lore.add("Rarity: " + pw.rarity().display.toUpperCase());
                        lore.add("Level " + lvl + " / " + max);
                        lore.add(equipped ? "Currently in slot 1" : "Left-click to equip (slot 1)");
                        if (atStation) {
                            lore.add(lvl >= max ? "Max level" : "Right-click to upgrade: "
                                    + plugin.unbound.costFor(lvl) + " points");
                        } else {
                            lore.add(inSlot2 ? "Currently in slot 2" : "Right-click to put in slot 2");
                        }
                        out.add(new Menus.Entry(Menus.icon(Material.NETHER_STAR, name, equipped || inSlot2, lore.toArray(new String[0])),
                                (pp, type) -> {
                                    if (type.isRightClick()) {
                                        if (atStation) plugin.unbound.upgrade(pp, pw);
                                        else plugin.setStandby(pp, pw);
                                    } else {
                                        plugin.equipPower(pp, pw);
                                    }
                                }));
                    } else {
                        out.add(new Menus.Entry(Menus.icon(Material.BARRIER, name, false, describe(pw),
                                "Rarity: " + pw.rarity().display.toUpperCase(), "LOCKED",
                                priceText(pw), "Or win it from a Power Crate - /crate", "Click to open the shop"),
                                (pp, type) -> openShop(pp)));
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
            case "RANKED_WIN" -> Material.NETHERITE_SWORD;
            case "OPEN_CRATE" -> Material.CHEST;
            case "TRADE" -> Material.EMERALD;
            case "REBIRTH" -> Material.BEACON;
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
                    String reward = "Reward: " + q.getInt("reward", 10) + " Unbound Coins";
                    if (q.getInt("reward-galaxy", 0) > 0) reward += " + " + q.getInt("reward-galaxy") + " Galaxy Coins";
                    out.add(new Menus.Entry(Menus.icon(questIcon(q.getString("type", "")), q.getString("name", id), false,
                            q.getString("description", ""), bar(plugin.unbound.progress(pl, id), target),
                            reward), null));
                }
                return out;
            }
        }.open(p);
    }

    // ------------------------------------------------------------ power shop (see Shops)

    void openShop(Player p) {
        plugin.shops.openPowerShop(p);
    }

    /** The pinned slot icons at the top of /powers: the Activator sword showing the power's orb. */
    private org.bukkit.inventory.ItemStack slotIcon(Player pl, int slot, AbilitySMP.Power pw) {
        if (pw == null) {
            return Menus.icon(Material.BARRIER, "SLOT " + slot + " - empty", false,
                    slot == 1 ? "Equip a power below to fill slot 1." : "Right-click a power below to fill slot 2.",
                    "Your two slots also show at the TOP of your screen in game.");
        }
        org.bukkit.inventory.ItemStack it = plugin.activatorItem();
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.setCustomModelData(AbilitySMP.modelDataFor(pw));
        m.displayName(net.kyori.adventure.text.Component.text("SLOT " + slot + ": " + AbilitySMP.pretty(pw.key()),
                pw == null ? net.kyori.adventure.text.format.NamedTextColor.GRAY : plugin.colorFor(pw))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        m.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text(describe(pw), net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("Your two slots also show at the TOP of your screen in game.",
                        net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("Switch with F or crouch + right-click.",
                        net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        it.setItemMeta(m);
        return it;
    }
}
