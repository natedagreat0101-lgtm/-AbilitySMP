package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
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

    enum Power {
        FLAME, SPEED, TANK, THOR, SHADOW, HEALER, FEATHER, STEALER, AQUA, HUNTER,
        GHOST, VAMPIRE, TELEPORTER, VOID;

        /** Halloween powers: only Ghost and Vampire. */
        boolean isHalloween() {
            return this == GHOST || this == VAMPIRE;
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
    TrickOrTreat tot;
    Profile profile;
    Ranks ranks;
    Locker locker;
    Menus menus;
    PowerMenus powerMenus;
    Horseman horseman;
    Duel duel;
    Shops shops;
    private NamespacedKey activatorKey;
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
        registerRerollRecipe();

        dataFile = new File(getDataFolder(), "players.yml");
        loadData();

        profile = new Profile(this);
        unbound = new Unbound(this);
        tot = new TrickOrTreat(this);
        ranks = new Ranks(this);
        locker = new Locker(this);
        menus = new Menus();
        powerMenus = new PowerMenus(this);
        horseman = new Horseman(this);
        duel = new Duel(this);
        shops = new Shops(this);
        tutorial = new Tutorial(this);
        admin = new Admin(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(tutorial, this);
        Bukkit.getPluginManager().registerEvents(admin, this);
        Bukkit.getPluginManager().registerEvents(unbound, this);
        Bukkit.getPluginManager().registerEvents(tot, this);
        Bukkit.getPluginManager().registerEvents(ranks, this);
        Bukkit.getPluginManager().registerEvents(locker, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(shops, this);
        Bukkit.getPluginManager().registerEvents(horseman, this);
        Bukkit.getPluginManager().registerEvents(duel, this);
        Bukkit.getScheduler().runTaskTimer(this, this::passiveTick, 20L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) setupPlayer(p);
    }

    @Override
    public void onDisable() {
        if (admin != null) admin.shutdown();
        if (horseman != null) horseman.shutdown();
        if (duel != null) duel.shutdown();
        if (shops != null) shops.save();
        if (tot != null) tot.stop(false);
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
        if (v < 9) {
            cfg.set("ranks.halloween.starter-kills", 1);   // Halloween ranks now come from kills with Halloween powers
            cfg.set("ranks.halloween.veteran-kills", 25);
            cfg.set("ranks.halloween.expert-kills", 100);
            cfg.set("config-version", 9);
        }
        cfg.options().copyDefaults(true);
        saveConfig();
    }

    private void loadData() {
        powers.clear();
        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = data.getConfigurationSection("players");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                Power p = Power.parse(sec.getString(id + ".power"));
                if (p != null) powers.put(uuid, p);
            } catch (IllegalArgumentException ignored) {
            }
        }
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
        hand.setAmount(hand.getAmount() - 1);
        setPower(p, newPower); // unlocks it permanently and equips it
        p.sendMessage(Component.text("You unlocked and equipped the " + pretty(newPower.key()) + " power!",
                colorFor(newPower)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        puff(p.getLocation().add(0, 1, 0), Particle.END_ROD, 30);
    }

    /** Equip a power you already own (used by the power menu). */
    boolean equipPower(Player p, Power pw) {
        if (!profile.owns(p, pw)) {
            bar(p, "You don't own that power yet. Check /shop.", NamedTextColor.RED);
            return false;
        }
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

    /** A random enabled power the player does not own yet, or null if they own them all. */
    Power randomUnowned(Player p) {
        return roll(p.getUniqueId(), null);
    }

    /** Unlocks a power for good and equips it (used when you buy a random power). */
    void unlockAndEquip(Player p, Power pw) {
        setPower(p, pw);
        p.sendMessage(Component.text("You got the " + pretty(pw.key()) + " power!", colorFor(pw)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        puff(p.getLocation().add(0, 1, 0), Particle.END_ROD, 30);
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
            if (power == null || !isEnabled(power) || p.getGameMode() == GameMode.SPECTATOR || admin.isCursed(p)) continue;

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
                default -> { } // TANK (hearts) and FEATHER (fall damage) are handled elsewhere
            }

            if (particles() && !p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                p.getWorld().spawnParticle(particleFor(power), p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
            }
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
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Power power = powerOf(p);
        if (power == Power.FEATHER && isEnabled(power)
                && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true); // Feather: no fall damage
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
        if (admin.isCursed(p)) {
            admin.sealedMessage(p);
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
        }
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
        if (isActivator(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            bar(e.getPlayer(), "The Power Activator is soulbound.", NamedTextColor.GRAY);
        }
    }

    private double um(Player p) {
        return unbound.powerMult(p);
    }

    // =====================================================================
    // Join / quit / death
    // =====================================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        setupPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
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
        if (name.equals("powers") || name.equals("quests") || name.equals("locker")) {
            if (!(sender instanceof Player mp)) {
                sender.sendMessage("Players only.");
                return true;
            }
            switch (name) {
                case "powers" -> powerMenus.openPowers(mp, false);
                case "quests" -> powerMenus.openQuests(mp);
                default -> locker.open(mp);
            }
            return true;
        }
        if (name.equals("rank")) {
            ranks.command(sender, args);
            return true;
        }
        if (name.equals("duel")) {
            duel.command(sender, args);
            return true;
        }
        if (name.equals("boss")) {
            horseman.command(sender, args);
            return true;
        }
        if (name.equals("halloweenshop")) {
            if (sender instanceof Player hp) shops.openHalloweenShop(hp);
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

        String usage = "Usage: /power shop | give <power> <player> | item <power> <player> | take <player> | list | "
                + "token <player> | points <player> <amount> | coins <player> <amount> | stock <power> [percent] | "
                + "set <power> <player> | reload";
        boolean admin0 = sender.hasPermission("abilitysmp.admin");
        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            // /power shop is for everyone: buy a reroll, a random power or the stocked power
            if (sender instanceof Player sp0) shops.openPowerShop(sp0);
            else sender.sendMessage(usage);
            return true;
        }
        if (!admin0) {
            sender.sendMessage(Component.text("Use /power shop to get a reroll or a power.", NamedTextColor.GRAY));
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
            case "coins" -> {
                if (args.length < 3) { sender.sendMessage(usage); return true; }
                Player ct = Bukkit.getPlayerExact(args[1]);
                if (ct == null) { sender.sendMessage("Player not online."); return true; }
                tot.giveCoins(ct, parseInt(args[2], 0));
                sender.sendMessage("Gave Halloween Coins to " + ct.getName() + ".");
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
