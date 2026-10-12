package com.github.drakescraft_labs.slimefun4.core.guide.themes;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.Material;

/**
 * A theme groups the item groups of several addons under one entry of the Slimefun Guide
 * (for example "Technology & Machines" or "Magic & Arcana") instead of listing every group by load order.
 *
 * @param id
 *            Stable identifier used in the config and in the generated NamespacedKey
 * @param name
 *            Display name (English, colour codes allowed)
 * @param icon
 *            Material shown in the guide
 * @param description
 *            Lore lines shown under the name
 * @param namespaces
 *            Lower-case NamespacedKey namespaces (= plugin names) whose item groups belong to this theme
 */
public record GuideTheme(@Nonnull String id, @Nonnull String name, @Nonnull Material icon, @Nonnull List<String> description,
                         @Nonnull Set<String> namespaces) {
}
