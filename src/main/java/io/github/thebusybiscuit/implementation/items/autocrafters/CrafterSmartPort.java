package io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters;

import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * Smart Port: una "caja" con menú propio que se coloca bajo un {@link AbstractAutoCrafter}. El cargo le mete
 * ingredientes (con tope por tipo, según la receta elegida) y recoge el resultado, algo que un cofre no hace bien.
 * Idea y diseño de Xzavier0722 (SlimefunGuguProject/Slimefun4), reescritos sobre {@code BlockStorage}.
 */
public class CrafterSmartPort extends SlimefunItem {

    public static final int[] INPUT_SLOTS = { 0, 1, 2, 3, 4, 5, 9, 10, 11, 12, 13, 14, 18, 19, 20, 21, 22, 23, 27, 28, 29, 30, 31, 32, 36, 37, 38, 39, 40, 41, 45, 46, 47, 48, 49, 50 };
    public static final int[] OUTPUT_SLOTS = { 7, 8, 16, 17, 25, 26, 34, 35, 43, 44, 52, 53 };
    private static final int COUNT_SLOT = 6;

    public CrafterSmartPort(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        new BlockMenuPreset(getId(), "&aCrafter Smart Port") {

            @Override
            public void init() {
                addItem(COUNT_SLOT, countItem(1), (p, slot, stack, action) -> false);
                for (int i = 15; i < 54; i += 9) {
                    addItem(i, ChestMenuUtils.getBackground(), (p, slot, stack, action) -> false);
                }
            }

            @Override
            public boolean canOpen(@Nonnull Block b, @Nonnull Player p) {
                return p.hasPermission("slimefun.inventory.bypass") || Slimefun.getProtectionManager().hasPermission(p, b.getLocation(), io.github.bakedlibs.dough.protection.Interaction.INTERACT_BLOCK);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
                String stored = BlockStorage.getLocationInfo(b.getLocation(), "ingredientCount");
                if (stored != null) {
                    menu.replaceExistingItem(COUNT_SLOT, countItem(Math.max(1, Integer.parseInt(stored))));
                }
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return OUTPUT_SLOTS;
                }

                // Tope por tipo de ingrediente: los slots de entrada se reparten entre los ingredientes distintos de la receta
                ItemStackWrapper wrapper = ItemStackWrapper.wrap(item);
                int limit = INPUT_SLOTS.length / Math.max(1, menu.getItemInSlot(COUNT_SLOT).getAmount()) * wrapper.getMaxStackSize();
                int total = wrapper.getAmount();

                for (int slot : INPUT_SLOTS) {
                    ItemStack inSlot = menu.getItemInSlot(slot);
                    if (inSlot != null && SlimefunUtils.isItemSimilar(inSlot, wrapper, true, false) && (total += inSlot.getAmount()) > limit) {
                        return new int[0];
                    }
                }

                return INPUT_SLOTS;
            }
        };

        CrafterInteractorManager.register(getId(), menu -> asInteractor(menu));
    }

    private static ItemStack countItem(int count) {
        ItemStack stack = new CustomItemStack(Material.CLOCK, "&bIngredientes distintos", "&7Lo fija el autocrafter de arriba", "&7según la receta elegida.");
        stack.setAmount(Math.min(64, count));
        return stack;
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockBreakHandler(false, true) {

            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                BlockMenu inv = BlockStorage.getInventory(e.getBlock());
                if (inv != null) {
                    for (int slot : INPUT_SLOTS) {
                        ItemStack s = inv.getItemInSlot(slot);
                        if (s != null) drops.add(s);
                    }
                    for (int slot : OUTPUT_SLOTS) {
                        ItemStack s = inv.getItemInSlot(slot);
                        if (s != null) drops.add(s);
                    }
                }
            }
        });
    }

    // Un adaptador por consulta; el menú real lo da el registro

    private static CrafterInteractable asInteractor(BlockMenu menu) {
        return new CrafterInteractable() {

            @Override
            public int[] getInputSlots() {
                return INPUT_SLOTS;
            }

            @Override
            public ItemStack getItem(int slot) {
                return menu.getItemInSlot(slot);
            }

            @Override
            public void setAmount(int slot, int amount) {
                ItemStack s = menu.getItemInSlot(slot);
                if (s != null) {
                    s.setAmount(amount);
                    if (amount <= 0) {
                        menu.replaceExistingItem(slot, null);
                    }
                }
            }

            @Override
            public boolean canOutput(ItemStack item) {
                return menu.fits(item, OUTPUT_SLOTS);
            }

            @Override
            public boolean addOutput(ItemStack item) {
                return menu.pushItem(item, OUTPUT_SLOTS) == null;
            }

            @Override
            public void setIngredientCount(Block b, int count) {
                int c = Math.max(1, Math.min(64, count));
                String value = String.valueOf(c);
                // se llama en cada tick del autocrafter: solo se escribe si cambia
                if (value.equals(BlockStorage.getLocationInfo(b.getLocation(), "ingredientCount"))) {
                    return;
                }
                menu.replaceExistingItem(COUNT_SLOT, countItem(c));
                BlockStorage.addBlockInfo(b, "ingredientCount", value);
            }
        };
    }
}
