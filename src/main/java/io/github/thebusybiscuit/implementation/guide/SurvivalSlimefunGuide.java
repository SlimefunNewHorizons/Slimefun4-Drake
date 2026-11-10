package io.github.thebusybiscuit.slimefun4.implementation.guide;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;

import io.github.bakedlibs.dough.chat.ChatInput;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.bakedlibs.dough.recipes.MinecraftRecipe;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.LockedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.core.services.CheatPolicy;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.AsyncRecipeChoiceTask;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedItemFlag;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.SlimefunGuideItem;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * The {@link SurvivalSlimefunGuide} is the standard version of our {@link SlimefunGuide}.
 * It uses an {@link Inventory} to display {@link SlimefunGuide} contents.
 *
 * @author TheBusyBiscuit
 * 
 * @see SlimefunGuide
 * @see SlimefunGuideImplementation
 * @see CheatSheetSlimefunGuide
 *
 */
public class SurvivalSlimefunGuide implements SlimefunGuideImplementation {

    private static final int MAX_ITEM_GROUPS = 36;
    private static final int MAX_RECIPE_PATH_ITEMS = 256;
    private static final Set<String> REPORTED_RECIPE_ERRORS = ConcurrentHashMap.newKeySet();

    private final int[] recipeSlots = { 3, 4, 5, 12, 13, 14, 21, 22, 23 };
    private final ItemStack item;
    private final boolean showVanillaRecipes;
    private final boolean showHiddenItemGroupsInSearch;
    private final boolean bookmarksEnabled;

    public SurvivalSlimefunGuide(boolean showVanillaRecipes, boolean showHiddenItemGroupsInSearch) {
        this(showVanillaRecipes, showHiddenItemGroupsInSearch, false);
    }

    public SurvivalSlimefunGuide(boolean showVanillaRecipes, boolean showHiddenItemGroupsInSearch, boolean bookmarksEnabled) {
        this.showVanillaRecipes = showVanillaRecipes;
        this.showHiddenItemGroupsInSearch = showHiddenItemGroupsInSearch;
        this.bookmarksEnabled = bookmarksEnabled;
        item = new SlimefunGuideItem(this, "&aSlimefun Guide &7(Chest GUI)");
    }

    @Override
    public @Nonnull SlimefunGuideMode getMode() {
        return SlimefunGuideMode.SURVIVAL_MODE;
    }

    @Override
    public @Nonnull ItemStack getItem() {
        return item;
    }

    protected final boolean isSurvivalMode() {
        return getMode() != SlimefunGuideMode.CHEAT_MODE;
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
    protected @Nonnull List<ItemGroup> getVisibleItemGroups(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        List<ItemGroup> groups = new LinkedList<>();

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            try {
                if (group instanceof FlexItemGroup flexItemGroup) {
                    if (flexItemGroup.isVisible(p, profile, getMode())) {
                        groups.add(group);
                    }
                } else if (!group.isHidden(p)) {
                    groups.add(group);
                }
            } catch (Exception | LinkageError x) {
                SlimefunAddon addon = group.getAddon();

                if (addon != null) {
                    addon.getLogger().log(Level.SEVERE, x, () -> "Could not display item group: " + group);
                } else {
                    Slimefun.logger().log(Level.SEVERE, x, () -> "Could not display item group: " + group);
                }
            }
        }

        return groups;
    }

    @Override
    public void openMainMenu(PlayerProfile profile, int page) {
        Player p = profile.getPlayer();

        if (!canBrowse(p)) {
            return;
        }

        GuideHistory history = history(profile);
        history.clear();
        history.setMainMenuPage(page);

        ChestMenu menu = create(p);
        List<ItemGroup> itemGroups = getVisibleItemGroups(p, profile);

        int index = 9;
        createHeader(p, profile, menu);

        int target = (MAX_ITEM_GROUPS * (page - 1)) - 1;

        while (target < (itemGroups.size() - 1) && index < MAX_ITEM_GROUPS + 9) {
            target++;

            ItemGroup group = itemGroups.get(target);
            showItemGroup(menu, p, profile, group, index);

            index++;
        }

        int pages = target == itemGroups.size() - 1 ? page : (itemGroups.size() - 1) / MAX_ITEM_GROUPS + 1;

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;

            if (next != page && next > 0) {
                openMainMenu(profile, next);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;

            if (next != page && next <= pages) {
                openMainMenu(profile, next);
            }

            return false;
        });

        menu.open(p);
    }

    private void showItemGroup(ChestMenu menu, Player p, PlayerProfile profile, ItemGroup group, int index) {
        if (!(group instanceof LockedItemGroup) || !isSurvivalMode() || ((LockedItemGroup) group).hasUnlocked(p, profile)) {
            menu.addItem(index, group.getItem(p));
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                openItemGroup(profile, group, 1);
                return false;
            });
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("");

            for (String line : Slimefun.getLocalization().getMessages(p, "guide.locked-itemgroup")) {
                lore.add(ChatColor.WHITE + line);
            }

            lore.add("");

            for (ItemGroup parent : ((LockedItemGroup) group).getParents()) {
                lore.add(parent.getItem(p).getItemMeta().getDisplayName());
            }

            menu.addItem(index, new CustomItemStack(Material.BARRIER, "&4" + Slimefun.getLocalization().getMessage(p, "guide.locked") + " &7- &f" + group.getItem(p).getItemMeta().getDisplayName(), lore.toArray(new String[0])));
            menu.addMenuClickHandler(index, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void openItemGroup(PlayerProfile profile, ItemGroup itemGroup, int page) {
        Player p = profile.getPlayer();

        if (!canBrowse(p)) {
            return;
        }

        if (itemGroup instanceof FlexItemGroup flexItemGroup) {
            flexItemGroup.open(p, profile, getMode());
            return;
        }

        history(profile).add(itemGroup, page);

        ChestMenu menu = create(p);
        createHeader(p, profile, menu);

        addBackButton(menu, 1, p, profile);

        int pages = (itemGroup.getItems().size() - 1) / MAX_ITEM_GROUPS + 1;

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;

            if (next != page && next > 0) {
                openItemGroup(profile, itemGroup, next);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;

            if (next != page && next <= pages) {
                openItemGroup(profile, itemGroup, next);
            }

            return false;
        });

        int index = 9;
        int itemGroupIndex = MAX_ITEM_GROUPS * (page - 1);

        for (int i = 0; i < MAX_ITEM_GROUPS; i++) {
            int target = itemGroupIndex + i;

            if (target >= itemGroup.getItems().size()) {
                break;
            }

            SlimefunItem sfitem = itemGroup.getItems().get(target);

            if (!sfitem.isDisabledIn(p.getWorld())) {
                displaySlimefunItem(menu, itemGroup, p, profile, sfitem, page, index);
                index++;
            }
        }

        menu.open(p);
    }

    private void displaySlimefunItem(ChestMenu menu, ItemGroup itemGroup, Player p, PlayerProfile profile, SlimefunItem sfitem, int page, int index) {
        menu.addItem(index, withAccessLore(p, profile, sfitem, decorateBookmarkedItem(p, sfitem)));
        menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
            try {
                if (!canBrowse(pl)) {
                    return false;
                }
                if (bookmarksEnabled && action.isRightClicked()) {
                    toggleBookmark(pl, sfitem);
                    openItemGroup(profile, itemGroup, page);
                } else {
                    displayItem(profile, sfitem, true);
                }
            } catch (Exception | LinkageError x) {
                printErrorMessage(pl, sfitem, x);
            }
            return false;
        });
    }

    @Override
    @ParametersAreNonnullByDefault
    public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
        openSearch(profile, input, addToHistory, 1);
    }

    private void openSearch(PlayerProfile profile, String input, boolean addToHistory, int page) {
        Player p = profile.getPlayer();

        if (!canBrowse(p)) {
            return;
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.search.inventory").replace("%item%", ChatUtils.crop(ChatColor.WHITE, input)));
        String searchTerm = ChatColor.stripColor(input.toLowerCase(Locale.ROOT));

        if (addToHistory) {
            history(profile).add(searchTerm);
        }

        menu.setEmptySlotsClickable(false);
        createHeader(p, profile, menu);
        addBackButton(menu, 1, p, profile);

        List<SlimefunItem> matches = new ArrayList<>();
        for (SlimefunItem candidate : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (!candidate.isHidden() && !candidate.isDisabledIn(p.getWorld()) && isItemGroupAccessible(p, candidate)
                && isSearchFilterApplicable(candidate, searchTerm)) {
                matches.add(candidate);
            }
        }
        int pages = Math.max(1, (matches.size() + MAX_ITEM_GROUPS - 1) / MAX_ITEM_GROUPS);
        int safePage = Math.min(page, pages);
        int start = (safePage - 1) * MAX_ITEM_GROUPS;
        for (int i = start; i < Math.min(start + MAX_ITEM_GROUPS, matches.size()); i++) {
            SlimefunItem slimefunItem = matches.get(i);
            int index = 9 + i - start;
            ItemStack itemstack = new CustomItemStack(decorateBookmarkedItem(p, slimefunItem), meta -> {
                ItemGroup itemGroup = slimefunItem.getItemGroup();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.WHITE + itemGroup.getDisplayName(p));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            });

            menu.addItem(index, withAccessLore(p, profile, slimefunItem, itemstack));
            menu.addMenuClickHandler(index, (pl, slot, itm, action) -> {
                try {
                    if (!canBrowse(pl)) {
                        return false;
                    }
                    if (bookmarksEnabled && action.isRightClicked()) {
                        toggleBookmark(pl, slimefunItem);
                        openSearch(profile, input, false, safePage);
                    } else {
                        displayItem(profile, slimefunItem, true);
                    }
                } catch (Exception | LinkageError x) {
                    printErrorMessage(pl, slimefunItem, x);
                }

                return false;
            });

        }
        if (matches.isEmpty()) {
            menu.addItem(22, new CustomItemStack(Material.PAPER, "&7No items match this search"), ChestMenuUtils.getEmptyClickHandler());
        }
        addPageButtons(menu, p, safePage, pages, next -> openSearch(profile, input, false, next));
        menu.open(p);
    }

    @ParametersAreNonnullByDefault
    private boolean isItemGroupAccessible(Player p, SlimefunItem slimefunItem) {
        return showHiddenItemGroupsInSearch || slimefunItem.getItemGroup().isAccessible(p);
    }

    @ParametersAreNonnullByDefault
    private boolean isSearchFilterApplicable(SlimefunItem slimefunItem, String searchTerm) {
        String itemName = ChatColor.stripColor(slimefunItem.getItemName()).toLowerCase(Locale.ROOT);
        return itemName.contains(searchTerm) || slimefunItem.getId().toLowerCase(Locale.ROOT).contains(searchTerm);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, ItemStack item, int index, boolean addToHistory) {
        Player p = profile.getPlayer();

        if (!canBrowse(p) || item == null || item.getType() == Material.AIR) {
            return;
        }

        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        if (sfItem != null) {
            displayItem(profile, sfItem, addToHistory);
            return;
        }

        if (!showVanillaRecipes) {
            return;
        }

        Recipe[] recipes = Slimefun.getMinecraftRecipeService().getRecipesFor(item);

        if (recipes.length == 0) {
            return;
        }

        showMinecraftRecipe(recipes, index, item, profile, p, addToHistory);
    }

    private void showMinecraftRecipe(Recipe[] recipes, int index, ItemStack item, PlayerProfile profile, Player p, boolean addToHistory) {
        Recipe recipe = recipes[index];

        ItemStack[] recipeItems = new ItemStack[9];
        RecipeType recipeType = RecipeType.NULL;
        ItemStack result = null;

        Optional<MinecraftRecipe<? super Recipe>> optional = MinecraftRecipe.of(recipe);
        AsyncRecipeChoiceTask task = new AsyncRecipeChoiceTask();

        if (optional.isPresent()) {
            showRecipeChoices(recipe, recipeItems, task);

            recipeType = new RecipeType(optional.get());
            result = recipe.getResult();
        } else {
            recipeItems = new ItemStack[] { null, null, null, null, new CustomItemStack(Material.BARRIER, "&4We are somehow unable to show you this Recipe :/"), null, null, null, null };
        }

        ChestMenu menu = create(p);

        if (addToHistory) {
            history(profile).add(item, index);
        }

        displayItem(menu, profile, p, item, result, recipeType, recipeItems, task);

        if (recipes.length > 1) {
            for (int i = 27; i < 36; i++) {
                menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }

            menu.addItem(28, ChestMenuUtils.getPreviousButton(p, index + 1, recipes.length), (pl, slot, action, stack) -> {
                if (index > 0) {
                    showMinecraftRecipe(recipes, index - 1, item, profile, p, true);
                }
                return false;
            });

            menu.addItem(34, ChestMenuUtils.getNextButton(p, index + 1, recipes.length), (pl, slot, action, stack) -> {
                if (index < recipes.length - 1) {
                    showMinecraftRecipe(recipes, index + 1, item, profile, p, true);
                }
                return false;
            });
        }

        menu.open(p);

        if (!task.isEmpty()) {
            task.start(menu.toInventory());
        }
    }

    private <T extends Recipe> void showRecipeChoices(T recipe, ItemStack[] recipeItems, AsyncRecipeChoiceTask task) {
        RecipeChoice[] choices = Slimefun.getMinecraftRecipeService().getRecipeShape(recipe);

        if (choices.length == 1 && choices[0] instanceof MaterialChoice materialChoice) {
            recipeItems[4] = new ItemStack(materialChoice.getChoices().get(0));

            if (materialChoice.getChoices().size() > 1) {
                task.add(recipeSlots[4], materialChoice);
            }
        } else {
            for (int i = 0; i < choices.length; i++) {
                if (choices[i] instanceof MaterialChoice materialChoice) {
                    recipeItems[i] = new ItemStack(materialChoice.getChoices().get(0));

                    if (materialChoice.getChoices().size() > 1) {
                        task.add(recipeSlots[i], materialChoice);
                    }
                }
            }
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory) {
        Player p = profile.getPlayer();

        if (!canBrowse(p) || !canViewRecipe(p, item)) {
            return;
        }

        ChestMenu menu = create(p);
        Optional<String> wiki = item.getWikipage();

        if (wiki.isPresent()) {
            menu.addItem(8, new CustomItemStack(Material.KNOWLEDGE_BOOK, ChatColor.WHITE + Slimefun.getLocalization().getMessage(p, "guide.tooltips.wiki"), "", ChatColor.GRAY + "\u21E8 " + ChatColor.GREEN + Slimefun.getLocalization().getMessage(p, "guide.tooltips.open-itemgroup")));
            menu.addMenuClickHandler(8, (pl, slot, itemstack, action) -> {
                pl.closeInventory();
                ChatUtils.sendURL(pl, wiki.get());
                return false;
            });
        }

        AsyncRecipeChoiceTask task = new AsyncRecipeChoiceTask();

        if (addToHistory) {
            history(profile).add(item);
        }

        ItemStack result = item.getRecipeOutput();
        RecipeType recipeType = item.getRecipeType();
        ItemStack[] recipe = item.getRecipe();

        displayItem(menu, profile, p, item, result, recipeType, recipe, task);

        addRecipeActions(menu, profile, p, item);

        if (item instanceof RecipeDisplayItem recipeDisplayItem) {
            displayRecipes(p, profile, menu, recipeDisplayItem, 0);
        }

        menu.open(p);

        if (!task.isEmpty()) {
            task.start(menu.toInventory());
        }
    }

    private void displayItem(ChestMenu menu, PlayerProfile profile, Player p, Object item, ItemStack output, RecipeType recipeType, ItemStack[] recipe, AsyncRecipeChoiceTask task) {
        addBackButton(menu, 0, p, profile);

        boolean isSlimefunRecipe = item instanceof SlimefunItem;

        for (int i = 0; i < Math.min(recipeSlots.length, recipe.length); i++) {
            ItemStack ingredient = recipe[i];
            SlimefunItem dependency = isSlimefunRecipe ? SlimefunItem.getByItem(ingredient) : null;
            ItemStack recipeItem = dependency == null ? ingredient : canViewRecipe(p, dependency)
                ? withAccessLore(p, profile, dependency, ingredient)
                : new CustomItemStack(Material.BARRIER, "&cUnavailable ingredient", access(p, profile, dependency));
            menu.addItem(recipeSlots[i], recipeItem, (pl, slot, stack, action) -> {
                try {
                    // The displayed stack may carry guide lore or be cycled by the recipe task.
                    if (dependency != null) {
                        displayItem(profile, dependency, true);
                    } else if (stack != null && stack.getType() != Material.BARRIER) {
                        displayItem(profile, stack, 0, true);
                    }
                } catch (Exception | LinkageError x) {
                    printErrorMessage(pl, x);
                }
                return false;
            });

            if (recipeItem != null && item instanceof MultiBlockMachine) {
                for (Tag<Material> tag : MultiBlock.getSupportedTags()) {
                    if (tag.isTagged(recipeItem.getType())) {
                        task.add(recipeSlots[i], tag);
                        break;
                    }
                }
            }
        }

        menu.addItem(10, recipeType.getItem(p), ChestMenuUtils.getEmptyClickHandler());
        menu.addItem(16, output, ChestMenuUtils.getEmptyClickHandler());
    }

    @ParametersAreNonnullByDefault
    public void createHeader(Player p, PlayerProfile profile, ChestMenu menu) {
        Validate.notNull(p, "The Player cannot be null!");
        Validate.notNull(profile, "The Profile cannot be null!");
        Validate.notNull(menu, "The Inventory cannot be null!");

        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        // Settings Panel
        menu.addItem(1, ChestMenuUtils.getMenuButton(p));
        menu.addMenuClickHandler(1, (pl, slot, item, action) -> {
            SlimefunGuideSettings.openSettings(pl, pl.getInventory().getItemInMainHand());
            return false;
        });

        // Search feature!
        menu.addItem(7, ChestMenuUtils.getSearchButton(p));
        menu.addMenuClickHandler(7, (pl, slot, item, action) -> {
            pl.closeInventory();

            Slimefun.getLocalization().sendMessage(pl, "guide.search.message");
            ChatInput.waitForPlayer(Slimefun.instance(), pl, msg -> SlimefunGuide.openSearch(profile, msg, getMode(), true));

            return false;
        });

        menu.addItem(3, new CustomItemStack(Material.BOOK, isSurvivalMode() ? "&aSurvival Guide" : "&cCreative Catalog",
            "&7Click to return to categories."), (pl, slot, stack, action) -> {
                openMainMenu(profile, 1);
                return false;
            });
        addFavoritesButton(menu, p, profile, 5);

        for (int i = 45; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
    }

    private void openBookmarks(@Nonnull PlayerProfile profile, int page) {
        Player p = profile.getPlayer();

        if (!canBrowse(p) || !bookmarksEnabled) {
            return;
        }

        List<SlimefunItem> bookmarkedItems = new ArrayList<>();
        for (String id : GuideBookmarks.get().getBookmarks(p.getUniqueId())) {
            SlimefunItem sfItem = SlimefunItem.getById(id);
            if (sfItem != null && !sfItem.isDisabledIn(p.getWorld()) && !sfItem.isHidden() && isItemGroupAccessible(p, sfItem)) {
                bookmarkedItems.add(sfItem);
            }
        }

        // Group favorites (e.g. bookmarked FlexItemGroup menus) are resolved dynamically against
        // the registry, so a group removed by a disabled/updated addon simply drops off the list.
        List<ItemGroup> bookmarkedGroups = new ArrayList<>();
        for (ItemGroup group : GuideBookmarks.get().getResolvedGroupBookmarks(p.getUniqueId())) {
            if (!(group instanceof FlexItemGroup flexItemGroup) || flexItemGroup.isVisible(p, profile, getMode())) {
                bookmarkedGroups.add(group);
            }
        }

        int entries = bookmarkedItems.size() + bookmarkedGroups.size();
        int pages = Math.max(1, (entries - 1) / MAX_ITEM_GROUPS + 1);
        int safePage = Math.max(1, Math.min(page, pages));
        ChestMenu menu = new ChestMenu(ChatColor.GOLD + "" + ChatColor.BOLD + "Favoritos de Slimefun");
        menu.setEmptySlotsClickable(false);
        createHeader(p, profile, menu);
        addBackButton(menu, 1, p, profile);
        menu.addItem(5, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        if (entries == 0) {
            menu.addItem(22, new CustomItemStack(Material.PAPER, "&7No favorites here",
                "&7Open a recipe and click its star to save it."), ChestMenuUtils.getEmptyClickHandler());
        }

        int start = MAX_ITEM_GROUPS * (safePage - 1);
        for (int index = 0; index < MAX_ITEM_GROUPS && start + index < entries; index++) {
            int target = start + index;
            int slot = index + 9;

            if (target < bookmarkedItems.size()) {
                SlimefunItem sfItem = bookmarkedItems.get(target);
                menu.addItem(slot, withAccessLore(p, profile, sfItem, decorateBookmarkedItem(p, sfItem)));
                menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                    if (!canBrowse(pl)) {
                        return false;
                    }
                    if (action.isRightClicked()) {
                        toggleBookmark(pl, sfItem);
                        openBookmarks(profile, safePage);
                    } else {
                        displayItem(profile, sfItem, true);
                    }
                    return false;
                });
            } else {
                ItemGroup group = bookmarkedGroups.get(target - bookmarkedItems.size());
                menu.addItem(slot, decorateBookmarkedGroup(p, group));
                menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                    if (!canBrowse(pl)) {
                        return false;
                    }
                    if (action.isRightClicked()) {
                        GuideBookmarks.get().toggleGroup(pl.getUniqueId(), group.getKey().toString());
                        openBookmarks(profile, safePage);
                    } else {
                        openItemGroup(profile, group, 1);
                    }
                    return false;
                });
            }
        }

        addPageButtons(menu, p, safePage, pages, next -> openBookmarks(profile, next));
        menu.open(p);
    }

    private @Nonnull ItemStack decorateBookmarkedGroup(@Nonnull Player player, @Nonnull ItemGroup group) {
        return new CustomItemStack(group.getItem(player), meta -> {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD + "★ Grupo guardado en favoritos");
            lore.add(ChatColor.GRAY + "Clic derecho para quitarlo");
            meta.setLore(lore);
        });
    }

    private @Nonnull ItemStack decorateBookmarkedItem(@Nonnull Player player, @Nonnull SlimefunItem sfItem) {
        if (!bookmarksEnabled) {
            return sfItem.getItem();
        }

        boolean bookmarked = GuideBookmarks.get().contains(player.getUniqueId(), sfItem.getId());
        return new CustomItemStack(sfItem.getItem(), meta -> {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(bookmarked ? ChatColor.GOLD + "★ Guardado en favoritos" : ChatColor.YELLOW + "Clic derecho: guardar favorito");
            if (bookmarked) {
                lore.add(ChatColor.GRAY + "Clic derecho para quitarlo");
            }
            meta.setLore(lore);
        });
    }

    private void toggleBookmark(@Nonnull Player player, @Nonnull SlimefunItem sfItem) {
        boolean added = GuideBookmarks.get().toggle(player.getUniqueId(), sfItem.getId());
        String itemName = ChatColor.stripColor(sfItem.getItemName());
        player.sendMessage(added
            ? ChatColor.GOLD + "★ " + ChatColor.WHITE + itemName + ChatColor.GOLD + " guardado en favoritos."
            : ChatColor.YELLOW + itemName + " fue eliminado de favoritos.");
    }

    private void addBackButton(ChestMenu menu, int slot, Player p, PlayerProfile profile) {
        GuideHistory history = history(profile);

        if (history.size() > 1) {
            menu.addItem(slot, new CustomItemStack(ChestMenuUtils.getBackButton(p, "", "&fLeft Click: &7Go back to previous Page", "&fShift + left Click: &7Go back to Main Menu")));

            menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                if (action.isShiftClicked()) {
                    openMainMenu(profile, history(profile).getMainMenuPage());
                } else {
                    history.goBack(this);
                }
                return false;
            });

        } else {
            menu.addItem(slot, new CustomItemStack(ChestMenuUtils.getBackButton(p, "", ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.guide"))));
            menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                openMainMenu(profile, history(profile).getMainMenuPage());
                return false;
            });
        }
    }

    private GuideHistory history(PlayerProfile profile) {
        return isSurvivalMode() ? profile.getGuideHistory() : profile.getLaboratoryGuideHistory();
    }

    private boolean canBrowse(Player player) {
        return player != null && Slimefun.getWorldSettingsService().isWorldEnabled(player.getWorld())
            && (isSurvivalMode() || CheatPolicy.canUseCheat(player));
    }

    private boolean canViewRecipe(Player player, SlimefunItem item) {
        return !item.isDisabledIn(player.getWorld()) && isItemGroupAccessible(player, item);
    }

    private boolean isCategoryLocked(Player player, PlayerProfile profile, SlimefunItem item) {
        return item.getItemGroup() instanceof LockedItemGroup group && !group.hasUnlocked(player, profile);
    }

    private String access(Player player, PlayerProfile profile, SlimefunItem item) {
        if (item.isDisabledIn(player.getWorld())) {
            return "&cUnavailable in this world";
        }
        if (!isSurvivalMode()) {
            return "&ePreview only · creative catalog";
        }
        if (!hasPermission(player, item) || !isItemGroupAccessible(player, item)) {
            return "&cPermission required";
        }
        if (isCategoryLocked(player, profile, item)) {
            return "&cUnlock the " + item.getItemGroup().getDisplayName(player) + " category first";
        }
        Research research = item.getResearch();
        if (!profile.hasUnlocked(research)) {
            return "&6Research: " + research.getName(player) + " &7(" + research.getCost() + " levels)";
        }
        return "&aResearch ready";
    }

    private ItemStack withAccessLore(Player player, PlayerProfile profile, SlimefunItem item, ItemStack icon) {
        return new CustomItemStack(icon, meta -> {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', access(player, profile, item)));
            meta.setLore(lore);
        });
    }

    private void addFavoritesButton(ChestMenu menu, Player player, PlayerProfile profile, int slot) {
        if (bookmarksEnabled) {
            int count = GuideBookmarks.get().size(player.getUniqueId()) + GuideBookmarks.get().groupSize(player.getUniqueId());
            menu.addItem(slot, new CustomItemStack(Material.NETHER_STAR, "&6Favorites &7(" + count + ")",
                "&7Open saved items and categories."), (pl, index, stack, action) -> {
                    history(profile).addPage(() -> openBookmarks(profile, 1));
                    openBookmarks(profile, 1);
                    return false;
                });
        }
    }

    private void addRecipeActions(ChestMenu menu, PlayerProfile profile, Player player, SlimefunItem item) {
        menu.addItem(6, new CustomItemStack(Material.COMPASS, isSurvivalMode() ? "&eUnlock Path" : "&eIngredient Tree",
            "&7Trace Slimefun ingredients across recipes.", access(player, profile, item)), (pl, slot, stack, action) -> {
                history(profile).addPage(() -> openRecipePath(profile, item, 1));
                openRecipePath(profile, item, 1);
                return false;
            });

        if (bookmarksEnabled) {
            boolean saved = GuideBookmarks.get().contains(player.getUniqueId(), item.getId());
            menu.addItem(2, new CustomItemStack(Material.NETHER_STAR, saved ? "&6Remove from Favorites" : "&eAdd to Favorites"),
                (pl, slot, stack, action) -> {
                    if (canBrowse(pl) && canViewRecipe(pl, item)) {
                        toggleBookmark(pl, item);
                        displayItem(profile, item, false);
                    }
                    return false;
                });
            addFavoritesButton(menu, player, profile, 7);
        }

        if (!isSurvivalMode()) {
            boolean claimable = !(item instanceof MultiBlockMachine) && CheatPolicy.canClaim(player, item);
            menu.addItem(17, new CustomItemStack(claimable ? Material.CHEST : Material.BARRIER,
                claimable ? "&cClaim test item" : "&7Item cannot be claimed", "&7Click: item · shift-click: stack"),
                (pl, slot, stack, action) -> {
                    if (canBrowse(pl) && canViewRecipe(pl, item) && !(item instanceof MultiBlockMachine)) {
                        CheatPolicy.claim(pl, item, action.isShiftClicked());
                    }
                    return false;
                });
        } else if (!profile.hasUnlocked(item.getResearch()) && hasPermission(player, item)
            && !isCategoryLocked(player, profile, item)) {
            menu.addItem(17, new CustomItemStack(Material.EXPERIENCE_BOTTLE, "&aUnlock Research",
                access(player, profile, item), "&7Click to confirm."), (pl, slot, stack, action) -> {
                    Research research = item.getResearch();
                    if (canBrowse(pl) && canViewRecipe(pl, item) && hasPermission(pl, item)
                        && !isCategoryLocked(pl, profile, item) && research != null) {
                        research.unlockFromGuide(this, pl, profile, item, ignored -> displayItem(profile, item, false));
                    }
                    return false;
                });
        }
    }

    private void openRecipePath(PlayerProfile profile, SlimefunItem target, int page) {
        Player player = profile.getPlayer();
        if (!canBrowse(player) || !canViewRecipe(player, target)) {
            return;
        }

        GuideRecipePath path = GuideRecipePath.collect(target, MAX_RECIPE_PATH_ITEMS,
            item -> !item.isHidden() && canViewRecipe(player, item));
        List<GuideRecipePath.Step> steps = new ArrayList<>();
        for (GuideRecipePath.Step step : path.steps()) {
            SlimefunItem item = step.item();
            if (!isSurvivalMode() || step.depth() == 0 || !canViewRecipe(player, item)
                || !hasPermission(player, item) || isCategoryLocked(player, profile, item)
                || !profile.hasUnlocked(item.getResearch())) {
                steps.add(step);
            }
        }

        int pages = Math.max(1, (steps.size() + MAX_ITEM_GROUPS - 1) / MAX_ITEM_GROUPS);
        int safePage = Math.min(page, pages);
        ChestMenu menu = new ChestMenu(isSurvivalMode() ? "Slimefun · Unlock Path" : "Slimefun · Ingredient Tree");
        menu.setEmptySlotsClickable(false);
        createHeader(player, profile, menu);
        addBackButton(menu, 1, player, profile);
        menu.addItem(4, new CustomItemStack(Material.COMPASS, target.getItemName(),
            isSurvivalMode() ? "&7Ingredient blockers: &f" + (steps.size() - 1) : "&7Slimefun ingredients: &f" + (steps.size() - 1),
            path.truncated() ? "&eSome dependencies were not scanned." : "&7Uses the main recipe of each item."),
            ChestMenuUtils.getEmptyClickHandler());

        for (int i = 0; i < MAX_ITEM_GROUPS; i++) {
            int index = (safePage - 1) * MAX_ITEM_GROUPS + i;
            if (index >= steps.size()) {
                break;
            }
            GuideRecipePath.Step step = steps.get(index);
            SlimefunItem item = step.item();
            String depth = step.depth() == 0 ? "&bTarget" : "&7Ingredient depth: " + step.depth();
            if (canViewRecipe(player, item) && (step.depth() == 0 || !item.isHidden())) {
                menu.addItem(9 + i, new CustomItemStack(item.getItem(), item.getItemName(), depth,
                    access(player, profile, item), "&eClick to inspect"), (pl, slot, stack, action) -> {
                        if (step.depth() == 0) {
                            history(profile).goBack(this);
                        } else {
                            displayItem(profile, item, true);
                        }
                        return false;
                    });
            } else {
                menu.addItem(9 + i, new CustomItemStack(Material.BARRIER, "&cUnavailable ingredient", depth,
                    access(player, profile, item)), ChestMenuUtils.getEmptyClickHandler());
            }
        }

        addPageButtons(menu, player, safePage, pages, next -> openRecipePath(profile, target, next));
        menu.open(player);
    }

    private void addPageButtons(ChestMenu menu, Player player, int page, int pages, IntConsumer open) {
        menu.addItem(46, ChestMenuUtils.getPreviousButton(player, page, pages), (pl, slot, stack, action) -> {
            if (page > 1) {
                open.accept(page - 1);
            }
            return false;
        });
        menu.addItem(52, ChestMenuUtils.getNextButton(player, page, pages), (pl, slot, stack, action) -> {
            if (page < pages) {
                open.accept(page + 1);
            }
            return false;
        });
    }

    @ParametersAreNonnullByDefault
    private void displayRecipes(Player p, PlayerProfile profile, ChestMenu menu, RecipeDisplayItem sfItem, int page) {
        final List<ItemStack> recipes;
        try {
            recipes = sfItem.getDisplayRecipes();
        } catch (RuntimeException | LinkageError ex) {
            displayBrokenRecipe(p, menu, sfItem, ex);
            return;
        }

        if (recipes != null && !recipes.isEmpty()) {
            menu.addItem(53, null);

            if (page == 0) {
                for (int i = 27; i < 36; i++) {
                    menu.replaceExistingItem(i, new CustomItemStack(ChestMenuUtils.getBackground(), sfItem.getRecipeSectionLabel(p)));
                    menu.addMenuClickHandler(i, ChestMenuUtils.getEmptyClickHandler());
                }
            }

            int pages = (recipes.size() - 1) / 18 + 1;

            menu.replaceExistingItem(28, ChestMenuUtils.getPreviousButton(p, page + 1, pages));
            menu.addMenuClickHandler(28, (pl, slot, itemstack, action) -> {
                if (page > 0) {
                    displayRecipes(pl, profile, menu, sfItem, page - 1);
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                }

                return false;
            });

            menu.replaceExistingItem(34, ChestMenuUtils.getNextButton(p, page + 1, pages));
            menu.addMenuClickHandler(34, (pl, slot, itemstack, action) -> {
                if (recipes.size() > (18 * (page + 1))) {
                    displayRecipes(pl, profile, menu, sfItem, page + 1);
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                }

                return false;
            });

            int inputs = 36;
            int outputs = 45;

            for (int i = 0; i < 18; i++) {
                int slot;

                if (i % 2 == 0) {
                    slot = inputs;
                    inputs++;
                } else {
                    slot = outputs;
                    outputs++;
                }

                addDisplayRecipe(menu, profile, sfItem, recipes, slot, i, page);
            }
        }
    }

    /** Keeps a malformed addon recipe from breaking the guide for the whole player session. */
    @ParametersAreNonnullByDefault
    private void displayBrokenRecipe(Player p, ChestMenu menu, RecipeDisplayItem sfItem, Throwable error) {
        for (int slot = 27; slot < 54; slot++) {
            menu.replaceExistingItem(slot, null);
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
        }

        menu.replaceExistingItem(40, new CustomItemStack(
            Material.BARRIER,
            "&cRecipe unavailable",
            "&7This item has an invalid recipe.",
            "&7The rest of the guide remains usable."
        ));
        menu.addMenuClickHandler(40, ChestMenuUtils.getEmptyClickHandler());

        reportRecipeError(sfItem, "Recipe display failed while viewed by " + p.getName(), error);
    }

    private void addDisplayRecipe(ChestMenu menu, PlayerProfile profile, RecipeDisplayItem sfItem, List<ItemStack> recipes, int slot, int i, int page) {
        try {
            if ((i + (page * 18)) < recipes.size()) {
                ItemStack displayItem = recipes.get(i + (page * 18));

                /*
                 * We want to clone this item to avoid corrupting the original
                 * but we wanna make sure no stupid addon creator sneaked some nulls in here
                 */
                if (displayItem != null) {
                    displayItem = displayItem.clone();
                }

                menu.replaceExistingItem(slot, displayItem);

                menu.addMenuClickHandler(slot, (pl, s, itemstack, action) -> {
                    if (itemstack != null && itemstack.getType() != org.bukkit.Material.AIR) {
                        displayItem(profile, itemstack, 0, true);
                    }
                    return false;
                });
            } else {
                menu.replaceExistingItem(slot, null);
                menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
            }
        } catch (RuntimeException | LinkageError ex) {
            menu.replaceExistingItem(slot, null);
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
            reportRecipeError(sfItem, "Invalid item in display recipe slot " + slot, ex);
        }
    }

    /** Records each broken recipe signature once to prevent player-triggered console floods. */
    private static void reportRecipeError(RecipeDisplayItem item, String context, Throwable error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String signature = item.getClass().getName() + '|' + error.getClass().getName() + '|' + message;
        if (REPORTED_RECIPE_ERRORS.add(signature)) {
            Slimefun.logger().log(Level.SEVERE, context + " for " + item.getClass().getName(), error);
        }
    }

    @ParametersAreNonnullByDefault
    private static boolean hasPermission(Player p, SlimefunItem item) {
        return Slimefun.getPermissionsService().hasPermission(p, item);
    }

    private @Nonnull ChestMenu create(@Nonnull Player p) {
        ChestMenu menu = new ChestMenu(isSurvivalMode()
            ? Slimefun.getLocalization().getMessage(p, "guide.title.main") : "&8Slimefun · Creative Catalog");

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        return menu;
    }

    @ParametersAreNonnullByDefault
    private void printErrorMessage(Player p, Throwable x) {
        p.sendMessage(ChatColor.DARK_RED + "An internal server error has occurred. Please inform an admin, check the console for further info.");
        Slimefun.logger().log(Level.SEVERE, "An error has occurred while trying to open a SlimefunItem in the guide!", x);
    }

    @ParametersAreNonnullByDefault
    private void printErrorMessage(Player p, SlimefunItem item, Throwable x) {
        p.sendMessage(ChatColor.DARK_RED + "An internal server error has occurred. Please inform an admin, check the console for further info.");
        item.error("This item has caused an error message to be thrown while viewing it in the Slimefun guide.", x);
    }

}
