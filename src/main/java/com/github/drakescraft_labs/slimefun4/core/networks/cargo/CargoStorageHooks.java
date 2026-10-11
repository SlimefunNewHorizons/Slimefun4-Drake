package com.github.drakescraft_labs.slimefun4.core.networks.cargo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.block.Block;

import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;

import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;

/**
 * Lets the {@link CargoNet} move items in and out of addon storages that keep their
 * contents in an internal cache instead of in their menu slots: InfinityExpansion's
 * StorageUnit and FluffyMachines' Barrel.
 * <p>
 * The addon methods are looked up once per class, through the whole class hierarchy,
 * and then cached. Before, every transfer called {@link Class#getDeclaredMethod} again,
 * which never sees inherited methods: subclasses such as FluffyMachines' MiniBarrel
 * never got their hook, and items that only share the name (LiteXpansion's
 * MultiFunctionalStorageUnit) threw and swallowed an exception on every transfer.
 *
 * @see CargoUtils
 */
final class CargoStorageHooks {

    /**
     * The input slots of a FluffyMachines Barrel.
     */
    private static final int[] BARREL_INPUT_SLOTS = { 19, 20 };

    private static final ClassValue<ItemHooks> ITEM_HOOKS = new ClassValue<>() {

        @Override
        protected ItemHooks computeValue(Class<?> type) {
            return ItemHooks.resolve(type);
        }
    };

    private static final ClassValue<CacheHooks> CACHE_HOOKS = new ClassValue<>() {

        @Override
        protected CacheHooks computeValue(Class<?> type) {
            return new CacheHooks(findMethod(type, "input"), findMethod(type, "output"));
        }
    };

    private CargoStorageHooks() {}

    /**
     * Moves stored items into the output slots before the cargo network withdraws from the target.
     *
     * @param target
     *            The {@link Block} the cargo node is attached to
     */
    static void beforeWithdraw(@Nonnull Block target) {
        SlimefunItem item = BlockStorage.check(target);

        if (item != null) {
            ITEM_HOOKS.get(item.getClass()).beforeWithdraw(item, target);
        }
    }

    /**
     * Moves items from the input slots into storage, so the cargo network finds free input slots.
     *
     * @param target
     *            The {@link Block} the cargo node is attached to
     */
    static void beforeInsert(@Nonnull Block target) {
        SlimefunItem item = BlockStorage.check(target);

        if (item != null) {
            ITEM_HOOKS.get(item.getClass()).beforeInsert(item, target);
        }
    }

    /**
     * Looks a method up in the given class and all of its superclasses.
     *
     * @return The accessible {@link Method}, or null if no class in the hierarchy declares it
     */
    @Nullable
    private static Method findMethod(@Nonnull Class<?> type, @Nonnull String name, @Nonnull Class<?>... parameters) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException x) {
                // Keep looking in the superclass
            } catch (RuntimeException x) {
                // The method exists but cannot be made accessible, treat it as missing
                return null;
            }
        }

        return null;
    }

    private static final class ItemHooks {

        private static final ItemHooks NONE = new ItemHooks(null, null, null, null);

        // InfinityExpansion StorageUnit
        private final Method getCache;

        // FluffyMachines Barrel
        private final Method getCapacity;
        private final Method pushOutput;
        private final Method acceptInput;

        private final AtomicBoolean warned = new AtomicBoolean();

        private ItemHooks(@Nullable Method getCache, @Nullable Method getCapacity, @Nullable Method pushOutput, @Nullable Method acceptInput) {
            this.getCache = getCache;
            this.getCapacity = getCapacity;
            this.pushOutput = pushOutput;
            this.acceptInput = acceptInput;
        }

        @Nonnull
        static ItemHooks resolve(@Nonnull Class<?> type) {
            String name = type.getName();

            if (name.contains("StorageUnit")) {
                Method getCache = findMethod(type, "getCache", Location.class);
                return getCache == null ? NONE : new ItemHooks(getCache, null, null, null);
            }

            if (name.contains("Barrel")) {
                Method getCapacity = findMethod(type, "getCapacity", Block.class);
                Method pushOutput = findMethod(type, "pushOutput", BlockMenu.class, Block.class, int.class);
                Method acceptInput = findMethod(type, "acceptInput", BlockMenu.class, Block.class, int.class, int.class);

                if (getCapacity != null && pushOutput != null && acceptInput != null) {
                    return new ItemHooks(null, getCapacity, pushOutput, acceptInput);
                }
            }

            return NONE;
        }

        void beforeWithdraw(@Nonnull SlimefunItem item, @Nonnull Block target) {
            if (this == NONE) {
                return;
            }

            try {
                if (getCache != null) {
                    Object cache = getCache.invoke(item, target.getLocation());

                    if (cache != null) {
                        CACHE_HOOKS.get(cache.getClass()).invoke(cache, false);
                    }
                } else {
                    BlockMenu menu = BlockStorage.getInventory(target);

                    if (menu != null) {
                        int capacity = (Integer) getCapacity.invoke(item, target);
                        pushOutput.invoke(item, menu, target, capacity);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException x) {
                warnOnce(item, x);
            }
        }

        void beforeInsert(@Nonnull SlimefunItem item, @Nonnull Block target) {
            if (this == NONE) {
                return;
            }

            try {
                if (getCache != null) {
                    Object cache = getCache.invoke(item, target.getLocation());

                    if (cache != null) {
                        CACHE_HOOKS.get(cache.getClass()).invoke(cache, true);
                    }
                } else {
                    BlockMenu menu = BlockStorage.getInventory(target);

                    if (menu != null) {
                        int capacity = (Integer) getCapacity.invoke(item, target);

                        for (int slot : BARREL_INPUT_SLOTS) {
                            acceptInput.invoke(item, menu, target, slot, capacity);
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException x) {
                warnOnce(item, x);
            }
        }

        private void warnOnce(@Nonnull SlimefunItem item, @Nonnull Exception x) {
            if (warned.compareAndSet(false, true)) {
                Throwable cause = x instanceof InvocationTargetException && x.getCause() != null ? x.getCause() : x;
                Slimefun.logger().log(Level.WARNING, cause, () -> "Cargo storage hook failed for " + item.getId() + ". Further errors for " + item.getClass().getName() + " will not be logged.");
            }
        }
    }

    private static final class CacheHooks {

        private final Method input;
        private final Method output;

        CacheHooks(@Nullable Method input, @Nullable Method output) {
            this.input = input;
            this.output = output;
        }

        void invoke(@Nonnull Object cache, boolean insert) throws ReflectiveOperationException {
            Method method = insert ? input : output;

            if (method != null) {
                method.invoke(cache);
            }
        }
    }
}
