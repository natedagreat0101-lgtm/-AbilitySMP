package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /notes: patch notes and how to use the new features. */
final class Notes {

    static final String VERSION = "11.0";

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

        h(s, "===== Unbound SMP Patch Notes: v11.0 - The Spawn Update =====");
        l(s, "🏝️ A REAL SPAWN, DonutSMP style! Admins: run /spawnbuild and a whole little spawn appears - "
                + "a round platform with lamp posts, NPCs, crate chests and the countdown board, all placed "
                + "for you. Or build your own spot and use /setspawn + /spawnnpc to place things by hand.");
        l(s, "NPCs at spawn - just right-click them");
        how(s, "RANDOM TELEPORT warps you somewhere safe in the wild (also /rtp). POWER SHOP opens /shop. "
                + "EVENT SHOP opens the current event shop (right now the Galaxy Shop). RANKED opens the "
                + "ranked menu. DUELS opens a list of everyone online - click a head to challenge them. "
                + "Brand-new players now start at spawn, and /spawn brings anyone back.");
        l(s, "The COUNTDOWN BOARD");
        how(s, "floating text at spawn counts down to the next big update, second by second - and the "
                + "moment the update lands it flips to IT IS OUT NOW! It uses the same date as /galactic, "
                + "so /galactic set moves the board too.");
        l(s, "CRATE CHESTS at spawn");
        how(s, "the crates at spawn are real chests now, like DonutSMP. Right-click one with a Power Crate "
                + "Key in your inventory to spin - the chest is never used up, only the key is. Admins can "
                + "turn any chest into a spawn crate with /crate set (and back with /crate unset).");

        h(s, "===== v10.0 - THE GALACTIC UPDATE =====");
        l(s, "🌌 THE GALACTIC UPDATE IS HERE! Halloween is retired: the Halloween Shop, Trick or Treat Night "
                + "and the Halloween events are gone - but EVERYTHING you already earned stays forever: your "
                + "Halloween powers, ranks, titles and costumes all still work.");
        l(s, "19 NEW POWERS - the four headliners");
        how(s, "SUPERNOVA: fire resistance, and a super that shoots 3 fireballs at your enemy (20s cooldown). "
                + "EARTH: Resistance II, and a super that traps your enemy in a stone box while you get "
                + "Strength III and minions hunt only them, for a whole minute (5 minute cooldown!). "
                + "FROST: water freezes under your feet, and the super freezes every enemy in 30 blocks for "
                + "5 seconds, then frost damage bites them for 20 seconds. "
                + "WIND: no fall damage, and a super that charges in the air for 2 seconds then SLAMS down "
                + "for 15 hearts to everyone within 15 blocks. Wind is ONE OF A KIND: only one player can "
                + "ever have it, and it only comes from an admin command. All four cost a LOT of Unbound Coins.");
        l(s, "15 more galaxy powers");
        how(s, "Starlight, Comet, Meteor, Lunar, Pulsar, Nebula, Solar Flare, Eclipse, Asteroid, Zero Gravity, "
                + "Wormhole, Quasar, Black Hole, Antimatter and Rocket - all in /powers and /shop, priced by "
                + "how strong they are, and all in Power Crates too (except Wind). Halloween powers are not "
                + "sold or rolled anymore: if you have one, it's a collector's item now.");
        l(s, "NEW CURRENCY: Galaxy Coins - /gshop");
        how(s, "Galaxy Coins are items in your inventory (shiny purple crystals). Bosses pay them now "
                + "(Horseman 30, Mimic 100, Ghost 10), and so do some quests, rebirths and events. Spend them "
                + "in the Galaxy Shop: crates, keys, elytra, a Space Helmet, a Star Cloak, the Galaxy Guardian "
                + "title and more - the limited-time items are there until Nov 16!");
        l(s, "THE QUICK SWITCH - two powers at once!");
        how(s, "equip TWO powers: in /powers, left-click sets slot 1 and right-click sets slot 2. Your two "
                + "slots show above your hotbar like [1] and [2]. In game, press F (the swap-hands key) or "
                + "crouch + right-click your Power Activator to switch powers instantly.");
        l(s, "6 NEW EVENTS (admin /panel -> Server Events)");
        how(s, "SUPERNOVA SPLEEF: a supernova teleports everyone to a molten arena - right-click shoots "
                + "fireballs, break the floor, last one standing wins Galaxy Coins (Fight Totems keep you "
                + "safe). GALAXY RUSH: Speed III for everyone under a galaxy night sky. VERITY: everyone "
                + "gets Strength III, a Fight Totem and a one-shot Verity snowball in a yellow arena until "
                + "the song ends - popped players go home. Plus Meteor Shower, Moon Gravity and Star Rain.");
        l(s, "STAFF RANKS: Mod, Tester and Co-Owner");
        how(s, "an OP can give them with /staffrank <player> <mod|tester|co-owner>. A staff rank shows "
                + "instead of your normal rank in chat and the tab list. /staffrank <player> none removes it.");
        l(s, "New locker stuff");
        how(s, "a Space Helmet and a Star Cloak costume, the Nebula Trail (own the Black Hole power), and "
                + "the Galaxy Guardian and Star Traveler titles - in /locker.");

        h(s, "===== v9.0 - The Galactic Update Eve =====");
        l(s, "🌌 THE GALACTIC UPDATE lands Sunday Nov 1 at 1:00 PM! Type /galactic for the live countdown.");
        l(s, "POWER CRATES - /crate");
        how(s, "powers come from crates now (the shop still exists too). Right-click a Power Crate with a "
                + "Power Crate Key in your inventory and spin the wheel. Powers have RARITIES now: Common, "
                + "Rare, Epic, Legendary and Mythic - and the crate odds match. Get crates and keys from "
                + "boss fights, the Halloween Shop and rebirths.");
        l(s, "RANKED DUELS - /ranked");
        how(s, "queue up against a random player, or fight a practice bot matched to your rank: Bronze bots "
                + "are easy, Master and Unbound King bots are monsters, and there is an IMPOSSIBLE bot if you "
                + "are feeling brave. Climb Bronze, Silver, Gold, Platinum, Diamond, Master... and the best "
                + "rank of all: UNBOUND KING. Nobody dies and nobody loses items.");
        l(s, "TRADING - /trade <player>");
        how(s, "trade items in a safe window. Both players have to press CONFIRM, and if anybody changes "
                + "their offer, both confirmations reset. No scams possible.");
        l(s, "REBIRTHS - /rebirth");
        how(s, "reset your Unbound Coins and power levels (you keep your powers!) for a permanent +10% "
                + "coin bonus per rebirth, a crate set and Halloween Coins. More rebirths = better rewards.");
        l(s, "Power levels now go to 100!");
        how(s, "upgrading goes all the way to level 100, and levels past 50 count double - a level 100 "
                + "power is about 16x stronger. It costs a LOT of coins. Good luck.");
        l(s, "30 NEW RANKS and TITLES that work");
        how(s, "ranks for playtime, quests, boss hunting, duels, ranked, collecting and rebirths - equip "
                + "them in /locker. Titles (Boss Slayer, Ghost Buster, Unbound King and more) now show after "
                + "your name in chat. Halloween costumes in /locker actually go on your body now, and your "
                + "real armor is kept safe while you wear one.");
        l(s, "Boss cooldowns");
        how(s, "after a boss fight starts, that boss rests: Horseman 5 minutes, Mimic 10, Ghost 3. "
                + "Bosses also drop crate keys now (the Mimic ALWAYS drops one).");
        l(s, "Better quests, better Halloween Shop, balanced economy");
        how(s, "new quests for ranked, crates, trading and bosses; the Halloween Shop sells crates, keys, "
                + "costumes, titles, an elytra and more (limited items now run until Nov 9); shop prices are "
                + "set by rarity and coins come a little slower, so getting stacked takes real work now.");
        l(s, "NEW EVENTS (admin /panel -> Server Events)");
        how(s, "Blood Moon, Headless Horseman Moon, Disco Party, Raining Tacos, Raining Diamonds, Raining "
                + "Gold, Raining Candy, custom note-block songs in the Music menu... and THE SNAP: a cutscene "
                + "where Notazombiemc presses a button and EVERY event starts at once.");

        h(s, "===== v8.0 - Halloween Boss Update =====");
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
        l(s, "Halloween Shop: /halloweenshop (replaced by the Galaxy Shop, /gshop, in v10)");
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
