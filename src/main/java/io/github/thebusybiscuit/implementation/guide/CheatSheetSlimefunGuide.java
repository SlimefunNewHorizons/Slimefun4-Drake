package io.github.thebusybiscuit.slimefun4.implementation.guide;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.SlimefunGuideItem;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * This is an admin-variant of the {@link SurvivalSlimefunGuide} which allows a {@link Player}
 * to spawn in a {@link SlimefunItem} via click rather than showing their {@link Recipe}.
 * 
 * @author TheBusyBiscuit
 *
 */
public class CheatSheetSlimefunGuide extends SurvivalSlimefunGuide {

    private final ItemStack item;

    public CheatSheetSlimefunGuide() {
        super(false, true);

        item = new SlimefunGuideItem(this, "&cSlimefun Guide &4(Cheat Sheet)");
    }

    /**
     * Returns a {@link List} of visible {@link ItemGroup} instances that the {@link SlimefunGuide} would display.
     *
     * @param p
     *            The {@link Player} who opened his {@link SlimefunGuide}
     * @param profile
     *            The {@link PlayerProfile} of the {@link Player}
     * 
     * @return a {@link List} of visible {@link ItemGroup} instances
     */
    @Override
    protected List<ItemGroup> getVisibleItemGroups(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        return super.getVisibleItemGroups(p, profile);
    }

    @Override
    public @Nonnull SlimefunGuideMode getMode() {
        return SlimefunGuideMode.CHEAT_MODE;
    }

    @Override
    public @Nonnull ItemStack getItem() {
        return item;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void createHeader(Player p, PlayerProfile profile, ChestMenu menu) {
        super.createHeader(p, profile, menu);

        // Remove Settings Panel
        menu.addItem(1, ChestMenuUtils.getBackground());
        menu.addMenuClickHandler(1, ChestMenuUtils.getEmptyClickHandler());
    }
}
