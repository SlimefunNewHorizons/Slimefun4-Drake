package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.services.NativeAccelerationService;

/**
 * Reports the native engine state and its runtime counters.
 */
final class NativeCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    NativeCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "native", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.native") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        NativeAccelerationService service = Slimefun.getNativeAccelerationService();
        sender.sendMessage(ChatColors.color("&6Slimefun-Rust &8| &7Estado: "
            + (service.isAvailable() ? "&aACTIVO" : "&cFALLBACK JAVA")));
        sender.sendMessage(ChatColors.color("&7ABI: &f" + service.getAbiVersion()
            + " &8| &7Llamadas nativas: &f" + service.getNativeCalls()
            + " &8| &7Fallbacks: &f" + service.getFallbackCalls()
            + " &8| &7Fallos: &f" + service.getFailures()));
    }
}
