package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.util.logging.Level;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * Selector del {@link SfStore} activo según {@code storage.backend} de config.yml
 * ({@code file} por defecto, {@code sqlite}, {@code mysql} o {@code postgres}). Si el SQL no arranca, cae a disco
 * y lo registra, para no perder bloques por un fallo de base de datos.
 */
public final class SfStores {

    private static final String[] ROOTS = { "data-storage/Slimefun/stored-blocks", "data-storage/Slimefun/stored-chunks",
        "data-storage/Slimefun/stored-inventories", "data-storage/Slimefun/universal-inventories" };

    private static SfStore active;

    private SfStores() {}

    public static synchronized @Nonnull SfStore get() {
        if (active == null) {
            active = create();
        }
        return active;
    }

    public static synchronized void close() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    private static SfStore create() {
        String backend = "file";
        try {
            backend = Slimefun.getCfg().getString("storage.backend");
        } catch (Exception | LinkageError ignored) {
            // sin plugin (tests): disco
        }
        if (backend == null || backend.equalsIgnoreCase("file")) {
            return new FileSfStore();
        }
        try {
            var cfg = Slimefun.getCfg();
            String url = switch (backend.toLowerCase()) {
                case "sqlite" -> "jdbc:sqlite:" + cfg.getString("storage.sqlite-file");
                default -> cfg.getString("storage.jdbc-url");
            };
            SqlSfStore sql = new SqlSfStore(url, cfg.getString("storage.jdbc-user"), cfg.getString("storage.jdbc-password"));
            int imported = sql.importFromDisk(ROOTS);
            if (imported > 0) {
                Slimefun.logger().log(Level.INFO, "Storage {0}: importados {1} ficheros desde disco (los originales no se tocan)", new Object[] { backend, imported });
            }
            Slimefun.logger().log(Level.INFO, "Storage activo: {0}", backend);
            return sql;
        } catch (Exception | LinkageError e) {
            Slimefun.logger().log(Level.SEVERE, e, () -> "No se pudo iniciar el storage '" + "SQL" + "', se usa disco");
            return new FileSfStore();
        }
    }
}
