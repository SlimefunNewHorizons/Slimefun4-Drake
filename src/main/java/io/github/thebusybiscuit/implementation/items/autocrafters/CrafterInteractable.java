package io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters;

import javax.annotation.Nonnull;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Almacén alternativo a un cofre sobre el que puede trabajar un {@link AbstractAutoCrafter}: un bloque de Slimefun
 * (p. ej. el {@link CrafterSmartPort}) registrado en {@link CrafterInteractorManager}. Idea de Xzavier0722
 * (SlimefunGuguProject/Slimefun4) reescrita sobre nuestro {@code BlockMenu}.
 */
public interface CrafterInteractable {

    /** Slots de los que el crafter puede tomar ingredientes. */
    @Nonnull
    int[] getInputSlots();

    ItemStack getItem(int slot);

    void setAmount(int slot, int amount);

    /** @return si el resultado cabe en la salida. */
    boolean canOutput(@Nonnull ItemStack item);

    /** @return si se pudo colocar el resultado. */
    boolean addOutput(@Nonnull ItemStack item);

    /** Se avisa al cambiar de receta: nº de ingredientes distintos (limita la entrada por cargo). */
    default void setIngredientCount(@Nonnull Block b, int count) {
    }
}
