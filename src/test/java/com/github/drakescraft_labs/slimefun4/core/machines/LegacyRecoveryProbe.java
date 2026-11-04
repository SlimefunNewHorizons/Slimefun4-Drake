package com.github.drakescraft_labs.slimefun4.core.machines;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.drakescraft_labs.slimefun4.api.SlimefunAddon;
import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.implementation.items.electric.machines.enchanting.AutoEnchanter;
import com.github.drakescraft_labs.slimefun4.legacy.Objects.SlimefunItem.abstractItems.MachineRecipe;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;

/** Isolated runtime fixture. Never install on a server containing player data. */
public final class LegacyRecoveryProbe extends JavaPlugin implements SlimefunAddon {
    public static final class LateField {}

    public static final class Machine extends AutoEnchanter {
        private LateField lateField;
        public final Map<Block, MachineRecipe> processing = new HashMap<>();

        public Machine() {
            super(new ItemGroup(NamespacedKey.minecraft("legacy_probe"), new ItemStack(Material.BOOK)),
                new SlimefunItemStack("LEGACY_RECOVERY_PROBE", Material.ENCHANTING_TABLE, "Legacy recovery probe"),
                RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] { new ItemStack(Material.DIRT) });
            setCapacity(100);
            setEnergyConsumption(10);
            setProcessingSpeed(1);
        }
    }

    @Override
    public void onEnable() {
        Machine machine = new Machine();
        machine.register(this);
        getServer().getScheduler().runTaskLater(this, () -> {
            Block block = getServer().getWorlds().getFirst().getBlockAt(0, 64, 0);
            block.getChunk().load();
            if (!getConfig().getBoolean("queued")) {
                block.setType(Material.ENCHANTING_TABLE);
                BlockStorage.store(block, machine.getId());
                machine.processing.put(block, new MachineRecipe(100000,
                    new ItemStack[] { new ItemStack(Material.GOLD_INGOT) },
                    new ItemStack[] { new ItemStack(Material.GOLD_BLOCK, 3) }));
                getConfig().set("queued", true);
                saveConfig();
                getLogger().info("T552_QUEUED: one operation, three gold blocks");
            } else {
                BlockMenu menu = BlockStorage.getInventory(block);
                int total = 0;
                if (menu != null) {
                    for (int slot : machine.getOutputSlots()) {
                        ItemStack stack = menu.getItemInSlot(slot);
                        if (stack != null && stack.getType() == Material.GOLD_BLOCK) {
                            total += stack.getAmount();
                        }
                    }
                }
                getLogger().info(total == 3 ? "T552_PASS: exactly three persisted gold blocks"
                    : "T552_FAIL: expected three persisted gold blocks, got " + total);
            }
        }, 100L);
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/SlimefunNewHorizons/Slimefun4-Drake/issues";
    }
}
