package me.abilitysmp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Galaxy Bosses (v12): the Galactic Update replaced the old bosses (the Headless Horseman,
 * the Mimic and the Ghost are gone). Six galaxy bosses live here now. Two of them are
 * PERMANENT - Notazombiemc and the Astronout will stay in the game forever. The other four
 * (Comet Knight, Solar Titan, Void Serpent, Eclipse Reaper) are seasonal: they leave the
 * galaxy on the date in config (galaxy-bosses.seasonal-until).
 *
 * Fights work like they always have: your own arena copy per fight, a Fight Totem in your
 * off hand (it pops = you lose, you never die), a boss party friend can join, and victory
 * pays Unbound Coins, Galaxy Coins, crate loot and quest progress.
 */
final class GalaxyBosses implements Listener {

    enum Def {
        NOTAZOMBIE("notazombiemc", "Notazombiemc", NamedTextColor.GREEN, false,
                160, 6, 0.30, 1.5, 40, 25, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Particle.HAPPY_VILLAGER),
        ASTRONOUT("astronout", "Astronout", NamedTextColor.LIGHT_PURPLE, false,
                360, 8, 0.27, 1.3, 120, 50, Material.SMOOTH_QUARTZ, Material.QUARTZ_BRICKS, Particle.END_ROD),
        COMET_KNIGHT("comet-knight", "Comet Knight", NamedTextColor.AQUA, true,
                200, 7, 0.29, 1.35, 50, 30, Material.PACKED_ICE, Material.BLUE_ICE, Particle.SNOWFLAKE),
        SOLAR_TITAN("solar-titan", "Solar Titan", NamedTextColor.GOLD, true,
                280, 9, 0.24, 2.0, 80, 40, Material.BLACKSTONE, Material.MAGMA_BLOCK, Particle.FLAME),
        VOID_SERPENT("void-serpent", "Void Serpent", NamedTextColor.DARK_PURPLE, true,
                240, 8, 0.34, 1.2, 70, 35, Material.DEEPSLATE_BRICKS, Material.PURPUR_BLOCK, Particle.REVERSE_PORTAL),
        ECLIPSE_REAPER("eclipse-reaper", "Eclipse Reaper", NamedTextColor.DARK_RED, true,
                450, 10, 0.28, 1.6, 150, 60, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRYING_OBSIDIAN, Particle.SQUID_INK);

        final String id;
        final String display;
        final NamedTextColor color;
        final boolean seasonal;
        final double health;
        final double damage;
        final double speed;
        final double scale;
        final int coins;
        final int points;
        final Material floor;
        final Material trim;
        final Particle particle;

        Def(String id, String display, NamedTextColor color, boolean seasonal, double health, double damage,
            double speed, double scale, int coins, int points, Material floor, Material trim, Particle particle) {
            this.id = id;
            this.display = display;
            this.color = color;
            this.seasonal = seasonal;
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.scale = scale;
            this.coins = coins;
            this.points = points;
            this.floor = floor;
            this.trim = trim;
            this.particle = particle;
        }

        static Def byText(String text) {
            String t = text.toLowerCase(Locale.ROOT).replace(' ', '-');
            for (Def d : values()) {
                if (d.id.equals(t) || d.display.toLowerCase(Locale.ROOT).replace(' ', '-').equals(t)
                        || t.contains(d.id) || d.id.contains(t)) return d;
            }
            return null;
        }
    }

    private static final class Fight {
        Def def;
        int slot;
        Location center;
        final List<Player> players = new ArrayList<>();
        final Map<UUID, Location> backs = new HashMap<>();
        final Set<UUID> out = new HashSet<>();
        final List<LivingEntity> minions = new ArrayList<>();
        LivingEntity boss;
        BossBar bar;
        int phase = 1;
        int tick;
        boolean ending;

        List<Player> active() {
            List<Player> out2 = new ArrayList<>();
            for (Player p : players) if (!out.contains(p.getUniqueId()) && p.isOnline()) out2.add(p);
            return out2;
        }

        Player nearestTo(LivingEntity from) {
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player p : active()) {
                if (p.getWorld() != from.getWorld()) continue;
                double d = p.getLocation().distanceSquared(from.getLocation());
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            return best;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey bossKey;
    private final Random random = new Random();
    private final Map<UUID, Fight> fights = new HashMap<>();
    private final Set<String> usedSlots = new HashSet<>();

    GalaxyBosses(AbilitySMP plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "galaxy_boss");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 1L);
    }

    boolean isBusy(Player p) {
        return fights.containsKey(p.getUniqueId());
    }

    /** Seasonal bosses are gone once the config date passes. */
    boolean seasonalOver() {
        try {
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("galaxy-bosses.timezone", "America/Toronto"));
            ZonedDateTime until = LocalDateTime.parse(
                    plugin.getConfig().getString("galaxy-bosses.seasonal-until", "2026-11-30 23:59"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(zone);
            return ZonedDateTime.now(zone).isAfter(until);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean available(Def d) {
        return !d.seasonal || !seasonalOver();
    }

    private double health(Def d) {
        return plugin.getConfig().getDouble("galaxy-bosses." + d.id + ".health", d.health);
    }

    private int coins(Def d) {
        return plugin.getConfig().getInt("galaxy-bosses." + d.id + ".coins", d.coins);
    }

    private int points(Def d) {
        return plugin.getConfig().getInt("galaxy-bosses." + d.id + ".points", d.points);
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    // =====================================================================
    // Command: /boss, /boss fight <name>, /boss invite/accept/deny/leaveparty/leave
    // =====================================================================

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length >= 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> {
                    if (args.length >= 2) plugin.party.invite(p, args[1]);
                    else p.sendMessage(Component.text("Usage: /boss invite <player>", NamedTextColor.GRAY));
                    return;
                }
                case "accept" -> {
                    plugin.party.accept(p);
                    return;
                }
                case "deny", "decline" -> {
                    plugin.party.deny(p);
                    return;
                }
                case "leaveparty" -> {
                    plugin.party.leaveParty(p);
                    return;
                }
                case "leave" -> {
                    Fight f = fights.get(p.getUniqueId());
                    if (f != null) onPlayerOut(f, p, false);
                    else p.sendMessage(Component.text("You are not in a boss fight.", NamedTextColor.GRAY));
                    return;
                }
                case "fight" -> {
                    if (args.length < 2) {
                        listBosses(p);
                        return;
                    }
                    Def d = Def.byText(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    if (d == null) {
                        p.sendMessage(Component.text("Unknown boss. Type /boss to see the list.", NamedTextColor.RED));
                        return;
                    }
                    start(p, d);
                    return;
                }
                default -> {
                }
            }
        }
        listBosses(p);
    }

    private void listBosses(Player p) {
        p.sendMessage(Component.text("===== 🌌 GALAXY BOSSES 🌌 =====", NamedTextColor.DARK_PURPLE));
        for (Def d : Def.values()) {
            if (!available(d)) {
                p.sendMessage(Component.text(" ✦ " + d.display + " - has left the galaxy (season over)", NamedTextColor.GRAY));
                continue;
            }
            long cd = plugin.bossCooldownLeft(p, d.id);
            String tag = d.seasonal ? " (seasonal)" : " (stays forever)";
            String state = cd > 0 ? " - resting for " + AbilitySMP.spanShort(cd) : " - READY";
            p.sendMessage(Component.text(" ✦ " + d.display, d.color)
                    .append(Component.text(tag + state, NamedTextColor.GRAY)));
            p.sendMessage(Component.text("    /boss fight " + d.id + "  -  pays " + coins(d) + " Galaxy Coins", NamedTextColor.DARK_GRAY));
        }
        p.sendMessage(Component.text("Bring a friend: /boss invite <player>, they run /boss accept.", NamedTextColor.YELLOW));
    }

    // =====================================================================
    // Starting a fight (same shape the old bosses used)
    // =====================================================================

    private Location centerFor(Fight f) {
        World w = Bukkit.getWorlds().get(0);
        int cx = plugin.getConfig().getInt("galaxy-bosses.arena-x", 0) + f.slot
                * plugin.getConfig().getInt("boss.arena-slot-spacing", 100);
        int y = plugin.getConfig().getInt("galaxy-bosses.arena-y", 200);
        int cz = plugin.getConfig().getInt("galaxy-bosses.arena-z", 16000) + f.def.ordinal() * 4000;
        if (!Arenas.built(w, cx, y, cz)) {
            Arenas.build(w, cx, y, cz, 22, f.def.floor, f.def.trim, 14);
        }
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    private void start(Player host, Def def) {
        if (!available(def)) {
            host.sendMessage(Component.text(def.display + " has left the galaxy - that boss was only here for the season.",
                    NamedTextColor.GRAY));
            return;
        }
        if (isBusy(host) || plugin.duel.isBusy(host) || plugin.ranked.isBusy(host) || plugin.trading.isBusy(host)) {
            host.sendMessage(Component.text("Finish what you are doing first.", NamedTextColor.RED));
            return;
        }
        long cd = plugin.bossCooldownLeft(host, def.id);
        if (cd > 0) {
            host.sendMessage(Component.text(def.display + " needs to rest! You can fight again in "
                    + AbilitySMP.spanShort(cd) + ".", NamedTextColor.RED));
            return;
        }
        List<Player> fighters = new ArrayList<>();
        fighters.add(host);
        for (Player m : plugin.party.members(host)) {
            if (m.equals(host)) continue;
            if (isBusy(m) || plugin.duel.isBusy(m) || plugin.ranked.isBusy(m) || plugin.trading.isBusy(m)) {
                host.sendMessage(Component.text(m.getName() + " is busy and can't join this fight.", NamedTextColor.GRAY));
                continue;
            }
            fighters.add(m);
        }
        for (Player p : fighters) plugin.startBossCooldown(p, def.id);

        final Fight f = new Fight();
        f.def = def;
        int slot = 0;
        while (usedSlots.contains(def.id + ":" + slot)) slot++;
        usedSlots.add(def.id + ":" + slot);
        f.slot = slot;
        f.players.addAll(fighters);
        for (Player p : fighters) {
            f.backs.put(p.getUniqueId(), p.getLocation().clone());
            fights.put(p.getUniqueId(), f);
        }
        Location c = centerFor(f);
        f.center = c;

        for (Player p : fighters) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0);
            p.setFoodLevel(20);
            p.setFireTicks(0);
            int off = fighters.indexOf(p) * 3;
            p.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 14 + off, 180f, 0f));
            p.setPlayerTime(13000L, false);
            plugin.fightTotem.protect(p, def.display, popped -> onPlayerOut(f, popped, true));
            p.showTitle(Title.title(Component.text(def.display, def.color),
                    Component.text("The galaxy boss appears...", NamedTextColor.GRAY),
                    Title.Times.times(java.time.Duration.ofMillis(400), java.time.Duration.ofSeconds(4),
                            java.time.Duration.ofMillis(600))));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!f.ending && !f.active().isEmpty()) spawnBoss(f, c);
        }, 100L);
    }

    private static ItemStack leather(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(Color.fromRGB(rgb));
        it.setItemMeta(lm);
        return it;
    }

    private static ItemStack gear(Material m) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() != null) {
            var meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void equip(LivingEntity e, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots,
                       ItemStack hand) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) return;
        if (helmet != null) eq.setHelmet(helmet);
        if (chest != null) eq.setChestplate(chest);
        if (legs != null) eq.setLeggings(legs);
        if (boots != null) eq.setBoots(boots);
        if (hand != null) eq.setItemInMainHand(hand);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    private void spawnBoss(Fight f, Location c) {
        World w = c.getWorld();
        Location bl = c.clone().add(0, 1, -10);
        w.strikeLightningEffect(bl);
        Def d = f.def;
        double hp = health(d);
        LivingEntity boss;
        switch (d) {
            case NOTAZOMBIE -> {
                Zombie z = w.spawn(bl, Zombie.class);
                z.setShouldBurnInDay(false);
                equip(z, leather(Material.LEATHER_HELMET, 0x3FA34D), leather(Material.LEATHER_CHESTPLATE, 0x2E7D32),
                        leather(Material.LEATHER_LEGGINGS, 0x1B5E20), leather(Material.LEATHER_BOOTS, 0x3FA34D),
                        gear(Material.IRON_SWORD));
                boss = z;
            }
            case ASTRONOUT -> {
                Zombie z = w.spawn(bl, Zombie.class);
                z.setShouldBurnInDay(false);
                equip(z, new ItemStack(Material.GLASS), leather(Material.LEATHER_CHESTPLATE, 0xF2F2F2),
                        leather(Material.LEATHER_LEGGINGS, 0xD9D9D9), leather(Material.LEATHER_BOOTS, 0xB0B0B0),
                        gear(Material.TRIDENT));
                boss = z;
            }
            case COMET_KNIGHT -> {
                Zombie z = w.spawn(bl, Zombie.class);
                z.setShouldBurnInDay(false);
                equip(z, new ItemStack(Material.IRON_HELMET), new ItemStack(Material.IRON_CHESTPLATE),
                        new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_BOOTS),
                        gear(Material.IRON_SWORD));
                boss = z;
            }
            case SOLAR_TITAN -> {
                Zombie z = w.spawn(bl, Zombie.class);
                z.setShouldBurnInDay(false);
                z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                        PotionEffect.INFINITE_DURATION, 0, false, false, false));
                equip(z, new ItemStack(Material.GOLDEN_HELMET), new ItemStack(Material.GOLDEN_CHESTPLATE),
                        new ItemStack(Material.GOLDEN_LEGGINGS), new ItemStack(Material.GOLDEN_BOOTS),
                        gear(Material.GOLDEN_AXE));
                boss = z;
            }
            case VOID_SERPENT -> {
                WitherSkeleton ws = w.spawn(bl, WitherSkeleton.class);
                equip(ws, null, leather(Material.LEATHER_CHESTPLATE, 0x2A0A3A),
                        leather(Material.LEATHER_LEGGINGS, 0x1A0526), leather(Material.LEATHER_BOOTS, 0x2A0A3A),
                        gear(Material.NETHERITE_SWORD));
                boss = ws;
            }
            case ECLIPSE_REAPER -> {
                WitherSkeleton ws = w.spawn(bl, WitherSkeleton.class);
                equip(ws, leather(Material.LEATHER_HELMET, 0x101018), leather(Material.LEATHER_CHESTPLATE, 0x181828),
                        leather(Material.LEATHER_LEGGINGS, 0x101018), leather(Material.LEATHER_BOOTS, 0x181828),
                        gear(Material.NETHERITE_HOE));
                boss = ws;
            }
            default -> throw new IllegalStateException("no boss for " + d);
        }
        boss.setRemoveWhenFarAway(false);
        boss.setCanPickupItems(false);
        if (boss instanceof Zombie z) z.setCanBreakDoors(false);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, d.id);
        boss.customName(Component.text(d.display, d.color));
        boss.setCustomNameVisible(true);
        attr(boss, Attribute.MAX_HEALTH, hp);
        boss.setHealth(hp);
        attr(boss, Attribute.ATTACK_DAMAGE, d.damage);
        attr(boss, Attribute.MOVEMENT_SPEED, d.speed);
        attr(boss, Attribute.SCALE, d.scale);
        attr(boss, Attribute.KNOCKBACK_RESISTANCE, 0.8);
        attr(boss, Attribute.FOLLOW_RANGE, 60);
        f.boss = boss;
        if (boss instanceof Mob mob) {
            Player first = f.nearestTo(boss);
            if (first != null) mob.setTarget(first);
        }
        f.bar = BossBar.bossBar(Component.text(d.display, d.color), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        for (Player p : f.active()) p.showBossBar(f.bar);
        w.playSound(bl, Sound.ENTITY_WITHER_SPAWN, 1f, 0.7f);
    }

    // =====================================================================
    // The fight tick: phases + per-boss tricks
    // =====================================================================

    private void tickAll() {
        for (Fight f : new HashSet<>(fights.values())) tick(f);
    }

    private void tick(Fight f) {
        if (f.ending) return;
        if (f.active().isEmpty()) {
            end(f, false, false);
            return;
        }
        LivingEntity b = f.boss;
        if (b == null || !b.isValid()) return;
        for (Player p : new ArrayList<>(f.active())) {
            if (p.getWorld() != b.getWorld()) onPlayerOut(f, p, false);
        }
        Player p = f.nearestTo(b);
        if (p == null) return;
        f.tick++;
        double max = health(f.def);
        double hp = b.getHealth();
        int ph = hp > max * 0.6 ? 1 : hp > max * 0.3 ? 2 : 3;
        if (ph != f.phase) {
            f.phase = ph;
            onPhase(f);
        }
        if (f.bar != null) f.bar.progress((float) Math.max(0.0, Math.min(1.0, hp / max)));
        World w = b.getWorld();
        w.spawnParticle(f.def.particle, b.getLocation().add(0, b.getHeight() * 0.7, 0), 3, 0.3, 0.4, 0.3, 0.02);
        if (f.tick % 20 == 0 && b instanceof Mob mob) mob.setTarget(p);
        double dist = b.getLocation().distance(p.getLocation());
        int pace = f.phase == 3 ? 3 : f.phase == 2 ? 2 : 1; // later phases use tricks more often

        switch (f.def) {
            case NOTAZOMBIE -> {
                if (f.tick % (240 / pace) == 0) { // Zombie Rush: a burst of speed
                    b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2));
                    w.playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.2f, 0.8f);
                }
                if (f.tick % (300 / pace) == 0 && dist > 4) { // leap at the target
                    Vector v = p.getLocation().toVector().subtract(b.getLocation().toVector()).normalize();
                    v.setY(0.55);
                    b.setVelocity(v.multiply(0.9));
                }
                if (f.phase == 2 && f.tick == 1) { /* handled in onPhase */ }
            }
            case ASTRONOUT -> {
                if (f.tick % (160 / pace) == 0) { // moon hop: big floaty jump toward the target
                    Vector v = p.getLocation().toVector().subtract(b.getLocation().toVector()).setY(0).normalize();
                    v.setY(0.95);
                    b.setVelocity(v.multiply(0.55));
                    b.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 50, 0));
                }
                if (f.tick % (90 / pace) == 0) { // ray gun: a volley of small fireballs
                    for (int i = 0; i < 3; i++) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!b.isValid()) return;
                            Vector dir = p.getLocation().add(0, 1, 0).toVector()
                                    .subtract(b.getEyeLocation().toVector());
                            if (dir.lengthSquared() < 1.0E-6) return;
                            SmallFireball fb = b.launchProjectile(SmallFireball.class, dir.normalize());
                            fb.setIsIncendiary(false);
                        }, i * 6L);
                    }
                    w.playSound(b.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.4f);
                }
                if (f.tick % (400 / pace) == 0) { // Zero-G Beam: everybody floats up... then drops
                    for (Player t : f.active()) {
                        t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 2));
                        plugin.bar(t, "Zero gravity!", NamedTextColor.LIGHT_PURPLE);
                    }
                    w.playSound(b.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
                }
            }
            case COMET_KNIGHT -> {
                if (f.tick % (170 / pace) == 0 && dist > 3) { // comet dash
                    Vector v = p.getLocation().toVector().subtract(b.getLocation().toVector()).normalize();
                    v.setY(0.25);
                    b.setVelocity(v.multiply(1.6));
                    w.playSound(b.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.8f);
                }
                if (f.tick % 40 == 0) { // frostbite aura
                    for (Player t : f.active()) {
                        if (t.getLocation().distanceSquared(b.getLocation()) < 16) {
                            t.setFreezeTicks(Math.min(t.getMaxFreezeTicks(), t.getFreezeTicks() + 30));
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
                        }
                    }
                }
            }
            case SOLAR_TITAN -> {
                if (f.tick % (110 / pace) == 0) { // fireball volley
                    Vector dir = p.getLocation().add(0, 1, 0).toVector().subtract(b.getEyeLocation().toVector());
                    if (dir.lengthSquared() > 1.0E-6) {
                        SmallFireball fb = b.launchProjectile(SmallFireball.class, dir.normalize());
                        fb.setIsIncendiary(false);
                    }
                    w.playSound(b.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.1f, 0.7f);
                }
                if (f.tick % (340 / pace) == 0) { // solar flare: blinding light
                    for (Player t : f.active()) {
                        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
                        plugin.bar(t, "Solar flare!", NamedTextColor.GOLD);
                    }
                    w.playSound(b.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
                }
            }
            case VOID_SERPENT -> {
                if (f.tick % (150 / pace) == 0) { // ambush: teleport behind the target and strike
                    Location behind = p.getLocation().subtract(p.getLocation().getDirection().multiply(1.6));
                    behind.setY(p.getLocation().getY());
                    b.teleport(behind);
                    w.spawnParticle(Particle.REVERSE_PORTAL, behind.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.1);
                    w.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
                    p.damage(4.0, b);
                }
                if (f.tick % (420 / pace) == 0) { // vanish for a few seconds
                    b.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 70, 0, false, false, false));
                    b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 70, 1));
                }
            }
            case ECLIPSE_REAPER -> {
                if (f.tick % (200 / pace) == 0) { // darkness pulse
                    for (Player t : f.active()) {
                        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                    }
                    w.playSound(b.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 0.6f);
                }
                if (f.tick % (260 / pace) == 0) { // a wither skull at the target
                    Vector dir = p.getLocation().add(0, 1, 0).toVector().subtract(b.getEyeLocation().toVector());
                    if (dir.lengthSquared() > 1.0E-6) {
                        WitherSkull sk = b.launchProjectile(WitherSkull.class, dir.normalize());
                        sk.setCharged(false);
                    }
                }
            }
        }
    }

    private void onPhase(Fight f) {
        LivingEntity b = f.boss;
        if (b == null) return;
        if (f.phase == 2) {
            attr(b, Attribute.MOVEMENT_SPEED, f.def.speed * 1.15);
            attr(b, Attribute.ATTACK_DAMAGE, f.def.damage + 2);
            for (Player p : f.active()) {
                p.showTitle(Title.title(Component.text(f.def.display + " is ANGRY!", f.def.color), Component.empty(),
                        Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(2),
                                java.time.Duration.ofMillis(400))));
            }
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.7f);
            if (f.def == Def.NOTAZOMBIE) { // his fans rush in
                for (int i = 0; i < 4; i++) {
                    Location at = f.center.clone().add(random.nextInt(13) - 6, 1, random.nextInt(13) - 6);
                    Zombie fan = at.getWorld().spawn(at, Zombie.class);
                    fan.setBaby(true);
                    fan.setShouldBurnInDay(false);
                    fan.customName(Component.text("Notazombiemc Fan", NamedTextColor.GREEN));
                    fan.setCustomNameVisible(true);
                    attr(fan, Attribute.MOVEMENT_SPEED, 0.32);
                    Player t = f.nearestTo(fan);
                    if (t != null) fan.setTarget(t);
                    f.minions.add(fan);
                }
            }
            if (f.def == Def.ECLIPSE_REAPER) { // phantom escorts
                for (int i = 0; i < 2; i++) {
                    Location at = b.getLocation().add(random.nextInt(9) - 4, 6, random.nextInt(9) - 4);
                    Phantom ph = at.getWorld().spawn(at, Phantom.class);
                    ph.setSize(2);
                    Player t = f.nearestTo(ph);
                    if (t != null) ph.setTarget(t);
                    f.minions.add(ph);
                }
            }
        } else if (f.phase == 3) {
            attr(b, Attribute.MOVEMENT_SPEED, f.def.speed * 1.3);
            attr(b, Attribute.ATTACK_DAMAGE, f.def.damage + 4);
            for (Player p : f.active()) {
                p.showTitle(Title.title(Component.text("FINAL PHASE!", NamedTextColor.DARK_RED),
                        Component.text(f.def.display + " is going all out", NamedTextColor.RED),
                        Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(3),
                                java.time.Duration.ofMillis(400))));
            }
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
        }
    }

    // =====================================================================
    // Death, defeat, victory, cleanup (Fight Totem rules: nobody dies)
    // =====================================================================

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        for (Fight f : new HashSet<>(fights.values())) {
            if (f.boss != null && e.getEntity().getUniqueId().equals(f.boss.getUniqueId())) {
                e.getDrops().clear();
                e.setDroppedExp(0);
                victory(f);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Fight f = fights.get(e.getPlayer().getUniqueId());
        if (f == null) return;
        Location back = f.backs.get(e.getPlayer().getUniqueId());
        if (back != null) e.getPlayer().teleport(back);
        onPlayerOut(f, e.getPlayer(), false);
    }

    boolean leave(Player p) {
        Fight f = fights.get(p.getUniqueId());
        if (f == null) return false;
        onPlayerOut(f, p, false);
        return true;
    }

    private void onPlayerOut(Fight f, Player p, boolean popped) {
        if (f.ending || f.out.contains(p.getUniqueId())) return;
        f.out.add(p.getUniqueId());
        if (f.bar != null) p.hideBossBar(f.bar);
        plugin.fightTotem.cleanup(p);
        if (p.isOnline()) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0);
            p.setFireTicks(0);
            for (PotionEffect e : new HashSet<>(p.getActivePotionEffects())) {
                if (e.getType() == PotionEffectType.LEVITATION || e.getType() == PotionEffectType.BLINDNESS) {
                    p.removePotionEffect(e.getType());
                }
            }
            Location back = f.backs.get(p.getUniqueId());
            if (back != null) p.teleport(back);
            plugin.admin.applySky(p);
            if (popped) {
                p.showTitle(Title.title(Component.text("Defeated!", NamedTextColor.RED),
                        Component.text(f.def.display + " wins this time", NamedTextColor.GRAY),
                        Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(3),
                                java.time.Duration.ofMillis(400))));
            }
        }
        for (Player other : f.active()) {
            other.sendMessage(Component.text(p.getName() + " is out of the fight!", NamedTextColor.RED));
        }
        if (f.active().isEmpty()) end(f, false, false);
    }

    private void victory(Fight f) {
        if (f.ending) return;
        int pts = points(f.def);
        int coins = coins(f.def);
        List<String> names = new ArrayList<>();
        for (Player p : f.active()) {
            names.add(p.getName());
            plugin.unbound.addPoints(p, pts, f.def.display);
            plugin.shops.giveGalaxyCoins(p, coins);
            plugin.crates.rewardForBoss(p, f.def.id);
            Profile.Data d = plugin.profile.of(p);
            d.bossKills++;
            plugin.profile.save();
            plugin.unbound.addProgress(p, "BOSS_KILL", 1);
            plugin.ranks.evaluate(p);
            p.showTitle(Title.title(Component.text("VICTORY!", NamedTextColor.GREEN),
                    Component.text("+" + pts + " Unbound Coins, +" + coins + " Galaxy Coins", NamedTextColor.YELLOW),
                    Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(5),
                            java.time.Duration.ofMillis(800))));
        }
        Bukkit.broadcast(Component.text(String.join(" and ", names) + " defeated " + f.def.display + "!",
                NamedTextColor.GOLD));
        end(f, true, false);
    }

    private void end(Fight f, boolean win, boolean immediate) {
        if (f.ending) return;
        f.ending = true;
        for (Player p : f.players) fights.remove(p.getUniqueId());
        usedSlots.remove(f.def.id + ":" + f.slot);
        if (f.bar != null) for (Player p : f.players) p.hideBossBar(f.bar);
        for (LivingEntity m : f.minions) if (m.isValid()) m.remove();
        if (f.boss != null && f.boss.isValid()) f.boss.remove();

        List<Player> stillIn = f.active();
        Runnable back = () -> {
            for (Player p : stillIn) {
                plugin.fightTotem.cleanup(p);
                if (!p.isOnline()) continue;
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(max != null ? max.getValue() : 20.0);
                p.setFireTicks(0);
                Location b = f.backs.get(p.getUniqueId());
                if (b != null) p.teleport(b);
                plugin.admin.applySky(p);
            }
        };
        if (immediate) back.run();
        else Bukkit.getScheduler().runTaskLater(plugin, back, win ? 100L : 40L);
    }

    void shutdown() {
        for (Fight f : new HashSet<>(fights.values())) end(f, false, true);
        fights.clear();
    }
}
