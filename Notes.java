package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /notes: patch notes and how to use the new features. */
final class Notes {

    static final String VERSION = "6.0";

    private Notes() {
    }

    private static void h(CommandSender s, String text) {
        s.sendMessage(Component.text(text, NamedTextColor.GOLD));
    }

    private static void l(CommandSender s, String text) {
        s.sendMessage(Component.text(" - " + text, NamedTextColor.YELLOW));
    }

    private static void how(CommandSender s, String text) {
        s.sendMessage(Component.text("     How: " + text, NamedTextColor.GRAY));
    }

    static void show(AbilitySMP plugin, CommandSender s, String[] args) {
        boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");

        h(s, "===== Unbound SMP Patch Notes: v6.0 (Halloween Part 2) =====");
        l(s, "Haunted Forest (Halloween dimension)");
        how(s, "build a portal frame out of jack-o'-lanterns (at least 2 wide, 3 tall inside) and light it with "
                + "flint and steel. Every mob you defeat there drops 5 Halloween Coins. Look for trick-or-treat huts "
                + "and loot chests.");
        l(s, "Duels: /duel <player>");
        how(s, "the other player runs /duel accept. You fight in an arena, nobody dies and nobody loses items. "
                + "The winner earns Unbound points.");
        l(s, "Boss: /boss fight Headless Horseman");
        how(s, "3 phases: gravestones, then slashes and pumpkin heads, then raging with fireballs, zombies and double "
                + "damage. Win for 50 Unbound points and 30 Halloween Coins.");
        l(s, "Ranks");
        how(s, "your rank rises with playtime and Halloween ranks come from the Haunted Forest. They are permanent. "
                + "Pick which one to show in /locker.");
        l(s, "/locker");
        how(s, "your permanent collection: equip ranks and trails, and see how to unlock the rest.");
        l(s, "Menus: /powers, /shop, /quests");
        how(s, "scrollable menus. /powers: left-click a power to equip it. /shop: buy powers with Unbound points, or "
                + "Halloween Coins for Ghost and Vampire. /quests: your quest progress.");
        l(s, "Fixes");
        how(s, "powers no longer swap back by themselves, and quest kills now count even from wither, fire and powers.");
        l(s, "Jack O' Lantern power removed");
        how(s, "only Ghost and Vampire are Halloween powers now. Anyone who had it now has Ghost.");

        if (all) {
            h(s, "===== v5.0 =====");
            l(s, "Power Activator sword, Speed starter power, Unbound points, quests, upgrade stations, Void, attached Vampire, Trick or Treat Night.");
            h(s, "===== v4.0 =====");
            l(s, "Mobs are enemies: powers and glow effects hit hostile mobs too.");
            l(s, "Reroll Token: 8 diamonds around a gold block. Right-click to roll a new power.");
            l(s, "Tutorial for new players (/skip, /tutorial), /notes, admin panel (/panel), Halloween events.");
            h(s, "===== v3.0 =====");
            l(s, "Vampire, Teleporter, power stars. Teleporter: idle Speed III, super dashes at an enemy, freezes "
                    + "them and flings them back for 4 hearts of damage.");
        } else {
            s.sendMessage(Component.text("Type /notes all to see older updates.", NamedTextColor.GRAY));
        }

        if (s instanceof Player p) {
            plugin.data.set("players." + p.getUniqueId() + ".notes-seen", VERSION);
            plugin.saveData();
        }
    }

    /** One-time nudge after the server updates. */
    static void joinHint(AbilitySMP plugin, Player p) {
        String seen = plugin.data.getString("players." + p.getUniqueId() + ".notes-seen");
        if (VERSION.equals(seen)) return;
        p.sendMessage(Component.text("Unbound SMP was updated to v" + VERSION + "! Type /notes to see what's new.",
                NamedTextColor.GOLD));
    }
}
