package me.mrCookieSlime.CSCoreLibPlugin.general.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.core.services.PerfFlags;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler;

/**
 * An old {@link Listener} for CS-CoreLib
 * This is an old remnant of CS-CoreLib, the last bits of the past. They will be removed once everything is
 *             updated.
 */
public class MenuListener implements Listener {

    static final Map<UUID, ChestMenu> menus = new HashMap<>();

    public MenuListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        ChestMenu menu = menus.remove(e.getPlayer().getUniqueId());

        if (menu != null) {
            markDirty(menu);
            menu.getMenuCloseHandler().onClose((Player) e.getPlayer());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        ChestMenu menu = menus.get(e.getWhoClicked().getUniqueId());

        if (menu != null) {
            markDirty(menu);

            // COLLECT_TO_CURSOR (doble clic para apilar) recoge todo lo que coincida de la vista
            // ENTERA, menu incluido, y lo hace sin pasar por un solo MenuClickHandler: la accion
            // los salta por diseño. Si el doble clic se da en el inventario del jugador, abajo se
            // consulta al playerInventoryClickHandler, que dice que si -- y con razon, porque el
            // slot pulsado es suyo -- y entonces vanilla barre los slots protegidos del menu.
            //
            // Asi salieron del barril los telescopios sin nombre del 11-08: son iconos de la GUI.
            // Nadie lo hizo a mano; los mods de ordenado (Inventory Profiles Next y parecidos)
            // disparan doble clic constantemente para apilar, y cada uno era un barrido.
            //
            // Se cancela solo con un menu de Slimefun abierto. El coste es perder el apilado por
            // doble clic mientras hay una maquina delante; el resto del ordenado sigue igual, que
            // es lo que se queria conservar.
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }

            if (e.getRawSlot() < e.getInventory().getSize()) {
                MenuClickHandler handler = menu.getMenuClickHandler(e.getSlot());

                if (handler == null) {
                    e.setCancelled(!menu.isEmptySlotsClickable() && (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR));
                } else if (handler instanceof AdvancedMenuClickHandler) {
                    e.setCancelled(!((AdvancedMenuClickHandler) handler).onClick(e, (Player) e.getWhoClicked(), e.getSlot(), e.getCursor(), new ClickAction(e.isRightClick(), e.isShiftClick())));
                } else {
                    e.setCancelled(!handler.onClick((Player) e.getWhoClicked(), e.getSlot(), e.getCurrentItem(), new ClickAction(e.isRightClick(), e.isShiftClick())));
                }
            } else {
                if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && PerfFlags.shiftClickInput
                    && menu.getPlayerInventoryClickHandler().onClick((Player) e.getWhoClicked(), e.getSlot(), e.getCurrentItem(), new ClickAction(e.isRightClick(), e.isShiftClick()))
                    && shiftInsertIntoInputs(e, menu)) {
                    return;
                }

                e.setCancelled(!menu.getPlayerInventoryClickHandler().onClick((Player) e.getWhoClicked(), e.getSlot(), e.getCurrentItem(), new ClickAction(e.isRightClick(), e.isShiftClick())));
            }
        }
    }

    /**
     * Shift-click desde el inventario del jugador hacia una maquina Slimefun: vanilla repartia el stack por CUALQUIER
     * ranura libre del menu (salida, barra de progreso...). Aqui solo se usan las ranuras de entrada que el preset
     * declara para el item (mismas que usa la red de carga). Lo que no cabe se queda en el inventario del jugador.
     * Todo ocurre en este evento, ya cancelado, sobre clones: el cursor no se toca y no hay segunda escritura vanilla.
     *
     * @return true si el evento quedo gestionado; false para dejar el comportamiento vanilla (menu sin ranuras de
     *         entrada declaradas, p. ej. GUIs de addons que no usan el transporte de items)
     */
    private boolean shiftInsertIntoInputs(InventoryClickEvent e, ChestMenu chestMenu) {
        ItemStack clicked = e.getCurrentItem();

        if (!(chestMenu instanceof DirtyChestMenu menu) || menu.getPreset() == null || clicked == null || clicked.getType() == Material.AIR || e.getClickedInventory() == null) {
            return false;
        }

        int[] slots;
        try {
            slots = menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, ItemStackWrapper.wrap(clicked));
        } catch (RuntimeException | LinkageError x) {
            return false;
        }

        if (slots == null || slots.length == 0) {
            return false;
        }

        e.setCancelled(true);

        int remaining = clicked.getAmount();
        ItemStackWrapper wrapper = ItemStackWrapper.wrap(clicked);
        int menuMax = menu.toInventory().getMaxStackSize();

        // 1) completar stacks parciales del mismo item; 2) ranuras vacias, en el orden declarado por la maquina
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot : slots) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack inSlot = menu.getItemInSlot(slot);

                if (pass == 0 && inSlot != null && inSlot.getType() != Material.AIR) {
                    int max = Math.min(inSlot.getMaxStackSize(), menuMax);
                    if (inSlot.getAmount() < max && ItemUtils.canStack(wrapper, inSlot)) {
                        int moved = Math.min(max - inSlot.getAmount(), remaining);
                        ItemStack grown = inSlot.clone();
                        grown.setAmount(inSlot.getAmount() + moved);
                        menu.replaceExistingItem(slot, grown);
                        remaining -= moved;
                    }
                } else if (pass == 1 && (inSlot == null || inSlot.getType() == Material.AIR)) {
                    int moved = Math.min(Math.min(clicked.getMaxStackSize(), menuMax), remaining);
                    ItemStack placed = clicked.clone();
                    placed.setAmount(moved);
                    menu.replaceExistingItem(slot, placed);
                    remaining -= moved;
                }
            }
        }

        if (remaining != clicked.getAmount()) {
            menu.markDirty();
            // getCurrentItem es el espejo vivo del slot pulsado: reducir su cantidad es la unica escritura al jugador.
            clicked.setAmount(Math.max(remaining, 0));
            if (remaining <= 0) {
                e.setCurrentItem(null);
            }
        }

        return true;
    }

    /**
     * Un {@link InventoryDragEvent} reparte items sobre varios slots sin pasar
     * por {@link #onClick}, evadiendo todos los MenuClickHandler. La tecnica de
     * "slot drag" (mantener click + arrastrar) explotaba esto para insertar items
     * en slots bloqueados (display/plantilla) de barriles, storages y demas menus
     * — base de varios dupes conocidos. Aqui se cancela cualquier drag que toque
     * slots del menu top; el drag puramente dentro del inventario del jugador se permite.
     */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        ChestMenu menu = menus.get(e.getWhoClicked().getUniqueId());

        if (menu == null) {
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < topSize) {
                e.setCancelled(true);
                return;
            }
        }

        markDirty(menu);
    }

    private void markDirty(ChestMenu menu) {
        if (menu instanceof DirtyChestMenu dirtyMenu) {
            dirtyMenu.markDirty();
        }
    }

}
