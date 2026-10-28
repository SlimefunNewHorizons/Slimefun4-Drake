package com.github.drakescraft_labs.slimefun4.core.commands.subcommands;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import com.github.drakescraft_labs.slimefun4.core.commands.SlimefunCommand;
import com.github.drakescraft_labs.slimefun4.core.commands.SubCommand;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.tasks.TickerTask;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * {@code /sf tick [freeze|unfreeze|query|at|<ID>]}: congela los tickers de Slimefun para depurar
 * (idea de SlimefunGuguProject/Slimefun4, reescrita sobre nuestro {@link TickerTask}).
 */
class TickCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    TickCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "tick", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.tick.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.tick") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        TickerTask t = Slimefun.getTickerTask();
        String arg = args.length > 1 ? args[1] : "";

        switch (arg.toLowerCase()) {
            case "" -> t.setTickFreeze(!t.isTickFreeze());
            case "freeze" -> {
                t.setTickFreezePredicate(l -> true);
                t.setTickFreeze(true);
            }
            case "unfreeze" -> {
                t.setTickFreeze(false);
                t.setTickFreezePredicate(l -> true);
            }
            case "query" -> {
            }
            case "at" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Solo jugadores.");
                    return;
                }
                Block b = p.getTargetBlockExact(8, FluidCollisionMode.NEVER);
                if (b == null || BlockStorage.check(b.getLocation()) == null) {
                    sender.sendMessage("Mira un bloque de Slimefun.");
                    return;
                }
                Location at = b.getLocation();
                t.setTickFreezePredicate(l -> l.equals(at));
                t.setTickFreeze(true);
            }
            default -> {
                String id = arg.toUpperCase();
                t.setTickFreezePredicate(l -> id.equals(BlockStorage.getLocationInfo(l, "id")));
                t.setTickFreeze(true);
            }
        }

        sender.sendMessage("[Slimefun] tick-freeze: " + (t.isTickFreeze() ? "ACTIVO" : "apagado") + " | tick-rate: " + t.getTickRate());
    }
}
