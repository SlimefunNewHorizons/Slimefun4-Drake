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

    /**
     * slimefun.shift-click-input (default TRUE): el shift-click desde el inventario del jugador hacia una maquina Slimefun
     * solo rellena las ranuras de entrada que la maquina declara (getSlotsAccessedByItemTransport INSERT). Poner false
     * devuelve el comportamiento vanilla (cualquier ranura libre del menu).
     */
    public static volatile boolean shiftClickInput = true;

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
            YamlConfiguration cfg = m == 0 ? null : YamlConfiguration.loadConfiguration(FILE);
            idleSkip = cfg != null && cfg.getBoolean("slimefun.idle-skip", false);
            shiftClickInput = cfg == null || cfg.getBoolean("slimefun.shift-click-input", true);
        } catch (Throwable t) {
            idleSkip = false;
            shiftClickInput = true;
        }
    }
}
