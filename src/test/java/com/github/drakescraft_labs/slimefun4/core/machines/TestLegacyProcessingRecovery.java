package com.github.drakescraft_labs.slimefun4.core.machines;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.items.electric.machines.enchanting.AutoEnchanter;
import com.github.drakescraft_labs.slimefun4.legacy.Objects.SlimefunItem.abstractItems.MachineRecipe;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;

/**
 * Máquinas legacy de addons (mapa {@code processing} en memoria): al apagar, lo que estaba en vuelo se entrega
 * a las ranuras de salida, una sola vez y sin perder ni duplicar nada.
 */
class TestLegacyProcessingRecovery {

    /** Máquina de prueba con el patrón heredado: mapa `processing` propio, fuera de MachineProcessor. */
    static class LegacyLikeMachine extends AutoEnchanter {
        public final Map<Block, MachineRecipe> processing = new HashMap<>();

        LegacyLikeMachine(String id) {
            super(new ItemGroup(org.bukkit.NamespacedKey.minecraft("legacy_" + id.toLowerCase()), new ItemStack(Material.BOOK)),
                new SlimefunItemStack(id, Material.ENCHANTING_TABLE, "&cLegacy " + id),
                RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] { new ItemStack(Material.DIRT) });
            setCapacity(100);
            setEnergyConsumption(10);
            setProcessingSpeed(1);
        }
    }

    private static ServerMock server;
    private static Slimefun plugin;
    private static WorldMock world;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("legacy-recovery");
        plugin = MockBukkit.load(Slimefun.class);
        BlockStorage.getOrCreate(world);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private LegacyLikeMachine placeMachine(String id, int x) {
        LegacyLikeMachine machine = new LegacyLikeMachine(id);
        machine.register(plugin);
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.ENCHANTING_TABLE);
        BlockStorage.store(block, machine.getId());
        return machine;
    }

    private static MachineRecipe recipe(Material input, Material output, int amount) {
        return new MachineRecipe(10, new ItemStack[] { new ItemStack(input) }, new ItemStack[] { new ItemStack(output, amount) });
    }

    @Test
    @DisplayName("La operación en vuelo se entrega a la salida al apagar y se elimina del mapa")
    void deliversInFlightOutputOnShutdown() {
        LegacyLikeMachine machine = placeMachine("TEST_LEGACY_A", 100);
        Block block = world.getBlockAt(100, 64, 0);
        machine.processing.put(block, recipe(Material.GOLD_INGOT, Material.GOLD_BLOCK, 3));

        int delivered = LegacyProcessingRecovery.completeAllActiveOperations();

        BlockMenu menu = BlockStorage.getInventory(block);
        int total = 0;
        for (int slot : machine.getOutputSlots()) {
            ItemStack stack = menu.getItemInSlot(slot);
            if (stack != null && stack.getType() == Material.GOLD_BLOCK) {
                total += stack.getAmount();
            }
        }
        Assertions.assertEquals(1, delivered);
        Assertions.assertEquals(3, total, "el resultado prometido debe llegar a las ranuras de salida");
        Assertions.assertTrue(machine.processing.isEmpty(), "la operación debe salir del mapa");
    }

    @Test
    @DisplayName("Llamarlo dos veces no duplica: la segunda pasada no entrega nada")
    void isIdempotent() {
        LegacyLikeMachine machine = placeMachine("TEST_LEGACY_B", 110);
        Block block = world.getBlockAt(110, 64, 0);
        machine.processing.put(block, recipe(Material.IRON_INGOT, Material.IRON_BLOCK, 2));

        Assertions.assertEquals(1, LegacyProcessingRecovery.completeAllActiveOperations());
        Assertions.assertEquals(0, LegacyProcessingRecovery.completeAllActiveOperations());

        int total = 0;
        BlockMenu menu = BlockStorage.getInventory(block);
        for (int slot : machine.getOutputSlots()) {
            ItemStack stack = menu.getItemInSlot(slot);
            if (stack != null && stack.getType() == Material.IRON_BLOCK) {
                total += stack.getAmount();
            }
        }
        Assertions.assertEquals(2, total, "no debe duplicarse en una segunda llamada");
    }

    @Test
    @DisplayName("Entrada obsoleta (bloque ya no es la máquina): se descarta sin entregar nada")
    void discardsStaleEntries() {
        LegacyLikeMachine machine = placeMachine("TEST_LEGACY_C", 120);
        Block ghost = world.getBlockAt(125, 64, 0);          // aquí nunca hubo máquina
        machine.processing.put(ghost, recipe(Material.COAL, Material.DIAMOND, 5));

        int delivered = LegacyProcessingRecovery.completeAllActiveOperations();

        Assertions.assertEquals(0, delivered);
        Assertions.assertTrue(machine.processing.isEmpty(), "la entrada obsoleta se limpia");
        Assertions.assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
            .noneMatch(i -> i.getItemStack().getType() == Material.DIAMOND), "no se debe regalar nada por una entrada fantasma");
    }

    @Test
    @DisplayName("Si la salida está llena, lo que no cabe se suelta en el mundo y no se pierde")
    void overflowIsDroppedNotLost() {
        LegacyLikeMachine machine = placeMachine("TEST_LEGACY_D", 130);
        Block block = world.getBlockAt(130, 64, 0);
        BlockMenu menu = BlockStorage.getInventory(block);
        for (int slot : machine.getOutputSlots()) {
            menu.replaceExistingItem(slot, new ItemStack(Material.DIRT, 64));     // salida ocupada por otra cosa
        }
        machine.processing.put(block, recipe(Material.EMERALD, Material.EMERALD_BLOCK, 4));

        Assertions.assertEquals(1, LegacyProcessingRecovery.completeAllActiveOperations());

        int dropped = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
            .filter(i -> i.getItemStack().getType() == Material.EMERALD_BLOCK)
            .mapToInt(i -> i.getItemStack().getAmount()).sum();
        Assertions.assertEquals(4, dropped, "el resultado completo debe aparecer en el mundo");
    }
}
