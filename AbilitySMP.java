package me.abilitysmp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * AbilitySMP - every power, power items (nether stars) and a battle pass in one plugin.
 * Right-click a sword to use your power. Right-click a power star to gain that power. /pass for the battle pass.
 */
public class AbilitySMP extends JavaPlugin implements Listener {

    /** Power rarities (v9), weakest to strongest. */
    enum Rarity {
        COMMON("Common", NamedTextColor.GRAY),
        RARE("Rare", NamedTextColor.AQUA),
        EPIC("Epic", NamedTextColor.LIGHT_PURPLE),
        LEGENDARY("Legendary", NamedTextColor.GOLD),
        MYTHIC("Mythic", NamedTextColor.DARK_RED);

        final String display;
        final NamedTextColor color;

        Rarity(String display, NamedTextColor color) {
            this.display = display;
            this.color = color;
        }
    }

    enum Power {
        FLAME, SPEED, TANK, THOR, SHADOW, HEALER, FEATHER, STEALER, AQUA, HUNTER,
        GHOST, VAMPIRE, TELEPORTER, VOID,
        // Halloween update (v8): ten new Halloween powers
        PUMPKIN, WITCH, SKELETON, SPIDER, SCARECROW, REAPER, CANDY, PHANTOM, WEREWOLF, CURSE,
        // Galactic Update (v10): the four headliners...
        SUPERNOVA, EARTH, WIND, FROST,
        // ...and fifteen more galaxy powers
        STARLIGHT, COMET, METEOR, LUNAR, PULSAR, NEBULA, SOLAR_FLARE, ECLIPSE, ASTEROID,
        ZERO_GRAVITY, WORMHOLE, QUASAR, BLACK_HOLE, ANTIMATTER, ROCKET,
        // v12: unlocked by a 7-day quest streak, never sold or rolled
        HACKER;

        /** Galactic Update powers (v10). */
        boolean isGalaxy() {
            return switch (this) {
                case SUPERNOVA, EARTH, WIND, FROST, STARLIGHT, COMET, METEOR, LUNAR, PULSAR, NEBULA,
                     SOLAR_FLARE, ECLIPSE, ASTEROID, ZERO_GRAVITY, WORMHOLE, QUASAR, BLACK_HOLE,
                     ANTIMATTER, ROCKET -> true;
                default -> false;
            };
        }

        /** Halloween powers: Ghost, Vampire and the ten v8 Halloween powers. */
        boolean isHalloween() {
            return switch (this) {
                case GHOST, VAMPIRE, PUMPKIN, WITCH, SKELETON, SPIDER, SCARECROW, REAPER, CANDY,
                     PHANTOM, WEREWOLF, CURSE -> true;
                default -> false;
            };
        }

        /** Rarity (v9): shown in menus and shops, and it drives the Power Crate odds and shop prices. */
        Rarity rarity() {
            return switch (this) {
                case SPEED, FLAME, FEATHER, AQUA, CANDY, STARLIGHT, ROCKET -> Rarity.COMMON;
                case HUNTER, HEALER, SHADOW, THOR, SKELETON, SPIDER, PUMPKIN,
                     COMET, LUNAR, METEOR -> Rarity.RARE;
                case STEALER, TANK, WITCH, SCARECROW, PHANTOM, WEREWOLF,
                     PULSAR, NEBULA, SOLAR_FLARE, ECLIPSE -> Rarity.EPIC;
                case TELEPORTER, VOID, REAPER, CURSE, SUPERNOVA, FROST,
                     ASTEROID, ZERO_GRAVITY, WORMHOLE, QUASAR -> Rarity.LEGENDARY;
                case GHOST, VAMPIRE, EARTH, WIND, BLACK_HOLE, ANTIMATTER, HACKER -> Rarity.MYTHIC;
            };
        }

        /** Name used in config.yml, e.g. jack-o-lantern */
        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        static Power parse(String s) {
            if (s == null) return null;
            String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (n.equals("JACK_O_LANTERN")) return GHOST; // Jack O Lantern was removed
            if (n.equals("MIMIC")) return VOID; // Mimic was removed, Void replaces it
            try {
                return valueOf(n);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final String DRAIN = "drain_";
    private static final String GAIN = "gain_";
    private static final String TANK_KEY = "tank_hearts";

    private final Map<UUID, Power> powers = new HashMap<>();
    /** The quick switch (v10): each player's second loadout power ("slot 2"). Slot 1 is the equipped power. */
    private final Map<UUID, Power> standby = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** haunter UUID -> players whose hearts that haunter is holding */
    private final Map<UUID, Set<UUID>> drained = new HashMap<>();
    private final Random random = new Random();

    private File dataFile;
    YamlConfiguration data;
    private NamespacedKey powerItemKey;
    private NamespacedKey rerollKey;
    Tutorial tutorial;
    Admin admin;
    Unbound unbound;
    Profile profile;
    Ranks ranks;
    Locker locker;
    Menus menus;
    PowerMenus powerMenus;
    GalaxyBosses bosses;
    FightTotem fightTotem;
    BossParty party;
    Duel duel;
    Shops shops;
    Crates crates;
    Trading trading;
    Ranked ranked;
    Rebirths rebirths;
    Spawn spawn;
    Weapons weapons;
    ItemShop itemShop;
    Lasso lasso;
    Music music;
    private NamespacedKey activatorKey;
    private NamespacedKey dupedKey;
    /** The two power slots shown at the TOP of the screen (v12), one boss bar per player. */
    final Map<UUID, BossBar> slotBars = new HashMap<>();
    private final Set<UUID> needsActivator = new HashSet<>();

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        powerItemKey = new NamespacedKey(this, "power_item");
        rerollKey = new NamespacedKey(this, "reroll_token");
        activatorKey = new NamespacedKey(this, "power_activator");
        dupedKey = new NamespacedKey(this, "duped_sword");
        registerRerollRecipe();

        dataFile = new File(getDataFolder(), "players.yml");
        loadData();

        fightTotem = new FightTotem(this);
        party = new BossParty();
        profile = new Profile(this);
        unbound = new Unbound(this);
        ranks = new Ranks(this);
        locker = new Locker(this);
        menus = new Menus();
        powerMenus = new PowerMenus(this);
        bosses = new GalaxyBosses(this);
        duel = new Duel(this);
        crates = new Crates(this);
        trading = new Trading(this);
        ranked = new Ranked(this);
        rebirths = new Rebirths(this);
        shops = new Shops(this);
        tutorial = new Tutorial(this);
        admin = new Admin(this);
        spawn = new Spawn(this);
        weapons = new Weapons(this);
        itemShop = new ItemShop(this);
        lasso = new Lasso(this);
        music = new Music(this);
        lasso.registerRecipe();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(tutorial, this);
        Bukkit.getPluginManager().registerEvents(admin, this);
        Bukkit.getPluginManager().registerEvents(unbound, this);
        Bukkit.getPluginManager().registerEvents(ranks, this);
        Bukkit.getPluginManager().registerEvents(locker, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(shops, this);
        Bukkit.getPluginManager().registerEvents(bosses, this);
        Bukkit.getPluginManager().registerEvents(fightTotem, this);
        Bukkit.getPluginManager().registerEvents(duel, this);
        Bukkit.getPluginManager().registerEvents(crates, this);
        Bukkit.getPluginManager().registerEvents(trading, this);
        Bukkit.getPluginManager().registerEvents(ranked, this);
        Bukkit.getPluginManager().registerEvents(spawn, this);
        Bukkit.getPluginManager().registerEvents(weapons, this);
        Bukkit.getPluginManager().registerEvents(itemShop, this);
        Bukkit.getPluginManager().registerEvents(lasso, this);
        Bukkit.getScheduler().runTaskTimer(this, this::passiveTick, 20L, 20L);
        // the quick-switch bar above the hotbar refreshes every 2 seconds, and so does
        // the power-slots bar at the TOP of the screen (v12) and the Activator's look
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                showSwitcher(p);
                refreshSlotBar(p);
                refreshActivator(p);
            }
        }, 23L, 40L);
        // duped swords (Hacker, v12) vanish 30 seconds after they were copied
        Bukkit.getScheduler().runTaskTimer(this, this::cleanDupedSwords, 27L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) setupPlayer(p);
    }

    @Override
    public void onDisable() {
        if (admin != null) admin.shutdown();
        if (bosses != null) bosses.shutdown();
        if (duel != null) duel.shutdown();
        if (ranked != null) ranked.shutdown();
        if (trading != null) trading.shutdown();
        if (fightTotem != null) fightTotem.shutdown();
        if (shops != null) shops.save();
        if (unbound != null) unbound.save();
        if (profile != null) profile.save();
        for (Player p : Bukkit.getOnlinePlayers()) clearHeartSteals(p);
    }

    /** Adds new settings and powers to configs written by older versions. */
    private void migrateConfig() {
        FileConfiguration cfg = getConfig();
        int v = cfg.contains("config-version", true) ? cfg.getInt("config-version") : 1;
        if (v < 3) {
            List<String> enabled = new ArrayList<>(cfg.getStringList("powers.enabled"));
            enabled.remove("mimic");
            for (String s : new String[]{"jack-o-lantern", "ghost", "vampire", "teleporter"}) {
                if (!enabled.contains(s)) enabled.add(s);
            }
            cfg.set("powers.enabled", enabled);
            cfg.set("config-version", 3);
        }
        if (v < 5) {
            List<String> en = new ArrayList<>(cfg.getStringList("powers.enabled"));
            en.remove("mimic");
            if (!en.contains("void")) en.add("void");
            cfg.set("powers.enabled", en);
            cfg.set("battlepass", null);   // the battle pass was removed
            cfg.set("speed.cooldown", 30); // Speed's super is now a Speed III burst
            cfg.set("config-version", 5);
        }
        if (v < 6) {
            List<String> en6 = new ArrayList<>(cfg.getStringList("powers.enabled"));
            en6.remove("jack-o-lantern");
            cfg.set("powers.enabled", en6);
            cfg.set("jack-o-lantern", null);
            cfg.set("drops.jack-o-lantern", null);
            cfg.set("passives.jack-o-lantern-glow-radius", null);
            cfg.set("config-version", 6);
        }
        if (v < 7) {
            cfg.set("haunted", null);                 // the Haunted Forest was removed
            cfg.set("shop.coin-prices", null);        // powers now cost Unbound Coins
            cfg.set("quests.haunted-hunter", null);
            cfg.set("quests.haunted-survivor", null);
            cfg.set("config-version", 7);
        }
        if (v < 8) {
            List<String> en8 = new ArrayList<>(cfg.getStringList("powers.enabled"));
            for (String s : new String[]{"pumpkin", "witch", "skeleton", "spider", "scarecrow",
                    "reaper", "candy", "phantom", "werewolf", "curse"}) {
                if (!en8.contains(s)) en8.add(s);
            }
            cfg.set("powers.enabled", en8);
            cfg.set("config-version", 8);
        }
        if (v < 9) {
            // v9 "Galactic Update Eve": power levels go to 100 and the economy is rebalanced,
            // so the numbers that shape both are refreshed on old configs. New sections
            // (crates, ranked, rebirth, galactic, boss cooldowns) arrive via copyDefaults.
            cfg.set("upgrade.max-level", 100);
            cfg.set("upgrade.costs", java.util.List.of(25, 50, 100, 200, 400, 800, 1500, 3000, 6000, 12000));
            cfg.set("upgrade.cooldown-per-level", 0.01);
            cfg.set("points.play-minutes", 25);
            cfg.set("points.play-reward", 8);
            cfg.set("shop.restock-minutes", 8);
            cfg.set("shop.stock-per-item", 2);
            cfg.set("horseman.reward-points", 40);
            cfg.set("duel.win-points", 12);
            cfg.set("config-version", 9);
        }
        if (v < 10) {
            // v10 "The Galactic Update": the 19 galaxy powers join the enabled list, the
            // Halloween shop/sections retire (Halloween powers, ranks and cosmetics that
            // players already earned are NOT touched), and the candy quest retires with
            // Trick or Treat Night. New prices and the galaxy-shop arrive via copyDefaults.
            List<String> en10 = new ArrayList<>(cfg.getStringList("powers.enabled"));
            for (String s : new String[]{"supernova", "earth", "wind", "frost", "starlight", "comet",
                    "meteor", "lunar", "pulsar", "nebula", "solar-flare", "eclipse", "asteroid",
                    "zero-gravity", "wormhole", "quasar", "black-hole", "antimatter", "rocket"}) {
                if (!en10.contains(s)) en10.add(s);
            }
            cfg.set("powers.enabled", en10);
            cfg.set("quests.candy-collector", null); // Trick or Treat Night is gone
            cfg.set("trickortreat", null);
            cfg.set("halloween-shop", null);
            cfg.set("events", null);
            cfg.set("rebirth.galaxy-coins-per-rebirth",
                    cfg.getInt("rebirth.halloween-coins-per-rebirth", 20));
            cfg.set("config-version", 10);
        }
        if (v < 11) {
            // v11 "The Spawn Update": the DonutSMP-style spawn (NPCs, countdown board,
            // crate chests, RTP). The spawn section arrives via copyDefaults; the spawn
            // point itself is set in game with /setspawn or /spawnbuild.
            cfg.set("config-version", 11);
        }
        if (v < 13) {
            // v13: hotbar-style power bar + /music - nothing to move; the HUD default
            // reads a config default when the key is absent from an old config.
            cfg.set("config-version", 13);
        }
        if (v < 12) {
            // v12: the six galaxy bosses replace the Horseman, the Mimic and the Ghost,
            // so the old boss sections and their cooldowns go away. Hacker joins the
            // enabled list (it is unlocked by a 7-day quest streak, never sold).
            List<String> en12 = new ArrayList<>(cfg.getStringList("powers.enabled"));
            if (!en12.contains("hacker")) en12.add("hacker");
            cfg.set("powers.enabled", en12);
            cfg.set("horseman", null);
            cfg.set("mimic", null);
            cfg.set("ghost-boss", null);
            cfg.set("boss.cooldown-seconds.horseman", null);
            cfg.set("boss.cooldown-seconds.mimic", null);
            cfg.set("boss.cooldown-seconds.ghost", null);
            cfg.set("config-version", 12);
        }
        cfg.options().copyDefaults(true);
        saveConfig();
    }

    private void loadData() {
        powers.clear();
        standby.clear();
        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = data.getConfigurationSection("players");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                Power p = Power.parse(sec.getString(id + ".power"));
                if (p != null) powers.put(uuid, p);
                Power s = Power.parse(sec.getString(id + ".standby"));
                if (s != null) standby.put(uuid, s);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // =====================================================================
    // The quick switch (v10): TWO powers equipped at once. Slot 1 is your
    // active power; slot 2 waits. Press F (swap hands) or crouch +
    // right-click your Power Activator to switch. The two slots are shown
    // above your hotbar in the action bar, like a little [1] [2] bar.
    // =====================================================================

    Power standbyOf(Player p) {
        return standby.get(p.getUniqueId());
    }

    /** Put a power you own into slot 2 (used by the /powers menu). */
    boolean setStandby(Player p, Power pw) {
        if (!profile.owns(p, pw)) {
            bar(p, "You don't own that power yet. Check /shop.", NamedTextColor.RED);
            return false;
        }
        if (!isEnabled(pw)) {
            bar(p, "That power is disabled.", NamedTextColor.RED);
            return false;
        }
        if (powerOf(p) == pw) {
            bar(p, "That power is already in slot 1.", NamedTextColor.GRAY);
            return false;
        }
        if (standbyOf(p) == pw) {
            bar(p, "That power is already in slot 2.", NamedTextColor.GRAY);
            return false;
        }
        standby.put(p.getUniqueId(), pw);
        data.set("players." + p.getUniqueId() + ".standby", pw.name());
        saveData();
        p.sendMessage(Component.text(pretty(pw.key()) + " is now in slot 2. Switch with F or crouch + right-click!",
                colorFor(pw)));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.4f);
        showSwitcher(p);
        return true;
    }

    /** Swap slot 1 and slot 2. */
    void switchPower(Player p) {
        Power cur = powerOf(p);
        Power st = standbyOf(p);
        if (st == null) {
            bar(p, "No power in slot 2. Open /powers and right-click a power to fill slot 2.", NamedTextColor.GRAY);
            return;
        }
        UUID id = p.getUniqueId();
        if (cur == null) {
            standby.remove(id);
            data.set("players." + id + ".standby", "NONE");
        } else {
            standby.put(id, cur);
            data.set("players." + id + ".standby", cur.name());
        }
        setPower(p, st); // saves players.yml, tank hearts, rank checks
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.5f);
        puff(p.getLocation().add(0, 1, 0), particleFor(st), 18);
        showSwitcher(p);
    }

    /** The little [1] [2] bar above the hotbar (the action bar), like the picture of the quick switch. */
    void showSwitcher(Player p) {
        if (!hudMode(p).equals("text")) return;
        Power cur = powerOf(p);
        Power st = standbyOf(p);
        if (cur == null || st == null) return;
        p.sendActionBar(Component.text("[1] ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(pretty(cur.key()), colorFor(cur), TextDecoration.BOLD))
                .append(Component.text("   [2] ", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .append(Component.text(pretty(st.key()), NamedTextColor.GRAY))
                .append(Component.text("   switch: F / crouch+click", NamedTextColor.DARK_GRAY)));
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (standbyOf(p) == null) return; // no loadout: the swap key works normally
        if (fightTotem.isProtected(p)) return; // FightTotem owns the swap during fights
        if (fightTotem.isFightTotem(e.getMainHandItem()) || fightTotem.isFightTotem(e.getOffHandItem())) return;
        e.setCancelled(true); // the swap key switches powers instead of swapping items
        switchPower(p);
    }

    void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }

    // =====================================================================
    // Small helpers
    // =====================================================================

    private double d(String path, double def) {
        return getConfig().getDouble(path, def);
    }

    private int i(String path, int def) {
        return getConfig().getInt(path, def);
    }

    Power powerOf(Player p) {
        return powers.get(p.getUniqueId());
    }

    boolean isEnabled(Power p) {
        return getConfig().getStringList("powers.enabled").contains(p.key());
    }

    private NamespacedKey key(String s) {
        return new NamespacedKey(this, s.toLowerCase(Locale.ROOT));
    }

    void bar(Player p, String text, NamedTextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    private boolean particles() {
        return getConfig().getBoolean("passives.particles-enabled", true);
    }

    private void puff(Location loc, Particle type, int count) {
        if (particles()) loc.getWorld().spawnParticle(type, loc, count, 0.4, 0.6, 0.4, 0.02);
    }

    /** Horizontal look direction that is never zero. */
    private Vector flat(Location loc) {
        Vector v = loc.getDirection().setY(0);
        if (v.lengthSquared() < 1.0E-6) return new Vector(0, 0, 1);
        return v.normalize();
    }

    /** Players and (by default) hostile mobs count as enemies. Pets, villagers and armor stands never do. */
    private boolean isEnemy(Player from, Entity e) {
        if (e.equals(from) || !e.isValid() || e.isDead()) return false;
        if (e instanceof Player other) {
            return other.getGameMode() != GameMode.SPECTATOR && getConfig().getBoolean("enemies.players", true);
        }
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand) return false;
        if (e instanceof Enemy) return getConfig().getBoolean("enemies.hostile-mobs", true);
        if (e instanceof Tameable t && t.isTamed()) return false;
        return e instanceof Mob && getConfig().getBoolean("enemies.passive-mobs", false);
    }

    private List<LivingEntity> enemiesNear(Player from, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : from.getWorld().getNearbyLivingEntities(from.getLocation(), radius)) {
            if (isEnemy(from, le) && le.getLocation().distanceSquared(from.getLocation()) <= radius * radius) {
                out.add(le);
            }
        }
        return out;
    }

    private LivingEntity nearestEnemy(Player from, double range) {
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity le : enemiesNear(from, range)) {
            double dist = le.getLocation().distanceSquared(from.getLocation());
            if (dist <= bestDist) {
                bestDist = dist;
                best = le;
            }
        }
        return best;
    }

    private void effect(Player p, PotionEffectType type, int level) {
        p.addPotionEffect(new PotionEffect(type, 60, Math.max(0, level - 1), true, false, false));
    }

    private void glowNearby(Player owner, double radius) {
        for (LivingEntity other : enemiesNear(owner, radius)) {
            other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, false));
        }
    }

    static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (String part : s.toLowerCase(Locale.ROOT).split("[_-]")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static boolean isSword(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    void give(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItem(p.getLocation(), rest);
        }
    }

    // =====================================================================
    // Giving / rolling powers
    // =====================================================================

    private boolean isHeldByOther(Power power, UUID except) {
        for (Map.Entry<UUID, Power> e : powers.entrySet()) {
            if (e.getValue() == power && !e.getKey().equals(except)) return true;
        }
        return false;
    }

    private boolean isLimited(Power p) {
        return getConfig().getBoolean(p.key() + ".limited", false);
    }

    /** Weighted random pick using the "drops" chances as weights. */
    private Power roll(UUID forWho, Power exclude) {
        List<Power> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Power p : Power.values()) {
            if (p == exclude || !isEnabled(p) || profile.owns(forWho, p)) continue;
            if (p.isHalloween() || p == Power.WIND || p == Power.HACKER) continue; // v10/v12: retired Halloween powers, the one-of-a-kind Wind and the streak-only Hacker never roll
            double w = d("drops." + p.key(), 0);
            if (w <= 0) continue;
            if (isLimited(p) && isHeldByOther(p, forWho)) continue;
            pool.add(p);
            weights.add(w);
            total += w;
        }
        if (pool.isEmpty()) return null;
        double r = random.nextDouble() * total;
        for (int k = 0; k < pool.size(); k++) {
            r -= weights.get(k);
            if (r <= 0) return pool.get(k);
        }
        return pool.get(pool.size() - 1);
    }

    private void setPower(Player p, Power power) {
        removePowerEffects(p);
        UUID id = p.getUniqueId();
        if (power == null) {
            powers.remove(id);
            data.set("players." + id + ".power", "NONE");
        } else {
            powers.put(id, power);
            data.set("players." + id + ".power", power.name());
        }
        if (power != null) profile.addOwned(id, power);
        applyTankHearts(p);
        saveData();
        if (ranks != null) ranks.evaluate(p);
        refreshActivator(p); // the Activator sword shows the new power (resource pack)
        refreshSlotBar(p);
    }

    private void removePowerEffects(Player p) {
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        clearHeartSteals(p);
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) {
            removeModifier(a, key(TANK_KEY));
            if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
        }
    }

    private void setupPlayer(Player p) {
        UUID id = p.getUniqueId();
        clearHeartSteals(p); // safety net after crashes
        p.discoverRecipe(rerollKey);
        boolean firstJoin = !data.contains("players." + id);
        if (firstJoin) {
            data.set("players." + id + ".rerolled", false);
            data.set("players." + id + ".first-join-at", System.currentTimeMillis());
            saveData();
            Power starter = Power.parse(getConfig().getString("starter-power", "speed"));
            if (starter == null) starter = Power.SPEED;
            setPower(p, starter);
            data.set("players." + id + ".activator-given", true);
            saveData();
            give(p, activatorItem());
            p.sendMessage(Component.text("You start with the " + pretty(starter.key())
                    + " power and a Power Activator sword. Hold the sword and right-click to use your power!",
                    NamedTextColor.GOLD));
            applyTankHearts(p);
            tutorial.onFirstJoin(p);
            return;
        }
        profile.addOwned(id, Power.SPEED);
        if (powerOf(p) != null) profile.addOwned(id, powerOf(p));
        applyTankHearts(p);
        giveActivatorOnce(p);
        Notes.joinHint(this, p);
    }

    private void applyTankHearts(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        removeModifier(a, key(TANK_KEY));
        if (powerOf(p) == Power.TANK && isEnabled(Power.TANK)) {
            double extra = i("passives.tank-extra-hearts", 4) * 2.0;
            a.addModifier(new AttributeModifier(key(TANK_KEY), extra, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    // =====================================================================
    // Power items (nether stars)
    // =====================================================================

    NamedTextColor colorFor(Power p) {
        return switch (p) {
            case FLAME -> NamedTextColor.GOLD;
            case SPEED -> NamedTextColor.AQUA;
            case TANK -> NamedTextColor.GRAY;
            case THOR -> NamedTextColor.YELLOW;
            case SHADOW -> NamedTextColor.DARK_GRAY;
            case HEALER -> NamedTextColor.LIGHT_PURPLE;
            case FEATHER -> NamedTextColor.WHITE;
            case STEALER -> NamedTextColor.DARK_PURPLE;
            case AQUA -> NamedTextColor.BLUE;
            case HUNTER -> NamedTextColor.GREEN;
            case GHOST -> NamedTextColor.DARK_AQUA;
            case VAMPIRE -> NamedTextColor.DARK_RED;
            case TELEPORTER -> NamedTextColor.DARK_BLUE;
            case VOID -> NamedTextColor.DARK_PURPLE;
            case PUMPKIN -> NamedTextColor.GOLD;
            case WITCH -> NamedTextColor.DARK_PURPLE;
            case SKELETON -> NamedTextColor.WHITE;
            case SPIDER -> NamedTextColor.DARK_GRAY;
            case SCARECROW -> NamedTextColor.YELLOW;
            case REAPER -> NamedTextColor.DARK_RED;
            case CANDY -> NamedTextColor.LIGHT_PURPLE;
            case PHANTOM -> NamedTextColor.AQUA;
            case WEREWOLF -> NamedTextColor.GRAY;
            case CURSE -> NamedTextColor.DARK_PURPLE;
            case SUPERNOVA -> NamedTextColor.RED;
            case EARTH -> NamedTextColor.DARK_GREEN;
            case WIND -> NamedTextColor.WHITE;
            case FROST -> NamedTextColor.AQUA;
            case STARLIGHT -> NamedTextColor.YELLOW;
            case COMET -> NamedTextColor.BLUE;
            case METEOR -> NamedTextColor.RED;
            case LUNAR -> NamedTextColor.LIGHT_PURPLE;
            case PULSAR -> NamedTextColor.YELLOW;
            case NEBULA -> NamedTextColor.DARK_PURPLE;
            case SOLAR_FLARE -> NamedTextColor.GOLD;
            case ECLIPSE -> NamedTextColor.DARK_GRAY;
            case ASTEROID -> NamedTextColor.GRAY;
            case ZERO_GRAVITY -> NamedTextColor.LIGHT_PURPLE;
            case WORMHOLE -> NamedTextColor.DARK_BLUE;
            case QUASAR -> NamedTextColor.LIGHT_PURPLE;
            case BLACK_HOLE -> NamedTextColor.DARK_GRAY;
            case ANTIMATTER -> NamedTextColor.DARK_RED;
            case ROCKET -> NamedTextColor.GOLD;
            case HACKER -> NamedTextColor.GREEN;
        };
    }

    ItemStack powerItem(Power p) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(line(pretty(p.key()) + " Power", colorFor(p)));
        m.lore(List.of(line("Right-click to gain this power.", NamedTextColor.GRAY),
                line("Your old power comes back as a star.", NamedTextColor.DARK_GRAY)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(powerItemKey, PersistentDataType.STRING, p.name());
        it.setItemMeta(m);
        return it;
    }

    private Power powerFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return null;
        String v = item.getItemMeta().getPersistentDataContainer().get(powerItemKey, PersistentDataType.STRING);
        return Power.parse(v);
    }

    /** Holding right-click fires the use event again and again; this guard makes one click = one use. */
    private final Map<UUID, Long> starGuard = new HashMap<>();

    private void useStar(Player p, ItemStack hand, Power newPower) {
        long now = System.currentTimeMillis();
        if (now - starGuard.getOrDefault(p.getUniqueId(), 0L) < 1500L) return;
        starGuard.put(p.getUniqueId(), now);

        if (!isEnabled(newPower)) {
            bar(p, "That power is disabled.", NamedTextColor.RED);
            return;
        }
        if (profile.owns(p, newPower)) {
            bar(p, "You already own that power. Use /powers to equip it.", NamedTextColor.GRAY);
            return;
        }
        if (newPower == Power.WIND && !windFree(p)) return; // only ONE Wind exists
        hand.setAmount(hand.getAmount() - 1);
        setPower(p, newPower); // unlocks it permanently and equips it
        p.sendMessage(Component.text("You unlocked and equipped the " + pretty(newPower.key()) + " power!",
                colorFor(newPower)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        puff(p.getLocation().add(0, 1, 0), Particle.END_ROD, 30);
    }

    /** The Wind power is one of a kind: true if nobody else owns it yet (or this player is the owner). */
    boolean windFree(Player p) {
        UUID owner = profile.ownerOf(Power.WIND);
        if (owner == null || owner.equals(p.getUniqueId())) return true;
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        bar(p, "The Wind power already belongs to " + (op.getName() == null ? "someone" : op.getName())
                + " - only ONE exists!", NamedTextColor.RED);
        return false;
    }

    /** Equip a power you already own (used by the power menu). */
    boolean equipPower(Player p, Power pw) {
        if (!profile.owns(p, pw)) {
            bar(p, "You don't own that power yet. Check /shop.", NamedTextColor.RED);
            return false;
        }
        if (pw == Power.WIND && !windFree(p)) return false;
        if (!isEnabled(pw)) {
            bar(p, "That power is disabled.", NamedTextColor.RED);
            return false;
        }
        if (powerOf(p) == pw) {
            bar(p, "Already equipped.", NamedTextColor.GRAY);
            return true;
        }
        setPower(p, pw);
        p.sendMessage(Component.text("Equipped the " + pretty(pw.key()) + " power.", colorFor(pw)));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.2f);
        return true;
    }

    // =====================================================================
    // Reroll token (craftable): diamonds all around + gold block in the middle
    // =====================================================================

    ItemStack rerollItem() {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta m = it.getItemMeta();
        m.displayName(line("Reroll Token", NamedTextColor.LIGHT_PURPLE));
        m.lore(List.of(line("Right-click to reroll your power.", NamedTextColor.GRAY),
                line("You get a new random power. The old one is lost!", NamedTextColor.DARK_GRAY)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(rerollKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    private boolean isRerollToken(ItemStack item) {
        return item != null && item.getType() == Material.AMETHYST_SHARD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(rerollKey, PersistentDataType.STRING);
    }

    private void registerRerollRecipe() {
        Bukkit.removeRecipe(rerollKey);
        if (!getConfig().getBoolean("reroll.recipe-enabled", true)) return;
        ShapedRecipe r = new ShapedRecipe(rerollKey, rerollItem());
        r.shape("DDD", "DGD", "DDD");
        r.setIngredient('D', Material.DIAMOND);
        r.setIngredient('G', Material.GOLD_BLOCK);
        Bukkit.addRecipe(r);
    }

    private void useRerollToken(Player p, ItemStack hand) {
        Power old = powerOf(p);
        Power next = roll(p.getUniqueId(), old);
        if (next == null) {
            bar(p, "You already own every power that can be rolled.", NamedTextColor.RED);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        setPower(p, next);
        p.sendMessage(Component.text("Rerolled! You now have the " + pretty(next.key()) + " power.", colorFor(next)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
        puff(p.getLocation().add(0, 1, 0), Particle.END_ROD, 30);
    }

    // =====================================================================
    // Idle abilities (run once per second)
    // =====================================================================

    private void passiveTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Power power = powerOf(p);
            if (power == null || !isEnabled(power) || p.getGameMode() == GameMode.SPECTATOR) continue;

            switch (power) {
                case FLAME -> effect(p, PotionEffectType.FIRE_RESISTANCE, 1);
                case SPEED -> effect(p, PotionEffectType.SPEED, i("speed.idle-level", 1));
                case THOR -> effect(p, PotionEffectType.STRENGTH, i("passives.thor-strength-level", 1));
                case STEALER -> effect(p, PotionEffectType.HASTE, i("passives.stealer-haste-level", 1));
                case HEALER -> effect(p, PotionEffectType.REGENERATION, i("passives.healer-regeneration-level", 2));
                case SHADOW, VAMPIRE -> effect(p, PotionEffectType.NIGHT_VISION, 1);
                case AQUA -> {
                    effect(p, PotionEffectType.WATER_BREATHING, 1);
                    effect(p, PotionEffectType.DOLPHINS_GRACE, i("passives.aqua-dolphins-grace-level", 1));
                }
                case HUNTER -> glowNearby(p, d("passives.hunter-glow-radius", 30));
                case GHOST -> {
                    effect(p, PotionEffectType.SPEED, i("passives.ghost-speed-level", 1));
                    glowNearby(p, d("ghost.glow-radius", 30));
                }
                case TELEPORTER -> effect(p, PotionEffectType.SPEED, i("passives.teleporter-speed-level", 3));
                case VOID -> effect(p, PotionEffectType.STRENGTH, i("passives.void-strength-level", 3));
                case WEREWOLF -> {
                    effect(p, PotionEffectType.SPEED, i("passives.werewolf-speed-level", 1));
                    effect(p, PotionEffectType.NIGHT_VISION, 1);
                }
                case WITCH -> effect(p, PotionEffectType.NIGHT_VISION, 1);
                // ------------------------------------------------ Galactic powers (v10)
                case SUPERNOVA -> effect(p, PotionEffectType.FIRE_RESISTANCE, 1);
                case EARTH -> effect(p, PotionEffectType.RESISTANCE, 2);
                case STARLIGHT -> effect(p, PotionEffectType.NIGHT_VISION, 1);
                case COMET -> effect(p, PotionEffectType.SPEED, 1);
                case LUNAR -> effect(p, PotionEffectType.JUMP_BOOST, 1);
                case SOLAR_FLARE -> effect(p, PotionEffectType.STRENGTH, 1);
                case FROST -> frostWalk(p);
                case ASTEROID -> asteroidOrbit(p);
                default -> { } // TANK (hearts), FEATHER and WIND (fall damage) are handled elsewhere
            }

            if (particles() && !p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                p.getWorld().spawnParticle(particleFor(power), p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
            }
        }
    }

    /** Frost (v10): Frost Walker all the time - the water under your feet freezes as you walk. */
    private void frostWalk(Player p) {
        int r = i("frost.walk-radius", 2);
        Location base = p.getLocation();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                for (int dy = -1; dy <= 0; dy++) {
                    org.bukkit.block.Block b = base.clone().add(dx, dy, dz).getBlock();
                    if (b.getType() == Material.WATER) b.setType(Material.FROSTED_ICE, false);
                }
            }
        }
    }

    /** Asteroid (v10): rocks orbit you and chip away at anything that gets too close. */
    private void asteroidOrbit(Player p) {
        Location c = p.getLocation().add(0, 1.2, 0);
        double ang = (System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            double a = ang + k * (Math.PI * 2 / 3);
            Location rock = c.clone().add(Math.cos(a) * 1.6, Math.sin(a * 2) * 0.3, Math.sin(a) * 1.6);
            rock.getWorld().spawnParticle(Particle.CRIT, rock, 2, 0.05, 0.05, 0.05, 0.01);
        }
        for (LivingEntity t : enemiesNear(p, d("asteroid.orbit-radius", 3.2))) {
            t.damage(d("asteroid.orbit-damage", 1.0), p);
            t.setNoDamageTicks(0);
        }
    }

    private Particle particleFor(Power power) {
        return switch (power) {
            case FLAME -> Particle.FLAME;
            case SPEED, FEATHER -> Particle.CLOUD;
            case TANK -> Particle.CRIT;
            case THOR -> Particle.ELECTRIC_SPARK;
            case SHADOW -> Particle.SMOKE;
            case HEALER -> Particle.HEART;
            case STEALER -> Particle.WITCH;
            case AQUA -> Particle.SPLASH;
            case HUNTER -> Particle.END_ROD;
            case GHOST -> Particle.SOUL;
            case VAMPIRE -> Particle.CRIMSON_SPORE;
            case TELEPORTER -> Particle.PORTAL;
            case VOID -> Particle.REVERSE_PORTAL;
            case PUMPKIN -> Particle.FLAME;
            case WITCH -> Particle.WITCH;
            case SKELETON -> Particle.ASH;
            case SPIDER -> Particle.SQUID_INK;
            case SCARECROW -> Particle.SMOKE;
            case REAPER -> Particle.SOUL;
            case CANDY -> Particle.HAPPY_VILLAGER;
            case PHANTOM -> Particle.CLOUD;
            case WEREWOLF -> Particle.CRIT;
            case CURSE -> Particle.ENCHANT;
            case SUPERNOVA -> Particle.FLAME;
            case EARTH -> Particle.CRIT;
            case WIND -> Particle.CLOUD;
            case FROST -> Particle.SNOWFLAKE;
            case STARLIGHT -> Particle.END_ROD;
            case COMET -> Particle.SNOWFLAKE;
            case METEOR -> Particle.FLAME;
            case LUNAR -> Particle.END_ROD;
            case PULSAR -> Particle.ELECTRIC_SPARK;
            case NEBULA -> Particle.DRAGON_BREATH;
            case SOLAR_FLARE -> Particle.FLAME;
            case ECLIPSE -> Particle.SQUID_INK;
            case ASTEROID -> Particle.CRIT;
            case ZERO_GRAVITY -> Particle.END_ROD;
            case WORMHOLE -> Particle.REVERSE_PORTAL;
            case QUASAR -> Particle.GLOW;
            case BLACK_HOLE -> Particle.SQUID_INK;
            case ANTIMATTER -> Particle.EXPLOSION;
            case ROCKET -> Particle.FLAME;
            case HACKER -> Particle.HAPPY_VILLAGER;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Power power = powerOf(p);
        if ((power == Power.FEATHER || power == Power.WIND) && isEnabled(power)
                && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true); // Feather and Wind: no fall damage
        }
    }

    // =====================================================================
    // Using a power: right-click with a sword (or a power star)
    // =====================================================================

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        // Power star: gain that power
        Power starPower = powerFromItem(hand);
        if (starPower != null) {
            e.setCancelled(true);
            useStar(p, hand, starPower);
            return;
        }

        // Reroll token: get a new random power
        if (isRerollToken(hand)) {
            e.setCancelled(true);
            useRerollToken(p, hand);
            return;
        }

        Power power = powerOf(p);
        if (power == null || !isEnabled(power)) return;
        if (!canActivate(hand)) return;
        if (duel.blocksPowers(p)) {
            bar(p, "Powers are disabled in duels.", NamedTextColor.GRAY);
            return;
        }

        if (p.isSneaking()) { // crouch + right-click the Activator = the quick switch (Bedrock-friendly)
            switchPower(p);
            return;
        }

        if (onCooldown(p)) return;
        Long before = cooldowns.get(p.getUniqueId());
        activate(p, power);
        if (!Objects.equals(before, cooldowns.get(p.getUniqueId()))) unbound.addProgress(p, "USE_POWER", 1);
    }

    private boolean onCooldown(Player p) {
        long left = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        if (left <= 0) return false;
        bar(p, "Cooldown: " + ((left + 999) / 1000) + "s", NamedTextColor.RED);
        return true;
    }

    private void startCooldown(Player p, Power power) {
        int seconds = i(power.key() + ".cooldown", 15);
        long ms = (long) (seconds * 1000L * unbound.cooldownMult(p)); // upgrades shorten cooldowns
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    private void activate(Player p, Power power) {
        String k = power.key();
        switch (power) {

            case FLAME -> {
                startCooldown(p, power);
                Vector dir = p.getEyeLocation().getDirection();
                Fireball fb = p.launchProjectile(Fireball.class, dir.clone().multiply(1.2));
                fb.setDirection(dir);
                fb.setYield((float) d("flame.fireball-yield", 2.0));
                fb.setShooter(p);
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
            }

            case SPEED -> {
                startCooldown(p, power);
                int ticks = (int) (i("speed.super-seconds", 8) * 20 * um(p));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks,
                        Math.max(0, i("speed.super-level", 3) - 1)));
                puff(p.getLocation().add(0, 1, 0), Particle.CLOUD, 25);
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
            }

            case THOR -> {
                LivingEntity t = nearestEnemy(p, d("thor.range", 30) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                t.getWorld().strikeLightning(t.getLocation());
            }

            case SHADOW -> {
                LivingEntity t = nearestEnemy(p, d("shadow.range", 25) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                Location tl = t.getLocation();
                Location dest = tl.clone().subtract(flat(tl).multiply(1.5));
                dest.setDirection(tl.toVector().subtract(dest.toVector()));
                puff(p.getLocation().add(0, 1, 0), Particle.SMOKE, 25);
                p.teleport(dest);
                puff(dest.clone().add(0, 1, 0), Particle.SMOKE, 25);
                t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
            }

            case HUNTER -> {
                LivingEntity t = nearestEnemy(p, d("hunter.range", 25) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                if (pull.lengthSquared() > 1.0E-6) pull.normalize();
                pull.multiply(d("hunter.pull-strength", 1.6));
                pull.setY(d("hunter.pull-up", 0.25));
                t.setVelocity(pull);
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
            }

            case STEALER -> {
                LivingEntity t = nearestEnemy(p, d("stealer.range", 20) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                int ticks = i("stealer.duration", 15) * 20;
                t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0));
                puff(t.getLocation().add(0, 1, 0), Particle.WITCH, 30);
                p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
            }

            case FEATHER -> {
                startCooldown(p, power);
                Vector v = p.getVelocity();
                v.setY(d("feather.launch-strength", 2.3));
                p.setVelocity(v);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0));
                puff(p.getLocation(), Particle.CLOUD, 25);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.2f);
            }

            case HEALER -> {
                startCooldown(p, power);
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(max != null ? max.getValue() : 20.0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
                puff(p.getLocation().add(0, 1, 0), Particle.HEART, 12);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            }

            case AQUA -> {
                startCooldown(p, power);
                final double speed = d("aqua.speed", 0.8);
                final int ticks = i("aqua.duration-ticks", 40);
                new BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || t++ >= ticks) {
                            cancel();
                            return;
                        }
                        p.setVelocity(p.getLocation().getDirection().multiply(speed));
                        puff(p.getLocation().add(0, 1, 0), Particle.SPLASH, 6);
                    }
                }.runTaskTimer(this, 0L, 1L);
                p.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_JUMP, 1f, 1f);
            }

            case TANK -> {
                startCooldown(p, power);
                int ticks = i("tank.stun-seconds", 3) * 20;
                double radius = d("tank.range", 8);
                for (LivingEntity other : enemiesNear(p, radius)) {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10));
                    other.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128));
                    if (other instanceof Player op) op.sendActionBar(Component.text("Stunned!", NamedTextColor.RED));
                }
                puff(p.getLocation().add(0, 0.5, 0), Particle.CRIT, 40);
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
            }

            case GHOST -> haunt(p, power,
                    d(k + ".range", 20) * um(p),
                    i(k + ".wither-seconds", 3),
                    i(k + ".wither-amplifier", 0),
                    i(k + ".hearts-stolen", 4),
                    i(k + ".invisibility-duration-seconds", 10));

            case VAMPIRE -> vampire(p);

            case TELEPORTER -> teleporter(p);

            case VOID -> voidStrike(p);

            // ------------------------------------------------ Halloween powers (v8)

            case PUMPKIN -> { // Pumpkin King: a huge exploding pumpkin bomb
                startCooldown(p, power);
                Vector dir = p.getEyeLocation().getDirection();
                Fireball fb = p.launchProjectile(Fireball.class, dir.clone().multiply(1.1));
                fb.setDirection(dir);
                fb.setYield((float) d("pumpkin.fireball-yield", 3.5));
                fb.setShooter(p);
                puff(p.getLocation().add(0, 1, 0), Particle.FLAME, 25);
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
            }

            case WITCH -> { // Witch: poison and slow every enemy around you, heal yourself a little
                startCooldown(p, power);
                double radius = d("witch.range", 10) * um(p);
                int secs = i("witch.poison-seconds", 6);
                for (LivingEntity t : enemiesNear(p, radius)) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, secs * 20, 1));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, 0));
                    puff(t.getLocation().add(0, 1, 0), Particle.WITCH, 18);
                    if (t instanceof Player tp) bar(tp, "The witch cursed you with poison!", NamedTextColor.DARK_PURPLE);
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 6 * 20, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 0.8f);
            }

            case SKELETON -> { // Skeleton: a volley of arrows at the nearest enemy (or straight ahead)
                LivingEntity t = nearestEnemy(p, d("skeleton.range", 30) * um(p));
                startCooldown(p, power);
                Location eye = p.getEyeLocation();
                Vector dir = t != null
                        ? t.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector()).normalize()
                        : eye.getDirection();
                int arrows = i("skeleton.arrows", 8);
                for (int n = 0; n < arrows; n++) {
                    Arrow a = p.getWorld().spawnArrow(eye.clone().add(dir.clone().multiply(1.2)), dir, 2.4f, 7f);
                    a.setShooter(p);
                    a.setDamage(d("skeleton.arrow-damage", 4.0));
                    a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1f, 0.8f);
            }

            case SPIDER -> { // Spider: trap the nearest enemy in webs and poison them
                LivingEntity t = nearestEnemy(p, d("spider.range", 20) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, i("spider.poison-seconds", 5) * 20, 1));
                Map<Location, org.bukkit.block.data.BlockData> old = new HashMap<>();
                for (int dy = 0; dy <= 1; dy++) {
                    org.bukkit.block.Block b = t.getLocation().clone().add(0, dy, 0).getBlock();
                    if (b.getType() == Material.AIR || b.getType() == Material.COBWEB) {
                        old.putIfAbsent(b.getLocation(), b.getBlockData());
                        b.setType(Material.COBWEB, false);
                    }
                }
                int webTicks = i("spider.web-seconds", 5) * 20;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    for (Map.Entry<Location, org.bukkit.block.data.BlockData> en : old.entrySet()) {
                        if (en.getKey().getBlock().getType() == Material.COBWEB) {
                            en.getKey().getBlock().setBlockData(en.getValue(), false);
                        }
                    }
                }, webTicks);
                if (t instanceof Player tp) bar(tp, "You are stuck in a spider web!", NamedTextColor.DARK_GRAY);
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.8f);
            }

            case SCARECROW -> { // Scarecrow: terrify everyone around you - nausea, weakness, thrown back
                startCooldown(p, power);
                double radius = d("scarecrow.range", 9) * um(p);
                int secs = i("scarecrow.fear-seconds", 6);
                for (LivingEntity t : enemiesNear(p, radius)) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, secs * 20, 0));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, secs * 20, 0));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, 0));
                    Vector away = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 1.0E-6) t.setVelocity(away.normalize().multiply(1.1).setY(0.35));
                    if (t instanceof Player tp) bar(tp, "The scarecrow terrifies you!", NamedTextColor.YELLOW);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.SMOKE, 35);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.2f, 0.6f);
            }

            case REAPER -> { // Reaper: blink to the nearest enemy, strike them and take their strength
                LivingEntity t = nearestEnemy(p, d("reaper.range", 25) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                Location tl = t.getLocation();
                Location dest = tl.clone().subtract(flat(tl).multiply(1.5));
                dest.setDirection(tl.toVector().subtract(dest.toVector()));
                puff(p.getLocation().add(0, 1, 0), Particle.SOUL, 25);
                p.teleport(dest);
                puff(dest.clone().add(0, 1, 0), Particle.SOUL, 25);
                t.damage(d("reaper.damage-hearts", 5) * 2.0 * um(p), p);
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : 20.0;
                p.setHealth(Math.min(cap, p.getHealth() + d("reaper.heal-hearts", 2.5) * 2.0));
                if (t instanceof Player tp) bar(tp, "The reaper struck you!", NamedTextColor.DARK_RED);
                p.playSound(dest, Sound.ENTITY_WITHER_SHOOT, 0.9f, 0.8f);
            }

            case CANDY -> { // Candy Rush: sugar makes you fast and strong, and mends you a little
                startCooldown(p, power);
                int ticks = i("candy.buff-seconds", 10) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : 20.0;
                p.setHealth(Math.min(cap, p.getHealth() + d("candy.heal-hearts", 4) * 2.0));
                ItemStack sweets = new ItemStack(Material.COOKIE, 3);
                give(p, sweets); // trick-or-treat candy
                puff(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 25);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1.2f);
            }

            case PHANTOM -> { // Phantom: dive through the air; your screech hurts and blinds nearby enemies
                startCooldown(p, power);
                Vector dash = p.getEyeLocation().getDirection().multiply(d("phantom.dash-strength", 1.8));
                dash.setY(Math.max(0.45, dash.getY()));
                p.setVelocity(dash);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));
                double radius = d("phantom.range", 7) * um(p);
                for (LivingEntity t : enemiesNear(p, radius)) {
                    t.damage(d("phantom.damage-hearts", 2) * 2.0 * um(p), p);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    if (t instanceof Player tp) bar(tp, "A phantom screeches at you!", NamedTextColor.AQUA);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.CLOUD, 25);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.9f);
            }

            case WEREWOLF -> { // Werewolf: the howl - a burst of speed and strength, enemies cower
                startCooldown(p, power);
                int ticks = i("werewolf.buff-seconds", 12) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1));
                for (LivingEntity t : enemiesNear(p, d("werewolf.range", 10) * um(p))) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
                    if (t instanceof Player tp) bar(tp, "The werewolf howls!", NamedTextColor.GRAY);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.CRIT, 25);
                p.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.4f, 0.6f);
            }

            case CURSE -> { // Curse: doom the nearest enemy - wither, weak and slow - and shield yourself
                LivingEntity t = nearestEnemy(p, d("curse.range", 22) * um(p));
                if (t == null) { bar(p, "No one in range to curse.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                int ticks = i("curse.curse-seconds", 8) * 20;
                t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, ticks, 1));
                t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 1));
                puff(t.getLocation().add(0, 1, 0), Particle.ENCHANT, 35);
                if (t instanceof Player tp) bar(tp, "You have been cursed!", NamedTextColor.DARK_PURPLE);
                p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.6f);
            }

            // ------------------------------------------------ Galactic powers (v10)

            case SUPERNOVA -> { // Supernova: three fireballs at the nearest enemy (or where you look)
                startCooldown(p, power);
                LivingEntity t = nearestEnemy(p, d("supernova.range", 40) * um(p));
                Location eye = p.getEyeLocation();
                Vector base = t != null
                        ? t.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector()).normalize()
                        : eye.getDirection();
                int n = i("supernova.fireballs", 3);
                for (int j = 0; j < n; j++) {
                    Vector dir = base.clone();
                    if (j > 0) { // spread the extra fireballs a little
                        dir.add(new Vector((random.nextDouble() - 0.5) * 0.16, (random.nextDouble() - 0.5) * 0.1,
                                (random.nextDouble() - 0.5) * 0.16)).normalize();
                    }
                    Fireball fb = p.launchProjectile(Fireball.class, dir.clone().multiply(1.3));
                    fb.setDirection(dir);
                    fb.setYield((float) d("supernova.fireball-yield", 1.8));
                    fb.setShooter(p);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.FLAME, 30);
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
            }

            case EARTH -> earthSuper(p);

            case WIND -> windSuper(p);

            case FROST -> frostSuper(p);

            case STARLIGHT -> { // Starlight: a beam of starlight on the nearest enemy
                LivingEntity t = nearestEnemy(p, d("starlight.range", 35) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                beamParticles(p.getEyeLocation(), t.getLocation().add(0, 1, 0), Particle.END_ROD);
                t.damage(d("starlight.damage-hearts", 3.5) * 2.0 * um(p), p);
                t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
                if (t instanceof Player tp) bar(tp, "Starlight burns you!", NamedTextColor.YELLOW);
            }

            case COMET -> { // Comet: a freezing dash - enemies you pass get frostbitten
                startCooldown(p, power);
                Vector dash = p.getEyeLocation().getDirection().multiply(d("comet.dash-strength", 2.4));
                dash.setY(Math.max(0.35, dash.getY()));
                p.setVelocity(dash);
                final Set<UUID> hit = new HashSet<>();
                new BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || t++ >= 12) {
                            cancel();
                            return;
                        }
                        puff(p.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 8);
                        for (LivingEntity e : enemiesNear(p, 2.6)) {
                            if (!hit.add(e.getUniqueId())) continue;
                            e.damage(d("comet.damage-hearts", 2) * 2.0 * um(p), p);
                            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                            e.setFreezeTicks(Math.min(e.getMaxFreezeTicks(), e.getFreezeTicks() + 100));
                        }
                    }
                }.runTaskTimer(this, 0L, 1L);
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.7f);
            }

            case METEOR -> { // Meteor: a meteor falls out of the sky onto the nearest enemy
                LivingEntity t = nearestEnemy(p, d("meteor.range", 45) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                final Location target = t.getLocation().clone();
                target.getWorld().spawnParticle(Particle.FLAME, target.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.3f);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Location from = target.clone().add(0, 26, 0);
                    LargeFireball fb = target.getWorld().spawn(from, LargeFireball.class);
                    fb.setDirection(new Vector(0, -1, 0));
                    fb.setVelocity(new Vector(0, -2.2, 0));
                    fb.setYield((float) d("meteor.yield", 3.0));
                    fb.setShooter(p);
                }, 12L);
            }

            case LUNAR -> { // Lunar: moon gravity - you bounce high, enemies float
                startCooldown(p, power);
                int ticks = i("lunar.duration-seconds", 15) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0));
                for (LivingEntity t : enemiesNear(p, d("lunar.range", 12) * um(p))) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0));
                    if (t instanceof Player tp) bar(tp, "Moon gravity grabs you!", NamedTextColor.LIGHT_PURPLE);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.END_ROD, 30);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            }

            case PULSAR -> { // Pulsar: three shockwave pulses that shove and hurt everyone close
                startCooldown(p, power);
                for (int pulse = 0; pulse < 3; pulse++) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!p.isOnline() || p.isDead()) return;
                        double radius = d("pulsar.range", 9) * um(p);
                        for (LivingEntity t : enemiesNear(p, radius)) {
                            t.damage(d("pulsar.damage-hearts", 1.5) * 2.0 * um(p), p);
                            Vector away = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                            if (away.lengthSquared() > 1.0E-6) t.setVelocity(away.normalize().multiply(1.3).setY(0.4));
                        }
                        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0),
                                40, radius * 0.4, 0.5, radius * 0.4, 0.1);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
                    }, pulse * 20L);
                }
            }

            case NEBULA -> { // Nebula: vanish into a star cloud that blinds everyone near you
                startCooldown(p, power);
                int ticks = i("nebula.duration-seconds", 10) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
                for (LivingEntity t : enemiesNear(p, d("nebula.range", 8) * um(p))) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    if (t instanceof Player tp) bar(tp, "You are lost in the nebula!", NamedTextColor.DARK_PURPLE);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.DRAGON_BREATH, 45);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
            }

            case SOLAR_FLARE -> { // Solar Flare: blind and burn every enemy around you
                startCooldown(p, power);
                double radius = d("solar-flare.range", 15) * um(p);
                int secs = i("solar-flare.blind-seconds", 5);
                for (LivingEntity t : enemiesNear(p, radius)) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, secs * 20, 0));
                    t.setFireTicks(Math.max(t.getFireTicks(), secs * 20));
                    t.damage(d("solar-flare.damage-hearts", 2) * 2.0 * um(p), p);
                    if (t instanceof Player tp) bar(tp, "A solar flare blinds you!", NamedTextColor.GOLD);
                }
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 60, radius * 0.4, 1, radius * 0.4, 0.1);
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
            }

            case ECLIPSE -> { // Eclipse: darkness for your enemies, power for you
                startCooldown(p, power);
                int ticks = i("eclipse.duration-seconds", 10) * 20;
                for (LivingEntity t : enemiesNear(p, d("eclipse.range", 20) * um(p))) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
                    if (t instanceof Player tp) bar(tp, "The eclipse swallows the light!", NamedTextColor.DARK_GRAY);
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
                puff(p.getLocation().add(0, 1, 0), Particle.SQUID_INK, 45);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.7f);
            }

            case ASTEROID -> { // Asteroid: a giant rock crashes down on the nearest enemy
                LivingEntity t = nearestEnemy(p, d("asteroid.range", 35) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                final Location target = t.getLocation().clone();
                target.getWorld().spawnParticle(Particle.SMOKE, target.clone().add(0, 0.3, 0), 25, 1.5, 0.2, 1.5, 0.02);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9f, 1.1f);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    double radius = d("asteroid.impact-radius", 5);
                    target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
                    target.getWorld().spawnParticle(Particle.LAVA, target.clone().add(0, 0.5, 0), 30, 1.5, 0.5, 1.5, 0.1);
                    target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
                    for (LivingEntity e : target.getWorld().getNearbyLivingEntities(target, radius)) {
                        if (isEnemy(p, e)) e.damage(d("asteroid.damage-hearts", 6) * 2.0 * um(p), p);
                    }
                }, 16L);
            }

            case ZERO_GRAVITY -> { // Zero Gravity: every enemy floats up... then crashes down
                startCooldown(p, power);
                double radius = d("zero-gravity.range", 14) * um(p);
                int secs = i("zero-gravity.float-seconds", 6);
                List<LivingEntity> targets = new ArrayList<>(enemiesNear(p, radius));
                for (LivingEntity t : targets) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, secs * 20, 0));
                    if (t instanceof Player tp) bar(tp, "Gravity is gone!", NamedTextColor.LIGHT_PURPLE);
                }
                puff(p.getLocation().add(0, 1, 0), Particle.END_ROD, 40);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    for (LivingEntity t : targets) {
                        if (!t.isValid() || t.isDead()) continue;
                        t.removePotionEffect(PotionEffectType.LEVITATION);
                        t.setVelocity(new Vector(0, -2.5, 0));
                        t.damage(d("zero-gravity.slam-hearts", 3) * 2.0 * um(p), p);
                        t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 15, 0.4, 0.2, 0.4, 0.05);
                    }
                }, secs * 20L + 10L);
            }

            case WORMHOLE -> { // Wormhole: swap places with the nearest enemy
                LivingEntity t = nearestEnemy(p, d("wormhole.range", 40) * um(p));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                Location mine = p.getLocation().clone();
                Location theirs = t.getLocation().clone();
                puff(mine.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 35);
                puff(theirs.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 35);
                p.teleport(theirs);
                t.teleport(mine);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
                if (t instanceof Player tp) bar(tp, "A wormhole swapped you!", NamedTextColor.DARK_BLUE);
            }

            case QUASAR -> quasarBeam(p);

            case BLACK_HOLE -> blackHole(p);

            case ANTIMATTER -> antimatter(p);

            case ROCKET -> { // Rocket: blast off!
                startCooldown(p, power);
                Vector v = p.getLocation().getDirection().multiply(0.5);
                v.setY(d("rocket.launch-strength", 2.6));
                p.setVelocity(v);
                int ticks = i("rocket.fall-seconds", 10) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, ticks, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 1f);
                new BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || t++ >= 30) {
                            cancel();
                            return;
                        }
                        puff(p.getLocation(), Particle.FIREWORK, 4);
                    }
                }.runTaskTimer(this, 0L, 2L);
            }

            case HACKER -> hackerSuper(p);
        }
    }

    /** A line of particles from A to B (starlight beams, quasar beams). */
    private void beamParticles(Location from, Location to, Particle particle) {
        Vector step = to.toVector().subtract(from.toVector());
        int pts = Math.max(2, (int) (step.length() * 2));
        step.multiply(1.0 / pts);
        Location cur = from.clone();
        for (int k = 0; k <= pts; k++) {
            cur.getWorld().spawnParticle(particle, cur, 2, 0.05, 0.05, 0.05, 0.01);
            cur.add(step);
        }
    }

    // =====================================================================
    // Earth (v10): trap your enemy in a stone box, gain Strength III, and
    // send minions that hunt only them. Lasts a minute; cooldown 5 minutes.
    // =====================================================================

    private void earthSuper(Player p) {
        LivingEntity t = nearestEnemy(p, d("earth.range", 30) * um(p));
        if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
        startCooldown(p, Power.EARTH);
        final int secs = i("earth.duration-seconds", 60);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 2)); // Strength III
        bar(p, "The earth rises for you!", NamedTextColor.DARK_GREEN);
        if (t instanceof Player tp) bar(tp, "The earth traps you!", NamedTextColor.DARK_GREEN);

        // the box: a hollow stone shell around the enemy, every block remembered and restored
        final Map<Location, org.bukkit.block.data.BlockData> old = new HashMap<>();
        Location c = t.getLocation().getBlock().getLocation();
        Material mat = Material.matchMaterial(getConfig().getString("earth.box-material", "STONE"));
        final Material boxMat = mat == null ? Material.STONE : mat;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                    if (!shell) continue;
                    org.bukkit.block.Block b = c.clone().add(dx, dy, dz).getBlock();
                    if (b.getType() == Material.BEDROCK || b.getType() == boxMat) continue;
                    old.putIfAbsent(b.getLocation(), b.getBlockData());
                    b.setType(boxMat, false);
                }
            }
        }
        c.getWorld().playSound(c, Sound.BLOCK_STONE_PLACE, 1.2f, 0.7f);

        // the minions: earth zombies that hunt only this enemy
        final List<org.bukkit.entity.Zombie> minions = new ArrayList<>();
        int count = i("earth.minions", 4);
        for (int k = 0; k < count; k++) {
            Location at = c.clone().add(3.5 + random.nextDouble() * 2, 1, (random.nextInt(9) - 4));
            org.bukkit.entity.Zombie z = c.getWorld().spawn(at, org.bukkit.entity.Zombie.class, mob -> {
                mob.setShouldBurnInDay(false);
                mob.customName(Component.text("Earth Minion", NamedTextColor.DARK_GREEN));
                mob.setCustomNameVisible(true);
                mob.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                mob.getEquipment().setItemInMainHandDropChance(0f);
                mob.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                mob.getEquipment().setHelmetDropChance(0f);
                mob.setTarget(t);
            });
            minions.add(z);
        }

        final LivingEntity target = t;
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 10;
                boolean done = ticks >= secs * 20 || !target.isValid() || target.isDead()
                        || target.getWorld() != c.getWorld()
                        || target.getLocation().distanceSquared(c) > 40 * 40;
                if (done) {
                    for (org.bukkit.entity.Zombie z : minions) if (z.isValid()) z.remove();
                    for (Map.Entry<Location, org.bukkit.block.data.BlockData> en : old.entrySet()) {
                        if (en.getKey().getBlock().getType() == boxMat) en.getKey().getBlock().setBlockData(en.getValue(), false);
                    }
                    cancel();
                    return;
                }
                for (org.bukkit.entity.Zombie z : minions) if (z.isValid()) z.setTarget(target);
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    // =====================================================================
    // Wind (v10): charge up in the air for 2 seconds, then slam down and
    // blast everyone in 15 blocks for 15 hearts. Only ONE exists.
    // =====================================================================

    private void windSuper(Player p) {
        startCooldown(p, Power.WIND);
        bar(p, "The wind gathers...", NamedTextColor.WHITE);
        new BukkitRunnable() {
            int t = 0;
            boolean slammed = false;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                if (t <= 40) { // charging: hover and rise for 2 seconds
                    Vector v = p.getVelocity();
                    v.setY(0.55);
                    p.setVelocity(v);
                    p.setFallDistance(0f);
                    puff(p.getLocation().add(0, 0.5, 0), Particle.CLOUD, 6);
                    if (t % 10 == 0) p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 0.6f + t / 40f);
                    return;
                }
                if (!slammed) { // slam!
                    slammed = true;
                    p.setVelocity(new Vector(0, -3.2, 0));
                    p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 1.4f, 0.6f);
                    return;
                }
                if (p.isOnGround() || t > 70) { // impact
                    double radius = d("wind.radius", 15) * um(p);
                    for (LivingEntity e : enemiesNear(p, radius)) {
                        e.damage(d("wind.damage-hearts", 15) * 2.0, p);
                        Vector away = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                        if (away.lengthSquared() > 1.0E-6) e.setVelocity(away.normalize().multiply(1.6).setY(0.7));
                        if (e instanceof Player ep) bar(ep, "The Wind slammed you!", NamedTextColor.WHITE);
                    }
                    Location at = p.getLocation();
                    at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 0.5, 0), 2, 0.5, 0.3, 0.5, 0);
                    at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.5, 0), 60, radius * 0.4, 0.5, radius * 0.4, 0.25);
                    at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);
                    p.setFallDistance(0f);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // =====================================================================
    // Frost (v10): freeze every enemy in 30 blocks for 5 seconds, then
    // frost damage keeps biting them for 20 seconds.
    // =====================================================================

    private void frostSuper(Player p) {
        startCooldown(p, Power.FROST);
        double radius = d("frost.radius", 30) * um(p);
        int freezeSecs = i("frost.freeze-seconds", 5);
        int dotSecs = i("frost.damage-seconds", 20);
        List<LivingEntity> targets = new ArrayList<>(enemiesNear(p, radius));
        for (LivingEntity t : targets) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeSecs * 20, 10));
            t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, freezeSecs * 20, 128));
            t.setFreezeTicks(t.getMaxFreezeTicks());
            puff(t.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 25);
            if (t instanceof Player tp) bar(tp, "You are frozen solid!", NamedTextColor.AQUA);
        }
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 80, radius * 0.35, 1, radius * 0.35, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.3f, 0.8f);
        if (targets.isEmpty()) return;
        new BukkitRunnable() {
            int pulses = 0;

            @Override
            public void run() { // frost damage every 2 seconds for the whole duration
                pulses++;
                if (pulses * 2 > dotSecs) {
                    cancel();
                    return;
                }
                for (LivingEntity t : targets) {
                    if (!t.isValid() || t.isDead()) continue;
                    t.damage(d("frost.damage-per-pulse", 1.0) * um(p), p);
                    t.setFreezeTicks(Math.max(t.getFreezeTicks(), t.getMaxFreezeTicks() / 2));
                    puff(t.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 6);
                }
            }
        }.runTaskTimer(this, 40L, 40L);
    }

    // =====================================================================
    // Quasar (v10): a huge beam of starlight in the direction you look
    // =====================================================================

    private void quasarBeam(Player p) {
        startCooldown(p, Power.QUASAR);
        bar(p, "Charging the quasar...", NamedTextColor.LIGHT_PURPLE);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || p.isDead()) return;
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            double range = d("quasar.range", 30);
            beamParticles(eye.clone().add(dir), eye.clone().add(dir.clone().multiply(range)), Particle.GLOW);
            beamParticles(eye.clone().add(dir), eye.clone().add(dir.clone().multiply(range)), Particle.END_ROD);
            for (LivingEntity t : enemiesNear(p, range + 4)) {
                // distance from the enemy to the beam line
                Vector to = t.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                double along = to.dot(dir);
                if (along < 0 || along > range) continue;
                Vector closest = dir.clone().multiply(along);
                if (to.subtract(closest).length() <= d("quasar.width", 2.2)) {
                    t.damage(d("quasar.damage-hearts", 8) * 2.0 * um(p), p);
                    if (t instanceof Player tp) bar(tp, "The quasar beam hits you!", NamedTextColor.LIGHT_PURPLE);
                }
            }
            p.getWorld().playSound(eye, Sound.ENTITY_WITHER_SHOOT, 1.1f, 1.4f);
        }, 20L);
    }

    // =====================================================================
    // Black Hole (v10): a hungry void that drags every enemy in, then bursts
    // =====================================================================

    private void blackHole(Player p) {
        LivingEntity t = nearestEnemy(p, d("black-hole.range", 30) * um(p));
        final Location center;
        if (t != null) center = t.getLocation().add(0, 1.5, 0);
        else center = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(14)).add(0, 0.5, 0);
        startCooldown(p, Power.BLACK_HOLE);
        final int secs = i("black-hole.duration-seconds", 6);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.6f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 2;
                center.getWorld().spawnParticle(Particle.SQUID_INK, center, 14, 0.4, 0.4, 0.4, 0.05);
                center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 10, 1.2, 1.2, 1.2, 0.4);
                for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, d("black-hole.pull-radius", 12))) {
                    if (!isEnemy(p, e)) continue;
                    Vector pull = center.toVector().subtract(e.getLocation().toVector());
                    double dist = Math.max(1.0, pull.length());
                    e.setVelocity(pull.normalize().multiply(Math.min(1.1, 14.0 / dist)).setY(0.12));
                }
                if (ticks >= secs * 20) { // the burst
                    for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, 8)) {
                        if (!isEnemy(p, e)) continue;
                        e.damage(d("black-hole.burst-hearts", 4) * 2.0 * um(p), p);
                        Vector away = e.getLocation().toVector().subtract(center.toVector());
                        away.setY(0.5);
                        if (away.lengthSquared() > 1.0E-6) e.setVelocity(away.normalize().multiply(1.5));
                    }
                    center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.4, 0.4, 0.4, 0);
                    center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // =====================================================================
    // Antimatter (v10): charge up, then a huge explosion centred on you
    // (it never hurts you, and it never breaks blocks)
    // =====================================================================

    private void antimatter(Player p) {
        startCooldown(p, Power.ANTIMATTER);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 70, 4));
        bar(p, "Antimatter charging...", NamedTextColor.DARK_RED);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                puff(p.getLocation().add(0, 1, 0), Particle.SMOKE, 4);
                if (t % 10 == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f + t / 20f);
                if (t < 40) return;
                double radius = d("antimatter.radius", 12) * um(p);
                for (LivingEntity e : enemiesNear(p, radius)) {
                    e.damage(d("antimatter.damage-hearts", 10) * 2.0 * um(p), p);
                    Vector away = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 1.0E-6) e.setVelocity(away.normalize().multiply(2.0).setY(0.8));
                    if (e instanceof Player ep) bar(ep, "ANTIMATTER!", NamedTextColor.DARK_RED);
                }
                Location at = p.getLocation();
                at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 1, 0), 5, 1, 0.6, 1, 0);
                at.getWorld().spawnParticle(Particle.FIREWORK, at.clone().add(0, 1, 0), 60, radius * 0.4, 1, radius * 0.4, 0.2);
                at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                at.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);
                cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // =====================================================================
    // Ghost / Jack-o-Lantern: invisible + wither + steal hearts
    // =====================================================================

    private void haunt(Player ghost, Power power, double range, int witherSec, int witherAmp,
                       int hearts, int invisSec) {
        LivingEntity target = nearestEnemy(ghost, range);
        if (target == null) {
            bar(ghost, "No one in range to haunt.", NamedTextColor.GRAY);
            return; // no cooldown spent
        }
        startCooldown(ghost, power);

        ghost.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisSec * 20, 0, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherSec * 20, witherAmp));
        stealHearts(ghost, target, power, hearts);

        puff(ghost.getLocation().add(0, 1, 0), Particle.SOUL, 30);
        puff(target.getLocation().add(0, 1, 0), Particle.SOUL, 30);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1f, 0.5f);
        ghost.playSound(ghost.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1f, 0.6f);
        if (target instanceof Player hp) bar(hp, "You are being haunted...", NamedTextColor.DARK_PURPLE);
    }

    private void stealHearts(Player ghost, LivingEntity target, Power power, int hearts) {
        double want = hearts * 2.0;
        double minLeft = i("ghost.min-hearts-left", 1) * 2.0;

        AttributeInstance tMax = target.getAttribute(Attribute.MAX_HEALTH);
        AttributeInstance gMax = ghost.getAttribute(Attribute.MAX_HEALTH);
        if (tMax == null || gMax == null) return;

        NamespacedKey drainKey = key(DRAIN + ghost.getUniqueId());
        NamespacedKey gainKey = key(GAIN + target.getUniqueId());

        removeModifier(tMax, drainKey);
        removeModifier(gMax, gainKey);

        double stolen = Math.min(want, tMax.getValue() - minLeft);
        double cap = i("ghost.max-extra-hearts", 10) * 2.0;
        if (cap > 0) stolen = Math.min(stolen, cap - gainedHearts(gMax));
        if (stolen <= 0) return;

        tMax.addModifier(new AttributeModifier(drainKey, -stolen, AttributeModifier.Operation.ADD_NUMBER));
        gMax.addModifier(new AttributeModifier(gainKey, stolen, AttributeModifier.Operation.ADD_NUMBER));

        if (target.getHealth() > tMax.getValue()) target.setHealth(tMax.getValue());
        ghost.setHealth(Math.min(gMax.getValue(), ghost.getHealth() + stolen));

        drained.computeIfAbsent(ghost.getUniqueId(), x -> new HashSet<>()).add(target.getUniqueId());

        int seconds = i(power.key() + ".steal-duration", 300);
        if (seconds > 0) {
            UUID gid = ghost.getUniqueId(), tid = target.getUniqueId();
            Bukkit.getScheduler().runTaskLater(this, () -> returnHearts(gid, tid), seconds * 20L);
        }
    }

    private void returnHearts(UUID ghostId, UUID targetId) {
        Entity g = Bukkit.getEntity(ghostId);
        Entity t = Bukkit.getEntity(targetId);
        if (t instanceof LivingEntity tl) removeModifier(tl.getAttribute(Attribute.MAX_HEALTH), key(DRAIN + ghostId));
        if (g instanceof LivingEntity gl) removeModifier(gl.getAttribute(Attribute.MAX_HEALTH), key(GAIN + targetId));
        Set<UUID> set = drained.get(ghostId);
        if (set != null) set.remove(targetId);
    }

    private double gainedHearts(AttributeInstance inst) {
        double sum = 0;
        String ns = getName().toLowerCase(Locale.ROOT);
        for (AttributeModifier m : inst.getModifiers()) {
            NamespacedKey k = m.getKey();
            if (k.getNamespace().equals(ns) && k.getKey().startsWith(GAIN)) sum += m.getAmount();
        }
        return sum;
    }

    private void removeModifier(AttributeInstance inst, NamespacedKey k) {
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(k)) inst.removeModifier(m);
        }
    }

    private void clearHeartSteals(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        String ns = getName().toLowerCase(Locale.ROOT);
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            NamespacedKey k = m.getKey();
            if (k.getNamespace().equals(ns)
                    && (k.getKey().startsWith(DRAIN) || k.getKey().startsWith(GAIN))) {
                inst.removeModifier(m);
            }
        }
        if (p.getHealth() > inst.getValue()) p.setHealth(inst.getValue());
    }

    // =====================================================================
    // Vampire: latch onto the enemy, drain them, fake blood
    // =====================================================================

    private void vampire(Player v) {
        LivingEntity target = nearestEnemy(v, d("vampire.range", 20) * um(v));
        if (target == null) {
            bar(v, "No one in range to leech.", NamedTextColor.GRAY);
            return; // no cooldown spent
        }
        startCooldown(v, Power.VAMPIRE);

        int witherTicks = i("vampire.wither-seconds", 4) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, i("vampire.wither-amplifier", 0)));

        final double amount = d("vampire.leech-per-pulse", 1.0) * um(v);
        final int totalTicks = i("vampire.leech-seconds", 5) * 20;

        v.playSound(v.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.6f);
        if (target instanceof Player vp) bar(vp, "A vampire has latched onto you!", NamedTextColor.DARK_RED);
        bar(v, "Latched on! Sneak to let go.", NamedTextColor.DARK_RED);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                boolean letGo = t > 10 && v.isSneaking();
                if (t >= totalTicks || letGo || !v.isOnline() || !target.isValid() || v.isDead() || target.isDead()
                        || v.getWorld() != target.getWorld()) {
                    cancel();
                    return;
                }

                // stick to the enemy's back, facing the same way they do
                Location tl = target.getLocation();
                Location at = tl.clone().subtract(flat(tl).multiply(0.6));
                at.setY(tl.getY() + 0.1);
                at.setDirection(tl.getDirection());
                v.teleport(at);
                v.setVelocity(new Vector(0, 0, 0));
                v.setFallDistance(0f);

                bleed(target, 4);
                if (t % 10 == 0) { // two bites per second
                    target.setNoDamageTicks(0);
                    target.damage(amount, v);
                    AttributeInstance max = v.getAttribute(Attribute.MAX_HEALTH);
                    double cap = max != null ? max.getValue() : 20.0;
                    v.setHealth(Math.min(cap, v.getHealth() + amount));
                    bleed(target, 16);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.6f, 0.6f);
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    /** Fake blood: red block-crack and dust particles coming out of the victim. */
    private void bleed(LivingEntity target, int amount) {
        Location loc = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, amount, 0.25, 0.3, 0.25, 0.1,
                Material.REDSTONE_BLOCK.createBlockData());
        loc.getWorld().spawnParticle(Particle.DUST, loc, Math.max(1, amount / 2), 0.25, 0.3, 0.25, 0,
                new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.3f));
    }

    // =====================================================================
    // Void: idle Strength III, super = wither + 7 hearts of damage
    // =====================================================================

    private void voidStrike(Player p) {
        LivingEntity t = nearestEnemy(p, d("void.range", 25) * um(p));
        if (t == null) {
            bar(p, "No one in range.", NamedTextColor.GRAY);
            return; // no cooldown spent
        }
        startCooldown(p, Power.VOID);

        t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, i("void.wither-seconds", 5) * 20,
                i("void.wither-amplifier", 1)));

        // beam of void particles from you to them
        Location from = p.getEyeLocation();
        Vector step = t.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
        int pts = 24;
        step.multiply(1.0 / pts);
        Location cur = from.clone();
        for (int k = 0; k <= pts; k++) {
            cur.getWorld().spawnParticle(Particle.REVERSE_PORTAL, cur, 3, 0.05, 0.05, 0.05, 0.02);
            cur.getWorld().spawnParticle(Particle.SQUID_INK, cur, 1, 0, 0, 0, 0);
            cur.add(step);
        }

        t.damage(d("void.damage-hearts", 7) * 2.0 * um(p), p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.6f);
        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.7f);
        if (t instanceof Player vt) bar(vt, "The void consumes you!", NamedTextColor.DARK_PURPLE);
    }

    // =====================================================================
    // Teleporter: dash to the enemy, freeze them, fling them back, damage them
    // =====================================================================

    private void teleporter(Player tp) {
        LivingEntity target = nearestEnemy(tp, d("teleporter.range", 30) * um(tp));
        if (target == null) {
            bar(tp, "No one in range.", NamedTextColor.GRAY);
            return; // no cooldown spent
        }
        startCooldown(tp, Power.TELEPORTER);

        final double dashSpeed = d("teleporter.dash-speed", 2.0);
        final int freezeTicks = i("teleporter.freeze-ticks", 20);
        tp.playSound(tp.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!tp.isOnline() || tp.isDead() || !target.isValid() || target.isDead()
                        || tp.getWorld() != target.getWorld() || t++ > 40) {
                    cancel();
                    return;
                }
                Vector to = target.getLocation().toVector().subtract(tp.getLocation().toVector());
                if (to.lengthSquared() <= 2.5 * 2.5) {
                    cancel();
                    slam(tp, target, freezeTicks);
                    return;
                }
                tp.setVelocity(to.normalize().multiply(dashSpeed));
                puff(tp.getLocation().add(0, 1, 0), Particle.PORTAL, 8);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void slam(Player tp, LivingEntity target, int freezeTicks) {
        tp.setVelocity(new Vector(0, 0, 0));

        // freeze
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks + 5, 10));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, freezeTicks + 5, 128));
        target.setFreezeTicks(target.getMaxFreezeTicks());
        puff(target.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 40);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        if (target instanceof Player fp) bar(fp, "Frozen!", NamedTextColor.AQUA);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!target.isValid() || target.isDead()) return;
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.removePotionEffect(PotionEffectType.JUMP_BOOST);
            target.setFreezeTicks(0);
            fling(tp, target);
        }, freezeTicks);
    }

    private void fling(Player tp, LivingEntity target) {
        Vector away = target.getLocation().toVector().subtract(tp.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) away = flat(tp.getLocation());
        else away.normalize();
        final Vector dir = away;

        final int ticks = 10;
        final double speed = d("teleporter.fling-distance", 20) / ticks;

        target.damage(d("teleporter.damage-hearts", 4) * 2.0 * um(tp), tp);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || t++ >= ticks) {
                    cancel();
                    return;
                }
                Vector v = dir.clone().multiply(speed);
                v.setY(t <= 3 ? 0.35 : 0.0);
                target.setVelocity(v);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // =====================================================================
    // Boss fight cooldowns (v9): after starting a boss you must wait before
    // fighting that same boss again. Stored per player in players.yml.
    // =====================================================================

    /** Cooldown length in seconds for one boss id (e.g. "notazombiemc", "astronout"). */
    long bossCooldownSeconds(String bossId) {
        long def = getConfig().getLong("boss.cooldown-seconds.default", 300L);
        return Math.max(0L, getConfig().getLong("boss.cooldown-seconds." + bossId, def));
    }

    /** Seconds left on this player's cooldown for that boss (0 = ready to fight). */
    long bossCooldownLeft(Player p, String bossId) {
        long until = data.getLong("players." + p.getUniqueId() + ".boss-cd-" + bossId, 0L);
        long left = (until - System.currentTimeMillis() + 999L) / 1000L;
        return Math.max(0L, left);
    }

    /** Starts the cooldown now (called when a boss fight begins). */
    void startBossCooldown(Player p, String bossId) {
        long secs = bossCooldownSeconds(bossId);
        if (secs <= 0) return;
        data.set("players." + p.getUniqueId() + ".boss-cd-" + bossId, System.currentTimeMillis() + secs * 1000L);
        saveData();
    }

    static String spanShort(long secs) {
        if (secs < 60) return secs + "s";
        long m = secs / 60, s = secs % 60;
        if (m < 60) return s == 0 ? m + "m" : m + "m " + s + "s";
        long h = m / 60;
        return h + "h " + (m % 60) + "m";
    }

    // =====================================================================
    // Rewards used by the tutorial: item:MATERIAL:AMOUNT, power:NAME, reroll:AMOUNT
    // =====================================================================

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    void giveReward(Player p, String reward) {
        String[] parts = reward.split(":");
        if (parts.length < 2) return;
        if (parts[0].equalsIgnoreCase("unlock")) { // unlock:category:id, e.g. unlock:trail:soul
            String lockerKey = reward.substring(reward.indexOf(':') + 1);
            if (profile.unlock(p.getUniqueId(), lockerKey)) {
                p.sendMessage(Component.text("Unlocked in your /locker!", NamedTextColor.GOLD));
            }
            return;
        }
        if (parts[0].equalsIgnoreCase("reroll")) {
            ItemStack token = rerollItem();
            token.setAmount(Math.max(1, Math.min(64, parseInt(parts[1], 1))));
            give(p, token);
            return;
        }
        if (parts[0].equalsIgnoreCase("crate")) { // crate:N = N Power Crates AND N keys
            if (crates != null) crates.giveSet(p, Math.max(1, Math.min(64, parseInt(parts[1], 1))));
            return;
        }
        if (parts[0].equalsIgnoreCase("cratekey")) { // cratekey:N = N Power Crate Keys only
            if (crates != null) crates.giveKeys(p, Math.max(1, Math.min(64, parseInt(parts[1], 1))));
            return;
        }
        if (parts[0].equalsIgnoreCase("galaxy")) { // galaxy:N = N Galaxy Coins (v10)
            if (shops != null) shops.giveGalaxyCoins(p, Math.max(1, Math.min(640, parseInt(parts[1], 1))));
            return;
        }
        if (parts[0].equalsIgnoreCase("power")) {
            Power pw = Power.parse(parts[1]);
            if (pw == null) {
                getLogger().warning("Unknown power in reward: " + reward);
                return;
            }
            give(p, powerItem(pw));
            return;
        }
        Material m = Material.matchMaterial(parts[1]);
        if (m == null || !m.isItem()) {
            getLogger().warning("Unknown item in reward: " + reward);
            return;
        }
        int amount = Math.max(1, parts.length > 2 ? parseInt(parts[2], 1) : 1);
        while (amount > 0) { // respect max stack size
            int n = Math.min(amount, m.getMaxStackSize());
            give(p, new ItemStack(m, n));
            amount -= n;
        }
    }

    // =====================================================================
    // Power Activator: the sword you right-click to use your power
    // =====================================================================

    ItemStack activatorItem() {
        ItemStack it = new ItemStack(Material.IRON_SWORD);
        ItemMeta m = it.getItemMeta();
        m.displayName(line("Power Activator", NamedTextColor.GOLD));
        m.lore(List.of(line("Hold it and right-click to use your power.", NamedTextColor.GRAY),
                line("Soulbound: it comes back when you die.", NamedTextColor.DARK_GRAY)));
        m.setUnbreakable(true);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(activatorKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    boolean isActivator(ItemStack item) {
        return item != null && item.getType() == Material.IRON_SWORD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(activatorKey, PersistentDataType.STRING);
    }

    private boolean canActivate(ItemStack hand) {
        return getConfig().getBoolean("activator.required", true) ? isActivator(hand) : isSword(hand);
    }

    private boolean hasActivator(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isActivator(it)) return true;
        }
        return false;
    }

    /** Players who joined before the update get their sword once. */
    private void giveActivatorOnce(Player p) {
        String path = "players." + p.getUniqueId() + ".activator-given";
        if (data.getBoolean(path, false)) return;
        data.set(path, true);
        saveData();
        if (!hasActivator(p)) give(p, activatorItem());
        p.sendMessage(Component.text("You got a Power Activator sword! Hold it and right-click to use your power.",
                NamedTextColor.GOLD));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isDupedSword(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            bar(e.getPlayer(), "The duped sword can't be dropped - it vanishes on its own.", NamedTextColor.GRAY);
            return;
        }
        if (isActivator(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            bar(e.getPlayer(), "The Power Activator is soulbound.", NamedTextColor.GRAY);
        }
    }

    private double um(Player p) {
        return unbound.powerMult(p);
    }


    // =====================================================================
    // v12: the two power slots at the TOP of the screen (a boss bar), the
    // Activator's per-power look for the resource pack, the Hacker power and
    // the 7-day quest streak that unlocks it
    // =====================================================================

    /** CustomModelData the resource pack uses to show your equipped power in your hand. */
    static int modelDataFor(Power pw) {
        return 2000 + pw.ordinal();
    }

    /** Makes every Power Activator in the inventory show the equipped power's orb. */
    void refreshActivator(Player p) {
        Power cur = powerOf(p);
        Integer want = cur == null ? null : modelDataFor(cur);
        for (ItemStack it : p.getInventory().getContents()) {
            if (!isActivator(it)) continue;
            ItemMeta m = it.getItemMeta();
            if (java.util.Objects.equals(m.getCustomModelData(), want)) continue;
            m.setCustomModelData(want);
            it.setItemMeta(m);
        }
    }

    /** The player's power-bar style: their own /hud choice, else the config default. */
    String hudMode(Player p) {
        String own = data.getString("players." + p.getUniqueId() + ".hud");
        if (own != null && (own.equals("font") || own.equals("text") || own.equals("off"))) return own;
        String d = getConfig().getString("quick-switch.hud-default", "font");
        return d.equals("text") || d.equals("off") ? d : "font";
    }

    void setHudMode(Player p, String mode) {
        data.set("players." + p.getUniqueId() + ".hud", mode);
        saveData();
        refreshSlotBar(p);
    }

    /** One hotbar-slot picture from the Power Pack font (empty slot when the power is null). */
    private String slotGlyph(Power pw, boolean selected) {
        if (pw == null) return String.valueOf((char) (selected ? 0xE159 : 0xE158));
        return String.valueOf((char) ((selected ? 0xE12C : 0xE100) + pw.ordinal()));
    }

    /** The two power slots at the TOP of the screen: hotbar pictures (font mode,
     *  needs the Power Pack) or plain text, as a boss bar. */
    void refreshSlotBar(Player p) {
        Power cur = powerOf(p);
        Power st = standbyOf(p);
        BossBar bar = slotBars.get(p.getUniqueId());
        String mode = hudMode(p);
        boolean show = getConfig().getBoolean("quick-switch.top-bar", true) && !mode.equals("off")
                && (mode.equals("font") ? cur != null : cur != null && st != null);
        if (!show) {
            if (bar != null) {
                p.hideBossBar(bar);
                slotBars.remove(p.getUniqueId());
            }
            return;
        }
        Component name;
        if (mode.equals("font")) {
            name = Component.text(slotGlyph(cur, true) + "\uE15A" + slotGlyph(st, false))
                    .font(Key.key("abilitysmp", "hud"));
        } else {
            name = Component.text("[1] ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(pretty(cur.key()), colorFor(cur), TextDecoration.BOLD))
                    .append(Component.text("    [2] ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(pretty(st.key()), colorFor(st), TextDecoration.BOLD))
                    .append(Component.text("    switch: F / crouch+click", NamedTextColor.GRAY));
        }
        if (bar == null) {
            bar = BossBar.bossBar(name, 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            slotBars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(name);
        }
    }

    boolean isDupedSword(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(dupedKey, PersistentDataType.LONG);
    }

    private void cleanDupedSwords() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean removed = false;
            ItemStack[] contents = p.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack it = contents[slot];
                if (!isDupedSword(it)) continue;
                long expiry = it.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(dupedKey, PersistentDataType.LONG, 0L);
                if (now >= expiry) {
                    p.getInventory().setItem(slot, null);
                    removed = true;
                }
            }
            if (removed) {
                bar(p, "The duped sword vanished!", NamedTextColor.GREEN);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
            }
        }
    }

    /** How good a sword is: material tier first, then Sharpness, then everything else. */
    private static int swordScore(ItemStack it) {
        String n = it.getType().name();
        int tier = n.startsWith("NETHERITE") ? 6 : n.startsWith("DIAMOND") ? 5 : n.startsWith("IRON") ? 4
                : n.startsWith("STONE") ? 3 : n.startsWith("GOLDEN") ? 2 : 1;
        int ench = 0;
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : it.getEnchantments().entrySet()) {
            ench += e.getKey().equals(org.bukkit.enchantments.Enchantment.SHARPNESS)
                    ? e.getValue() * 10 : e.getValue();
        }
        return tier * 1000 + ench;
    }

    /** The best sword in a player's whole inventory (hotbar, storage and off hand), or null. */
    static ItemStack bestSwordOf(Player target) {
        ItemStack best = null;
        int bestScore = -1;
        List<ItemStack> all = new ArrayList<>(Arrays.asList(target.getInventory().getContents()));
        all.add(target.getInventory().getItemInOffHand());
        for (ItemStack it : all) {
            if (it == null || !it.getType().name().endsWith("_SWORD")) continue;
            int score = swordScore(it);
            if (score > bestScore) {
                bestScore = score;
                best = it;
            }
        }
        return best;
    }

    /** Hacker: copy the nearest enemy player's best sword. The copy vanishes after 30 seconds. */
    private void hackerSuper(Player p) {
        Player target = null;
        double best = Double.MAX_VALUE;
        double range = d("hacker.range", 20) * um(p);
        for (Player other : p.getWorld().getPlayers()) {
            if (other.equals(p) || !isEnemy(p, other)) continue;
            double dist = other.getLocation().distanceSquared(p.getLocation());
            if (dist < best) {
                best = dist;
                target = other;
            }
        }
        if (target == null || best > range * range) {
            bar(p, "No player in range to hack.", NamedTextColor.GRAY);
            return;
        }
        ItemStack sword = bestSwordOf(target);
        if (sword == null) {
            bar(p, target.getName() + " has no sword to dupe!", NamedTextColor.GRAY);
            return;
        }
        startCooldown(p, Power.HACKER);
        ItemStack copy = sword.clone();
        copy.setAmount(1);
        ItemMeta m = copy.getItemMeta();
        m.getPersistentDataContainer().set(dupedKey, PersistentDataType.LONG,
                System.currentTimeMillis() + i("hacker.dupe-seconds", 30) * 1000L);
        List<Component> lore = m.hasLore() ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(line("Duped by the Hacker power - vanishes in " + i("hacker.dupe-seconds", 30) + "s!",
                NamedTextColor.GREEN));
        m.lore(lore);
        copy.setItemMeta(m);
        give(p, copy);
        puff(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 25);
        puff(target.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 25);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        bar(p, "You duped " + target.getName() + "'s best sword for "
                + i("hacker.dupe-seconds", 30) + "s!", NamedTextColor.GREEN);
        bar(target, "A hacker duped your sword!", NamedTextColor.GREEN);
    }

    /** The player's current quest-day streak (0 if the streak has already lapsed). */
    int questStreak(Player p) {
        String path = "players." + p.getUniqueId() + ".";
        String last = data.getString(path + "quest-last-day", "");
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("America/Toronto"));
        if (last.equals(today.toString()) || last.equals(today.minusDays(1).toString())) {
            return data.getInt(path + "quest-streak", 0);
        }
        return 0;
    }

    /** Called every time a player finishes a quest: that day counts toward the streak. */
    void hackerQuestDay(Player p) {
        String path = "players." + p.getUniqueId() + ".";
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("America/Toronto"));
        String last = data.getString(path + "quest-last-day", "");
        if (today.toString().equals(last)) return; // today already counted
        int streak = today.minusDays(1).toString().equals(last)
                ? data.getInt(path + "quest-streak", 0) + 1 : 1;
        data.set(path + "quest-streak", streak);
        data.set(path + "quest-last-day", today.toString());
        saveData();
        int need = i("hacker.streak-days", 7);
        if (profile.owns(p, Power.HACKER)) return;
        if (streak >= need) {
            profile.addOwned(p.getUniqueId(), Power.HACKER);
            profile.save();
            ranks.evaluate(p);
            p.showTitle(Title.title(Component.text("HACKER UNLOCKED!", NamedTextColor.GREEN),
                    Component.text(need + "-day quest streak complete!", NamedTextColor.GRAY),
                    Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(5),
                            java.time.Duration.ofMillis(800))));
            p.sendMessage(Component.text("You finished a quest " + need + " days in a row and unlocked the "
                    + "HACKER power! Equip it in /powers: it dupes the best sword of the nearest player.",
                    NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Bukkit.broadcast(Component.text(p.getName() + " unlocked the HACKER power with a " + need
                    + "-day quest streak!", NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("Quest streak: " + streak + "/" + need
                    + " days. Finish a quest every day to unlock the HACKER power - miss a day and it resets!",
                    NamedTextColor.GREEN));
        }
    }

    // =====================================================================
    // Join / quit / death
    // =====================================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        setupPlayer(e.getPlayer());
        Galactic.joinHint(this, e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (party != null) party.onQuit(p);
        BossBar topBar = slotBars.remove(id);
        if (topBar != null) p.hideBossBar(topBar);
        clearHeartSteals(p);
        drained.remove(id);
        for (Set<UUID> s : drained.values()) s.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        UUID id = dead.getUniqueId();

        // the Power Activator is soulbound: it never drops, and comes back on respawn
        e.getDrops().removeIf(this::isActivator);
        e.getDrops().removeIf(this::isDupedSword); // duped swords (Hacker) vanish instead of dropping
        needsActivator.add(id);

        Set<UUID> victims = drained.remove(id);
        if (victims != null) for (UUID t : new HashSet<>(victims)) returnHearts(id, t);
        for (Map.Entry<UUID, Set<UUID>> en : new HashMap<>(drained).entrySet()) {
            if (en.getValue().contains(id)) returnHearts(en.getKey(), id);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        applyTankHearts(e.getPlayer());
        Player rp = e.getPlayer();
        if (needsActivator.remove(rp.getUniqueId())) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (rp.isOnline() && !hasActivator(rp)) give(rp, activatorItem());
            });
        }
    }

    // =====================================================================
    // Commands: /power, /reroll, /pass
    // =====================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("reroll")) return reroll(sender);
        if (name.equals("notes")) {
            Notes.show(this, sender, args);
            return true;
        }
        if (name.equals("skip")) {
            if (sender instanceof Player sp) tutorial.skip(sp);
            return true;
        }
        if (name.equals("tutorial")) {
            if (sender instanceof Player tp2) tutorial.restart(tp2);
            return true;
        }
        if (name.equals("panel")) {
            admin.command(sender, args);
            return true;
        }
        if (name.equals("activator")) {
            if (sender instanceof Player ap) {
                if (hasActivator(ap)) {
                    ap.sendMessage(Component.text("You already have your Power Activator.", NamedTextColor.GRAY));
                } else {
                    give(ap, activatorItem());
                    ap.sendMessage(Component.text("Here is a new Power Activator.", NamedTextColor.GOLD));
                }
            }
            return true;
        }
        if (name.equals("powers") || name.equals("shop") || name.equals("quests") || name.equals("locker")) {
            if (!(sender instanceof Player mp)) {
                sender.sendMessage("Players only.");
                return true;
            }
            switch (name) {
                case "powers" -> powerMenus.openPowers(mp, false);
                case "shop" -> powerMenus.openShop(mp);
                case "quests" -> powerMenus.openQuests(mp);
                default -> locker.open(mp);
            }
            return true;
        }
        if (name.equals("duel")) {
            duel.command(sender, args);
            return true;
        }
        if (name.equals("boss")) {
            bosses.command(sender, args);
            return true;
        }
        if (name.equals("gshop")) {
            if (sender instanceof Player hp) shops.openGalaxyShop(hp);
            return true;
        }
        if (name.equals("itemshop")) {
            if (sender instanceof Player ip) itemShop.open(ip);
            return true;
        }
        if (name.equals("weapons")) {
            if (sender instanceof Player wp) weapons.info(wp);
            return true;
        }
        if (name.equals("music")) {
            if (sender instanceof Player mup) music.handle(mup, args);
            return true;
        }
        if (name.equals("hud")) {
            if (sender instanceof Player hp2) {
                String cur2 = hudMode(hp2);
                String next = args.length > 0 ? args[0].toLowerCase(Locale.ROOT)
                        : cur2.equals("font") ? "text" : cur2.equals("text") ? "off" : "font";
                if (!next.equals("font") && !next.equals("text") && !next.equals("off")) {
                    hp2.sendMessage(Component.text("Use /hud font, /hud text or /hud off.", NamedTextColor.RED));
                } else {
                    setHudMode(hp2, next);
                    if (next.equals("font")) hp2.sendMessage(Component.text(
                            "Power bar: hotbar pictures (needs the Power Pack on). Seeing boxes instead? The pack is off - use /hud text.", NamedTextColor.GREEN));
                    else if (next.equals("text")) hp2.sendMessage(Component.text("Power bar: text style.", NamedTextColor.GREEN));
                    else hp2.sendMessage(Component.text("Power bar: hidden.", NamedTextColor.GREEN));
                }
            }
            return true;
        }
        if (name.equals("staffrank")) {
            staffRankCommand(sender, args);
            return true;
        }
        if (name.equals("shopkeeper")) {
            shops.keeperCommand(sender, args);
            return true;
        }
        if (name.equals("points") || name.equals("station") || name.equals("upgrade")) {
            unbound.command(sender, name, args);
            return true;
        }
        if (name.equals("crate")) {
            crates.command(sender, args);
            return true;
        }
        if (name.equals("trade")) {
            trading.command(sender, args);
            return true;
        }
        if (name.equals("ranked")) {
            ranked.command(sender, args);
            return true;
        }
        if (name.equals("rebirth")) {
            rebirths.command(sender, args);
            return true;
        }
        if (name.equals("galactic")) {
            Galactic.command(this, sender, args);
            return true;
        }
        if (name.equals("spawn")) {
            spawn.teleportCommand(sender);
            return true;
        }
        if (name.equals("setspawn")) {
            spawn.setSpawnCommand(sender);
            return true;
        }
        if (name.equals("spawnnpc")) {
            spawn.npcCommand(sender, args);
            return true;
        }
        if (name.equals("spawnbuild")) {
            spawn.buildCommand(sender);
            return true;
        }
        if (name.equals("rtp")) {
            spawn.rtpCommand(sender);
            return true;
        }

        String usage = "Usage: /power give <power> <player> | item <power> <player> | take <player> | list | "
                + "token <player> | points <player> <amount> | set <power> <player> | reload";
        if (args.length == 0) {
            sender.sendMessage(usage);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                loadData();
                for (Player p : Bukkit.getOnlinePlayers()) applyTankHearts(p);
                sender.sendMessage("AbilitySMP reloaded.");
            }
            case "list" -> {
                StringBuilder sb = new StringBuilder("Powers: ");
                for (Power p : Power.values()) sb.append(p.key()).append(' ');
                sender.sendMessage(sb.toString().trim());
            }
            case "give", "item", "set" -> {
                if (args.length < 3) { sender.sendMessage(usage); return true; }
                Power power = Power.parse(args[1]);
                if (power == null) { sender.sendMessage("Unknown power. Use /power list"); return true; }
                Player t = Bukkit.getPlayerExact(args[2]);
                if (t == null) { sender.sendMessage("Player not online."); return true; }
                if (power == Power.WIND) { // only ONE Wind exists, and commands are the only way to get it
                    UUID owner = profile.ownerOf(Power.WIND);
                    if (owner != null && !owner.equals(t.getUniqueId())) {
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
                        sender.sendMessage("The Wind power already belongs to "
                                + (op.getName() == null ? "someone" : op.getName()) + " - only ONE exists.");
                        return true;
                    }
                }
                if (!args[0].equalsIgnoreCase("set")) { // give = the power star item; set = assign directly
                    give(t, powerItem(power));
                    sender.sendMessage("Gave a " + pretty(power.key()) + " power star to " + t.getName() + ".");
                } else {
                    setPower(t, power);
                    t.sendMessage(Component.text("You now have the " + pretty(power.key()) + " power.", NamedTextColor.GOLD));
                    sender.sendMessage("Gave " + power.key() + " to " + t.getName() + ".");
                }
            }
            case "take" -> {
                if (args.length < 2) { sender.sendMessage(usage); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage("Player not online."); return true; }
                setPower(t, null);
                t.sendMessage(Component.text("Your power was removed.", NamedTextColor.GRAY));
                sender.sendMessage("Removed power from " + t.getName() + ".");
            }
            case "stock" -> {
                Power sp = args.length > 1 ? Power.parse(args[1]) : null;
                if (sp == null) { sender.sendMessage("Usage: /power stock <power> [discount percent]"); return true; }
                shops.stockPower(sender, sp, args.length > 2 ? parseInt(args[2], 0) : 0);
            }
            case "token" -> {
                if (args.length < 2) { sender.sendMessage(usage); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage("Player not online."); return true; }
                give(t, rerollItem());
                sender.sendMessage("Gave a reroll token to " + t.getName() + ".");
            }
            case "points" -> {
                if (args.length < 3) { sender.sendMessage(usage); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage("Player not online."); return true; }
                unbound.addPoints(t, parseInt(args[2], 0), "admin");
                sender.sendMessage("Gave Unbound Coins to " + t.getName() + ".");
            }
            default -> sender.sendMessage(usage);
        }
        return true;
    }

    /** /staffrank <player> <mod|tester|co-owner|none> - staff ranks only an OP can give (v10). */
    private void staffRankCommand(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only an OP can give staff ranks.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /staffrank <player> <mod|tester|co-owner|none>");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        org.bukkit.OfflinePlayer off = t == null ? Bukkit.getOfflinePlayer(args[0]) : t;
        if (off == null || (!off.hasPlayedBefore() && t == null)) {
            sender.sendMessage("Player not found.");
            return;
        }
        String rank = args[1].toLowerCase(Locale.ROOT).replace('_', '-');
        if (rank.equals("coowner")) rank = "co-owner";
        if (rank.equals("none") || rank.equals("remove")) {
            profile.of(off.getUniqueId()).staffRank = null;
            profile.save();
            if (t != null) {
                ranks.refresh(t);
                t.sendMessage(Component.text("Your staff rank was removed.", NamedTextColor.GRAY));
            }
            sender.sendMessage("Removed the staff rank from " + args[0] + ".");
            return;
        }
        if (!Ranks.STAFF.containsKey(rank)) {
            sender.sendMessage("Staff ranks are: mod, tester, co-owner (or none to remove).");
            return;
        }
        profile.of(off.getUniqueId()).staffRank = rank;
        profile.save();
        if (t != null) {
            ranks.refresh(t);
            t.sendMessage(Component.text("You are now a " + Ranks.staffName(rank) + "!", Ranks.staffColor(rank)));
        }
        sender.sendMessage("Gave the " + Ranks.staffName(rank) + " rank to " + args[0] + ".");
    }

    private boolean reroll(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        UUID id = p.getUniqueId();
        if (!getConfig().getBoolean("rerolling.enabled", true)) {
            p.sendMessage("Rerolling is disabled.");
            return true;
        }
        if (data.getBoolean("players." + id + ".rerolled", false)) {
            p.sendMessage("You already rerolled.");
            return true;
        }
        long first = data.getLong("players." + id + ".first-join-at", 0L);
        long windowMs = i("rerolling.window-minutes", 10) * 60_000L;
        if (first == 0L || System.currentTimeMillis() - first > windowMs) {
            p.sendMessage("The reroll window has passed.");
            return true;
        }
        Power next = roll(id, powerOf(p));
        if (next == null) {
            p.sendMessage("No other power available.");
            return true;
        }
        data.set("players." + id + ".rerolled", true);
        setPower(p, next);
        p.sendMessage(Component.text("New power: " + pretty(next.key()), NamedTextColor.GOLD));
        return true;
    }
}
