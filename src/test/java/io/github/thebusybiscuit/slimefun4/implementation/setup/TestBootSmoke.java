package io.github.thebusybiscuit.slimefun4.implementation.setup;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import org.mockbukkit.mockbukkit.MockBukkit;

/** Arranque de humo: carga el core y registra TODOS los objetos y las investigaciones sin excepción. */
class TestBootSmoke {

    private static Slimefun plugin;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("SlimefunItemSetup registra todos los objetos del core sin excepción")
    void registersAllItems() {
        Assertions.assertDoesNotThrow(() -> SlimefunItemSetup.setup(plugin));
        Assertions.assertDoesNotThrow(ResearchSetup::setupResearches);

        int count = Slimefun.getRegistry().getAllSlimefunItems().size();
        Assertions.assertTrue(count > 500, "Se esperaban >500 objetos registrados, hay " + count);

        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            Assertions.assertNotNull(item.getId());
            Assertions.assertNotNull(item.getItem(), item.getId());
            Assertions.assertNotNull(item.getItemGroup(), item.getId());
            Assertions.assertSame(plugin, item.getAddon(), item.getId());
        }
    }
}
