package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.util.Map;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

class GuideCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    GuideCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "guide", false);
    }

    @Override
    public void onExecute(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            if (sender.hasPermission("slimefun.command.guide")) {
                boolean wantsOpen = args.length > 1 && ("open".equalsIgnoreCase(args[1]) || "abrir".equalsIgnoreCase(args[1]));
                boolean hasGuide = false;
                for (ItemStack current : player.getInventory().getContents()) {
                    if (SlimefunGuide.isGuideItem(current)) {
                        hasGuide = true;
                        break;
                    }
                }

                if (wantsOpen || hasGuide) {
                    SlimefunGuide.openGuide(player, SlimefunGuideMode.SURVIVAL_MODE);
                    return;
                }

                SlimefunGuideMode design = SlimefunGuide.getDefaultMode();
                ItemStack guideItem = SlimefunGuide.getItem(design).clone();
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(guideItem);
                if (!leftovers.isEmpty()) {
                    for (ItemStack item : leftovers.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                    player.sendMessage(ChatColors.color("&a&lSlimefun &8» &eTu inventario estaba lleno. La Guía de Slimefun se ha soltado a tus pies."));
                } else {
                    player.sendMessage(ChatColors.color("&a&lSlimefun &8» &a¡Se te ha entregado la Guía de Slimefun! Haz clic derecho con ella o escribe &e/sf open_guide &apara consultar recetas."));
                }
            } else {
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
        }
    }

}
