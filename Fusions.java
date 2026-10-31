package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mixed powers (v14) - 50 fusion powers made in the Mixing Pot from two base
 * powers. They are hidden in /powers until a player owns one, they are never
 * sold, never in crates and never rolled. Each fusion has a real passive and
 * a real super, driven by the archetype table below.
 */
final class Fusions implements Listener {

    record Def(AbilitySMP.Power self, AbilitySMP.Power a, AbilitySMP.Power b, String rarity,
               String color, String particle, String archetype, double dmg, double radius,
               int seconds, int count, String fx, String passive, int cooldown, String blurb) {
    }

    static final Map<AbilitySMP.Power, Def> DEFS = new EnumMap<>(AbilitySMP.Power.class);
    private static final Map<String, AbilitySMP.Power> BY_PAIR = new HashMap<>();

    static {
        DEFS.put(AbilitySMP.Power.MAGMA, new Def(AbilitySMP.Power.MAGMA, AbilitySMP.Power.FLAME, AbilitySMP.Power.EARTH, "LEGENDARY", "RED", "FLAME", "blast", 5, 6, 0, 0, "fire", "FIRE_RESISTANCE:0", 45, "A magma explosion around you."));
        BY_PAIR.put("EARTH+FLAME", AbilitySMP.Power.MAGMA);
        DEFS.put(AbilitySMP.Power.WILDFIRE, new Def(AbilitySMP.Power.WILDFIRE, AbilitySMP.Power.FLAME, AbilitySMP.Power.WIND, "EPIC", "GOLD", "FLAME", "storm", 3, 10, 0, 0, "fire", "SPEED:0", 40, "Fire sweeps every enemy near you."));
        BY_PAIR.put("FLAME+WIND", AbilitySMP.Power.WILDFIRE);
        DEFS.put(AbilitySMP.Power.STEAM, new Def(AbilitySMP.Power.STEAM, AbilitySMP.Power.FLAME, AbilitySMP.Power.AQUA, "EPIC", "AQUA", "CLOUD", "blast", 4, 5, 0, 0, "weak", "WATER_BREATHING:0", 35, "A scalding steam burst that weakens."));
        BY_PAIR.put("AQUA+FLAME", AbilitySMP.Power.STEAM);
        DEFS.put(AbilitySMP.Power.VOLTRUNNER, new Def(AbilitySMP.Power.VOLTRUNNER, AbilitySMP.Power.THOR, AbilitySMP.Power.SPEED, "LEGENDARY", "YELLOW", "ELECTRIC_SPARK", "dash", 3, 14, 0, 0, "none", "SPEED:1", 25, "An electric dash through enemies."));
        BY_PAIR.put("SPEED+THOR", AbilitySMP.Power.VOLTRUNNER);
        DEFS.put(AbilitySMP.Power.STORMWALL, new Def(AbilitySMP.Power.STORMWALL, AbilitySMP.Power.THOR, AbilitySMP.Power.TANK, "EPIC", "GRAY", "ELECTRIC_SPARK", "buff", 0, 0, 12, 0, "RESISTANCE:1,STRENGTH:1", "RESISTANCE:0", 50, "Storm armour: Resistance + Strength."));
        BY_PAIR.put("TANK+THOR", AbilitySMP.Power.STORMWALL);
        DEFS.put(AbilitySMP.Power.STORM_HUNTER, new Def(AbilitySMP.Power.STORM_HUNTER, AbilitySMP.Power.THOR, AbilitySMP.Power.HUNTER, "EPIC", "GREEN", "ELECTRIC_SPARK", "beam", 5, 24, 0, 0, "none", "NIGHT_VISION:0", 35, "A lightning beam at range."));
        BY_PAIR.put("HUNTER+THOR", AbilitySMP.Power.STORM_HUNTER);
        DEFS.put(AbilitySMP.Power.SHADOWSTEP, new Def(AbilitySMP.Power.SHADOWSTEP, AbilitySMP.Power.SHADOW, AbilitySMP.Power.TELEPORTER, "LEGENDARY", "DARK_GRAY", "SMOKE", "dash", 0, 18, 0, 0, "teleport", "NIGHT_VISION:0", 30, "Teleport forward in a puff of shadow."));
        BY_PAIR.put("SHADOW+TELEPORTER", AbilitySMP.Power.SHADOWSTEP);
        DEFS.put(AbilitySMP.Power.NIGHTSHADE, new Def(AbilitySMP.Power.NIGHTSHADE, AbilitySMP.Power.SHADOW, AbilitySMP.Power.VAMPIRE, "LEGENDARY", "DARK_PURPLE", "CRIMSON_SPORE", "drain", 5, 20, 0, 0, "none", "NIGHT_VISION:0", 40, "Drain an enemy's health into yours."));
        BY_PAIR.put("SHADOW+VAMPIRE", AbilitySMP.Power.NIGHTSHADE);
        DEFS.put(AbilitySMP.Power.BLOODMOON, new Def(AbilitySMP.Power.BLOODMOON, AbilitySMP.Power.VAMPIRE, AbilitySMP.Power.LUNAR, "MYTHIC", "DARK_RED", "CRIMSON_SPORE", "storm", 4, 12, 0, 0, "wither", "STRENGTH:0", 60, "A blood moon storm withers everyone near."));
        BY_PAIR.put("LUNAR+VAMPIRE", AbilitySMP.Power.BLOODMOON);
        DEFS.put(AbilitySMP.Power.SOUL_REAPER, new Def(AbilitySMP.Power.SOUL_REAPER, AbilitySMP.Power.REAPER, AbilitySMP.Power.GHOST, "MYTHIC", "DARK_AQUA", "SOUL", "summon", 0, 0, 60, 2, "SKELETON", "SPEED:0", 60, "Summon two skeletal reapers."));
        BY_PAIR.put("GHOST+REAPER", AbilitySMP.Power.SOUL_REAPER);
        DEFS.put(AbilitySMP.Power.PLAGUE, new Def(AbilitySMP.Power.PLAGUE, AbilitySMP.Power.WITCH, AbilitySMP.Power.CURSE, "LEGENDARY", "DARK_PURPLE", "WITCH", "storm", 2, 9, 0, 0, "poison", "NIGHT_VISION:0", 45, "A plague cloud poisons everyone near."));
        BY_PAIR.put("CURSE+WITCH", AbilitySMP.Power.PLAGUE);
        DEFS.put(AbilitySMP.Power.BONECHILL, new Def(AbilitySMP.Power.BONECHILL, AbilitySMP.Power.SKELETON, AbilitySMP.Power.FROST, "EPIC", "AQUA", "SNOWFLAKE", "volley", 0, 0, 0, 6, "snowball", "SPEED:0", 30, "A volley of freezing snowballs."));
        BY_PAIR.put("FROST+SKELETON", AbilitySMP.Power.BONECHILL);
        DEFS.put(AbilitySMP.Power.WEBHUNTER, new Def(AbilitySMP.Power.WEBHUNTER, AbilitySMP.Power.SPIDER, AbilitySMP.Power.HUNTER, "EPIC", "DARK_GRAY", "CRIT", "trap", 0, 5, 6, 0, "web", "HASTE:0", 40, "Webs burst out and trap nearby enemies."));
        BY_PAIR.put("HUNTER+SPIDER", AbilitySMP.Power.WEBHUNTER);
        DEFS.put(AbilitySMP.Power.HARVEST, new Def(AbilitySMP.Power.HARVEST, AbilitySMP.Power.PUMPKIN, AbilitySMP.Power.SCARECROW, "EPIC", "GOLD", "HAPPY_VILLAGER", "summon", 0, 0, 60, 2, "ZOMBIE", "HASTE:0", 55, "Summon two scarecrow helpers."));
        BY_PAIR.put("PUMPKIN+SCARECROW", AbilitySMP.Power.HARVEST);
        DEFS.put(AbilitySMP.Power.SUGAR_RUSH, new Def(AbilitySMP.Power.SUGAR_RUSH, AbilitySMP.Power.CANDY, AbilitySMP.Power.SPEED, "EPIC", "LIGHT_PURPLE", "HEART", "buff", 0, 0, 10, 0, "SPEED:2,JUMP_BOOST:1", "SPEED:0", 30, "A sugar rush: huge speed and jumps."));
        BY_PAIR.put("CANDY+SPEED", AbilitySMP.Power.SUGAR_RUSH);
        DEFS.put(AbilitySMP.Power.WRAITHGUARD, new Def(AbilitySMP.Power.WRAITHGUARD, AbilitySMP.Power.PHANTOM, AbilitySMP.Power.TANK, "LEGENDARY", "GRAY", "SOUL", "buff", 0, 0, 15, 0, "RESISTANCE:2,ABSORPTION:1", "RESISTANCE:0", 55, "Ghostly armour shrugs off damage."));
        BY_PAIR.put("PHANTOM+TANK", AbilitySMP.Power.WRAITHGUARD);
        DEFS.put(AbilitySMP.Power.MOONHOWL, new Def(AbilitySMP.Power.MOONHOWL, AbilitySMP.Power.WEREWOLF, AbilitySMP.Power.LUNAR, "EPIC", "LIGHT_PURPLE", "CLOUD", "buff", 0, 0, 12, 0, "STRENGTH:2,SPEED:1", "NIGHT_VISION:0", 45, "Howl at the moon: Strength III burst."));
        BY_PAIR.put("LUNAR+WEREWOLF", AbilitySMP.Power.MOONHOWL);
        DEFS.put(AbilitySMP.Power.STARFORGE, new Def(AbilitySMP.Power.STARFORGE, AbilitySMP.Power.STARLIGHT, AbilitySMP.Power.EARTH, "EPIC", "YELLOW", "END_ROD", "trap", 1.5, 6, 8, 0, "zone", "RESISTANCE:0", 50, "A burning star-forge zone around you."));
        BY_PAIR.put("EARTH+STARLIGHT", AbilitySMP.Power.STARFORGE);
        DEFS.put(AbilitySMP.Power.COMETFALL, new Def(AbilitySMP.Power.COMETFALL, AbilitySMP.Power.COMET, AbilitySMP.Power.METEOR, "LEGENDARY", "BLUE", "FLAME", "volley", 0, 0, 0, 3, "fireball", "SPEED:0", 40, "Three comets streak from your hand."));
        BY_PAIR.put("COMET+METEOR", AbilitySMP.Power.COMETFALL);
        DEFS.put(AbilitySMP.Power.SOLAR_STORM, new Def(AbilitySMP.Power.SOLAR_STORM, AbilitySMP.Power.SOLAR_FLARE, AbilitySMP.Power.PULSAR, "LEGENDARY", "GOLD", "ELECTRIC_SPARK", "storm", 4, 11, 0, 0, "blind", "FIRE_RESISTANCE:0", 55, "A blinding solar storm."));
        BY_PAIR.put("PULSAR+SOLAR_FLARE", AbilitySMP.Power.SOLAR_STORM);
        DEFS.put(AbilitySMP.Power.VOIDWALKER, new Def(AbilitySMP.Power.VOIDWALKER, AbilitySMP.Power.VOID, AbilitySMP.Power.TELEPORTER, "MYTHIC", "DARK_PURPLE", "PORTAL", "dash", 4, 24, 0, 0, "teleport", "STRENGTH:1", 35, "Rip through the void, landing with a shockwave."));
        BY_PAIR.put("TELEPORTER+VOID", AbilitySMP.Power.VOIDWALKER);
        DEFS.put(AbilitySMP.Power.SINGULARITY, new Def(AbilitySMP.Power.SINGULARITY, AbilitySMP.Power.BLACK_HOLE, AbilitySMP.Power.WORMHOLE, "MYTHIC", "DARK_BLUE", "PORTAL", "trap", 1, 7, 6, 0, "pull", "RESISTANCE:0", 70, "A singularity drags enemies in and crushes them."));
        BY_PAIR.put("BLACK_HOLE+WORMHOLE", AbilitySMP.Power.SINGULARITY);
        DEFS.put(AbilitySMP.Power.HYPERNOVA, new Def(AbilitySMP.Power.HYPERNOVA, AbilitySMP.Power.SUPERNOVA, AbilitySMP.Power.ANTIMATTER, "MYTHIC", "RED", "FLAME", "blast", 9, 8, 0, 0, "fire", "FIRE_RESISTANCE:0", 90, "The biggest explosion of any power."));
        BY_PAIR.put("ANTIMATTER+SUPERNOVA", AbilitySMP.Power.HYPERNOVA);
        DEFS.put(AbilitySMP.Power.GAMMARAY, new Def(AbilitySMP.Power.GAMMARAY, AbilitySMP.Power.QUASAR, AbilitySMP.Power.STARLIGHT, "MYTHIC", "LIGHT_PURPLE", "END_ROD", "beam", 8, 30, 0, 0, "none", "NIGHT_VISION:0", 60, "A devastating gamma beam."));
        BY_PAIR.put("QUASAR+STARLIGHT", AbilitySMP.Power.GAMMARAY);
        DEFS.put(AbilitySMP.Power.AFTERBURNER, new Def(AbilitySMP.Power.AFTERBURNER, AbilitySMP.Power.ROCKET, AbilitySMP.Power.SPEED, "EPIC", "GOLD", "CLOUD", "dash", 3, 16, 0, 0, "none", "SPEED:1", 25, "Rocket-fuelled dash, burning everyone you pass."));
        BY_PAIR.put("ROCKET+SPEED", AbilitySMP.Power.AFTERBURNER);
        DEFS.put(AbilitySMP.Power.GLITCH, new Def(AbilitySMP.Power.GLITCH, AbilitySMP.Power.HACKER, AbilitySMP.Power.TELEPORTER, "MYTHIC", "GREEN", "ELECTRIC_SPARK", "beam", 4, 22, 0, 0, "scramble", "SPEED:1", 40, "A glitch beam that scrambles the target's position."));
        BY_PAIR.put("HACKER+TELEPORTER", AbilitySMP.Power.GLITCH);
        DEFS.put(AbilitySMP.Power.BLIZZARD, new Def(AbilitySMP.Power.BLIZZARD, AbilitySMP.Power.AQUA, AbilitySMP.Power.FROST, "LEGENDARY", "AQUA", "SNOWFLAKE", "storm", 3, 10, 0, 0, "freeze", "WATER_BREATHING:0", 50, "A blizzard freezes everyone near you."));
        BY_PAIR.put("AQUA+FROST", AbilitySMP.Power.BLIZZARD);
        DEFS.put(AbilitySMP.Power.HEALING_TIDE, new Def(AbilitySMP.Power.HEALING_TIDE, AbilitySMP.Power.AQUA, AbilitySMP.Power.HEALER, "EPIC", "BLUE", "HEART", "auraheal", 4, 8, 0, 0, "none", "REGENERATION:0", 45, "A healing tide for you and nearby players."));
        BY_PAIR.put("AQUA+HEALER", AbilitySMP.Power.HEALING_TIDE);
        DEFS.put(AbilitySMP.Power.GUARDIAN_ANGEL, new Def(AbilitySMP.Power.GUARDIAN_ANGEL, AbilitySMP.Power.HEALER, AbilitySMP.Power.FEATHER, "EPIC", "WHITE", "HEART", "buff", 0, 0, 20, 0, "REGENERATION:2,SLOW_FALLING:0", "REGENERATION:0", 50, "Angel wings: big regen and a gentle fall."));
        BY_PAIR.put("FEATHER+HEALER", AbilitySMP.Power.GUARDIAN_ANGEL);
        DEFS.put(AbilitySMP.Power.CYCLONE, new Def(AbilitySMP.Power.CYCLONE, AbilitySMP.Power.FEATHER, AbilitySMP.Power.WIND, "EPIC", "WHITE", "CLOUD", "storm", 2, 9, 0, 0, "levitate", "JUMP_BOOST:1", 45, "A cyclone throws enemies into the air."));
        BY_PAIR.put("FEATHER+WIND", AbilitySMP.Power.CYCLONE);
        DEFS.put(AbilitySMP.Power.DATA_THIEF, new Def(AbilitySMP.Power.DATA_THIEF, AbilitySMP.Power.STEALER, AbilitySMP.Power.HACKER, "MYTHIC", "GREEN", "WITCH", "drain", 5, 18, 0, 0, "weak", "SPEED:1", 40, "Hack an enemy's health into yours."));
        BY_PAIR.put("HACKER+STEALER", AbilitySMP.Power.DATA_THIEF);
        DEFS.put(AbilitySMP.Power.FORTRESS, new Def(AbilitySMP.Power.FORTRESS, AbilitySMP.Power.TANK, AbilitySMP.Power.EARTH, "LEGENDARY", "DARK_GREEN", "CRIT", "buff", 0, 0, 14, 0, "RESISTANCE:2,ABSORPTION:1", "RESISTANCE:1", 60, "Become a walking fortress."));
        BY_PAIR.put("EARTH+TANK", AbilitySMP.Power.FORTRESS);
        DEFS.put(AbilitySMP.Power.TECTONIC_RIFT, new Def(AbilitySMP.Power.TECTONIC_RIFT, AbilitySMP.Power.EARTH, AbilitySMP.Power.WORMHOLE, "LEGENDARY", "DARK_GREEN", "CRIT", "trap", 1.5, 6, 8, 0, "zone", "RESISTANCE:0", 55, "Open a rift that grinds enemies down."));
        BY_PAIR.put("EARTH+WORMHOLE", AbilitySMP.Power.TECTONIC_RIFT);
        DEFS.put(AbilitySMP.Power.ORBITAL, new Def(AbilitySMP.Power.ORBITAL, AbilitySMP.Power.ZERO_GRAVITY, AbilitySMP.Power.WIND, "EPIC", "LIGHT_PURPLE", "CLOUD", "buff", 0, 0, 10, 0, "LEVITATION:0,SLOW_FALLING:0", "JUMP_BOOST:1", 40, "Float up, then drift down like orbit."));
        BY_PAIR.put("WIND+ZERO_GRAVITY", AbilitySMP.Power.ORBITAL);
        DEFS.put(AbilitySMP.Power.STARFALL, new Def(AbilitySMP.Power.STARFALL, AbilitySMP.Power.ASTEROID, AbilitySMP.Power.COMET, "LEGENDARY", "GRAY", "END_ROD", "volley", 0, 0, 0, 5, "fireball", "RESISTANCE:0", 45, "Five falling stars from your hand."));
        BY_PAIR.put("ASTEROID+COMET", AbilitySMP.Power.STARFALL);
        DEFS.put(AbilitySMP.Power.BULWARK, new Def(AbilitySMP.Power.BULWARK, AbilitySMP.Power.ASTEROID, AbilitySMP.Power.TANK, "EPIC", "GRAY", "CRIT", "blast", 4, 5, 0, 0, "knock", "RESISTANCE:1", 40, "A shockwave that throws enemies back."));
        BY_PAIR.put("ASTEROID+TANK", AbilitySMP.Power.BULWARK);
        DEFS.put(AbilitySMP.Power.PYROCLASM, new Def(AbilitySMP.Power.PYROCLASM, AbilitySMP.Power.METEOR, AbilitySMP.Power.FLAME, "LEGENDARY", "RED", "FLAME", "storm", 4, 10, 0, 0, "fire", "FIRE_RESISTANCE:0", 50, "A pyroclastic storm of fire."));
        BY_PAIR.put("FLAME+METEOR", AbilitySMP.Power.PYROCLASM);
        DEFS.put(AbilitySMP.Power.UMBRA, new Def(AbilitySMP.Power.UMBRA, AbilitySMP.Power.LUNAR, AbilitySMP.Power.ECLIPSE, "LEGENDARY", "DARK_GRAY", "SMOKE", "beam", 6, 26, 0, 0, "blind", "NIGHT_VISION:0", 45, "A beam of pure darkness that blinds."));
        BY_PAIR.put("ECLIPSE+LUNAR", AbilitySMP.Power.UMBRA);
        DEFS.put(AbilitySMP.Power.NIGHTFALL, new Def(AbilitySMP.Power.NIGHTFALL, AbilitySMP.Power.ECLIPSE, AbilitySMP.Power.SHADOW, "LEGENDARY", "DARK_GRAY", "SMOKE", "storm", 3, 11, 0, 0, "blind", "NIGHT_VISION:0", 50, "Night falls: everyone near is blinded and hurt."));
        BY_PAIR.put("ECLIPSE+SHADOW", AbilitySMP.Power.NIGHTFALL);
        DEFS.put(AbilitySMP.Power.POLTERGEIST, new Def(AbilitySMP.Power.POLTERGEIST, AbilitySMP.Power.GHOST, AbilitySMP.Power.PHANTOM, "MYTHIC", "AQUA", "SOUL", "summon", 0, 0, 60, 3, "VEX", "SPEED:0", 60, "Summon three poltergeist vexes."));
        BY_PAIR.put("GHOST+PHANTOM", AbilitySMP.Power.POLTERGEIST);
        DEFS.put(AbilitySMP.Power.BLOOD_WITCH, new Def(AbilitySMP.Power.BLOOD_WITCH, AbilitySMP.Power.VAMPIRE, AbilitySMP.Power.WITCH, "LEGENDARY", "DARK_RED", "WITCH", "drain", 6, 20, 0, 0, "none", "NIGHT_VISION:0", 45, "A heavy life-drain curse."));
        BY_PAIR.put("VAMPIRE+WITCH", AbilitySMP.Power.BLOOD_WITCH);
        DEFS.put(AbilitySMP.Power.DOOM_HERALD, new Def(AbilitySMP.Power.DOOM_HERALD, AbilitySMP.Power.CURSE, AbilitySMP.Power.VOID, "MYTHIC", "DARK_PURPLE", "WITCH", "storm", 4, 10, 0, 0, "wither", "STRENGTH:1", 60, "Herald doom: a withering storm."));
        BY_PAIR.put("CURSE+VOID", AbilitySMP.Power.DOOM_HERALD);
        DEFS.put(AbilitySMP.Power.GRIM_BONES, new Def(AbilitySMP.Power.GRIM_BONES, AbilitySMP.Power.SKELETON, AbilitySMP.Power.REAPER, "LEGENDARY", "WHITE", "SOUL", "summon", 0, 0, 60, 3, "SKELETON", "STRENGTH:0", 55, "Raise three grim skeletons."));
        BY_PAIR.put("REAPER+SKELETON", AbilitySMP.Power.GRIM_BONES);
        DEFS.put(AbilitySMP.Power.WEBFIELD, new Def(AbilitySMP.Power.WEBFIELD, AbilitySMP.Power.SPIDER, AbilitySMP.Power.SCARECROW, "EPIC", "DARK_GREEN", "SMOKE", "trap", 0, 6, 7, 0, "web", "HASTE:0", 45, "A field of webs snares everyone near."));
        BY_PAIR.put("SCARECROW+SPIDER", AbilitySMP.Power.WEBFIELD);
        DEFS.put(AbilitySMP.Power.JACK_O_HEX, new Def(AbilitySMP.Power.JACK_O_HEX, AbilitySMP.Power.PUMPKIN, AbilitySMP.Power.WITCH, "EPIC", "GOLD", "FLAME", "volley", 0, 0, 0, 4, "fireball", "NIGHT_VISION:0", 40, "Hurl flaming pumpkin bombs."));
        BY_PAIR.put("PUMPKIN+WITCH", AbilitySMP.Power.JACK_O_HEX);
        DEFS.put(AbilitySMP.Power.SWEET_RELIEF, new Def(AbilitySMP.Power.SWEET_RELIEF, AbilitySMP.Power.CANDY, AbilitySMP.Power.HEALER, "EPIC", "LIGHT_PURPLE", "HEART", "auraheal", 3, 7, 0, 0, "none", "REGENERATION:0", 40, "Sweet healing for you and nearby players."));
        BY_PAIR.put("CANDY+HEALER", AbilitySMP.Power.SWEET_RELIEF);
        DEFS.put(AbilitySMP.Power.SKY_HUNTER, new Def(AbilitySMP.Power.SKY_HUNTER, AbilitySMP.Power.HUNTER, AbilitySMP.Power.FEATHER, "EPIC", "GREEN", "END_ROD", "volley", 0, 0, 0, 6, "arrow", "SPEED:0", 35, "A fan of arrows from above the trees."));
        BY_PAIR.put("FEATHER+HUNTER", AbilitySMP.Power.SKY_HUNTER);
        DEFS.put(AbilitySMP.Power.WARP_DRIVE, new Def(AbilitySMP.Power.WARP_DRIVE, AbilitySMP.Power.TELEPORTER, AbilitySMP.Power.ROCKET, "LEGENDARY", "DARK_BLUE", "PORTAL", "dash", 0, 20, 0, 0, "teleport", "SPEED:1", 30, "Engage the warp drive: instant jump forward."));
        BY_PAIR.put("ROCKET+TELEPORTER", AbilitySMP.Power.WARP_DRIVE);
        DEFS.put(AbilitySMP.Power.ION_CANNON, new Def(AbilitySMP.Power.ION_CANNON, AbilitySMP.Power.THOR, AbilitySMP.Power.QUASAR, "MYTHIC", "YELLOW", "ELECTRIC_SPARK", "beam", 9, 32, 0, 0, "none", "STRENGTH:0", 65, "Fire the ion cannon. Ouch."));
        BY_PAIR.put("QUASAR+THOR", AbilitySMP.Power.ION_CANNON);
        DEFS.put(AbilitySMP.Power.CORONA, new Def(AbilitySMP.Power.CORONA, AbilitySMP.Power.SUPERNOVA, AbilitySMP.Power.SOLAR_FLARE, "MYTHIC", "GOLD", "FLAME", "blast", 8, 7, 0, 0, "fireblind", "FIRE_RESISTANCE:0", 80, "A corona blast of stellar fire."));
        BY_PAIR.put("SOLAR_FLARE+SUPERNOVA", AbilitySMP.Power.CORONA);
    }

    private final AbilitySMP plugin;
    private final Random random = new Random();
    private final NamespacedKey freezeKey;
    private final List<Zone> zones = new ArrayList<>();
    private final Map<Location, Long> webRestore = new HashMap<>();

    private static final class Zone {
        Location center;
        double radius;
        double dmgHp;
        boolean pull;
        long endAt;
        AbilitySMP.Power power;
    }

    Fusions(AbilitySMP plugin) {
        this.plugin = plugin;
        this.freezeKey = new NamespacedKey(plugin, "fusion_freeze");
        new BukkitRunnable() {
            @Override
            public void run() {
                tickZones();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    // ------------------------------------------------------------- lookups

    static Def def(AbilitySMP.Power pw) {
        return DEFS.get(pw);
    }

    static AbilitySMP.Power mix(AbilitySMP.Power a, AbilitySMP.Power b) {
        if (a == null || b == null || a == b) return null;
        String k = a.name().compareTo(b.name()) < 0 ? a.name() + "+" + b.name() : b.name() + "+" + a.name();
        return BY_PAIR.get(k);
    }

    static int total() {
        return DEFS.size();
    }

    static int countOwned(AbilitySMP plugin, Player p) {
        int n = 0;
        for (AbilitySMP.Power pw : DEFS.keySet()) if (plugin.profile.owns(p, pw)) n++;
        return n;
    }

    static AbilitySMP.Rarity rarityOf(AbilitySMP.Power pw) {
        Def d = DEFS.get(pw);
        return d == null ? AbilitySMP.Rarity.EPIC : AbilitySMP.Rarity.valueOf(d.rarity());
    }

    static NamedTextColor colorOf(AbilitySMP.Power pw) {
        Def d = DEFS.get(pw);
        if (d == null) return NamedTextColor.LIGHT_PURPLE;
        return switch (d.color()) {
            case "RED" -> NamedTextColor.RED;
            case "GOLD" -> NamedTextColor.GOLD;
            case "AQUA" -> NamedTextColor.AQUA;
            case "YELLOW" -> NamedTextColor.YELLOW;
            case "GRAY" -> NamedTextColor.GRAY;
            case "GREEN" -> NamedTextColor.GREEN;
            case "BLUE" -> NamedTextColor.BLUE;
            case "WHITE" -> NamedTextColor.WHITE;
            case "DARK_RED" -> NamedTextColor.DARK_RED;
            case "DARK_GRAY" -> NamedTextColor.DARK_GRAY;
            case "DARK_AQUA" -> NamedTextColor.DARK_AQUA;
            case "DARK_BLUE" -> NamedTextColor.DARK_BLUE;
            case "DARK_GREEN" -> NamedTextColor.DARK_GREEN;
            case "DARK_PURPLE" -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    static Particle particleOf(AbilitySMP.Power pw) {
        Def d = DEFS.get(pw);
        if (d == null) return Particle.WITCH;
        try {
            return Particle.valueOf(d.particle());
        } catch (IllegalArgumentException e) {
            return Particle.WITCH;
        }
    }

    static String describe(AbilitySMP.Power pw) {
        Def d = DEFS.get(pw);
        if (d == null) return "";
        return "Mixed power (" + AbilitySMP.pretty(d.a().key()) + " + " + AbilitySMP.pretty(d.b().key())
                + "). " + d.blurb() + " Made in the Mixing Pot.";
    }

    // ------------------------------------------------------------- helpers

    private List<LivingEntity> foes(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof LivingEntity le && le != p && !(e instanceof org.bukkit.entity.ArmorStand)
                    && le.getLocation().distanceSquared(p.getLocation()) <= r * r) {
                out.add(le);
            }
        }
        return out;
    }

    private LivingEntity nearestFoe(Player p, double range) {
        LivingEntity best = null;
        double bd = range * range;
        for (LivingEntity le : foes(p, range)) {
            double d = le.getLocation().distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                best = le;
            }
        }
        return best;
    }

    private void fx(LivingEntity t, String fx, Player src) {
        switch (fx) {
            case "fire", "fireblind" -> {
                t.setFireTicks(100);
                if (fx.equals("fireblind")) t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
            }
            case "blind" -> t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            case "weak" -> t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 140, 0));
            case "poison" -> t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 0));
            case "wither" -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 0));
            case "freeze" -> t.setFreezeTicks(160);
            case "levitate" -> t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0));
            case "knock" -> {
                Vector v = t.getLocation().toVector().subtract(src.getLocation().toVector());
                v.setY(0.45);
                if (v.lengthSquared() > 0.01) t.setVelocity(v.normalize().multiply(1.6));
            }
            case "scramble" -> {
                Location l = t.getLocation().clone().add(random.nextInt(13) - 6, 1, random.nextInt(13) - 6);
                l = l.getWorld().getHighestBlockAt(l).getLocation().add(0, 1, 0);
                t.teleport(l);
                t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.2);
            }
            default -> {
            }
        }
    }

    private void buffs(Player p, String spec, int seconds) {
        for (String part : spec.split(",")) {
            String[] kv = part.split(":");
            PotionEffectType type = PotionEffectType.getByName(kv[0].trim());
            if (type == null) continue;
            int amp = kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 0;
            p.addPotionEffect(new PotionEffect(type, seconds * 20, amp));
        }
    }

    // ------------------------------------------------------------- passive

    void passive(Player p, AbilitySMP.Power pw) {
        Def d = DEFS.get(pw);
        if (d == null || d.passive() == null || d.passive().isEmpty()) return;
        for (String part : d.passive().split(",")) {
            String[] kv = part.split(":");
            PotionEffectType type = PotionEffectType.getByName(kv[0].trim());
            if (type == null) continue;
            int amp = kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 0;
            p.addPotionEffect(new PotionEffect(type, 80, amp, true, false, false));
        }
    }

    // ------------------------------------------------------------- supers

    void activate(Player p, AbilitySMP.Power pw) {
        Def d = DEFS.get(pw);
        if (d == null) return;
        Particle part = particleOf(pw);
        Location eye = p.getEyeLocation();
        switch (d.archetype()) {
            case "blast" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                Location c = p.getLocation();
                c.getWorld().spawnParticle(part, c.clone().add(0, 1, 0), 60, d.radius() * 0.4, 1, d.radius() * 0.4, 0.15);
                c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 1, 0), 2);
                p.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
                for (LivingEntity t : foes(p, d.radius())) {
                    t.damage(d.dmg() * 2, p);
                    fx(t, d.fx(), p);
                    if (!d.fx().equals("knock")) fx(t, "knock", p);
                }
            }
            case "storm" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                Location c = p.getLocation();
                p.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
                for (LivingEntity t : foes(p, d.radius())) {
                    t.getWorld().spawnParticle(part, t.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.1);
                    t.getWorld().strikeLightningEffect(t.getLocation());
                    t.damage(d.dmg() * 2, p);
                    fx(t, d.fx(), p);
                }
            }
            case "beam" -> {
                LivingEntity t = beamTarget(p, d.radius());
                if (t == null) {
                    p.sendActionBar(Component.text("No one in your sights.", NamedTextColor.GRAY));
                    return;
                }
                plugin.startCooldownSeconds(p, d.cooldown());
                Vector dir = eye.getDirection();
                for (double s = 1; s < eye.distance(t.getLocation().add(0, 1, 0)); s += 0.6) {
                    p.getWorld().spawnParticle(part, eye.clone().add(dir.clone().multiply(s)), 3, 0.05, 0.05, 0.05, 0);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
                t.damage(d.dmg() * 2, p);
                fx(t, d.fx(), p);
            }
            case "drain" -> {
                LivingEntity t = nearestFoe(p, d.radius());
                if (t == null) {
                    p.sendActionBar(Component.text("No one in range to drain.", NamedTextColor.GRAY));
                    return;
                }
                plugin.startCooldownSeconds(p, d.cooldown());
                t.damage(d.dmg() * 2, p);
                fx(t, d.fx(), p);
                double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                p.setHealth(Math.min(max, p.getHealth() + d.dmg()));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.6, 0), 8, 0.4, 0.4, 0.4, 0);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 0.8f);
            }
            case "volley" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                Vector dir = eye.getDirection();
                for (int i = 0; i < d.count(); i++) {
                    double spread = (i - (d.count() - 1) / 2.0) * 0.07;
                    Vector v = dir.clone();
                    double cos = Math.cos(spread), sin = Math.sin(spread);
                    double x = v.getX() * cos - v.getZ() * sin;
                    double z = v.getX() * sin + v.getZ() * cos;
                    v.setX(x);
                    v.setZ(z);
                    switch (d.fx()) {
                        case "fireball" -> {
                            Fireball fb = p.launchProjectile(Fireball.class, v.clone().multiply(1.1));
                            fb.setYield(1.2f);
                            fb.setShooter(p);
                        }
                        case "snowball" -> {
                            Snowball sb = p.launchProjectile(Snowball.class, v.clone().multiply(1.6));
                            sb.getPersistentDataContainer().set(freezeKey, PersistentDataType.BYTE, (byte) 1);
                            sb.setShooter(p);
                        }
                        default -> {
                            Arrow ar = p.launchProjectile(Arrow.class, v.clone().multiply(2.4));
                            ar.setShooter(p);
                        }
                    }
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
            }
            case "buff" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                buffs(p, d.fx(), d.seconds());
                p.getWorld().spawnParticle(part, p.getLocation().add(0, 1, 0), 35, 0.5, 0.9, 0.5, 0.1);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
            case "auraheal" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                double heal = d.dmg() * 2;
                List<Player> targets = new ArrayList<>();
                targets.add(p);
                for (Entity e : p.getNearbyEntities(d.radius(), d.radius(), d.radius())) {
                    if (e instanceof Player other && other != p) targets.add(other);
                }
                for (Player t : targets) {
                    double max = t.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    t.setHealth(Math.min(max, t.getHealth() + heal));
                    t.getWorld().spawnParticle(Particle.HEART, t.getLocation().add(0, 1.8, 0), 10, 0.4, 0.3, 0.4, 0);
                }
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            }
            case "dash" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                Vector dir = eye.getDirection();
                if (d.fx().equals("teleport")) {
                    Location dest = eye.clone();
                    Location last = p.getLocation().clone();
                    for (double s = 1; s <= d.radius(); s += 0.5) {
                        Location probe = eye.clone().add(dir.clone().multiply(s)).add(0, -1, 0);
                        if (probe.getBlock().isSolid()) break;
                        Location feet = probe.clone().add(0, 1, 0);
                        if (!feet.getBlock().isSolid() && !feet.clone().add(0, 1, 0).getBlock().isSolid()) {
                            last = feet;
                        }
                    }
                    p.getWorld().spawnParticle(part, p.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.1);
                    p.teleport(last.setDirection(dir));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
                    if (d.dmg() > 0) {
                        for (LivingEntity t : foes(p, 4)) {
                            t.damage(d.dmg() * 2, p);
                        }
                    }
                } else {
                    p.setVelocity(dir.clone().multiply(2.1).setY(Math.max(0.42, dir.getY())));
                    Location mid = p.getLocation().add(dir.clone().multiply(5));
                    p.getWorld().spawnParticle(part, mid.add(0, 1, 0), 25, 1, 0.6, 1, 0.1);
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                    if (d.dmg() > 0) {
                        for (Entity e : p.getNearbyEntities(6, 3, 6)) {
                            if (e instanceof LivingEntity t && t != p
                                    && t.getLocation().distanceSquared(mid) <= 12) {
                                t.damage(d.dmg() * 2, p);
                                fx(t, d.fx(), p);
                            }
                        }
                    }
                }
            }
            case "trap" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                if (d.fx().equals("web")) {
                    long until = System.currentTimeMillis() + d.seconds() * 1000L;
                    for (LivingEntity t : foes(p, d.radius())) {
                        Block b = t.getLocation().getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.COBWEB);
                            webRestore.put(b.getLocation(), until);
                        }
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, d.seconds() * 20, 1));
                    }
                    p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_STEP, 1f, 0.7f);
                } else {
                    Zone z = new Zone();
                    z.center = p.getLocation().clone();
                    z.radius = d.radius();
                    z.dmgHp = d.dmg();
                    z.pull = d.fx().equals("pull");
                    z.endAt = System.currentTimeMillis() + d.seconds() * 1000L;
                    z.power = pw;
                    zones.add(z);
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.8f);
                }
            }
            case "summon" -> {
                plugin.startCooldownSeconds(p, d.cooldown());
                EntityType type = EntityType.valueOf(d.fx());
                LivingEntity target = nearestFoe(p, 24);
                for (int i = 0; i < d.count(); i++) {
                    Location at = p.getLocation().clone().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                    Entity e = p.getWorld().spawnEntity(at, type);
                    if (e instanceof LivingEntity le) {
                        le.customName(Component.text(AbilitySMP.pretty(pw.key()) + " Minion", colorOf(pw)));
                        le.setCustomNameVisible(true);
                        if (le instanceof org.bukkit.entity.Skeleton sk) {
                            sk.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.IRON_SWORD));
                        }
                        if (le instanceof Mob mob && target != null) mob.setTarget(target);
                        final LivingEntity minion = le;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (minion.isValid()) {
                                    minion.getWorld().spawnParticle(Particle.SOUL, minion.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.05);
                                    minion.remove();
                                }
                            }
                        }.runTaskLater(plugin, d.seconds() * 20L);
                    }
                }
                p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
            }
            default -> {
            }
        }
    }

    private LivingEntity beamTarget(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        for (double s = 1; s <= range; s += 0.5) {
            Location point = eye.clone().add(dir.clone().multiply(s));
            if (point.getBlock().isSolid()) return null;
            for (Entity e : p.getWorld().getNearbyEntities(point, 1.3, 1.3, 1.3)) {
                if (e instanceof LivingEntity le && le != p && !(e instanceof org.bukkit.entity.ArmorStand)) return le;
            }
        }
        return null;
    }

    private void tickZones() {
        long now = System.currentTimeMillis();
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            if (now > z.endAt || z.center.getWorld() == null) {
                it.remove();
                continue;
            }
            z.center.getWorld().spawnParticle(particleOf(z.power), z.center.clone().add(0, 0.4, 0),
                    14, z.radius * 0.6, 0.3, z.radius * 0.6, 0.02);
            for (Entity e : z.center.getWorld().getNearbyEntities(z.center, z.radius, 3, z.radius)) {
                if (e instanceof LivingEntity le && !(e instanceof org.bukkit.entity.ArmorStand)) {
                    le.damage(z.dmgHp);
                    if (z.pull) {
                        Vector v = z.center.toVector().subtract(le.getLocation().toVector());
                        v.setY(0.1);
                        if (v.lengthSquared() > 1) le.setVelocity(v.normalize().multiply(0.45));
                    }
                }
            }
        }
        Iterator<Map.Entry<Location, Long>> wi = webRestore.entrySet().iterator();
        while (wi.hasNext()) {
            Map.Entry<Location, Long> en = wi.next();
            if (now > en.getValue()) {
                if (en.getKey().getBlock().getType() == Material.COBWEB) en.getKey().getBlock().setType(Material.AIR);
                wi.remove();
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!proj.getPersistentDataContainer().has(freezeKey, PersistentDataType.BYTE)) return;
        if (e.getHitEntity() instanceof LivingEntity le) {
            le.setFreezeTicks(140);
            if (proj.getShooter() instanceof Player shooter) le.damage(2, shooter);
        }
    }
}
