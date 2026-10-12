package com.github.drakescraft_labs.slimefun4.core.commands.subcommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.core.commands.SlimefunCommand;
import com.github.drakescraft_labs.slimefun4.core.commands.SubCommand;
import com.github.drakescraft_labs.slimefun4.core.guide.themes.GuideThemes;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;

/**
 * {@code /sf themes <on|off|status|reload|report>}: turns the themed organisation of {@code /sf guide} and
 * {@code /sf cheat} on or off without a restart, reloads {@code guide-themes.yml} and lists the addon categories
 * that no theme claims yet.
 */
class GuideThemesCommand extends SubCommand {

    private static final String PERMISSION = "slimefun.command.themes";

    GuideThemesCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "themes", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(PERMISSION) && !sender.isOp()) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";

        switch (action) {
            case "on", "off" -> {
                boolean enable = action.equals("on");
                Slimefun.getCfg().setValue(GuideThemes.ENABLED_PATH, enable);
                Slimefun.getCfg().save();
                sender.sendMessage("§aGuide themes " + (enable ? "enabled" : "disabled") + ". Open /sf guide or /sf cheat to see it.");
            }
            case "reload" -> {
                GuideThemes.reset();
                GuideThemes themes = GuideThemes.get();
                sender.sendMessage("§aguide-themes.yml reloaded: " + themes.getThemes().size() + " themes.");
            }
            case "report" -> report(sender);
            default -> {
                GuideThemes themes = GuideThemes.get();
                sender.sendMessage("§eGuide themes: " + (themes.isEnabled() ? "§aenabled" : "§cdisabled") + "§e, " + themes.getThemes().size()
                    + " themes. Usage: /sf themes <on|off|status|reload|report>");
            }
        }
    }

    private void report(@Nonnull CommandSender sender) {
        Set<String> unassigned = GuideThemes.get().unassignedNamespaces(Slimefun.getRegistry().getAllItemGroups());

        if (unassigned.isEmpty()) {
            sender.sendMessage("§aEvery item group belongs to a theme.");
            return;
        }

        List<String> sorted = new ArrayList<>(unassigned);
        sorted.sort(String::compareTo);

        int groups = 0;

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (unassigned.contains(group.getKey().getNamespace().toLowerCase(Locale.ROOT))) {
                groups++;
            }
        }

        sender.sendMessage("§e" + groups + " categories in the fallback theme (\"Other Addons\"). Namespaces to add to guide-themes.yml:");
        sender.sendMessage("§7" + String.join(", ", sorted));
    }
}
