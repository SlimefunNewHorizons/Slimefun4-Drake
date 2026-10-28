package io.github.thebusybiscuit.slimefun4.core.commands;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestAddonsCommand {

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

    @Test
    @DisplayName("/sf addons check sin coordenadas configuradas avisa y no consulta la red")
    void checkWithoutConfig() {
        org.mockbukkit.mockbukkit.entity.PlayerMock p = server.addPlayer();
        p.setOp(true);
        server.execute("slimefun", p, "addons", "check");
        String msg = p.nextMessage();
        Assertions.assertNotNull(msg);
        Assertions.assertTrue(msg.contains("addons.maven"), msg);
    }
}
