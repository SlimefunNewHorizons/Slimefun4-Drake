package com.github.drakescraft_labs.slimefun4.legacy.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestBlockInfoConfigNullSafety {

    @Test
    void testEmptyBlockDataNullSafety() {
        EmptyBlockData empty = new EmptyBlockData();
        Assertions.assertDoesNotThrow(() -> {
            Assertions.assertNotNull(empty.getKeys());
            Assertions.assertTrue(empty.getKeys().isEmpty());
            Assertions.assertNotNull(empty.getMap());
            Assertions.assertTrue(empty.getMap().isEmpty());
            Assertions.assertFalse(empty.contains("any-key"));
            Assertions.assertNull(empty.getString("any-key"));
            Assertions.assertEquals("{}", empty.toJSON());
        });
    }

    @Test
    void testBlockInfoConfigNullConstructor() {
        BlockInfoConfig config = new BlockInfoConfig(null);
        Assertions.assertDoesNotThrow(() -> {
            Assertions.assertNotNull(config.getKeys());
            Assertions.assertTrue(config.getKeys().isEmpty());
            Assertions.assertNotNull(config.getMap());
            Assertions.assertTrue(config.getMap().isEmpty());
            Assertions.assertFalse(config.contains("any-key"));
            Assertions.assertNull(config.getString("any-key"));
            Assertions.assertEquals("{}", config.toJSON());
        });
    }
}
