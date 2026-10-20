package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** On-screen tutorial for new players: movement, wood, crafting, stone, tips, commands. /skip to skip. */
final class Tutorial implements Listener {

    enum Step {
        WELCOME, MOVE, FIND_TREE, MINE_WOOD, PLANKS, TABLE, PLACE_TABLE, PICKAXE, STONE,
        TIP_FOOD, TIP_LIGHT, TIP_BED, COMMANDS, DONE
    }

    private final AbilitySMP plugin;
    private final Map<UUID, Step> steps = new HashMap<>();
    private final Map<UUID, Integer> seconds = new HashMap<>();
    private final Map<UUID, Integer> progress = new HashMap<>();
    private final Map<UUID, Location> anchors = new HashMap<>();

    Tutorial(AbilitySMP plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------ texts

    private static String title(Step s) {
        return switch (s) {
            case WELCOME -> "Welcome to Unbound SMP";
            case MOVE -> "Moving around";
            case FIND_TREE -> "Let's find a tree";
            case MINE_WOOD -> "Let's get 5 pieces of wood";
            case PLANKS -> "Make planks";
            case TABLE -> "Make a crafting table";
            case PLACE_TABLE -> "Place your crafting table";
            case PICKAXE -> "Make a wooden pickaxe";
            case STONE -> "Mine some stone";
            case TIP_FOOD -> "Stay fed";
            case TIP_LIGHT -> "Stay safe";
            case TIP_BED -> "Sleep through the night";
            case COMMANDS -> "Server commands";
            case DONE -> "Tutorial complete!";
        };
    }

    private static String subtitle(Step s) {
        return switch (s) {
            case WELCOME -> "Here is how to play";
            case MOVE -> "W A S D to walk, SPACE to jump, mouse to look";
            case FIND_TREE -> "and mine some wood";
            case MINE_WOOD -> "To break a block, hold LEFT CLICK!";
            case PLANKS -> "Press E, put 1 log in the crafting grid, take the planks";
            case TABLE -> "Put 4 planks in a square in the 2x2 grid";
            case PLACE_TABLE -> "Hold it and RIGHT CLICK the ground";
            case PICKAXE -> "Right-click the table: 3 planks on top, 2 sticks below";
            case STONE -> "Hold LEFT CLICK on gray stone with your pickaxe";
            case TIP_FOOD -> "Eat food when your hunger bar drops. RIGHT CLICK to eat";
            case TIP_LIGHT -> "Place torches. Mobs spawn in the dark!";
            case TIP_BED -> "Craft a bed (3 wool + 3 planks), RIGHT CLICK it at night";
            case COMMANDS -> "/pass   /notes   /reroll   and right-click a sword for your power";
            case DONE -> "Have fun on Unbound SMP!";
        };
    }

    private static String hint(Step s, int prog) {
        return switch (s) {
            case WELCOME -> "Welcome! The tutorial starts in a moment";
            case MOVE -> "Walk around a little (about 6 blocks)";
            case FIND_TREE -> "Look for a tree and walk up to it";
            case MINE_WOOD -> "Wood: " + prog + "/5 (hold LEFT CLICK on a log)";
            case PLANKS -> "Open your inventory with E and craft planks from a log";
            case TABLE -> "Craft a crafting table from 4 planks";
            case PLACE_TABLE -> "RIGHT CLICK the ground with the crafting table";
            case PICKAXE -> "Right-click your table: craft sticks, then a wooden pickaxe";
            case STONE -> "Stone: " + prog + "/3 (hold LEFT CLICK on stone)";
            case TIP_FOOD, TIP_LIGHT, TIP_BED, COMMANDS -> subtitle(s);
            case DONE -> "";
        };
    }

    /** Seconds before an info step moves on by itself (0 = waits for the player). */
    private static int timed(Step s) {
        return switch (s) {
            case WELCOME -> 7;
            case TIP_FOOD, TIP_LIGHT, TIP_BED -> 8;
            case COMMANDS -> 10;
            default -> 0;
        };
    }

    // ------------------------------------------------------------ flow

    void onFirstJoin(Player p) {
        if (!plugin.getConfig().getBoolean("tutorial.enabled", true)) return;
        UUID id = p.getUniqueId();
        steps.put(id, Step.WELCOME);
        save(id, Step.WELCOME);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            enter(p, Step.WELCOME);
            skipButton(p);
            if (p.getGameMode() == GameMode.CREATIVE) {
                p.sendMessage(Component.text("[Tutorial] The tutorial works best in survival mode. Use /skip to skip it.",
                        NamedTextColor.GRAY));
            }
        }, 60L);
    }

    void restart(Player p) {
        UUID id = p.getUniqueId();
        steps.put(id, Step.WELCOME);
        seconds.put(id, 0);
        progress.put(id, 0);
        save(id, Step.WELCOME);
        enter(p, Step.WELCOME);
        skipButton(p);
    }

    void skip(Player p) {
        UUID id = p.getUniqueId();
        if (!steps.containsKey(id)) {
            p.sendMessage(Component.text("You're not in the tutorial. Use /tutorial to start it.", NamedTextColor.GRAY));
            return;
        }
        clear(id);
        save(id, Step.DONE);
        p.sendMessage(Component.text("Tutorial skipped. You can replay it any time with /tutorial.", NamedTextColor.YELLOW));
    }

    private void clear(UUID id) {
        steps.remove(id);
        seconds.remove(id);
        progress.remove(id);
        anchors.remove(id);
    }

    private void save(UUID id, Step step) {
        plugin.data.set("players." + id + ".tutorial-step", step.name());
        plugin.saveData();
    }

    private void skipButton(Player p) {
        p.sendMessage(Component.text("[Tutorial] ", NamedTextColor.GOLD)
                .append(Component.text("Click here to SKIP the tutorial", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/skip"))));
    }

    private void enter(Player p, Step step) {
        UUID id = p.getUniqueId();
        if (step == Step.MOVE) anchors.put(id, p.getLocation());
        p.showTitle(Title.title(
                Component.text(title(step), NamedTextColor.GOLD),
                Component.text(subtitle(step), NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(600))));
        p.sendMessage(Component.text("[Tutorial] " + title(step) + " - " + subtitle(step), NamedTextColor.GOLD));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    private void advance(Player p) {
        UUID id = p.getUniqueId();
        Step cur = steps.get(id);
        if (cur == null) return;
        Step next = Step.values()[Math.min(cur.ordinal() + 1, Step.DONE.ordinal())];
        seconds.put(id, 0);
        progress.put(id, 0);
        if (next == Step.DONE) {
            finish(p);
            return;
        }
        steps.put(id, next);
        save(id, next);
        enter(p, next);
    }

    private void finish(Player p) {
        UUID id = p.getUniqueId();
        clear(id);
        save(id, Step.DONE);
        p.showTitle(Title.title(
                Component.text(title(Step.DONE), NamedTextColor.GREEN),
                Component.text(subtitle(Step.DONE), NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(800))));
        for (String reward : plugin.getConfig().getStringList("tutorial.finish-reward")) {
            plugin.giveReward(p, reward);
        }
        p.sendMessage(Component.text("[Tutorial] Done! Type /notes to see everything the server can do.", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    // ------------------------------------------------------------ once per second

    private void tick() {
        for (Map.Entry<UUID, Step> en : new ArrayList<>(steps.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) continue;
            UUID id = p.getUniqueId();
            Step st = en.getValue();
            int sec = seconds.merge(id, 1, Integer::sum);

            p.sendActionBar(Component.text(hint(st, progress.getOrDefault(id, 0)) + "   |   /skip to skip",
                    NamedTextColor.YELLOW));

            int timed = timed(st);
            if (timed > 0) {
                if (sec >= timed) advance(p);
                continue;
            }

            if (st == Step.MOVE) {
                Location a = anchors.get(id);
                if (a == null || a.getWorld() != p.getWorld()) {
                    anchors.put(id, p.getLocation());
                } else {
                    double dx = a.getX() - p.getLocation().getX();
                    double dz = a.getZ() - p.getLocation().getZ();
                    if (dx * dx + dz * dz >= 36) advance(p);
                }
            } else if (st == Step.FIND_TREE && logNear(p.getLocation(), 5)) {
                advance(p);
            }

            if (sec == 180) {
                p.sendMessage(Component.text("[Tutorial] Stuck? You can type /skip at any time.", NamedTextColor.GRAY));
            }
        }
    }

    private boolean logNear(Location loc, int r) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Tag.LOGS.isTagged(w.getBlockAt(bx + dx, by + dy, bz + dz).getType())) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (steps.containsKey(id)) return;
        String s = plugin.data.getString("players." + id + ".tutorial-step");
        final Step st = parse(s);
        if (st == null || st == Step.DONE) return;
        steps.put(id, st);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            enter(p, st);
            skipButton(p);
        }, 40L);
    }

    private static Step parse(String s) {
        if (s == null) return null;
        try {
            return Step.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer().getUniqueId());
    }

    private int bump(Player p) {
        return progress.merge(p.getUniqueId(), 1, Integer::sum);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Step st = steps.get(p.getUniqueId());
        if (st == null) return;
        Material m = e.getBlock().getType();
        if (st == Step.MINE_WOOD && Tag.LOGS.isTagged(m)) {
            if (bump(p) >= 5) advance(p);
        } else if (st == Step.STONE
                && (m == Material.STONE || m == Material.COBBLESTONE || m == Material.DEEPSLATE)) {
            if (bump(p) >= 3) advance(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Step st = steps.get(p.getUniqueId());
        if (st == null) return;
        Material r = e.getRecipe().getResult().getType();
        if ((st == Step.PLANKS && Tag.PLANKS.isTagged(r))
                || (st == Step.TABLE && r == Material.CRAFTING_TABLE)
                || (st == Step.PICKAXE && r == Material.WOODEN_PICKAXE)) {
            advance(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (steps.get(p.getUniqueId()) == Step.PLACE_TABLE
                && e.getBlockPlaced().getType() == Material.CRAFTING_TABLE) {
            advance(p);
        }
    }
}
