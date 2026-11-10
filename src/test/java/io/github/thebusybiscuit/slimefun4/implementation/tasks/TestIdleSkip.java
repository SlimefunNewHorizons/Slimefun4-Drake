package io.github.thebusybiscuit.slimefun4.implementation.tasks;

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

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.PerfFlags;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * slimefun.idle-skip: equivalencia con la ruta original y microbenchmark de findNextRecipe en una maquina ociosa.
 * Lanzar el benchmark con -Dsf.bench=true (imprime ns/llamada); sin eso solo corre la equivalencia.
 */
class TestIdleSkip {

    static class Probe extends AContainer {
        Probe(String id, int recipeCount) {
            super(new ItemGroup(org.bukkit.NamespacedKey.minecraft("idle_" + id.toLowerCase()), new ItemStack(Material.DIRT)),
                new SlimefunItemStack(id, Material.FURNACE, "&cProbe " + id), RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] { new ItemStack(Material.DIRT) });
            setCapacity(100);
            setEnergyConsumption(10);
            setProcessingSpeed(1);
            Material[] mats = Material.values();
            int made = 0;
            for (Material m : mats) {
                if (made == recipeCount) break;
                if (!m.isItem() || m.isAir() || m == Material.COBBLESTONE) continue;
                registerRecipe(2, new ItemStack[] { new ItemStack(m) }, new ItemStack[] { new ItemStack(Material.COBBLESTONE) });
                made++;
            }
            // receta de dos entradas para ejercitar el caso "found.size() == length"
            registerRecipe(2, new ItemStack[] { new ItemStack(Material.COBBLESTONE), new ItemStack(Material.SAND) },
                new ItemStack[] { new ItemStack(Material.GLASS) });
        }

        @Override
        public ItemStack getProgressBar() {
            return new ItemStack(Material.FLINT_AND_STEEL);
        }

        @Override
        public String getMachineIdentifier() {
            return "IDLE_PROBE";
        }

        MachineRecipe find(BlockMenu menu) {
            return findNextRecipe(menu);
        }
    }

    private static ServerMock server;
    private static Slimefun plugin;
    private static WorldMock world;
    private static int nextX = 0;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("idle-skip");
        plugin = MockBukkit.load(Slimefun.class);
        BlockStorage.getOrCreate(world);
    }

    @AfterAll
    static void unload() {
        PerfFlags.idleSkip = false;
        MockBukkit.unmock();
    }

    private static BlockMenu place(Probe m) {
        m.register(plugin);
        Block b = world.getBlockAt(nextX++, 64, 0);
        b.setType(Material.FURNACE);
        BlockStorage.store(b, m.getId());
        return BlockStorage.getInventory(b);
    }

    private static String run(Probe m, boolean flag, ItemStack[] inputs, ItemStack[] outputsFill) {
        PerfFlags.idleSkip = flag;
        BlockMenu menu = place(m);
        int[] in = m.getInputSlots();
        for (int i = 0; i < inputs.length; i++) menu.replaceExistingItem(in[i], inputs[i]);
        for (int s : m.getOutputSlots()) if (outputsFill != null) menu.replaceExistingItem(s, outputsFill[0]);
        MachineRecipe r = m.find(menu);
        StringBuilder sb = new StringBuilder(r == null ? "null" : r.getOutput()[0].getType() + "x" + r.getOutput()[0].getAmount());
        for (int s : in) sb.append('|').append(menu.getItemInSlot(s));
        return sb.toString();
    }

    @Test
    @DisplayName("Con y sin idle-skip la busqueda de receta devuelve lo mismo y consume lo mismo")
    void sameResult() {
        ItemStack[][] cases = {
            {},                                                                   // ociosa
            { new ItemStack(Material.DIAMOND_SWORD) },                            // entrada sin receta
            { new ItemStack(Material.OAK_LOG, 3) },                               // receta de una entrada
            { new ItemStack(Material.COBBLESTONE, 2), new ItemStack(Material.SAND, 2) }, // dos entradas
        };
        int n = 0;
        for (ItemStack[] c : cases) {
            String off = run(new Probe("IDLE_A" + n, 20), false, clone(c), null);
            String on = run(new Probe("IDLE_B" + n, 20), true, clone(c), null);
            Assertions.assertEquals(off, on, "caso " + n);
            n++;
        }
        // salida llena: no debe empezar receta en ninguna ruta
        String off = run(new Probe("IDLE_C", 20), false, clone(cases[2]), new ItemStack[] { new ItemStack(Material.DIRT, 64) });
        String on = run(new Probe("IDLE_D", 20), true, clone(cases[2]), new ItemStack[] { new ItemStack(Material.DIRT, 64) });
        Assertions.assertEquals(off, on);
        Assertions.assertTrue(PerfFlags.IDLE_SKIPPED.sum() > 0, "el atajo debio dispararse en el caso ocioso");
    }

    private static ItemStack[] clone(ItemStack[] a) {
        ItemStack[] r = new ItemStack[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i].clone();
        return r;
    }

    @Test
    @DisplayName("Microbenchmark: maquina ociosa, findNextRecipe off vs on")
    void benchmark() {
        if (!Boolean.getBoolean("sf.bench")) return;
        for (int recipes : new int[] { 10, 40, 150 }) {
            Probe m = new Probe("IDLE_BENCH" + recipes, recipes);
            BlockMenu menu = place(m);
            int iters = 200_000;
            double off = 0, on = 0;
            for (int round = 0; round < 5; round++) { // las ultimas rondas ya con JIT caliente
                PerfFlags.idleSkip = false;
                long t = System.nanoTime();
                for (int i = 0; i < iters; i++) m.find(menu);
                off = (System.nanoTime() - t) / (double) iters;
                PerfFlags.idleSkip = true;
                t = System.nanoTime();
                for (int i = 0; i < iters; i++) m.find(menu);
                on = (System.nanoTime() - t) / (double) iters;
            }
            System.out.printf("BENCH recetas=%d  off=%.0f ns/tick  on=%.0f ns/tick  x%.1f%n", recipes + 1, off, on, off / on);
        }
    }
}
