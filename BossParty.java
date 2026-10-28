package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boss parties: /boss invite <player> asks a friend to join your boss fights, /boss accept joins.
 * A party is you plus one friend (2 players). When the party leader starts any boss fight -
 * Headless Horseman, Mimic or Ghost - the friend is teleported into the same (separate) arena,
 * gets their own Fight Totem, and earns the same rewards if they are still standing at the end.
 * If a player's totem pops they are knocked out and sent back; the fight only ends in defeat
 * when everybody is out.
 */
final class BossParty {

    static final int MAX_SIZE = 2;
    private static final long INVITE_MS = 60_000L;

    private final Map<UUID, UUID> inviteFrom = new HashMap<>(); // invited player -> leader
    private final Map<UUID, Long> inviteAt = new HashMap<>();
    private final Map<UUID, Set<UUID>> parties = new HashMap<>(); // leader -> members (incl. leader)
    private final Map<UUID, UUID> leaderOf = new HashMap<>(); // member -> leader

    /** The leader of the party this player is in, or the player themself if they have no party. */
    UUID leaderId(Player p) {
        return leaderOf.getOrDefault(p.getUniqueId(), p.getUniqueId());
    }

    boolean inParty(Player p) {
        return parties.containsKey(leaderId(p)) && parties.get(leaderId(p)).size() > 1;
    }

    /** Online members of this player's party, leader first. Just the player if they have no party. */
    List<Player> members(Player p) {
        List<Player> out = new ArrayList<>();
        Set<UUID> set = parties.get(leaderId(p));
        if (set == null) {
            out.add(p);
            return out;
        }
        for (UUID id : set) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) out.add(m);
        }
        if (out.isEmpty()) out.add(p);
        return out;
    }

    // =====================================================================
    // Commands: invite / accept / deny / leaveparty
    // =====================================================================

    void invite(Player leader, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || t.equals(leader)) {
            leader.sendMessage(Component.text("Pick a friend who is online.", NamedTextColor.RED));
            return;
        }
        UUID lid = leaderId(leader);
        Set<UUID> set = parties.get(lid);
        if (set != null && set.size() >= MAX_SIZE && !set.contains(t.getUniqueId())) {
            leader.sendMessage(Component.text("Your boss party is full (you + one friend).", NamedTextColor.RED));
            return;
        }
        if (leaderOf.containsKey(t.getUniqueId())) {
            leader.sendMessage(Component.text(t.getName() + " is already in a boss party.", NamedTextColor.RED));
            return;
        }
        inviteFrom.put(t.getUniqueId(), lid);
        inviteAt.put(t.getUniqueId(), System.currentTimeMillis());
        leader.sendMessage(Component.text("Boss party invite sent to " + t.getName() + ".", NamedTextColor.GOLD));
        t.sendMessage(Component.text(leader.getName() + " invited you to join their boss fights! ", NamedTextColor.GOLD)
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/boss accept")))
                .append(Component.text("  "))
                .append(Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/boss deny"))));
    }

    void accept(Player p) {
        UUID lid = inviteFrom.remove(p.getUniqueId());
        Long at = inviteAt.remove(p.getUniqueId());
        Player leader = lid == null ? null : Bukkit.getPlayer(lid);
        if (leader == null || at == null || System.currentTimeMillis() - at > INVITE_MS) {
            p.sendMessage(Component.text("You have no (valid) boss party invite.", NamedTextColor.GRAY));
            return;
        }
        Set<UUID> set = parties.computeIfAbsent(lid, k -> {
            Set<UUID> s = new LinkedHashSet<>();
            s.add(k);
            leaderOf.put(k, k);
            return s;
        });
        if (set.size() >= MAX_SIZE) {
            p.sendMessage(Component.text("That boss party is full now.", NamedTextColor.RED));
            return;
        }
        set.add(p.getUniqueId());
        leaderOf.put(p.getUniqueId(), lid);
        p.sendMessage(Component.text("You joined " + leader.getName() + "'s boss party! When they start a boss, "
                + "you will be teleported in with them.", NamedTextColor.GOLD));
        leader.sendMessage(Component.text(p.getName() + " joined your boss party!", NamedTextColor.GOLD));
    }

    void deny(Player p) {
        UUID lid = inviteFrom.remove(p.getUniqueId());
        inviteAt.remove(p.getUniqueId());
        if (lid == null) {
            p.sendMessage(Component.text("You have no boss party invite.", NamedTextColor.GRAY));
            return;
        }
        Player leader = Bukkit.getPlayer(lid);
        if (leader != null) leader.sendMessage(Component.text(p.getName() + " declined your boss party invite.", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Boss party invite declined.", NamedTextColor.GRAY));
    }

    /** Removes the player from their party (disbanding it if they were the leader). */
    void leaveParty(Player p) {
        UUID lid = leaderId(p);
        Set<UUID> set = parties.get(lid);
        if (set == null || set.size() <= 1) {
            parties.remove(lid);
            leaderOf.remove(p.getUniqueId());
            p.sendMessage(Component.text("You are not in a boss party.", NamedTextColor.GRAY));
            return;
        }
        for (UUID id : new ArrayList<>(set)) {
            leaderOf.remove(id);
            Player m = Bukkit.getPlayer(id);
            if (m != null && !m.equals(p)) {
                m.sendMessage(Component.text("The boss party was disbanded.", NamedTextColor.GRAY));
            }
        }
        parties.remove(lid);
        p.sendMessage(Component.text("You left the boss party.", NamedTextColor.GRAY));
    }

    void onQuit(Player p) {
        inviteFrom.remove(p.getUniqueId());
        inviteAt.remove(p.getUniqueId());
    }
}
