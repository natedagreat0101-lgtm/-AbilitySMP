package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v15.2: /afk. Toggle AFK on and you earn Unbound Coins every minute you
 * stay away (config afk.coins-per-minute, default 10). Moving (actually
 * changing position - looking around is fine), typing /afk again, or
 * logging out ends it, and you're told what you earned. Your tab-list name
 * gets an [AFK] tag while you're away so everyone can see it.
 */
final class Afk implements Listener {

    private final AbilitySMP plugin;
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID, Integer> earned = new HashMap<>();

    Afk(AbilitySMP plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::payTick, 1200L, 1200L);
    }

    private int rate() {
        return Math.max(0, plugin.getConfig().getInt("afk.coins-per-minute", 10));
    }

    boolean isAfk(Player p) {
        return afk.contains(p.getUniqueId());
    }

    void toggle(Player p) {
        if (isAfk(p)) stop(p, true);
        else start(p);
    }

    private void start(Player p) {
        afk.add(p.getUniqueId());
        earned.put(p.getUniqueId(), 0);
        p.playerListName(Component.text("[AFK] ", NamedTextColor.GRAY).append(p.name()));
        Bukkit.broadcast(Component.text(p.getName() + " is now AFK.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("You are AFK. You'll earn " + rate()
                + " Unbound Coins every minute. Move or type /afk again to come back.", NamedTextColor.GREEN));
    }

    private void stop(Player p, boolean announce) {
        afk.remove(p.getUniqueId());
        int got = earned.getOrDefault(p.getUniqueId(), 0);
        earned.remove(p.getUniqueId());
        p.playerListName(null);
        if (announce) {
            Bukkit.broadcast(Component.text(p.getName() + " is back.", NamedTextColor.GRAY));
            p.sendMessage(Component.text(got > 0
                    ? "Welcome back! You earned " + got + " Unbound Coins while AFK."
                    : "Welcome back!", NamedTextColor.GREEN));
        }
    }

    private void payTick() {
        int rate = rate();
        if (rate <= 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isAfk(p)) continue;
            plugin.unbound.addPoints(p, rate, "afk");
            earned.merge(p.getUniqueId(), rate, Integer::sum);
            plugin.bar(p, "+" + rate + " Unbound Coins (AFK)", NamedTextColor.GOLD);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!isAfk(e.getPlayer())) return;
        // looking around doesn't count - only actually walking somewhere
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            stop(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (isAfk(e.getPlayer())) stop(e.getPlayer(), false);
    }
}
