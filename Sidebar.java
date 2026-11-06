package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * v15: the DonutSMP-style sidebar. Every player gets their own scoreboard on
 * the right side of the screen:
 *
 *        UNBOUND SMP
 *        Unbound Coins: 1,234
 *        Galaxy Coins: 56
 *        Deaths: 3
 *        Kills: 7
 *        PLAYTIME: 5h 12m
 *
 * The lines use fixed entries with team prefixes, so values update without
 * flicker. Deaths and kills (player kills) are counted into players.yml.
 * /sidebar toggles it per player; sidebar.enabled in config is the master
 * switch. Playtime comes from the Unbound system's playMinutes.
 */
final class Sidebar implements Listener {

    private final AbilitySMP plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private static final String[] KEYS = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7"};
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.US);

    Sidebar(AbilitySMP plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 50L, 50L);
    }

    private boolean masterOn() {
        return plugin.getConfig().getBoolean("sidebar.enabled", true);
    }

    private boolean wants(Player p) {
        return !"off".equals(plugin.data.getString("players." + p.getUniqueId() + ".sidebar"));
    }

    private void tick() {
        if (!masterOn()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!wants(p)) continue;
            if (!boards.containsKey(p.getUniqueId())) attach(p);
            else refresh(p);
        }
    }

    private void attach(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("unbound", "dummy",
                Component.text("UNBOUND SMP", NamedTextColor.GOLD, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < KEYS.length; i++) {
            Team team = board.registerNewTeam("l" + i);
            team.addEntry(KEYS[i]);
            obj.getScore(KEYS[i]).setScore(KEYS.length - i);
        }
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
        refresh(p);
    }

    private void refresh(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) return;
        String id = p.getUniqueId().toString();
        String[] lines = {
                "",
                "§eUnbound Coins: §f" + FMT.format(plugin.unbound.points(p)),
                "§6Week " + plugin.ages.week() + ": §f" + plugin.ages.ageName(plugin.ages.week()),
                "",
                "§cDeaths: §f" + plugin.data.getInt("players." + id + ".deaths", 0),
                "§aKills: §f" + plugin.data.getInt("players." + id + ".kills", 0),
                "",
                "§bPLAYTIME: §f" + playtime(plugin.profile.of(p.getUniqueId()).playMinutes),
        };
        for (int i = 0; i < KEYS.length; i++) {
            Team team = board.getTeam("l" + i);
            if (team != null) team.setPrefix(lines[i]);
        }
    }

    static String playtime(int minutes) {
        if (minutes < 60) return minutes + "m";
        int days = minutes / 1440, hours = (minutes % 1440) / 60, mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        return hours + "h " + mins + "m";
    }

    void toggle(Player p) {
        String path = "players." + p.getUniqueId() + ".sidebar";
        if (wants(p) && boards.containsKey(p.getUniqueId())) {
            plugin.data.set(path, "off");
            plugin.saveData();
            boards.remove(p.getUniqueId());
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.sendMessage(Component.text("Sidebar hidden. Use /sidebar to bring it back.", NamedTextColor.GRAY));
        } else {
            plugin.data.set(path, "on");
            plugin.saveData();
            if (masterOn()) attach(p);
            p.sendMessage(Component.text("Sidebar on!", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (masterOn() && wants(e.getPlayer())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attach(e.getPlayer()), 20L);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.data.set("players." + victim.getUniqueId() + ".deaths",
                plugin.data.getInt("players." + victim.getUniqueId() + ".deaths", 0) + 1);
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            plugin.data.set("players." + killer.getUniqueId() + ".kills",
                    plugin.data.getInt("players." + killer.getUniqueId() + ".kills", 0) + 1);
        }
        plugin.saveData();
    }
}
