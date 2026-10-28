package me.mrCookieSlime.Slimefun.api.inventory;

import java.io.File;
import java.io.IOException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.bakedlibs.dough.config.Config;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import io.github.thebusybiscuit.slimefun4.utils.FileUtils;

/**
 * BlockMenu ya no escribe los ".sfi" a traves de dough Config: su constructor creaba un
 * DoughLogger por guardado y el setParent de ese logger toma el treeLock global de
 * java.util.logging, lo que bloqueo el Server thread 10 s durante el lote sincrono de
 * autoguardado (watchdog del 2026-09-12, ticket 439).
 *
 * El lector sigue siendo dough Config --BlockStorage#loadInventories--, asi que el formato
 * escrito tiene que seguir siendo exactamente el que ese lector entiende. Esta prueba fija
 * ese contrato escritor/lector.
 */
class TestBlockMenuFileFormat {

    private static ServerMock server;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() throws IOException {
        MockBukkit.unmock();
        FileUtils.deleteDirectory(new File("data-storage"));
    }

    private static BlockMenuPreset newPreset(String id) {
        return new BlockMenuPreset(id, "Test Menu") {

            @Override
            public void init() {
                // Tamano fijo: sin ranuras ocupadas, las 9 quedan disponibles como inventario.
                setSize(9);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }

    private static File inventoryFile(Location l) {
        return new File("data-storage/Slimefun/stored-inventories/"
            + l.getWorld().getName() + ';' + l.getBlockX() + ';' + l.getBlockY() + ';' + l.getBlockZ() + ".sfi");
    }

    @Test
    void testSavedFileIsReadableByDoughConfig() {
        World world = server.addSimpleWorld("sfi_format_world");
        Location l = new Location(world, 1, 64, 1);

        BlockMenuPreset preset = newPreset("TEST_SFI_FORMAT");
        BlockMenu menu = new BlockMenu(preset, l);

        ItemStack item = new ItemStack(Material.DIAMOND, 7);
        menu.replaceExistingItem(4, item);
        menu.markDirty();
        menu.save(l);
        flushStore();

        File file = inventoryFile(l);
        Assertions.assertTrue(file.exists(), "el menu deberia haber escrito su .sfi");

        // Misma via de lectura que BlockStorage#loadInventories.
        Config cfg = new Config(file);
        Assertions.assertEquals("TEST_SFI_FORMAT", cfg.getString("preset"), "el id del preset debe persistir");
        Assertions.assertEquals(item, cfg.getItem("4"), "el item debe recuperarse identico");
    }

    @Test
    void testEmptySlotIsClearedInsteadOfKept() {
        World world = server.addSimpleWorld("sfi_clear_world");
        Location l = new Location(world, 2, 64, 2);

        BlockMenuPreset preset = newPreset("TEST_SFI_CLEAR");
        BlockMenu menu = new BlockMenu(preset, l);

        menu.replaceExistingItem(4, new ItemStack(Material.DIAMOND, 7));
        menu.markDirty();
        menu.save(l);
        flushStore();

        Assertions.assertNotNull(new Config(inventoryFile(l)).getItem("4"), "precondicion: la ranura estaba escrita");

        // Vaciar la ranura tiene que borrar la clave, no dejar el item anterior en disco:
        // un .sfi obsoleto reintroduce items al reiniciar.
        menu.replaceExistingItem(4, null);
        menu.markDirty();
        menu.save(l);
        flushStore();

        Assertions.assertNull(new Config(inventoryFile(l)).getItem("4"), "la ranura vaciada no debe seguir en disco");
    }

    private static void flushStore() {
        io.github.thebusybiscuit.slimefun4.storage.backend.SfStores.get().flush();
    }
}
