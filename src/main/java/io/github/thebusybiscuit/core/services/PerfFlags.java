package io.github.thebusybiscuit.slimefun4.core.services;

import java.io.File;
import java.util.concurrent.atomic.LongAdder;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Interruptores de rendimiento de Slimefun leidos de {@code drakes-data/perf.yml} (clave {@code slimefun.*}).
 * Se vigila la fecha del archivo una vez por ciclo del TickerTask y solo se reparsea si cambio, asi que activar o
 * desactivar un flag no requiere reinicio. Si el archivo no existe (Paper plano, pruebas) todo queda en false y
 * el codigo Java original corre identico.
 */
public final class PerfFlags {

    /** slimefun.idle-skip: las maquinas AContainer sin ningun item de entrada no recorren la lista de recetas. */
    public static volatile boolean idleSkip = false;

    /** Cuantas busquedas de receta se evitaron (visible en pruebas y depuracion). */
    public static final LongAdder IDLE_SKIPPED = new LongAdder();

    private static final File FILE = new File("drakes-data/perf.yml");
    private static long lastModified = -1;

    private PerfFlags() {}

    public static void refresh() {
        try {
            long m = FILE.lastModified(); // 0 si no existe
            if (m == lastModified) {
                return;
            }
            lastModified = m;
            idleSkip = m != 0 && YamlConfiguration.loadConfiguration(FILE).getBoolean("slimefun.idle-skip", false);
        } catch (Throwable t) {
            idleSkip = false;
        }
    }
}
