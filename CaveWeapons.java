package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v15.5 THE CAVE UPDATE: custom cave weapons, sold in the Cave Shop for
 * Cave Coins. Right-click = the ability (each has a cooldown); the Magma
 * Bow works by shooting it. Every weapon carries the cave_weapon tag,
 * and a CustomModelData (4000 + slot) so Power Pack v8 can texture it.
 */
final class CaveWeapons implements Listener {

    enum CWDef {
        ECHO_BLASTER("echo_blaster", "Echo Blaster", NamedTextColor.DARK_AQUA, Material.BLAZE_ROD, 300,
                "Right-click: a sonic blast - 6 hearts to the first enemy in front of you (20s)"),
        CRYSTAL_SPEAR("crystal_spear", "Crystal Spear", NamedTextColor.LIGHT_PURPLE, Material.TRIDENT, 250,
                "Right-click: throw a volley of 3 crystal shards (15s)"),
        DEEPSLATE_HAMMER("deepslate_hammer", "Deepslate Hammer", NamedTextColor.GRAY, Material.MACE, 350,
                "Right-click: ground slam - damage and launch everything around you (30s)"),
        LUMINOUS_BLADE("luminous_blade", "Luminous Blade", NamedTextColor.YELLOW, Material.GOLDEN_SWORD, 200,
                "Right-click: a blinding flash - blindness and weakness for enemies near you (45s)"),
        MAGMA_BOW("magma_bow", "Magma Bow", NamedTextColor.RED, Material.BOW, 280,
                "Its arrows explode in a small ball of fire where they land");

        final String id;
        final String display;
        final NamedTextColor color;
        final Material material;
        final int price;
        final String lore;

        CWDef(String id, String display, NamedTextColor color, Material material, int price, String lore) {
            this.id = id;
            this.display = display;
            this.color = color;
            this.material = material;
            this.price = price;
            this.lore = lore;
        }

        static CWDef byId(String id) {
            for (CWDef d : values()) if (d.id.equals(id)) return d;
            return null;
        }
    }

    private final AbilitySMP plugin;
    private final NamespacedKey weaponKey;
    private final NamespacedKey arrowKey;
    private final Map<String, Long> cooldowns = new HashMap<>();

    CaveWeapons(AbilitySMP plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "cave_weapon");
        this.arrowKey = new NamespacedKey(plugin, "magma_arrow");
    }

    ItemStack create(CWDef d) {
        ItemStack it = new ItemStack(d.material);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(d.display, d.color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ CAVE WEAPON ✦", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(d.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.setUnbreakable(true);
        m.setEnchantmentGlintOverride(true);
        m.setCustomModelData(4000 + d.ordinal()); // Power Pack v8 draws this weapon's texture
        m.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, d.id);
        it.setItemMeta(m);
        return it;
    }

    private CWDef of(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer()
                .get(weaponKey, PersistentDataType.STRING);
        return id == null ? null : CWDef.byId(id);
    }

    private boolean cooled(Player p, CWDef d, int seconds) {
        String k = p.getUniqueId() + ":" + d.id;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(k, 0L);
        if (until > now) {
            p.sendActionBar(Component.text(d.display + " recharging: "
                    + ((until - now + 999) / 1000) + "s", NamedTextColor.RED));
            return false;
        }
        cooldowns.put(k, now + seconds * 1000L);
        return true;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        CWDef d = of(p.getInventory().getItemInMainHand());
        if (d == null) return;
        switch (d) {
            case ECHO_BLASTER -> {
                if (!cooled(p, d, 20)) return;
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                p.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                LivingEntity hit = null;
                for (double step = 1; step <= 15; step += 0.5) {
                    Location at = eye.clone().add(dir.clone().multiply(step));
                    p.getWorld().spawnParticle(Particle.SONIC_BOOM, at, 1);
                    for (LivingEntity le : at.getNearbyLivingEntities(1.2)) {
                        if (le != p) {
                            hit = le;
                            break;
                        }
                    }
                    if (hit != null) break;
                }
                if (hit != null) {
                    hit.damage(12.0, p);
                    hit.setVelocity(dir.clone().multiply(1.2).setY(0.4));
                }
            }
            case CRYSTAL_SPEAR -> {
                if (!cooled(p, d, 15)) return;
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.2f);
                for (int i = -1; i <= 1; i++) {
                    Arrow a = p.launchProjectile(Arrow.class,
                            p.getEyeLocation().getDirection().clone()
                                    .add(new Vector(0, i * 0.03, 0))
                                    .rotateAroundY(Math.toRadians(i * 6)));
                    a.setDamage(6.0);
                    a.setGlowing(true);
                }
            }
            case DEEPSLATE_HAMMER -> {
                if (!cooled(p, d, 30)) return;
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.7f);
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                for (LivingEntity le : p.getLocation().getNearbyLivingEntities(5)) {
                    if (le == p) continue;
                    le.damage(8.0, p);
                    le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector())
                            .normalize().multiply(1.1).setY(0.9));
                }
            }
            case LUMINOUS_BLADE -> {
                if (!cooled(p, d, 45)) return;
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.6f);
                p.getWorld().spawnParticle(Particle.FLASH, p.getEyeLocation(), 1, 0, 0, 0, 0,
                        org.bukkit.Color.fromRGB(255, 244, 190)); // FLASH needs a Color value on Paper 1.21.11 (v20.1)
                for (LivingEntity le : p.getLocation().getNearbyLivingEntities(7)) {
                    if (le == p) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            }
            case MAGMA_BOW -> {
                // passive - works by shooting, see below
            }
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (of(e.getBow()) != CWDef.MAGMA_BOW) return;
        e.getProjectile().getPersistentDataContainer().set(arrowKey, PersistentDataType.INTEGER, 1);
        e.getProjectile().setGlowing(true);
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        if (e.getEntity().getPersistentDataContainer().get(arrowKey, PersistentDataType.INTEGER) == null) {
            return;
        }
        Location at = e.getEntity().getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
        at.getWorld().spawnParticle(Particle.FLAME, at, 25, 0.4, 0.4, 0.4, 0.05);
        Object shooter = e.getEntity().getShooter();
        for (LivingEntity le : at.getNearbyLivingEntities(3)) {
            if (shooter instanceof Player sp && le == sp) continue;
            le.damage(4.0, shooter instanceof LivingEntity s ? s : null);
            le.setFireTicks(60);
        }
        e.getEntity().remove();
    }

    /** /caveweapons: the list, for chat help. */
    void info(Player p) {
        p.sendMessage(Component.text("===== ⚔ CAVE WEAPONS ⚔ =====", NamedTextColor.DARK_PURPLE));
        for (CWDef d : CWDef.values()) {
            p.sendMessage(Component.text(" ✦ " + d.display + " - " + d.price + " Cave Coins in /caveshop",
                    d.color));
            p.sendMessage(Component.text("   " + d.lore, NamedTextColor.GRAY));
        }
    }
}
