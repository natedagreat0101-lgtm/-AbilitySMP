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
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * /boss fight Headless Horseman. The boss is an invisible armored zombie, so only his dark armor, held
 * pumpkin head and axe show, with fire burning at his neck, next to a glowing ghost horse.
 * Phase 1 gravestones. Phase 2 slashes, pumpkin heads and gravestones. Phase 3 (raging) adds fireballs,
 * Jack O' Lanterns, 10 zombies and double damage.
 */
final class Horseman implements Listener {

    private static final class Fight {
        final Player player;
        Location back;
        Zombie boss;
        ZombieHorse horse;
        BossBar bar;
        int phase = 1;
        long tick;
        boolean ending;
        final List<FallingBlock> shots = new ArrayList<>();
        final List<LivingEntity> minions = new ArrayList<>();
        final Map<Location, BlockData> old = new HashMap<>();

        Fight(Player player) {
            this.player = player;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey shotKey;
    private final Random random = new Random();
    private Fight fight;
    private Team glow;

    Horseman(AbilitySMP plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "headless_horseman");
        this.shotKey = new NamespacedKey(plugin, "horseman_shot");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    boolean isBusy(Player p) {
        return fight != null && fight.player.getUniqueId().equals(p.getUniqueId());
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
        if (args.length >= 2 && args[0].equalsIgnoreCase("fight")) {
            String who = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
            if (!who.contains("horseman")) {
                p.sendMessage(Component.text("Unknown boss. Try: /boss fight Headless Horseman", NamedTextColor.RED));
                return;
            }
            start(p);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave") && fight != null && isBusy(p)) {
            end(fight, false, false);
            return;
        }
        p.sendMessage(Component.text("Usage: /boss fight Headless Horseman", NamedTextColor.GRAY));
    }

    private Location center() {
        World w = Bukkit.getWorlds().get(0);
        int cx = cfg("horseman.arena-x", 0), y = cfg("horseman.arena-y", 200), cz = cfg("horseman.arena-z", -4000);
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

    private void start(Player p) {
        if (!plugin.getConfig().getBoolean("halloween.enabled", true)) {
            p.sendMessage(Component.text("The Halloween content is closed right now.", NamedTextColor.GRAY));
            return;
        }
        if (fight != null) {
            p.sendMessage(Component.text("The arena is busy. Someone is fighting the Horseman right now.", NamedTextColor.RED));
            return;
        }
        if (plugin.duel.isBusy(p)) {
            p.sendMessage(Component.text("Finish your duel first.", NamedTextColor.RED));
            return;
        }
        Location c = center();
        final Fight f = new Fight(p);
        f.back = p.getLocation().clone();
        fight = f;

        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 14, 180f, 0f));
        p.setPlayerTime(13000L, false);
        p.showTitle(Title.title(Component.text("The Headless Horseman", NamedTextColor.GOLD),
                Component.text("rises from the dark...", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(600))));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fight == f && !f.ending && p.isOnline()) spawnBoss(f, c);
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
        z.setTarget(f.player);

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
        f.player.showBossBar(f.bar);
        w.playSound(bl, Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
    }

    // =====================================================================
    // The fight, once per tick
    // =====================================================================

    private void tick() {
        Fight f = fight;
        if (f == null || f.ending) return;
        Player p = f.player;
        if (!p.isOnline()) {
            end(f, false, true);
            return;
        }
        Zombie b = f.boss;
        if (b == null || !b.isValid()) return;
        if (p.getWorld() != b.getWorld()) {
            end(f, false, false);
            return;
        }
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
        Player p = f.player;
        if (f.phase == 2) {
            attr(b, Attribute.MOVEMENT_SPEED, 0.30);
            attr(b, Attribute.ATTACK_DAMAGE, 8);
            p.showTitle(Title.title(Component.text("The Horseman is ANGRY!", NamedTextColor.GOLD), Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5f, 0.5f);
        } else if (f.phase == 3) {
            attr(b, Attribute.MOVEMENT_SPEED, 0.34);
            attr(b, Attribute.ATTACK_DAMAGE, 10);
            p.showTitle(Title.title(Component.text("RAGING!", NamedTextColor.DARK_RED),
                    Component.text("He deals double damage", NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);
            summonRage(f);
        }
    }

    private void summonRage(Fight f) {
        Location c = center();
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
        if (m instanceof Mob mob) mob.setTarget(f.player);
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
        Player p = f.player;
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
        Player p = f.player;
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
        Vector dir = f.player.getLocation().add(0, 1, 0).toVector().subtract(b.getEyeLocation().toVector());
        if (dir.lengthSquared() < 1.0E-6) return;
        Fireball fb = b.launchProjectile(Fireball.class, dir.normalize());
        fb.setYield(1.5f);
        fb.setIsIncendiary(false);
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
    }

    private void tickShots(Fight f) {
        Player p = f.player;
        for (FallingBlock fb : new ArrayList<>(f.shots)) {
            if (!fb.isValid() || fb.getTicksLived() > 120) {
                fb.remove();
                f.shots.remove(fb);
                continue;
            }
            if (fb.getLocation().add(0, 0.5, 0).distanceSquared(p.getLocation().add(0, 1, 0)) < 2.4) {
                String kind = fb.getPersistentDataContainer().get(shotKey, PersistentDataType.STRING);
                double dmg = "pumpkin".equals(kind) ? cfgD("horseman.pumpkin-damage", 8) : cfgD("horseman.gravestone-damage", 6);
                p.damage(dmg, f.boss);
                p.setVelocity(fb.getVelocity().multiply(0.4).setY(0.3));
                p.getWorld().spawnParticle(Particle.CRIT, fb.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, fb.getLocation(), 8, 0.2, 0.2, 0.2, 0.02);
                fb.remove();
                f.shots.remove(fb);
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
        Fight f = fight;
        if (f == null || f.phase < 3) return;
        Entity d = e.getDamager();
        boolean fromBoss = isBoss(d)
                || (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh && isBoss(sh));
        if (fromBoss) e.setDamage(e.getDamage() * 2.0); // raging: double damage
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerHurt(EntityDamageEvent e) {
        Fight f = fight;
        if (f == null || f.ending || !(e.getEntity() instanceof Player p) || !isBusy(p)) return;
        if (e.getFinalDamage() >= p.getHealth()) {
            e.setCancelled(true); // you are defeated, but you don't die and you keep your items
            p.showTitle(Title.title(Component.text("Defeated!", NamedTextColor.RED),
                    Component.text("The Horseman wins this time", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
            end(f, false, false);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        Fight f = fight;
        if (f == null || f.boss == null || !e.getEntity().getUniqueId().equals(f.boss.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        victory(f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Fight f = fight;
        if (f == null || !isBusy(e.getPlayer())) return;
        e.getPlayer().teleport(f.back);
        end(f, false, true);
    }

    private void victory(Fight f) {
        Player p = f.player;
        if (f.ending) return;
        int points = cfg("horseman.reward-points", 50);
        int coins = cfg("horseman.reward-coins", 30);
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
        Bukkit.broadcast(Component.text(p.getName() + " defeated the Headless Horseman!", NamedTextColor.GOLD));
        end(f, true, false);
    }

    private void end(Fight f, boolean win, boolean immediate) {
        if (f.ending) return;
        f.ending = true;
        Player p = f.player;
        if (f.bar != null) p.hideBossBar(f.bar);
        for (FallingBlock fb : f.shots) fb.remove();
        for (LivingEntity m : f.minions) if (m.isValid()) m.remove();
        if (f.boss != null && f.boss.isValid()) f.boss.remove();
        if (f.horse != null) {
            glowTeam().removeEntry(f.horse.getUniqueId().toString());
            f.horse.remove();
        }
        for (Map.Entry<Location, BlockData> en : f.old.entrySet()) en.getKey().getBlock().setBlockData(en.getValue(), false);

        Runnable back = () -> {
            if (p.isOnline()) {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(max != null ? max.getValue() : 20.0);
                p.setFireTicks(0);
                p.teleport(f.back);
                plugin.admin.applySky(p);
            }
            if (fight == f) fight = null;
        };
        if (immediate) back.run();
        else Bukkit.getScheduler().runTaskLater(plugin, back, win ? 100L : 40L);
    }

    void shutdown() {
        if (fight != null) end(fight, false, true);
    }
}
