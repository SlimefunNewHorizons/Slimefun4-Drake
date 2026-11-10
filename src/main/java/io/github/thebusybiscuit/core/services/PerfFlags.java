package io.github.thebusybiscuit.slimefun4.core.services;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.LongAdder;

/**
 * Interruptores de rendimiento de Slimefun leidos de {@code drakes-data/perf.yml} (clave {@code slimefun.*}) a traves de
 * {@code org.purpurmc.purpur.perf.DrakesPerf.cfg()} del kernel DrakesPurpur, por reflexion. Sin el kernel (Paper plano,
 * pruebas) todo queda en false y el codigo Java original corre identico.
 * Se refrescan una vez por ciclo del TickerTask, no por bloque.
 */
public final class PerfFlags {

    /** slimefun.idle-skip: las maquinas AContainer sin ningun item de entrada no recorren la lista de recetas. */
    public static volatile boolean idleSkip = false;

    /** Cuantas busquedas de receta se evitaron (visible en pruebas y depuracion). */
    public static final LongAdder IDLE_SKIPPED = new LongAdder();

    private static Method cfgMethod;
    private static Method getBoolean;
    private static boolean resolved;

    private PerfFlags() {}

    public static void refresh() {
        try {
            if (!resolved) {
                resolved = true;
                Class<?> perf = Class.forName("org.purpurmc.purpur.perf.DrakesPerf");
                cfgMethod = perf.getMethod("cfg");
                getBoolean = cfgMethod.getReturnType().getMethod("getBoolean", String.class, boolean.class);
            }
            if (cfgMethod != null) {
                idleSkip = (boolean) getBoolean.invoke(cfgMethod.invoke(null), "slimefun.idle-skip", false);
            }
        } catch (Throwable t) {
            cfgMethod = null; // sin kernel Drakes: flags quedan como estan (false por defecto)
        }
    }
}
