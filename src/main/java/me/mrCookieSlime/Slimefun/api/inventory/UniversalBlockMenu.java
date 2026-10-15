package me.mrCookieSlime.Slimefun.api.inventory;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

import io.github.bakedlibs.dough.config.Config;

// This class will be deprecated, relocated and rewritten in a future version.
public class UniversalBlockMenu extends DirtyChestMenu {

    public UniversalBlockMenu(BlockMenuPreset preset) {
        super(preset);

        preset.clone(this);

        save();
    }

    public UniversalBlockMenu(BlockMenuPreset preset, Config cfg) {
        super(preset);

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

    public void save() {
        if (!isDirty()) {
            return;
        }

        // To force CS-CoreLib to build the Inventory
        this.getContents();

        File file = new File("data-storage/Slimefun/universal-inventories/" + preset.getID() + ".sfi");
        // Mismo motivo que en BlockMenu$MenuSnapshot#save: un DoughLogger por guardado
        // serializa el treeLock global de java.util.logging. Ver InventoryFileWriter.
        YamlConfiguration cfg = InventoryFileWriter.load(file);
        cfg.set("preset", preset.getID());

        for (int slot : preset.getInventorySlots()) {
            InventoryFileWriter.setItem(cfg, String.valueOf(slot), getItemInSlot(slot));
        }

        InventoryFileWriter.save(cfg, file);

        changes = 0;
    }

}
