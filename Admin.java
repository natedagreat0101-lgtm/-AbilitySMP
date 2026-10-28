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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The admin panel (/panel): announcements, heal-all, sky and weather, upgrade stations,
 * the power shop manager, music, and the Server Events menu (which lives in GalaxyEvents).
 * The Halloween events were retired in the Galactic Update (v10).
 */
final class Admin implements Listener {

    static final class AdminHolder implements InventoryHolder {
        /** 0 = main, 1 = music, 2 = events. */
        final int menu;
        Inventory inv;

        AdminHolder(boolean music) {
            this.menu = music ? 1 : 0;
        }

        AdminHolder(int menu) {
            this.menu = menu;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final AbilitySMP plugin;
    private final Set<UUID> awaiting = new HashSet<>();
    private String skyMode = "NONE";
    private BukkitTask songTask;
    private String currentTrack;
    final GalaxyEvents events;

    Admin(AbilitySMP plugin) {
        this.plugin = plugin;
        this.events = new GalaxyEvents(plugin, this);
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

    void openMain(Player p) {
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

        inv.setItem(28, item(Material.EMERALD_BLOCK, "Power Shop manager", NamedTextColor.GREEN,
                "Stock a power and discount it.", "Everyone sees it at the top of the screen."));
        inv.setItem(30, item(Material.CLOCK, "Restock the power shop now", NamedTextColor.AQUA,
                "It also restocks by itself every few minutes."));
        inv.setItem(32, item(Material.FIREWORK_ROCKET, "Server Events", NamedTextColor.LIGHT_PURPLE,
                "Supernova Spleef, Galaxy Rush, Verity,", "Meteor Shower, Moon Gravity and Star Rain."));
        inv.setItem(43, item(Material.REDSTONE_BLOCK, "Stop all events", NamedTextColor.RED));
    }

    private void openEvents(Player p) {
        AdminHolder holder = new AdminHolder(2);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Admin Panel - Events", NamedTextColor.DARK_PURPLE));
        holder.inv = inv;
        events.fillEvents(inv);
        p.openInventory(inv);
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
        inv.setItem(20, item(Material.NOTE_BLOCK, "Note Song: Galaxy Theme", NamedTextColor.LIGHT_PURPLE,
                "A custom note-block song, played live", "by the server itself. Click to play!"));
        inv.setItem(21, item(Material.NOTE_BLOCK, "Note Song: Verity Theme", NamedTextColor.YELLOW,
                "A dark note-block song, played live", "by the server itself. Click to play!"));
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
        if (h.menu == 2) {
            events.eventsClick(p, slot);
        } else if (h.menu == 1) {
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
            case 32 -> {
                openEvents(p);
                return false;
            }
            case 14 -> plugin.unbound.addStation(p, p.getTargetBlockExact(6));
            case 19 -> setSky("DAY");
            case 20 -> setSky("ORANGE");
            case 21 -> setSky("NIGHT");
            case 22 -> setSky("NONE");
            case 24 -> setWeather(false, false);
            case 25 -> setWeather(true, false);
            case 26 -> setWeather(true, true);
            case 43 -> events.stopAllEvents();
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
        } else if (slot == 20) {
            playNoteSong(false);
            p.sendMessage(Component.text("Playing the Galaxy Theme for everyone.", NamedTextColor.LIGHT_PURPLE));
        } else if (slot == 21) {
            playNoteSong(true);
            p.sendMessage(Component.text("Playing the Verity Theme for everyone.", NamedTextColor.YELLOW));
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
    }

    // =====================================================================
    // Music: vanilla discs (and resource-pack sounds) by key
    // =====================================================================

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
        if (songTask != null) songTask.cancel();
        songTask = null;
        if (currentTrack == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.stopSound(currentTrack);
        currentTrack = null;
    }

    // =====================================================================
    // Custom music: note-block songs played live by the server.
    // (A plugin cannot stream YouTube or audio files - for real songs, put
    // the audio in a resource pack and add its key under music.tracks.)
    // =====================================================================

    /** Plays a little song made of note-block sounds for everyone. Notes are semitones (0-24). */
    private void playNoteSong(boolean dark) {
        stopTrack();
        final int[] notes = dark
                ? new int[]{12, 11, 12, 7, 5, 7, 3, 2, 12, 11, 12, 7, 5, 3, 2, 0}
                : new int[]{12, 16, 19, 24, 19, 16, 21, 19, 16, 12, 14, 16, 19, 24, 28, 24};
        final org.bukkit.Sound instrument = dark ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS
                : org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
        songTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int i = 0;

            @Override
            public void run() {
                if (i >= notes.length) {
                    songTask.cancel();
                    songTask = null;
                    return;
                }
                float pitch = (float) Math.pow(2.0, (notes[i] - 12) / 12.0);
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), instrument, 0.9f, pitch);
                i++;
            }
        }, 0L, dark ? 6L : 4L);
    }

    void shutdown() {
        if (songTask != null) songTask.cancel();
        events.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
    }
}
