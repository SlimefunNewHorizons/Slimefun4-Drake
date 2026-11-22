package me.mrCookieSlime.Slimefun.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.thebusybiscuit.slimefun4.storage.backend.CachedSfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStores;
import io.github.thebusybiscuit.slimefun4.storage.backend.SqlSfStore;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;

class TestBlockStorageSfStoreCacheLoad {

    @Test
    void loadBlockCacheConfigReadsFromActiveSfStoreInsteadOfDisk(@TempDir Path tempDir) throws Exception {
        SfStore sql = new CachedSfStore(new SqlSfStore("jdbc:sqlite:" + tempDir.resolve("sf.db"), null, null));
        Field activeField = SfStores.class.getDeclaredField("active");
        activeField.setAccessible(true);
        SfStore previous = (SfStore) activeField.get(null);

        try {
            activeField.set(null, sql);

            String vpath = "data-storage/Slimefun/stored-blocks/test_world_sfstore/FM_FAST_GRIND_STONE.sfb";
            sql.write(vpath,
                "test_world_sfstore;-12131;64;14126: '{\"energy-charge\":\"1024\",\"id\":\"FM_FAST_GRIND_STONE\"}'\n"
                    + "test_world_sfstore;10;64;10: '{\"energy-charge\":\"512\",\"id\":\"FM_FAST_GRIND_STONE\"}'\n"
            );

            Config cfg = BlockStorage.loadBlockCacheConfig("test_world_sfstore", "FM_FAST_GRIND_STONE");
            assertEquals(2, cfg.getKeys().size());
            assertTrue(cfg.contains("test_world_sfstore;-12131;64;14126"));
            assertTrue(cfg.contains("test_world_sfstore;10;64;10"));

            // Mutating another location must preserve all existing entries loaded from SfStore.
            cfg.setValue("test_world_sfstore;20;64;20", "{\"energy-charge\":\"256\",\"id\":\"FM_FAST_GRIND_STONE\"}");
            assertEquals(3, cfg.getKeys().size());
            assertTrue(cfg.contains("test_world_sfstore;-12131;64;14126"));
        } finally {
            sql.close();
            activeField.set(null, previous);
        }
    }

    @Test
    void inventoryFileWriterLoadsFromActiveSfStore(@TempDir Path tempDir) throws Exception {
        SfStore sql = new CachedSfStore(new SqlSfStore("jdbc:sqlite:" + tempDir.resolve("sf-inv.db"), null, null));
        Field activeField = SfStores.class.getDeclaredField("active");
        activeField.setAccessible(true);
        SfStore previous = (SfStore) activeField.get(null);

        try {
            activeField.set(null, sql);

            File file = new File("data-storage/Slimefun/stored-inventories/test_world_sfstore;-12131;64;14126.sfi");
            sql.write(file.getPath().replace('\\', '/'), "preset: FM_FAST_GRIND_STONE\n");

            var loader = Class.forName("me.mrCookieSlime.Slimefun.api.inventory.InventoryFileWriter")
                .getDeclaredMethod("load", File.class);
            loader.setAccessible(true);
            var yaml = (org.bukkit.configuration.file.YamlConfiguration) loader.invoke(null, file);
            assertEquals("FM_FAST_GRIND_STONE", yaml.getString("preset"));
        } finally {
            sql.close();
            activeField.set(null, previous);
        }
    }
}
