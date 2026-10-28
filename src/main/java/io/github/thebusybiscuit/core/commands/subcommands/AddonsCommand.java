package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * {@code /sf addons}: lista los addons que registraron objetos, con versión y nº de objetos. Versión ligera del gestor
 * de addons de Slimefun5 (sin instalar ni actualizar desde dentro del juego: eso lo hace portar_addons.py / el lote).
 */
class AddonsCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    AddonsCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "addons", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.addons.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.addons") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        Map<String, int[]> items = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> versions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            SlimefunAddon addon = item.getAddon();
            items.computeIfAbsent(addon.getName(), k -> new int[1])[0]++;
            Plugin plugin = addon.getJavaPlugin();
            versions.put(addon.getName(), plugin == null ? "?" : plugin.getPluginMeta().getVersion());
        }

        sender.sendMessage("[Slimefun] " + items.size() + " addons con objetos:");
        items.forEach((name, n) -> sender.sendMessage("  " + name + " " + versions.get(name) + " - " + n[0] + " objetos"));
    }
}
