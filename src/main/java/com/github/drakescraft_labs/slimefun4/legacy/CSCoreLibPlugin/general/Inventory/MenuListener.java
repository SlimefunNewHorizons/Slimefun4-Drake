package me.mrCookieSlime.CSCoreLibPlugin.general.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.DirtyChestMenu;
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
                e.setCancelled(!menu.getPlayerInventoryClickHandler().onClick((Player) e.getWhoClicked(), e.getSlot(), e.getCurrentItem(), new ClickAction(e.isRightClick(), e.isShiftClick())));
            }
        }
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
