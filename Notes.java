package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /notes: patch notes and how to use the new features. */
final class Notes {

    static final String VERSION = "8.0";

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

        h(s, "===== Unbound SMP Patch Notes: v8.0 - Halloween Boss Update =====");
        l(s, "NEW BOSS: The Mimic - /boss fight Mimic");
        how(s, "a giant chest monster with a long tongue that drags you in. At 75% health he starts chomping "
                + "(4 hearts and poison) and weakening you. At 50% he makes a copy of YOU wearing your gear. "
                + "At 25% he goes ULTRA: two GIANT copies of you (6 hearts a hit, but any weapon drops them "
                + "in 2 hits) and every attack at once. Reward: 100 Halloween Coins.");
        l(s, "NEW BOSS: The Ghost - /boss fight Ghost");
        how(s, "an easy boss with 20 hearts. He is visible for 5 seconds, then invisible for 3 seconds - "
                + "he sneaks behind you and hits you - then visible again, over and over. "
                + "Reward: 10 Halloween Coins and 5 Unbound Coins.");
        l(s, "Boss fights can never kill you now: the Fight Totem");
        how(s, "when a boss fight or a duel starts you get a Fight Totem in your off hand. If a hit would "
                + "kill you, the totem pops and you lose the fight instead of dying. Win and it disappears. "
                + "It is tagged, so your own normal totems are never touched.");
        l(s, "Every boss fight has its own arena - and you can bring a friend");
        how(s, "no more waiting for the Horseman arena. Use /boss invite <player>, they run /boss accept, "
                + "and when you start any boss they are teleported in with you and earn the same rewards.");
        l(s, "10 new Halloween powers");
        how(s, "Pumpkin, Witch, Skeleton, Spider, Scarecrow, Reaper, Candy, Phantom, Werewolf and Curse. "
                + "Find them in /powers and /shop.");
        l(s, "Still here: /duel, /boss fight Headless Horseman, /locker, /powers, /quests");

        h(s, "===== v7.0 =====");
        l(s, "Power Shop restocks every 5 minutes: /shop");
        how(s, "a new selection of powers appears, paid for with Unbound Coins. Each power has limited stock for the "
                + "whole server, so be quick. Admins can pin a power and put it on sale, and everyone sees it at the "
                + "top of the screen.");
        l(s, "Halloween Shop: /halloweenshop");
        how(s, "spend Halloween Coins on items. Limited-time Halloween items, like the Ghost and Vampire powers and "
                + "the Soul Trail, are only sold until the date shown in the shop.");
        l(s, "Shopkeepers");
        how(s, "right-click a Shopkeeper villager to open the Power Shop, or a Halloween Shopkeeper to open the "
                + "Halloween Shop. Admins get the spawn eggs with /give.");
        l(s, "Haunted Forest removed");
        how(s, "the dimension is gone. Ranks and progress you already earned are kept forever. Halloween Coins now "
                + "come from Trick or Treat Night and the Headless Horseman.");
        l(s, "Unbound Coins");
        how(s, "the currency from quests and playtime is now called Unbound Coins everywhere.");

        if (all) {
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
