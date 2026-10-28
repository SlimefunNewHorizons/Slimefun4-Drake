package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.entity.Player;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;

/** {@code /sf itemid}: muestra el ID de Slimefun del objeto en la mano (idea de Gugu). */
class ItemIdCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    ItemIdCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "itemid", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.itemid.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return;
        }
        if (!p.hasPermission("slimefun.command.itemid") && !p.isOp()) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }
        SlimefunItem item = SlimefunItem.getByItem(p.getInventory().getItemInMainHand());
        sender.sendMessage("[Slimefun] " + (item == null ? "No es un objeto de Slimefun." : "ID: " + item.getId() + " (addon: " + item.getAddon().getName() + ")"));
    }
}
