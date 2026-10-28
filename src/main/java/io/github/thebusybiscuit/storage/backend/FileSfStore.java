package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
        Path target = Paths.get(path);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ch.write(StandardCharsets.UTF_8.encode(content));
            ch.force(true);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
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
