package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.AddonUpdateChecker;
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

        if (args.length > 1 && args[1].equalsIgnoreCase("check")) {
            check(sender);
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

    private static final AddonUpdateChecker CHECKER = new AddonUpdateChecker();

    /**
     * {@code /sf addons check}: compara la versión instalada con el maven-metadata del repo (solo lectura, async, 5 s).
     * Las coordenadas se declaran en config.yml: {@code addons.maven.<NombreAddon>: grupo/ruta/artefacto}.
     */
    private static void check(CommandSender sender) {
        Set<String> names = Slimefun.getCfg().contains("addons.maven") ? Slimefun.getCfg().getKeys("addons.maven") : Set.of();
        if (names.isEmpty()) {
            sender.sendMessage("[Slimefun] Sin coordenadas: define addons.maven.<Addon>: grupo/ruta/artefacto en config.yml.");
            return;
        }

        Map<String, CompletableFuture<String>> pending = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            String coord = Slimefun.getCfg().getString("addons.maven." + name);
            pending.put(name, AddonUpdateChecker.isValidCoordinate(coord) ? CHECKER.latest(coord) : CompletableFuture.completedFuture(null));
        }

        sender.sendMessage("[Slimefun] Consultando " + pending.size() + " addons en " + AddonUpdateChecker.DEFAULT_BASE + " ...");
        CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).whenComplete((v, e) -> Slimefun.runSync(() -> {
            pending.forEach((name, f) -> {
                Plugin pl = Bukkit.getPluginManager().getPlugin(name);
                String installed = pl == null ? null : pl.getPluginMeta().getVersion();
                String latest = f.getNow(null);
                String state;
                if (installed == null) {
                    state = "no instalado";
                } else if (latest == null) {
                    state = "sin datos (error, timeout o coordenada inválida)";
                } else if (AddonUpdateChecker.compareVersions(installed, latest) < 0) {
                    state = "ACTUALIZABLE -> " + latest;
                } else {
                    state = "al día";
                }
                sender.sendMessage("  " + name + " " + (installed == null ? "-" : installed) + ": " + state);
            });
        }));
    }
}
