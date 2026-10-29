package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Spawn (v11): a little DonutSMP-style spawn. An admin sets the spawn point with /setspawn
 * (or builds a whole ready-made platform with /spawnbuild), then NPCs do the rest:
 * right-click them for a Random Teleport, the Power Shop, the Event Shop (the Galaxy Shop),
 * Ranked, or Duels. A floating countdown board shows the time left until the next big
 * update and flips to "OUT NOW!" when it lands. Crate chests live in Crates.java (/crate set).
 */
final class Spawn implements Listener {

    /** The duel picker menu: heads of everyone online; clicking one sends a duel request. */
    static final class DuelPicker implements InventoryHolder {
        final List<UUID> targets = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US);
    private static final String[] TYPES = {"rtp", "shop", "event", "ranked", "duels"};

    private final AbilitySMP plugin;
    private final NamespacedKey npcKey;
    private final NamespacedKey boardKey;
    private final Random random = new Random();
    private final Map<UUID, Long> rtpAt = new HashMap<>();
    private final List<ArmorStand> board = new ArrayList<>();
    private long lastBoardScan = 0L;

    Spawn(AbilitySMP plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "spawn_npc");
        this.boardKey = new NamespacedKey(plugin, "countdown_board");
        Bukkit.getScheduler().runTaskTimer(plugin, this::boardTick, 40L, 20L); // refresh the countdown every second
    }

    // =====================================================================
    // The spawn point
    // =====================================================================

    Location spawnLocation() {
        String w = plugin.getConfig().getString("spawn.point.world", "");
        if (w == null || w.isEmpty()) return null;
        World world = Bukkit.getWorld(w);
        if (world == null) return null;
        return new Location(world,
                plugin.getConfig().getDouble("spawn.point.x", 0.5),
                plugin.getConfig().getDouble("spawn.point.y", 64),
                plugin.getConfig().getDouble("spawn.point.z", 0.5),
                (float) plugin.getConfig().getDouble("spawn.point.yaw", 0),
                (float) plugin.getConfig().getDouble("spawn.point.pitch", 0));
    }

    private void setSpawn(Location at) {
        plugin.getConfig().set("spawn.point.world", at.getWorld().getName());
        plugin.getConfig().set("spawn.point.x", at.getX());
        plugin.getConfig().set("spawn.point.y", at.getY());
        plugin.getConfig().set("spawn.point.z", at.getZ());
        plugin.getConfig().set("spawn.point.yaw", (double) at.getYaw());
        plugin.getConfig().set("spawn.point.pitch", (double) at.getPitch());
        plugin.saveConfig();
    }

    void teleportCommand(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        Location at = spawnLocation();
        if (at == null) {
            p.sendMessage(Component.text("Spawn is not set yet." + (p.hasPermission("abilitysmp.admin")
                    ? " Stand where spawn should be and run /setspawn - or run /spawnbuild for a ready-made spawn!"
                    : " Ask an admin to set it with /setspawn."), NamedTextColor.RED));
            return;
        }
        p.teleport(at);
        p.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        plugin.bar(p, "Welcome to spawn!", NamedTextColor.GOLD);
    }

    void setSpawnCommand(CommandSender s) {
        if (!s.hasPermission("abilitysmp.admin")) {
            s.sendMessage(Component.text("Only admins can set the spawn.", NamedTextColor.RED));
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        setSpawn(p.getLocation());
        p.getWorld().setSpawnLocation(p.getLocation());
        s.sendMessage(Component.text("Spawn set to where you are standing. Players can use /spawn to come here.",
                NamedTextColor.GREEN));
    }

    /** Brand-new players start at spawn, DonutSMP style. */
    @EventHandler
    public void onFirstJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPlayedBefore()) return;
        Location at = spawnLocation();
        if (at == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.teleport(at);
        });
    }

    // =====================================================================
    // NPCs
    // =====================================================================

    private static String npcName(String type) {
        return switch (type) {
            case "rtp" -> "Random Teleport";
            case "shop" -> "Power Shop";
            case "event" -> "Event Shop";
            case "ranked" -> "Ranked";
            case "duels" -> "Duels";
            default -> type;
        };
    }

    private static NamedTextColor npcColor(String type) {
        return switch (type) {
            case "rtp" -> NamedTextColor.GREEN;
            case "shop" -> NamedTextColor.AQUA;
            case "event" -> NamedTextColor.LIGHT_PURPLE;
            case "ranked" -> NamedTextColor.RED;
            case "duels" -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }

    private static Villager.Profession npcJob(String type) {
        return switch (type) {
            case "rtp" -> Villager.Profession.CARTOGRAPHER;
            case "shop" -> Villager.Profession.LIBRARIAN;
            case "event" -> Villager.Profession.CLERIC;
            case "ranked" -> Villager.Profession.ARMORER;
            case "duels" -> Villager.Profession.WEAPONSMITH;
            default -> Villager.Profession.NITWIT;
        };
    }

    /** Spawns one NPC of the given type at `at`, facing `lookAt`. */
    void placeNpc(Location at, String type, Location lookAt) {
        Location spot = at.clone();
        Vector face = lookAt.toVector().subtract(spot.toVector()).setY(0);
        if (face.lengthSquared() > 1.0E-6) spot.setDirection(face);
        spot.getWorld().spawn(spot, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setCollidable(false);
            v.setProfession(npcJob(type));
            v.customName(Component.text("▶ " + npcName(type) + " ◀", npcColor(type))
                    .decoration(TextDecoration.ITALIC, false));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, type);
        });
    }

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        String type = v.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (type == null) return;
        e.setCancelled(true); // no villager trading menu
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        switch (type) {
            case "rtp" -> rtp(p);
            case "shop" -> plugin.powerMenus.openShop(p);
            case "event" -> plugin.shops.openGalaxyShop(p); // the current event shop
            case "ranked" -> plugin.ranked.command(p, new String[0]);
            case "duels" -> openDuelPicker(p);
            default -> {
            }
        }
    }

    void npcCommand(CommandSender s, String[] args) {
        if (!s.hasPermission("abilitysmp.admin")) {
            s.sendMessage(Component.text("Only admins can place spawn NPCs.", NamedTextColor.RED));
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length == 0) {
            s.sendMessage("Usage: /spawnnpc <rtp|shop|event|ranked|duels|board|remove>");
            return;
        }
        String what = args[0].toLowerCase(Locale.ROOT);
        if (what.equals("board")) {
            placeBoard(p.getLocation());
            s.sendMessage(Component.text("Countdown board placed! It counts down to the next update "
                    + "(/galactic set changes the date).", NamedTextColor.GREEN));
            return;
        }
        if (what.equals("remove")) {
            s.sendMessage(Component.text(removeNear(p.getLocation())
                    ? "Removed the nearest spawn NPC / board piece."
                    : "Nothing to remove within 5 blocks.", NamedTextColor.GRAY));
            return;
        }
        for (String t : TYPES) {
            if (t.equals(what)) {
                placeNpc(p.getLocation(), t, p.getLocation().add(p.getLocation().getDirection().multiply(3)));
                s.sendMessage(Component.text(npcName(t) + " NPC placed. Right-click it to use it!",
                        NamedTextColor.GREEN));
                return;
            }
        }
        s.sendMessage("Usage: /spawnnpc <rtp|shop|event|ranked|duels|board|remove>");
    }

    /** Removes the closest spawn NPC or countdown-board stand within 5 blocks. True if something went. */
    private boolean removeNear(Location at) {
        Entity best = null;
        double bestD = 25.0;
        for (Entity en : at.getWorld().getNearbyEntities(at, 5, 5, 5)) {
            boolean ours = (en instanceof Villager
                    && en.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING))
                    || (en instanceof ArmorStand
                    && en.getPersistentDataContainer().has(boardKey, PersistentDataType.STRING));
            if (!ours) continue;
            double d = en.getLocation().distanceSquared(at);
            if (d < bestD) {
                bestD = d;
                best = en;
            }
        }
        if (best == null) return false;
        if (best instanceof ArmorStand) { // removing one board line removes the whole board
            for (Entity en : new ArrayList<>(board)) en.remove();
            board.clear();
            for (Entity en : at.getWorld().getEntitiesByClass(ArmorStand.class)) {
                if (en.getPersistentDataContainer().has(boardKey, PersistentDataType.STRING)) en.remove();
            }
        } else {
            best.remove();
        }
        return true;
    }

    // =====================================================================
    // Duels NPC: pick a player, send the normal duel request
    // =====================================================================

    private void openDuelPicker(Player p) {
        List<Player> others = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) others.add(o);
        if (others.isEmpty()) {
            plugin.bar(p, "Nobody else is online to duel right now.", NamedTextColor.GRAY);
            return;
        }
        DuelPicker holder = new DuelPicker();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("⚔ Challenge a player ⚔", NamedTextColor.YELLOW));
        holder.inv = inv;
        int slot = 0;
        for (Player o : others) {
            if (slot >= 54) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(o);
            m.displayName(Component.text(o.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Click to challenge them to a duel!", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            head.setItemMeta(m);
            inv.setItem(slot++, head);
            holder.targets.add(o.getUniqueId());
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onPickerClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof DuelPicker holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= holder.targets.size()) return;
        Player t = Bukkit.getPlayer(holder.targets.get(slot));
        p.closeInventory();
        if (t == null) {
            plugin.bar(p, "That player is not online anymore.", NamedTextColor.GRAY);
            return;
        }
        plugin.duel.request(p, t.getName());
    }

    // =====================================================================
    // Random Teleport (the RTP NPC and /rtp)
    // =====================================================================

    void rtpCommand(CommandSender s) {
        if (s instanceof Player p) rtp(p);
        else s.sendMessage("Players only.");
    }

    void rtp(Player p) {
        if (plugin.fightTotem.isProtected(p) || plugin.duel.isBusy(p) || plugin.horseman.isBusy(p)
                || plugin.hallow.isBusy(p) || plugin.ranked.isBusy(p) || plugin.trading.isBusy(p)) {
            plugin.bar(p, "You can't teleport in the middle of a fight.", NamedTextColor.RED);
            return;
        }
        int cd = plugin.getConfig().getInt("spawn.rtp-cooldown-seconds", 30);
        if (cd > 0 && !p.hasPermission("abilitysmp.admin")) {
            long left = (rtpAt.getOrDefault(p.getUniqueId(), 0L) + cd * 1000L - System.currentTimeMillis()) / 1000L;
            if (left > 0) {
                plugin.bar(p, "RTP is cooling down: " + left + "s left.", NamedTextColor.GRAY);
                return;
            }
        }
        World w = p.getWorld().getEnvironment() == World.Environment.NORMAL ? p.getWorld() : Bukkit.getWorlds().get(0);
        int radius = Math.max(100, plugin.getConfig().getInt("spawn.rtp-radius", 5000));
        for (int tries = 0; tries < 15; tries++) {
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            Block top = w.getHighestBlockAt(x, z);
            Material ground = top.getType();
            if (ground == Material.WATER || ground == Material.LAVA || ground == Material.MAGMA_BLOCK
                    || ground == Material.CACTUS || ground == Material.FIRE || ground == Material.SOUL_FIRE
                    || ground == Material.SWEET_BERRY_BUSH || ground == Material.POWDER_SNOW) continue;
            Location at = new Location(w, x + 0.5, top.getY() + 1.0, z + 0.5,
                    p.getLocation().getYaw(), 0f);
            rtpAt.put(p.getUniqueId(), System.currentTimeMillis());
            p.teleport(at);
            p.setFallDistance(0f);
            p.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
            plugin.bar(p, "Whoosh! You landed " + (int) Math.sqrt((double) x * x + (double) z * z)
                    + " blocks from the middle of the world.", NamedTextColor.GREEN);
            return;
        }
        plugin.bar(p, "Couldn't find a safe landing spot - try again!", NamedTextColor.RED);
    }

    // =====================================================================
    // The countdown board: floating text that counts down to the next update
    // and flips to OUT NOW when it lands. The date is the /galactic date.
    // =====================================================================

    /** Places the board (4 floating lines) just above where the admin stands. */
    void placeBoard(Location at) {
        for (Entity en : new ArrayList<>(board)) en.remove();
        board.clear();
        Location base = at.clone().add(0, 2.6, 0);
        for (int i = 0; i < 4; i++) {
            final int line = i;
            ArmorStand st = base.getWorld().spawn(base.clone().add(0, -0.32 * i, 0), ArmorStand.class, a -> {
                a.setInvisible(true);
                a.setGravity(false);
                a.setMarker(true);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.setCustomNameVisible(true);
                a.customName(Component.text("...", NamedTextColor.WHITE));
                a.getPersistentDataContainer().set(boardKey, PersistentDataType.INTEGER, line);
            });
            board.add(st);
        }
        boardTick();
    }

    private void boardTick() {
        board.removeIf(st -> !st.isValid());
        if (board.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastBoardScan < 5000L) return; // find boards again after a restart, but not constantly
            lastBoardScan = now;
            for (World w : Bukkit.getWorlds()) {
                for (ArmorStand st : w.getEntitiesByClass(ArmorStand.class)) {
                    if (st.getPersistentDataContainer().has(boardKey, PersistentDataType.INTEGER)) board.add(st);
                }
            }
            if (board.isEmpty()) return;
            board.sort((a, b) -> Integer.compare(
                    a.getPersistentDataContainer().getOrDefault(boardKey, PersistentDataType.INTEGER, 0),
                    b.getPersistentDataContainer().getOrDefault(boardKey, PersistentDataType.INTEGER, 0)));
        }
        String name = Galactic.name(plugin);
        long left = Galactic.secondsLeft(plugin);
        ZonedDateTime rel = Galactic.release(plugin);
        Component[] lines = new Component[4];
        lines[0] = Component.text("🌌 THE " + name.toUpperCase(Locale.ROOT) + " 🌌", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true);
        if (rel == null) {
            lines[1] = Component.text("Coming soon...", NamedTextColor.LIGHT_PURPLE);
            lines[2] = Component.text("The date is not set yet - stay tuned!", NamedTextColor.GRAY);
            lines[3] = Component.text("Type /galactic for news", NamedTextColor.YELLOW);
        } else if (left > 0) {
            lines[1] = Component.text("⏳ Drops in: " + Galactic.countdown(plugin) + " ⏳", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true);
            lines[2] = Component.text(rel.format(WHEN), NamedTextColor.GRAY);
            lines[3] = Component.text("Get ready!", NamedTextColor.YELLOW);
        } else {
            lines[1] = Component.text("✦ IT IS OUT NOW! ✦", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true);
            lines[2] = Component.text("Type /notes to see everything that is new!", NamedTextColor.YELLOW);
            lines[3] = Component.text("New powers, events, shops and more!", NamedTextColor.GRAY);
        }
        for (int i = 0; i < board.size() && i < 4; i++) board.get(i).customName(lines[i]);
    }

    // =====================================================================
    // /spawnbuild: a ready-made little DonutSMP-style spawn platform with
    // the NPCs, 3 crate chests and the countdown board already placed.
    // =====================================================================

    void buildCommand(CommandSender s) {
        if (!s.hasPermission("abilitysmp.admin")) {
            s.sendMessage(Component.text("Only admins can build the spawn.", NamedTextColor.RED));
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        Location center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();

        // the floor: a round platform, plus clear air above it
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > 11.5) continue;
                Block floor = w.getBlockAt(cx + dx, cy - 1, cz + dz);
                if (floor.getType() != Material.BEDROCK) {
                    Material m;
                    if (dist < 2.5) m = Material.GOLD_BLOCK;
                    else if (dist <= 4.5) m = Material.POLISHED_ANDESITE;
                    else if (dist <= 8.5) m = Material.STONE_BRICKS;
                    else m = (dx + dz) % 6 == 0 ? Material.SEA_LANTERN : Material.POLISHED_ANDESITE;
                    floor.setType(m, false);
                }
                for (int dy = 0; dy <= 5; dy++) {
                    Block air = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (air.getType() != Material.BEDROCK) air.setType(Material.AIR, false);
                }
            }
        }
        // lamp posts on the four straight sides
        int[][] lamps = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
        for (int[] l : lamps) {
            w.getBlockAt(cx + l[0], cy, cz + l[1]).setType(Material.OAK_FENCE, false);
            w.getBlockAt(cx + l[0], cy + 1, cz + l[1]).setType(Material.OAK_FENCE, false);
            w.getBlockAt(cx + l[0], cy + 2, cz + l[1]).setType(Material.SEA_LANTERN, false);
        }
        // 3 crate chests on the west side, facing the middle
        for (int dz : new int[]{-2, 0, 2}) {
            Block chest = w.getBlockAt(cx - 9, cy, cz + dz);
            chest.setType(Material.CHEST, false);
            if (chest.getBlockData() instanceof Directional dir) {
                dir.setFacing(BlockFace.EAST);
                chest.setBlockData(dir, false);
            }
            plugin.crates.addCrateChest(chest);
        }
        // wipe any old spawn NPCs/board standing on the platform, then place fresh ones
        for (Entity en : w.getNearbyEntities(center, 14, 8, 14)) {
            if ((en instanceof Villager && en.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING))
                    || (en instanceof ArmorStand
                    && en.getPersistentDataContainer().has(boardKey, PersistentDataType.STRING))) {
                en.remove();
            }
        }
        board.clear();
        // the countdown board on the north side, NPCs in an arc facing the middle
        placeBoard(new Location(w, cx + 0.5, cy, cz - 8.5));
        placeNpc(new Location(w, cx - 7.5, cy, cz - 1.5), "ranked", center);
        placeNpc(new Location(w, cx + 7.5, cy, cz - 1.5), "event", center);
        placeNpc(new Location(w, cx - 5.5, cy, cz + 4.5), "duels", center);
        placeNpc(new Location(w, cx + 5.5, cy, cz + 4.5), "shop", center);
        placeNpc(new Location(w, cx + 0.5, cy, cz + 8.0), "rtp", center);
        // the spawn point itself: middle of the platform, facing the board
        Location spawnAt = new Location(w, cx + 0.5, cy, cz + 0.5, 180f, 0f);
        setSpawn(spawnAt);
        w.setSpawnLocation(spawnAt);
        p.teleport(spawnAt);
        s.sendMessage(Component.text("Your DonutSMP-style spawn is built! Platform, countdown board, 5 NPCs "
                + "and 3 crate chests are all placed, and /spawn now brings players here.", NamedTextColor.GREEN));
        s.sendMessage(Component.text("Right-click the NPCs to use them. The crate chests open with a "
                + "Power Crate Key.", NamedTextColor.YELLOW));
    }
}
