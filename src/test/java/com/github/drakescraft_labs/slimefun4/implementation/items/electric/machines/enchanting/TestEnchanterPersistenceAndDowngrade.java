package com.github.drakescraft_labs.slimefun4.implementation.items.electric.machines.enchanting;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
import com.github.drakescraft_labs.slimefun4.core.machines.MachineProcessor;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.operations.CraftingOperation;
import com.github.drakescraft_labs.slimefun4.legacy.Objects.SlimefunItem.abstractItems.MachineRecipe;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;

class TestEnchanterPersistenceAndDowngrade {

    private static ServerMock server;
    private static Slimefun plugin;
    private static WorldMock world;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
        plugin = MockBukkit.load(Slimefun.class);
        BlockStorage.getOrCreate(world);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Ticket #55: AutoEnchanter must NEVER downgrade an existing enchantment (e.g. Sharpness 50 -> 20)")
    void testAutoEnchanterNeverDowngrades() {
        AutoEnchanter enchanter = new AutoEnchanter(
            new ItemGroup(org.bukkit.NamespacedKey.minecraft("test_enchant_group"), new ItemStack(Material.BOOK)),
            new SlimefunItemStack("TEST_AUTO_ENCHANTER", Material.ENCHANTING_TABLE, "&cTest Auto Enchanter"),
            RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] { new ItemStack(Material.DIRT) }
        );
        enchanter.setCapacity(100);
        enchanter.setEnergyConsumption(10);
        enchanter.setProcessingSpeed(1);
        enchanter.register(plugin);

        Block block = world.getBlockAt(10, 64, 10);
        block.setType(Material.ENCHANTING_TABLE);
        BlockStorage.store(block, enchanter.getId());

        BlockMenu menu = BlockStorage.getInventory(block);
        Assertions.assertNotNull(menu, "BlockMenu should not be null for registered machine");

        // Target: Sword with Sharpness 50
        ItemStack targetSword = new ItemStack(Material.DIAMOND_SWORD);
        targetSword.addUnsafeEnchantment(Enchantment.SHARPNESS, 50);

        // Book: Lower level Sharpness 20
        ItemStack lowerBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) lowerBook.getItemMeta();
        meta.addStoredEnchant(Enchantment.SHARPNESS, 20, true);
        lowerBook.setItemMeta(meta);

        // Place in input slots (19 and 20)
        menu.replaceExistingItem(19, targetSword);
        menu.replaceExistingItem(20, lowerBook);

        // findNextRecipe calls AutoEnchantEvent (which is async), so it must be executed asynchronously
        MachineRecipe recipe = java.util.concurrent.CompletableFuture.supplyAsync(() -> enchanter.findNextRecipe(menu)).join();

        // Since the only enchant on the book is lower than the sword, it MUST NOT accept it as a valid recipe
        Assertions.assertNull(recipe, "AutoEnchanter must reject recipe that would downgrade an enchantment from level 50 to 20");
    }

    @Test
    @DisplayName("Ticket #55: AutoEnchanter allows upgrades when book level is strictly higher")
    void testAutoEnchanterAllowsUpgrades() {
        AutoEnchanter enchanter = new AutoEnchanter(
            new ItemGroup(org.bukkit.NamespacedKey.minecraft("test_upgrade_group"), new ItemStack(Material.BOOK)),
            new SlimefunItemStack("TEST_UPGRADE_ENCHANTER", Material.ENCHANTING_TABLE, "&cTest Upgrade Enchanter"),
            RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] { new ItemStack(Material.DIRT) }
        );
        enchanter.setCapacity(100);
        enchanter.setEnergyConsumption(10);
        enchanter.setProcessingSpeed(1);
        enchanter.register(plugin);

        Block block = world.getBlockAt(20, 64, 20);
        block.setType(Material.ENCHANTING_TABLE);
        BlockStorage.store(block, enchanter.getId());

        BlockMenu menu = BlockStorage.getInventory(block);
        Assertions.assertNotNull(menu);

        // Target: Sword with Sharpness 20
        ItemStack targetSword = new ItemStack(Material.DIAMOND_SWORD);
        targetSword.addUnsafeEnchantment(Enchantment.SHARPNESS, 20);

        // Book: Higher level Sharpness 50
        ItemStack higherBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) higherBook.getItemMeta();
        meta.addStoredEnchant(Enchantment.SHARPNESS, 50, true);
        higherBook.setItemMeta(meta);

        menu.replaceExistingItem(19, targetSword);
        menu.replaceExistingItem(20, higherBook);

        MachineRecipe recipe = java.util.concurrent.CompletableFuture.supplyAsync(() -> enchanter.findNextRecipe(menu)).join();
        Assertions.assertNotNull(recipe, "AutoEnchanter should allow valid upgrade from Sharpness 20 to Sharpness 50");
        Assertions.assertEquals(50, recipe.getOutput()[0].getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    @DisplayName("Ticket #55: MachineProcessor refunds in-flight items to BlockMenu on shutdown")
    void testMachineProcessorRefundOnShutdown() {
        AutoEnchanter enchanter = new AutoEnchanter(
            new ItemGroup(org.bukkit.NamespacedKey.minecraft("test_persist_group"), new ItemStack(Material.BOOK)),
            new SlimefunItemStack("TEST_PERSIST_ENCHANTER", Material.ENCHANTING_TABLE, "&cTest Persist Enchanter"),
            RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] { new ItemStack(Material.DIRT) }
        );
        enchanter.setCapacity(100);
        enchanter.setEnergyConsumption(10);
        enchanter.setProcessingSpeed(1);
        enchanter.register(plugin);

        Block block = world.getBlockAt(30, 64, 30);
        block.setType(Material.ENCHANTING_TABLE);
        BlockStorage.store(block, enchanter.getId());

        BlockMenu menu = BlockStorage.getInventory(block);
        Assertions.assertNotNull(menu);

        // Simulate an item being processed mid-craft
        ItemStack valuableSword = new ItemStack(Material.NETHERITE_SWORD);
        valuableSword.addUnsafeEnchantment(Enchantment.SHARPNESS, 50);

        ItemStack valuableBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) valuableBook.getItemMeta();
        meta.addStoredEnchant(Enchantment.UNBREAKING, 3, true);
        valuableBook.setItemMeta(meta);

        // Machine consumed the items: input slots are currently empty
        menu.replaceExistingItem(19, null);
        menu.replaceExistingItem(20, null);

        MachineProcessor<CraftingOperation> processor = enchanter.getMachineProcessor();
        CraftingOperation operation = new CraftingOperation(
            new ItemStack[] { valuableSword, valuableBook },
            new ItemStack[] { valuableSword.clone(), new ItemStack(Material.BOOK) },
            100
        );
        processor.startOperation(block, operation);

        Assertions.assertNotNull(processor.getOperation(block), "Operation should be active in processor");

        // Trigger shutdown refunding
        MachineProcessor.refundAllActiveProcessors();

        // The items MUST be refunded back into the BlockMenu's input slots
        ItemStack restored1 = menu.getItemInSlot(19);
        ItemStack restored2 = menu.getItemInSlot(20);

        Assertions.assertTrue(
            (restored1 != null && restored1.getType() == Material.NETHERITE_SWORD) ||
            (restored2 != null && restored2.getType() == Material.NETHERITE_SWORD),
            "Valuable sword must be refunded to BlockMenu on shutdown"
        );
        Assertions.assertTrue(
            (restored1 != null && restored1.getType() == Material.ENCHANTED_BOOK) ||
            (restored2 != null && restored2.getType() == Material.ENCHANTED_BOOK),
            "Valuable book must be refunded to BlockMenu on shutdown"
        );
    }

    @Test
    @DisplayName("Ticket #55: machines that keep their ingredients in the input slots are never refunded (no dupes)")
    void testNoRefundWhenIngredientsAreNotConsumed() {
        // Mirrors GCE PrivateCoop: the recipe lookup leaves the ingredients in the input slots
        AutoEnchanter keepsInputs = new AutoEnchanter(
            new ItemGroup(org.bukkit.NamespacedKey.minecraft("test_keep_group"), new ItemStack(Material.BOOK)),
            new SlimefunItemStack("TEST_KEEP_INPUTS_MACHINE", Material.ENCHANTING_TABLE, "&cTest Keep Inputs"),
            RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] { new ItemStack(Material.DIRT) }
        ) {
            @Override
            protected MachineRecipe findNextRecipe(BlockMenu menu) {
                return null;
            }
        };
        keepsInputs.setCapacity(100);
        keepsInputs.setEnergyConsumption(10);
        keepsInputs.setProcessingSpeed(1);
        keepsInputs.register(plugin);

        Assertions.assertFalse(keepsInputs.canRefundInFlightOperation());

        Block block = world.getBlockAt(40, 64, 40);
        block.setType(Material.ENCHANTING_TABLE);
        BlockStorage.store(block, keepsInputs.getId());

        BlockMenu menu = BlockStorage.getInventory(block);
        Assertions.assertNotNull(menu);

        ItemStack parent = new ItemStack(Material.EGG);
        menu.replaceExistingItem(19, parent.clone());

        MachineProcessor<CraftingOperation> processor = keepsInputs.getMachineProcessor();
        processor.startOperation(block, new CraftingOperation(new ItemStack[] { parent }, new ItemStack[] { new ItemStack(Material.CHICKEN_SPAWN_EGG) }, 100));

        // Aborting mid-craft must not hand out a copy of the ingredient
        processor.endOperation(block);
        Assertions.assertEquals(1, menu.getItemInSlot(19).getAmount());
        Assertions.assertNull(menu.getItemInSlot(20));

        // Neither may the shutdown refund
        processor.startOperation(block, new CraftingOperation(new ItemStack[] { parent }, new ItemStack[] { new ItemStack(Material.CHICKEN_SPAWN_EGG) }, 100));
        MachineProcessor.refundAllActiveProcessors();
        Assertions.assertEquals(1, menu.getItemInSlot(19).getAmount());
        Assertions.assertNull(menu.getItemInSlot(20));
    }
}
