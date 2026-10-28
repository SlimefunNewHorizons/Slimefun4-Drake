package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.backend.FileSfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStores;
import io.github.thebusybiscuit.slimefun4.storage.backend.SqlSfStore;

/**
 * {@code /sf storage [status|export]}: estado del backend y volcado de la base de datos a ficheros
 * ({@code data-storage/Slimefun/export/}) para poder volver a {@code backend: file}.
 */
class StorageCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    StorageCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "storage", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.storage.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.storage") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        SfStore store = SfStores.get();
        String sub = args.length > 1 ? args[1].toLowerCase() : "status";

        if (sub.equals("export")) {
            if (store instanceof FileSfStore) {
                sender.sendMessage("[Slimefun] El backend ya es 'file'; no hay nada que exportar.");
                return;
            }
            Slimefun.runSync(() -> sender.sendMessage("[Slimefun] Exportando..."));
            int n = ((SqlSfStore) store).exportToDisk("data-storage/Slimefun/export");
            sender.sendMessage("[Slimefun] Exportados " + n + " ficheros a data-storage/Slimefun/export/. Para volver a disco, mueve su contenido a data-storage/Slimefun/ y pon storage.backend: file.");
            return;
        }

        sender.sendMessage("[Slimefun] Storage: " + (store instanceof SqlSfStore sql ? "SQL | " + sql.stats() : "file (disco, fsync)"));
    }
}
