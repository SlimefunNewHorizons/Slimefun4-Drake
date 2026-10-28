package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.backend.CachedSfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStores;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * {@code /sf diag}: diagnóstico para lag/ping. Solo lee estructuras en memoria (sin I/O ni esperas): tickers por addon,
 * coste medio de tick por addon, estado del {@link CachedSfStore} y memoria de los caches.
 */
class DiagCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    DiagCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "diag", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.diag.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.diag") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        for (String line : report()) {
            sender.sendMessage(line);
        }
    }

    static @Nonnull List<String> report() {
        List<String> out = new ArrayList<>();
        Runtime rt = Runtime.getRuntime();
        out.add("[Slimefun] diag | heap " + (rt.totalMemory() - rt.freeMemory()) / 1_048_576 + "/" + rt.maxMemory() / 1_048_576 + " MB | tick-rate " + Slimefun.getTickerTask().getTickRate());

        // tickers activos por addon
        Map<String, Integer> tickers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int total = 0;
        for (Set<Location> locations : Slimefun.getTickerTask().getLocations().values()) {
            for (Location l : locations) {
                SlimefunItem item = BlockStorage.check(l);
                tickers.merge(item == null ? "?" : item.getAddon().getName(), 1, Integer::sum);
                total++;
            }
        }
        Map<String, Double> avg = Slimefun.getProfiler().getAddonAverages();
        out.add("Tickers activos: " + total + " (coste = media móvil por tick)");
        for (Map.Entry<String, Integer> e : tickers.entrySet()) {
            double ns = avg.getOrDefault(e.getKey(), 0.0);
            out.add(String.format("  %s: %d bloques, %.2f ms/tick", e.getKey(), e.getValue(), ns / 1_000_000.0));
        }

        // almacenamiento
        SfStore store = SfStores.get();
        if (store instanceof CachedSfStore c) {
            CachedSfStore.Stats s = c.stats();
            out.add("Store: cola " + s.queued() + ", pendientes " + s.pending() + ", lecturas " + s.reads() + " (cache " + s.cacheHits() + "), escrituras " + s.writes()
                    + ", fallos " + s.failures() + ", descartadas " + s.discarded());
            out.add("Cache de inventarios: " + s.cacheEntries() + " entradas, ~" + (s.cacheChars() * 2 / 1024) + " KB");
        } else {
            out.add("Store: " + store.getClass().getSimpleName() + " (sin cache de escritura)");
        }
        return out;
    }
}
