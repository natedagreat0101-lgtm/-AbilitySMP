package me.abilitysmp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Admin panel (/panel, OP only): announcements, sky presets, weather, music, Halloween events.
 *
 * Sky note: Minecraft's sky colour comes from the time of day and weather, so the presets are
 * Day, Orange Sunset, Night and storms. Free colour picking would need a resource pack or data pack.
 */
final class Admin implements Listener {

    static final class AdminHolder implements InventoryHolder {
        Inventory inv;
        final boolean musicMenu;

        AdminHolder(boolean musicMenu) {
            this.musicMenu = musicMenu;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final AbilitySMP plugin;
    private final Set<UUID> awaiting = new HashSet<>();
    private final Map<UUID, Long> cursed = new HashMap<>();
    private final List<Location> lanterns = new ArrayList<>();
    private final Random random = new Random();

    private String skyMode = "NONE";
    private boolean lanternsOn;
    private boolean musicOn;
    private boolean curseOn;
    private BukkitTask lanternTask;
    private BukkitTask musicTask;
    private String currentTrack;

    Admin(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    // Command: /panel   /panel music <track|key|stop>
    // =====================================================================

    void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("music")) {
            if (args.length < 2) {
                p.sendMessage("Usage: /panel music <track name | sound key | stop>");
                return;
            }
            if (args[1].equalsIgnoreCase("stop")) {
                stopTrack();
                p.sendMessage("Music stopped.");
                return;
            }
            String key = plugin.getConfig().getString("music.tracks." + args[1], args[1]);
            playTrack(key);
            p.sendMessage("Playing " + key);
            return;
        }
        openMain(p);
    }

    // =====================================================================
    // Menus
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

    private void openMain(Player p) {
        AdminHolder holder = new AdminHolder(false);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Admin Panel", NamedTextColor.DARK_RED));
        holder.inv = inv;
        fillMain(inv);
        p.openInventory(inv);
    }

    private void fillMain(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK);
        for (int s = 0; s < 54; s++) inv.setItem(s, filler);

        inv.setItem(10, item(Material.WRITABLE_BOOK, "Send admin message", NamedTextColor.GOLD,
                "Click, then type in chat.", "Shows as [Admin] YourName: message"));
        inv.setItem(12, item(Material.GOLDEN_APPLE, "Heal and feed everyone", NamedTextColor.GREEN));
        inv.setItem(14, item(Material.ANVIL, "Make looked-at block an Upgrade Station", NamedTextColor.AQUA,
                "Look at a block (within 6 blocks), then click.", "Players right-click it to upgrade powers."));
        inv.setItem(16, item(Material.JUKEBOX, "Music", NamedTextColor.LIGHT_PURPLE, "Open the track list"));

        inv.setItem(19, item(Material.SUNFLOWER, "Sky: Day", NamedTextColor.YELLOW,
                skyMode.equals("DAY") ? "Active" : "Click to set"));
        inv.setItem(20, item(Material.ORANGE_DYE, "Sky: Orange sunset", NamedTextColor.GOLD,
                skyMode.equals("ORANGE") ? "Active" : "Click to set"));
        inv.setItem(21, item(Material.BLACK_DYE, "Sky: Night", NamedTextColor.DARK_BLUE,
                skyMode.equals("NIGHT") ? "Active" : "Click to set"));
        inv.setItem(22, item(Material.BARRIER, "Sky: Back to normal", NamedTextColor.WHITE));
        inv.setItem(24, item(Material.BUCKET, "Weather: Clear", NamedTextColor.WHITE));
        inv.setItem(25, item(Material.WATER_BUCKET, "Weather: Rain", NamedTextColor.AQUA));
        inv.setItem(26, item(Material.LIGHTNING_ROD, "Weather: Thunderstorm", NamedTextColor.DARK_AQUA));

        inv.setItem(37, item(Material.JACK_O_LANTERN, "Halloween: Jack-o-Lanterns", NamedTextColor.GOLD, on(lanternsOn)));
        inv.setItem(38, item(Material.NOTE_BLOCK, "Halloween: Spooky music", NamedTextColor.GOLD, on(musicOn)));
        inv.setItem(39, item(Material.WITHER_SKELETON_SKULL, "Halloween: Mob Curse", NamedTextColor.DARK_RED,
                on(curseOn), "Killing a hostile mob seals", "your power for a while"));
        inv.setItem(40, item(Material.PUMPKIN_PIE, "Trick or Treat Night", NamedTextColor.GOLD,
                on(plugin.tot.isActive()), "Little huts appear around players.", "Candy can be traded for Halloween Coins."));
        inv.setItem(41, item(Material.CARVED_PUMPKIN, "Full Halloween Night", NamedTextColor.GOLD,
                "Orange sky + lanterns + music + curse"));
        inv.setItem(43, item(Material.REDSTONE_BLOCK, "Stop all events", NamedTextColor.RED));
        inv.setItem(28, item(Material.EMERALD_BLOCK, "Power Shop manager", NamedTextColor.GREEN,
                "Stock a power and discount it.", "Everyone sees it at the top of the screen."));
        inv.setItem(30, item(Material.CLOCK, "Restock the power shop now", NamedTextColor.AQUA,
                "It also restocks by itself every few minutes."));
    }

    private void openMusic(Player p) {
        AdminHolder holder = new AdminHolder(true);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Admin Panel - Music", NamedTextColor.DARK_PURPLE));
        holder.inv = inv;
        List<String> names = trackNames();
        for (int k = 0; k < names.size() && k < 18; k++) {
            inv.setItem(k, item(Material.MUSIC_DISC_13, names.get(k), NamedTextColor.LIGHT_PURPLE,
                    plugin.getConfig().getString("music.tracks." + names.get(k), ""), "Click to play for everyone"));
        }
        inv.setItem(18, item(Material.ARROW, "Back", NamedTextColor.WHITE));
        inv.setItem(22, item(Material.BARRIER, "Stop music", NamedTextColor.RED));
        p.openInventory(inv);
    }

    private List<String> trackNames() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("music.tracks");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    // =====================================================================
    // Clicks
    // =====================================================================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("abilitysmp.admin")) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getRawSlot();
        if (h.musicMenu) {
            musicClick(p, slot);
        } else if (mainClick(p, slot)) {
            fillMain(h.inv); // refresh ON/OFF labels
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AdminHolder) e.setCancelled(true);
    }

    /** Returns true if the main menu stays open and should be refreshed. */
    private boolean mainClick(Player p, int slot) {
        switch (slot) {
            case 10 -> {
                startMessage(p);
                return false;
            }
            case 16 -> {
                openMusic(p);
                return false;
            }
            case 12 -> healAll();
            case 14 -> plugin.unbound.addStation(p, p.getTargetBlockExact(6));
            case 19 -> setSky("DAY");
            case 20 -> setSky("ORANGE");
            case 21 -> setSky("NIGHT");
            case 22 -> setSky("NONE");
            case 24 -> setWeather(false, false);
            case 25 -> setWeather(true, false);
            case 26 -> setWeather(true, true);
            case 37 -> toggleLanterns();
            case 38 -> toggleHalloweenMusic();
            case 39 -> toggleCurse();
            case 40 -> toggleTrickOrTreat();
            case 41 -> fullHalloween();
            case 43 -> stopAllEvents();
            case 28 -> {
                plugin.shops.openStockManager(p);
                return false;
            }
            case 30 -> plugin.shops.restock(true);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void musicClick(Player p, int slot) {
        if (slot == 18) {
            openMain(p);
        } else if (slot == 22) {
            stopTrack();
        } else {
            List<String> names = trackNames();
            if (slot >= 0 && slot < names.size()) {
                playTrack(plugin.getConfig().getString("music.tracks." + names.get(slot), ""));
                p.sendMessage(Component.text("Playing " + names.get(slot) + " for everyone.", NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    // =====================================================================
    // Admin messages
    // =====================================================================

    private void startMessage(Player p) {
        awaiting.add(p.getUniqueId());
        p.closeInventory();
        p.sendMessage(Component.text("Type your announcement in chat now (or type cancel).", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!awaiting.remove(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) {
                p.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
                return;
            }
            Bukkit.broadcast(Component.text("[Admin] ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(p.getName() + ": ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(msg, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
            for (Player all : Bukkit.getOnlinePlayers()) {
                all.playSound(all.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            }
        });
    }

    // =====================================================================
    // Heal, sky, weather
    // =====================================================================

    private void healAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(a != null ? a.getValue() : 20.0);
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
        Bukkit.broadcast(Component.text("An admin healed and fed everyone!", NamedTextColor.GREEN));
    }

    private void setSky(String mode) {
        skyMode = mode;
        for (Player p : Bukkit.getOnlinePlayers()) applySky(p);
    }

    void applySky(Player p) {
        switch (skyMode) {
            case "DAY" -> p.setPlayerTime(6000L, false);
            case "ORANGE" -> p.setPlayerTime(12600L, false);
            case "NIGHT" -> p.setPlayerTime(18000L, false);
            default -> p.resetPlayerTime();
        }
    }

    private void setWeather(boolean storm, boolean thunder) {
        for (World w : Bukkit.getWorlds()) {
            w.setStorm(storm);
            w.setThundering(thunder);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applySky(e.getPlayer());
        if (musicOn) play(e.getPlayer(), hallowMusicKey());
    }

    // =====================================================================
    // Music
    // =====================================================================

    private String hallowMusicKey() {
        return plugin.getConfig().getString("halloween.music", "minecraft:music_disc.13");
    }

    private void play(Player p, String key) {
        try {
            p.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(key),
                    net.kyori.adventure.sound.Sound.Source.RECORD, 1f, 1f), net.kyori.adventure.sound.Sound.Emitter.self());
        } catch (RuntimeException ignored) {
            // bad sound key
        }
    }

    private void playTrack(String key) {
        stopTrack();
        if (key == null || key.isEmpty()) return;
        currentTrack = key;
        for (Player p : Bukkit.getOnlinePlayers()) play(p, key);
    }

    private void stopTrack() {
        if (currentTrack == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.stopSound(currentTrack);
        currentTrack = null;
    }

    // =====================================================================
    // Halloween events
    // =====================================================================

    private void toggleLanterns() {
        if (lanternsOn) stopLanterns();
        else startLanterns();
    }

    private void startLanterns() {
        lanternsOn = true;
        long interval = Math.max(1, plugin.getConfig().getInt("halloween.lantern-interval-seconds", 5)) * 20L;
        lanternTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnLanterns, 40L, interval);
    }

    private void stopLanterns() {
        lanternsOn = false;
        if (lanternTask != null) lanternTask.cancel();
        lanternTask = null;
        removeLanterns();
    }

    private void spawnLanterns() {
        int max = plugin.getConfig().getInt("halloween.max-lanterns", 120);
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            for (int k = 0; k < 3; k++) {
                int dx = (4 + random.nextInt(15)) * (random.nextBoolean() ? 1 : -1);
                int dz = (4 + random.nextInt(15)) * (random.nextBoolean() ? 1 : -1);
                Block top = w.getHighestBlockAt(p.getLocation().getBlockX() + dx, p.getLocation().getBlockZ() + dz);
                Block above = top.getRelative(BlockFace.UP);
                if (!top.getType().isSolid() || !above.getType().isAir()) continue;
                above.setType(Material.JACK_O_LANTERN, false);
                if (above.getBlockData() instanceof Directional dir) {
                    dir.setFacing(FACES[random.nextInt(FACES.length)]);
                    above.setBlockData(dir, false);
                }
                lanterns.add(above.getLocation());
            }
        }
        while (lanterns.size() > max) {
            clearLantern(lanterns.remove(0));
        }
    }

    private void clearLantern(Location loc) {
        Block b = loc.getBlock();
        if (b.getType() == Material.JACK_O_LANTERN) b.setType(Material.AIR, false);
    }

    private void removeLanterns() {
        for (Location loc : lanterns) clearLantern(loc);
        lanterns.clear();
    }

    private void toggleHalloweenMusic() {
        if (musicOn) stopHalloweenMusic();
        else startHalloweenMusic();
    }

    private void startHalloweenMusic() {
        musicOn = true;
        final String key = hallowMusicKey();
        long period = Math.max(10, plugin.getConfig().getInt("halloween.music-length-seconds", 178)) * 20L;
        musicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) play(p, key);
        }, 0L, period);
    }

    private void stopHalloweenMusic() {
        musicOn = false;
        if (musicTask != null) musicTask.cancel();
        musicTask = null;
        String key = hallowMusicKey();
        for (Player p : Bukkit.getOnlinePlayers()) p.stopSound(key);
    }

    private void toggleCurse() {
        curseOn = !curseOn;
        if (!curseOn) cursed.clear();
        else Bukkit.broadcast(Component.text("The Mob Curse is active: killing a hostile mob seals your power!",
                NamedTextColor.DARK_RED));
    }

    private void toggleTrickOrTreat() {
        if (plugin.tot.isActive()) {
            plugin.tot.stop(true);
            if (musicOn) stopHalloweenMusic();
            setSky("NONE");
        } else {
            setSky("NIGHT");
            if (!musicOn) startHalloweenMusic();
            plugin.tot.start();
        }
    }

    private void fullHalloween() {
        if (skyMode.equals("ORANGE") && lanternsOn && musicOn && curseOn) {
            stopAllEvents();
            return;
        }
        setSky("ORANGE");
        if (!lanternsOn) startLanterns();
        if (!musicOn) startHalloweenMusic();
        if (!curseOn) curseOn = true;
        Bukkit.broadcast(Component.text("Halloween Night has begun... beware the curse!", NamedTextColor.GOLD));
    }

    private void stopAllEvents() {
        setSky("NONE");
        if (lanternsOn) stopLanterns();
        if (musicOn) stopHalloweenMusic();
        curseOn = false;
        cursed.clear();
        plugin.tot.stop(false);
        Bukkit.broadcast(Component.text("The Halloween events have ended.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!curseOn) return;
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || !(dead instanceof Enemy)) return;
        Player killer = dead.getKiller();
        if (killer == null) return;
        int sec = plugin.getConfig().getInt("halloween.curse-seconds", 60);
        cursed.put(killer.getUniqueId(), System.currentTimeMillis() + sec * 1000L);
        killer.sendMessage(Component.text("The curse seals your power for " + sec + " seconds!", NamedTextColor.DARK_RED));
        killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.7f);
    }

    boolean isCursed(Player p) {
        Long until = cursed.get(p.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            cursed.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    void sealedMessage(Player p) {
        Long until = cursed.get(p.getUniqueId());
        long left = until == null ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
        p.sendActionBar(Component.text("Your power is sealed by the curse for " + left + "s", NamedTextColor.DARK_RED));
    }

    void shutdown() {
        if (lanternTask != null) lanternTask.cancel();
        if (musicTask != null) musicTask.cancel();
        removeLanterns();
        for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
    }
}
