package com.github.drakescraft_labs.slimefun4.integrations;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;

/**
 * This handles all integrations with {@link WorldEdit}.
 *
 * <p>WorldEdit only carries vanilla block state in a clipboard. Slimefun stores
 * machine data and inventories separately, so a successful edit over a
 * Slimefun location must always invalidate its old data. This deliberately does
 * not clone machines or their inventories, avoiding duplicated or corrupted
 * machine state.</p>
 * 
 * @author TheBusyBiscuit
 *
 */
class WorldEditIntegration {

    WorldEditIntegration() {
        try {
            // This ensures that we are using a version which supports Extents
            Class.forName("com.sk89q.worldedit.extent.Extent");
        } catch (ClassNotFoundException e) {
            // Re-throw the exception for the IntegrationsManager to catch
            throw new IllegalStateException(e);
        }
    }

    public void register() {
        WorldEdit.getInstance().getEventBus().register(this);
    }

    @Subscribe
    public void wrapForLogging(EditSessionEvent event) {
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {

            @Override
            public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 pos, T block) throws WorldEditException {
                boolean changed = getExtent().setBlock(pos, block);
                if (!changed) {
                    return false;
                }

                World world = Bukkit.getWorld(event.getWorld().getName());
                if (world != null) {
                    Location location = new Location(world, pos.x(), pos.y(), pos.z());
                    if (BlockStorage.hasBlockInfo(location)) {
                        BlockStorage.clearBlockInfo(location);
                    }
                }

                return true;
            }

        });
    }

}
