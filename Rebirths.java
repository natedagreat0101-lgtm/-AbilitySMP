package me.abilitysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Rebirths (v9): /rebirth. Trade everything for a fresh start and permanent power: your Unbound
 * Coins and power levels reset to zero, but you KEEP every power you own, and each rebirth adds
 * a permanent bonus to all coins you earn, plus a crate set and Halloween Coins. The more
 * rebirths you have, the better it pays - and the more it costs to do it again.
 */
final class Rebirths {

    private final AbilitySMP plugin;

    Rebirths(AbilitySMP plugin) {
        this.plugin = plugin;
    }

    int count(Player p) {
        return plugin.profile.of(p).rebirths;
    }

    /** Cost in Unbound Coins of the NEXT rebirth for this player. */
    int cost(Player p) {
        int base = plugin.getConfig().getInt("rebirth.base-cost", 250);
        int n = count(p); // going for rebirth number n+1
        return base * (n + 1) * (n + 1);
    }

    /** Boss kills required for the NEXT rebirth. */
    int bossKillsNeeded(Player p) {
        return plugin.getConfig().getInt("rebirth.boss-kills-per-rebirth", 3) * (count(p) + 1);
    }

    double coinBonusPercent(Player p) {
        return plugin.getConfig().getDouble("rebirth.coin-bonus-percent", 10) * count(p);
    }

    void command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("confirm")) {
            doRebirth(p);
            return;
        }
        int n = count(p);
        p.sendMessage(Component.text("===== Rebirth =====", NamedTextColor.DARK_PURPLE));
        p.sendMessage(Component.text("Your rebirths: " + n + "  (coin bonus so far: +" + (int) coinBonusPercent(p) + "%)",
                NamedTextColor.LIGHT_PURPLE));
        p.sendMessage(Component.text("Rebirthing RESETS your Unbound Coins and all your power levels to zero.",
                NamedTextColor.YELLOW));
        p.sendMessage(Component.text("You KEEP all your powers, ranks, titles, cosmetics and quests.", NamedTextColor.GREEN));
        p.sendMessage(Component.text("You GAIN: + " + plugin.getConfig().getInt("rebirth.coin-bonus-percent", 10)
                + "% coins forever, 1 Power Crate + Key, and "
                + plugin.getConfig().getInt("rebirth.halloween-coins-per-rebirth", 20) * (n + 1) + " Halloween Coins.",
                NamedTextColor.GOLD));
        p.sendMessage(Component.text("Cost of rebirth #" + (n + 1) + ": " + cost(p) + " Unbound Coins (you have "
                + plugin.unbound.points(p) + ") and " + bossKillsNeeded(p) + " total boss kills (you have "
                + plugin.profile.of(p).bossKills + ").", NamedTextColor.AQUA));
        p.sendMessage(Component.text("Ready? Type /rebirth confirm", NamedTextColor.GRAY));
    }

    private void doRebirth(Player p) {
        if (plugin.fightTotem.isProtected(p) || plugin.duel.isBusy(p) || plugin.horseman.isBusy(p)
                || plugin.hallow.isBusy(p) || plugin.ranked.isBusy(p) || plugin.trading.isBusy(p)) {
            p.sendMessage(Component.text("You can't rebirth in the middle of something!", NamedTextColor.RED));
            return;
        }
        int cost = cost(p);
        int killsNeeded = bossKillsNeeded(p);
        if (plugin.profile.of(p).bossKills < killsNeeded) {
            p.sendMessage(Component.text("You need " + killsNeeded + " total boss kills first. You have "
                    + plugin.profile.of(p).bossKills + ". Go boss hunting!", NamedTextColor.RED));
            return;
        }
        if (!plugin.unbound.spend(p, cost)) {
            p.sendMessage(Component.text("Not enough Unbound Coins: rebirth costs " + cost + ", you have "
                    + plugin.unbound.points(p) + ".", NamedTextColor.RED));
            return;
        }
        // the reset: coins (the rest of them) and power levels go back to zero
        plugin.unbound.resetCurrencyAndLevels(p);
        Profile.Data d = plugin.profile.of(p);
        d.rebirths++;
        plugin.profile.save();
        // the rewards
        int hw = plugin.getConfig().getInt("rebirth.halloween-coins-per-rebirth", 20) * d.rebirths;
        if (hw > 0) plugin.tot.giveCoins(p, hw);
        plugin.crates.giveSet(p, 1);
        plugin.unbound.addProgress(p, "REBIRTH", 1);
        plugin.ranks.evaluate(p);
        plugin.locker.checkUnlocks(p);
        p.showTitle(Title.title(Component.text("REBIRTH #" + d.rebirths + "!", NamedTextColor.DARK_PURPLE),
                Component.text("+" + (int) coinBonusPercent(p) + "% coins forever", NamedTextColor.LIGHT_PURPLE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.3f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 80, 0.5, 0.8, 0.5, 0.15);
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.5);
        Bukkit.broadcast(Component.text(p.getName() + " has REBIRTHED for the " + ordinal(d.rebirths)
                + " time! (+" + (int) coinBonusPercent(p) + "% coins forever)", NamedTextColor.DARK_PURPLE));
        p.sendMessage(Component.text("Rebirth complete! Your coins and power levels were reset, but every coin "
                + "you earn from now on is worth +" + (int) coinBonusPercent(p) + "% more.", NamedTextColor.GREEN));
    }

    private static String ordinal(int n) {
        return switch (n) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> n + "th";
        };
    }
}
