package com.github.drakescraft_labs.slimefun4.utils;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/**
 * Builds the {@link EntityDamageByEntityEvent EntityDamageByEntityEvents} that Slimefun calls
 * to ask protection plugins whether an entity may be hurt.
 * <p>
 * Every short constructor of that event is marked for removal (1.21.11, 26.1 and 26.2).
 * This uses the full one and fills it in exactly like those constructors did: a generic
 * {@link DamageSource} caused by the damager, the damage as the base modifier, not critical.
 */
public final class DamageEvents {

    private static final Function<? super Double, Double> ZERO = Functions.constant(-0.0);

    private DamageEvents() {}

    /**
     * Creates, but does not call, a damage event for the given entities.
     *
     * @param damager
     *            The {@link Entity} dealing the damage
     * @param damagee
     *            The {@link Entity} taking the damage
     * @param cause
     *            The {@link DamageCause}
     * @param damage
     *            The amount of damage
     *
     * @return The event, ready to be passed to the plugin manager
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    @SuppressWarnings("deprecation")
    public static EntityDamageByEntityEvent create(Entity damager, Entity damagee, DamageCause cause, double damage) {
        DamageSource source = DamageSource.builder(DamageType.GENERIC).withCausingEntity(damager).withDirectEntity(damager).build();

        Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
        modifiers.put(DamageModifier.BASE, damage);

        Map<DamageModifier, Function<? super Double, Double>> modifierFunctions = new EnumMap<>(DamageModifier.class);
        modifierFunctions.put(DamageModifier.BASE, ZERO);

        return new EntityDamageByEntityEvent(damager, damagee, cause, source, modifiers, modifierFunctions, false);
    }
}
