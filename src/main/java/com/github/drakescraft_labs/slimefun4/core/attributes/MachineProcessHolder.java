package com.github.drakescraft_labs.slimefun4.core.attributes;

import javax.annotation.Nonnull;

import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.core.machines.MachineOperation;
import com.github.drakescraft_labs.slimefun4.core.machines.MachineProcessor;

/**
 * This {@link ItemAttribute} marks a {@link SlimefunItem} as a {@link MachineProcessHolder}.
 * A {@link MachineProcessHolder} can hold a {@link MachineProcessor} which is responsible for
 * handling any {@link MachineOperation}.
 * 
 * @author TheBusyBiscuit
 * 
 * @param <T>
 *            The type of {@link MachineOperation} the {@link MachineProcessor} should hold.
 */
public interface MachineProcessHolder<T extends MachineOperation> extends ItemAttribute {

    /**
     * This method returns our {@link MachineProcessor} instance.
     * 
     * @return Our {@link MachineProcessor}
     */
    @Nonnull
    MachineProcessor<T> getMachineProcessor();

    /**
     * Whether an operation held by this machine can be returned safely when the
     * server stops. Implementations must opt in only after removing every input
     * from their inventory when the operation starts.
     *
     * @return {@code true} when in-flight {@code CraftingOperation} inputs are refundable
     */
    default boolean canRefundInFlightOperation() {
        return false;
    }

}
