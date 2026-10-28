package io.github.thebusybiscuit.slimefun4.core.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Consulta SOLO LECTURA del {@code maven-metadata.xml} de un repositorio Maven para saber la última versión publicada
 * de un addon. Asíncrono (nunca bloquea el hilo principal), timeout de 5 s, caché con TTL y sin descargar jars.
 */
public class AddonUpdateChecker {

    public static final String DEFAULT_BASE = "https://maven.drakescraft.cl";
    private static final long OK_TTL_MS = 10 * 60_000L;
    private static final long FAIL_TTL_MS = 60_000L;
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_CACHE = 256;
    private static final Pattern COORD = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*(/[A-Za-z0-9_-][A-Za-z0-9_.-]*)+");
    private static final Pattern TAG = Pattern.compile("<(release|latest|version)>\\s*([^<\\s]+)\\s*</\\1>");

    private record Entry(String version, long expires) {
    }

    private final String base;
    private final Duration timeout;
    private final HttpClient client;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public AddonUpdateChecker() {
        this(DEFAULT_BASE, Duration.ofSeconds(5));
    }

    public AddonUpdateChecker(@Nonnull String base, @Nonnull Duration timeout) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** Coordenada {@code grupo/ruta/artefacto} (con barras, p. ej. {@code com/drakescraft/miaddon}); sin {@code ..}. */
    public static boolean isValidCoordinate(@Nullable String coordinate) {
        return coordinate != null && coordinate.length() <= 200 && !coordinate.contains("..") && COORD.matcher(coordinate).matches();
    }

    /** Extrae la última versión: prefiere {@code <release>}, luego {@code <latest>}, luego la última {@code <version>}. */
    public static @Nullable String parseLatest(@Nullable String xml) {
        if (xml == null) {
            return null;
        }
        String release = null;
        String latest = null;
        String last = null;
        Matcher m = TAG.matcher(xml);
        while (m.find()) {
            switch (m.group(1)) {
                case "release" -> release = m.group(2);
                case "latest" -> latest = m.group(2);
                default -> last = m.group(2);
            }
        }
        return release != null ? release : latest != null ? latest : last;
    }

    /** Compara versiones por segmentos (numéricos como números); un -SNAPSHOT es anterior a su versión final. */
    public static int compareVersions(@Nonnull String a, @Nonnull String b) {
        boolean sa = a.toUpperCase().contains("SNAPSHOT");
        boolean sb = b.toUpperCase().contains("SNAPSHOT");
        String[] pa = a.split("[^A-Za-z0-9]+");
        String[] pb = b.split("[^A-Za-z0-9]+");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            String x = i < pa.length ? pa[i] : "0";
            String y = i < pb.length ? pb[i] : "0";
            boolean nx = x.matches("\\d{1,9}");
            boolean ny = y.matches("\\d{1,9}");
            int c = nx && ny ? Integer.compare(Integer.parseInt(x), Integer.parseInt(y)) : x.compareToIgnoreCase(y);
            if (c != 0) {
                // un segmento no numérico ("SNAPSHOT", "Universal") frente a número no decide aquí
                if (nx != ny) {
                    return nx ? 1 : -1;
                }
                if (!nx && (x.equalsIgnoreCase("SNAPSHOT") || y.equalsIgnoreCase("SNAPSHOT"))) {
                    break;
                }
                return c;
            }
        }
        return sa == sb ? 0 : sa ? -1 : 1;
    }

    /** Última versión publicada; el futuro completa con {@code null} si falla o no existe (nunca lanza). */
    public @Nonnull CompletableFuture<String> latest(@Nonnull String coordinate) {
        if (!isValidCoordinate(coordinate)) {
            return CompletableFuture.completedFuture(null);
        }
        Entry cached = cache.get(coordinate);
        if (cached != null && cached.expires() > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached.version());
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/" + coordinate + "/maven-metadata.xml")).timeout(timeout).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenApply(resp -> {
            if (resp.statusCode() != 200) {
                return null;
            }
            try (InputStream in = resp.body()) {
                return parseLatest(new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return null;
            }
        }).exceptionally(e -> null).thenApply(v -> {
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
            cache.put(coordinate, new Entry(v, System.currentTimeMillis() + (v == null ? FAIL_TTL_MS : OK_TTL_MS)));
            return v;
        });
    }
}
