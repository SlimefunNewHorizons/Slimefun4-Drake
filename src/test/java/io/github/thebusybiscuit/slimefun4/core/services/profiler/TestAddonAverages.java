package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import org.mockbukkit.mockbukkit.MockBukkit;

class TestAddonAverages {

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void movingAverageConvergesAndInactiveAddonsDecayAway() {
        SlimefunProfiler p = Slimefun.getProfiler();
        p.updateAddonAverages(Map.of("A", 1_000_000L, "B", 5_000L));
        assertEquals(1_000_000.0, p.getAddonAverages().get("A"));

        p.updateAddonAverages(Map.of("A", 2_000_000L, "B", 5_000L));
        assertEquals(1_200_000.0, p.getAddonAverages().get("A"), 0.001);

        for (int i = 0; i < 100; i++) {
            p.updateAddonAverages(Map.of("A", 2_000_000L)); // B ya no tickea
        }
        assertFalse(p.getAddonAverages().containsKey("B"));
        assertTrue(p.getAddonAverages().get("A") > 1_900_000);
        for (int i = 0; i < 100; i++) {
            p.updateAddonAverages(Map.of());
        }
        assertTrue(p.getAddonAverages().isEmpty());
    }
}
