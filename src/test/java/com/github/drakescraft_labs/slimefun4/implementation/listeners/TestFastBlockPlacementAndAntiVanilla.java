package com.github.drakescraft_labs.slimefun4.implementation.listeners;

import java.io.File;
import java.io.IOException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.block.state.BlockStateMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.core.attributes.NotPlaceable;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.test.TestUtilities;
import com.github.drakescraft_labs.slimefun4.utils.FileUtils;

class TestFastBlockPlacementAndAntiVanilla {

    private static ServerMock server;
    private static Slimefun plugin;
    private static SlimefunItem virtualFarmItem;
    private static SlimefunItem unplaceableItem;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);

        new BlockListener(plugin);

        virtualFarmItem = TestUtilities.mockSlimefunItem(
            plugin,
            "TEST_VIRTUAL_FARM",
            new ItemStack(Material.WARPED_NYLIUM)
        );
        virtualFarmItem.register(plugin);

        unplaceableItem = new MockUnplaceableItem(
            new ItemGroup(new NamespacedKey(plugin, "test_group"), new ItemStack(Material.BOOK)),
            new SlimefunItemStack("TEST_UNPLACEABLE", Material.DISPENSER, "&cUnplaceable Dispenser"),
            RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[9]
        );
        unplaceableItem.register(plugin);
    }

    @AfterAll
    public static void unload() throws IOException {
        MockBukkit.unmock();
        FileUtils.deleteDirectory(new File("data-storage"));
    }

    @BeforeEach
    public void beforeEach() {
        server.getPluginManager().clearEvents();
    }

    @Test
    @DisplayName("Doble paquete en la misma coordenada no debe purgar metadatos de Slimefun")
    void testFastPlacementDuplicatePacketDoesNotEraseMetadata() {
        PlayerMock player = new PlayerMock(server, "PasienteMock");
        ItemStack farmStack = virtualFarmItem.getItem().clone();
        player.getInventory().setItemInMainHand(farmStack);

        World world = server.addSimpleWorld("fast_place_world");
        BlockStorage.getOrCreate(world);

        Location loc = new Location(world, 100, 64, 100);
        Block block = new BlockMock(Material.WARPED_NYLIUM, loc);
        Block blockAgainst = new BlockMock(Material.STONE, new Location(world, 100, 63, 100));
        BlockState replacedAirState = new BlockStateMock(Material.AIR);

        // Evento 1: Colocación inicial del bloque Slimefun
        BlockPlaceEvent event1 = new BlockPlaceEvent(
            block,
            replacedAirState,
            blockAgainst,
            farmStack,
            player,
            true,
            EquipmentSlot.HAND
        );

        server.getPluginManager().callEvent(event1);

        Assertions.assertFalse(event1.isCancelled(), "El evento 1 debe ser permitido");
        Assertions.assertTrue(BlockStorage.hasBlockInfo(block), "El bloque debe estar registrado en BlockStorage tras el evento 1");
        Assertions.assertEquals("TEST_VIRTUAL_FARM", BlockStorage.checkID(block), "El ID debe ser TEST_VIRTUAL_FARM");

        // Evento 2: Paquete duplicado en ráfaga rápida sobre el mismo bloque ya colocado
        ItemStack offHandEmpty = new ItemStack(Material.AIR);
        BlockState replacedPlacedState = new BlockStateMock(Material.WARPED_NYLIUM);
        BlockPlaceEvent event2 = new BlockPlaceEvent(
            block,
            replacedPlacedState,
            blockAgainst,
            offHandEmpty,
            player,
            true,
            EquipmentSlot.OFF_HAND
        );

        server.getPluginManager().callEvent(event2);

        Assertions.assertTrue(event2.isCancelled(), "El evento 2 duplicado debe ser cancelado");
        Assertions.assertTrue(BlockStorage.hasBlockInfo(block), "El bloque NO debe ser purgado de BlockStorage por el evento 2");
        Assertions.assertEquals("TEST_VIRTUAL_FARM", BlockStorage.checkID(block), "El ID debe seguir intacto tras la ráfaga");
    }

    @Test
    @DisplayName("Escudo Anti-Vanilla cancela colocación de ítem Slimefun no resuelto")
    void testAntiVanillaShieldBlocksUnresolvedSlimefunItem() {
        PlayerMock player = new PlayerMock(server, "PlayerShield1");

        // Crear un ItemStack con PDC de Slimefun pero un ID inexistente
        ItemStack fakeSfItem = new ItemStack(Material.WARPED_NYLIUM);
        ItemMeta meta = fakeSfItem.getItemMeta();
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "slimefun_item"),
            PersistentDataType.STRING,
            "CORRUPTED_OR_UNKNOWN_ID"
        );
        fakeSfItem.setItemMeta(meta);

        player.getInventory().setItemInMainHand(fakeSfItem);

        World world = server.addSimpleWorld("shield_world_1");
        BlockStorage.getOrCreate(world);

        Location loc = new Location(world, 200, 64, 200);
        Block block = new BlockMock(Material.WARPED_NYLIUM, loc);
        Block blockAgainst = new BlockMock(Material.STONE, new Location(world, 200, 63, 200));

        BlockPlaceEvent event = new BlockPlaceEvent(
            block,
            new BlockStateMock(Material.AIR),
            blockAgainst,
            fakeSfItem,
            player,
            true,
            EquipmentSlot.HAND
        );

        server.getPluginManager().callEvent(event);

        Assertions.assertTrue(event.isCancelled(), "El escudo anti-vanilla debe cancelar la colocación para evitar que se haga vanilla");
        Assertions.assertFalse(BlockStorage.hasBlockInfo(block), "No debe registrarse en BlockStorage");
        String warning = player.nextMessage();
        Assertions.assertNotNull(warning, "El jugador debe recibir un diagnóstico para el ítem no resuelto");
        Assertions.assertTrue(warning.contains("metadata that is no longer recognised"));
        Assertions.assertTrue(warning.contains("This is not a speed block"));
        Assertions.assertFalse(warning.contains("placing blocks too fast"));
    }

    @Test
    @DisplayName("NotPlaceable omite el registro de Slimefun sin cancelar listeners de addons")
    void testNotPlaceableSkipsSlimefunRegistrationWithoutCancellingEvent() {
        PlayerMock player = new PlayerMock(server, "PlayerShield2");
        ItemStack unplaceableStack = unplaceableItem.getItem().clone();
        player.getInventory().setItemInMainHand(unplaceableStack);

        World world = server.addSimpleWorld("shield_world_2");
        BlockStorage.getOrCreate(world);

        Location loc = new Location(world, 300, 64, 300);
        Block block = new BlockMock(Material.DISPENSER, loc);
        Block blockAgainst = new BlockMock(Material.STONE, new Location(world, 300, 63, 300));

        BlockPlaceEvent event = new BlockPlaceEvent(
            block,
            new BlockStateMock(Material.AIR),
            blockAgainst,
            unplaceableStack,
            player,
            true,
            EquipmentSlot.HAND
        );

        server.getPluginManager().callEvent(event);

        Assertions.assertFalse(event.isCancelled(), "NotPlaceable no debe cancelar BlockPlaceEvent");
        Assertions.assertFalse(BlockStorage.hasBlockInfo(block), "Un ítem NotPlaceable no debe registrarse en BlockStorage");
    }

    @Test
    @DisplayName("Metadata huérfana sobre aire no bloquea colocación ni dispara falsa alarma de FastPlace")
    void testOrphanSlimefunMetadataOnAirDoesNotTriggerFastPlace() {
        PlayerMock player = new PlayerMock(server, "PlayerChestPlacer");
        ItemStack chestItem = new ItemStack(Material.CHEST);
        player.getInventory().setItemInMainHand(chestItem);

        World world = server.addSimpleWorld("chest_world");
        BlockStorage.getOrCreate(world);

        Location loc = new Location(world, 400, 64, 400);
        Block block = new BlockMock(Material.CHEST, loc);
        Block blockAgainst = new BlockMock(Material.STONE, new Location(world, 400, 63, 400));

        // Simular metadata huérfana previa en la ubicación donde ahora hay aire
        BlockStorage.addBlockInfo(loc, "id", "TEST_VIRTUAL_FARM");

        BlockPlaceEvent event = new BlockPlaceEvent(
            block,
            new BlockStateMock(Material.AIR),
            blockAgainst,
            chestItem,
            player,
            true,
            EquipmentSlot.HAND
        );

        server.getPluginManager().callEvent(event);

        Assertions.assertFalse(event.isCancelled(), "La colocación no debe cancelarse por FastPlace falso");
        Assertions.assertNull(player.nextMessage(), "No debe enviarse advertencia de colocar bloques muy rápido");
        Assertions.assertTrue(Slimefun.getTickerTask().isDeletedSoon(loc), "La metadata huérfana debe estar encolada para purga");
    }

    @Test
    @DisplayName("Metadata residual de ID desconocido o nulo no cancela colocación en bloques reemplazables como césped")
    void testUnknownOrphanMetadataOnGrassDoesNotCancelPlacement() {
        PlayerMock player = new PlayerMock(server, "PlayerGrassPlacer");
        ItemStack chestItem = new ItemStack(Material.CHEST);
        player.getInventory().setItemInMainHand(chestItem);

        World world = server.addSimpleWorld("grass_world");
        BlockStorage.getOrCreate(world);

        Location loc = new Location(world, 500, 64, 500);
        Block block = new BlockMock(Material.CHEST, loc);
        Block blockAgainst = new BlockMock(Material.DIRT, new Location(world, 500, 63, 500));

        // Simular metadata huérfana de un plugin desinstalado o ID corrupto sobre césped
        BlockStorage.addBlockInfo(loc, "id", "DELETED_OR_UNKNOWN_ADDON_ITEM");

        BlockPlaceEvent event = new BlockPlaceEvent(
            block,
            new BlockStateMock(Material.SHORT_GRASS),
            blockAgainst,
            chestItem,
            player,
            true,
            EquipmentSlot.HAND
        );

        server.getPluginManager().callEvent(event);

        Assertions.assertFalse(event.isCancelled(), "La colocación no debe cancelarse sobre césped con metadata residual");
        Assertions.assertTrue(Slimefun.getTickerTask().isDeletedSoon(loc), "La metadata huérfana debe purgarse");
    }

    private static class MockUnplaceableItem extends SlimefunItem implements NotPlaceable {
        public MockUnplaceableItem(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
            super(itemGroup, item, recipeType, recipe);
        }
    }
}
