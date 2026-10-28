package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.io.IOException;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Almacén de blobs de texto direccionado por ruta virtual ({@code data-storage/Slimefun/stored-blocks/mundo/ID.sfb}).
 * Es la única capa por la que {@code BlockStorage} y los menús leen y escriben sus ficheros {@code .sfb/.sfc/.sfi},
 * de modo que el mismo modelo en memoria puede vivir en disco ({@link FileSfStore}) o en una base de datos
 * ({@link SqlSfStore}: SQLite, MySQL o PostgreSQL). Inspirado en la capa SQL de SlimefunGuguProject/Slimefun4,
 * pero sin cambiar la API de {@code BlockStorage} que usan los addons.
 */
public interface SfStore {

    /** @return el contenido, o {@code null} si no existe. */
    @Nullable
    String read(@Nonnull String path);

    boolean exists(@Nonnull String path);

    /** Escritura atómica y durable (fsync en disco, transacción en SQL). */
    void write(@Nonnull String path, @Nonnull String content) throws IOException;

    void delete(@Nonnull String path) throws IOException;

    /** Recorre los ficheros directos de {@code dir}: nombre (sin directorio) y contenido. */
    void forEach(@Nonnull String dir, @Nonnull BiConsumer<String, String> action);

    default void close() {
    }
}
