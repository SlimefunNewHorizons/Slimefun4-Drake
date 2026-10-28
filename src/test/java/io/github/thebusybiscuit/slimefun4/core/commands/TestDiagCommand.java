package io.github.thebusybiscuit.slimefun4.core.commands;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TestDiagCommand {

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
    @DisplayName("/sf diag responde con heap, tickers y estado del store")
    void diag() {
        PlayerMock p = server.addPlayer();
        p.setOp(true);
        server.execute("slimefun", p, "diag");
        StringBuilder all = new StringBuilder();
        String line;
        while ((line = p.nextMessage()) != null) {
            all.append(line).append('\n');
        }
        String out = all.toString();
        Assertions.assertTrue(out.contains("diag | heap"), out);
        Assertions.assertTrue(out.contains("Tickers activos"), out);
        Assertions.assertTrue(out.contains("Store:"), out);
    }

    @Test
    @DisplayName("/sf diag sin permiso no muestra nada")
    void diagDenied() {
        PlayerMock p = server.addPlayer();
        server.execute("slimefun", p, "diag");
        String out = String.valueOf(p.nextMessage());
        Assertions.assertFalse(out.contains("diag | heap"), out);
    }
}
