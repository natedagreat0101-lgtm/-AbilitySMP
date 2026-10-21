package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /notes: patch notes and how to use the new features. */
final class Notes {

    static final String VERSION = "5.0";

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

        h(s, "===== Unbound SMP Patch Notes: v5.0 =====");
        l(s, "Power Activator sword");
        how(s, "you start with a sword named Power Activator. Hold it and right-click to use your power. "
                + "It is soulbound: it can't be dropped and comes back when you die. Lost it? Use /activator.");
        l(s, "New starter power: Speed");
        how(s, "new players start with Speed. Idle: Speed I. Super: a Speed III burst for 8 seconds.");
        l(s, "Powers from admins are items");
        how(s, "when an admin gives you a power you get a glowing nether star. Right-click it to gain the power "
                + "(your old power comes back as a star).");
        l(s, "Battle pass removed");
        how(s, "replaced by Unbound points, quests and power upgrades (below).");
        l(s, "Unbound points");
        how(s, "earn them from quests and by playing: 10 points every 20 minutes. /points shows your balance.");
        l(s, "Quests");
        how(s, "/quests lists them: kill mobs or players, chop logs, mine ores, use your power, collect candy. "
                + "When you finish one you get points and it resets by itself so you can do it again.");
        l(s, "Upgrade station");
        how(s, "right-click an upgrade station (an admin sets them up) and spend Unbound points to level up your "
                + "power. Each level: shorter cooldown, more range, damage and duration. Max level 5.");
        l(s, "New power: Void");
        how(s, "idle: Strength III. Super: the nearest enemy gets Wither and takes 7 hearts of damage.");
        l(s, "Vampire is now attached");
        how(s, "your super latches you onto the enemy's back while you drain them, with fake blood spraying out. "
                + "Sneak to let go. Mimic is gone.");
        l(s, "Trick or Treat Night (admin event)");
        how(s, "little huts appear around players. Right-click the trick-or-treater at the door for candy (or a "
                + "harmless trick). Sneak + right-click to trade 5 candy for a Halloween Coin.");

        if (all) {
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
