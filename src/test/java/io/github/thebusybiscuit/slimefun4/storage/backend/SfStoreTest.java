package io.github.thebusybiscuit.slimefun4.storage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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

    @Test
    void fileBatch(@TempDir Path dir) throws Exception {
        FileSfStore store = new FileSfStore();
        Map<String, String> batch = new HashMap<>();
        for (int i = 0; i < 600; i++) { // más que el CHUNK de descriptores
            batch.put(dir + "/stored-blocks/w/" + i + ".sfb", "v: " + i + "\n");
        }
        store.writeAll(batch);
        assertEquals("v: 599\n", store.read(dir + "/stored-blocks/w/599.sfb"));
        try (var files = Files.list(dir.resolve("stored-blocks/w"))) {
            assertEquals(600, files.count()); // sin .tmp residuales
        }
        batch.replaceAll((k, v) -> v + "x");
        store.writeAll(batch);
        assertEquals("v: 0\nx", store.read(dir + "/stored-blocks/w/0.sfb"));
    }

    @Test
    void sqlBatchIsTransactional(@TempDir Path dir) throws Exception {
        SqlSfStore sql = new SqlSfStore("jdbc:sqlite:" + dir.resolve("b.db"), null, null);
        Map<String, String> batch = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            batch.put("d/stored-inventories/" + i + ".sfi", "i: " + i);
        }
        sql.writeAll(batch);
        Map<String, String> listed = new HashMap<>();
        sql.forEach("d/stored-inventories", listed::put);
        assertEquals(50, listed.size());
        sql.close();
    }

    /** Backend lento que registra el orden y cuenta lecturas. */
    private static final class SlowStore extends FileSfStore {
        final List<String> log = java.util.Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public String read(String path) {
            reads.incrementAndGet();
            return super.read(path);
        }

        @Override
        public void writeAll(Map<String, String> batch) throws IOException {
            sleep();
            log.add("W" + new java.util.TreeMap<>(batch).keySet());
            super.writeAll(batch);
        }

        @Override
        public void delete(String path) throws IOException {
            sleep();
            log.add("D[" + path + "]");
            super.delete(path);
        }

        private static void sleep() {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void cachedReadYourWritesAndNoBackendReads(@TempDir Path dir) throws Exception {
        SlowStore slow = new SlowStore();
        CachedSfStore store = new CachedSfStore(slow);
        String p = dir + "/stored-inventories/a.sfi";
        store.write(p, "v1");
        assertEquals("v1", store.read(p)); // aún en cola
        store.write(p, "v2");
        assertEquals("v2", store.read(p));
        store.flush();
        assertEquals("v2", slow.unwrap().read(p));
        slow.reads.set(0);
        for (int i = 0; i < 100; i++) {
            assertEquals("v2", store.read(p));
        }
        assertEquals(0, slow.reads.get()); // cache write-through: ni un acceso al backend
        store.delete(p);
        assertNull(store.read(p));
        assertFalse(store.exists(p));
        store.flush();
        assertFalse(slow.exists(p));
        store.close();
    }

    @Test
    void cachedPreloadViaForEach(@TempDir Path dir) throws Exception {
        String d = dir + "/stored-inventories";
        Files.createDirectories(Path.of(d));
        Files.writeString(Path.of(d, "x.sfi"), "pre");
        SlowStore slow = new SlowStore();
        CachedSfStore store = new CachedSfStore(slow);
        Map<String, String> seen = new HashMap<>();
        store.forEach(d, seen::put);
        assertEquals(Map.of("x.sfi", "pre"), seen);
        slow.reads.set(0);
        assertEquals("pre", store.read(d + "/x.sfi"));
        assertEquals(0, slow.reads.get());
        store.close();
    }

    @Test
    void cachedOrderAndCloseDrainsQueue(@TempDir Path dir) throws Exception {
        SlowStore slow = new SlowStore();
        CachedSfStore store = new CachedSfStore(slow);
        String a = dir + "/stored-blocks/w/a.sfb";
        String b = dir + "/stored-blocks/w/b.sfb";
        store.write(a, "1");
        store.delete(a);
        store.write(a, "2");
        store.writeAll(Map.of(b, "3"));
        store.write(a, "4");
        assertEquals("4", store.read(a)); // pendiente, aún no en disco
        store.close(); // debe vaciar la cola entera
        assertEquals("4", Files.readString(Path.of(a)));
        assertEquals("3", Files.readString(Path.of(b)));
        // la secuencia del backend respeta el orden: el borrado queda entre dos escrituras
        int del = slow.log.indexOf("D[" + a + "]");
        assertTrue(del > 0 || slow.log.get(0).startsWith("W"), slow.log.toString());
        assertTrue(slow.log.get(slow.log.size() - 1).contains(a) || slow.log.get(slow.log.size() - 1).contains(b));
        assertThrowsClosed(store, a);
    }

    private static void assertThrowsClosed(SfStore store, String path) {
        try {
            store.write(path, "late");
            throw new AssertionError("debia fallar tras close()");
        } catch (IOException expected) {
            // ok
        }
    }
}
