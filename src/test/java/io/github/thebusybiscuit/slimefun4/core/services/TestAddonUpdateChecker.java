package io.github.thebusybiscuit.slimefun4.core.services;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class TestAddonUpdateChecker {

    private static final String XML = "<metadata><versioning><latest>1.2.0</latest><release>1.1.0</release>"
            + "<versions><version>1.0.0</version><version>1.1.0</version></versions></versioning></metadata>";

    @Test
    @DisplayName("parseLatest prefiere release, luego latest, luego la última version")
    void parse() {
        Assertions.assertEquals("1.1.0", AddonUpdateChecker.parseLatest(XML));
        Assertions.assertEquals("2.0", AddonUpdateChecker.parseLatest("<latest>2.0</latest><version>1</version>"));
        Assertions.assertEquals("3", AddonUpdateChecker.parseLatest("<version>1</version><version>3</version>"));
        Assertions.assertNull(AddonUpdateChecker.parseLatest("<html>404</html>"));
        Assertions.assertNull(AddonUpdateChecker.parseLatest(null));
    }

    @Test
    @DisplayName("compareVersions es numérica y trata SNAPSHOT como anterior")
    void compare() {
        Assertions.assertTrue(AddonUpdateChecker.compareVersions("1.9.0", "1.10.0") < 0);
        Assertions.assertEquals(0, AddonUpdateChecker.compareVersions("1.0", "1.0.0"));
        Assertions.assertTrue(AddonUpdateChecker.compareVersions("1.3.1-SNAPSHOT", "1.3.1") < 0);
        Assertions.assertTrue(AddonUpdateChecker.compareVersions("2.0", "1.9.9") > 0);
        Assertions.assertTrue(AddonUpdateChecker.compareVersions("1.3.1-Universal-26.x-SNAPSHOT", "1.3.2-Universal-26.x-SNAPSHOT") < 0);
    }

    @Test
    @DisplayName("Las coordenadas con .. o caracteres raros se rechazan")
    void coordinates() {
        Assertions.assertTrue(AddonUpdateChecker.isValidCoordinate("io/github/bakedlibs/dough-core"));
        Assertions.assertFalse(AddonUpdateChecker.isValidCoordinate("../etc/passwd"));
        Assertions.assertFalse(AddonUpdateChecker.isValidCoordinate("a/b?x=1"));
        Assertions.assertFalse(AddonUpdateChecker.isValidCoordinate("solo"));
        Assertions.assertFalse(AddonUpdateChecker.isValidCoordinate(null));
    }

    @Test
    @DisplayName("latest consulta una vez (caché), tolera 404 y respeta el timeout")
    void http() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        HttpServer srv = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        srv.createContext("/g/ok/maven-metadata.xml", ex -> {
            hits.incrementAndGet();
            byte[] b = XML.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        srv.createContext("/g/slow/maven-metadata.xml", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        srv.start();
        try {
            AddonUpdateChecker c = new AddonUpdateChecker("http://127.0.0.1:" + srv.getAddress().getPort(), Duration.ofSeconds(5));
            Assertions.assertEquals("1.1.0", c.latest("g/ok").join());
            Assertions.assertEquals("1.1.0", c.latest("g/ok").join());
            Assertions.assertEquals(1, hits.get());
            Assertions.assertNull(c.latest("g/missing").join());
            AddonUpdateChecker fast = new AddonUpdateChecker("http://127.0.0.1:" + srv.getAddress().getPort(), Duration.ofMillis(500));
            long t0 = System.nanoTime();
            Assertions.assertNull(fast.latest("g/slow").join());
            Assertions.assertTrue((System.nanoTime() - t0) / 1_000_000 < 2800);
            Assertions.assertNull(c.latest("../x/y").join());
        } finally {
            srv.stop(0);
        }
    }
}
