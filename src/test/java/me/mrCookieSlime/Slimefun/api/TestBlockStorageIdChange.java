package me.mrCookieSlime.Slimefun.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.FileUtils;

/**
 * Cada id de Slimefun persiste sus posiciones en su propio fichero "<id>.sfb". Cuando una
 * posicion cambia de id --Cultivation reemplaza CROP_STICKS por la planta que nace del cruce--
 * la entrada del id anterior tiene que desaparecer. Si sobrevive, en el arranque siguiente dos
 * ficheros reclaman la misma Location, resolveDuplicateBlock descarta una y la planta deja de
 * responder al clic derecho. Ticket 283.
 */
class TestBlockStorageIdChange {

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

    private static File storedBlocksFile(World world, String id) {
        return new File("data-storage/Slimefun/stored-blocks/" + world.getName() + '/' + id + ".sfb");
    }

    @Test
    void testChangingIdDropsTheOldEntry() {
        World world = server.addSimpleWorld("id_change_world");
        BlockStorage storage = BlockStorage.getOrCreate(world);
        Location l = new Location(world, 10, 64, 10);

        BlockStorage.addBlockInfo(l, "id", "CROP_STICKS");
        storage.save();
        flushStore();

        File oldFile = storedBlocksFile(world, "CROP_STICKS");
        Assertions.assertTrue(oldFile.exists(), "el id inicial deberia haberse persistido");

        BlockStorage.addBlockInfo(l, "id", "CULTIVATION_PLANT");
        storage.save();
        flushStore();

        Assertions.assertEquals("CULTIVATION_PLANT", BlockStorage.getLocationInfo(l, "id"));
        Assertions.assertTrue(
            storedBlocksFile(world, "CULTIVATION_PLANT").exists(),
            "el id nuevo deberia estar persistido"
        );
        Assertions.assertFalse(
            oldFile.exists(),
            "la entrada del id anterior quedo huerfana: reclamara la misma Location en el arranque siguiente"
        );
    }

    @Test
    void testUnrelatedKeyKeepsTheSameFile() {
        World world = server.addSimpleWorld("same_id_world");
        BlockStorage storage = BlockStorage.getOrCreate(world);
        Location l = new Location(world, 4, 64, 4);

        BlockStorage.addBlockInfo(l, "id", "CROP_STICKS");
        BlockStorage.addBlockInfo(l, "owner", "pacox77");
        storage.save();
        flushStore();

        Assertions.assertTrue(
            storedBlocksFile(world, "CROP_STICKS").exists(),
            "escribir una clave que no es el id no debe borrar el fichero del id"
        );
        Assertions.assertEquals("CROP_STICKS", BlockStorage.getLocationInfo(l, "id"));
        Assertions.assertEquals("pacox77", BlockStorage.getLocationInfo(l, "owner"));
    }

    @Test
    void testLosingIdWithoutBreakingIsLogged() {
        World world = server.addSimpleWorld("id_lost_world");
        BlockStorage.getOrCreate(world);
        Location l = new Location(world, 7, 64, 7);
        BlockStorage.addBlockInfo(l, "id", "CROP_STICKS");
        Assertions.assertTrue(BlockStorage.hasBlockInfo(l));

        List<String> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage().startsWith("[BlockStorage]")) {
                    warnings.add(String.valueOf(record.getParameters()[1]) + '|' + record.getParameters()[3]);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Slimefun.logger().addHandler(handler);

        try {
            // Ticket 111: un json sin 'id' sobre una maquina borra su entrada .sfb sin tocar el bloque.
            BlockStorage.setBlockInfo(l, "{\"owner\":\"x\"}", false);
        } finally {
            Slimefun.logger().removeHandler(handler);
        }

        Assertions.assertFalse(BlockStorage.hasBlockInfo(l));
        Assertions.assertEquals(1, warnings.size(), "la perdida del id debe quedar registrada");
        Assertions.assertTrue(warnings.get(0).startsWith("CROP_STICKS|"));
        Assertions.assertTrue(warnings.get(0).contains("TestBlockStorageIdChange"), "el WARN debe nombrar al llamador");
    }

    private static void flushStore() {
        io.github.thebusybiscuit.slimefun4.storage.backend.SfStores.get().flush();
    }
}
