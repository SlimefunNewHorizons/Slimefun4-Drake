package me.mrCookieSlime.Slimefun.api.inventory;

import io.github.thebusybiscuit.slimefun4.storage.backend.SfStores;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * Reads and writes the legacy {@code .sfi} inventory files without going through
 * {@link io.github.bakedlibs.dough.config.Config}.
 * <p>
 * Every {@code Config} constructor builds a {@code DoughLogger}, and that logger's
 * constructor calls {@link java.util.logging.Logger#setParent(java.util.logging.Logger)}
 * plus {@code setLevel(Level.ALL)}. Both take the process-wide {@code treeLock} of
 * {@link java.util.logging.LogManager}, and {@code doSetParent} walks the parent logger's
 * list of children while holding it. The synchronous auto-save batch persists hundreds of
 * dirty {@link BlockMenu}s per pass, so it was creating hundreds of throw-away loggers a
 * pass: the child list walk degrades into quadratic work under a lock shared with every
 * thread that logs, which stalled the server thread for 10 s and tripped the watchdog on
 * 2026-09-12.
 * <p>
 * The logger was only ever used to report an {@link IOException} from the write itself, so
 * it is reported here directly through Slimefun's own logger instead.
 *
 * @see BlockMenu
 * @see UniversalBlockMenu
 */
final class InventoryFileWriter {

    private InventoryFileWriter() {}

    /** Ruta virtual estable del almacén (siempre con '/'). */
    static String path(File file) {
        return file.getPath().replace('\\', '/');
    }

    /**
     * Loads an existing inventory file, or an empty configuration when it does not exist
     * or cannot be parsed. Mirrors {@code new Config(File)}.
     *
     * @param file
     *            The {@code .sfi} file to read
     *
     * @return The parsed configuration, never null
     */
    @Nonnull
    static YamlConfiguration load(@Nonnull File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.options().copyDefaults(true);

        return cfg;
    }

    /**
     * Stores a value under the given key. {@code null} clears the key, which is what
     * {@code Config#setValue(String, Object)} does for an empty slot.
     *
     * @param cfg
     *            The configuration to write into
     * @param key
     *            The key to write
     * @param item
     *            The item to store, or null to clear the slot
     */
    static void setItem(@Nonnull YamlConfiguration cfg, @Nonnull String key, @Nullable ItemStack item) {
        cfg.set(key, item);
    }

    /**
     * Writes the configuration back to disk, logging a failure the same way
     * {@code Config#save()} did.
     *
     * @param cfg
     *            The configuration to persist
     * @param file
     *            The {@code .sfi} file to write
     */
    static void save(@Nonnull YamlConfiguration cfg, @Nonnull File file) {
        try {
            SfStores.get().write(path(file), cfg.saveToString());
        } catch (IOException x) {
            Slimefun.logger().log(Level.SEVERE, "Exception while saving a Config file", x);
        }
    }
}
