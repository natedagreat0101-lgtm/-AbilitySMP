package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Lasso (v12): a craftable rope. Right-click to throw it at whatever you are looking
 * at (up to 12 blocks): it deals 1.5 hearts, yanks the target toward you, plays a whip
 * sound and draws an animated rope through the air. Cooldown: 1 second.
 * Recipe: 4 string around a stick - cheap on purpose.
 */
final class Lasso implements Listener {

    private final AbilitySMP plugin;
    private final NamespacedKey lassoKey;
    private final NamespacedKey recipeKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    Lasso(AbilitySMP plugin) {
        this.plugin = plugin;
        this.lassoKey = new NamespacedKey(plugin, "lasso");
        this.recipeKey = new NamespacedKey(plugin, "lasso_recipe");
    }

    ItemStack item() {
        ItemStack it = new ItemStack(Material.LEAD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Lasso", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Right-click: throw it at whatever you look at.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Hits for 1.5 hearts and yanks them toward you.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Cooldown: 1 second. Craft it: string around a stick.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(lassoKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    boolean isLasso(ItemStack it) {
        return it != null && it.getType() == Material.LEAD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(lassoKey, PersistentDataType.STRING);
    }

    void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe r = new ShapedRecipe(recipeKey, item());
        r.shape(" S ", "STS", " S ");
        r.setIngredient('S', Material.STRING);
        r.setIngredient('T', Material.STICK);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!isLasso(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true); // never actually leads anything

        int cdMs = plugin.getConfig().getInt("lasso.cooldown-ms", 1000);
        long left = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        if (left > 0) {
            plugin.bar(p, "Lasso ready in " + (Math.round(left / 100.0) / 10.0) + "s", NamedTextColor.GRAY);
            return;
        }
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + cdMs);
        throwLasso(p);
    }

    private void throwLasso(Player p) {
        double range = plugin.getConfig().getDouble("lasso.range", 12);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location hand = eye.clone().add(dir.clone().multiply(0.6)).add(0, -0.25, 0);

        // find the first living thing along the rope's line
        LivingEntity target = null;
        Location hitAt = eye.clone().add(dir.clone().multiply(range));
        for (double step = 1.0; step <= range; step += 0.4) {
            Location at = eye.clone().add(dir.clone().multiply(step));
            boolean blocked = at.getBlock().getType().isSolid();
            for (LivingEntity le : at.getWorld().getNearbyLivingEntities(at, 0.9)) {
                if (le.equals(p) || !le.isValid() || le.isDead()) continue;
                if (le instanceof Player other) {
                    if (other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                    // no lassoing people out of duels, ranked matches or boss fights
                    if (plugin.duel.isBusy(p) || plugin.duel.isBusy(other)
                            || plugin.ranked.isBusy(p) || plugin.ranked.isBusy(other)
                            || plugin.bosses.isBusy(p) || plugin.bosses.isBusy(other)) continue;
                } else if (!(le instanceof Enemy) && !(le instanceof Mob)) {
                    continue;
                }
                target = le;
                hitAt = le.getLocation().add(0, le.getHeight() * 0.6, 0);
                break;
            }
            if (target != null || blocked) {
                if (target == null) hitAt = at;
                break;
            }
        }

        p.getWorld().playSound(hand, Sound.ENTITY_FISHING_BOBBER_THROW, 1.1f, 1.3f); // the whip throw
        animateRope(hand, hitAt);

        if (target == null) return;
        final LivingEntity t = target;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!t.isValid() || t.isDead()) return;
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.1f); // snap!
            t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.05);
            t.damage(plugin.getConfig().getDouble("lasso.damage-hearts", 1.5) * 2.0, p);
            Vector pull = p.getLocation().add(0, 0.4, 0).toVector().subtract(t.getLocation().toVector());
            if (pull.lengthSquared() > 1.0E-6) {
                t.setVelocity(pull.normalize()
                        .multiply(plugin.getConfig().getDouble("lasso.pull-strength", 1.15)).setY(0.38));
            }
            if (t instanceof Player tp) plugin.bar(tp, "You got lassoed!", NamedTextColor.GOLD);
        }, 3L);
    }

    /** The rope flying through the air: the line grows over 3 ticks, with a loop at the tip. */
    private void animateRope(Location from, Location to) {
        Particle.DustOptions rope = new Particle.DustOptions(Color.fromRGB(160, 110, 55), 1.1f);
        for (int phase = 1; phase <= 3; phase++) {
            final int ph = phase;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Vector span = to.toVector().subtract(from.toVector());
                double frac = ph / 3.0;
                int points = Math.max(2, (int) (span.length() * frac * 2));
                for (int i = 0; i <= points; i++) {
                    Location at = from.clone().add(span.clone().multiply(frac * i / points));
                    at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, rope);
                }
                // the loop at the tip
                Location tip = from.clone().add(span.clone().multiply(frac));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    tip.getWorld().spawnParticle(Particle.DUST,
                            tip.clone().add(Math.cos(a) * 0.35, Math.sin(a) * 0.35, 0), 1, 0, 0, 0, 0, rope);
                }
            }, ph);
        }
    }
}
