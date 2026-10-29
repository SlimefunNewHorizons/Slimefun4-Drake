package io.github.thebusybiscuit.core.machines;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Cierra, al apagar el servidor, las operaciones en curso de las máquinas «legacy» de los addons.
 *
 * <p>Patrón heredado de Slimefun: {@code findNextRecipe(menu)} <b>consume los ingredientes al empezar</b> y deja la
 * receta solo en un mapa en memoria ({@code processing: Map<Block, MachineRecipe>}). Si el servidor se reinicia a mitad
 * de proceso, esos ítems desaparecen: es la causa de «se me perdieron los libros / encantamientos tras el reinicio».
 * Las máquinas que usan {@link MachineProcessor} ya tienen su propio reembolso; esta clase cubre las que no.
 *
 * <p>Estrategia: <b>completar la operación</b>, no reembolsar. Se entregan los {@code getOutput()} que la máquina ya
 * había prometido a las ranuras de salida. Así no hace falta saber qué consumió cada máquina (algunas consumen solo si
 * {@code isInputConsumed()}, otras construyen la receta con piezas reales) y es imposible duplicar: cada operación
 * produce lo mismo que habría producido al terminar su contador, una sola vez, porque la entrada se elimina del mapa.
 * Lo que no cabe en las ranuras de salida se suelta en el mundo, nunca se pierde.
 */
public final class LegacyProcessingRecovery {

    private static final String FIELD_NAME = "processing";
    private static final String RECIPE_CLASS = "MachineRecipe";

    private LegacyProcessingRecovery() {}

    /**
     * Recorre todos los {@link SlimefunItem} registrados y completa las operaciones pendientes de sus mapas
     * {@code processing}. Debe llamarse en el hilo principal, antes de guardar los inventarios.
     *
     * @return número de operaciones entregadas
     */
    public static int completeAllActiveOperations() {
        Set<Object> seenMaps = Collections.newSetFromMap(new IdentityHashMap<>());
        int delivered = 0;
        int stale = 0;

        for (SlimefunItem item : new ArrayList<>(Slimefun.getRegistry().getAllSlimefunItems())) {
            try {
                int[] result = recoverItem(item, seenMaps);
                delivered += result[0];
                stale += result[1];
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.SEVERE, "[Slimefun] Fallo al recuperar operaciones legacy de " + item.getId(), t);
            }
        }

        if (delivered > 0 || stale > 0) {
            Bukkit.getLogger().info("[Slimefun] Operaciones legacy completadas al apagar: " + delivered
                + " entregadas, " + stale + " obsoletas descartadas");
        }
        return delivered;
    }

    /** @return {entregadas, obsoletas} */
    private static int[] recoverItem(@Nonnull SlimefunItem item, @Nonnull Set<Object> seenMaps) throws IllegalAccessException {
        int delivered = 0;
        int stale = 0;

        for (Class<?> type = item.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            Field field;
            try {
                field = type.getDeclaredField(FIELD_NAME);
            } catch (NoSuchFieldException e) {
                continue;
            }
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            field.setAccessible(true);
            Object value = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(item);
            if (!(value instanceof Map<?, ?> map) || map.isEmpty() || !seenMaps.add(map)) {
                continue;
            }

            for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
                int outcome = recoverEntry(item, entry.getKey(), entry.getValue());
                if (outcome != 0) {
                    map.remove(entry.getKey());          // una sola vez: nunca se entrega dos veces
                    if (outcome > 0) {
                        delivered++;
                    } else {
                        stale++;
                    }
                }
            }
        }
        return new int[] { delivered, stale };
    }

    /** @return 1 entregada, -1 obsoleta (se descarta), 0 no reconocida (no se toca) */
    private static int recoverEntry(@Nonnull SlimefunItem item, Object key, Object recipe) {
        Location loc = toLocation(key);
        ItemStack[] outputs = outputsOf(recipe);
        if (loc == null || outputs == null) {
            return 0;
        }

        World world = loc.getWorld();
        BlockMenu menu = BlockStorage.getInventory(loc);
        // Entrada obsoleta: la máquina ya no está ahí (bloque roto). Nada que entregar; se limpia.
        if (world == null || menu == null || !item.getId().equals(BlockStorage.checkID(loc))) {
            return -1;
        }

        int[] outputSlots = (item instanceof InventoryBlock ib) ? ib.getOutputSlots() : null;
        for (ItemStack result : outputs) {
            if (result == null || result.getType().isAir()) {
                continue;
            }
            ItemStack remaining = outputSlots != null ? menu.pushItem(result.clone(), outputSlots) : result.clone();
            if (remaining != null && !remaining.getType().isAir() && remaining.getAmount() > 0) {
                world.dropItemNaturally(loc, remaining);
            }
        }
        menu.markDirty();
        return 1;
    }

    private static Location toLocation(Object key) {
        if (key instanceof Block block) {
            return block.getLocation();
        }
        if (key instanceof Location location) {
            return location;
        }
        return null;
    }

    /** Lee {@code getOutput()} por reflexión: el addon puede usar cualquiera de los dos paquetes de MachineRecipe. */
    private static ItemStack[] outputsOf(Object recipe) {
        if (recipe == null || !isRecipe(recipe.getClass())) {
            return null;
        }
        try {
            Method getOutput = recipe.getClass().getMethod("getOutput");
            return getOutput.invoke(recipe) instanceof ItemStack[] outputs ? outputs : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean isRecipe(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getSimpleName().equals(RECIPE_CLASS)) {
                return true;
            }
        }
        return false;
    }
}
