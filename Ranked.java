package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Ranked duels (v9): /ranked. Fight a random player from the queue, or a practice bot whose gear
 * and stats match YOUR rank - easy in Bronze, a nightmare in Master, and the top ranks face bots
 * that feel impossible. The ladder climbs Bronze, Silver, Gold, Platinum, Diamond, Master and
 * finally UNBOUND KING. Like every protected fight it uses the Fight Totem: nobody dies and
 * nobody loses their stuff, ever.
 */
final class Ranked implements Listener {

    // ------------------------------------------------------------ the ladder

    record Tier(String name, int minRating, TextColor color) {
    }

    static final List<Tier> LADDER = List.of(
            new Tier("Bronze", 0, TextColor.color(0xCD7F32)),
            new Tier("Silver", 1100, TextColor.color(0xC0C0C0)),
            new Tier("Gold", 1300, NamedTextColor.GOLD),
            new Tier("Platinum", 1550, TextColor.color(0x7FE7DC)),
            new Tier("Diamond", 1850, TextColor.color(0x5FF5E8)),
            new Tier("Master", 2200, NamedTextColor.LIGHT_PURPLE),
            new Tier("Unbound King", 2600, NamedTextColor.YELLOW));

    static int tierIndex(int rating) {
        int idx = 0;
        for (int i = 0; i < LADDER.size(); i++) if (rating >= LADDER.get(i).minRating()) idx = i;
        return idx;
    }

    static Tier tierOf(int rating) {
        return LADDER.get(tierIndex(rating));
    }

    int rating(Player p) {
        return plugin.profile.of(p).rankedRating;
    }

    // ------------------------------------------------------------ fights

    private static final class Fight {
        final List<Player> humans = new ArrayList<>(); // 1 (vs bot) or 2 (vs player)
        final Map<UUID, Location> backs = new HashMap<>();
        Zombie bot;                 // null for player-vs-player
        int botTier = -1;           // ladder index the bot was built from; -2 = IMPOSSIBLE
        boolean impossible;
        int slot;
        boolean fighting;
        boolean over;
        int countdown;
        long start;
        BukkitTask task;

        Player other(Player p) {
            for (Player h : humans) if (!h.getUniqueId().equals(p.getUniqueId())) return h;
            return null;
        }
    }

    private static final String[] BOT_NAMES = {
            "ShadowByte", "xXReaperXx", "NoScopeNina", "CaptainClutch",
            "PixelProwler", "VoidWalker99", "TurboTim", "LunaStrike"};

    private final AbilitySMP plugin;
    private final NamespacedKey botKey;
    private final Random random = new Random();
    private final Map<UUID, Fight> fights = new HashMap<>();
    private final Map<UUID, Fight> botFights = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();

    Ranked(AbilitySMP plugin) {
        this.plugin = plugin;
        this.botKey = new NamespacedKey(plugin, "ranked_bot");
        Bukkit.getScheduler().runTaskTimer(plugin, this::queueTick, 40L, 20L);
    }

    boolean isBusy(Player p) {
        return fights.containsKey(p.getUniqueId()) || queue.contains(p.getUniqueId());
    }

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private boolean busyElsewhere(Player p) {
        return plugin.duel.isBusy(p) || plugin.bosses.isBusy(p)
                || plugin.trading.isBusy(p) || plugin.fightTotem.isProtected(p);
    }

    // =====================================================================
    // Command and menu
    // =====================================================================

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "queue", "play", "player" -> {
                    toggleQueue(p);
                    return;
                }
                case "bot", "practice" -> {
                    startBotFight(p, false);
                    return;
                }
                case "impossible" -> {
                    startBotFight(p, true);
                    return;
                }
                case "leave" -> {
                    queue.remove(p.getUniqueId());
                    Fight f = fights.get(p.getUniqueId());
                    if (f != null) finish(f, f.other(p), p, "left the match");
                    else p.sendMessage(Component.text("You left the ranked queue.", NamedTextColor.GRAY));
                    return;
                }
                default -> {
                }
            }
        }
        openMenu(p);
    }

    private void openMenu(Player p) {
        new Menus.Paged("Ranked Duels") {
            @Override
            List<Menus.Entry> entries(Player pl) {
                List<Menus.Entry> out = new ArrayList<>();
                int r = rating(pl);
                Tier t = tierOf(r);
                Tier next = tierIndex(r) + 1 < LADDER.size() ? LADDER.get(tierIndex(r) + 1) : null;
                out.add(new Menus.Entry(Menus.icon(Material.EMERALD, "Your rank: " + t.name(), true,
                        "Rating: " + r,
                        next == null ? "You are at the TOP of the ladder!" : "Next rank: " + next.name() + " at " + next.minRating(),
                        "Ranked wins: " + plugin.profile.of(pl).rankedWins,
                        "Nobody dies and nobody loses items - the Fight Totem protects you."), null));
                boolean queued = queue.contains(pl.getUniqueId());
                out.add(new Menus.Entry(Menus.icon(Material.DIAMOND_SWORD,
                        queued ? "In the queue... (click to leave)" : "Fight a RANDOM PLAYER", false,
                        "Matched against whoever is queued. Real rating on the line!"),
                        (pp, type) -> toggleQueue(pp)));
                out.add(new Menus.Entry(Menus.icon(Material.ZOMBIE_HEAD, "Fight a BOT (matched to your rank)", false,
                        "Bronze bots are easy. Master bots are monsters.",
                        "Win: +15 rating, Lose: -10 rating"),
                        (pp, type) -> startBotFight(pp, false)));
                out.add(new Menus.Entry(Menus.icon(Material.WITHER_SKELETON_SKULL, "Fight the IMPOSSIBLE bot", false,
                        "200 HP, full netherite, 10 hearts a hit. Good luck.",
                        "Just for fun - no rating changes."),
                        (pp, type) -> startBotFight(pp, true)));
                StringBuilder ladder = new StringBuilder();
                for (Tier tier : LADDER) ladder.append(tier.name()).append(" ").append(tier.minRating()).append("  ");
                out.add(new Menus.Entry(Menus.icon(Material.BOOK, "The ladder", false,
                        ladder.toString().trim()), null));
                return out;
            }
        }.open(p);
    }

    // =====================================================================
    // Queue (random player matchmaking)
    // =====================================================================

    private void toggleQueue(Player p) {
        if (fights.containsKey(p.getUniqueId())) {
            p.sendMessage(Component.text("You are already in a ranked match.", NamedTextColor.RED));
            return;
        }
        if (queue.remove(p.getUniqueId())) {
            p.sendMessage(Component.text("You left the ranked queue.", NamedTextColor.GRAY));
            return;
        }
        if (busyElsewhere(p)) {
            p.sendMessage(Component.text("Finish what you are doing first.", NamedTextColor.RED));
            return;
        }
        queue.add(p.getUniqueId());
        p.sendMessage(Component.text("You are in the ranked queue. You will be matched with a random player...",
                NamedTextColor.GOLD));
        p.closeInventory();
    }

    private void queueTick() {
        queue.removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            return p == null || !p.isOnline() || busyElsewhere(p) || fights.containsKey(id);
        });
        while (queue.size() >= 2) {
            var it = queue.iterator();
            Player a = Bukkit.getPlayer(it.next());
            it.remove();
            Player b = Bukkit.getPlayer(it.next());
            it.remove();
            if (a == null || b == null) continue;
            startMatch(a, b);
        }
    }

    // =====================================================================
    // Starting fights
    // =====================================================================

    private Location centerFor(Fight f) {
        World w = Bukkit.getWorlds().get(0);
        int cx = cfg("ranked.arena-x", 0) + f.slot * cfg("boss.arena-slot-spacing", 100);
        int y = cfg("ranked.arena-y", 230), cz = cfg("ranked.arena-z", 12000);
        if (!Arenas.built(w, cx, y, cz)) {
            Arenas.build(w, cx, y, cz, 14, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, 8);
        }
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    private Fight newFight(List<Player> humans) {
        Fight f = new Fight();
        int slot = 0;
        while (usedSlots.contains(slot)) slot++;
        usedSlots.add(slot);
        f.slot = slot;
        f.countdown = Math.max(1, cfg("ranked.countdown-seconds", 3));
        f.humans.addAll(humans);
        for (Player p : humans) {
            f.backs.put(p.getUniqueId(), p.getLocation().clone());
            fights.put(p.getUniqueId(), f);
        }
        return f;
    }

    private void heal(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
    }

    private void beginCountdown(Fight f) {
        f.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(f), 0L, 20L);
    }

    private void startMatch(Player a, Player b) {
        Fight f = newFight(List.of(a, b));
        Location c = centerFor(f);
        for (Player p : new Player[]{a, b}) {
            for (PotionEffect e : new HashSet<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
            heal(p);
            p.setPlayerTime(6000L, false);
        }
        a.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() - 9, 0f, 0f));
        b.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 9, 180f, 0f));
        for (Player p : new Player[]{a, b}) {
            plugin.fightTotem.protect(p, "Ranked Duel", popped -> {
                if (!f.over) finish(f, f.other(popped), popped, "totem popped");
            });
            p.sendMessage(Component.text("RANKED match vs " + f.other(p).getName() + " (" + tierOf(rating(f.other(p))).name()
                    + ", " + rating(f.other(p)) + "). Nobody loses items!", NamedTextColor.GOLD));
        }
        beginCountdown(f);
    }

    private void startBotFight(Player p, boolean impossible) {
        if (fights.containsKey(p.getUniqueId())) {
            p.sendMessage(Component.text("You are already in a ranked match.", NamedTextColor.RED));
            return;
        }
        queue.remove(p.getUniqueId());
        if (busyElsewhere(p)) {
            p.sendMessage(Component.text("Finish what you are doing first.", NamedTextColor.RED));
            return;
        }
        Fight f = newFight(List.of(p));
        f.impossible = impossible;
        f.botTier = impossible ? -2 : tierIndex(rating(p));
        Location c = centerFor(f);
        for (PotionEffect e : new HashSet<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        heal(p);
        p.setPlayerTime(6000L, false);
        p.teleport(new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() - 9, 0f, 0f));
        plugin.fightTotem.protect(p, "Ranked Duel", popped -> {
            if (!f.over) finish(f, null, popped, "totem popped");
        });
        spawnBot(f, new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 9));
        Tier t = tierOf(rating(p));
        p.sendMessage(Component.text(impossible
                ? "The IMPOSSIBLE bot appears. No rating on the line - just pain."
                : "Practice bot matched to your rank: " + t.name() + ". Win +15 / lose -10 rating.", NamedTextColor.GOLD));
        p.closeInventory();
        beginCountdown(f);
    }

    // ------------------------------------------------------------ the bot

    private ItemStack gear(Material m, int prot) {
        ItemStack it = new ItemStack(m);
        if (prot > 0) {
            ItemMeta meta = it.getItemMeta();
            meta.addEnchant(Enchantment.PROTECTION, prot, true);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void spawnBot(Fight f, Location at) {
        int tier = f.botTier;
        double hp;
        double dmg;
        double speed;
        Material[] armor; // helmet, chest, legs, boots
        Material weapon;
        int prot;
        int strength;
        int speedLvl;
        boolean regen;
        String label;
        if (f.impossible) {
            hp = 200; dmg = 20; speed = 0.32; prot = 4; strength = 1; speedLvl = 1; regen = true;
            armor = new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                    Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
            weapon = Material.NETHERITE_AXE;
            label = "☠ IMPOSSIBLE ☠";
        } else {
            switch (tier) {
                case 0 -> { hp = 20; dmg = 4; speed = 0.23; prot = 0; strength = 0; speedLvl = 0; regen = false;
                    armor = new Material[]{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
                    weapon = Material.STONE_SWORD; }
                case 1 -> { hp = 26; dmg = 5; speed = 0.24; prot = 0; strength = 0; speedLvl = 0; regen = false;
                    armor = new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS};
                    weapon = Material.IRON_SWORD; }
                case 2 -> { hp = 32; dmg = 6; speed = 0.25; prot = 0; strength = 0; speedLvl = 0; regen = false;
                    armor = new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
                    weapon = Material.IRON_SWORD; }
                case 3 -> { hp = 40; dmg = 8; speed = 0.26; prot = 1; strength = 0; speedLvl = 0; regen = false;
                    armor = new Material[]{Material.DIAMOND_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.DIAMOND_BOOTS};
                    weapon = Material.DIAMOND_SWORD; }
                case 4 -> { hp = 50; dmg = 10; speed = 0.28; prot = 2; strength = 0; speedLvl = 0; regen = false;
                    armor = new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
                    weapon = Material.DIAMOND_SWORD; }
                case 5 -> { hp = 65; dmg = 12; speed = 0.30; prot = 3; strength = 0; speedLvl = 0; regen = false;
                    armor = new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
                    weapon = Material.NETHERITE_SWORD; }
                default -> { hp = 85; dmg = 14; speed = 0.31; prot = 4; strength = 0; speedLvl = 1; regen = true;
                    armor = new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
                    weapon = Material.NETHERITE_SWORD; }
            }
            label = LADDER.get(Math.max(0, tier)).name();
        }
        String botName = f.impossible ? "The Final Boss" : BOT_NAMES[random.nextInt(BOT_NAMES.length)];
        Zombie z = at.getWorld().spawn(at, Zombie.class, bot -> {
            bot.setBaby(false);
            bot.setShouldBurnInDay(false);
            bot.setCanBreakDoors(false);
            bot.setRemoveWhenFarAway(false);
            bot.setCanPickupItems(false);
            bot.getPersistentDataContainer().set(botKey, PersistentDataType.STRING, "1");
            bot.customName(Component.text(botName + " [" + label + "]", f.impossible ? NamedTextColor.DARK_RED : NamedTextColor.GRAY));
            bot.setCustomNameVisible(true);
            EntityEquipment eq = bot.getEquipment();
            eq.setHelmet(gear(armor[0], prot));
            eq.setChestplate(gear(armor[1], prot));
            eq.setLeggings(gear(armor[2], prot));
            eq.setBoots(gear(armor[3], prot));
            eq.setItemInMainHand(new ItemStack(weapon));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            AttributeInstance h = bot.getAttribute(Attribute.MAX_HEALTH);
            if (h != null) {
                h.setBaseValue(hp);
                bot.setHealth(hp);
            }
            AttributeInstance d = bot.getAttribute(Attribute.ATTACK_DAMAGE);
            if (d != null) d.setBaseValue(dmg);
            AttributeInstance sp = bot.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(speed);
            AttributeInstance fr = bot.getAttribute(Attribute.FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(60);
            if (strength > 0) bot.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, strength, false, false, false));
            if (speedLvl > 0) bot.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, speedLvl, false, false, false));
            if (regen) bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        });
        f.bot = z;
        botFights.put(z.getUniqueId(), f);
    }

    // =====================================================================
    // The fight tick: countdown, bot targeting, time limit
    // =====================================================================

    private void tick(Fight f) {
        if (f.over) {
            if (f.task != null) f.task.cancel();
            return;
        }
        if (!f.fighting) {
            if (f.countdown > 0) {
                for (Player p : f.humans) {
                    if (!p.isOnline()) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10, true, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 128, true, false, false));
                    p.showTitle(Title.title(Component.text(String.valueOf(f.countdown), NamedTextColor.GOLD),
                            Component.text("Ranked match", NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
                }
                if (f.bot != null && f.bot.isValid()) f.bot.setTarget(null);
                f.countdown--;
            } else {
                for (Player p : f.humans) {
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                    p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    p.showTitle(Title.title(Component.text("FIGHT!", NamedTextColor.RED), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))));
                }
                f.fighting = true;
                f.start = System.currentTimeMillis();
            }
            return;
        }
        if (f.bot != null && f.bot.isValid() && !f.humans.isEmpty()) {
            Player target = f.humans.get(0);
            if (target.isOnline()) f.bot.setTarget(target);
        }
        if (System.currentTimeMillis() - f.start > cfg("ranked.max-minutes", 5) * 60_000L) {
            finish(f, null, null, "time");
        }
    }

    // =====================================================================
    // Damage rules: only your opponent (or the bot) can hurt you
    // =====================================================================

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player s) return s;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        // a bot was hit
        Fight bf = botFights.get(e.getEntity().getUniqueId());
        if (bf != null) {
            if (bf.over || !bf.fighting) {
                e.setCancelled(true);
                return;
            }
            if (e instanceof EntityDamageByEntityEvent ed) {
                Player attacker = attackerOf(ed.getDamager());
                if (attacker == null || !bf.humans.contains(attacker)) e.setCancelled(true);
            }
            return;
        }
        if (!(e.getEntity() instanceof Player victim)) return;
        Fight f = fights.get(victim.getUniqueId());
        if (f == null) return;
        if (f.over || !f.fighting) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ed) {
            Entity damager = ed.getDamager();
            if (f.bot != null && damager.getUniqueId().equals(f.bot.getUniqueId())) return; // the bot may hit you
            Player attacker = attackerOf(damager);
            if (attacker == null || !attacker.equals(f.other(victim))) e.setCancelled(true);
        }
        // lethal hits are NOT stopped here: the Fight Totem pops and finishes the match as a loss
    }

    @EventHandler
    public void onBotDeath(EntityDeathEvent e) {
        Fight f = botFights.get(e.getEntity().getUniqueId());
        if (f == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (!f.over && !f.humans.isEmpty()) finish(f, f.humans.get(0), null, "bot slain");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        queue.remove(p.getUniqueId());
        Fight f = fights.get(p.getUniqueId());
        if (f == null || f.over) return;
        Location back = f.backs.get(p.getUniqueId());
        if (back != null) p.teleport(back);
        finish(f, f.bot == null ? f.other(p) : null, p, "forfeit");
    }

    // =====================================================================
    // Ending + rating
    // =====================================================================

    private void finish(Fight f, Player winner, Player loser, String how) {
        if (f.over) return;
        f.over = true;
        if (f.task != null) f.task.cancel();
        boolean vsBot = f.bot != null;
        boolean impossible = f.impossible;
        if (f.bot != null) {
            botFights.remove(f.bot.getUniqueId());
            if (f.bot.isValid()) f.bot.remove();
            f.bot = null;
        }
        for (Player p : f.humans) {
            plugin.fightTotem.cleanup(p);
            if (!p.isOnline()) continue;
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            heal(p);
        }
        if (impossible) {
            if (winner != null) { // the human actually beat the IMPOSSIBLE bot
                winner.showTitle(Title.title(Component.text("IMPOSSIBLE... BEATEN?!", NamedTextColor.YELLOW),
                        Component.text("You actually did it. Legend.", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
                Bukkit.broadcast(Component.text(winner.getName() + " just beat the IMPOSSIBLE ranked bot!!", NamedTextColor.YELLOW));
                rewardWin(winner, 0);
            } else if (loser != null && loser.isOnline()) {
                loser.sendMessage(Component.text("The IMPOSSIBLE bot wins again. It always does.", NamedTextColor.GRAY));
            }
        } else if (vsBot) {
            if (winner != null) botResult(winner, true);
            else if (loser != null && loser.isOnline()) botResult(loser, false);
        } else if (winner != null) {
            applyResult(winner, loser);
        } else if ("time".equals(how)) {
            for (Player p : f.humans) {
                if (p.isOnline()) p.sendMessage(Component.text("Time! The ranked match is a draw - no rating changes.", NamedTextColor.GRAY));
            }
        } else {
            for (Player p : f.humans) {
                if (p.isOnline()) p.sendMessage(Component.text("The ranked match ended.", NamedTextColor.GRAY));
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : f.humans) {
                if (p.isOnline()) {
                    Location back = f.backs.get(p.getUniqueId());
                    if (back != null) p.teleport(back);
                    plugin.admin.applySky(p);
                }
                fights.remove(p.getUniqueId());
            }
            usedSlots.remove(f.slot);
        }, 60L);
    }

    private void setRating(Player p, int value) {
        plugin.profile.of(p).rankedRating = Math.max(0, value);
        plugin.profile.touch();
        plugin.profile.save();
        plugin.ranks.evaluate(p);
    }

    /** Player-vs-player result: elo-style rating change for both sides. */
    private void applyResult(Player winner, Player loser) {
        int rw = rating(winner);
        int rl = loser == null ? rw : rating(loser);
        double expected = 1.0 / (1.0 + Math.pow(10.0, (rl - rw) / 400.0));
        int delta = Math.max(5, Math.min(40, (int) Math.round(32.0 * (1.0 - expected))));
        int oldTierW = tierIndex(rw);
        setRating(winner, rw + delta);
        winner.sendMessage(Component.text("Ranked WIN vs " + (loser == null ? "?" : loser.getName()) + "! +" + delta
                + " rating (" + rating(winner) + " - " + tierOf(rating(winner)).name() + ")", NamedTextColor.GREEN));
        if (tierIndex(rating(winner)) > oldTierW) {
            winner.showTitle(Title.title(Component.text("RANK UP!", NamedTextColor.GOLD),
                    Component.text("You are now " + tierOf(rating(winner)).name(), tierOf(rating(winner)).color()),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
            Bukkit.broadcast(Component.text(winner.getName() + " ranked up to " + tierOf(rating(winner)).name() + "!",
                    NamedTextColor.GOLD));
        }
        if (loser != null && loser.isOnline()) {
            int loss = Math.max(5, delta * 3 / 4);
            setRating(loser, rating(loser) - loss);
            loser.sendMessage(Component.text(winner.getName() + " won the ranked match. -" + loss + " rating ("
                    + rating(loser) + " - " + tierOf(rating(loser)).name() + ")", NamedTextColor.GRAY));
        }
        rewardWin(winner, cfg("ranked.win-points", 10));
    }

    /** Called from the bot-fight finish paths to settle rating for practice bots. */
    private void botResult(Player human, boolean won) {
        if (won) {
            int old = rating(human);
            setRating(human, old + 15);
            human.sendMessage(Component.text("Bot beaten! +15 rating (" + rating(human) + " - "
                    + tierOf(rating(human)).name() + ")", NamedTextColor.GREEN));
            if (tierIndex(rating(human)) > tierIndex(old)) {
                human.showTitle(Title.title(Component.text("RANK UP!", NamedTextColor.GOLD),
                        Component.text("You are now " + tierOf(rating(human)).name(), tierOf(rating(human)).color()),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
            }
            rewardWin(human, cfg("ranked.bot-win-points", 5));
        } else {
            setRating(human, rating(human) - 10);
            human.sendMessage(Component.text("The bot won. -10 rating (" + rating(human) + " - "
                    + tierOf(rating(human)).name() + ")", NamedTextColor.GRAY));
        }
    }

    private void rewardWin(Player winner, int points) {
        plugin.profile.of(winner).rankedWins++;
        plugin.profile.touch();
        plugin.profile.save();
        plugin.unbound.addProgress(winner, "RANKED_WIN", 1);
        if (points > 0) {
            plugin.unbound.addPoints(winner, points, "ranked win");
            winner.sendMessage(Component.text("+" + points + " Unbound Coins!", NamedTextColor.AQUA));
        }
    }

    void shutdown() {
        queue.clear();
        for (Fight f : new HashSet<>(fights.values())) {
            f.over = true;
            if (f.task != null) f.task.cancel();
            if (f.bot != null && f.bot.isValid()) f.bot.remove();
            for (Player p : f.humans) {
                plugin.fightTotem.cleanup(p);
                if (!p.isOnline()) continue;
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                Location back = f.backs.get(p.getUniqueId());
                if (back != null) p.teleport(back);
                plugin.admin.applySky(p);
            }
        }
        fights.clear();
        botFights.clear();
    }
}
