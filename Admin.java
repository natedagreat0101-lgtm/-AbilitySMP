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
        /** 0 = main panel, 1 = music, 2 = server events (v9). */
        final int menu;

        AdminHolder(boolean musicMenu) {
            this.musicMenu = musicMenu;
            this.menu = musicMenu ? 1 : 0;
        }

        AdminHolder(int menu) {
            this.menu = menu;
            this.musicMenu = menu == 1;
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

    // v9 server events
    private boolean bloodMoonOn;
    private boolean horsemanMoonOn;
    private boolean discoOn;
    private BukkitTask moonTask;
    private BukkitTask discoTask;
    private BukkitTask songTask;
    private final Map<String, BukkitTask> rainTasks = new HashMap<>();
    private final List<org.bukkit.entity.Entity> snapProps = new ArrayList<>();
    private final Map<Location, org.bukkit.block.data.BlockData> snapBlocks = new HashMap<>();
    private boolean snapRunning;

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
        inv.setItem(32, item(Material.FIREWORK_ROCKET, "Server Events", NamedTextColor.LIGHT_PURPLE,
                "Blood Moon, Horseman Moon, Disco Party,", "item rain and... THE SNAP."));
    }

    private void openEvents(Player p) {
        AdminHolder holder = new AdminHolder(2);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Admin Panel - Events", NamedTextColor.DARK_PURPLE));
        holder.inv = inv;
        fillEvents(inv);
        p.openInventory(inv);
    }

    private void fillEvents(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK);
        for (int s = 0; s < 54; s++) inv.setItem(s, filler);
        inv.setItem(10, item(Material.REDSTONE, "Blood Moon", NamedTextColor.DARK_RED, on(bloodMoonOn),
                "The sky goes dark and strong monsters", "rise around every player."));
        inv.setItem(11, item(Material.JACK_O_LANTERN, "Headless Horseman Moon", NamedTextColor.GOLD, on(horsemanMoonOn),
                "An orange moon: pumpkin-headed minions", "of the Horseman stalk the players."));
        inv.setItem(12, item(Material.NOTE_BLOCK, "Disco Party", NamedTextColor.LIGHT_PURPLE, on(discoOn),
                "Music, sparkles and speed for everyone!"));
        inv.setItem(13, item(Material.COOKED_BEEF, "Raining Tacos", NamedTextColor.YELLOW, on(rainTasks.containsKey("tacos")),
                "Tacos fall from the sky. 🌮"));
        inv.setItem(14, item(Material.DIAMOND, "Raining Diamonds", NamedTextColor.AQUA, on(rainTasks.containsKey("diamonds")),
                "Diamonds fall from the sky!"));
        inv.setItem(15, item(Material.GOLD_NUGGET, "Raining Gold", NamedTextColor.GOLD, on(rainTasks.containsKey("gold")),
                "Gold falls from the sky."));
        inv.setItem(16, item(Material.SUGAR, "Raining Candy", NamedTextColor.LIGHT_PURPLE, on(rainTasks.containsKey("candy")),
                "Halloween candy falls from the sky."));
        inv.setItem(29, item(Material.NETHER_STAR, "THE SNAP", NamedTextColor.DARK_PURPLE,
                snapRunning ? "The cutscene is playing..." : "Play the cutscene: Notazombiemc finds a button,",
                snapRunning ? "" : "presses it, and EVERY event starts at once!"));
        inv.setItem(31, item(Material.REDSTONE_BLOCK, "Stop all events", NamedTextColor.RED));
        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.WHITE));
    }

    private void eventsClick(Player p, int slot) {
        switch (slot) {
            case 10 -> toggleBloodMoon();
            case 11 -> toggleHorsemanMoon();
            case 12 -> toggleDisco();
            case 13 -> toggleRain("tacos");
            case 14 -> toggleRain("diamonds");
            case 15 -> toggleRain("gold");
            case 16 -> toggleRain("candy");
            case 29 -> theSnap();
            case 31 -> stopAllEvents();
            case 45 -> {
                openMain(p);
                return;
            }
            default -> {
                return;
            }
        }
        // refresh the ON/OFF labels in the menu the admin is looking at
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof AdminHolder h && h.menu == 2) {
            fillEvents(h.inv);
        }
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
        inv.setItem(20, item(Material.NOTE_BLOCK, "Note Song: Spooky Jingle", NamedTextColor.DARK_PURPLE,
                "A custom note-block song, played live", "by the server itself. Click to play!"));
        inv.setItem(21, item(Material.NOTE_BLOCK, "Note Song: Taco Party", NamedTextColor.YELLOW,
                "A custom note-block song, played live", "by the server itself. Click to play!"));
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
            eventsClick(p, slot);
        } else if (h.musicMenu) {
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
        } else if (slot == 20) {
            playNoteSong(true);
            p.sendMessage(Component.text("Playing the Spooky Jingle for everyone.", NamedTextColor.DARK_PURPLE));
        } else if (slot == 21) {
            playNoteSong(false);
            p.sendMessage(Component.text("Playing Taco Party for everyone.", NamedTextColor.YELLOW));
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
        if (songTask != null) songTask.cancel();
        songTask = null;
        if (currentTrack == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.stopSound(currentTrack);
        currentTrack = null;
    }

    // =====================================================================
    // Custom music: note-block songs played live by the server (v9)
    // =====================================================================

    /** Plays a little song made of note-block sounds for everyone. Notes are semitones (0-24). */
    private void playNoteSong(boolean spooky) {
        stopTrack();
        final int[] notes = spooky
                ? new int[]{12, 11, 12, 7, 8, 4, 7, 3, 12, 11, 12, 19, 17, 15, 11, 12}
                : new int[]{12, 16, 19, 16, 21, 19, 16, 12, 14, 16, 19, 24, 19, 16, 14, 12};
        final org.bukkit.Sound instrument = spooky ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS
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
        }, 0L, spooky ? 5L : 4L);
    }

    // =====================================================================
    // Server events (v9): moons, disco, item rain and THE SNAP
    // =====================================================================

    private void broadcastTitle(String title, NamedTextColor color, String sub) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(title, color),
                    Component.text(sub, NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                            java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(500))));
        }
    }

    private void toggleBloodMoon() {
        if (bloodMoonOn) {
            stopMoons();
            Bukkit.broadcast(Component.text("The Blood Moon fades...", NamedTextColor.GRAY));
        } else {
            bloodMoonOn = true;
            startMoons();
            setSky("NIGHT");
            broadcastTitle("🩸 BLOOD MOON 🩸", NamedTextColor.DARK_RED, "The monsters are coming...");
            Bukkit.broadcast(Component.text("A BLOOD MOON rises! Strong monsters hunt every player!", NamedTextColor.DARK_RED));
        }
    }

    private void toggleHorsemanMoon() {
        if (horsemanMoonOn) {
            horsemanMoonOn = false;
            if (!bloodMoonOn) stopMoons();
            else moonSky();
            Bukkit.broadcast(Component.text("The Horseman Moon sets...", NamedTextColor.GRAY));
        } else {
            horsemanMoonOn = true;
            startMoons();
            moonSky();
            broadcastTitle("🎃 HORSEMAN MOON 🎃", NamedTextColor.GOLD, "His minions ride tonight...");
            Bukkit.broadcast(Component.text("The HORSEMAN MOON rises! Pumpkin-headed minions stalk the players!",
                    NamedTextColor.GOLD));
        }
    }

    private void moonSky() {
        setSky(bloodMoonOn ? "NIGHT" : horsemanMoonOn ? "ORANGE" : "NONE");
    }

    private void startMoons() {
        if (moonTask != null) return;
        moonTask = Bukkit.getScheduler().runTaskTimer(plugin, this::moonTick, 60L, 100L);
    }

    private void stopMoons() {
        bloodMoonOn = false;
        horsemanMoonOn = false;
        if (moonTask != null) moonTask.cancel();
        moonTask = null;
        moonSky();
    }

    private void moonTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            if (plugin.fightTotem.isProtected(p)) continue; // never ambush a boss fight or duel
            long nearby = p.getNearbyEntities(30, 12, 30).stream()
                    .filter(e -> e instanceof Enemy).count();
            if (nearby >= 8) continue; // cap the swarm
            Location at = p.getLocation().add((random.nextInt(17) - 8), 0, (random.nextInt(17) - 8));
            at.setY(w.getHighestBlockYAt(at) + 1);
            if (bloodMoonOn) {
                org.bukkit.entity.Zombie z = w.spawn(at, org.bukkit.entity.Zombie.class, mob -> {
                    mob.setShouldBurnInDay(false);
                    EntityEquipmentFix.armor(mob, Material.LEATHER_HELMET, 0x8B0000);
                    mob.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.STRENGTH, 20 * 60, 0, false, false, false));
                    mob.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SPEED, 20 * 60, 0, false, false, false));
                    mob.customName(Component.text("Blood Moon Hunter", NamedTextColor.DARK_RED));
                    mob.setTarget(p);
                });
                w.spawnParticle(org.bukkit.Particle.CRIMSON_SPORE, z.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.02);
            }
            if (horsemanMoonOn) {
                w.spawn(at, org.bukkit.entity.Zombie.class, mob -> {
                    mob.setShouldBurnInDay(false);
                    EntityEquipmentFix.armor(mob, Material.CARVED_PUMPKIN, -1);
                    mob.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
                    mob.getEquipment().setItemInMainHandDropChance(0f);
                    mob.customName(Component.text("Horseman Minion", NamedTextColor.GOLD));
                    mob.setTarget(p);
                });
                if (random.nextInt(4) == 0) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
                }
            }
        }
    }

    /** Tiny helper so the moon mobs can be dressed inside their spawn lambdas. */
    private static final class EntityEquipmentFix {
        static void armor(org.bukkit.entity.LivingEntity e, Material helmet, int rgb) {
            org.bukkit.inventory.EntityEquipment eq = e.getEquipment();
            if (eq == null) return;
            ItemStack h = new ItemStack(helmet);
            if (rgb >= 0 && h.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) {
                lm.setColor(org.bukkit.Color.fromRGB(rgb));
                h.setItemMeta(lm);
            }
            eq.setHelmet(h);
            eq.setHelmetDropChance(0f);
        }
    }

    private void toggleDisco() {
        if (discoOn) {
            stopDisco();
            Bukkit.broadcast(Component.text("The Disco Party is over.", NamedTextColor.GRAY));
            return;
        }
        discoOn = true;
        playNoteSong(false);
        broadcastTitle("🪩 DISCO PARTY 🪩", NamedTextColor.LIGHT_PURPLE, "Everybody dance!");
        Bukkit.broadcast(Component.text("DISCO PARTY! Music, sparkles and speed for everyone!", NamedTextColor.LIGHT_PURPLE));
        discoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location l = p.getLocation().add(0, 2.2, 0);
                p.getWorld().spawnParticle(org.bukkit.Particle.NOTE, l, 2, 0.4, 0.2, 0.4, 0.5);
                p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, p.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0.02);
                p.getWorld().spawnParticle(org.bukkit.Particle.WITCH, p.getLocation().add(0, 0.5, 0), 2, 0.4, 0.3, 0.4, 0.02);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, 30, 0, true, false, false));
                if (random.nextInt(8) == 0) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f,
                            0.5f + random.nextFloat());
                }
            }
        }, 10L, 10L);
    }

    private void stopDisco() {
        discoOn = false;
        if (discoTask != null) discoTask.cancel();
        discoTask = null;
    }

    private ItemStack rainItem(String kind) {
        switch (kind) {
            case "tacos" -> {
                ItemStack taco = new ItemStack(Material.COOKED_BEEF);
                ItemMeta m = taco.getItemMeta();
                m.displayName(Component.text("🌮 Taco", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                taco.setItemMeta(m);
                return taco;
            }
            case "diamonds" -> {
                return new ItemStack(Material.DIAMOND);
            }
            case "gold" -> {
                return new ItemStack(Material.GOLD_NUGGET, 2);
            }
            default -> {
                return plugin.tot.candyItem();
            }
        }
    }

    private void toggleRain(String kind) {
        BukkitTask old = rainTasks.remove(kind);
        if (old != null) {
            old.cancel();
            Bukkit.broadcast(Component.text("The " + kind + " rain stops.", NamedTextColor.GRAY));
            return;
        }
        String label = switch (kind) {
            case "tacos" -> "🌮 IT'S RAINING TACOS! 🌮";
            case "diamonds" -> "💎 IT'S RAINING DIAMONDS! 💎";
            case "gold" -> "🪙 IT'S RAINING GOLD! 🪙";
            default -> "🍬 IT'S RAINING CANDY! 🍬";
        };
        broadcastTitle(label, NamedTextColor.YELLOW, "Look up!");
        int perDrop = Math.max(1, plugin.getConfig().getInt("events.rain." + kind + "-per-drop",
                kind.equals("diamonds") ? 1 : kind.equals("gold") ? 2 : 3));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                World w = p.getWorld();
                if (w.getEnvironment() != World.Environment.NORMAL) continue;
                for (int i = 0; i < perDrop; i++) {
                    Location at = p.getLocation().add(random.nextInt(13) - 6, 11 + random.nextInt(3), random.nextInt(13) - 6);
                    org.bukkit.entity.Item drop = w.dropItem(at, rainItem(kind));
                    drop.setVelocity(new org.bukkit.util.Vector(0, -0.4, 0));
                    drop.setPickupDelay(12);
                }
            }
        }, 20L, 15L);
        rainTasks.put(kind, task);
    }

    private void stopAllRain() {
        for (BukkitTask t : rainTasks.values()) t.cancel();
        rainTasks.clear();
    }

    // ------------------------------------------------------------ THE SNAP

    /** The cutscene: Notazombiemc finds a button, presses it, and EVERY event starts at once. */
    private void theSnap() {
        if (snapRunning) return;
        snapRunning = true;
        cleanupSnapProps();
        List<Player> audience = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (audience.isEmpty()) {
            snapRunning = false;
            return;
        }
        // freeze everyone for the cutscene and put Notazombiemc (and his button) in front of them
        for (Player p : audience) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 20 * 9, 10, true, false, false));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.BLINDNESS, 50, 0, true, false, false));
            Location standAt = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
            standAt.setY(p.getWorld().getHighestBlockYAt(standAt) + 1);
            org.bukkit.entity.ArmorStand stand = p.getWorld().spawn(standAt, org.bukkit.entity.ArmorStand.class, s -> {
                s.setInvulnerable(true);
                s.setGravity(false);
                s.customName(Component.text("Notazombiemc", NamedTextColor.YELLOW));
                s.setCustomNameVisible(true);
                s.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            });
            snapProps.add(stand);
            // the button: a stone block with a stone button on top, between the stand and the player
            org.bukkit.util.Vector toward = p.getLocation().toVector().subtract(standAt.toVector());
            toward.setY(0);
            Location btnLoc = standAt.clone().add(toward.multiply(0.35));
            Block base = btnLoc.getBlock();
            if (base.getType() == Material.AIR) {
                snapBlocks.put(base.getLocation(), base.getBlockData());
                base.setType(Material.STONE, false);
            }
            Block btn = base.getRelative(BlockFace.UP);
            if (btn.getType() == Material.AIR) {
                snapBlocks.put(btn.getLocation(), btn.getBlockData());
                btn.setType(Material.STONE_BUTTON, false);
            }
        }
        Bukkit.broadcast(Component.text("⚠ Notazombiemc has found a mysterious button...", NamedTextColor.YELLOW));
        broadcastTitle("Notazombiemc found a button...", NamedTextColor.YELLOW, "uh oh.");
        org.bukkit.scheduler.BukkitRunnable cutscene = new org.bukkit.scheduler.BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                switch (step) {
                    case 3 -> {
                        broadcastTitle("He's reaching for it...", NamedTextColor.GOLD, "Don't do it. DON'T DO IT.");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
                        }
                    }
                    case 6 -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                        }
                        broadcastTitle("CLICK!", NamedTextColor.RED, "He pressed it.");
                    }
                    case 7 -> {
                        for (org.bukkit.entity.Entity e : snapProps) {
                            if (e.isValid()) e.getWorld().strikeLightningEffect(e.getLocation());
                        }
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.8f, 1f);
                            p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, p.getLocation().add(0, 1, 0),
                                    40, 0.6, 0.6, 0.6, 0.15);
                        }
                        broadcastTitle("💥 THE SNAP! 💥", NamedTextColor.DARK_PURPLE, "ALL EVENTS AT ONCE!");
                        Bukkit.broadcast(Component.text("THE SNAP! Notazombiemc pressed the button and EVERY event started at once!",
                                NamedTextColor.DARK_PURPLE));
                        if (!bloodMoonOn) toggleBloodMoon();
                        if (!horsemanMoonOn) toggleHorsemanMoon();
                        if (!discoOn) toggleDisco();
                        for (String kind : new String[]{"tacos", "diamonds", "gold", "candy"}) {
                            if (!rainTasks.containsKey(kind)) toggleRain(kind);
                        }
                        if (!lanternsOn) startLanterns();
                        if (!musicOn) startHalloweenMusic();
                        if (!curseOn) toggleCurse();
                        if (!plugin.tot.isActive()) plugin.tot.start();
                    }
                    case 9 -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                            p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                        }
                        cleanupSnapProps();
                        snapRunning = false;
                        cancel();
                    }
                    default -> {
                    }
                }
            }
        };
        cutscene.runTaskTimer(plugin, 40L, 40L);
    }

    private void cleanupSnapProps() {
        for (org.bukkit.entity.Entity e : snapProps) if (e.isValid()) e.remove();
        snapProps.clear();
        for (Map.Entry<Location, org.bukkit.block.data.BlockData> en : snapBlocks.entrySet()) {
            en.getKey().getBlock().setBlockData(en.getValue(), false);
        }
        snapBlocks.clear();
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
        stopMoons();
        stopDisco();
        stopAllRain();
        cleanupSnapProps();
        snapRunning = false;
        setSky("NONE");
        if (lanternsOn) stopLanterns();
        if (musicOn) stopHalloweenMusic();
        curseOn = false;
        cursed.clear();
        plugin.tot.stop(false);
        Bukkit.broadcast(Component.text("All events have ended.", NamedTextColor.GRAY));
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
        if (moonTask != null) moonTask.cancel();
        if (discoTask != null) discoTask.cancel();
        if (songTask != null) songTask.cancel();
        stopAllRain();
        cleanupSnapProps();
        removeLanterns();
        for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
    }
}
