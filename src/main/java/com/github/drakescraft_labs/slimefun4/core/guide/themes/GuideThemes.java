package com.github.drakescraft_labs.slimefun4.core.guide.themes;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.guide.ThemeItemGroup;

/**
 * Optional organisation layer for the Slimefun Guide and the Cheat Sheet.
 *
 * <p>When {@code guide.themes.enabled} is {@code true} in {@code config.yml}, the main menu shows one entry per
 * theme (defined in {@code plugins/Slimefun/guide-themes.yml}) instead of the flat list of every addon group.
 * Item groups are assigned to a theme by the namespace of their key (the lower-case plugin name that registered
 * them); anything unassigned lands in the fallback theme. A last entry lists every group A-Z.
 * Recipes, IDs, researches and permissions are not touched: only the navigation changes.
 */
public final class GuideThemes {

    public static final String ENABLED_PATH = "guide.themes.enabled";
    public static final String FILE_NAME = "guide-themes.yml";
    public static final String ALL_THEME_ID = "all";

    private static GuideThemes instance;

    private final List<GuideTheme> themes;
    private final GuideTheme fallback;
    private final GuideTheme all;
    private final Map<String, GuideTheme> byNamespace = new HashMap<>();

    GuideThemes(@Nonnull List<GuideTheme> themes, @Nonnull GuideTheme fallback, @Nonnull GuideTheme all) {
        this.themes = List.copyOf(themes);
        this.fallback = fallback;
        this.all = all;

        for (GuideTheme theme : themes) {
            for (String namespace : theme.namespaces()) {
                byNamespace.putIfAbsent(namespace, theme);
            }
        }
    }

    /** Returns the loaded themes, reading {@code guide-themes.yml} the first time. */
    public static synchronized @Nonnull GuideThemes get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Forces the next {@link #get()} to read the file again (used by reloads and tests). */
    public static synchronized void reset() {
        instance = null;
    }

    private static @Nonnull GuideThemes load() {
        Slimefun plugin = Slimefun.instance();
        File file = new File(plugin.getDataFolder(), FILE_NAME);

        try {
            if (!file.exists()) {
                plugin.saveResource(FILE_NAME, false);
            }
            return fromConfiguration(YamlConfiguration.loadConfiguration(file));
        } catch (RuntimeException | LinkageError x) {
            plugin.getLogger().log(Level.WARNING, x, () -> "Could not read " + FILE_NAME + ", using built-in defaults");
        }

        try (Reader reader = new java.io.InputStreamReader(plugin.getResource(FILE_NAME), java.nio.charset.StandardCharsets.UTF_8)) {
            return fromConfiguration(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException | RuntimeException x) {
            plugin.getLogger().log(Level.SEVERE, x, () -> "Could not read the default " + FILE_NAME);
            return fromConfiguration(new YamlConfiguration());
        }
    }

    /** Builds the themes from a configuration section; missing or invalid parts fall back to safe defaults. */
    public static @Nonnull GuideThemes fromConfiguration(@Nonnull ConfigurationSection config) {
        List<GuideTheme> list = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("themes");

        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);

                if (entry != null) {
                    list.add(parseTheme(id, entry));
                }
            }
        }

        GuideTheme fallback = parseOrDefault(config.getConfigurationSection("fallback"), "other", "&7Other Addons", Material.ENDER_CHEST);
        GuideTheme all = parseOrDefault(config.getConfigurationSection("all"), ALL_THEME_ID, "&fAll Categories &7(A-Z)", Material.BOOKSHELF);
        return new GuideThemes(list, fallback, all);
    }

    private static @Nonnull GuideTheme parseOrDefault(@Nullable ConfigurationSection entry, String id, String name, Material icon) {
        if (entry == null) {
            return new GuideTheme(id, name, icon, List.of(), Set.of());
        }
        return parseTheme(id, entry);
    }

    private static @Nonnull GuideTheme parseTheme(@Nonnull String id, @Nonnull ConfigurationSection entry) {
        Material icon = Material.matchMaterial(entry.getString("icon", "BOOK"));
        Set<String> namespaces = new LinkedHashSet<>();

        for (String namespace : entry.getStringList("namespaces")) {
            namespaces.add(namespace.toLowerCase(Locale.ROOT).trim());
        }

        return new GuideTheme(id.toLowerCase(Locale.ROOT), entry.getString("name", id), icon == null ? Material.BOOK : icon,
            entry.getStringList("description"), namespaces);
    }

    public boolean isEnabled() {
        return Slimefun.getCfg().getBoolean(ENABLED_PATH);
    }

    public @Nonnull List<GuideTheme> getThemes() {
        return themes;
    }

    /** Returns the theme of an item group, or the fallback theme if no theme claims its namespace. */
    public @Nonnull GuideTheme themeOf(@Nonnull ItemGroup group) {
        return byNamespace.getOrDefault(group.getKey().getNamespace().toLowerCase(Locale.ROOT), fallback);
    }

    /**
     * Replaces a flat, already sorted list of visible groups by the theme entries to show in the main menu.
     * Empty themes are hidden; the fallback theme and the "all categories" entry go last.
     */
    public @Nonnull List<ItemGroup> toThemeEntries(@Nonnull List<ItemGroup> visibleGroups) {
        Map<GuideTheme, List<ItemGroup>> grouped = new LinkedHashMap<>();

        for (GuideTheme theme : themes) {
            grouped.put(theme, new ArrayList<>());
        }
        grouped.put(fallback, new ArrayList<>());

        for (ItemGroup group : visibleGroups) {
            grouped.computeIfAbsent(themeOf(group), t -> new ArrayList<>()).add(group);
        }

        List<ItemGroup> entries = new ArrayList<>();

        for (Map.Entry<GuideTheme, List<ItemGroup>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                entries.add(new ThemeItemGroup(entry.getKey(), entry.getValue()));
            }
        }

        if (!visibleGroups.isEmpty()) {
            entries.add(new ThemeItemGroup(all, visibleGroups));
        }

        return entries;
    }

    /** Namespaces that are not claimed by any theme (useful to refine {@code guide-themes.yml}). */
    public @Nonnull Set<String> unassignedNamespaces(@Nonnull Iterable<ItemGroup> groups) {
        Set<String> result = new LinkedHashSet<>();

        for (ItemGroup group : groups) {
            String namespace = group.getKey().getNamespace().toLowerCase(Locale.ROOT);

            if (!byNamespace.containsKey(namespace)) {
                result.add(namespace);
            }
        }

        return result;
    }
}
