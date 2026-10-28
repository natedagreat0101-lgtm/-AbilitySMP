package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The Galactic Update countdown (v9): /galactic shows a live countdown to the next big update.
 * The date lives in config.yml (galactic.release / galactic.timezone), so it can be changed any
 * time - in the file, or in game with /galactic set 2026-11-01 13:00 (admin).
 * Default: Sunday Nov 1 2026, 1:00 PM, America/Toronto.
 */
final class Galactic {

    private static final DateTimeFormatter PARSE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US);

    private Galactic() {
    }

    static ZonedDateTime release(AbilitySMP plugin) {
        try {
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("galactic.timezone", "America/Toronto"));
            return LocalDateTime.parse(plugin.getConfig().getString("galactic.release", "2026-11-01 13:00"), PARSE)
                    .atZone(zone);
        } catch (Exception ex) {
            return null;
        }
    }

    static String name(AbilitySMP plugin) {
        return plugin.getConfig().getString("galactic.name", "Galactic Update");
    }

    /** Seconds until the release (negative once it is out). Long.MIN_VALUE if the date is broken. */
    static long secondsLeft(AbilitySMP plugin) {
        ZonedDateTime rel = release(plugin);
        if (rel == null) return Long.MIN_VALUE;
        return rel.toEpochSecond() - ZonedDateTime.now(rel.getZone()).toEpochSecond();
    }

    static String countdown(AbilitySMP plugin) {
        long s = secondsLeft(plugin);
        if (s == Long.MIN_VALUE) return "date not set";
        if (s <= 0) return "OUT NOW!";
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    static void command(AbilitySMP plugin, CommandSender s, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("set") && s.hasPermission("abilitysmp.admin")) {
            String when = args[1] + " " + args[2];
            try {
                LocalDateTime.parse(when, PARSE); // validate before saving
                plugin.getConfig().set("galactic.release", when);
                plugin.saveConfig();
                s.sendMessage(Component.text("The " + name(plugin) + " now drops on " + when
                        + " (" + plugin.getConfig().getString("galactic.timezone", "America/Toronto") + ").",
                        NamedTextColor.GREEN));
            } catch (Exception ex) {
                s.sendMessage(Component.text("That date does not work. Use: /galactic set 2026-11-01 13:00",
                        NamedTextColor.RED));
            }
            return;
        }
        ZonedDateTime rel = release(plugin);
        s.sendMessage(Component.text("🌌 THE " + name(plugin).toUpperCase(Locale.ROOT) + " 🌌", NamedTextColor.DARK_PURPLE));
        if (rel == null) {
            s.sendMessage(Component.text("The release date is not set yet. Stay tuned!", NamedTextColor.GRAY));
            return;
        }
        long left = secondsLeft(plugin);
        if (left <= 0) {
            s.sendMessage(Component.text("IT IS OUT NOW! Type /notes to see everything that is new.",
                    NamedTextColor.GREEN));
        } else {
            s.sendMessage(Component.text("Drops in: " + countdown(plugin), NamedTextColor.LIGHT_PURPLE));
            s.sendMessage(Component.text("When: " + rel.format(SHOW) + " (" + rel.getZone() + ")",
                    NamedTextColor.GRAY));
            s.sendMessage(Component.text("Get your powers leveled, your crates saved and your rank up before it lands!",
                    NamedTextColor.YELLOW));
        }
        if (s.hasPermission("abilitysmp.admin")) {
            s.sendMessage(Component.text("Admin: change the date with /galactic set 2026-11-01 13:00",
                    NamedTextColor.DARK_GRAY));
        }
    }

    /** A short login reminder while the countdown is running (last 14 days) or just after launch. */
    static void joinHint(AbilitySMP plugin, Player p) {
        ZonedDateTime rel = release(plugin);
        if (rel == null) return;
        long left = secondsLeft(plugin);
        if (left > 0 && left <= 14L * 86400L) {
            p.sendMessage(Component.text("🌌 The " + name(plugin) + " lands in " + countdown(plugin)
                    + "! /galactic for the countdown.", NamedTextColor.DARK_PURPLE));
        } else if (left <= 0 && left > -86400L) {
            p.sendMessage(Component.text("🌌 The " + name(plugin) + " is OUT NOW! /galactic for details.",
                    NamedTextColor.DARK_PURPLE));
        }
    }
}
