package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import org.mockbukkit.mockbukkit.MockBukkit;

class TestBarteringRandomization {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void testRandomizedDropsAreACopyWithSameContents() {
        Set<ItemStack> original = Slimefun.getRegistry().getBarteringDrops();
        original.clear();
        original.add(new ItemStack(Material.DIAMOND));
        original.add(new ItemStack(Material.EMERALD));
        original.add(new ItemStack(Material.GOLD_INGOT));

        List<ItemStack> shuffled = Slimefun.getRegistry().getRandomizedBarteringDrops();
        Assertions.assertEquals(original, new HashSet<>(shuffled));

        shuffled.clear();
        Assertions.assertEquals(3, original.size());
    }

    @Test
    void testOrderVaries() {
        Set<ItemStack> original = Slimefun.getRegistry().getBarteringDrops();
        original.clear();
        original.add(new ItemStack(Material.DIAMOND));
        original.add(new ItemStack(Material.EMERALD));
        original.add(new ItemStack(Material.GOLD_INGOT));

        Set<Material> firsts = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            firsts.add(Slimefun.getRegistry().getRandomizedBarteringDrops().get(0).getType());
        }

        Assertions.assertEquals(3, firsts.size());
        original.clear();
    }
}
