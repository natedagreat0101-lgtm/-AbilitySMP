package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /notes: patch notes and how to use the new features. */
final class Notes {

    static final String VERSION = "10.0";

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

        h(s, "===== Unbound SMP Patch Notes: v10.0 =====");
        l(s, "4 new special ranks");
        how(s, "Unbound Tester, Unbound Mob, Unbound Co-Owner and Unbound Owner are given by the server owner and are "
                + "permanent. Pick which rank to show in /locker.");
        l(s, "Better scary music");
        how(s, "Halloween Night now plays an original eerie music-box tune instead of a looping disc.");
        h(s, "===== v9.0 =====");
        l(s, "Headless Horseman cooldown: 20 minutes");
        how(s, "after a fight you must wait 20 minutes. You get a title and a sound the moment it resets, even if it "
                + "ended while you were offline.");
        l(s, "Halloween Shopkeepers");
        how(s, "every Trick or Treat hut now has a Halloween Shopkeeper villager. Right-click one to open the Halloween Shop.");
        l(s, "New Halloween ranks");
        how(s, "Halloween Starter, Veteran and Expert come from kills with Halloween powers (Ghost or Vampire). "
                + "Survivor needs the Headless Horseman, and King needs the Horseman, every Halloween power and Expert.");
        h(s, "===== v8.0 =====");
        l(s, "New way to get powers: /power shop");
        how(s, "buy a Reroll Token or a random power with Unbound Coins. Every 5 minutes ONE power is stocked; the "
                + "better the power, the more it costs. Rerolls are how you get new powers.");
        l(s, "Halloween Coins are now a balance, not an item");
        how(s, "check them with /points. Earn them from Trick or Treat Night and the Headless Horseman.");
        l(s, "/halloweenshop");
        how(s, "spend Halloween Coins. The Ghost and Vampire powers are rare there: each restock has only a small "
                + "chance to stock one. Limited-time items end on the date shown in the shop.");
        l(s, "Admins");
        how(s, "stock and discount any power from /panel or /power stock <power> [percent]. Everyone sees it at the top "
                + "of the screen. /power coins <player> <amount> gives Halloween Coins.");
        l(s, "The old /shop is gone. Shopkeeper villagers open /power shop and /halloweenshop.");

        if (all) {
            h(s, "===== v7.0 =====");
            l(s, "Haunted Forest removed, Unbound Coins, shopkeeper eggs.");
            h(s, "===== v6.0 =====");
            l(s, "Duels, the Headless Horseman boss, permanent ranks, /locker, scrollable menus, the power menu and quest menu.");
            h(s, "===== v5.0 =====");
            l(s, "Power Activator sword, Speed starter power, Unbound Coins, quests, upgrade stations, Void, attached Vampire, Trick or Treat Night.");
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
