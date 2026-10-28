package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.balance.AddonBalanceSummary;
import io.github.thebusybiscuit.slimefun4.core.balance.BalanceService;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * {@code /sf balance [addon]}: sin argumentos lista el resumen de balance de cada addon (media, pico y nº de
 * objetos overpowered); con un addon, sus 10 objetos más fuertes. Portado de Slimefun5/Slimefun5.
 */
class BalanceCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    BalanceCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "balance", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.balance.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.balance") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        BalanceService balance = BalanceService.instance();

        if (args.length > 1) {
            String addon = args[1];
            AddonBalanceSummary s = balance.summarize(addon);
            if (s.isEmpty()) {
                sender.sendMessage("[Slimefun] Sin datos de balance para '" + addon + "'.");
                return;
            }
            sender.sendMessage("[Slimefun] " + addon + ": media " + s.getAverage() + ", pico " + s.getPeak() + ", overpowered " + s.getOpCount() + ", veredicto " + s.getVerdict());
            for (SlimefunItem item : balance.topItems(addon, 10)) {
                sender.sendMessage("  " + balance.scoreOf(item).getScore() + "  " + item.getId() + "  (" + balance.verdictOf(item) + ")");
            }
            return;
        }

        Set<String> addons = new TreeSet<>();
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            addons.add(item.getAddon().getName());
        }
        addons.stream()
            .map(a -> Map(a, balance.summarize(a)))
            .filter(e -> !e.s().isEmpty())
            .sorted(Comparator.comparingInt((Entry e) -> e.s().getOpCount()).reversed())
            .limit(30)
            .forEach(e -> sender.sendMessage("  " + e.name() + ": media " + e.s().getAverage() + ", pico " + e.s().getPeak() + ", OP " + e.s().getOpCount()));
    }

    private record Entry(String name, AddonBalanceSummary s) {
    }

    private static Entry Map(String name, AddonBalanceSummary s) {
        return new Entry(name, s);
    }
}
