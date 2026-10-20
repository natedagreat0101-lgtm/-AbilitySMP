package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /notes: patch notes and how to use the new features. */
final class Notes {

    static final String VERSION = "4.0";

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

        h(s, "===== Unbound SMP Patch Notes: v4.0 =====");
        l(s, "Mobs are enemies now");
        how(s, "every power that targets or glows enemies (Thor, Shadow, Hunter, Stealer, Ghost, Jack-o-Lantern, "
                + "Vampire, Teleporter, Tank stun) now hits hostile mobs too. Pets and villagers are safe.");
        l(s, "Craftable Reroll Token");
        how(s, "craft with diamonds in all 8 outer slots and a gold block in the middle. Right-click the token "
                + "to roll a new random power at any time. Your old power is lost, so choose wisely!");
        l(s, "New player tutorial");
        how(s, "new players get on-screen steps: moving, mining wood, crafting, stone, tips and commands. "
                + "/skip skips it, /tutorial replays it, and you get a small reward for finishing.");
        l(s, "/notes");
        how(s, "this list. Use /notes all to also see older updates.");
        l(s, "Admin panel (OP only): /panel");
        how(s, "send announcements as [Admin] with your name, change the sky (day, orange sunset, night), set the "
                + "weather, play music, heal everyone and start a new battle pass season.");
        l(s, "Halloween events (from the panel)");
        how(s, "Orange Sky, Jack-o-Lanterns popping up near players, spooky music, and the Mob Curse: "
                + "killing a hostile mob seals your power for a minute. 'Full Halloween Night' turns everything on.");
        l(s, "Music");
        how(s, "/panel music <track|stop>. Tracks are listed in config.yml under music: tracks.");

        if (all) {
            h(s, "===== v3.0 =====");
            l(s, "Vampire replaced Mimic");
            how(s, "right-click a sword to leech the nearest enemy and wither them. Heals you while it drains them.");
            l(s, "Teleporter power");
            how(s, "idle: Speed III. Super: dash to an enemy, freeze them, then fling them back about 20 blocks "
                    + "for 4 hearts of damage.");
            l(s, "Power stars");
            how(s, "right-click a glowing nether star to gain that power. Your old power comes back as a star.");
            l(s, "Battle pass: /pass");
            how(s, "30 tiers. 20 minutes online or 5 kills = 1 level. Click a tier to claim. Resets every 14 days.");
            l(s, "Right-click a sword to use your power");
            how(s, "each power also has an idle ability that is always on.");
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
