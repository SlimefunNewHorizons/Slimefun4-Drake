package com.github.drakescraft_labs.slimefun4.core.guide.options;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.drake.dough.items.CustomItemStack;
import com.github.drakescraft_labs.slimefun4.api.researches.Research;
import com.github.drakescraft_labs.slimefun4.core.guide.SlimefunGuide;
import com.github.drakescraft_labs.slimefun4.core.guide.SlimefunGuideMode;
import com.github.drakescraft_labs.slimefun4.core.services.LocalizationService;
import com.github.drakescraft_labs.slimefun4.core.services.github.GitHubService;
import com.github.drakescraft_labs.slimefun4.core.services.localization.Language;
import com.github.drakescraft_labs.slimefun4.core.services.sounds.SoundEffect;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.utils.ChatUtils;
import com.github.drakescraft_labs.slimefun4.utils.ChestMenuUtils;
import com.github.drakescraft_labs.slimefun4.utils.NumberUtils;
import com.github.drakescraft_labs.slimefun4.utils.SlimefunUtils;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * This static utility class offers various methods that provide access to the
 * Settings menu of our {@link SlimefunGuide}.
 *
 * This menu is used to allow a {@link Player} to change things such as the {@link Language}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 *
 */
public final class SlimefunGuideSettings {

    private static final int[] BACKGROUND_SLOTS = { 1, 3, 5, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 48, 50, 52, 53 };
    private static final List<SlimefunGuideOption<?>> options = new ArrayList<>();

    static {
        options.add(new GuideModeOption());
        options.add(new FireworksOption());
        options.add(new LearningAnimationOption());
        options.add(new PlayerLanguageOption());
    }

    private SlimefunGuideSettings() {}

    public static <T> void addOption(@Nonnull SlimefunGuideOption<T> option) {
        options.add(option);
    }

    @ParametersAreNonnullByDefault
    public static void openSettings(Player p, ItemStack guide) {
        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.settings"));

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_OPEN_SETTING_SOUND::playFor);

        ChestMenuUtils.drawBackground(menu, BACKGROUND_SLOTS);

        addHeader(p, menu, guide);
        addConfigurableOptions(p, menu, guide);

        menu.open(p);
    }

    @ParametersAreNonnullByDefault
    private static void addHeader(Player p, ChestMenu menu, ItemStack guide) {
        LocalizationService locale = Slimefun.getLocalization();

        // @formatter:off
        menu.addItem(0, new CustomItemStack(SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE),
            "&e\u21E6 " + locale.getMessage(p, "guide.back.title"),
            "",
            "&7" + locale.getMessage(p, "guide.back.guide")
        ));
        // @formatter:on

        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            SlimefunGuide.openGuide(pl, guide);
            return false;
        });

        GitHubService github = Slimefun.getGitHubService();

        List<String> contributorsLore = new ArrayList<>();
        contributorsLore.add("");
        contributorsLore.addAll(locale.getMessages(p, "guide.credits.description", msg -> msg.replace("%contributors%", String.valueOf(github.getContributors().size()))));
        contributorsLore.add("");
        contributorsLore.add("&7\u21E8 &e" + locale.getMessage(p, "guide.credits.open"));

        // @formatter:off
        menu.addItem(2, new CustomItemStack(SlimefunUtils.getCustomHead("e952d2b3f351a6b0487cc59db31bf5f2641133e5ba0006b18576e996a0293e52"),
            "&c" + locale.getMessage(p, "guide.title.credits"),
            contributorsLore.toArray(new String[0])
        ));
        // @formatter:on

        menu.addMenuClickHandler(2, (pl, slot, action, item) -> {
            ContributorsMenu.open(pl, 0);
            return false;
        });

        // @formatter:off
        menu.addItem(4, new CustomItemStack(Material.WRITABLE_BOOK,
            ChatColor.GREEN + locale.getMessage(p, "guide.title.versions"),
            "&7&o" + locale.getMessage(p, "guide.tooltips.versions-notice"),
            "",
            "&fMinecraft: &a" + Bukkit.getBukkitVersion(),
            "&fSlimefun: &a" + Slimefun.getVersion()),
            ChestMenuUtils.getEmptyClickHandler()
        );
        // @formatter:on

        // @formatter:off
        menu.addItem(6, new CustomItemStack(Material.COMPARATOR,
           "&e" + locale.getMessage(p, "guide.title.source"),
           "",
           "&7Optimized Slimefun technology ecosystem",
           "&7tailored exclusively for &6DrakesCraft&7.",
           "&7Performance improvements, fixes and new modules.",
           "",
           "&7\u21E8 &eClick to open GitHub (DrakesCraft-Labs)"
        ));
        // @formatter:on

        menu.addMenuClickHandler(6, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://github.com/DrakesCraft-Labs");
            return false;
        });

        // @formatter:off
        menu.addItem(8, new CustomItemStack(Material.KNOWLEDGE_BOOK,
            "&3" + locale.getMessage(p, "guide.title.wiki"),
            "",
            "&7Questions about a machine or item?",
            "&7Check the official DrakesCraft web guide for",
            "&7every detail, recipe, guide and tip.",
            "",
            "&7\u21E8 &eClick to open the DrakesCraft Web Guide"
        ));
        // @formatter:on

        menu.addMenuClickHandler(8, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://web.drakescraft.cl/guia-slimefun.html");
            return false;
        });

        // @formatter:off
        menu.addItem(47, new CustomItemStack(Material.BOOKSHELF,
            "&3" + locale.getMessage(p, "guide.title.addons"),
            "",
            "&7Slimefun on DrakesCraft is a whole universe.",
            "&7We run dozens of integrated and balanced addons",
            "&7(Infinity, Supreme, Networks, DankTech, DynaTech, etc.).",
            "",
            "&7Active addons on this server: &b" + Slimefun.getInstalledAddons().size(),
            "",
            "&7\u21E8 &eClick to see the Addon Wiki on the web"
        ));
        // @formatter:on

        menu.addMenuClickHandler(47, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://web.drakescraft.cl/");
            return false;
        });

        // @formatter:off
        menu.addItem(49, new CustomItemStack(Material.REDSTONE_TORCH,
            "&4" + locale.getMessage(p, "guide.title.bugs"),
            "",
            "&7Found a bug, glitch or error?",
            "&7Report it to &6Jack &7and &bSAORI &7on our Discord!",
            "&7Direct support in English and Spanish, with rewards for reports.",
            "",
            "&7\u21E8 &eClick to open the support and tickets channel"
        ));
        // @formatter:on

        menu.addMenuClickHandler(49, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://discord.gg/drakescraft");
            return false;
        });

        // @formatter:off
        menu.addItem(51, new CustomItemStack(Material.TOTEM_OF_UNDYING,
            "&6Totem of Wisdom &b(SAORI Assistant)",
            "",
            "&7Interactive in-game guide and server assistance.",
            "&7Get quick help on key commands, economy,",
            "&7island protection and the Slimefun ecosystem.",
            "",
            "&7\u21E8 &eClick to get SAORI's help in chat"
        ), (pl, slot, item, action) -> {
            pl.closeInventory();
            pl.sendMessage("§8§m--------------------------------------------------");
            pl.sendMessage("§6§l✦ DRAKESCRAFT ︱ HELP & QUICK GUIDE ✦");
            pl.sendMessage("§7Hi §e" + pl.getName() + "§7, how can §bSAORI§7 help you?");
            pl.sendMessage("");
            pl.sendMessage("§e ⏵ §b/guia §7- Opens the web guide and server tutorials");
            pl.sendMessage("§e ⏵ §b/asistencia §7- Ask the Staff and SAORI for direct help");
            pl.sendMessage("§e ⏵ §b/sf guide §7- The Slimefun encyclopedia guide");
            pl.sendMessage("§e ⏵ §b/is help §7- Commands and settings for your BentoBox island");
            pl.sendMessage("§e ⏵ §b/discord §7- Join our official community");
            pl.sendMessage("§8§m--------------------------------------------------");
            SoundEffect.TOME_OF_KNOWLEDGE_USE_SOUND.playFor(pl);
            return false;
        });
    }

    @ParametersAreNonnullByDefault
    private static void addConfigurableOptions(Player p, ChestMenu menu, ItemStack guide) {
        int i = 19;

        for (SlimefunGuideOption<?> option : options) {
            Optional<ItemStack> item = option.getDisplayItem(p, guide);

            if (item.isPresent()) {
                menu.addItem(i, item.get());
                menu.addMenuClickHandler(i, (pl, slot, stack, action) -> {
                    option.onClick(p, guide);
                    return false;
                });

                i++;
            }
        }
    }

    /**
     * This method checks if the given {@link Player} has enabled the {@link FireworksOption}
     * in their {@link SlimefunGuide}.
     * If they enabled this setting, they will see fireworks when they unlock a {@link Research}.
     *
     * @param p
     *            The {@link Player}
     *
     * @return Whether this {@link Player} wants to see fireworks when unlocking a {@link Research}
     */
    public static boolean hasFireworksEnabled(@Nonnull Player p) {
        return getOptionValue(p, FireworksOption.class, true);
    }

    /**
     * This method checks if the given {@link Player} has enabled the {@link LearningAnimationOption}
     * in their {@link SlimefunGuide}.
     * If they enabled this setting, they will see messages in chat about the progress of their {@link Research}.
     *
     * @param p
     *            The {@link Player}
     *
     * @return Whether this {@link Player} wants to info messages in chat when unlocking a {@link Research}
     */
    public static boolean hasLearningAnimationEnabled(@Nonnull Player p) {
        return getOptionValue(p, LearningAnimationOption.class, true);
    }

    /**
     * Helper method to get the value of a {@link SlimefunGuideOption} that the {@link Player}
     * has set in their {@link SlimefunGuide}
     *
     * @param p
     *            The {@link Player}
     * @param optionsClass
     *            Class of the {@link SlimefunGuideOption} to get the value of
     * @param defaultValue
     *            Default value to return in case the option is not found at all or has no value set
     * @param <T>
     *            Type of the {@link SlimefunGuideOption}
     * @param <V>
     *            Type of the {@link SlimefunGuideOption} value
     *
     * @return The value of given {@link SlimefunGuideOption}
     */
    @Nonnull
    private static <T extends SlimefunGuideOption<V>, V> V getOptionValue(@Nonnull Player p, @Nonnull Class<T> optionsClass, @Nonnull V defaultValue) {
        for (SlimefunGuideOption<?> option : options) {
            if (optionsClass.isInstance(option)) {
                T o = optionsClass.cast(option);
                ItemStack guide = SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE);
                return o.getSelectedOption(p, guide).orElse(defaultValue);
            }
        }

        return defaultValue;
    }

}
