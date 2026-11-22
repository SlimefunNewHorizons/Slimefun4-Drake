package me.mrCookieSlime.Slimefun.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.backend.CachedSfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStore;
import io.github.thebusybiscuit.slimefun4.storage.backend.SfStores;
import io.github.thebusybiscuit.slimefun4.storage.backend.SqlSfStore;
import io.github.thebusybiscuit.slimefun4.utils.FileUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;

class TestBlockStorageSfStoreCacheLoad {

    private static ServerMock server;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() throws IOException {
        MockBukkit.unmock();
        FileUtils.deleteDirectory(new File("data-storage"));
    }

    private static SfStore swapActiveStore(SfStore replacement) throws Exception {
        Field activeField = SfStores.class.getDeclaredField("active");
        activeField.setAccessible(true);
        SfStore previous = (SfStore) activeField.get(null);
        activeField.set(null, replacement);
        return previous;
    }

    @Test
    void loadBlockCacheConfigReadsFromActiveSfStoreInsteadOfDisk(@TempDir Path tempDir) throws Exception {
        SfStore sql = new CachedSfStore(new SqlSfStore("jdbc:sqlite:" + tempDir.resolve("sf.db"), null, null));
        SfStore previous = swapActiveStore(sql);

        try {
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
            swapActiveStore(previous);
        }
    }

    @Test
    void loadBlockCacheConfigReconstructsFromMemoryWhenSfStoreReturnsNull() throws Exception {
        World world = server.addSimpleWorld("reconstruct_null_world");
        BlockStorage storage = BlockStorage.getOrCreate(world);
        Location l1 = new Location(world, 1, 64, 1);
        Location l2 = new Location(world, 2, 64, 2);
        Location other = new Location(world, 3, 64, 3);

        BlockStorage.addBlockInfo(l1, "id", "FM_FAST_GRIND_STONE");
        BlockStorage.addBlockInfo(l1, "energy-charge", "1024");
        BlockStorage.addBlockInfo(l2, "id", "FM_FAST_GRIND_STONE");
        BlockStorage.addBlockInfo(l2, "energy-charge", "512");
        BlockStorage.addBlockInfo(other, "id", "OTHER_MACHINE");

        SfStore failingReadStore = new InMemorySfStore() {
            @Override
            public String read(String path) {
                return null;
            }
        };
        SfStore previous = swapActiveStore(failingReadStore);

        try {
            Config cfg = BlockStorage.loadBlockCacheConfig(storage, world.getName(), "FM_FAST_GRIND_STONE");
            assertEquals(2, cfg.getKeys().size());
            assertTrue(cfg.contains(world.getName() + ";1;64;1"));
            assertTrue(cfg.contains(world.getName() + ";2;64;2"));
            assertFalse(cfg.contains(world.getName() + ";3;64;3"));
            assertTrue(cfg.getString(world.getName() + ";1;64;1").contains("\"energy-charge\":\"1024\""));
            assertTrue(cfg.getString(world.getName() + ";2;64;2").contains("\"energy-charge\":\"512\""));
        } finally {
            swapActiveStore(previous);
        }
    }

    @Test
    void loadBlockCacheConfigReconstructsFromMemoryWhenSfStoreReturnsCorruptYaml() throws Exception {
        World world = server.addSimpleWorld("reconstruct_corrupt_world");
        BlockStorage storage = BlockStorage.getOrCreate(world);
        Location l1 = new Location(world, 10, 70, 10);
        Location l2 = new Location(world, 20, 70, 20);

        BlockStorage.addBlockInfo(l1, "id", "FM_FAST_GRIND_STONE");
        BlockStorage.addBlockInfo(l2, "id", "FM_FAST_GRIND_STONE");

        SfStore corruptStore = new InMemorySfStore() {
            @Override
            public String read(String path) {
                return ":\n\t[corrupt: yaml: :::";
            }
        };
        SfStore previous = swapActiveStore(corruptStore);

        try {
            Config cfg = BlockStorage.loadBlockCacheConfig(storage, world.getName(), "FM_FAST_GRIND_STONE");
            assertEquals(2, cfg.getKeys().size());
            assertTrue(cfg.contains(world.getName() + ";10;70;10"));
            assertTrue(cfg.contains(world.getName() + ";20;70;20"));
        } finally {
            swapActiveStore(previous);
        }
    }

    @Test
    void concurrentRefreshCacheAndSaveNeverLosesNewlyPlacedBlocks() throws Exception {
        World world = server.addSimpleWorld("concurrent_save_world");
        BlockStorage storage = BlockStorage.getOrCreate(world);

        CountDownLatch enteredWriteAll = new CountDownLatch(1);
        CountDownLatch allowWriteAllToFinish = new CountDownLatch(1);
        AtomicBoolean firstWriteAll = new AtomicBoolean(true);

        InMemorySfStore store = new InMemorySfStore() {
            @Override
            public void writeAll(Map<String, String> batch) throws IOException {
                if (firstWriteAll.compareAndSet(true, false)) {
                    enteredWriteAll.countDown();
                    try {
                        allowWriteAllToFinish.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                super.writeAll(batch);
            }
        };

        SfStore previous = swapActiveStore(store);

        try {
            Location loc1 = new Location(world, 100, 64, 100);
            Location loc2 = new Location(world, 200, 64, 200);
            BlockStorage.addBlockInfo(loc1, "id", "FM_FAST_GRIND_STONE");

            AtomicReference<Throwable> asyncError = new AtomicReference<>();
            Thread saveThread = new Thread(() -> {
                try {
                    storage.save();
                } catch (Throwable t) {
                    asyncError.set(t);
                }
            }, "test-autosave-thread");
            saveThread.start();

            assertTrue(enteredWriteAll.await(5, TimeUnit.SECONDS), "save() should reach writeAll()");

            AtomicReference<Throwable> mainError = new AtomicReference<>();
            Thread placeThread = new Thread(() -> {
                try {
                    BlockStorage.addBlockInfo(loc2, "id", "FM_FAST_GRIND_STONE");
                } catch (Throwable t) {
                    mainError.set(t);
                }
            }, "test-main-place-thread");
            placeThread.start();

            // Give placeThread time to contend on synchronized (storage) while save() is in writeAll().
            Thread.sleep(50);
            allowWriteAllToFinish.countDown();

            saveThread.join(5000);
            placeThread.join(5000);
            assertEquals(null, asyncError.get());
            assertEquals(null, mainError.get());

            // Flush the second block placed concurrently during the first save().
            storage.save();

            String vpath = "data-storage/Slimefun/stored-blocks/" + world.getName() + "/FM_FAST_GRIND_STONE.sfb";
            String persisted = store.read(vpath);
            assertNotNull(persisted, "Persisted .sfb content must exist");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(persisted);
            assertTrue(yaml.contains(world.getName() + ";100;64;100"), "First block must remain persisted");
            assertTrue(yaml.contains(world.getName() + ";200;64;200"), "Concurrently placed second block must not be lost");
        } finally {
            swapActiveStore(previous);
        }
    }

    @Test
    void inventoryFileWriterLoadsFromActiveSfStore(@TempDir Path tempDir) throws Exception {
        SfStore sql = new CachedSfStore(new SqlSfStore("jdbc:sqlite:" + tempDir.resolve("sf-inv.db"), null, null));
        SfStore previous = swapActiveStore(sql);

        try {
            File file = new File("data-storage/Slimefun/stored-inventories/test_world_sfstore;-12131;64;14126.sfi");
            sql.write(file.getPath().replace('\\', '/'), "preset: FM_FAST_GRIND_STONE\n");

            var loader = Class.forName("me.mrCookieSlime.Slimefun.api.inventory.InventoryFileWriter")
                .getDeclaredMethod("load", File.class);
            loader.setAccessible(true);
            var yaml = (org.bukkit.configuration.file.YamlConfiguration) loader.invoke(null, file);
            assertEquals("FM_FAST_GRIND_STONE", yaml.getString("preset"));
        } finally {
            sql.close();
            swapActiveStore(previous);
        }
    }

    private static class InMemorySfStore implements SfStore {
        private final Map<String, String> data = new ConcurrentHashMap<>();

        @Override
        public String read(String path) {
            return data.get(path);
        }

        @Override
        public boolean exists(String path) {
            return data.containsKey(path);
        }

        @Override
        public void write(String path, String content) throws IOException {
            data.put(path, content);
        }

        @Override
        public void delete(String path) throws IOException {
            data.remove(path);
        }

        @Override
        public void forEach(String dir, BiConsumer<String, String> action) {
            String prefix = dir.endsWith("/") ? dir : dir + "/";
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    String name = entry.getKey().substring(prefix.length());
                    if (!name.contains("/")) {
                        action.accept(name, entry.getValue());
                    }
                }
            }
        }
    }
}
