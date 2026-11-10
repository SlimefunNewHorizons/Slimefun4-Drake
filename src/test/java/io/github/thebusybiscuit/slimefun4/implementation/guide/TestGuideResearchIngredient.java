package io.github.thebusybiscuit.slimefun4.implementation.guide;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.test.TestUtilities;

/** Clic en un ingrediente bloqueado de la pagina de receta: investiga (XP) y vuelve a la misma receta. */
class TestGuideResearchIngredient {

    private static ServerMock server;
    private static Slimefun plugin;
    private static SlimefunItem owner;
    private static SlimefunItem ingredient;
    private static Research research;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        new me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.MenuListener(plugin); // en tests no se registra solo
        Slimefun.getRegistry().setResearchingEnabled(true);
        ItemGroup group = TestUtilities.getItemGroup(plugin, "guide_research");
        SlimefunItemStack ing = new SlimefunItemStack("GR_INGREDIENT", Material.EMERALD, "&aIngrediente");
        ingredient = new SlimefunItem(group, ing, RecipeType.NULL, new ItemStack[9]);
        ingredient.register(plugin);
        ItemStack[] recipe = new ItemStack[9];
        recipe[0] = ing;
        owner = new SlimefunItem(group, new SlimefunItemStack("GR_OWNER", Material.DIAMOND, "&bProducto"), RecipeType.NULL, recipe);
        owner.register(plugin);
        research = new Research(new NamespacedKey(plugin, "guide_research"), 987654, "Guide research", 3);
        research.addItems(ingredient);
        research.register();
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private boolean slotIsLocked(PlayerMock p) {
        ItemStack at = p.getOpenInventory().getTopInventory().getItem(3);
        return at != null && at.getType() == Material.BARRIER;
    }

    @Test
    @DisplayName("Sin XP suficiente el clic no investiga ni cobra")
    void notEnoughXp() throws Exception {
        PlayerMock p = server.addPlayer();
        PlayerProfile profile = TestUtilities.awaitProfile(p);
        p.setLevel(1);
        Slimefun.getRegistry().getSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE).displayItem(profile, owner, true);
        Assertions.assertTrue(slotIsLocked(p), "el ingrediente aparece bloqueado");

        p.simulateInventoryClick(p.getOpenInventory(), 3);

        Assertions.assertFalse(profile.hasUnlocked(research));
        Assertions.assertEquals(1, p.getLevel());
    }

    @Test
    @DisplayName("Con XP: cobra el coste habitual, aprende y la receta vuelve a mostrarse desbloqueada")
    void unlocksAndReturns() throws Exception {
        PlayerMock p = server.addPlayer();
        PlayerProfile profile = TestUtilities.awaitProfile(p);
        p.setLevel(5);
        Slimefun.getRegistry().getSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE).displayItem(profile, owner, true);
        Assertions.assertTrue(slotIsLocked(p));

        p.simulateInventoryClick(p.getOpenInventory(), 3);
        server.getScheduler().performTicks(400);

        Assertions.assertTrue(profile.hasUnlocked(research));
        Assertions.assertEquals(2, p.getLevel(), "coste 3 niveles");
        Assertions.assertFalse(slotIsLocked(p), "volvio a la misma receta con el ingrediente ya visible");
        Assertions.assertNotNull(p.getOpenInventory().getTopInventory().getItem(16), "es la pagina de receta de GR_OWNER");
    }
}
