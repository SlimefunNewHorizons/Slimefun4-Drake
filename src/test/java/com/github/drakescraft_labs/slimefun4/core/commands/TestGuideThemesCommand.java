package com.github.drakescraft_labs.slimefun4.core.commands;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.github.drakescraft_labs.slimefun4.core.guide.themes.GuideThemes;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;

class TestGuideThemesCommand {

    private static ServerMock server;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @AfterEach
    void reset() {
        Slimefun.getCfg().setValue(GuideThemes.ENABLED_PATH, false);
        GuideThemes.reset();
    }

    @Test
    @DisplayName("An operator can turn the guide themes on and off")
    void testToggle() {
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        server.execute("slimefun", admin, "themes", "on").assertSucceeded();
        Assertions.assertTrue(Slimefun.getCfg().getBoolean(GuideThemes.ENABLED_PATH));
        Assertions.assertTrue(GuideThemes.get().isEnabled());

        server.execute("slimefun", admin, "themes", "off").assertSucceeded();
        Assertions.assertFalse(Slimefun.getCfg().getBoolean(GuideThemes.ENABLED_PATH));
    }

    @Test
    @DisplayName("A normal player cannot change the guide themes")
    void testNoPermission() {
        PlayerMock player = server.addPlayer();
        player.setOp(false);

        server.execute("slimefun", player, "themes", "on").assertSucceeded();
        Assertions.assertFalse(Slimefun.getCfg().getBoolean(GuideThemes.ENABLED_PATH));
    }

    @Test
    @DisplayName("The report and reload actions answer with a message")
    void testReportAndReload() {
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        server.execute("slimefun", admin, "themes", "reload").assertSucceeded();
        Assertions.assertNotNull(admin.nextMessage());

        server.execute("slimefun", admin, "themes", "report").assertSucceeded();
        Assertions.assertNotNull(admin.nextMessage());
    }
}
