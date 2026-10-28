package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Backend clásico en disco. Escribe a un temporal, hace fsync y mueve en atómico (un corte de luz no deja ficheros a 0 bytes). */
public class FileSfStore implements SfStore {

    @Override
    public @Nullable String read(@Nonnull String path) {
        try {
            Path p = Paths.get(path);
            return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean exists(@Nonnull String path) {
        return Files.isRegularFile(Paths.get(path));
    }

    @Override
    public void write(@Nonnull String path, @Nonnull String content) throws IOException {
        writeAll(Map.of(path, content));
    }

    /** Máximo de temporales abiertos a la vez (descriptores de fichero). */
    private static final int CHUNK = 256;

    @Override
    public void writeAll(@Nonnull Map<String, String> batch) throws IOException {
        List<Map.Entry<String, String>> entries = new ArrayList<>(batch.entrySet());
        for (int i = 0; i < entries.size(); i += CHUNK) {
            writeChunk(entries.subList(i, Math.min(entries.size(), i + CHUNK)));
        }
    }

    private static void writeChunk(List<Map.Entry<String, String>> chunk) throws IOException {
        int n = chunk.size();
        Path[] targets = new Path[n];
        Path[] tmps = new Path[n];
        FileChannel[] chans = new FileChannel[n];
        java.util.Set<Path> dirs = new java.util.LinkedHashSet<>();
        try {
            // 1) escribir todos los temporales sin sincronizar
            for (int i = 0; i < n; i++) {
                Path target = Paths.get(chunk.get(i).getKey());
                targets[i] = target;
                tmps[i] = target.resolveSibling(target.getFileName() + ".tmp");
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                    dirs.add(target.getParent());
                }
                chans[i] = FileChannel.open(tmps[i], StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                java.nio.ByteBuffer buf = StandardCharsets.UTF_8.encode(chunk.get(i).getValue());
                while (buf.hasRemaining()) {
                    chans[i].write(buf);
                }
            }
            // 2) fsync en bloque, solo ahora que todo está escrito
            for (FileChannel ch : chans) {
                ch.force(true);
            }
        } finally {
            for (FileChannel ch : chans) {
                if (ch != null) {
                    try {
                        ch.close();
                    } catch (IOException ignored) {
                        // cierre best-effort
                    }
                }
            }
        }
        // 3) renombrados atómicos: ningún destino queda a 0 bytes ni a medias
        for (int i = 0; i < n; i++) {
            try {
                Files.move(tmps[i], targets[i], StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailure) {
                Files.move(tmps[i], targets[i], StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // 4) fsync de cada directorio una vez, para que los renombrados sobrevivan a un corte
        for (Path d : dirs) {
            try (FileChannel dc = FileChannel.open(d, StandardOpenOption.READ)) {
                dc.force(true);
            } catch (IOException | UnsupportedOperationException ignored) {
                // Windows no permite abrir directorios: best-effort
            }
        }
    }

    @Override
    public void delete(@Nonnull String path) throws IOException {
        Files.deleteIfExists(Paths.get(path));
    }

    @Override
    public void forEach(@Nonnull String dir, @Nonnull BiConsumer<String, String> action) {
        Path d = Paths.get(dir);
        if (!Files.isDirectory(d)) {
            return;
        }
        try (Stream<Path> files = Files.list(d)) {
            files.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().endsWith(".tmp")).forEach(f -> {
                try {
                    action.accept(f.getFileName().toString(), Files.readString(f, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // fichero ilegible: se omite igual que hacía YamlConfiguration.loadConfiguration
                }
            });
        } catch (IOException e) {
            // directorio ilegible: nada que cargar
        }
    }
}
