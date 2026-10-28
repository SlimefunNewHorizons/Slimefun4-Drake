package io.github.thebusybiscuit.slimefun4.implementation.items.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import io.github.bakedlibs.dough.protection.ProtectionManager;

import io.github.thebusybiscuit.slimefun4.api.exceptions.TagMisconfigurationException;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.test.TestUtilities;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TestPickaxeOfVeinMining {

    private static ServerMock server;
    private static Slimefun plugin;

    @BeforeAll
    public static void load() throws TagMisconfigurationException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        SlimefunTag.reloadAll();
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private Block mockBlock(World mockWorld, Location loc) {
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getLocation()).thenReturn(loc);
        Mockito.when(block.getWorld()).thenReturn(mockWorld);
        Mockito.when(block.getType()).thenReturn(Material.COAL_ORE);
        Mockito.when(block.getDrops(ArgumentMatchers.any(ItemStack.class))).thenReturn(new ArrayList<>());
        return block;
    }

    @Test
    void testVeinMiningSkipsSlimefunBlocks() throws ReflectiveOperationException {
        var field = Slimefun.getIntegrations().getClass().getDeclaredField("protectionManager");
        field.setAccessible(true);
        field.set(Slimefun.getIntegrations(), new ProtectionManager(plugin));

        PlayerMock player = server.addPlayer();
        World world = TestUtilities.createWorld(server);
        World mockWorld = Mockito.mock(World.class);

        PickaxeOfVeinMining pick = new PickaxeOfVeinMining(TestUtilities.getItemGroup(plugin, "vein_pick"), new SlimefunItemStack("TEST_VEIN_PICK", Material.IRON_PICKAXE, "&5Vein"), RecipeType.NULL, new ItemStack[9]);
        pick.register(plugin);
        TestUtilities.mockSlimefunItem(plugin, "VEIN_PROTECTED_BLOCK", new SlimefunItemStack("VEIN_PROTECTED_BLOCK", Material.COAL_ORE, "&4Machine")).register(plugin);

        Location vanillaLoc = new Location(world, 1, 64, 0);
        Location sfLoc = new Location(world, 2, 64, 0);
        BlockStorage.addBlockInfo(sfLoc, "id", "VEIN_PROTECTED_BLOCK");
        Block vanilla = mockBlock(mockWorld, vanillaLoc);
        Block machine = mockBlock(mockWorld, sfLoc);

        Method m = PickaxeOfVeinMining.class.getDeclaredMethod("breakBlocks", Player.class, List.class, int.class, ItemStack.class);
        m.setAccessible(true);
        m.invoke(pick, player, List.of(vanilla, machine), 0, new ItemStack(Material.IRON_PICKAXE));

        Mockito.verify(vanilla).setType(Material.AIR);
        Mockito.verify(machine, Mockito.never()).setType(ArgumentMatchers.any(Material.class));
    }
}
