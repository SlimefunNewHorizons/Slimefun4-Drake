package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.PerfFlags;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/** Shift-click hacia maquinas Slimefun: solo ranuras de entrada declaradas, el resto se queda en el jugador. */
class TestShiftClickInput {

    static class Probe extends AContainer {
        Probe(String id) {
            super(new ItemGroup(org.bukkit.NamespacedKey.minecraft("shift_" + id.toLowerCase()), new ItemStack(Material.DIRT)),
                new SlimefunItemStack(id, Material.FURNACE, "&cProbe " + id), RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] { new ItemStack(Material.DIRT) });
            setCapacity(100);
            setEnergyConsumption(10);
            setProcessingSpeed(1);
        }

        @Override
        public ItemStack getProgressBar() {
            return new ItemStack(Material.FLINT_AND_STEEL);
        }

        @Override
        public String getMachineIdentifier() {
            return "SHIFT_PROBE";
        }
    }

    private static ServerMock server;
    private static Slimefun plugin;
    private static WorldMock world;
    private static int n = 0;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("shift-click");
        plugin = MockBukkit.load(Slimefun.class);
        new me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.MenuListener(plugin); // en tests no se registra solo
        BlockStorage.getOrCreate(world);
    }

    @AfterAll
    static void unload() {
        PerfFlags.shiftClickInput = true;
        MockBukkit.unmock();
    }

    private record Setup(Probe machine, BlockMenu menu, PlayerMock player) {}

    private Setup open() {
        Probe m = new Probe("SHIFT_" + n);
        m.register(plugin);
        Block b = world.getBlockAt(n++, 64, 0);
        b.setType(Material.FURNACE);
        BlockStorage.store(b, m.getId());
        BlockMenu menu = BlockStorage.getInventory(b);
        PlayerMock p = server.addPlayer();
        menu.open(p);
        return new Setup(m, menu, p);
    }

    /** Shift-click sobre el item del jugador; el rawSlot se localiza buscando el que la vista asocia a ese stack. */
    private InventoryClickEvent shift(Setup s, ItemStack inPlayerSlot9) {
        s.player.getInventory().setItem(9, inPlayerSlot9);
        int top = s.player.getOpenInventory().getTopInventory().getSize();
        InventoryClickEvent e = null;
        for (int raw = top; raw < top + 36; raw++) {
            InventoryClickEvent probe = new InventoryClickEvent(s.player.getOpenInventory(), SlotType.CONTAINER, raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            if (probe.getCurrentItem() != null) {
                e = probe;
                break;
            }
        }
        Assertions.assertNotNull(e, "la vista expone el stack del jugador");
        server.getPluginManager().callEvent(e);
        return e;
    }

    private static int held(PlayerMock p) {
        int t = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            t += i != null && i.getType() == Material.COAL ? i.getAmount() : 0;
        }
        return t;
    }

    private static int count(BlockMenu menu, int[] slots) {
        int t = 0;
        for (int slot : slots) {
            ItemStack i = menu.getItemInSlot(slot);
            t += i == null ? 0 : i.getAmount();
        }
        return t;
    }

    @Test
    @DisplayName("Va a las ranuras de entrada y no a salida ni decoracion")
    void goesToInputsOnly() {
        Setup s = open();
        InventoryClickEvent e = shift(s, new ItemStack(Material.COAL, 40));

        Assertions.assertTrue(e.isCancelled(), "vanilla no debe repartir el stack");
        Assertions.assertEquals(40, count(s.menu, s.machine.getInputSlots()));
        Assertions.assertEquals(0, count(s.menu, s.machine.getOutputSlots()));
        Assertions.assertEquals(0, held(s.player), "el jugador ya no conserva nada");
        int total = 0;
        for (int i = 0; i < s.menu.toInventory().getSize(); i++) {
            ItemStack it = s.menu.getItemInSlot(i);
            if (it != null && it.getType() == Material.COAL) total += it.getAmount();
        }
        Assertions.assertEquals(40, total, "ni un item fuera de las entradas, ni duplicado");
    }

    @Test
    @DisplayName("Lo que no cabe se queda en el inventario del jugador, sin duplicar")
    void overflowStaysWithPlayer() {
        Setup s = open();
        int[] in = s.machine.getInputSlots();
        for (int slot : in) s.menu.replaceExistingItem(slot, new ItemStack(Material.COAL, 60));

        shift(s, new ItemStack(Material.COAL, 64));

        int cap = in.length * 64;
        Assertions.assertEquals(cap, count(s.menu, in));
        int left = held(s.player);
        Assertions.assertEquals(in.length * 60 + 64 - cap, left);
        Assertions.assertEquals(in.length * 60 + 64, count(s.menu, in) + left, "conservacion de items");
    }

    @Test
    @DisplayName("Con el flag apagado no interviene (vanilla)")
    void flagOffLeavesVanilla() {
        Setup s = open();
        PerfFlags.shiftClickInput = false;
        try {
            InventoryClickEvent e = shift(s, new ItemStack(Material.COAL, 10));
            Assertions.assertFalse(e.isCancelled());
            Assertions.assertEquals(10, held(s.player));
        } finally {
            PerfFlags.shiftClickInput = true;
        }
    }
}
