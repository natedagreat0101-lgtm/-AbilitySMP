package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The legendary weapons from AltarSMP (v12), rebuilt for AbilitySMP. Fourteen weapons,
 * each identified by the same PDC tag AltarSMP used ("legendary_weapon"), sold in the
 * new /itemshop. Triggers are simplified to this server's style: right-click = ability 1,
 * crouch + right-click = ability 2, and passives run while the weapon is in your main hand.
 * Two AltarSMP ideas are simplified on purpose: the Wand of Illusion cannot truly disguise
 * a player without a packet library, so its captured soul powers its burst instead; and
 * Vulcan's arrows ignite but do not literally pierce shields.
 */
final class Weapons implements Listener {

    enum WDef {
        HYPERION("hyperion", "Hyperion", NamedTextColor.GOLD, Material.NETHERITE_SWORD, 2500,
                new String[]{"Passive: you cannot catch fire while holding it.",
                        "Right-click: Scorching Blade - a blade of flame (30s)",
                        "Crouch + right-click: Holy Lance - a beam of light (1m)"}),
        NIGHTPIERCER("nightpiercer", "Nightpiercer", NamedTextColor.DARK_RED, Material.NETHERITE_SWORD, 2200,
                new String[]{"Passive: Regeneration I at night while holding it.",
                        "Right-click: Transformation - burst forward as a bat cloud (30s)",
                        "Crouch + right-click: Crimson Bite - steal 2 hearts for 10s (1m)"}),
        FROST_SCYTHE("frost_scythe", "Frost Scythe", NamedTextColor.AQUA, Material.NETHERITE_HOE, 2000,
                new String[]{"Right-click: Scythe Throw - a flying scythe that freezes (30s)",
                        "Crouch + right-click: Command of Ice - 3 ice shards (45s)"}),
        ILLUSION_WAND("illusion_wand", "Wand of Illusion", NamedTextColor.DARK_PURPLE, Material.BLAZE_ROD, 1800,
                new String[]{"Kill a mob with the wand to capture its soul.",
                        "Right-click: unleash the captured soul's power",
                        "Crouch + right-click: set the captured mob free"}),
        BONE_BLADE("bone_blade", "Bone Blade", NamedTextColor.WHITE, Material.NETHERITE_SWORD, 1500,
                new String[]{"Right-click: Skeletal Leap + Speed III (30s)",
                        "Crouch + right-click: Bone Cage - stuns a player for 4s (1m)"}),
        BLOODLUST("bloodlust", "Bloodlust", NamedTextColor.RED, Material.NETHERITE_SWORD, 2600,
                new String[]{"The more you kill with it, the stronger it gets.",
                        "Kills unlock: bleed, Speed II, blood tracker,",
                        "blood trail, Strength I and the Blood Hook."}),
        VULCANS_CROSSBOW("vulcans_crossbow", "Vulcan's Crossbow", NamedTextColor.RED, Material.CROSSBOW, 2400,
                new String[]{"Passive: fires 3 arrows at once; the middle one ignites.",
                        "Crouch + right-click: arm Vulcan's Wrath - your next",
                        "shot is an exploding fireball (45s)"}),
        SHADOW_BLADE("shadow_blade", "Shadow Blade", NamedTextColor.DARK_PURPLE, Material.NETHERITE_SWORD, 2100,
                new String[]{"Passive: Speed I while holding it.",
                        "Right-click: Shadow Daggers x3 - slowness + blindness (1m)",
                        "Crouch + right-click: Shadow Leap - vanish and teleport (1m)"}),
        CUTLASS("cutlass", "Cutlass", NamedTextColor.YELLOW, Material.GOLDEN_SWORD, 1400,
                new String[]{"Right-click: Thousand Cuts - a flurry, then a lunge (30s)",
                        "Crouch + right-click: Parry - block 3 hits for 7s (30s)"}),
        PALADIN_AXE("paladin_axe", "Paladin's Battle Axe", NamedTextColor.GOLD, Material.NETHERITE_AXE, 2300,
                new String[]{"Passive: Haste I while holding it.",
                        "Right-click: Earthshatter - a shockwave where you look (45s)",
                        "Crouch + right-click: Stalwart Absorption - soak damage,",
                        "then blast it back out (30s)"}),
        MINDWEAVER("mindweaver", "Mindweaver", NamedTextColor.LIGHT_PURPLE, Material.NETHERITE_SWORD, 1700,
                new String[]{"Knockback II.",
                        "Right-click: Mind Leap + Speed IV (15s)",
                        "Hold crouch, then let go: Mindweaver Gust (15-30s)"}),
        PURE_BLADE("pure_blade", "Pure Blade", NamedTextColor.WHITE, Material.NETHERITE_SWORD, 1900,
                new String[]{"Right-click: Shade Soul - a piercing shadow (45s)",
                        "Crouch + right-click: Cyclone Slash - spin and cut (30s)"}),
        EARTH_GAUNTLET("earth_gauntlet", "Earth Gauntlet", NamedTextColor.GOLD, Material.MACE, 1600,
                new String[]{"Right-click: Meteor Strike - charge your next punch (30s)",
                        "Crouch + right-click: Mudslide - mud an enemy for 4s (45s)",
                        "While an enemy is mudded, right-click pulls them to you"}),
        WITHER_REAPER("wither_reaper", "Wither Reaper", NamedTextColor.DARK_PURPLE, Material.NETHERITE_SWORD, 2800,
                new String[]{"Damage builds your wither meter. A full meter punishes YOU.",
                        "Right-click: Wither Cleansing - expel the meter (30s)",
                        "Crouch + right-click: Wither Leap - ram enemies (15s)"});

        final String id;
        final String display;
        final NamedTextColor color;
        final Material material;
        final int price;
        final String[] lore;

        WDef(String id, String display, NamedTextColor color, Material material, int price, String[] lore) {
            this.id = id;
            this.display = display;
            this.color = color;
            this.material = material;
            this.price = price;
            this.lore = lore;
        }

        int price(AbilitySMP plugin) {
            return plugin.getConfig().getInt("itemshop.weapons." + id + ".price", price);
        }

        static WDef byId(String id) {
            for (WDef d : values()) if (d.id.equals(id)) return d;
            return null;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey weaponKey;
    private final NamespacedKey killsKey;
    private final NamespacedKey capturedKey;
    private final NamespacedKey vulcanArrowKey;
    private final Random random = new Random();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Set<UUID> wrathArmed = new HashSet<>();
    private final Set<UUID> wrathShots = new HashSet<>();
    private final Map<UUID, Long> parryUntil = new HashMap<>();
    private final Map<UUID, Integer> parryBlocks = new HashMap<>();
    private final Map<UUID, Long> absorbUntil = new HashMap<>();
    private final Map<UUID, Double> absorbed = new HashMap<>();
    private final Map<UUID, Long> meteorUntil = new HashMap<>();
    private final Map<UUID, UUID> muddedBy = new HashMap<>(); // target -> owner
    private final Map<UUID, Long> muddedUntil = new HashMap<>();
    private final Map<UUID, Integer> witherMeter = new HashMap<>();
    private final Map<UUID, Integer> leapCharges = new HashMap<>();
    private final Map<UUID, Long> leapWindow = new HashMap<>();
    private final Map<UUID, Set<UUID>> leapHit = new HashMap<>();
    private final Map<UUID, Long> chargeStart = new HashMap<>();

    Weapons(AbilitySMP plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "legendary_weapon");
        this.killsKey = new NamespacedKey(plugin, "bloodlust_kills");
        this.capturedKey = new NamespacedKey(plugin, "captured_soul");
        this.vulcanArrowKey = new NamespacedKey(plugin, "vulcan_arrow");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------ items

    ItemStack create(WDef d) {
        ItemStack it = new ItemStack(d.material);
        ItemMeta m = it.getItemMeta();
        m.displayName(line(d.display, d.color));
        m.lore(loreFor(d, 0, null));
        m.setUnbreakable(true);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, d.id);
        switch (d) {
            case HYPERION -> {
                m.addEnchant(Enchantment.SHARPNESS, 5, true);
                m.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                m.addEnchant(Enchantment.LOOTING, 3, true);
            }
            case NIGHTPIERCER -> m.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            case PALADIN_AXE -> m.addEnchant(Enchantment.EFFICIENCY, 5, true);
            case MINDWEAVER -> m.addEnchant(Enchantment.KNOCKBACK, 2, true);
            case SHADOW_BLADE -> {
                m.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        new NamespacedKey(plugin, "shadow_damage"), 7.0, AttributeModifier.Operation.ADD_NUMBER));
                m.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        new NamespacedKey(plugin, "shadow_speed"), -2.4, AttributeModifier.Operation.ADD_NUMBER));
            }
            default -> {
            }
        }
        if (d == WDef.PALADIN_AXE) {
            m.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, "paladin_damage"), 9.0, AttributeModifier.Operation.ADD_NUMBER));
            m.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "paladin_speed"), -3.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        it.setItemMeta(m);
        return it;
    }

    private List<Component> loreFor(WDef d, int kills, String captured) {
        List<Component> out = new ArrayList<>();
        out.add(line("✦ LEGENDARY WEAPON ✦ (from AltarSMP)", NamedTextColor.DARK_PURPLE));
        for (String l : d.lore) out.add(line(l, NamedTextColor.GRAY));
        if (d == WDef.BLOODLUST) {
            out.add(line("Kills: " + kills, NamedTextColor.RED));
            out.add(line(kills >= 1 ? "✔ Speed II while holding" : "1 kill: Speed II while holding", NamedTextColor.DARK_GRAY));
            out.add(line(kills >= 3 ? "✔ Blood Trail (crouch + right-click)" : "3 kills: Blood Trail", NamedTextColor.DARK_GRAY));
            out.add(line(kills >= 4 ? "✔ Strength I while holding" : "4 kills: Strength I while holding", NamedTextColor.DARK_GRAY));
            out.add(line(kills >= 5 ? "✔ Blood Hook (right-click)" : "5 kills: Blood Hook", NamedTextColor.DARK_GRAY));
        }
        if (d == WDef.ILLUSION_WAND) {
            out.add(line(captured == null ? "Captured soul: none" : "Captured soul: " + captured, NamedTextColor.LIGHT_PURPLE));
        }
        out.add(line("Unbreakable", NamedTextColor.DARK_GRAY));
        return out;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    WDef defOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        return id == null ? null : WDef.byId(id);
    }

    boolean isWeapon(ItemStack it) {
        return defOf(it) != null;
    }

    private int killsOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        return it.getItemMeta().getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    // ------------------------------------------------------------ cooldowns

    private boolean ready(Player p, WDef d, int slot, int seconds) {
        String key = p.getUniqueId() + ":" + d.id + ":" + slot;
        long left = cooldowns.getOrDefault(key, 0L) - System.currentTimeMillis();
        if (left > 0) {
            plugin.bar(p, d.display + " ready in " + ((left + 999) / 1000) + "s", NamedTextColor.RED);
            return false;
        }
        cooldowns.put(key, System.currentTimeMillis() + seconds * 1000L);
        return true;
    }

    private boolean peekReady(Player p, WDef d, int slot) {
        return cooldowns.getOrDefault(p.getUniqueId() + ":" + d.id + ":" + slot, 0L) <= System.currentTimeMillis();
    }

    // ------------------------------------------------------------ helpers

    private static boolean foe(Player from, Entity e) {
        if (e == null || e.equals(from) || !e.isValid() || e.isDead() || !(e instanceof LivingEntity)) return false;
        if (e instanceof Player other) return other.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        return e instanceof Enemy || e instanceof Mob;
    }

    private List<LivingEntity> foesNear(Player from, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : c.getWorld().getNearbyLivingEntities(c, r)) {
            if (foe(from, le) && le.getLocation().distanceSquared(c) <= r * r) out.add(le);
        }
        return out;
    }

    private void effect(Player p, PotionEffectType type, int ticks, int level) {
        p.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, level - 1), true, false, false));
    }

    /** A point of magic that flies from the player's eyes until it hits something. */
    private void launchPoint(Player p, double range, double speedPerTick, Particle particle,
                             double hitbox, boolean pierce, java.util.function.Consumer<LivingEntity> onHit) {
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        Set<UUID> hit = new HashSet<>();
        new org.bukkit.scheduler.BukkitRunnable() {
            Location cur = start.clone().add(dir.clone().multiply(1.2));
            double travelled = 0;

            @Override
            public void run() {
                for (int step = 0; step < 2; step++) {
                    cur.add(dir.clone().multiply(speedPerTick / 2));
                    travelled += speedPerTick / 2;
                    cur.getWorld().spawnParticle(particle, cur, 3, 0.08, 0.08, 0.08, 0.01);
                    if (cur.getBlock().getType().isSolid()) {
                        cancel();
                        return;
                    }
                    for (LivingEntity le : cur.getWorld().getNearbyLivingEntities(cur, hitbox)) {
                        if (!foe(p, le) || hit.contains(le.getUniqueId())) continue;
                        hit.add(le.getUniqueId());
                        onHit.accept(le);
                        if (!pierce) {
                            cancel();
                            return;
                        }
                    }
                    if (travelled >= range) {
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void ring(Location c, Particle particle, double r, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            c.getWorld().spawnParticle(particle, c.clone().add(Math.cos(a) * r, 0.4, Math.sin(a) * r),
                    1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------ using weapons

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        WDef d = defOf(hand);
        if (d == null) return;
        boolean crouch = p.isSneaking();

        switch (d) {
            case VULCANS_CROSSBOW -> { // crouch + right-click arms the Wrath shot
                if (crouch) {
                    e.setCancelled(true);
                    if (wrathArmed.remove(p.getUniqueId())) {
                        plugin.bar(p, "Vulcan's Wrath disarmed.", NamedTextColor.GRAY);
                    } else if (ready(p, d, 1, 0)) {
                        wrathArmed.add(p.getUniqueId());
                        cooldowns.remove(p.getUniqueId() + ":" + d.id + ":1"); // cooldown starts at the shot
                        plugin.bar(p, "Vulcan's Wrath ARMED - your next shot explodes!", NamedTextColor.RED);
                        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 0.8f);
                    }
                }
                return;
            }
            case EARTH_GAUNTLET -> { // right-click pulls a mudded enemy instead of charging a punch
                if (!crouch && pullMudded(p)) {
                    e.setCancelled(true);
                    return;
                }
            }
            default -> {
            }
        }
        e.setCancelled(true);
        useAbility(p, hand, d, crouch);
    }

    private void useAbility(Player p, ItemStack hand, WDef d, boolean crouch) {
        switch (d) {
            case HYPERION -> {
                if (crouch) { // Holy Lance
                    if (!ready(p, d, 2, 60)) return;
                    Location eye = p.getEyeLocation();
                    Location end = eye.clone().add(eye.getDirection().multiply(24));
                    for (double s = 2; s <= 24; s += 1.5) {
                        Location at = eye.clone().add(eye.getDirection().multiply(s));
                        at.getWorld().spawnParticle(Particle.GLOW, at, 3, 0.1, 0.1, 0.1, 0.01);
                        at.getWorld().spawnParticle(Particle.END_ROD, at, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                    for (LivingEntity t : foesNear(p, end, 3.5)) {
                        t.damage(6.0, p);
                        t.setFireTicks(60);
                    }
                    p.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.6f);
                } else { // Scorching Blade
                    if (!ready(p, d, 1, 30)) return;
                    launchPoint(p, 22, 1.4, Particle.FLAME, 1.4, false, t -> {
                        t.damage(7.5, p);
                        t.setFireTicks(80);
                        t.getWorld().spawnParticle(Particle.FLAME, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                    });
                    p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                }
            }
            case NIGHTPIERCER -> {
                if (crouch) crimsonBite(p, d);
                else { // Transformation: a bat-cloud burst in the direction you look
                    if (!ready(p, d, 1, 30)) return;
                    Vector dir = p.getEyeLocation().getDirection();
                    new org.bukkit.scheduler.BukkitRunnable() {
                        int t = 0;

                        @Override
                        public void run() {
                            if (!p.isOnline() || p.isDead() || t++ >= 40) {
                                cancel();
                                return;
                            }
                            p.setVelocity(dir.clone().multiply(1.15).setY(Math.max(0.12, dir.getY() * 1.15)));
                            p.setFallDistance(0f);
                            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 0.8, 0),
                                    8, 0.4, 0.3, 0.4, 0.02);
                            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 0.8, 0),
                                    3, 0.3, 0.3, 0.3, 0.02);
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                    p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.7f);
                }
            }
            case FROST_SCYTHE -> {
                if (crouch) { // Command of Ice: three shards, one after another
                    if (!ready(p, d, 2, 45)) return;
                    for (int i = 0; i < 3; i++) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!p.isOnline()) return;
                            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 3, 0),
                                    10, 0.3, 0.2, 0.3, 0.02);
                            launchPoint(p, 20, 1.6, Particle.SNOWFLAKE, 1.4, false, t -> {
                                t.damage(4.0, p);
                                t.setFreezeTicks(t.getMaxFreezeTicks());
                                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                            });
                        }, i * 8L);
                    }
                    p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
                } else { // Scythe Throw
                    if (!ready(p, d, 1, 30)) return;
                    launchPoint(p, 28, 1.7, Particle.SNOWFLAKE, 1.5, false, t -> {
                        t.damage(10.0, p);
                        t.setFreezeTicks(t.getMaxFreezeTicks());
                        if (t instanceof Player tp) tp.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                        else t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
                    });
                    p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.2f);
                }
            }
            case ILLUSION_WAND -> {
                String captured = capturedOf(hand);
                if (crouch) { // set the captured mob free
                    if (captured == null || captured.startsWith("player:")) {
                        plugin.bar(p, "No captured mob to free.", NamedTextColor.GRAY);
                        return;
                    }
                    try {
                        org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(captured);
                        Location at = p.getLocation().add(p.getLocation().getDirection().multiply(2)).add(0, 0.5, 0);
                        Entity freed = at.getWorld().spawnEntity(at, type);
                        if (freed instanceof Mob mob) mob.setTarget(null);
                        setCaptured(hand, null);
                        plugin.bar(p, "You set the " + captured.toLowerCase().replace('_', ' ') + " free.", NamedTextColor.LIGHT_PURPLE);
                    } catch (IllegalArgumentException ex) {
                        setCaptured(hand, null);
                    }
                } else {
                    if (captured == null) {
                        plugin.bar(p, "The wand is empty - kill a mob with it to capture its soul.", NamedTextColor.GRAY);
                        return;
                    }
                    if (!ready(p, d, 1, 8)) return;
                    unleashSoul(p, captured);
                }
            }
            case BONE_BLADE -> {
                if (crouch) { // Bone Cage
                    if (!ready(p, d, 2, 60)) return;
                    launchPoint(p, 20, 1.8, Particle.ASH, 1.3, false, t -> {
                        if (t instanceof Player tp) {
                            tp.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 10));
                            tp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128));
                            plugin.bar(tp, "Bone Cage! You are stunned!", NamedTextColor.WHITE);
                        } else {
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 10));
                        }
                        ring(t.getLocation(), Particle.ASH, 1.2, 14);
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_SKELETON_HURT, 1.2f, 0.7f);
                    });
                    p.playSound(p.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1f, 0.8f);
                } else { // Skeletal Leap
                    if (!ready(p, d, 1, 30)) return;
                    Vector v = p.getEyeLocation().getDirection().multiply(1.5);
                    v.setY(Math.max(0.45, v.getY() * 1.5));
                    p.setVelocity(v);
                    effect(p, PotionEffectType.SPEED, 80, 3);
                    p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.1f);
                }
            }
            case BLOODLUST -> {
                int kills = killsOf(hand);
                if (crouch) { // Blood Trail (3+ kills)
                    if (kills < 3) {
                        plugin.bar(p, "Blood Trail unlocks at 3 kills with Bloodlust.", NamedTextColor.GRAY);
                        return;
                    }
                    if (!ready(p, d, 2, 60)) return;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 0, false, false, false));
                    effect(p, PotionEffectType.SPEED, 300, 2);
                    plugin.bar(p, "You sink into the blood trail...", NamedTextColor.RED);
                    p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 0.7f);
                } else { // Blood Hook (5+ kills)
                    if (kills < 5) {
                        plugin.bar(p, "Blood Hook unlocks at 5 kills with Bloodlust.", NamedTextColor.GRAY);
                        return;
                    }
                    if (!ready(p, d, 1, 30)) return;
                    launchPoint(p, 15, 1.6, Particle.CRIMSON_SPORE, 1.3, false, t -> {
                        t.damage(4.0, p);
                        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                        if (pull.lengthSquared() > 1.0E-6) {
                            t.setVelocity(pull.normalize().multiply(2.0).setY(0.35));
                        }
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
                    });
                    // red dust colour for the chain
                    p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.9f);
                }
            }
            case SHADOW_BLADE -> {
                if (crouch) { // Shadow Leap
                    if (!ready(p, d, 2, 60)) return;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
                    p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.03);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!p.isOnline() || p.isDead()) return;
                        Location dest = p.getTargetBlockExact(15) != null
                                ? p.getTargetBlockExact(15).getLocation().add(0.5, 1, 0.5)
                                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(10));
                        dest.setDirection(p.getEyeLocation().getDirection());
                        p.teleport(dest);
                        p.getWorld().spawnParticle(Particle.SMOKE, dest.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.03);
                        p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.9f);
                    }, 15L);
                } else { // Shadow Daggers
                    if (!ready(p, d, 1, 60)) return;
                    for (int i = -1; i <= 1; i++) {
                        final double yawOff = i * 0.07;
                        launchPointYaw(p, yawOff, t -> {
                            t.damage(4.0, p);
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                            t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        });
                    }
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.3f);
                }
            }
            case CUTLASS -> {
                if (crouch) { // Parry
                    if (!ready(p, d, 2, 30)) return;
                    parryUntil.put(p.getUniqueId(), System.currentTimeMillis() + 7000L);
                    parryBlocks.put(p.getUniqueId(), 3);
                    plugin.bar(p, "Parry stance! You can block 3 hits.", NamedTextColor.YELLOW);
                    p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f);
                } else { // Thousand Cuts
                    if (!ready(p, d, 1, 30)) return;
                    new org.bukkit.scheduler.BukkitRunnable() {
                        int t = 0;

                        @Override
                        public void run() {
                            if (!p.isOnline() || p.isDead()) {
                                cancel();
                                return;
                            }
                            t++;
                            if (t <= 5) { // five fast slashes in front
                                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                                Location front = p.getLocation().add(p.getLocation().getDirection().multiply(2.2)).add(0, 1, 0);
                                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, front, 2, 0.4, 0.3, 0.4, 0);
                                for (LivingEntity target : foesNear(p, front, 1.9)) target.damage(2.5, p);
                            } else { // the lunge stab
                                Vector v = p.getEyeLocation().getDirection().multiply(1.3);
                                v.setY(0.15);
                                p.setVelocity(v);
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    if (!p.isOnline()) return;
                                    for (LivingEntity target : foesNear(p, p.getLocation().add(0, 1, 0), 2.4)) {
                                        target.damage(4.0, p);
                                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                                    }
                                }, 4L);
                                cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 2L);
                }
            }
            case PALADIN_AXE -> {
                if (crouch) { // Stalwart Absorption
                    if (!ready(p, d, 2, 30)) return;
                    absorbUntil.put(p.getUniqueId(), System.currentTimeMillis() + 5000L);
                    absorbed.put(p.getUniqueId(), 0.0);
                    effect(p, PotionEffectType.RESISTANCE, 100, 1);
                    plugin.bar(p, "Stalwart Absorption! Damage you take becomes your shockwave.", NamedTextColor.GOLD);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> releaseAbsorb(p), 100L);
                } else { // Earthshatter
                    if (!ready(p, d, 1, 45)) return;
                    Location at = p.getTargetBlockExact(20) != null
                            ? p.getTargetBlockExact(20).getLocation().add(0.5, 1, 0.5)
                            : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(12));
                    at.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 0.4, 0.3, 0.4, 0);
                    ring(at.clone().add(0, -1, 0), Particle.CRIT, 3.5, 18);
                    at.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.7f);
                    for (LivingEntity t : foesNear(p, at, 5)) {
                        if (t.isOnGround()) {
                            t.damage(8.0, p);
                            t.setVelocity(new Vector(0, 0.55, 0));
                        }
                    }
                }
            }
            case MINDWEAVER -> {
                if (!crouch) { // Mind Leap
                    if (!ready(p, d, 1, 15)) return;
                    Vector v = p.getEyeLocation().getDirection().multiply(1.6);
                    v.setY(Math.max(0.4, v.getY() * 1.6));
                    p.setVelocity(v);
                    effect(p, PotionEffectType.SPEED, 40, 4);
                    p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.3f);
                } else {
                    plugin.bar(p, "Hold crouch to charge the Gust, then let go!", NamedTextColor.LIGHT_PURPLE);
                }
            }
            case PURE_BLADE -> {
                if (crouch) { // Cyclone Slash
                    if (!ready(p, d, 2, 30)) return;
                    Location c = p.getLocation();
                    for (int dx = -4; dx <= 4; dx++) {
                        for (int dz = -4; dz <= 4; dz++) {
                            org.bukkit.block.Block b = c.clone().add(dx, 0, dz).getBlock();
                            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
                            org.bukkit.block.Block b2 = c.clone().add(dx, 1, dz).getBlock();
                            if (b2.getType() == Material.COBWEB) b2.setType(Material.AIR, false);
                        }
                    }
                    for (LivingEntity t : foesNear(p, c.clone().add(0, 1, 0), 4)) t.damage(6.0, p);
                    effect(p, PotionEffectType.SPEED, 40, 3);
                    ring(c, Particle.SWEEP_ATTACK, 2.5, 16);
                    p.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.8f);
                } else { // Shade Soul
                    if (!ready(p, d, 1, 45)) return;
                    launchPoint(p, 25, 1.7, Particle.SQUID_INK, 1.5, true, t -> {
                        t.damage(6.0, p);
                        t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                    });
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.8f);
                }
            }
            case EARTH_GAUNTLET -> {
                if (crouch) { // Mudslide
                    if (!ready(p, d, 2, 45)) return;
                    launchPoint(p, 18, 1.3, Particle.SMOKE, 1.4, false, t -> {
                        muddedBy.put(t.getUniqueId(), p.getUniqueId());
                        muddedUntil.put(t.getUniqueId(), System.currentTimeMillis() + 4000L);
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        if (t instanceof Player tp) plugin.bar(tp, "You are MUDDED!", NamedTextColor.GOLD);
                        plugin.bar(p, "Mudded! Right-click in the next 4s to pull them to you.", NamedTextColor.GOLD);
                    });
                    p.playSound(p.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 0.8f);
                } else { // Meteor Strike: charge the next punch
                    if (!ready(p, d, 1, 30)) return;
                    meteorUntil.put(p.getUniqueId(), System.currentTimeMillis() + 5000L);
                    plugin.bar(p, "Meteor Strike charged! Your next punch in 5s hits like a meteor.", NamedTextColor.GOLD);
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
                }
            }
            case WITHER_REAPER -> {
                if (crouch) { // Wither Leap: up to 3 rams in a short window
                    long now = System.currentTimeMillis();
                    if (leapWindow.getOrDefault(p.getUniqueId(), 0L) < now) {
                        if (!ready(p, d, 2, 15)) return;
                        leapCharges.put(p.getUniqueId(), 3);
                        leapWindow.put(p.getUniqueId(), now + 6000L);
                        leapHit.put(p.getUniqueId(), new HashSet<>());
                    }
                    int left = leapCharges.getOrDefault(p.getUniqueId(), 0);
                    if (left <= 0) {
                        plugin.bar(p, "No leaps left - wait for the cooldown.", NamedTextColor.GRAY);
                        return;
                    }
                    leapCharges.put(p.getUniqueId(), left - 1);
                    Vector v = p.getEyeLocation().getDirection().multiply(1.35);
                    v.setY(Math.max(0.5, v.getY() * 1.35));
                    p.setVelocity(v);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.2f);
                } else { // Wither Cleansing
                    int meter = witherMeter.getOrDefault(p.getUniqueId(), 0);
                    if (meter < 30) {
                        plugin.bar(p, "Your wither meter is too low to cleanse (" + meter + "/100).", NamedTextColor.GRAY);
                        return;
                    }
                    if (!ready(p, d, 1, 30)) return;
                    witherMeter.put(p.getUniqueId(), 0);
                    for (LivingEntity t : foesNear(p, p.getLocation().add(0, 1, 0), 6)) {
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        t.damage(3.0, p);
                    }
                    p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 60, 1.5, 0.8, 1.5, 0.1);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.8f);
                    plugin.bar(p, "Cleansed! Wither meter emptied.", NamedTextColor.DARK_PURPLE);
                }
            }
            default -> {
            }
        }
    }

    /** Crimson Bite: enemies in front lose 2 hearts of max health for 10s; you gain them. */
    private void crimsonBite(Player p, WDef d) {
        if (!ready(p, d, 2, 60)) return;
        Location front = p.getLocation().add(p.getLocation().getDirection().multiply(2.4)).add(0, 1, 0);
        boolean stole = false;
        for (LivingEntity t : foesNear(p, front, 2.2)) {
            AttributeInstance tMax = t.getAttribute(Attribute.MAX_HEALTH);
            AttributeInstance pMax = p.getAttribute(Attribute.MAX_HEALTH);
            if (tMax == null || pMax == null || tMax.getValue() <= 6) continue;
            NamespacedKey drainK = new NamespacedKey(plugin, "bite_drain_" + p.getUniqueId());
            NamespacedKey gainK = new NamespacedKey(plugin, "bite_gain_" + t.getUniqueId());
            tMax.addModifier(new AttributeModifier(drainK, -4.0, AttributeModifier.Operation.ADD_NUMBER));
            pMax.addModifier(new AttributeModifier(gainK, 4.0, AttributeModifier.Operation.ADD_NUMBER));
            if (t.getHealth() > tMax.getValue()) t.setHealth(tMax.getValue());
            p.setHealth(Math.min(pMax.getValue(), p.getHealth() + 4.0));
            UUID tid = t.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Entity e1 = Bukkit.getEntity(tid);
                if (e1 instanceof LivingEntity le && le.getAttribute(Attribute.MAX_HEALTH) != null) {
                    var inst = le.getAttribute(Attribute.MAX_HEALTH);
                    for (AttributeModifier mod : new ArrayList<>(inst.getModifiers())) {
                        if (mod.getKey().equals(drainK)) inst.removeModifier(mod);
                    }
                }
                var inst2 = p.getAttribute(Attribute.MAX_HEALTH);
                if (inst2 != null) {
                    for (AttributeModifier mod : new ArrayList<>(inst2.getModifiers())) {
                        if (mod.getKey().equals(gainK)) inst2.removeModifier(mod);
                    }
                    if (p.isOnline() && p.getHealth() > inst2.getValue()) p.setHealth(inst2.getValue());
                }
            }, 200L);
            stole = true;
            t.getWorld().spawnParticle(Particle.DUST, t.getLocation().add(0, 1.2, 0), 15, 0.3, 0.4, 0.3, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(160, 0, 0), 1.2f));
        }
        if (stole) {
            plugin.bar(p, "Crimson Bite! You hold their hearts for 10s.", NamedTextColor.DARK_RED);
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8f, 0.7f);
        } else {
            cooldowns.remove(p.getUniqueId() + ":" + d.id + ":2"); // nothing bitten: no cooldown spent
            plugin.bar(p, "No one in front of you to bite.", NamedTextColor.GRAY);
        }
    }

    private void releaseAbsorb(Player p) {
        absorbUntil.remove(p.getUniqueId());
        double stored = absorbed.getOrDefault(p.getUniqueId(), 0.0);
        absorbed.remove(p.getUniqueId());
        if (!p.isOnline() || p.isDead() || stored <= 0) return;
        double dmg = Math.min(20.0, stored * 0.75);
        Location c = p.getLocation().add(0, 1, 0);
        for (LivingEntity t : foesNear(p, c, 6)) {
            t.damage(dmg, p);
            Vector away = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) t.setVelocity(away.normalize().multiply(1.1).setY(0.4));
        }
        ring(p.getLocation(), Particle.EXPLOSION, 4, 16);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        plugin.bar(p, "Stalwart shockwave: " + (Math.round(dmg * 10) / 10.0) + " damage!", NamedTextColor.GOLD);
    }

    private boolean pullMudded(Player p) {
        UUID found = null;
        for (Map.Entry<UUID, UUID> en : muddedBy.entrySet()) {
            if (!en.getValue().equals(p.getUniqueId())) continue;
            if (muddedUntil.getOrDefault(en.getKey(), 0L) < System.currentTimeMillis()) continue;
            found = en.getKey();
            break;
        }
        if (found == null) return false;
        Entity e = Bukkit.getEntity(found);
        if (!(e instanceof LivingEntity t) || !t.isValid()) return false;
        Vector pull = p.getLocation().add(0, 0.5, 0).toVector().subtract(t.getLocation().toVector());
        if (pull.lengthSquared() > 1.0E-6) t.setVelocity(pull.normalize().multiply(2.2).setY(0.3));
        muddedBy.remove(found);
        muddedUntil.remove(found);
        plugin.bar(p, "Pulled!", NamedTextColor.GOLD);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
        return true;
    }

    private void launchPointYaw(Player p, double yawOffset, java.util.function.Consumer<LivingEntity> onHit) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().clone();
        double cos = Math.cos(yawOffset), sin = Math.sin(yawOffset);
        Vector spread = new Vector(dir.getX() * cos - dir.getZ() * sin, dir.getY(),
                dir.getX() * sin + dir.getZ() * cos).normalize();
        Location start = eye.clone();
        new org.bukkit.scheduler.BukkitRunnable() {
            Location cur = start.clone().add(spread.clone().multiply(1.2));
            double travelled = 0;

            @Override
            public void run() {
                for (int step = 0; step < 2; step++) {
                    cur.add(spread.clone().multiply(0.85));
                    travelled += 0.85;
                    cur.getWorld().spawnParticle(Particle.SMOKE, cur, 2, 0.06, 0.06, 0.06, 0.01);
                    if (cur.getBlock().getType().isSolid()) {
                        cancel();
                        return;
                    }
                    for (LivingEntity le : cur.getWorld().getNearbyLivingEntities(cur, 1.3)) {
                        if (!foe(p, le)) continue;
                        onHit.accept(le);
                        cancel();
                        return;
                    }
                    if (travelled >= 22) {
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String capturedOf(ItemStack hand) {
        if (hand == null || !hand.hasItemMeta()) return null;
        return hand.getItemMeta().getPersistentDataContainer().get(capturedKey, PersistentDataType.STRING);
    }

    private void setCaptured(ItemStack hand, String value) {
        ItemMeta m = hand.getItemMeta();
        if (value == null) m.getPersistentDataContainer().remove(capturedKey);
        else m.getPersistentDataContainer().set(capturedKey, PersistentDataType.STRING, value);
        WDef d = defOf(hand);
        if (d != null) {
            String label = value == null ? null
                    : value.startsWith("player:") ? value.substring(7)
                    : value.toLowerCase().replace('_', ' ');
            m.lore(loreFor(d, killsOf(hand), label));
        }
        hand.setItemMeta(m);
    }

    private void unleashSoul(Player p, String captured) {
        if (captured.startsWith("player:")) { // a player's soul: slip away like them
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false, false));
            effect(p, PotionEffectType.SPEED, 100, 2);
            plugin.bar(p, "You slip away like " + captured.substring(7) + "...", NamedTextColor.LIGHT_PURPLE);
            return;
        }
        LivingEntity target = null;
        double bd = 24 * 24;
        for (LivingEntity le : foesNear(p, p.getLocation(), 24)) {
            double dd = le.getLocation().distanceSquared(p.getLocation());
            if (dd < bd) {
                bd = dd;
                target = le;
            }
        }
        switch (captured) {
            case "CREEPER" -> {
                Location at = target != null ? target.getLocation() : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(8));
                at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0.2, 0.2, 0.2, 0);
                at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
                for (LivingEntity t : foesNear(p, at, 3.5)) t.damage(8.0, p);
            }
            case "BLAZE" -> {
                if (target != null) {
                    for (int i = 0; i < 3; i++) {
                        final LivingEntity tt = target;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!p.isOnline() || !tt.isValid()) return;
                            Vector dir = tt.getLocation().add(0, 1, 0).toVector().subtract(p.getEyeLocation().toVector());
                            if (dir.lengthSquared() > 1.0E-6) p.launchProjectile(org.bukkit.entity.SmallFireball.class, dir.normalize());
                        }, i * 5L);
                    }
                }
            }
            case "SPIDER", "CAVE_SPIDER" -> {
                if (target != null) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    org.bukkit.block.Block b = target.getLocation().getBlock();
                    Material old = b.getType();
                    if (old == Material.AIR) {
                        b.setType(Material.COBWEB, false);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
                        }, 80L);
                    }
                }
            }
            case "ENDERMAN" -> {
                Location dest = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(10));
                dest.setY(Math.max(dest.getY(), p.getLocation().getY()));
                p.teleport(dest);
                p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
            default -> {
                if (target != null) {
                    target.damage(6.0, p);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                    target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                } else {
                    plugin.bar(p, "No enemy in range for the soul burst.", NamedTextColor.GRAY);
                }
            }
        }
    }

    // ------------------------------------------------------------ shooting, damage, deaths

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        WDef d = defOf(e.getBow());
        if (d != WDef.VULCANS_CROSSBOW || !(e.getProjectile() instanceof Arrow main)) return;
        main.getPersistentDataContainer().set(vulcanArrowKey, PersistentDataType.STRING, "main");
        if (wrathArmed.remove(p.getUniqueId())) { // the Wrath shot: one exploding fireball
            e.setCancelled(true);
            cooldowns.put(p.getUniqueId() + ":" + d.id + ":1", System.currentTimeMillis() + 45_000L);
            Vector dir = p.getEyeLocation().getDirection();
            Fireball fb = p.launchProjectile(Fireball.class, dir.clone().multiply(1.2));
            fb.setDirection(dir);
            fb.setYield(0f);
            fb.setIsIncendiary(false);
            wrathShots.add(fb.getUniqueId());
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.8f);
            return;
        }
        // two extra tightly packed arrows either side of the real one
        for (double off : new double[]{-0.055, 0.055}) {
            Vector v = main.getVelocity().clone();
            double cos = Math.cos(off), sin = Math.sin(off);
            Vector spread = new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
            Arrow extra = p.getWorld().spawn(main.getLocation(), Arrow.class);
            extra.setVelocity(spread);
            extra.setShooter(p);
            extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            extra.getPersistentDataContainer().set(vulcanArrowKey, PersistentDataType.STRING, "extra");
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!wrathShots.remove(e.getEntity().getUniqueId())) return;
        Location at = e.getEntity().getLocation();
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0.3, 0.3, 0.3, 0);
        at.getWorld().spawnParticle(Particle.FLAME, at, 40, 1.2, 1.2, 1.2, 0.08);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
        if (e.getEntity().getShooter() instanceof Player p) {
            for (LivingEntity t : foesNear(p, at, 3)) {
                t.damage(10.0, p);
                t.setFireTicks(60);
            }
        }
        e.getEntity().remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        // Vulcan arrows ignite (the main arrow); extra arrows are marked too but only main ignites
        if (e.getDamager() instanceof Arrow arrow
                && "main".equals(arrow.getPersistentDataContainer().get(vulcanArrowKey, PersistentDataType.STRING))) {
            e.getEntity().setFireTicks(Math.max(e.getEntity().getFireTicks(), 60));
        }
        if (!(e.getEntity() instanceof Player victim)) return;

        // Parry: block up to 3 hits, knock the attacker back
        Long pu = parryUntil.get(victim.getUniqueId());
        if (pu != null && pu > System.currentTimeMillis()
                && defOf(victim.getInventory().getItemInMainHand()) == WDef.CUTLASS) {
            int blocks = parryBlocks.getOrDefault(victim.getUniqueId(), 0);
            if (blocks > 0) {
                e.setCancelled(true);
                parryBlocks.put(victim.getUniqueId(), blocks - 1);
                if (e.getDamager() instanceof LivingEntity attacker) {
                    Vector away = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 1.0E-6) attacker.setVelocity(away.normalize().multiply(1.2).setY(0.35));
                }
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                plugin.bar(victim, "Parried! (" + (blocks - 1) + " blocks left)", NamedTextColor.YELLOW);
                return;
            }
        }
        // Stalwart Absorption: most damage is soaked up instead of taken
        Long au = absorbUntil.get(victim.getUniqueId());
        if (au != null && au > System.currentTimeMillis()
                && defOf(victim.getInventory().getItemInMainHand()) == WDef.PALADIN_AXE) {
            absorbed.merge(victim.getUniqueId(), e.getDamage() * 0.8, Double::sum);
            e.setDamage(e.getDamage() * 0.2);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        WDef d = defOf(hand);
        if (d == null || !(e.getEntity() instanceof LivingEntity target)) return;
        switch (d) {
            case BLOODLUST -> { // Infection: a chance to make them bleed
                if (killsOf(hand) >= 0 && random.nextDouble() < 0.15) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
                }
            }
            case EARTH_GAUNTLET -> { // Meteor Strike punch
                Long mu = meteorUntil.get(p.getUniqueId());
                if (mu != null && mu > System.currentTimeMillis()) {
                    meteorUntil.remove(p.getUniqueId());
                    e.setDamage(e.getDamage() + 8.0);
                    Vector away = target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 1.0E-6) target.setVelocity(away.normalize().multiply(1.8).setY(0.5));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
                    plugin.bar(p, "METEOR STRIKE!", NamedTextColor.GOLD);
                }
            }
            case WITHER_REAPER -> { // damage builds the meter
                int meter = Math.min(100, witherMeter.getOrDefault(p.getUniqueId(), 0) + (int) Math.ceil(e.getDamage() * 2));
                witherMeter.put(p.getUniqueId(), meter);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        ItemStack hand = killer.getInventory().getItemInMainHand();
        WDef d = defOf(hand);
        if (d == WDef.BLOODLUST) {
            int kills = killsOf(hand) + 1;
            ItemMeta m = hand.getItemMeta();
            m.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
            m.lore(loreFor(d, kills, null));
            hand.setItemMeta(m);
            plugin.bar(killer, "Bloodlust kills: " + kills, NamedTextColor.RED);
        }
        if (d == WDef.ILLUSION_WAND && !(e.getEntity() instanceof Player)) {
            setCaptured(hand, e.getEntity().getType().name());
            plugin.bar(killer, "The wand captured a " + e.getEntity().getType().name().toLowerCase().replace('_', ' ') + "!",
                    NamedTextColor.LIGHT_PURPLE);
        }
        if (d == WDef.ILLUSION_WAND && e.getEntity() instanceof Player dead) {
            setCaptured(hand, "player:" + dead.getName());
            plugin.bar(killer, "The wand captured " + dead.getName() + "'s soul!", NamedTextColor.LIGHT_PURPLE);
        }
        if (d == WDef.WITHER_REAPER) {
            witherMeter.put(killer.getUniqueId(),
                    Math.min(100, witherMeter.getOrDefault(killer.getUniqueId(), 0) + 15));
        }
    }

    /** Mindweaver Gust: charge by crouching, release by standing up. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (defOf(p.getInventory().getItemInMainHand()) != WDef.MINDWEAVER) return;
        if (e.isSneaking()) {
            chargeStart.put(p.getUniqueId(), System.currentTimeMillis());
        } else {
            Long start = chargeStart.remove(p.getUniqueId());
            if (start == null) return;
            long heldMs = System.currentTimeMillis() - start;
            if (heldMs < 500) return;
            if (!peekReady(p, WDef.MINDWEAVER, 2)) {
                plugin.bar(p, "Mindweaver Gust is still cooling down.", NamedTextColor.RED);
                return;
            }
            int cd = 15 + random.nextInt(16);
            cooldowns.put(p.getUniqueId() + ":" + WDef.MINDWEAVER.id + ":2",
                    System.currentTimeMillis() + cd * 1000L);
            double power = Math.min(2.6, 0.8 + heldMs / 1000.0);
            double radius = 3.5 + power * 1.5;
            for (LivingEntity t : foesNear(p, p.getLocation().add(0, 1, 0), radius)) {
                Vector away = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 1.0E-6) t.setVelocity(away.normalize().multiply(power).setY(0.45));
                t.damage(2.0, p);
            }
            ring(p.getLocation(), Particle.CLOUD, radius * 0.7, 20);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.3f, 0.8f);
            plugin.bar(p, "Mindweaver Gust! (cooldown " + cd + "s)", NamedTextColor.LIGHT_PURPLE);
        }
    }

    // ------------------------------------------------------------ passives, once a second

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            WDef d = defOf(hand);
            if (d != null) {
                switch (d) {
                    case HYPERION -> effect(p, PotionEffectType.FIRE_RESISTANCE, 70, 1);
                    case SHADOW_BLADE -> effect(p, PotionEffectType.SPEED, 70, 1);
                    case PALADIN_AXE -> effect(p, PotionEffectType.HASTE, 70, 1);
                    case NIGHTPIERCER -> {
                        long time = p.getWorld().getTime();
                        if (time >= 13000 && time <= 23000) effect(p, PotionEffectType.REGENERATION, 70, 1);
                    }
                    case BLOODLUST -> {
                        int kills = killsOf(hand);
                        if (kills >= 1) effect(p, PotionEffectType.SPEED, 70, 2);
                        if (kills >= 4) effect(p, PotionEffectType.STRENGTH, 70, 1);
                        if (kills >= 2) { // Blood Tracker: only you see the trail to nearby players
                            for (Player other : p.getWorld().getPlayers()) {
                                if (other.equals(p) || other.getLocation().distanceSquared(p.getLocation()) > 900) continue;
                                p.spawnParticle(Particle.DUST, other.getLocation().add(0, 1.6, 0), 2, 0.1, 0.1, 0.1, 0,
                                        new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 0), 1.4f));
                            }
                        }
                    }
                    case WITHER_REAPER -> {
                        int meter = witherMeter.getOrDefault(p.getUniqueId(), 0);
                        if (meter >= 100) { // the meter overflows: the wither punishes YOU
                            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                            witherMeter.put(p.getUniqueId(), 40);
                            plugin.bar(p, "The wither overflows! Cleanse it (right-click) before it fills again!",
                                    NamedTextColor.DARK_PURPLE);
                        } else if (meter >= 70) {
                            plugin.bar(p, "Wither meter: " + meter + "/100 - cleanse soon!", NamedTextColor.DARK_PURPLE);
                        }
                        // ramming: while a leap window is open, touching an enemy hurts them
                        if (leapWindow.getOrDefault(p.getUniqueId(), 0L) > now) {
                            Set<UUID> hit = leapHit.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
                            for (LivingEntity t : foesNear(p, p.getLocation().add(0, 1, 0), 1.8)) {
                                if (hit.add(t.getUniqueId())) {
                                    t.damage(4.0, p);
                                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
                                }
                            }
                        }
                    }
                    case CUTLASS -> {
                        Long pu = parryUntil.get(p.getUniqueId());
                        if (pu != null && pu > now) effect(p, PotionEffectType.SLOWNESS, 30, 1);
                    }
                    default -> {
                    }
                }
                // Mindweaver charge feedback
                if (d == WDef.MINDWEAVER && p.isSneaking() && chargeStart.containsKey(p.getUniqueId())) {
                    long heldMs = now - chargeStart.get(p.getUniqueId());
                    plugin.bar(p, "Charging Gust... " + Math.min(100, (int) (heldMs / 20)) + "%", NamedTextColor.LIGHT_PURPLE);
                }
            }
            // mudded targets stay slowed until the mud dries
            Long mu = muddedUntil.get(p.getUniqueId());
            if (mu != null) {
                if (mu > now) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                } else {
                    muddedUntil.remove(p.getUniqueId());
                    muddedBy.remove(p.getUniqueId());
                }
            }
        }
        // mudded mobs too
        for (Map.Entry<UUID, Long> en : new ArrayList<>(muddedUntil.entrySet())) {
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent instanceof LivingEntity le && !(ent instanceof Player)) {
                if (en.getValue() > now) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                else {
                    muddedUntil.remove(en.getKey());
                    muddedBy.remove(en.getKey());
                }
            }
        }
    }

    /** /weapons: the list of legendary weapons and where to get them. */
    void info(Player p) {
        p.sendMessage(Component.text("===== ⚔ LEGENDARY WEAPONS ⚔ =====", NamedTextColor.DARK_PURPLE));
        for (WDef d : WDef.values()) {
            p.sendMessage(Component.text(" ✦ " + d.display, d.color)
                    .append(Component.text(" - " + d.price(plugin) + " Unbound Coins in /itemshop (always in stock)",
                            NamedTextColor.GRAY)));
        }
        p.sendMessage(Component.text("Right-click = ability 1, crouch + right-click = ability 2.", NamedTextColor.YELLOW));
    }
}
