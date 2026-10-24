package me.abilitysmp;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Permanent ranks. Normal ranks upgrade by total playtime. Halloween ranks come from Halloween progress.
 * Unlocking a rank saves it forever in profile.yml, so removing Halloween content never removes a rank.
 */
final class Ranks implements Listener {

    static final class Rank {
        final String id;
        final String name;
        final boolean halloween;
        final String requirement;
        final NamedTextColor color;
        final Predicate<Player> condition;

        Rank(String id, String name, boolean halloween, String requirement, NamedTextColor color,
             Predicate<Player> condition) {
            this.id = id;
            this.name = name;
            this.halloween = halloween;
            this.requirement = requirement;
            this.color = color;
            this.condition = condition;
        }
    }

    private static final NamedTextColor[] NORMAL_COLORS = {
            NamedTextColor.GRAY, NamedTextColor.WHITE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.RED, NamedTextColor.YELLOW};

    private final AbilitySMP plugin;
    private final List<Rank> ranks = new ArrayList<>();
    /** Chat is rendered off the main thread, so it reads prefixes from this thread-safe cache. */
    private final Map<UUID, Component> prefixCache = new ConcurrentHashMap<>();

    Ranks(AbilitySMP plugin) {
        this.plugin = plugin;
        build();
        Bukkit.getScheduler().runTaskTimer(plugin, this::minuteTick, 1200L, 1200L);
    }

    // ------------------------------------------------------------ definitions

    private void build() {
        ranks.clear();
        ConfigurationSection normal = plugin.getConfig().getConfigurationSection("ranks.normal");
        if (normal != null && !normal.getKeys(false).isEmpty()) {
            int i = 0;
            for (String id : normal.getKeys(false)) {
                ConfigurationSection r = normal.getConfigurationSection(id);
                if (r == null) continue;
                final int minutes = r.getInt("minutes", 0);
                ranks.add(new Rank(id, r.getString("name", id), false,
                        minutes <= 0 ? "Everyone starts here" : "Play for " + minutes + " minutes in total",
                        NORMAL_COLORS[i % NORMAL_COLORS.length],
                        p -> plugin.profile.of(p).playMinutes >= minutes));
                i++;
            }
        } else {
            String[] names = {"I'm New Here", "Noob", "Unbound Pro", "Unbound Veteran", "Unbound Expert", "Unbound King"};
            String[] ids = {"new-here", "noob", "unbound-pro", "unbound-veteran", "unbound-expert", "unbound-king"};
            int[] mins = {0, 20, 40, 60, 120, 160};
            for (int i = 0; i < names.length; i++) {
                final int m = mins[i];
                ranks.add(new Rank(ids[i], names[i], false,
                        m <= 0 ? "Everyone starts here" : "Play for " + m + " minutes in total",
                        NORMAL_COLORS[i], p -> plugin.profile.of(p).playMinutes >= m));
            }
        }

        final int vet = plugin.getConfig().getInt("ranks.halloween.veteran-kills", 50);
        final int exp = plugin.getConfig().getInt("ranks.halloween.expert-kills", 150);
        final int surviveSeconds = 20 * 60;

        ranks.add(new Rank("hw-starter", "Halloween Starter", true, "Retired: needed the Haunted Forest (ranks you already earned stay)",
                NamedTextColor.GOLD, p -> plugin.profile.of(p).hauntedVisits >= 1));
        ranks.add(new Rank("hw-veteran", "Halloween Veteran", true, "Retired: needed the Haunted Forest (ranks you already earned stay)",
                NamedTextColor.RED, p -> plugin.profile.of(p).hauntedKills >= vet));
        ranks.add(new Rank("hw-expert", "Halloween Expert", true, "Retired: needed the Haunted Forest (ranks you already earned stay)",
                NamedTextColor.DARK_RED, p -> plugin.profile.of(p).hauntedKills >= exp));
        ranks.add(new Rank("hw-power-king", "Halloween Power King", true, "Obtain every Halloween power (Ghost and Vampire)",
                NamedTextColor.LIGHT_PURPLE, this::hasAllHalloweenPowers));
        ranks.add(new Rank("hw-survivor", "Halloween Survivor", true,
                "Retired: needed the Haunted Forest (ranks you already earned stay)",
                NamedTextColor.GREEN, p -> plugin.profile.of(p).survivalSeconds >= surviveSeconds));
        ranks.add(new Rank("hw-king", "Halloween King", true,
                "Retired: needed the Haunted Forest (ranks you already earned stay)",
                NamedTextColor.YELLOW, p -> plugin.profile.of(p).horsemanDefeated
                && plugin.profile.isUnlocked(p.getUniqueId(), "rank:hw-survivor")
                && hasAllHalloweenPowers(p)));
    }

    private boolean hasAllHalloweenPowers(Player p) {
        for (AbilitySMP.Power pw : AbilitySMP.Power.values()) {
            if (pw.isHalloween() && !plugin.profile.owns(p, pw)) return false;
        }
        return true;
    }

    List<Rank> all() {
        return ranks;
    }

    Rank get(String id) {
        for (Rank r : ranks) if (r.id.equals(id)) return r;
        return null;
    }

    // ------------------------------------------------------------ unlocking

    /** Unlocks every rank the player now qualifies for, and equips the newest normal rank automatically. */
    void evaluate(Player p) {
        UUID id = p.getUniqueId();
        for (Rank r : ranks) {
            String key = "rank:" + r.id;
            if (plugin.profile.isUnlocked(id, key) || !r.condition.test(p)) continue;
            plugin.profile.unlock(id, key);
            if (!r.halloween) {
                String cur = plugin.profile.equipped(id, "rank");
                Rank curRank = cur == null ? null : get(cur);
                if (curRank == null || !curRank.halloween) plugin.profile.setEquipped(id, "rank", r.id);
            }
            if (r.id.equals("new-here") && plugin.profile.of(p).playMinutes == 0) {
                continue; // quiet start rank
            }
            p.showTitle(Title.title(Component.text("New rank unlocked!", NamedTextColor.GOLD),
                    Component.text(r.name, r.color),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.sendMessage(Component.text("Rank unlocked: " + r.name + ". Equip it any time in /locker.", r.color));
        }
        refresh(p);
    }

    private void minuteTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.profile.of(p).playMinutes++;
            plugin.profile.touch();
            evaluate(p);
        }
    }

    // ------------------------------------------------------------ display

    Component prefix(UUID id) {
        return prefixCache.getOrDefault(id, Component.empty());
    }

    /** Updates the chat prefix and tab-list name from the equipped rank. */
    void refresh(Player p) {
        String eq = plugin.profile.equipped(p.getUniqueId(), "rank");
        Rank r = eq == null ? null : get(eq);
        Component pre = r == null ? Component.empty() : Component.text("[" + r.name + "] ", r.color);
        prefixCache.put(p.getUniqueId(), pre);
        p.playerListName(pre.append(Component.text(p.getName(), NamedTextColor.WHITE)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        evaluate(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                prefix(source.getUniqueId()).append(name).append(Component.text(": ", NamedTextColor.GRAY)).append(message)));
    }
}
