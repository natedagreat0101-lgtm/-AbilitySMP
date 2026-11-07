package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v15.5 THE CAVE UPDATE: cave bosses.
 *
 * /caveboss <boss> wakes a boss right where you stand - but only deep down
 * (below Y=40 by default), and only once per player every 10 minutes. Four
 * bosses, each a buffed vanilla mob with a boss bar and one nasty trick.
 * Whoever helps kill one shares the Cave Coin reward: the killer gets the
 * full prize, everyone who fought nearby gets half.
 */
final class CaveBosses implements Listener {

    enum CBDef {
        ANCIENT_MINER("ancient_miner", "The Ancient Miner", EntityType.ZOMBIE, 150, 80,
                NamedTextColor.GOLD),
        CRYSTAL_GOLEM("crystal_golem", "Crystal Golem", EntityType.IRON_GOLEM, 250, 120,
                NamedTextColor.LIGHT_PURPLE),
        CAVE_GUARDIAN("cave_guardian", "Cave Guardian", EntityType.RAVAGER, 220, 110,
                NamedTextColor.DARK_GREEN),
        SCULK_HORROR("sculk_horror", "The Sculk Horror", EntityType.WARDEN, 320, 180,
                NamedTextColor.DARK_AQUA);

        final String id;
        final String display;
        final EntityType type;
        final double health;
        final int reward;
        final NamedTextColor color;

        CBDef(String id, String display, EntityType type, double health, int reward,
              NamedTextColor color) {
            this.id = id;
            this.display = display;
            this.type = type;
            this.health = health;
            this.reward = reward;
            this.color = color;
        }

        static CBDef byId(String id) {
            for (CBDef d : values()) if (d.id.equals(id)) return d;
            return null;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey bossKey;
    private final Map<UUID, Long> lastSummon = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, CBDef> live = new HashMap<>();

    CaveBosses(AbilitySMP plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "cave_boss");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private CBDef of(LivingEntity e) {
        String id = e.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        return id == null ? null : CBDef.byId(id);
    }

    void command(Player p, String[] args) {
        if (args.length < 1) {
            p.sendMessage(Component.text("===== ⛏ CAVE BOSSES ⛏ =====", NamedTextColor.DARK_AQUA));
            for (CBDef d : CBDef.values()) {
                p.sendMessage(Component.text(" ✦ /caveboss " + d.id + " - " + d.display + " ("
                        + (int) d.health + " HP, pays " + d.reward + " Cave Coins)", d.color));
            }
            p.sendMessage(Component.text("Bosses only wake deep down, below Y="
                    + plugin.getConfig().getInt("cave.boss-max-y", 40)
                    + ". One summon per player every 10 minutes.", NamedTextColor.GRAY));
            return;
        }
        CBDef d = CBDef.byId(args[0].toLowerCase(java.util.Locale.ROOT).replace('-', '_'));
        if (d == null) {
            p.sendMessage(Component.text("Unknown boss. Type /caveboss to see them all.",
                    NamedTextColor.RED));
            return;
        }
        long wait = 600_000L - (System.currentTimeMillis()
                - lastSummon.getOrDefault(p.getUniqueId(), 0L));
        if (wait > 0) {
            p.sendMessage(Component.text("The caves are still quiet... wait "
                    + ((wait + 59_999) / 60_000) + " more minute(s) before summoning again.",
                    NamedTextColor.RED));
            return;
        }
        if (p.getLocation().getY() > plugin.getConfig().getInt("cave.boss-max-y", 40)) {
            p.sendMessage(Component.text("Too close to the surface! Cave bosses only wake below Y="
                    + plugin.getConfig().getInt("cave.boss-max-y", 40) + ".", NamedTextColor.RED));
            return;
        }
        spawn(p, d);
    }

    private void spawn(Player p, CBDef d) {
        lastSummon.put(p.getUniqueId(), System.currentTimeMillis());
        Location at = p.getLocation().clone().add(p.getLocation().getDirection().multiply(4).setY(0));
        LivingEntity boss = (LivingEntity) p.getWorld().spawnEntity(at, d.type);
        boss.customName(Component.text(d.display, d.color));
        boss.setCustomNameVisible(true);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(d.health);
        boss.setHealth(d.health);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, d.id);
        if (boss instanceof Zombie z) {
            z.setAdult();
            z.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            z.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            z.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
            z.getEquipment().setHelmetDropChance(0f);
            z.getEquipment().setChestplateDropChance(0f);
            z.getEquipment().setItemInMainHandDropChance(0f);
        }
        BossBar bar = Bukkit.createBossBar(d.display, BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bars.put(boss.getUniqueId(), bar);
        live.put(boss.getUniqueId(), d);
        Bukkit.broadcast(Component.text("⛏ " + d.display + " has woken in the caves! (summoned by "
                + p.getName() + ")", d.color));
        p.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
    }

    private void tick() {
        var it = live.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            org.bukkit.entity.Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity boss) || boss.isDead()) {
                BossBar bar = bars.remove(en.getKey());
                if (bar != null) bar.removeAll();
                it.remove();
                continue;
            }
            CBDef d = en.getValue();
            BossBar bar = bars.get(en.getKey());
            double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (bar != null) {
                bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / max)));
                for (Player p : boss.getLocation().getNearbyPlayers(50)) bar.addPlayer(p);
            }
            // the boss's nasty trick, every tick cycle (4 seconds)
            Player near = null;
            for (Player p : boss.getLocation().getNearbyPlayers(12)) {
                near = p;
                break;
            }
            if (near == null) continue;
            switch (d) {
                case SCULK_HORROR -> {
                    near.damage(4.0, boss);
                    near.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM,
                            0.6f, 1f);
                }
                case CRYSTAL_GOLEM -> {
                    near.setVelocity(near.getLocation().toVector()
                            .subtract(boss.getLocation().toVector()).normalize()
                            .multiply(1.4).setY(0.8));
                    boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                            1f, 0.8f);
                }
                case CAVE_GUARDIAN -> near.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                case ANCIENT_MINER -> {
                    if (boss.getHealth() < max * 0.5
                            && boss.getLocation().getNearbyEntities(10, 10, 10).stream()
                            .filter(x -> x.getType() == EntityType.ZOMBIE).count() < 3) {
                        Zombie minion = (Zombie) boss.getWorld()
                                .spawnEntity(boss.getLocation(), EntityType.ZOMBIE);
                        minion.customName(Component.text("Miner's Helper", NamedTextColor.GRAY));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        CBDef d = of(e.getEntity());
        if (d == null) return;
        BossBar bar = bars.remove(e.getEntity().getUniqueId());
        if (bar != null) bar.removeAll();
        live.remove(e.getEntity().getUniqueId());
        Player killer = e.getEntity().getKiller();
        Bukkit.broadcast(Component.text("⛏ " + d.display + " has been defeated!", d.color));
        for (Player p : e.getEntity().getLocation().getNearbyPlayers(25)) {
            int pay = (killer != null && p == killer) ? d.reward : d.reward / 2;
            plugin.cave.addCoins(p, pay, "cave boss");
            plugin.cave.progress(p, "boss", 1, true);
            p.sendMessage(Component.text("+" + pay + " Cave Coins for the boss kill!",
                    NamedTextColor.GOLD));
        }
        // bonus loot on top of the normal drops
        Location at = e.getEntity().getLocation();
        e.getDrops().add(new ItemStack(Material.DIAMOND, 2));
        if (d == CBDef.SCULK_HORROR) e.getDrops().add(new ItemStack(Material.ECHO_SHARD, 2));
        if (d == CBDef.CRYSTAL_GOLEM) e.getDrops().add(new ItemStack(Material.AMETHYST_SHARD, 8));
        if (d == CBDef.CAVE_GUARDIAN) e.getDrops().add(new ItemStack(Material.EMERALD, 4));
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
    }
}
