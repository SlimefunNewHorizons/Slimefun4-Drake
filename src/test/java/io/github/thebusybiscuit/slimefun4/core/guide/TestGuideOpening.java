package io.github.thebusybiscuit.slimefun4.core.guide;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import io.github.thebusybiscuit.slimefun4.implementation.guide.CheatSheetSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideBookmarks;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerPreResearchEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.test.TestUtilities;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.MenuListener;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestGuideOpening {

    private static ServerMock server;
    private static Slimefun plugin;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        // The test boot deliberately skips the production inventory listeners.
        new MenuListener(plugin);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @ParametersAreNonnullByDefault
    private @Nonnull PlayerProfile prepare(SlimefunGuideImplementation guide, Consumer<GuideHistory> consumer) throws InterruptedException {
        Player player = server.addPlayer();
        PlayerProfile profile = TestUtilities.awaitProfile(player);
        GuideHistory history = profile.getGuideHistory();
        consumer.accept(history);
        history.openLastEntry(guide);
        return profile;
    }

    @Test
    @DisplayName("Test if the Slimefun Guide Main Menu can be opened from the History")
    void testOpenMainMenu() throws InterruptedException {
        SlimefunGuideImplementation guide = Mockito.mock(SlimefunGuideImplementation.class);
        PlayerProfile profile = prepare(guide, history -> {});
        Mockito.verify(guide).openMainMenu(profile, 1);
    }

    @Test
    @DisplayName("Test if an ItemGroup can be opened from the History")
    void testOpenItemGroup() throws InterruptedException {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "history_itemgroup"), new CustomItemStack(Material.BLUE_TERRACOTTA, "&9Testy test"));

        SlimefunGuideImplementation guide = Mockito.mock(SlimefunGuideImplementation.class);
        PlayerProfile profile = prepare(guide, history -> history.add(itemGroup, 1));
        Mockito.verify(guide).openItemGroup(profile, itemGroup, 1);
    }

    @Test
    @DisplayName("Test if a SlimefunItem can be viewed from the History")
    void testOpenSlimefunItem() throws InterruptedException {
        SlimefunItem item = TestUtilities.mockSlimefunItem(plugin, "OPEN_SLIMEFUN_ITEM", new CustomItemStack(Material.PRISMARINE_SHARD, "&5Some Shard I guess"));

        SlimefunGuideImplementation guide = Mockito.mock(SlimefunGuideImplementation.class);
        PlayerProfile profile = prepare(guide, history -> history.add(item));
        Mockito.verify(guide).displayItem(profile, item, false);
    }

    @Test
    @DisplayName("Test if an ItemStack can be viewed from the History")
    void testOpenItemStack() throws InterruptedException {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);

        SlimefunGuideImplementation guide = Mockito.mock(SlimefunGuideImplementation.class);
        PlayerProfile profile = prepare(guide, history -> history.add(item, 1));
        Mockito.verify(guide).displayItem(profile, item, 1, false);
    }

    @Test
    @DisplayName("Test if the Slimefun Search works with normal and colored terms")
    void testOpenSearch_withColoredSearchTerm() throws InterruptedException {
        String normalTerm = "iron";
        String coloredTerm = ChatColor.DARK_PURPLE + "iron";

        SlimefunItem testItem = TestUtilities.mockSlimefunItem(plugin, "IRON_ITEM", new CustomItemStack(Material.IRON_INGOT, "iron item"));
        testItem.register(plugin);

        Player player = server.addPlayer();
        PlayerProfile profile = TestUtilities.awaitProfile(player);
        SlimefunGuideImplementation guide = new SurvivalSlimefunGuide(false, false);

        guide.openSearch(profile, normalTerm, false);
        // Assert we can open with a non-coloured search term
        Assertions.assertTrue(hasGuideItem(player, testItem), "Failed on normal query");

        guide.openSearch(profile, coloredTerm, false);
        // Assert we can open with a coloured search term
        Assertions.assertTrue(hasGuideItem(player, testItem), "Failed on colored query");
    }

    @Test
    @DisplayName("Test if the Slimefun Search can be opened from the History")
    void testOpenSearchHistory() throws InterruptedException {
        String term = "electric";

        SlimefunGuideImplementation guide = Mockito.mock(SlimefunGuideImplementation.class);
        PlayerProfile profile = prepare(guide, history -> history.add(term));
        Mockito.verify(guide).openSearch(profile, term, false);
    }

    @Test
    @DisplayName("Test if the Back button works")
    void testGoBack() throws InterruptedException {
        Player player = server.addPlayer();

        String query = "electric";
        String query2 = "cargo";

        SlimefunGuideImplementation guide = Mockito.mock(SlimefunGuideImplementation.class);

        PlayerProfile profile = TestUtilities.awaitProfile(player);
        GuideHistory history = profile.getGuideHistory();
        history.add(query);
        history.add(query2);
        history.goBack(guide);
        Mockito.verify(guide).openSearch(profile, query, false);
    }

    @Test
    void testSurvivalShowsIndirectResearchAndKeepsSavedTarget() throws InterruptedException {
        Player player = server.addPlayer();
        player.setLevel(8);
        PlayerProfile profile = TestUtilities.awaitProfile(player);
        var registry = Slimefun.getRegistry();
        boolean researching = registry.isResearchingEnabled();
        registry.setResearchingEnabled(true);
        try {
            SlimefunItem leaf = registerGuideItem("GUIDE_TEST_LEAF", Material.QUARTZ);
            SlimefunItem component = registerGuideItem("GUIDE_TEST_COMPONENT", Material.IRON_INGOT, leaf);
            SlimefunItem machine = registerGuideItem("GUIDE_TEST_MACHINE", Material.FURNACE, component, component);
            // Add a cycle to exercise traversal through recipes supplied by addons.
            leaf.getRecipe()[0] = machine.getItem();
            Research research = new Research(new NamespacedKey(plugin, "guide_test_leaf"), 970, "Guide Test Research", 5);
            research.addItems(leaf);
            research.register();

            var guide = new SurvivalSlimefunGuide(false, false, true);
            guide.displayItem(profile, machine, true);
            click(player, 2);
            Assertions.assertTrue(GuideBookmarks.get().contains(player.getUniqueId(), machine.getId()));
            click(player, 7);
            Assertions.assertSame(machine, SlimefunItem.getByItem(guideSlot(player, 9)));
            click(player, 9);
            click(player, 6);
            Assertions.assertEquals(Material.QUARTZ, guideSlot(player, 10).getType());
            Assertions.assertNull(guideSlot(player, 11), "The cycle and repeated component must not produce duplicate rows");
            Assertions.assertEquals(8, player.getLevel(), "Viewing prerequisites must not spend XP");

            click(player, 10);
            Assertions.assertEquals(Material.EXPERIENCE_BOTTLE, guideSlot(player, 17).getType());
            click(player, 17);
            server.getPluginManager().assertEventFired(PlayerPreResearchEvent.class, event ->
                event.getPlayer().equals(player) && event.getResearch().equals(research));
            Assertions.assertEquals(3, player.getLevel());
        } finally {
            registry.setResearchingEnabled(researching);
        }
    }

    @Test
    void testCreativePreviewRequiresExplicitClaimAndRechecksAccess() throws InterruptedException {
        Player player = server.addPlayer();
        PlayerProfile profile = TestUtilities.awaitProfile(player);
        SlimefunItem item = registerGuideItem("GUIDE_TEST_CREATIVE", Material.LAPIS_LAZULI);
        player.setOp(true);
        var guide = new CheatSheetSlimefunGuide(true);

        guide.openSearch(profile, item.getId(), true);
        click(player, 9);
        Assertions.assertFalse(player.getInventory().contains(Material.LAPIS_LAZULI));
        Assertions.assertEquals(Material.CHEST, guideSlot(player, 17).getType());
        Assertions.assertEquals(0, profile.getGuideHistory().size(), "Creative browsing must not replace survival history");

        player.setOp(false);
        click(player, 17);
        Assertions.assertFalse(player.getInventory().contains(Material.LAPIS_LAZULI));
        player.setOp(true);
        click(player, 17);
        Assertions.assertTrue(player.getInventory().contains(Material.LAPIS_LAZULI));
    }

    private static SlimefunItem registerGuideItem(String id, Material material, SlimefunItem... ingredients) {
        SlimefunItem item = TestUtilities.mockSlimefunItem(plugin, id, new CustomItemStack(material, id));
        for (int i = 0; i < ingredients.length; i++) {
            item.getRecipe()[i] = ingredients[i].getItem();
        }
        item.register(plugin);
        return item;
    }

    private static boolean hasGuideItem(Player player, SlimefunItem item) {
        return Arrays.stream(player.getOpenInventory().getTopInventory().getContents())
            .anyMatch(stack -> SlimefunItem.getByItem(stack) == item);
    }

    private static ItemStack guideSlot(Player player, int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }

    private static void click(Player player, int slot) {
        var event = new InventoryClickEvent(player.getOpenInventory(), SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        Assertions.assertTrue(event.isCancelled(), "Guide icons cannot be taken out of the menu");
    }

}
