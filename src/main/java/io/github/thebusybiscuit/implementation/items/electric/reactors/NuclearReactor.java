package io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactive;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;

/**
 * The {@link NuclearReactor} is an implementation of {@link Reactor} that uses
 * any {@link Radioactive} material to generate energy.
 * It needs water coolant as well as a steady supply of Reactor Coolant Cells.
 * While actively running, it emits hazardous gamma radiation to any player within
 * 7 blocks unless protected by a full hazmat suit.
 * 
 * @author TheBusyBiscuit
 * @author SlimefunNewHorizons
 * 
 * @see NetherStarReactor
 *
 */
public abstract class NuclearReactor extends Reactor {

    @ParametersAreNonnullByDefault
    protected NuclearReactor(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    protected void registerDefaultFuelTypes() {
        registerFuel(new MachineFuel(1200, SlimefunItems.URANIUM, SlimefunItems.NEPTUNIUM));
        registerFuel(new MachineFuel(600, SlimefunItems.NEPTUNIUM, SlimefunItems.PLUTONIUM));
        registerFuel(new MachineFuel(1500, SlimefunItems.BOOSTED_URANIUM, null));
    }

    @Override
    public ItemStack getProgressBar() {
        return SlimefunItems.LAVA_CRYSTAL;
    }

    @Override
    public ItemStack getCoolant() {
        return SlimefunItems.REACTOR_COOLANT_CELL;
    }

    @Override
    public ItemStack getFuelIcon() {
        return SlimefunItems.URANIUM;
    }

    @Override
    public void extraTick(@Nonnull Location l) {
        Slimefun.runSync(() -> {
            for (Entity entity : l.getWorld().getNearbyEntities(l, 7, 7, 7, n -> n instanceof Player && n.isValid())) {
                if (entity instanceof Player player) {
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }
                    Optional<PlayerProfile> profile = PlayerProfile.find(player);
                    if (profile.isPresent() && !profile.get().hasFullProtectionAgainst(ProtectionType.RADIATION)) {
                        int exposure = RadiationUtils.getExposure(player);
                        if (exposure == 0) {
                            Slimefun.getLocalization().sendMessage(player, "messages.radiation");
                        }
                        RadiationUtils.addExposure(player, 2);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
                    }
                }
            }
        });
    }

}
