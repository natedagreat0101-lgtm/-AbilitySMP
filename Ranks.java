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
        /** Playtime ranks auto-equip as you climb; achievement ranks (v9) never steal your equipped rank. */
        final boolean autoEquip;

        Rank(String id, String name, boolean halloween, String requirement, NamedTextColor color,
             Predicate<Player> condition) {
            this(id, name, halloween, requirement, color, condition, !halloween);
        }

        Rank(String id, String name, boolean halloween, String requirement, NamedTextColor color,
             Predicate<Player> condition, boolean autoEquip) {
            this.id = id;
            this.name = name;
            this.halloween = halloween;
            this.requirement = requirement;
            this.color = color;
            this.condition = condition;
            this.autoEquip = autoEquip;
        }
    }

    // ------------------------------------------------------------ staff ranks (v10)

    /** Staff ranks an OP can hand out with /staffrank. They replace the normal rank in chat and the tab list. */
    static final Map<String, String[]> STAFF = Map.of(
            "mod", new String[]{"Mod", "GREEN"},
            "tester", new String[]{"Tester", "YELLOW"},
            "co-owner", new String[]{"Co-Owner", "RED"});

    static NamedTextColor staffColor(String id) {
        return switch (id) {
            case "mod" -> NamedTextColor.GREEN;
            case "tester" -> NamedTextColor.YELLOW;
            default -> NamedTextColor.RED;
        };
    }

    static String staffName(String id) {
        String[] s = STAFF.get(id);
        return s == null ? null : s[0];
    }

    private static final NamedTextColor[] NORMAL_COLORS = {
            NamedTextColor.GRAY, NamedTextColor.WHITE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.RED, NamedTextColor.YELLOW};

    private final AbilitySMP plugin;
    private final List<Rank> ranks = new ArrayList<>();
    /** Chat is rendered off the main thread, so it reads prefixes from this thread-safe cache. */
    private final Map<UUID, Component> prefixCache = new ConcurrentHashMap<>();
    /** Equipped locker title per player, shown after their name in chat and the tab list. */
    private final Map<UUID, String> titleCache = new ConcurrentHashMap<>();

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

        buildAchievementRanks();
    }

    /**
     * v9: 30 achievement ranks - playtime, quests, boss hunting, duels, ranked, collecting and rebirths.
     * They never auto-equip (your playtime rank stays on); equip them yourself in /locker.
     */
    private void buildAchievementRanks() {
        // Playing (total playtime, in minutes)
        playRank("settler", "Settler", 240, NamedTextColor.GREEN);
        playRank("regular", "Server Regular", 480, NamedTextColor.AQUA);
        playRank("grinder", "The Grinder", 960, NamedTextColor.BLUE);
        playRank("no-life", "No Life", 1920, NamedTextColor.DARK_PURPLE);
        playRank("time-lord", "Time Lord", 3840, NamedTextColor.LIGHT_PURPLE);
        playRank("eternal", "The Eternal", 7680, NamedTextColor.GOLD);
        // Quests (total quests completed)
        questRank("quest-starter", "Quest Starter", 10, NamedTextColor.GREEN);
        questRank("quest-ace", "Quest Ace", 30, NamedTextColor.AQUA);
        questRank("quest-master", "Quest Master", 75, NamedTextColor.LIGHT_PURPLE);
        questRank("quest-legend", "Quest Legend", 150, NamedTextColor.GOLD);
        questRank("quest-mythic", "Quest Mythic", 300, NamedTextColor.DARK_RED);
        // Boss hunter (total boss kills)
        bossRank("boss-scout", "Boss Scout", 3, NamedTextColor.GREEN);
        bossRank("boss-hunter", "Boss Hunter", 10, NamedTextColor.AQUA);
        bossRank("boss-slayer", "Boss Slayer", 25, NamedTextColor.RED);
        bossRank("boss-nightmare", "Boss Nightmare", 50, NamedTextColor.DARK_RED);
        bossRank("boss-god", "Boss God", 100, NamedTextColor.GOLD);
        // Specific bosses
        ranks.add(ach("horseman-bane", "Horseman's Bane", "Defeat the Headless Horseman", NamedTextColor.GOLD,
                p -> plugin.profile.of(p).horsemanDefeated));
        ranks.add(ach("mimic-bane", "Chest Breaker", "Defeat the Mimic", NamedTextColor.DARK_RED,
                p -> plugin.profile.of(p).mimicDefeated));
        ranks.add(ach("ghost-bane", "Ghostbuster", "Defeat the Ghost", NamedTextColor.LIGHT_PURPLE,
                p -> plugin.profile.of(p).ghostDefeated));
        // Duels (friendly duel wins)
        duelRank("duel-starter", "Duel Starter", 5, NamedTextColor.GREEN);
        duelRank("duel-champion", "Duel Champion", 25, NamedTextColor.AQUA);
        duelRank("duel-legend", "Duel Legend", 75, NamedTextColor.GOLD);
        // Ranked (ranked rating)
        ratedRank("ranked-riser", "Ranked Riser", 1200, NamedTextColor.GREEN);
        ratedRank("ranked-star", "Ranked Star", 1500, NamedTextColor.AQUA);
        ratedRank("ranked-master", "Ranked Master", 2000, NamedTextColor.LIGHT_PURPLE);
        ratedRank("ranked-king", "King of Ranked", 2400, NamedTextColor.GOLD);
        // Collecting powers
        ranks.add(ach("collector", "Power Collector", "Own 10 different powers", NamedTextColor.AQUA,
                p -> plugin.profile.of(p).owned.size() >= 10));
        ranks.add(ach("hoarder", "Power Hoarder", "Own 18 different powers", NamedTextColor.LIGHT_PURPLE,
                p -> plugin.profile.of(p).owned.size() >= 18));
        ranks.add(ach("completionist", "Completionist", "Own every power (except the one-of-a-kind Wind)", NamedTextColor.GOLD,
                p -> ownableOwned(p) >= AbilitySMP.Power.values().length - 1));
        // Rebirths
        ranks.add(ach("reborn", "The Reborn", "Rebirth at least once (use /rebirth)", NamedTextColor.DARK_PURPLE,
                p -> plugin.profile.of(p).rebirths >= 1));
    }

    /** How many powers this player owns, not counting the one-of-a-kind Wind power. */
    private int ownableOwned(Player p) {
        java.util.Set<String> owned = plugin.profile.of(p).owned;
        return owned.size() - (owned.contains("WIND") ? 1 : 0);
    }

    private Rank ach(String id, String name, String req, NamedTextColor color, Predicate<Player> cond) {
        return new Rank(id, name, false, req, color, cond, false);
    }

    private void playRank(String id, String name, int minutes, NamedTextColor color) {
        ranks.add(ach(id, name, "Play for " + minutes + " minutes in total", color,
                p -> plugin.profile.of(p).playMinutes >= minutes));
    }

    private void questRank(String id, String name, int count, NamedTextColor color) {
        ranks.add(ach(id, name, "Complete " + count + " quests", color,
                p -> plugin.unbound.completed(p) >= count));
    }

    private void bossRank(String id, String name, int kills, NamedTextColor color) {
        ranks.add(ach(id, name, "Defeat " + kills + " bosses", color,
                p -> plugin.profile.of(p).bossKills >= kills));
    }

    private void duelRank(String id, String name, int wins, NamedTextColor color) {
        ranks.add(ach(id, name, "Win " + wins + " friendly duels", color,
                p -> plugin.profile.of(p).duelWins >= wins));
    }

    private void ratedRank(String id, String name, int rating, NamedTextColor color) {
        ranks.add(ach(id, name, "Reach " + rating + " ranked rating (/ranked)", color,
                p -> plugin.profile.of(p).rankedRating >= rating));
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
            if (!r.halloween && r.autoEquip) {
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

    String titleSuffix(UUID id) {
        String t = titleCache.get(id);
        return t == null || t.isEmpty() ? "" : " (" + t + ")";
    }

    /** Updates the chat prefix and tab-list name from the equipped rank and locker title. */
    void refresh(Player p) {
        String staff = plugin.profile.of(p).staffRank;
        String staffLabel = staff == null ? null : staffName(staff);
        Component pre;
        if (staffLabel != null) {
            pre = Component.text("[" + staffLabel + "] ", staffColor(staff)); // a staff rank replaces the normal one
        } else {
            String eq = plugin.profile.equipped(p.getUniqueId(), "rank");
            Rank r = eq == null ? null : get(eq);
            pre = r == null ? Component.empty() : Component.text("[" + r.name + "] ", r.color);
        }
        prefixCache.put(p.getUniqueId(), pre);
        String title = plugin.locker == null ? null : plugin.locker.titleName(p.getUniqueId());
        if (title == null) titleCache.remove(p.getUniqueId());
        else titleCache.put(p.getUniqueId(), title);
        Component list = pre.append(Component.text(p.getName(), NamedTextColor.WHITE));
        if (title != null) list = list.append(Component.text(" (" + title + ")", NamedTextColor.GRAY));
        p.playerListName(list);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        evaluate(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                prefix(source.getUniqueId()).append(name)
                        .append(Component.text(titleSuffix(source.getUniqueId()), NamedTextColor.GRAY))
                        .append(Component.text(": ", NamedTextColor.GRAY)).append(message)));
    }
}
