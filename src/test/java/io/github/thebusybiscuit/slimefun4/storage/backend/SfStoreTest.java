package io.github.thebusybiscuit.slimefun4.storage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SfStoreTest {

    private static void roundTrip(SfStore store, String base) throws Exception {
        String a = base + "/stored-blocks/world/A.sfb";
        String b = base + "/stored-blocks/world/B.sfb";
        assertNull(store.read(a));
        assertFalse(store.exists(a));

        store.write(a, "x: 1\n");
        store.write(b, "y: 2\n");
        store.write(a, "x: 3\n"); // reemplazo

        assertEquals("x: 3\n", store.read(a));
        assertTrue(store.exists(b));

        Map<String, String> listed = new HashMap<>();
        store.forEach(base + "/stored-blocks/world", listed::put);
        assertEquals(Map.of("A.sfb", "x: 3\n", "B.sfb", "y: 2\n"), listed);

        store.delete(a);
        assertNull(store.read(a));
        store.delete(a); // borrar lo inexistente no falla
    }

    @Test
    void fileStore(@TempDir Path dir) throws Exception {
        roundTrip(new FileSfStore(), dir.toString());
    }

    @Test
    void sqliteStoreAndImport(@TempDir Path dir) throws Exception {
        Path blocks = dir.resolve("disk/stored-blocks/w");
        Files.createDirectories(blocks);
        Files.writeString(blocks.resolve("OLD.sfb"), "z: 9\n");

        SqlSfStore sql = new SqlSfStore("jdbc:sqlite:" + dir.resolve("t.db"), null, null);
        assertEquals(1, sql.importFromDisk(dir.resolve("disk").toString()));
        assertEquals(0, sql.importFromDisk(dir.resolve("disk").toString())); // solo importa con la tabla vacia
        assertEquals("z: 9\n", sql.read(blocks.resolve("OLD.sfb").toString().replace('\\', '/')));

        roundTrip(sql, "data-storage/Slimefun");
        sql.close();
    }
}
