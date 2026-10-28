package io.github.thebusybiscuit.slimefun4.storage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

class TestCachedSfStoreBounds {

    private static final class MemStore implements SfStore {
        final Map<String, String> data = new ConcurrentHashMap<>();

        public String read(String path) {
            return data.get(path);
        }

        public boolean exists(String path) {
            return data.containsKey(path);
        }

        public void write(String path, String content) {
            data.put(path, content);
        }

        public void delete(String path) {
            data.remove(path);
        }

        public void forEach(String dir, BiConsumer<String, String> action) {
            data.forEach((k, v) -> {
                if (k.startsWith(dir + "/")) {
                    action.accept(k.substring(dir.length() + 1), v);
                }
            });
        }
    }

    @Test
    void deletedInventoriesDoNotLeaveCacheEntries() throws Exception {
        CachedSfStore store = new CachedSfStore(new MemStore());
        for (int i = 0; i < 100; i++) {
            String p = "x-inventories/w/" + i + ".sfi";
            store.write(p, "v");
            store.delete(p);
            assertNull(store.read(p));
        }
        store.flush();
        for (int i = 0; i < 100; i++) {
            assertNull(store.read("x-inventories/w/" + i + ".sfi"));
        }
        assertEquals(0, store.stats().cacheEntries());
        store.close();
    }

    @Test
    void cacheIsBoundedAndStillReadsEverything() throws Exception {
        MemStore mem = new MemStore();
        CachedSfStore store = new CachedSfStore(mem);
        int n = CachedSfStore.MAX_CACHE + 3000;
        for (int i = 0; i < n; i++) {
            store.write("x-inventories/w/" + i + ".sfi", "v" + i);
        }
        store.flush();
        assertTrue(store.stats().cacheEntries() <= CachedSfStore.MAX_CACHE, "cache = " + store.stats().cacheEntries());
        for (int i = 0; i < n; i += 997) {
            assertEquals("v" + i, store.read("x-inventories/w/" + i + ".sfi"));
        }
        store.close();
    }

    @Test
    void statsCountReadsWritesAndHits() throws Exception {
        CachedSfStore store = new CachedSfStore(new MemStore());
        store.write("x-inventories/a.sfi", "abc");
        store.flush();
        store.read("x-inventories/a.sfi");
        store.read("x-inventories/a.sfi");
        CachedSfStore.Stats s = store.stats();
        assertEquals(1, s.writes());
        assertEquals(2, s.reads());
        assertEquals(2, s.cacheHits());
        assertEquals(0, s.queued());
        assertEquals(0, s.pending());
        assertEquals(1, s.cacheEntries());
        assertEquals(3, s.cacheChars());
        assertEquals(0, s.failures());
        store.close();
    }
}
