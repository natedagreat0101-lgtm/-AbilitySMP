package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * AbilitySMP - every power in one plugin. Right-click with a sword to use your power.
 * Uses the same config.yml and players.yml layout as before, so it is a drop-in replacement.
 */
public class AbilitySMP extends JavaPlugin implements Listener {

    enum Power {
        FLAME, SPEED, TANK, THOR, SHADOW, HEALER, FEATHER, STEALER, AQUA, HUNTER,
        JACK_O_LANTERN, GHOST, MIMIC;

        /** Name used in config.yml, e.g. jack-o-lantern */
        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        static Power parse(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
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
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, BukkitTask> disguiseTasks = new HashMap<>();
    /** haunter UUID -> players whose hearts that haunter is holding */
    private final Map<UUID, Set<UUID>> drained = new HashMap<>();
    private final Random random = new Random();

    private File dataFile;
    private YamlConfiguration data;
    private DisguiseHook disguises;

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        loadData();

        if (Bukkit.getPluginManager().getPlugin("LibsDisguises") != null) {
            disguises = DisguiseHook.create();
        }
        if (disguises == null) {
            getLogger().warning("LibsDisguises not found: Mimic will hit and have lives, but won't change your look.");
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::passiveTick, 20L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) setupPlayer(p);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearHeartSteals(p);
            if (disguises != null) disguises.undisguise(p);
        }
    }

    private void loadData() {
        powers.clear();
        lives.clear();
        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = data.getConfigurationSection("players");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                Power p = Power.parse(sec.getString(id + ".power"));
                if (p != null) {
                    powers.put(uuid, p);
                    if (p == Power.MIMIC) lives.put(uuid, mimicLives());
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveData() {
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

    private int mimicLives() {
        return i("mimic.lives", 2);
    }

    private Power powerOf(Player p) {
        return powers.get(p.getUniqueId());
    }

    private boolean isEnabled(Power p) {
        return getConfig().getStringList("powers.enabled").contains(p.key());
    }

    private NamespacedKey key(String s) {
        return new NamespacedKey(this, s.toLowerCase(Locale.ROOT));
    }

    private void bar(Player p, String text, NamedTextColor color) {
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

    private Player nearestEnemy(Player from, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player other : from.getWorld().getPlayers()) {
            if (other.equals(from) || other.getGameMode() == GameMode.SPECTATOR) continue;
            double dist = other.getLocation().distanceSquared(from.getLocation());
            if (dist <= bestDist) {
                bestDist = dist;
                best = other;
            }
        }
        return best;
    }

    private void effect(Player p, PotionEffectType type, int level) {
        p.addPotionEffect(new PotionEffect(type, 60, Math.max(0, level - 1), true, false, false));
    }

    private void glowNearby(Player owner, double radius) {
        for (Player other : owner.getWorld().getPlayers()) {
            if (other.equals(owner) || other.getGameMode() == GameMode.SPECTATOR) continue;
            if (other.getLocation().distanceSquared(owner.getLocation()) <= radius * radius) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, false));
            }
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

    /** Weighted random pick using the "drops" chances as weights. */
    private Power roll(UUID forWho, Power exclude) {
        List<Power> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Power p : Power.values()) {
            if (p == exclude || !isEnabled(p)) continue;
            double w = d("drops." + p.key(), 0);
            if (w <= 0) continue;
            if (getConfig().getBoolean(p.key() + ".limited", false) && isHeldByOther(p, forWho)) continue;
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
            lives.remove(id);
            data.set("players." + id + ".power", "NONE");
        } else {
            powers.put(id, power);
            data.set("players." + id + ".power", power.name());
            if (power == Power.MIMIC) lives.put(id, mimicLives());
        }
        applyTankHearts(p);
        saveData();
    }

    private void removePowerEffects(Player p) {
        stopDisguise(p);
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
        if (!data.contains("players." + id) && getConfig().getBoolean("drops.enabled", true)) {
            Power rolled = roll(id, null);
            if (rolled != null) {
                data.set("players." + id + ".rerolled", false);
                data.set("players." + id + ".first-join-at", System.currentTimeMillis());
                setPower(p, rolled);
                p.sendMessage(Component.text("You got the " + rolled.key() + " power! Right-click with a sword to use it.",
                        NamedTextColor.GOLD));
                if (getConfig().getBoolean("rerolling.enabled", true)) {
                    p.sendMessage(Component.text("Don't like it? Use /reroll within "
                            + i("rerolling.window-minutes", 10) + " minutes.", NamedTextColor.GRAY));
                }
                return;
            }
        }
        if (powers.get(id) == Power.MIMIC) lives.put(id, mimicLives());
        applyTankHearts(p);
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
    // Idle abilities (run once per second)
    // =====================================================================

    private void passiveTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Power power = powerOf(p);
            if (power == null || !isEnabled(power) || p.getGameMode() == GameMode.SPECTATOR) continue;

            switch (power) {
                case FLAME -> effect(p, PotionEffectType.FIRE_RESISTANCE, 1);
                case SPEED -> effect(p, PotionEffectType.SPEED, i("passives.speed-level", 2));
                case THOR -> effect(p, PotionEffectType.STRENGTH, i("passives.thor-strength-level", 1));
                case STEALER -> effect(p, PotionEffectType.HASTE, i("passives.stealer-haste-level", 1));
                case HEALER -> effect(p, PotionEffectType.REGENERATION, i("passives.healer-regeneration-level", 2));
                case SHADOW -> effect(p, PotionEffectType.NIGHT_VISION, 1);
                case AQUA -> {
                    effect(p, PotionEffectType.WATER_BREATHING, 1);
                    effect(p, PotionEffectType.DOLPHINS_GRACE, i("passives.aqua-dolphins-grace-level", 1));
                }
                case HUNTER -> glowNearby(p, d("passives.hunter-glow-radius", 30));
                case JACK_O_LANTERN -> glowNearby(p, d("passives.jack-o-lantern-glow-radius", 30));
                case GHOST -> {
                    effect(p, PotionEffectType.SPEED, i("passives.ghost-speed-level", 1));
                    glowNearby(p, d("ghost.glow-radius", 30));
                }
                default -> { } // TANK (hearts) and FEATHER / MIMIC are handled elsewhere
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
            case STEALER, MIMIC -> Particle.WITCH;
            case AQUA -> Particle.SPLASH;
            case HUNTER -> Particle.END_ROD;
            case JACK_O_LANTERN, GHOST -> Particle.SOUL;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Power power = powerOf(p);
        if (power == null || !isEnabled(power)) return;

        // Feather: no fall damage
        if (power == Power.FEATHER && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }

        // Mimic: a killing blow uses up a life instead
        if (power == Power.MIMIC && e.getFinalDamage() >= p.getHealth()) {
            int left = lives.getOrDefault(p.getUniqueId(), 1);
            if (left <= 1) return; // last life: let it happen
            e.setCancelled(true);
            lives.put(p.getUniqueId(), left - 1);

            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0);
            p.setFireTicks(0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            p.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
            bar(p, "Mimic: life lost, " + (left - 1) + " left", NamedTextColor.GOLD);
        }
    }

    // =====================================================================
    // Using a power: right-click with a sword
    // =====================================================================

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        Power power = powerOf(p);
        if (power == null || !isEnabled(power)) return;
        if (!p.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) return;

        if (onCooldown(p)) return;
        activate(p, power);
    }

    private boolean onCooldown(Player p) {
        long left = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        if (left <= 0) return false;
        bar(p, "Cooldown: " + ((left + 999) / 1000) + "s", NamedTextColor.RED);
        return true;
    }

    private void startCooldown(Player p, Power power) {
        int seconds = i(power.key() + ".cooldown", 15);
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
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
                Vector v = flat(p.getLocation()).multiply(d("speed.dash-strength", 2.2));
                v.setY(d("speed.dash-up", 0.4));
                p.setVelocity(v);
                puff(p.getLocation().add(0, 1, 0), Particle.CLOUD, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
            }

            case THOR -> {
                Player t = nearestEnemy(p, d("thor.range", 30));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                t.getWorld().strikeLightning(t.getLocation());
            }

            case SHADOW -> {
                Player t = nearestEnemy(p, d("shadow.range", 25));
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
                Player t = nearestEnemy(p, d("hunter.range", 25));
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
                Player t = nearestEnemy(p, d("stealer.range", 20));
                if (t == null) { bar(p, "No one in range.", NamedTextColor.GRAY); return; }
                startCooldown(p, power);
                int ticks = i("stealer.duration", 15) * 20;
                // steal their strength: they get weak and slow, you get strong and fast
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
                for (Player other : p.getWorld().getPlayers()) {
                    if (other.equals(p) || other.getGameMode() == GameMode.SPECTATOR) continue;
                    if (other.getLocation().distanceSquared(p.getLocation()) > radius * radius) continue;
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10));
                    other.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128));
                    other.sendActionBar(Component.text("Stunned!", NamedTextColor.RED));
                }
                puff(p.getLocation().add(0, 0.5, 0), Particle.CRIT, 40);
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
            }

            case JACK_O_LANTERN -> haunt(p, power,
                    d(k + ".range", 25),
                    i(k + ".wither-duration-seconds", 5),
                    i(k + ".wither-amplifier", 1),
                    i(k + ".hearts-stolen", 3),
                    i(k + ".invisibility-duration-seconds", 5));

            case GHOST -> haunt(p, power,
                    d(k + ".range", 20),
                    i(k + ".wither-seconds", 3),
                    i(k + ".wither-amplifier", 0),
                    i(k + ".hearts-stolen", 4),
                    i(k + ".invisibility-duration-seconds", 10));

            case MIMIC -> mimic(p);
        }
    }

    // =====================================================================
    // Ghost / Jack-o-Lantern: invisible + wither + steal hearts
    // =====================================================================

    private void haunt(Player ghost, Power power, double range, int witherSec, int witherAmp,
                       int hearts, int invisSec) {
        Player target = nearestEnemy(ghost, range);
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
        target.playSound(target.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1f, 0.5f);
        ghost.playSound(ghost.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1f, 0.6f);
        bar(target, "You are being haunted...", NamedTextColor.DARK_PURPLE);
    }

    private void stealHearts(Player ghost, Player target, Power power, int hearts) {
        double want = hearts * 2.0;
        double minLeft = i("ghost.min-hearts-left", 1) * 2.0;

        AttributeInstance tMax = target.getAttribute(Attribute.MAX_HEALTH);
        AttributeInstance gMax = ghost.getAttribute(Attribute.MAX_HEALTH);
        if (tMax == null || gMax == null) return;

        NamespacedKey drainKey = key(DRAIN + ghost.getUniqueId());
        NamespacedKey gainKey = key(GAIN + target.getUniqueId());

        // an older steal between these two is replaced, so it can't stack forever
        removeModifier(tMax, drainKey);
        removeModifier(gMax, gainKey);

        double stolen = Math.min(want, tMax.getValue() - minLeft);
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
        Player g = Bukkit.getPlayer(ghostId);
        Player t = Bukkit.getPlayer(targetId);
        if (t != null) removeModifier(t.getAttribute(Attribute.MAX_HEALTH), key(DRAIN + ghostId));
        if (g != null) removeModifier(g.getAttribute(Attribute.MAX_HEALTH), key(GAIN + targetId));
        Set<UUID> set = drained.get(ghostId);
        if (set != null) set.remove(targetId);
    }

    private void removeModifier(AttributeInstance inst, NamespacedKey k) {
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(k)) inst.removeModifier(m);
        }
    }

    /** Strips stolen-heart modifiers (they are saved with the player, so clear them on quit/join). */
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
    // Mimic: turn into the nearest enemy and hit them
    // =====================================================================

    private void mimic(Player mimic) {
        Player target = nearestEnemy(mimic, d("mimic.range", 20));
        if (target == null) {
            bar(mimic, "No one in range to mimic.", NamedTextColor.GRAY);
            return; // no cooldown spent
        }
        startCooldown(mimic, Power.MIMIC);

        if (disguises != null) {
            if (disguises.disguise(mimic, target)) {
                scheduleUndisguise(mimic, i("mimic.disguise-duration-seconds", 15));
            } else {
                mimic.sendMessage(Component.text("Disguise failed (check LibsDisguises).", NamedTextColor.RED));
            }
        } else {
            mimic.sendMessage(Component.text("Install LibsDisguises to actually look like them.", NamedTextColor.RED));
        }

        target.damage(d("mimic.damage-hearts", 3) * 2.0, mimic);
        puff(mimic.getLocation().add(0, 1, 0), Particle.WITCH, 30);
        mimic.playSound(mimic.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        bar(target, "Someone is mimicking you!", NamedTextColor.RED);
    }

    private void scheduleUndisguise(Player mimic, int seconds) {
        UUID id = mimic.getUniqueId();
        BukkitTask old = disguiseTasks.remove(id);
        if (old != null) old.cancel();
        disguiseTasks.put(id, Bukkit.getScheduler().runTaskLater(this, () -> {
            disguiseTasks.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && disguises != null) disguises.undisguise(p);
        }, seconds * 20L));
    }

    private void stopDisguise(Player p) {
        BukkitTask t = disguiseTasks.remove(p.getUniqueId());
        if (t != null) t.cancel();
        if (disguises != null) disguises.undisguise(p);
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
        stopDisguise(p);
        drained.remove(id);
        for (Set<UUID> s : drained.values()) s.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        UUID id = dead.getUniqueId();
        stopDisguise(dead);

        Set<UUID> victims = drained.remove(id);
        if (victims != null) for (UUID t : new HashSet<>(victims)) returnHearts(id, t);
        for (Map.Entry<UUID, Set<UUID>> en : new HashMap<>(drained).entrySet()) {
            if (en.getValue().contains(id)) returnHearts(en.getKey(), id);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (powerOf(p) == Power.MIMIC) lives.put(p.getUniqueId(), mimicLives());
        applyTankHearts(p);
    }

    // =====================================================================
    // Commands: /power and /reroll
    // =====================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("reroll")) {
            return reroll(sender);
        }

        String usage = "Usage: /power give <power> <player> | take <player> | list | reload";
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
            case "give" -> {
                if (args.length < 3) { sender.sendMessage(usage); return true; }
                Power power = Power.parse(args[1]);
                if (power == null) { sender.sendMessage("Unknown power. Use /power list"); return true; }
                Player t = Bukkit.getPlayerExact(args[2]);
                if (t == null) { sender.sendMessage("Player not online."); return true; }
                setPower(t, power);
                t.sendMessage(Component.text("You now have the " + power.key() + " power.", NamedTextColor.GOLD));
                sender.sendMessage("Gave " + power.key() + " to " + t.getName() + ".");
            }
            case "take" -> {
                if (args.length < 2) { sender.sendMessage(usage); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage("Player not online."); return true; }
                setPower(t, null);
                t.sendMessage(Component.text("Your power was removed.", NamedTextColor.GRAY));
                sender.sendMessage("Removed power from " + t.getName() + ".");
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
        p.sendMessage(Component.text("New power: " + next.key(), NamedTextColor.GOLD));
        return true;
    }

    // =====================================================================
    // LibsDisguises through reflection (so the plugin works without it)
    // =====================================================================

    static final class DisguiseHook {
        private final Constructor<?> playerDisguiseCtor;
        private final Method disguiseToAll;
        private final Method undisguiseToAll;

        private DisguiseHook(Constructor<?> c, Method d, Method u) {
            this.playerDisguiseCtor = c;
            this.disguiseToAll = d;
            this.undisguiseToAll = u;
        }

        static DisguiseHook create() {
            try {
                Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
                Class<?> playerDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
                Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
                return new DisguiseHook(
                        playerDisguise.getConstructor(Player.class),
                        api.getMethod("disguiseToAll", Entity.class, disguise),
                        api.getMethod("undisguiseToAll", Entity.class));
            } catch (Throwable t) {
                return null;
            }
        }

        boolean disguise(Player who, Player as) {
            try {
                disguiseToAll.invoke(null, who, playerDisguiseCtor.newInstance(as));
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        void undisguise(Player who) {
            try {
                undisguiseToAll.invoke(null, who);
            } catch (Throwable ignored) {
            }
        }
    }
}
