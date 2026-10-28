package me.mrCookieSlime.Slimefun.api.inventory;

import io.github.thebusybiscuit.slimefun4.storage.backend.SfStores;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

// This class will be deprecated, relocated and rewritten in a future version.
public class BlockMenu extends DirtyChestMenu {

    private Location location;

    private static String serializeLocation(Location l) {
        return l.getWorld().getName() + ';' + l.getBlockX() + ';' + l.getBlockY() + ';' + l.getBlockZ();
    }

    public BlockMenu(BlockMenuPreset preset, Location l) {
        super(preset);
        this.location = l;

        preset.clone(this);
        this.getContents();
    }

    public BlockMenu(BlockMenuPreset preset, Location l, Config cfg) {
        super(preset);
        this.location = l;

        for (int i = 0; i < 54; i++) {
            if (cfg.contains(String.valueOf(i))) {
                addItem(i, cfg.getItem(String.valueOf(i)));
            }
        }

        preset.clone(this);

        if (preset.getSize() > -1 && !preset.getPresetSlots().contains(preset.getSize() - 1) && cfg.contains(String.valueOf(preset.getSize() - 1))) {
            addItem(preset.getSize() - 1, cfg.getItem(String.valueOf(preset.getSize() - 1)));
        }

        this.getContents();
    }

    public void save(Location l) {
        MenuSnapshot snapshot = captureSnapshot(l);

        if (snapshot == null) {
            return;
        }

        snapshot.save();
    }

    /**
     * Bukkit inventories are main-thread state. Async autosaves must only write an
     * immutable copy, otherwise machines can persist a partial inventory during a restart.
     */
    private MenuSnapshot captureSnapshot(Location l) {
        if (Bukkit.isPrimaryThread()) {
            return captureSnapshotOnPrimaryThread(l);
        }

        Slimefun plugin = Slimefun.instance();

        /*
         * Al apagar, el plugin sigue existiendo pero ya esta deshabilitado y el scheduler
         * rechaza tareas nuevas. Antes se comprobaba solo el null, se intentaba programar igual
         * y saltaba IllegalPluginAccessException, que no estaba cazada y abortaba el resto del
         * lote de ese mundo.
         *
         * Devolver null deja el bloque marcado como sucio a proposito: no se toca 'changes', asi
         * que lo recoge el volcado sincrono de AutoSavingService#shutdownSave, que corre en el
         * hilo principal y reintenta hasta cinco veces. Saltarse esta pasada no pierde nada.
         */
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> captureSnapshotOnPrimaryThread(l)).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while snapshotting Slimefun inventory", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Could not snapshot Slimefun inventory", ex.getCause());
        } catch (CancellationException | IllegalPluginAccessException ex) {
            // Carrera con el apagado: entre el isEnabled() de arriba y el callSyncMethod el
            // servidor empezo a pararse. Mismo trato, el bloque sigue sucio para el volcado.
            return null;
        }
    }

    private MenuSnapshot captureSnapshotOnPrimaryThread(Location l) {
        if (!isDirty()) {
            return null;
        }

        // Force the legacy menu to materialize before taking independent ItemStack copies.
        ItemStack[] liveContents = this.getContents();
        ItemStack[] contents = new ItemStack[liveContents.length];

        for (int slot : preset.getInventorySlots()) {
            ItemStack item = super.getItemInSlot(slot);
            contents[slot] = item == null ? null : item.clone();
        }

        changes = 0;
        return new MenuSnapshot(l, preset.getID(), new HashSet<>(preset.getInventorySlots()), contents);
    }

    private static final class MenuSnapshot {

        private final Location location;
        private final String presetId;
        private final Set<Integer> slots;
        private final ItemStack[] contents;

        private MenuSnapshot(Location location, String presetId, Set<Integer> slots, ItemStack[] contents) {
            this.location = location;
            this.presetId = presetId;
            this.slots = slots;
            this.contents = contents;
        }

        private void save() {
            File file = new File("data-storage/Slimefun/stored-inventories/" + serializeLocation(location) + ".sfi");
            /*
             * No se usa dough Config aqui: su constructor crea un DoughLogger por instancia y ese
             * setParent/setLevel toma el treeLock global de java.util.logging. El lote sincrono de
             * autoguardado persiste cientos de menus por pasada, asi que ese lock llego a bloquear
             * el Server thread 10 s y disparar el watchdog (2026-09-12). Ver InventoryFileWriter.
             */
            YamlConfiguration cfg = InventoryFileWriter.load(file);
            cfg.set("preset", presetId);

            for (int slot : slots) {
                InventoryFileWriter.setItem(cfg, String.valueOf(slot), contents[slot]);
            }

            InventoryFileWriter.save(cfg, file);
        }
    }

    public void move(Location l) {
        this.delete(this.location);
        this.location = l;
        this.preset.newInstance(this, l);
        this.save(l);
    }

    /**
     * Reload this {@link BlockMenu} based on its {@link BlockMenuPreset}.
     */
    public void reload() {
        this.preset.clone(this);
    }

    public Block getBlock() {
        return location.getBlock();
    }

    public Location getLocation() {
        return location;
    }

    /**
     * This method drops the contents of this {@link BlockMenu} on the ground at the given
     * {@link Location}.
     * 
     * @param l
     *            Where to drop these items
     * @param slots
     *            The slots of items that should be dropped
     */
    public void dropItems(Location l, int... slots) {
        for (int slot : slots) {
            ItemStack item = getItemInSlot(slot);

            if (item != null) {
                l.getWorld().dropItemNaturally(l, item);
                replaceExistingItem(slot, null);
            }
        }
    }

    public void delete(Location l) {
        File file = new File("data-storage/Slimefun/stored-inventories/" + serializeLocation(l) + ".sfi");

        if (SfStores.get().exists(InventoryFileWriter.path(file))) {
            try {
                SfStores.get().delete(InventoryFileWriter.path(file));
            } catch (IOException e) {
                Slimefun.logger().log(Level.WARNING, e, () -> "Could not delete file \"" + file.getName() + '"');
            }
        }
    }
}
