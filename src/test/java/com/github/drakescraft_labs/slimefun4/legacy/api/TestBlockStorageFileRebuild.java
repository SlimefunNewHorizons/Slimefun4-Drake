package com.github.drakescraft_labs.slimefun4.legacy.api;

import java.io.File;
import java.io.IOException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.utils.FileUtils;

/**
 * Cada "<id>.sfb" se reconstruye desde los bloques vivos al guardar, sin releerlo de disco.
 * Esa reconstruccion no puede perder nada de lo que habia en el fichero: ni los bloques que no
 * cambiaron, ni las entradas que no se pudieron cargar, ni los bloques guardados en el fichero
 * de otro id.
 */
class TestBlockStorageFileRebuild {

    private static ServerMock server;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() throws IOException {
        MockBukkit.unmock();
        FileUtils.deleteDirectory(new File("data-storage"));
    }

    private static File storedBlocksFile(String worldName, String id) {
        return new File("data-storage/Slimefun/stored-blocks/" + worldName + '/' + id + ".sfb");
    }

    private static void writeBlocksFile(String worldName, String id, String... keysAndValues) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();

        for (int i = 0; i < keysAndValues.length; i += 2) {
            cfg.set(keysAndValues[i], keysAndValues[i + 1]);
        }

        File file = storedBlocksFile(worldName, id);
        file.getParentFile().mkdirs();
        cfg.save(file);
    }

    @Test
    void testRebuildKeepsUntouchedAndUnloadableEntries() throws IOException {
        String worldName = "rebuild_world";
        writeBlocksFile(worldName, "REBUILD_ID",
            worldName + ";1;64;1", "{\"id\":\"REBUILD_ID\",\"x\":\"1\"}",
            worldName + ";2;64;2", "{\"id\":\"REBUILD_ID\",\"x\":\"2\"}",
            worldName + ";3;64;3", "{this is not json",
            worldName + ";4;64;4", "{\"x\":\"no id\"}"
        );

        World world = server.addSimpleWorld(worldName);
        BlockStorage storage = BlockStorage.getOrCreate(world);

        BlockStorage.addBlockInfo(new Location(world, 1, 64, 1), "x", "changed");
        storage.save();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(storedBlocksFile(worldName, "REBUILD_ID"));
        Assertions.assertTrue(saved.getString(worldName + ";1;64;1").contains("changed"), "el bloque modificado deberia guardarse con su valor nuevo");
        // Se reserializa desde memoria: el orden de las claves puede cambiar, el contenido no
        JsonObject untouched = JsonParser.parseString(saved.getString(worldName + ";2;64;2")).getAsJsonObject();
        Assertions.assertEquals("REBUILD_ID", untouched.get("id").getAsString(), "un bloque sin cambios no puede desaparecer del fichero");
        Assertions.assertEquals("2", untouched.get("x").getAsString());
        Assertions.assertEquals("{this is not json", saved.getString(worldName + ";3;64;3"), "una entrada que no se pudo cargar debe seguir en disco");
        Assertions.assertEquals("{\"x\":\"no id\"}", saved.getString(worldName + ";4;64;4"), "una entrada sin id debe seguir en disco");
    }

    @Test
    void testBlockInTheWrongFileIsMoved() throws IOException {
        String worldName = "wrong_file_world";
        writeBlocksFile(worldName, "OTHER_ID", worldName + ";5;64;5", "{\"id\":\"MOVED_ID\"}");

        World world = server.addSimpleWorld(worldName);
        BlockStorage storage = BlockStorage.getOrCreate(world);
        storage.save();

        YamlConfiguration moved = YamlConfiguration.loadConfiguration(storedBlocksFile(worldName, "MOVED_ID"));
        Assertions.assertNotNull(moved.getString(worldName + ";5;64;5"), "el bloque deberia estar en el fichero de su id");
        Assertions.assertFalse(storedBlocksFile(worldName, "OTHER_ID").exists(), "el fichero del otro id ya no tiene entradas y deberia borrarse");
    }

    @Test
    void testDeletingAnInputNodeDropsItsPendingItems() {
        World world = server.addSimpleWorld("pending_overflow_world");
        BlockStorage.getOrCreate(world);
        Location l = new Location(world, 6, 64, 6);

        YamlConfiguration item = new YamlConfiguration();
        item.set("item", new ItemStack(Material.DIAMOND, 7));

        BlockStorage.addBlockInfo(l, "id", "CARGO_NODE_INPUT");
        BlockStorage.addBlockInfo(l, "pending-overflow", item.saveToString());
        BlockStorage.deleteLocationInfoUnsafely(l, true);

        int dropped = world.getEntitiesByClass(Item.class).stream()
            .filter(entity -> entity.getItemStack().getType() == Material.DIAMOND)
            .mapToInt(entity -> entity.getItemStack().getAmount())
            .sum();

        Assertions.assertEquals(7, dropped, "los items pendientes del nodo deberian soltarse al borrar sus datos");
        Assertions.assertFalse(BlockStorage.hasBlockInfo(l));
    }
}
