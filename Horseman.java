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
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /boss fight Headless Horseman. The boss is an invisible armored zombie, so only his dark armor, held
 * pumpkin head and axe show, with fire burning at his neck, next to a glowing ghost horse.
 * Phase 1 gravestones. Phase 2 slashes, pumpkin heads and gravestones. Phase 3 (raging) adds fireballs,
 * Jack O' Lanterns, 10 zombies and double damage.
 *
 * Every fight now gets its OWN arena copy (nobody waits for the arena any more), a friend in your
 * boss party (/boss invite) joins you in it, and every fighter holds a Fight Totem: a lethal hit
 * pops the totem and knocks that player out instead of killing them (see FightTotem).
 */
final class Horseman implements Listener {

    private static final class Fight {
        final List<Player> players = new ArrayList<>();
        final Map<UUID, Location> backs = new HashMap<>();
        final Set<UUID> out = new HashSet<>();
        Location center;
        int slot;
        Zombie boss;
        ZombieHorse horse;
        BossBar bar;
        int phase = 1;
        long tick;
        boolean ending;
        final List<FallingBlock> shots = new ArrayList<>();
        final List<LivingEntity> minions = new ArrayList<>();
        final Map<Location, BlockData> old = new HashMap<>();

        /** Players still in the fight (not knocked out, still online). */
        List<Player> active() {
            List<Player> list = new ArrayList<>();
            for (Player p : players) if (!out.contains(p.getUniqueId()) && p.isOnline()) list.add(p);
            return list;
        }

        Player nearestTo(LivingEntity e) {
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player p : active()) {
                if (p.getWorld() != e.getWorld()) continue;
                double d = p.getLocation().distanceSquared(e.getLocation());
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
    private final NamespacedKey shotKey;
    private final Random random = new Random();
    private final Map<UUID, Fight> fights = new HashMap<>(); // every participant -> their fight
    private final Set<Integer> usedSlots = new HashSet<>();
    private Team glow;

    Horseman(AbilitySMP plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "headless_horseman");
        this.shotKey = new NamespacedKey(plugin, "horseman_shot");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 1L);
    }

    boolean isBusy(Player p) {
        return fights.containsKey(p.getUniqueId());
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private double cfgD(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    private boolean isBoss(Entity e) {
        return e.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private Team glowTeam() {
        if (glow == null) {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            glow = sb.getTeam("hh_glow");
            if (glow == null) glow = sb.registerNewTeam("hh_glow");
            glow.color(NamedTextColor.GOLD);
        }
        return glow;
    }

    // =====================================================================
    // Command and arena
    // =====================================================================

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
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
                    else if (!plugin.hallow.leave(p)) {
                        p.sendMessage(Component.text("You are not in a boss fight.", NamedTextColor.GRAY));
                    }
                    return;
                }
                default -> {
                }
            }
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("fight")) {
            String who = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
            if (who.contains("mimic") || who.contains("ghost")) {
                plugin.hallow.command(s, args);
                return;
            }
            if (!who.contains("horseman")) {
                p.sendMessage(Component.text("Unknown boss. Try: /boss fight Headless Horseman, /boss fight Mimic or /boss fight Ghost",
                        NamedTextColor.RED));
                return;
            }
            start(p);
            return;
        }
        p.sendMessage(Component.text("Usage: /boss fight <Headless Horseman|Mimic|Ghost> - /boss invite <friend> to bring a friend, /boss accept to join them",
                NamedTextColor.GRAY));
    }

    /** Each fight gets its own arena, spaced along X, so two Horseman fights never collide. */
    private Location centerFor(Fight f) {
        World w = Bukkit.getWorlds().get(0);
        int cx = cfg("horseman.arena-x", 0) + f.slot * cfg("boss.arena-slot-spacing", 100);
        int y = cfg("horseman.arena-y", 200), cz = cfg("horseman.arena-z", -4000);
        if (!Arenas.built(w, cx, y, cz)) {
            Arenas.build(w, cx, y, cz, 22, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, 14);
            decorate(w, cx, y, cz);
        }
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    private void decorate(World w, int cx, int y, int cz) {
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 19), z = cz + (int) Math.round(Math.sin(a) * 19);
            for (int h = 1; h <= 3; h++) w.getBlockAt(x, y + h, z).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            w.getBlockAt(x, y + 4, z).setType(Material.JACK_O_LANTERN, false);
        }
        for (int i = 0; i < 10; i++) { // gravestones
            double a = Math.PI * 2 * i / 10 + 0.3;
            int r = 11 + (i % 3) * 2;
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            w.getBlockAt(x, y + 1, z).setType(Material.COBBLED_DEEPSLATE_WALL, false);
            w.getBlockAt(x, y + 2, z).setType(Material.COBBLED_DEEPSLATE_SLAB, false);
        }
    }

    // =====================================================================
    // Starting the fight
    // =====================================================================

    private void start(Player host) {
        if (!plugin.getConfig().getBoolean("halloween.enabled", true)) {
            host.sendMessage(Component.text("The Halloween content is closed right now.", NamedTextColor.GRAY));
            return;
        }
        if (isBusy(host) || plugin.hallow.isBusy(host)) {
            host.sendMessage(Component.text("You are already in a boss fight.", NamedTextColor.RED));
            return;
        }
        if (plugin.duel.isBusy(host)) {
            host.sendMessage(Component.text("Finish your duel first.", NamedTextColor.RED));
            return;
        }
        // a friend in your boss party joins you in the same arena; busy friends are left behind
        List<Player> fighters = new ArrayList<>();
        fighters.add(host);
        for (Player m : plugin.party.members(host)) {
            if (m.equals(host)) continue;
            if (isBusy(m) || plugin.hallow.isBusy(m) || plugin.duel.isBusy(m)) {
                host.sendMessage(Component.text(m.getName() + " is busy and can't join this fight.", NamedTextColor.GRAY));
                continue;
            }
            fighters.add(m);
        }

        final Fight f = new Fight();
        int slot = 0;
        while (usedSlots.contains(slot)) slot++;
        usedSlots.add(slot);
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
            plugin.fightTotem.protect(p, "Headless Horseman", popped -> onPlayerOut(f, popped, true));
            p.showTitle(Title.title(Component.text("The Headless Horseman", NamedTextColor.GOLD),
                    Component.text("rises from the dark...", NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(600))));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!f.ending && !f.active().isEmpty()) spawnBoss(f, c);
        }, 100L);
    }

    private void spawnBoss(Fight f, Location c) {
        World w = c.getWorld();
        Location bl = c.clone().add(0, 1, -10);
        w.strikeLightningEffect(bl);
        double hp = cfgD("horseman.health", 100);

        Zombie z = w.spawn(bl, Zombie.class, boss -> {
            boss.setBaby(false);
            boss.setShouldBurnInDay(false);
            boss.setCanBreakDoors(false);
            boss.setRemoveWhenFarAway(false);
            boss.setCanPickupItems(false);
            boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, "1");
            boss.customName(Component.text("Headless Horseman", NamedTextColor.GOLD));
            boss.setCustomNameVisible(false);
            // invisible: only the armor, the held pumpkin head and the axe are visible, so he has no head
            boss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            EntityEquipment eq = boss.getEquipment();
            eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.fromRGB(45, 15, 55)));
            eq.setLeggings(leather(Material.LEATHER_LEGGINGS, Color.fromRGB(20, 12, 25)));
            eq.setBoots(leather(Material.LEATHER_BOOTS, Color.fromRGB(35, 20, 10)));
            ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
            ItemMeta am = axe.getItemMeta();
            am.setEnchantmentGlintOverride(true);
            axe.setItemMeta(am);
            eq.setItemInMainHand(axe);
            eq.setItemInOffHand(new ItemStack(Material.JACK_O_LANTERN)); // the stolen head
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);
            attr(boss, Attribute.MAX_HEALTH, hp);
            boss.setHealth(hp);
            attr(boss, Attribute.SCALE, 1.3);
            attr(boss, Attribute.ATTACK_DAMAGE, 6);
            attr(boss, Attribute.MOVEMENT_SPEED, 0.26);
            attr(boss, Attribute.KNOCKBACK_RESISTANCE, 0.7);
            attr(boss, Attribute.FOLLOW_RANGE, 60);
        });
        f.boss = z;
        Player first = f.nearestTo(z);
        if (first != null) z.setTarget(first);

        // the ghostly horse stands nearby with an orange glow
        f.horse = w.spawn(c.clone().add(6, 1, -9), ZombieHorse.class, h -> {
            h.setAI(false);
            h.setSilent(true);
            h.setInvulnerable(true);
            h.setPersistent(false);
            h.setGlowing(true);
        });
        glowTeam().addEntry(f.horse.getUniqueId().toString());

        f.bar = BossBar.bossBar(Component.text("Headless Horseman", NamedTextColor.GOLD), 1f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        for (Player p : f.active()) p.showBossBar(f.bar);
        w.playSound(bl, Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
    }

    // =====================================================================
    // The fight, once per tick
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
        Zombie b = f.boss;
        if (b == null || !b.isValid()) return;
        for (Player p : new ArrayList<>(f.active())) {
            if (p.getWorld() != b.getWorld()) onPlayerOut(f, p, false);
        }
        Player p = f.nearestTo(b);
        if (p == null) return;
        f.tick++;
        World w = b.getWorld();
        double max = cfgD("horseman.health", 100);
        double hp = b.getHealth();
        int ph = hp > max * 0.5 ? 1 : hp > cfgD("horseman.rage-health", 25) ? 2 : 3;
        if (ph != f.phase) {
            f.phase = ph;
            onPhase(f);
        }
        f.bar.progress((float) Math.max(0.0, Math.min(1.0, hp / max)));

        Location neck = b.getLocation().add(0, b.getHeight() * 0.95, 0);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, neck, 3, 0.12, 0.05, 0.12, 0.01);
        w.spawnParticle(Particle.FLAME, neck, 2, 0.1, 0.05, 0.1, 0.01);
        if (f.tick % 4 == 0) {
            w.spawnParticle(Particle.FLAME, b.getLocation().add(b.getLocation().getDirection().multiply(0.6)).add(0, b.getHeight() * 0.65, 0),
                    2, 0.1, 0.1, 0.1, 0.01);
        }
        if (f.tick % 20 == 0) b.setTarget(p);

        double dist = b.getLocation().distance(p.getLocation());
        switch (f.phase) {
            case 1 -> {
                if (f.tick % 80 == 0) shoot(f, false);
            }
            case 2 -> {
                if (f.tick % 30 == 0 && dist < 5) slash(f);
                if (f.tick % 90 == 0) shoot(f, true);
                if (f.tick % 110 == 0) shoot(f, false);
            }
            default -> {
                if (f.tick % 24 == 0 && dist < 5) slash(f);
                if (f.tick % 60 == 0) shoot(f, false);
                if (f.tick % 70 == 0) fireball(f);
            }
        }
        tickShots(f);
    }

    private void onPhase(Fight f) {
        Zombie b = f.boss;
        if (f.phase == 2) {
            attr(b, Attribute.MOVEMENT_SPEED, 0.30);
            attr(b, Attribute.ATTACK_DAMAGE, 8);
            for (Player p : f.active()) {
                p.showTitle(Title.title(Component.text("The Horseman is ANGRY!", NamedTextColor.GOLD), Component.empty(),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
            }
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5f, 0.5f);
        } else if (f.phase == 3) {
            attr(b, Attribute.MOVEMENT_SPEED, 0.34);
            attr(b, Attribute.ATTACK_DAMAGE, 10);
            for (Player p : f.active()) {
                p.showTitle(Title.title(Component.text("RAGING!", NamedTextColor.DARK_RED),
                        Component.text("He deals double damage", NamedTextColor.RED),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
            }
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);
            summonRage(f);
        }
    }

    private void summonRage(Fight f) {
        Location c = f.center;
        World w = c.getWorld();
        for (int i = 0; i < 10; i++) { // 10 zombies
            spawnMinion(f, spot(c, 8 + random.nextInt(9)), "zombie");
        }
        for (int i = 0; i < 3; i++) { // Jack O' Lantern mobs
            spawnMinion(f, spot(c, 8 + random.nextInt(9)), "jack");
        }
        for (int i = 0; i < 8; i++) { // Jack O' Lantern blocks around the arena
            double a = Math.PI * 2 * i / 8 + 0.2;
            Block b = w.getBlockAt(c.getBlockX() + (int) Math.round(Math.cos(a) * 16), c.getBlockY() + 1,
                    c.getBlockZ() + (int) Math.round(Math.sin(a) * 16));
            f.old.putIfAbsent(b.getLocation(), b.getBlockData());
            b.setType(Material.JACK_O_LANTERN, false);
        }
    }

    private Location spot(Location c, int radius) {
        double a = random.nextDouble() * Math.PI * 2;
        return c.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius);
    }

    private void spawnMinion(Fight f, Location loc, String type) {
        LivingEntity m = minion(loc, type);
        m.setRemoveWhenFarAway(false);
        if (m instanceof Mob mob) {
            Player t = f.nearestTo(m);
            if (t != null) mob.setTarget(t);
        }
        f.minions.add(m);
    }

    private static ItemStack leather(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(c);
        it.setItemMeta(lm);
        return it;
    }

    /** The zombies and Jack O' Lantern mobs summoned in phase 3. */
    private LivingEntity minion(Location loc, String type) {
        Zombie z = loc.getWorld().spawn(loc, Zombie.class);
        boolean jack = type.equals("jack");
        z.customName(Component.text(jack ? "Jack O' Lantern" : "Rotten Zombie", NamedTextColor.GOLD));
        z.setCustomNameVisible(true);
        z.setCanPickupItems(false);
        EntityEquipment eq = z.getEquipment();
        eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.ORANGE));
        eq.setLeggings(leather(Material.LEATHER_LEGGINGS, Color.ORANGE));
        eq.setBoots(leather(Material.LEATHER_BOOTS, Color.ORANGE));
        if (jack) eq.setHelmet(new ItemStack(Material.JACK_O_LANTERN));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        return z;
    }

    // ---- attacks

    private void slash(Fight f) {
        Zombie b = f.boss;
        Player p = f.nearestTo(b);
        if (p == null) return;
        Location l = b.getLocation().add(b.getLocation().getDirection().multiply(1.6)).add(0, b.getHeight() * 0.6, 0);
        b.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 3, 0.6, 0.3, 0.6, 0);
        b.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        if (b.getLocation().distance(p.getLocation()) < 5) {
            p.damage(cfgD("horseman.slash-damage", 6), b);
            Vector away = p.getLocation().toVector().subtract(b.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) p.setVelocity(away.normalize().multiply(0.7).setY(0.3));
        }
    }

    private void shoot(Fight f, boolean pumpkin) {
        Zombie b = f.boss;
        Player p = f.nearestTo(b);
        if (p == null) return;
        Location from = b.getLocation().add(0, b.getHeight() * 0.8, 0);
        Vector dir = p.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0E-6) return;
        dir.normalize().multiply(1.25).setY(dir.getY() + 0.12);
        BlockData data = pumpkin ? Material.CARVED_PUMPKIN.createBlockData() : Material.COBBLED_DEEPSLATE_WALL.createBlockData();
        FallingBlock fb = from.getWorld().spawnFallingBlock(from, data);
        fb.setVelocity(dir);
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.getPersistentDataContainer().set(shotKey, PersistentDataType.STRING, pumpkin ? "pumpkin" : "grave");
        f.shots.add(fb);
        from.getWorld().playSound(from, Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.7f);
    }

    private void fireball(Fight f) {
        Zombie b = f.boss;
        Player target = f.nearestTo(b);
        if (target == null) return;
        Vector dir = target.getLocation().add(0, 1, 0).toVector().subtract(b.getEyeLocation().toVector());
        if (dir.lengthSquared() < 1.0E-6) return;
        Fireball fb = b.launchProjectile(Fireball.class, dir.normalize());
        fb.setYield(1.5f);
        fb.setIsIncendiary(false);
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
    }

    private void tickShots(Fight f) {
        for (FallingBlock fb : new ArrayList<>(f.shots)) {
            if (!fb.isValid() || fb.getTicksLived() > 120) {
                fb.remove();
                f.shots.remove(fb);
                continue;
            }
            for (Player p : f.active()) {
                if (fb.getLocation().add(0, 0.5, 0).distanceSquared(p.getLocation().add(0, 1, 0)) < 2.4) {
                    String kind = fb.getPersistentDataContainer().get(shotKey, PersistentDataType.STRING);
                    double dmg = "pumpkin".equals(kind) ? cfgD("horseman.pumpkin-damage", 8) : cfgD("horseman.gravestone-damage", 6);
                    p.damage(dmg, f.boss);
                    p.setVelocity(fb.getVelocity().multiply(0.4).setY(0.3));
                    p.getWorld().spawnParticle(Particle.CRIT, fb.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
                    p.getWorld().spawnParticle(Particle.LARGE_SMOKE, fb.getLocation(), 8, 0.2, 0.2, 0.2, 0.02);
                    fb.remove();
                    f.shots.remove(fb);
                    break;
                }
            }
        }
    }

    // =====================================================================
    // Damage rules, defeat, victory
    // =====================================================================

    @EventHandler
    public void onShotLand(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock fb
                && fb.getPersistentDataContainer().has(shotKey, PersistentDataType.STRING)) {
            e.setCancelled(true); // thrown gravestones never turn into real blocks
            fb.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        Entity srcEnt = d instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : d;
        if (!isBoss(srcEnt)) return;
        for (Fight f : new HashSet<>(fights.values())) {
            if (f.boss != null && f.boss.getUniqueId().equals(srcEnt.getUniqueId()) && f.phase >= 3) {
                e.setDamage(e.getDamage() * 2.0); // raging: double damage
                return;
            }
        }
    }

    // NOTE: there is deliberately no lethal-damage check here any more. The old one (priority HIGH,
    // only in this class) sometimes let a lethal hit through - a boss projectile, fire or poison
    // tick, or phase-3 double damage - and the player really died. FightTotem now guards every
    // fighter at HIGHEST priority: the totem pops, the player is knocked out via onPlayerOut,
    // and the player never dies.

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
        if (back != null) e.getPlayer().teleport(back); // so they don't log back in inside the arena
        onPlayerOut(f, e.getPlayer(), false);
    }

    /** A player's Fight Totem popped (or they left): they are out; the fight ends if nobody is left. */
    private void onPlayerOut(Fight f, Player p, boolean popped) {
        if (f.ending || f.out.contains(p.getUniqueId())) return;
        f.out.add(p.getUniqueId());
        if (f.bar != null) p.hideBossBar(f.bar);
        plugin.fightTotem.cleanup(p);
        if (p.isOnline()) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0);
            p.setFireTicks(0);
            Location back = f.backs.get(p.getUniqueId());
            if (back != null) p.teleport(back);
            plugin.admin.applySky(p);
            if (popped) {
                p.showTitle(Title.title(Component.text("Defeated!", NamedTextColor.RED),
                        Component.text("The Horseman wins this time", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
            }
        }
        for (Player other : f.active()) {
            other.sendMessage(Component.text(p.getName() + " is out of the fight!", NamedTextColor.RED));
        }
        if (f.active().isEmpty()) end(f, false, false);
    }

    private void victory(Fight f) {
        if (f.ending) return;
        int points = cfg("horseman.reward-points", 50);
        int coins = cfg("horseman.reward-coins", 30);
        List<String> names = new ArrayList<>();
        for (Player p : f.active()) {
            names.add(p.getName());
            plugin.unbound.addPoints(p, points, "Headless Horseman");
            plugin.tot.giveCoins(p, coins);
            Profile.Data d = plugin.profile.of(p);
            d.horsemanDefeated = true;
            d.bossKills++;
            plugin.profile.save();
            plugin.unbound.addProgress(p, "BOSS_KILL", 1);
            plugin.ranks.evaluate(p);
            p.showTitle(Title.title(Component.text("VICTORY!", NamedTextColor.GREEN),
                    Component.text("+" + points + " Unbound Coins, +" + coins + " Halloween Coins", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(800))));
        }
        Bukkit.broadcast(Component.text(String.join(" and ", names) + " defeated the Headless Horseman!", NamedTextColor.GOLD));
        end(f, true, false);
    }

    private void end(Fight f, boolean win, boolean immediate) {
        if (f.ending) return;
        f.ending = true;
        for (Player p : f.players) fights.remove(p.getUniqueId());
        usedSlots.remove(f.slot);
        if (f.bar != null) for (Player p : f.players) p.hideBossBar(f.bar);
        for (FallingBlock fb : f.shots) fb.remove();
        for (LivingEntity m : f.minions) if (m.isValid()) m.remove();
        if (f.boss != null && f.boss.isValid()) f.boss.remove();
        if (f.horse != null) {
            glowTeam().removeEntry(f.horse.getUniqueId().toString());
            f.horse.remove();
        }
        for (Map.Entry<Location, BlockData> en : f.old.entrySet()) en.getKey().getBlock().setBlockData(en.getValue(), false);

        List<Player> stillIn = f.active();
        Runnable back = () -> {
            for (Player p : stillIn) {
                plugin.fightTotem.cleanup(p); // win: the Fight Totem disappears here
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
