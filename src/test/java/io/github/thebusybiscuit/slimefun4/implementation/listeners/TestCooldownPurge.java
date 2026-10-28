package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class TestCooldownPurge {

    @Test
    void purgeOnlyKicksInWhenLargeAndDropsExpiredEntries() {
        Map<UUID, Long> m = new ConcurrentHashMap<>();
        long now = 100_000L;
        for (int i = 0; i < 50; i++) {
            m.put(UUID.randomUUID(), 0L);
        }
        BlockListener.purgeStale(m, now);
        assertEquals(50, m.size()); // pequeño: no se toca

        for (int i = 0; i < 100; i++) {
            m.put(UUID.randomUUID(), 0L);
        }
        UUID fresh = UUID.randomUUID();
        m.put(fresh, now - 100);
        BlockListener.purgeStale(m, now);
        assertEquals(Map.of(fresh, now - 100), m);
    }
}
