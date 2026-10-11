package com.github.drakescraft_labs.slimefun4.core.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;

import com.github.drakescraft_labs.slimefun4.api.player.PlayerProfile;
import com.github.drakescraft_labs.slimefun4.core.debug.Debug;
import com.github.drakescraft_labs.slimefun4.core.debug.TestCase;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;

import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;

/**
 * This Service is responsible for automatically saving {@link Player} and {@link Block}
 * data.
 * 
 * @author TheBusyBiscuit
 *
 */
public class AutoSavingService implements Listener {

    private int interval;
    private Slimefun plugin;
    private int dynamicIntervalSeconds;
    private int dynamicThreshold;
    private int dynamicBatchSize;
    private final AtomicBoolean playerPersistenceRunning = new AtomicBoolean();
    private final AtomicBoolean blockPersistenceRunning = new AtomicBoolean();
    private int blockSaveMaxRetries = 30;

    /**
     * Ticks to wait before retrying a periodic block save that lost the persistence lock.
     */
    private static final long BLOCK_SAVE_RETRY_DELAY = 20L;

    /**
     * Retries a periodic block save always gets, even when the configured interval is
     * too short to fit the regular budget.
     */
    private static final int BLOCK_SAVE_MIN_RETRIES = 30;

    /**
     * Seconds at the tail of the interval kept free of retries, so a cycle always gives
     * up before its successor is scheduled instead of leaving two retry chains running.
     */
    private static final int BLOCK_SAVE_RETRY_MARGIN_SECONDS = 30;

    /**
     * This method starts the {@link AutoSavingService} with the given interval.
     * 
     * @param plugin
     *            The current instance of Slimefun
     * @param interval
     *            The interval in which to run this task
     */
    public void start(@Nonnull Slimefun plugin, int interval) {
        this.interval = interval;
        this.plugin = plugin;
        this.blockSaveMaxRetries = computeBlockSaveMaxRetries(interval);
        this.dynamicIntervalSeconds = Math.max(0, plugin.getConfig().getInt("performance.dynamic-block-autosave.interval-seconds", 30));
        this.dynamicThreshold = Math.max(1, plugin.getConfig().getInt("performance.dynamic-block-autosave.threshold", 2048));
        this.dynamicBatchSize = Math.max(1, plugin.getConfig().getInt("performance.dynamic-block-autosave.batch-size", 500));

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllPlayers, 2000L, interval * 60L * 20L);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllBlocks, 2000L, interval * 60L * 20L);
        if (dynamicIntervalSeconds > 0) {
            // Runs on the server thread because Slimefun inventories must be copied there.
            // The block files are then written asynchronously, see saveDirtyBlocksBatch().
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveDirtyBlocksBatch, dynamicIntervalSeconds * 20L, dynamicIntervalSeconds * 20L);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * This method saves every {@link PlayerProfile} in memory and removes profiles
     * that were marked for deletion.
     */
    private void saveAllPlayers() {
        if (!playerPersistenceRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            saveAllPlayersNow();
        } finally {
            playerPersistenceRunning.set(false);
        }
    }

    private void saveAllPlayersNow() {
        Iterator<PlayerProfile> iterator = PlayerProfile.iterator();
        int players = 0;

        Debug.log(TestCase.PLAYER_PROFILE_DATA, "Saving all players data");

        while (iterator.hasNext()) {
            try {
                PlayerProfile profile = iterator.next();

                if (profile.isDirty()) {
                    players++;
                    profile.save();

                    Debug.log(TestCase.PLAYER_PROFILE_DATA, "Saved data for {} ({})",
                        profile.getPlayer() != null ? profile.getPlayer().getName() : "Unknown", profile.getUUID()
                    );
                }

                // Remove the PlayerProfile from memory if the player has left the server (marked from removal)
                // and they're still not on the server
                // At this point, we've already saved their profile so we can safely remove it
                // without worry for having a data sync issue (e.g. data is changed but then we try to re-load older data)
                if (profile.isMarkedForDeletion() && profile.getPlayer() == null) {
                    iterator.remove();

                    Debug.log(TestCase.PLAYER_PROFILE_DATA, "Removed data from memory for {}",
                        profile.getUUID()
                    );
                }
            } catch (Throwable t) {
                Slimefun.logger().log(Level.SEVERE, "Error saving player profile during auto-save", t);
            }
        }

        if (players > 0) {
            Slimefun.logger().log(Level.INFO, "Auto-saved all player data for {0} player(s)!", players);
        }
    }

    /**
     * This method saves the data of every {@link Block} marked dirty by {@link BlockStorage}.
     */
    private void saveAllBlocks() {
        saveAllBlocksWithRetry(0);
    }

    private void saveAllBlocksWithRetry(int attempt) {
        if (!blockPersistenceRunning.compareAndSet(false, true)) {
            // The dynamic batch drain holds the lock. Returning here would push the full
            // pass -- and BlockStorage.saveChunks() with it -- back by a whole interval,
            // so retry shortly instead of dropping the cycle.
            retrySaveAllBlocks(attempt);
            return;
        }

        try {
            saveAllBlocksNow();
        } finally {
            blockPersistenceRunning.set(false);
        }
    }

    /**
     * One retry per second, spread over the interval minus a safety margin. A fixed budget
     * of 30 s was not enough for the dynamic batch to finish draining a large queue, so a
     * cycle could still be lost -- and with it BlockStorage.saveChunks() -- even though the
     * next full pass was still 15 minutes away.
     * 
     * @param intervalMinutes
     *            The configured auto-save interval in minutes
     * 
     * @return How many times a periodic block save may retry before giving up
     */
    private static int computeBlockSaveMaxRetries(int intervalMinutes) {
        long secondsPerRetry = Math.max(1L, BLOCK_SAVE_RETRY_DELAY / 20L);
        long budget = (intervalMinutes * 60L - BLOCK_SAVE_RETRY_MARGIN_SECONDS) / secondsPerRetry;

        return (int) Math.max(BLOCK_SAVE_MIN_RETRIES, budget);
    }

    private void retrySaveAllBlocks(int attempt) {
        if (attempt >= blockSaveMaxRetries) {
            Slimefun.logger().log(Level.WARNING, "Periodic block auto-save skipped: block persistence stayed busy for {0} retries.", blockSaveMaxRetries);
            return;
        }

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> saveAllBlocksWithRetry(attempt + 1), BLOCK_SAVE_RETRY_DELAY);
    }

    private void saveAllBlocksNow() {
        Set<BlockStorage> worlds = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            try {
                BlockStorage storage = BlockStorage.getStorage(world);

                if (storage != null) {
                    storage.computeChanges();

                    if (storage.getChanges() > 0) {
                        worlds.add(storage);
                    }
                }
            } catch (Throwable t) {
                Slimefun.logger().log(Level.SEVERE, "Error computing block changes for world " + world.getName() + " during auto-save", t);
            }
        }

        if (!worlds.isEmpty()) {
            Slimefun.logger().log(Level.INFO, "Auto-saving block data... (Next auto-save: {0}m)", interval);

            for (BlockStorage storage : worlds) {
                try {
                    storage.save();
                } catch (Throwable t) {
                    Slimefun.logger().log(Level.SEVERE, "Error saving block data for world " + (storage.getWorld() != null ? storage.getWorld().getName() : "unknown") + " during auto-save", t);
                }
            }
        }

        try {
            BlockStorage.saveChunks();
        } catch (Throwable t) {
            Slimefun.logger().log(Level.SEVERE, "Error saving chunks during auto-save", t);
        }
    }

    /**
     * This drains large BlockStorage queues in small batches. It avoids letting
     * dirty block data pile up for the full periodic auto-save interval while
     * also avoiding one huge disk I/O spike when busy Slimefun worlds are active.
     */
    private void saveDirtyBlocksBatch() {
        if (!blockPersistenceRunning.compareAndSet(false, true)) {
            return;
        }

        boolean handedOff = false;

        try {
            List<BlockStorage> storages = saveDirtyInventoriesBatchNow();

            /*
             * BlockStorage now builds the block files from scratch inside saveBlockFiles(), so no
             * Bukkit configuration is shared with the server thread anymore. Loading and writing
             * those YAML files is the expensive part of the batch, so it leaves the main thread.
             * The persistence lock is held until the async part is done.
             */
            if (!storages.isEmpty() && plugin != null && plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> saveDirtyBlockFilesBatch(storages));
                handedOff = true;
            }
        } finally {
            if (!handedOff) {
                blockPersistenceRunning.set(false);
            }
        }
    }

    /**
     * Saves dirty Slimefun inventories on the server thread, where they can be copied directly.
     *
     * @return Every {@link BlockStorage} above the threshold, whose block files still need saving
     */
    private List<BlockStorage> saveDirtyInventoriesBatchNow() {
        List<BlockStorage> storages = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            try {
                BlockStorage storage = BlockStorage.getStorage(world);

                if (storage == null) {
                    continue;
                }

                storage.computeChanges();
                int queued = storage.getChanges();

                if (queued >= dynamicThreshold) {
                    Slimefun.logger().log(Level.FINE, "Dynamic block auto-save: draining up to {0} of {1} queued change(s) for world \"{2}\".", new Object[] { dynamicBatchSize, queued, world.getName() });
                    storage.saveInventories(dynamicBatchSize);
                    storages.add(storage);
                }
            } catch (Throwable t) {
                Slimefun.logger().log(Level.SEVERE, "Error draining dynamic block auto-save for world " + world.getName(), t);
            }
        }

        return storages;
    }

    private void saveDirtyBlockFilesBatch(List<BlockStorage> storages) {
        try {
            for (BlockStorage storage : storages) {
                try {
                    storage.saveBlockFiles(dynamicBatchSize);
                } catch (Throwable t) {
                    Slimefun.logger().log(Level.SEVERE, "Error draining dynamic block auto-save for world " + (storage.getWorld() != null ? storage.getWorld().getName() : "unknown"), t);
                }
            }
        } finally {
            blockPersistenceRunning.set(false);
        }
    }

    /**
     * Listen to WorldSaveEvent to automatically write BlockStorage data to disk.
     * This ensures that Slimefun data is aligned with Minecraft world saves,
     * preventing rollback exploits and desync issues.
     */
    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        World world = event.getWorld();
        BlockStorage storage = BlockStorage.getStorage(world);

        if (storage == null || plugin == null) {
            return;
        }

        Runnable saveTask = () -> {
            if (!blockPersistenceRunning.compareAndSet(false, true)) {
                return;
            }

            try {
                storage.computeChanges();
                if (storage.getChanges() > 0) {
                    Slimefun.logger().log(Level.INFO, "Saving BlockStorage for world \"{0}\" on WorldSaveEvent ({1} changes queued)", new Object[] { world.getName(), storage.getChanges() });
                    storage.save();
                }
            } catch (Throwable t) {
                Slimefun.logger().log(Level.SEVERE, "Error saving block data for world " + world.getName() + " on WorldSaveEvent", t);
            } finally {
                blockPersistenceRunning.set(false);
            }
        };

        // Durante el apagado el scheduler rechaza tareas nuevas (IllegalPluginAccessException)
        // y perderíamos el guardado más importante: el del save-all final. Se ejecuta en línea.
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, saveTask);
        } else {
            saveTask.run();
        }
    }

    /**
     * Guardado síncrono al apagar el servidor: todos los perfiles en memoria (no solo los marcados dirty)
     * y el mismo volcado de bloques que el auto-save periódico, más {@link BlockStorage#saveChunks()}.
     * Debe ejecutarse en el hilo principal durante {@code onDisable}.
     */
    public void shutdownSave() {
        Slimefun.logger().log(Level.INFO, "Slimefun: ejecutando guardado de apagado (jugadores, bloques y chunks)...");
        try {
            saveAllPlayersShutdown();
        } catch (Throwable t) {
            Slimefun.logger().log(Level.SEVERE, "Error fatal al guardar los perfiles de jugadores en el apagado", t);
        }
        try {
            saveAllBlocksShutdown();
        } catch (Throwable t) {
            Slimefun.logger().log(Level.SEVERE, "Error fatal al guardar los bloques de BlockStorage en el apagado", t);
        }
        try {
            BlockStorage.saveChunks();
        } catch (Throwable t) {
            Slimefun.logger().log(Level.SEVERE, "Error fatal al guardar los chunks de BlockStorage en el apagado", t);
        }
    }

    private void saveAllPlayersShutdown() {
        Iterator<PlayerProfile> iterator = PlayerProfile.iterator();
        int players = 0;

        while (iterator.hasNext()) {
            PlayerProfile profile = iterator.next();
            try {
                profile.save();
                players++;
            } catch (Throwable t) {
                Slimefun.logger().log(Level.SEVERE, "Error al guardar el perfil del jugador " + profile.getUUID() + " en el apagado", t);
            }
        }

        if (players > 0) {
            Slimefun.logger().log(Level.INFO, "Slimefun: guardados {0} perfil(es) de jugador en el apagado.", players);
        }
    }

    private void saveAllBlocksShutdown() {
        int worlds = 0;

        for (World world : Bukkit.getWorlds()) {
            BlockStorage storage = BlockStorage.getStorage(world);

            if (storage != null) {
                try {
                    int passes = storage.flushUntilClean(5);
                    storage.computeChanges();
                    if (storage.getChanges() > 0) {
                        Slimefun.logger().log(Level.SEVERE, "Slimefun: quedaron {0} cambios sin persistir en el mundo '{1}' tras {2} intentos.", new Object[] { storage.getChanges(), world.getName(), passes });
                    }
                    worlds++;
                } catch (Throwable t) {
                    Slimefun.logger().log(Level.SEVERE, "Error al guardar BlockStorage para el mundo '" + world.getName() + "' en el apagado", t);
                }
            }
        }

        if (worlds > 0) {
            Slimefun.logger().log(Level.INFO, "Slimefun: volcado de BlockStorage revisado en {0} mundo(s) al apagar.", worlds);
        }
    }

}
