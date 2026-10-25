package me.abilitysmp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Permanent player data: playtime, unlocked ranks and cosmetics, owned powers, equipped items and
 * Halloween progress. Nothing here is ever reset by an event, a season or by removing Halloween content.
 */
final class Profile {

    static final class Data {
        int playMinutes;
        int hauntedVisits;
        int hauntedKills;
        int survivalSeconds;
        int duelWins;
        int bossKills;
        int halloweenCoins;
        boolean horsemanDefeated;
        final Set<String> unlocked = new LinkedHashSet<>();
        final Set<String> owned = new LinkedHashSet<>();
        final Map<String, String> equipped = new HashMap<>();
    }

    private final AbilitySMP plugin;
    private final File file;
    private YamlConfiguration yaml;
    private final Map<UUID, Data> map = new HashMap<>();
    private boolean dirty;

    Profile(AbilitySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "profile.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 600L, 600L);
    }

    // ------------------------------------------------------------ access

    Data of(UUID id) {
        return map.computeIfAbsent(id, k -> new Data());
    }

    Data of(Player p) {
        return of(p.getUniqueId());
    }

    void touch() {
        dirty = true;
    }

    boolean owns(UUID id, AbilitySMP.Power power) {
        return of(id).owned.contains(power.name());
    }

    boolean owns(Player p, AbilitySMP.Power power) {
        return owns(p.getUniqueId(), power);
    }

    void addOwned(UUID id, AbilitySMP.Power power) {
        if (of(id).owned.add(power.name())) dirty = true;
    }

    boolean isUnlocked(UUID id, String key) {
        return of(id).unlocked.contains(key);
    }

    /** Returns true if this unlock is new. Unlocks are saved right away. */
    boolean unlock(UUID id, String key) {
        if (!of(id).unlocked.add(key)) return false;
        dirty = true;
        save();
        return true;
    }

    String equipped(UUID id, String category) {
        return of(id).equipped.get(category);
    }

    void setEquipped(UUID id, String category, String itemId) {
        if (itemId == null) of(id).equipped.remove(category);
        else of(id).equipped.put(category, itemId);
        dirty = true;
        save();
    }

    // ------------------------------------------------------------ saving

    private void load() {
        map.clear();
        yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Data d = new Data();
                String b = key + ".";
                d.playMinutes = sec.getInt(b + "play-minutes");
                d.duelWins = sec.getInt(b + "duel-wins");
                d.hauntedVisits = sec.getInt(b + "halloween.visits");
                d.hauntedKills = sec.getInt(b + "halloween.kills");
                d.survivalSeconds = sec.getInt(b + "halloween.survival-seconds");
                d.bossKills = sec.getInt(b + "halloween.boss-kills");
                d.halloweenCoins = sec.getInt(b + "halloween.coins");
                d.horsemanDefeated = sec.getBoolean(b + "halloween.horseman-defeated");
                d.unlocked.addAll(sec.getStringList(b + "unlocked"));
                d.owned.addAll(sec.getStringList(b + "owned"));
                ConfigurationSection eq = sec.getConfigurationSection(b + "equipped");
                if (eq != null) for (String c : eq.getKeys(false)) d.equipped.put(c, eq.getString(c));
                map.put(id, d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    void save() {
        yaml.set("players", null);
        for (Map.Entry<UUID, Data> e : map.entrySet()) {
            String b = "players." + e.getKey() + ".";
            Data d = e.getValue();
            yaml.set(b + "play-minutes", d.playMinutes);
            yaml.set(b + "duel-wins", d.duelWins);
            yaml.set(b + "halloween.visits", d.hauntedVisits);
            yaml.set(b + "halloween.kills", d.hauntedKills);
            yaml.set(b + "halloween.survival-seconds", d.survivalSeconds);
            yaml.set(b + "halloween.boss-kills", d.bossKills);
            yaml.set(b + "halloween.coins", d.halloweenCoins);
            yaml.set(b + "halloween.horseman-defeated", d.horsemanDefeated);
            yaml.set(b + "unlocked", new ArrayList<>(d.unlocked));
            yaml.set(b + "owned", new ArrayList<>(d.owned));
            for (Map.Entry<String, String> q : d.equipped.entrySet()) yaml.set(b + "equipped." + q.getKey(), q.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "profile.yml.tmp");
            yaml.save(tmp); // write to a temp file first so a crash can never corrupt the real one
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save profile.yml: " + ex.getMessage());
        }
    }
}
