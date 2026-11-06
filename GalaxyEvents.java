package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The Galactic Update's admin events (v10), opened from /panel -> Server Events:
 * Supernova Spleef, Galaxy Rush, Verity, Meteor Shower, Moon Gravity and Star Rain.
 * The two arena games use Fight Totems, so nobody can die or lose their stuff.
 */
final class GalaxyEvents implements Listener {

    private final AbilitySMP plugin;
    private final Admin admin;
    private final Random random = new Random();
    private final NamespacedKey verityKey;

    // Supernova Spleef
    private boolean spleefRunning;
    private final Map<UUID, Location> spleefReturn = new HashMap<>();
    private final Set<UUID> spleefAlive = new HashSet<>();
    private final Set<Location> spleefBlocks = new HashSet<>(); // every block the arena is made of
    private final Set<Location> spleefFloor = new HashSet<>();
    private final Set<UUID> spleefBalls = new HashSet<>();
    private final Map<UUID, Long> shootCd = new HashMap<>();
    private Location spleefCenter;
    private BukkitTask spleefTask;
    private int spleefLeft;

    // Verity
    private boolean verityRunning;
    private final Map<UUID, Location> verityReturn = new HashMap<>();
    private final Set<UUID> verityAlive = new HashSet<>();
    private Location verityCenter;
    private BukkitTask verityTask;
    private int verityLeft;
    private int verityNote;

    // Timed / toggle events
    private boolean rushOn;
    private BukkitTask rushTask;
    private int rushLeft;
    private boolean meteorOn;
    private BukkitTask meteorTask;
    private final Set<UUID> meteors = new HashSet<>();
    private boolean moonOn;
    private BukkitTask moonTask;
    private int moonLeft;
    private boolean starRainOn;
    private BukkitTask starRainTask;

    GalaxyEvents(AbilitySMP plugin, Admin admin) {
        this.plugin = plugin;
        this.admin = admin;
        this.verityKey = new NamespacedKey(plugin, "verity_snowball");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt("galaxy-events." + path, def);
    }

    // =====================================================================
    // The /panel Server Events menu (Admin owns the inventory, we fill it)
    // =====================================================================

    private ItemStack item(Material m, String name, NamedTextColor color, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private static String on(boolean b) {
        return b ? "ON (click to turn off)" : "OFF (click to turn on)";
    }

    void fillEvents(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK);
        for (int s = 0; s < 54; s++) inv.setItem(s, filler);
        inv.setItem(10, item(Material.MAGMA_BLOCK, "Supernova Spleef", NamedTextColor.RED,
                spleefRunning ? "RUNNING - click to end it early" : "A supernova falls! Everyone is teleported to a molten",
                spleefRunning ? "" : "arena with flame powers and shoots the floor away.",
                spleefRunning ? "" : "Fight Totems on. Last one standing wins Galaxy Coins!"));
        inv.setItem(11, item(Material.ENDER_EYE, "Galaxy Rush", NamedTextColor.LIGHT_PURPLE,
                on(rushOn), "Speed III for everyone under a galaxy night sky."));
        inv.setItem(12, item(Material.SNOWBALL, "Verity", NamedTextColor.YELLOW,
                verityRunning ? "RUNNING - click to end it early" : "It's me, it's Verity! Everyone gets Strength III,",
                verityRunning ? "" : "a Fight Totem and a one-shot Verity snowball in a",
                verityRunning ? "" : "yellow arena, until the song ends. Popped = sent home."));
        inv.setItem(13, item(Material.FIRE_CHARGE, "Meteor Shower", NamedTextColor.GOLD, on(meteorOn),
                "Meteors crash down around every player. Dodge!"));
        inv.setItem(14, item(Material.FEATHER, "Moon Gravity", NamedTextColor.AQUA, on(moonOn),
                "Jump Boost and slow falling for everyone. Boing."));
        inv.setItem(15, item(Material.PRISMARINE_CRYSTALS, "Star Rain", NamedTextColor.DARK_PURPLE, on(starRainOn),
                "Galaxy Coins, shards and pearls fall from the sky!"));
        inv.setItem(31, item(Material.REDSTONE_BLOCK, "Stop all events", NamedTextColor.RED));
        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.WHITE));
    }

    void eventsClick(Player p, int slot) {
        switch (slot) {
            case 10 -> {
                if (spleefRunning) endSpleef(true);
                else startSpleef(p);
            }
            case 11 -> toggleRush();
            case 12 -> {
                if (verityRunning) endVerity(true);
                else startVerity(p);
            }
            case 13 -> toggleMeteor();
            case 14 -> toggleMoon();
            case 15 -> toggleStarRain();
            case 31 -> stopAllEvents();
            case 45 -> {
                admin.openMain(p);
                return;
            }
            default -> {
                return;
            }
        }
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Admin.AdminHolder h && h.menu == 2) {
            fillEvents(h.inv);
        }
    }

    // =====================================================================
    // Shared helpers
    // =====================================================================

    private void broadcastTitle(String title, NamedTextColor color, String sub) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Component.text(title, color),
                    Component.text(sub, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
    }

    /** Players who can join an arena game: online, in a normal game mode, not already in a protected fight. */
    private List<Player> eligible() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (plugin.fightTotem.isProtected(p)) continue;
            out.add(p);
        }
        return out;
    }

    private World overworld() {
        for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == World.Environment.NORMAL) return w;
        return Bukkit.getWorlds().get(0);
    }

    private void sendHome(Player p, Map<UUID, Location> returns) {
        Location back = returns.remove(p.getUniqueId());
        plugin.fightTotem.cleanup(p);
        if (back != null && p.isOnline()) p.teleport(back);
    }

    // =====================================================================
    // Supernova Spleef: a molten arena, flame fireballs, last one standing
    // =====================================================================

    private void startSpleef(Player adminPlayer) {
        List<Player> players = eligible();
        if (players.isEmpty()) {
            adminPlayer.sendMessage(Component.text("Nobody can join the spleef right now.", NamedTextColor.RED));
            return;
        }
        World w = overworld();
        int cx = cfg("spleef.x", 0), cy = cfg("spleef.y", 240), cz = cfg("spleef.z", 20000);
        spleefCenter = new Location(w, cx, cy, cz);
        buildSpleefArena(w, cx, cy, cz, 14);
        spleefRunning = true;
        spleefLeft = Math.max(20, cfg("spleef.seconds", 60));
        broadcastTitle("☀ SUPERNOVA SPLEEF ☀", NamedTextColor.RED, "Shoot the floor! Last one standing wins!");
        Bukkit.broadcast(Component.text("A SUPERNOVA fell on the world! Everyone was teleported to the molten arena - "
                + "right-click to shoot fireballs and break the floor. Last one standing wins!", NamedTextColor.RED));
        int i = 0;
        for (Player p : players) {
            spleefReturn.put(p.getUniqueId(), p.getLocation().clone());
            double ang = Math.PI * 2 * i++ / Math.max(1, players.size());
            Location spawn = new Location(w, cx + Math.cos(ang) * 10, cy + 1, cz + Math.sin(ang) * 10);
            spawn.setDirection(spleefCenter.clone().add(0, 1, 0).toVector().subtract(spawn.toVector()));
            p.teleport(spawn);
            spleefAlive.add(p.getUniqueId());
            plugin.fightTotem.protect(p, "Supernova Spleef", this::spleefOut);
            p.sendMessage(Component.text("Right-click to shoot fireballs! Break the floor under everyone else. "
                    + "Your Fight Totem keeps you safe - if you fall, you're out.", NamedTextColor.YELLOW));
        }
        spleefTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spleefTick, 20L, 20L);
    }

    private void buildSpleefArena(World w, int cx, int cy, int cz, int r) {
        spleefBlocks.clear();
        spleefFloor.clear();
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int d2 = dx * dx + dz * dz;
                Location floorLoc = new Location(w, cx + dx, cy, cz + dz);
                if (d2 <= r * r) {
                    setArena(floorLoc, Material.MAGMA_BLOCK);
                    spleefFloor.add(floorLoc);
                    setArena(new Location(w, cx + dx, cy - 6, cz + dz), Material.LAVA);
                    for (int h = 1; h <= 7; h++) setArena(new Location(w, cx + dx, cy + h, cz + dz), Material.AIR);
                    setArena(new Location(w, cx + dx, cy + 8, cz + dz), Material.BARRIER);
                } else if (d2 <= (r + 2) * (r + 2)) {
                    for (int h = 0; h <= 8; h++) setArena(new Location(w, cx + dx, cy + h, cz + dz), Material.BARRIER);
                }
            }
        }
    }

    private void setArena(Location loc, Material m) {
        loc.getBlock().setType(m, false);
        spleefBlocks.add(loc.clone());
    }

    private void spleefTick() {
        if (!spleefRunning) return;
        spleefLeft--;
        for (UUID id : new ArrayList<>(spleefAlive)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                spleefEliminate(id, false);
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, true, false, false));
            if (p.getLocation().getY() < spleefCenter.getY() - 3
                    || p.getLocation().distanceSquared(spleefCenter) > 30 * 30) {
                spleefEliminate(id, true); // fell (or escaped): out
                continue;
            }
            plugin.bar(p, "Spleef: " + spleefLeft + "s left - " + spleefAlive.size() + " standing", NamedTextColor.RED);
        }
        if (spleefLeft <= 0 || spleefAlive.size() <= 1) endSpleef(false);
    }

    /** The Fight Totem popped: the player is out of the spleef. */
    private void spleefOut(Player p) {
        spleefEliminate(p.getUniqueId(), true);
    }

    private void spleefEliminate(UUID id, boolean announce) {
        if (!spleefAlive.remove(id)) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            sendHome(p, spleefReturn);
            if (announce) {
                p.sendMessage(Component.text("You're out of the Supernova Spleef!", NamedTextColor.RED));
                Bukkit.broadcast(Component.text(p.getName() + " is out of the Supernova Spleef! "
                        + spleefAlive.size() + " left.", NamedTextColor.GOLD));
            }
        } else {
            spleefReturn.remove(id);
        }
        if (spleefRunning && spleefAlive.size() <= 1) endSpleef(false);
    }

    private void endSpleef(boolean forced) {
        if (!spleefRunning) return;
        spleefRunning = false;
        if (spleefTask != null) spleefTask.cancel();
        spleefTask = null;
        Player winner = null;
        if (!forced && spleefAlive.size() == 1) winner = Bukkit.getPlayer(spleefAlive.iterator().next());
        for (UUID id : new ArrayList<>(spleefAlive)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) sendHome(p, spleefReturn);
            spleefAlive.remove(id);
        }
        spleefReturn.clear();
        for (Location loc : spleefBlocks) loc.getBlock().setType(Material.AIR, false);
        spleefBlocks.clear();
        spleefFloor.clear();
        spleefBalls.clear();
        if (winner != null) {
            int prize = cfg("spleef.prize", 25);
            plugin.shops.giveGalaxyCoins(winner, prize);
            broadcastTitle("🏆 " + winner.getName() + " WINS! 🏆", NamedTextColor.GOLD,
                    "Supernova Spleef champion: +" + prize + " Galaxy Coins");
            Bukkit.broadcast(Component.text(winner.getName() + " won the Supernova Spleef and got "
                    + prize + " Galaxy Coins!", NamedTextColor.GOLD));
        } else {
            Bukkit.broadcast(Component.text("The Supernova Spleef is over!", NamedTextColor.GRAY));
        }
    }

    // =====================================================================
    // Verity: Strength III, a yellow arena and one-shot snowballs, until
    // the song ends. (The song is "It's me, It's Verity" by Horror Skunx;
    // a plugin can't stream YouTube, so the server plays a dark note-block
    // version itself for the length of the song.)
    // =====================================================================

    private ItemStack verityBall() {
        ItemStack it = new ItemStack(Material.SNOWBALL);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Verity", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("One hit pops a Fight Totem.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(verityKey, PersistentDataType.STRING, "1");
        it.setItemMeta(m);
        return it;
    }

    private boolean isVerityBall(ItemStack it) {
        return it != null && it.getType() == Material.SNOWBALL && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(verityKey, PersistentDataType.STRING);
    }

    private void startVerity(Player adminPlayer) {
        List<Player> players = eligible();
        if (players.isEmpty()) {
            adminPlayer.sendMessage(Component.text("Nobody can join Verity right now.", NamedTextColor.RED));
            return;
        }
        World w = overworld();
        int cx = cfg("verity.x", 0), cy = cfg("verity.y", 240), cz = cfg("verity.z", 24000);
        verityCenter = new Location(w, cx, cy, cz);
        if (!Arenas.built(w, cx, cy, cz)) {
            Arenas.build(w, cx, cy, cz, 12, Material.YELLOW_CONCRETE, Material.GOLD_BLOCK, 6);
        }
        verityRunning = true;
        verityLeft = Math.max(20, cfg("verity.seconds", 150));
        verityNote = 0;
        broadcastTitle("🟡 VERITY 🟡", NamedTextColor.YELLOW, "It's me... it's Verity.");
        Bukkit.broadcast(Component.text("VERITY is here! Strength III, Fight Totems and one-shot Verity snowballs "
                + "until the song ends. Get popped and you go home!", NamedTextColor.YELLOW));
        for (Player p : players) {
            verityReturn.put(p.getUniqueId(), p.getLocation().clone());
            Location spawn = verityCenter.clone().add(random.nextInt(11) - 5, 1, random.nextInt(11) - 5);
            p.teleport(spawn);
            verityAlive.add(p.getUniqueId());
            plugin.fightTotem.protect(p, "Verity", this::verityOut);
            ItemStack balls = verityBall();
            balls.setAmount(64);
            plugin.give(p, balls);
        }
        verityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::verityTick, 20L, 20L);
    }

    private void verityTick() {
        if (!verityRunning) return;
        verityLeft--;
        // the "song": a slow, dark note-block melody on loop (the server's own version)
        final int[] notes = {12, 11, 12, 7, 5, 7, 3, 2};
        float pitch = (float) Math.pow(2.0, (notes[verityNote++ % notes.length] - 12) / 12.0);
        for (UUID id : new ArrayList<>(verityAlive)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                verityAlive.remove(id);
                verityReturn.remove(id);
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 2, true, false, false)); // Strength III
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, pitch);
            int have = 0;
            for (ItemStack it : p.getInventory().getContents()) if (isVerityBall(it)) have += it.getAmount();
            if (have < 16) {
                ItemStack more = verityBall();
                more.setAmount(16);
                plugin.give(p, more);
            }
            if (p.getLocation().distanceSquared(verityCenter) > 30 * 30) verityOut(p);
            else plugin.bar(p, "Verity: " + verityLeft + "s of song left - " + verityAlive.size() + " still in",
                    NamedTextColor.YELLOW);
        }
        if (verityLeft <= 0 || verityAlive.isEmpty()) endVerity(false);
    }

    /** The Fight Totem popped: Verity sends you back to where you were. */
    private void verityOut(Player p) {
        if (!verityAlive.remove(p.getUniqueId())) return;
        removeVerityBalls(p);
        sendHome(p, verityReturn);
        p.sendMessage(Component.text("Your totem popped! Verity sends you back...", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text(p.getName() + " was popped out of Verity! " + verityAlive.size()
                + " left.", NamedTextColor.GOLD));
        if (verityRunning && verityAlive.isEmpty()) endVerity(false);
    }

    private void removeVerityBalls(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isVerityBall(contents[i])) p.getInventory().setItem(i, null);
        }
    }

    private void endVerity(boolean forced) {
        if (!verityRunning) return;
        verityRunning = false;
        if (verityTask != null) verityTask.cancel();
        verityTask = null;
        int survivors = verityAlive.size();
        for (UUID id : new ArrayList<>(verityAlive)) {
            Player p = Bukkit.getPlayer(id);
            verityAlive.remove(id);
            if (p == null) {
                verityReturn.remove(id);
                continue;
            }
            removeVerityBalls(p);
            sendHome(p, verityReturn);
            if (!forced && survivors > 0) {
                int prize = cfg("verity.prize", 10);
                plugin.shops.giveGalaxyCoins(p, prize);
                p.sendMessage(Component.text("You survived Verity until the song ended! +" + prize
                        + " Galaxy Coins.", NamedTextColor.GREEN));
            }
        }
        verityReturn.clear();
        Bukkit.broadcast(Component.text("The Verity song has ended.", NamedTextColor.GRAY));
    }

    // =====================================================================
    // Galaxy Rush: Speed III under a galaxy night sky
    // =====================================================================

    private void toggleRush() {
        if (rushOn) {
            stopRush();
            Bukkit.broadcast(Component.text("Galaxy Rush is over.", NamedTextColor.GRAY));
            return;
        }
        rushOn = true;
        rushLeft = Math.max(20, cfg("rush.seconds", 120));
        broadcastTitle("🌌 GALAXY RUSH 🌌", NamedTextColor.LIGHT_PURPLE, "Speed III for everyone!");
        Bukkit.broadcast(Component.text("GALAXY RUSH! Everyone gets Speed III under the galaxy sky!",
                NamedTextColor.LIGHT_PURPLE));
        for (Player p : Bukkit.getOnlinePlayers()) p.setPlayerTime(18000L, false); // night: the galaxy comes out
        rushTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!rushOn) return;
            rushLeft--;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, true, false, false)); // Speed III
                Location l = p.getLocation().add(0, 2.4, 0);
                p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, l, 3, 0.5, 0.2, 0.5, 0.02);
                p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, p.getLocation().add(0, 1, 0),
                        2, 0.5, 0.5, 0.5, 0.02);
            }
            if (rushLeft <= 0) {
                stopRush();
                Bukkit.broadcast(Component.text("Galaxy Rush is over.", NamedTextColor.GRAY));
            }
        }, 20L, 20L);
    }

    private void stopRush() {
        rushOn = false;
        if (rushTask != null) rushTask.cancel();
        rushTask = null;
        for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
    }

    // =====================================================================
    // Meteor Shower
    // =====================================================================

    private void toggleMeteor() {
        if (meteorOn) {
            meteorOn = false;
            if (meteorTask != null) meteorTask.cancel();
            meteorTask = null;
            Bukkit.broadcast(Component.text("The meteor shower passes.", NamedTextColor.GRAY));
            return;
        }
        meteorOn = true;
        broadcastTitle("☄ METEOR SHOWER ☄", NamedTextColor.GOLD, "Watch the sky... and dodge!");
        Bukkit.broadcast(Component.text("A METEOR SHOWER! Meteors are crashing down around everyone!",
                NamedTextColor.GOLD));
        meteorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (plugin.fightTotem.isProtected(p)) continue; // never bomb a boss fight or duel
                World w = p.getWorld();
                if (w.getEnvironment() != World.Environment.NORMAL) continue;
                Location from = p.getLocation().add(random.nextInt(17) - 8, 26, random.nextInt(17) - 8);
                org.bukkit.entity.LargeFireball fb = w.spawn(from, org.bukkit.entity.LargeFireball.class);
                Vector to = p.getLocation().add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3).toVector()
                        .subtract(from.toVector()).normalize().multiply(1.9);
                fb.setDirection(to);
                fb.setVelocity(to);
                fb.setYield(0f); // the boom is done by hand on impact, so it never breaks blocks
                fb.setIsIncendiary(false);
                meteors.add(fb.getUniqueId());
                w.spawnParticle(org.bukkit.Particle.FLAME, from, 12, 0.3, 0.3, 0.3, 0.02);
            }
        }, 40L, 60L);
    }

    // =====================================================================
    // Moon Gravity
    // =====================================================================

    private void toggleMoon() {
        if (moonOn) {
            stopMoon();
            Bukkit.broadcast(Component.text("Gravity is back to normal.", NamedTextColor.GRAY));
            return;
        }
        moonOn = true;
        moonLeft = Math.max(20, cfg("moon.seconds", 90));
        broadcastTitle("🌙 MOON GRAVITY 🌙", NamedTextColor.AQUA, "Boing!");
        Bukkit.broadcast(Component.text("MOON GRAVITY! Everyone bounces high and falls slowly!",
                NamedTextColor.AQUA));
        moonTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!moonOn) return;
            moonLeft--;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 3, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, true, false, false));
                if (random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 0.3, 0),
                            2, 0.3, 0.1, 0.3, 0.01);
                }
            }
            if (moonLeft <= 0) {
                stopMoon();
                Bukkit.broadcast(Component.text("Gravity is back to normal.", NamedTextColor.GRAY));
            }
        }, 20L, 20L);
    }

    private void stopMoon() {
        moonOn = false;
        if (moonTask != null) moonTask.cancel();
        moonTask = null;
    }

    // =====================================================================
    // Star Rain
    // =====================================================================

    private void toggleStarRain() {
        if (starRainOn) {
            starRainOn = false;
            if (starRainTask != null) starRainTask.cancel();
            starRainTask = null;
            Bukkit.broadcast(Component.text("The star rain stops.", NamedTextColor.GRAY));
            return;
        }
        starRainOn = true;
        broadcastTitle("⭐ STAR RAIN ⭐", NamedTextColor.DARK_PURPLE, "Treasure is falling from the sky!");
        Bukkit.broadcast(Component.text("STAR RAIN! Galaxy Coins, amethyst shards and ender pearls are falling!",
                NamedTextColor.DARK_PURPLE));
        starRainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                World w = p.getWorld();
                if (w.getEnvironment() != World.Environment.NORMAL) continue;
                for (int i = 0; i < 2; i++) {
                    Location at = p.getLocation().add(random.nextInt(13) - 6, 11 + random.nextInt(3), random.nextInt(13) - 6);
                    ItemStack drop;
                    int roll = random.nextInt(100);
                    if (roll < 20) drop = plugin.shops.galaxyCoinItem(1);
                    else if (roll < 55) drop = new ItemStack(Material.ENDER_PEARL);
                    else drop = new ItemStack(Material.AMETHYST_SHARD, 2);
                    org.bukkit.entity.Item it = w.dropItem(at, drop);
                    it.setVelocity(new Vector(0, -0.4, 0));
                    it.setPickupDelay(12);
                }
            }
        }, 20L, 15L);
    }

    // =====================================================================
    // Event listeners: spleef fireballs, Verity snowballs, meteor impacts
    // =====================================================================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!spleefRunning || !spleefAlive.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - shootCd.getOrDefault(p.getUniqueId(), 0L) < 350L) return;
        shootCd.put(p.getUniqueId(), now);
        Vector dir = p.getEyeLocation().getDirection();
        org.bukkit.entity.Fireball fb = p.launchProjectile(org.bukkit.entity.Fireball.class, dir.clone().multiply(1.6));
        fb.setDirection(dir);
        fb.setYield(0f); // the floor breaking is done by hand, so nothing else can be griefed
        fb.setIsIncendiary(false);
        fb.setShooter(p);
        spleefBalls.add(fb.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.1f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (spleefBalls.remove(proj.getUniqueId())) {
            if (e.getHitBlock() != null) { // break a little patch of the spleef floor
                Location hit = e.getHitBlock().getLocation();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Location l = hit.clone().add(dx, 0, dz);
                        if (spleefFloor.remove(l)) {
                            l.getBlock().setType(Material.AIR, false);
                            l.getWorld().spawnParticle(org.bukkit.Particle.LAVA, l.clone().add(0.5, 0.5, 0.5),
                                    6, 0.3, 0.2, 0.3, 0.05);
                        }
                    }
                }
            }
            if (e.getHitEntity() instanceof Player victim && spleefAlive.contains(victim.getUniqueId())) {
                if (proj.getShooter() instanceof Player s) victim.damage(2.0, s);
                else victim.damage(2.0);
                Vector away = victim.getLocation().toVector().subtract(proj.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 1.0E-6) victim.setVelocity(away.normalize().multiply(0.9).setY(0.3));
            }
            proj.remove();
            return;
        }
        if (meteors.remove(proj.getUniqueId())) {
            Location at = proj.getLocation();
            at.getWorld().createExplosion(at, (float) cfg("meteor.power", 2), false, false);
            proj.remove();
        }
    }

    @EventHandler
    public void onVerityHit(EntityDamageByEntityEvent e) {
        if (!verityRunning) return;
        if (!(e.getDamager() instanceof Snowball ball)) return;
        if (!isVerityBall(ball.getItem())) return;
        if (!(ball.getShooter() instanceof Player shooter) || !verityAlive.contains(shooter.getUniqueId())) return;
        if (!(e.getEntity() instanceof Player victim) || !verityAlive.contains(victim.getUniqueId())) {
            e.setCancelled(true); // Verity snowballs only work inside the Verity event
            return;
        }
        e.setDamage(1000.0); // one shot: the Fight Totem pops and the victim is sent home
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (spleefAlive.contains(p.getUniqueId())) {
            plugin.fightTotem.cleanup(p);
            spleefEliminate(p.getUniqueId(), false);
        }
        if (verityAlive.contains(p.getUniqueId())) {
            plugin.fightTotem.cleanup(p);
            verityAlive.remove(p.getUniqueId());
            verityReturn.remove(p.getUniqueId());
            if (verityRunning && verityAlive.isEmpty()) endVerity(false);
        }
    }

    // =====================================================================
    // Stop everything
    // =====================================================================

    void stopAllEvents() {
        if (spleefRunning) endSpleef(true);
        if (verityRunning) endVerity(true);
        if (rushOn) stopRush();
        if (meteorOn) toggleMeteor();
        if (moonOn) stopMoon();
        if (starRainOn) toggleStarRain();
        Bukkit.broadcast(Component.text("All events have ended.", NamedTextColor.GRAY));
    }

    void shutdown() {
        if (spleefTask != null) spleefTask.cancel();
        if (verityTask != null) verityTask.cancel();
        if (rushTask != null) rushTask.cancel();
        if (meteorTask != null) meteorTask.cancel();
        if (moonTask != null) moonTask.cancel();
        if (starRainTask != null) starRainTask.cancel();
        if (spleefRunning) {
            for (UUID id : new ArrayList<>(spleefAlive)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) sendHome(p, spleefReturn);
            }
            for (Location loc : spleefBlocks) loc.getBlock().setType(Material.AIR, false);
        }
        if (verityRunning) {
            for (UUID id : new ArrayList<>(verityAlive)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) sendHome(p, verityReturn);
            }
        }
        if (rushOn) for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
    }

    boolean anyRunning() {
        return spleefRunning || verityRunning || rushOn || meteorOn || moonOn || starRainOn;
    }
}
