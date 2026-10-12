package com.github.drakescraft_labs.slimefun4.core.guide.themes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import dev.drake.dough.items.CustomItemStack;
import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.player.PlayerProfile;
import com.github.drakescraft_labs.slimefun4.core.guide.SlimefunGuideMode;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import com.github.drakescraft_labs.slimefun4.implementation.guide.ThemeItemGroup;
import com.github.drakescraft_labs.slimefun4.test.TestUtilities;

@SuppressWarnings("deprecation")
class TestGuideThemes {

    private static ServerMock server;
    private static Slimefun plugin;

    private static final String SAMPLE = """
        themes:
          tech:
            name: "&bTechnology"
            icon: COMPARATOR
            namespaces: [DynaTech, InfinityExpansion]
          magic:
            name: "&dMagic"
            icon: ENCHANTED_BOOK
            namespaces: [alchimiavitae]
          empty:
            name: "&7Nothing here"
            icon: not_a_material
            namespaces: [nobody]
        fallback:
          name: "&7Other"
          icon: ENDER_CHEST
        all:
          name: "&fAll"
          icon: BOOKSHELF
        """;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @AfterEach
    void reset() {
        Slimefun.getCfg().setValue(GuideThemes.ENABLED_PATH, false);
        GuideThemes.reset();
    }

    private static ItemGroup group(String namespace, String key, String name) {
        return group(namespace, key, name, Material.DIAMOND);
    }

    private static ItemGroup group(String namespace, String key, String name, Material icon) {
        return new ItemGroup(new NamespacedKey(namespace, key), new CustomItemStack(icon, name));
    }

    private static GuideThemes sample() {
        return GuideThemes.fromConfiguration(YamlConfiguration.loadConfiguration(new java.io.StringReader(SAMPLE)));
    }

    @Test
    @DisplayName("Namespaces are matched case-insensitively and unknown ones fall back")
    void testThemeOf() {
        GuideThemes themes = sample();

        Assertions.assertEquals("tech", themes.themeOf(group("dynatech", "a", "A")).id());
        Assertions.assertEquals("tech", themes.themeOf(group("infinityexpansion", "a", "A")).id());
        Assertions.assertEquals("magic", themes.themeOf(group("alchimiavitae", "a", "A")).id());
        Assertions.assertEquals("other", themes.themeOf(group("somethingelse", "a", "A")).id());
        Assertions.assertEquals(Material.BOOK, themes.getThemes().get(2).icon(), "an invalid icon must not break loading");
    }

    @Test
    @DisplayName("Empty themes are hidden, the fallback comes before the A-Z entry, and nothing is lost")
    void testThemeEntries() {
        GuideThemes themes = sample();
        List<ItemGroup> visible = List.of(group("dynatech", "m", "Machines"), group("alchimiavitae", "p", "Potions"),
            group("unknown", "x", "Mystery"), group("infinityexpansion", "i", "Infinity"));

        List<ItemGroup> entries = themes.toThemeEntries(visible);
        List<String> ids = new ArrayList<>();
        int total = 0;

        for (ItemGroup entry : entries) {
            ThemeItemGroup theme = (ThemeItemGroup) entry;
            ids.add(theme.getTheme().id());

            if (!theme.getTheme().id().equals(GuideThemes.ALL_THEME_ID)) {
                total += theme.getMembers().size();
            }
        }

        Assertions.assertEquals(List.of("tech", "magic", "other", "all"), ids);
        Assertions.assertEquals(visible.size(), total, "every group must appear in exactly one theme");
        Assertions.assertEquals(visible.size(), ((ThemeItemGroup) entries.get(entries.size() - 1)).getMembers().size());
        Assertions.assertTrue(themes.toThemeEntries(List.of()).isEmpty());
    }

    @Test
    @DisplayName("Namespaces that no theme claims are reported")
    void testUnassigned() {
        Set<String> unassigned = sample().unassignedNamespaces(List.of(group("dynatech", "a", "A"), group("zzz", "a", "A")));
        Assertions.assertEquals(Set.of("zzz"), unassigned);
    }

    @Test
    @DisplayName("The bundled guide-themes.yml is valid: real icons and no namespace in two themes")
    void testBundledFile() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(TestGuideThemes.class.getClassLoader().getResourceAsStream(GuideThemes.FILE_NAME), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            GuideThemes themes = GuideThemes.fromConfiguration(yaml);
            Assertions.assertTrue(themes.getThemes().size() >= 8);

            Set<String> seen = new HashSet<>();

            for (GuideTheme theme : themes.getThemes()) {
                Assertions.assertFalse(theme.namespaces().isEmpty(), theme.id() + " has no namespaces");
                Assertions.assertNotNull(Material.matchMaterial(yaml.getString("themes." + theme.id() + ".icon")), theme.id() + " icon");
                Assertions.assertFalse(theme.name().isBlank());

                for (String namespace : theme.namespaces()) {
                    Assertions.assertTrue(seen.add(namespace), "namespace in two themes: " + namespace);
                }
            }

            Assertions.assertTrue(seen.contains("slimefun"), "core groups need a theme");
        }
    }

    /** Number of menu slots (9-44) showing the given material: MockBukkit has no languages, so group names are not readable. */
    private static int count(Inventory inventory, Material material) {
        int total = 0;

        for (int slot = 9; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);

            if (item != null && item.getType() == material) {
                total++;
            }
        }

        return total;
    }

    private static List<String> titles(Inventory inventory) {
        List<String> titles = new ArrayList<>();

        for (int slot = 9; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);

            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                titles.add(ChatColor.stripColor(item.getItemMeta().getDisplayName()));
            }
        }

        return titles;
    }

    private static void registerGroupWithItem(String namespace, String key, String name, Material icon, String itemId) {
        ItemGroup group = group(namespace, key, name, icon);
        SlimefunItem item = TestUtilities.mockSlimefunItem(plugin, itemId, new CustomItemStack(Material.BAMBOO, "&6" + name + " item"));
        item.setItemGroup(group);
        item.register(plugin);
        item.load();
    }

    @Test
    @DisplayName("With themes disabled the main menu is unchanged; enabled it shows themes and the theme page lists its groups")
    void testMainMenu() throws InterruptedException {
        registerGroupWithItem("dynatech", "theme_test_machines", "ZZ Machines Group", Material.IRON_BLOCK, "THEME_TEST_ITEM_1");
        registerGroupWithItem("alchimiavitae", "theme_test_potions", "ZZ Potions Group", Material.BREWING_STAND, "THEME_TEST_ITEM_2");

        PlayerMock player = server.addPlayer();
        PlayerProfile profile = TestUtilities.awaitProfile(player);
        SurvivalSlimefunGuide guide = (SurvivalSlimefunGuide) Slimefun.getRegistry().getSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE);

        guide.openMainMenu(profile, 1);
        Inventory flat = player.getOpenInventory().getTopInventory();
        Assertions.assertEquals(1, count(flat, Material.IRON_BLOCK), "flat menu lists the addon group");
        Assertions.assertEquals(1, count(flat, Material.BREWING_STAND));
        Assertions.assertFalse(titles(flat).contains("Technology & Machines"));

        Slimefun.getCfg().setValue(GuideThemes.ENABLED_PATH, true);
        guide.openMainMenu(profile, 1);
        Inventory themedInv = player.getOpenInventory().getTopInventory();
        List<String> themed = titles(themedInv);
        Assertions.assertTrue(themed.contains("Technology & Machines"), themed.toString());
        Assertions.assertTrue(themed.contains("Magic & Arcana"), themed.toString());
        Assertions.assertTrue(themed.contains("All Categories (A-Z)"), themed.toString());
        Assertions.assertEquals(0, count(themedInv, Material.IRON_BLOCK), "addon groups are inside their theme now");
        Assertions.assertEquals(0, count(themedInv, Material.BREWING_STAND));

        ThemeItemGroup tech = null;

        for (ItemGroup entry : GuideThemes.get().toThemeEntries(new ArrayList<>(Slimefun.getRegistry().getAllItemGroups()))) {
            if (entry instanceof ThemeItemGroup theme && theme.getTheme().id().equals("technology")) {
                tech = theme;
            }
        }

        Assertions.assertNotNull(tech);
        guide.openGroupList(profile, tech, 1);
        Inventory inside = player.getOpenInventory().getTopInventory();
        Assertions.assertEquals(1, count(inside, Material.IRON_BLOCK), "the technology theme lists its group");
        Assertions.assertEquals(0, count(inside, Material.BREWING_STAND), "magic groups do not belong to the technology theme");
    }
}
