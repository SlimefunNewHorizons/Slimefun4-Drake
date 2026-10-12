package com.github.drakescraft_labs.slimefun4.implementation.guide;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.groups.FlexItemGroup;
import com.github.drakescraft_labs.slimefun4.api.player.PlayerProfile;
import com.github.drakescraft_labs.slimefun4.core.guide.SlimefunGuideImplementation;
import com.github.drakescraft_labs.slimefun4.core.guide.SlimefunGuideMode;
import com.github.drakescraft_labs.slimefun4.core.guide.themes.GuideTheme;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;

import dev.drake.dough.items.CustomItemStack;

/**
 * A virtual entry of the guide main menu that lists the item groups of one {@link GuideTheme}.
 * It is never registered in the item group registry, so no other part of Slimefun sees it.
 */
public class ThemeItemGroup extends FlexItemGroup {

    private final GuideTheme theme;
    private final List<ItemGroup> members;
    private final ItemStack icon;

    public ThemeItemGroup(@Nonnull GuideTheme theme, @Nonnull List<ItemGroup> members) {
        super(new NamespacedKey(Slimefun.instance(), "theme_" + theme.id()), createIcon(theme, members.size()));
        this.theme = theme;
        this.members = List.copyOf(members);
        this.icon = createIcon(theme, members.size());
    }

    private static @Nonnull ItemStack createIcon(@Nonnull GuideTheme theme, int size) {
        List<String> lore = new ArrayList<>();
        lore.add("");

        for (String line : theme.description()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        if (!theme.description().isEmpty()) {
            lore.add("");
        }

        lore.add(ChatColor.GRAY + "Categories: " + ChatColor.WHITE + size);
        lore.add("");
        lore.add(ChatColor.GRAY + "⇨ " + ChatColor.YELLOW + "Click to open");
        return new CustomItemStack(theme.icon(), theme.name(), lore.toArray(new String[0]));
    }

    /** Themes are not translated through the language files: the name and lore come straight from guide-themes.yml. */
    @Override
    public @Nonnull ItemStack getItem(@Nonnull Player p) {
        return icon.clone();
    }

    public @Nonnull GuideTheme getTheme() {
        return theme;
    }

    public @Nonnull List<ItemGroup> getMembers() {
        return members;
    }

    @Override
    public boolean isVisible(Player p, PlayerProfile profile, SlimefunGuideMode layout) {
        return !members.isEmpty();
    }

    @Override
    public void open(Player p, PlayerProfile profile, SlimefunGuideMode layout) {
        SlimefunGuideImplementation guide = Slimefun.getRegistry().getSlimefunGuide(layout);

        if (guide instanceof SurvivalSlimefunGuide survivalGuide) {
            survivalGuide.openGroupList(profile, this, 1);
        }
    }
}
