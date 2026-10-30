package me.abilitysmp;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /music - a personal music player. Plays the vanilla music discs and the
 * Power Pack's own songs (custom .ogg files registered in the pack's
 * sounds.json) just for the player who asked. YouTube links cannot be
 * played: Minecraft has no way to stream them and YouTube blocks direct
 * audio streaming - the honest answer is said in chat, with the offer to
 * pack a song file instead.
 */
public class Music {

    private final AbilitySMP plugin;
    private final Map<UUID, String> nowPlaying = new LinkedHashMap<>();

    /** Disc name -> vanilla sound event suffix. */
    static final Map<String, String> DISCS = new LinkedHashMap<>();
    /** Friendly name -> [pack sound id, display name]. */
    static final Map<String, String[]> PACK_TRACKS = new LinkedHashMap<>();

    static {
        for (String d : new String[]{"13", "cat", "blocks", "chirp", "far", "mall", "mellohi",
                "stal", "strad", "ward", "11", "wait", "otherside", "5", "pigstep", "relic",
                "creator", "precipitate"}) {
            DISCS.put(d, "music_disc." + d);
        }
        PACK_TRACKS.put("nebula", new String[]{"nebula_drift", "Nebula Drift"});
        PACK_TRACKS.put("nebuladrift", new String[]{"nebula_drift", "Nebula Drift"});
        PACK_TRACKS.put("galaxy", new String[]{"galaxy_rush", "Galaxy Rush"});
        PACK_TRACKS.put("galaxyrush", new String[]{"galaxy_rush", "Galaxy Rush"});
        PACK_TRACKS.put("boss", new String[]{"boss_battle", "Boss Battle"});
        PACK_TRACKS.put("bossbattle", new String[]{"boss_battle", "Boss Battle"});
    }

    Music(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    void handle(Player p, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(p);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("stop")) {
            stop(p);
            p.sendMessage(Component.text("Music stopped.", NamedTextColor.GRAY));
            return;
        }
        if (sub.equals("play")) {
            if (args.length < 2) {
                p.sendMessage(Component.text("Usage: /music play <song> - see /music list", NamedTextColor.RED));
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) sb.append(args[i]).append(' ');
            play(p, sb.toString().trim());
            return;
        }
        // "/music <song name>" works too.
        StringBuilder sb = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) sb.append(' ').append(args[i]);
        play(p, sb.toString().trim());
    }

    private void play(Player p, String what) {
        String low = what.toLowerCase(Locale.ROOT);
        if (low.contains("youtube.com") || low.contains("youtu.be")) {
            p.sendMessage(Component.text("I can't play YouTube links in Minecraft - the game has no way to stream them, and YouTube blocks direct audio streaming.", NamedTextColor.RED));
            p.sendMessage(Component.text("Two things that DO work: /music play cat (any music disc), and the pack songs - /music play nebula, galaxy or boss.", NamedTextColor.GRAY));
            p.sendMessage(Component.text("Want a specific song in the game? Send me the song as an mp3/ogg file and I'll put it into the Power Pack so /music play can play the real thing.", NamedTextColor.GRAY));
            return;
        }
        String key = low.replace(" ", "").replace("-", "").replace("_", "");
        String disc = DISCS.get(key);
        if (disc != null) {
            start(p, Key.key("minecraft", disc), "Music Disc - " + AbilitySMP.pretty(key.equals("13") || key.equals("11") || key.equals("5") ? "disc " + key : key));
            return;
        }
        String[] track = PACK_TRACKS.get(key);
        if (track != null) {
            start(p, Key.key("abilitysmp", "music." + track[0]), track[1] + " (Power Pack song)");
            return;
        }
        p.sendMessage(Component.text("I don't know that song. /music list shows everything I can play.", NamedTextColor.RED));
    }

    private void start(Player p, Key soundKey, String display) {
        stop(p);
        p.playSound(Sound.sound(soundKey, Sound.Source.RECORD, 1f, 1f));
        nowPlaying.put(p.getUniqueId(), display);
        p.sendMessage(Component.text("♪ Now playing: ", NamedTextColor.GREEN)
                .append(Component.text(display, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  (/music stop to stop it)", NamedTextColor.GRAY)));
    }

    void stop(Player p) {
        p.stopSound(org.bukkit.SoundCategory.RECORDS);
        nowPlaying.remove(p.getUniqueId());
    }

    private void list(Player p) {
        p.sendMessage(Component.text("♪ MUSIC ♪", NamedTextColor.GOLD, TextDecoration.BOLD));
        String np = nowPlaying.get(p.getUniqueId());
        if (np != null) p.sendMessage(Component.text("Now playing: " + np, NamedTextColor.GREEN));
        p.sendMessage(Component.text("Power Pack songs (need the pack on): ", NamedTextColor.AQUA)
                .append(Component.text("nebula, galaxy, boss", NamedTextColor.WHITE)));
        p.sendMessage(Component.text("Music discs: ", NamedTextColor.AQUA)
                .append(Component.text(String.join(", ", DISCS.keySet()), NamedTextColor.WHITE)));
        p.sendMessage(Component.text("Play one with /music play <name>, stop with /music stop. Only you hear your music.", NamedTextColor.GRAY));
    }
}
