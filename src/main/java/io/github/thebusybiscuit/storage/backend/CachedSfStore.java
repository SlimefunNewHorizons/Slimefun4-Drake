package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Decorador de {@link SfStore}: escrituras asíncronas en un único hilo escritor (cola ordenada, agrupadas en
 * lotes) y cache de lectura en memoria para los inventarios. El hilo principal nunca espera a la BD ni al disco
 * salvo en un miss raro; leer tras escribir ve siempre el valor nuevo (mapa de pendientes + cache write-through).
 * {@link #flush()} y {@link #close()} esperan a que la cola esté vacía.
 */
public class CachedSfStore implements SfStore {

    private static final Logger LOG = Logger.getLogger("Slimefun.SfStore");
    private static final int MAX_BATCH = 512;
    /** Tope del cache de lectura: es solo un acelerador (write-through), evictar nunca pierde datos. */
    static final int MAX_CACHE = 20_000;

    /** Valor en vuelo o cacheado; {@code content == null} significa borrado. Se compara por identidad. */
    private static final class Slot {
        final String content;

        Slot(String content) {
            this.content = content;
        }
    }

    private record Op(String path, Slot slot, CountDownLatch marker) {
    }

    private final SfStore delegate;
    private final Map<String, Slot> pending = new ConcurrentHashMap<>();
    private final Map<String, Slot> cache = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Op> queue = new LinkedBlockingQueue<>();
    private final Thread writer;
    private volatile boolean closed;
    private final java.util.concurrent.atomic.LongAdder reads = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder cacheHits = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder writeOps = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder failures = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder discarded = new java.util.concurrent.atomic.LongAdder();

    /** Estado para diagnóstico (/sf diag); lectura barata, sin bloquear. */
    public record Stats(int queued, int pending, int cacheEntries, long cacheChars, long reads, long cacheHits, long writes, long failures, long discarded) {
    }

    public @Nonnull Stats stats() {
        long chars = 0;
        for (Slot s : cache.values()) {
            chars += s.content == null ? 0 : s.content.length();
        }
        return new Stats(queue.size(), pending.size(), cache.size(), chars, reads.sum(), cacheHits.sum(), writeOps.sum(), failures.sum(), discarded.sum());
    }

    public CachedSfStore(@Nonnull SfStore delegate) {
        this.delegate = delegate;
        this.writer = new Thread(this::run, "Slimefun-SfStore-Writer");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /** Solo los inventarios se cachean: son los que se leen en runtime; bloques y chunks se cargan una vez. */
    private static boolean cacheable(String path) {
        return path.contains("-inventories/");
    }

    private void remember(String path, Slot slot) {
        if (cache.size() < MAX_CACHE) {
            cache.putIfAbsent(path, slot);
        }
    }

    /** Si el cache supera el tope, descarta ~10 % de entradas (las ya persistidas se releen del backend). */
    private void trim() {
        int excess = cache.size() - MAX_CACHE;
        if (excess <= 0) {
            return;
        }
        int drop = excess + MAX_CACHE / 10;
        for (String k : cache.keySet()) {
            if (drop-- <= 0) {
                break;
            }
            cache.remove(k);
        }
    }

    @Override
    public SfStore unwrap() {
        return delegate.unwrap();
    }

    @Override
    public @Nullable String read(@Nonnull String path) {
        reads.increment();
        Slot s = pending.get(path);
        if (s != null) {
            cacheHits.increment();
            return s.content;
        }
        if (cacheable(path)) {
            s = cache.get(path);
            if (s != null) {
                cacheHits.increment();
                return s.content;
            }
            String v = delegate.read(path);
            if (v == null) {
                return null; // no se cachean los ausentes: cada borrado dejaba una entrada para siempre
            }
            remember(path, new Slot(v));
            Slot now = cache.get(path);
            return now == null ? v : now.content;
        }
        return delegate.read(path);
    }

    @Override
    public boolean exists(@Nonnull String path) {
        Slot s = pending.get(path);
        if (s == null && cacheable(path)) {
            s = cache.get(path);
        }
        return s != null ? s.content != null : delegate.exists(path);
    }

    private void submit(String path, String content) throws IOException {
        if (closed) {
            throw new IOException("SfStore cerrado: " + path);
        }
        Slot slot = new Slot(content);
        // pending antes que cache y cola: cualquier lector ve ya el valor nuevo
        pending.put(path, slot);
        if (cacheable(path)) {
            if (content == null) {
                cache.remove(path);
            } else {
                cache.put(path, slot);
                trim();
            }
        }
        queue.add(new Op(path, slot, null));
    }

    @Override
    public void write(@Nonnull String path, @Nonnull String content) throws IOException {
        submit(path, content);
    }

    @Override
    public void writeAll(@Nonnull Map<String, String> batch) throws IOException {
        for (Map.Entry<String, String> e : batch.entrySet()) {
            submit(e.getKey(), e.getValue());
        }
    }

    @Override
    public void delete(@Nonnull String path) throws IOException {
        submit(path, null);
    }

    @Override
    public void forEach(@Nonnull String dir, @Nonnull BiConsumer<String, String> action) {
        flush(); // el listado del backend debe incluir lo pendiente
        boolean cache = cacheable(dir + "/");
        delegate.forEach(dir, (name, content) -> {
            if (cache) {
                remember(dir + "/" + name, new Slot(content));
            }
            action.accept(name, content);
        });
    }

    @Override
    public void flush() {
        if (closed && !writer.isAlive()) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        queue.add(new Op(null, null, latch));
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        flush();
        closed = true;
        writer.interrupt();
        try {
            writer.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.close();
    }

    private void run() {
        List<Op> ops = new ArrayList<>();
        while (true) {
            ops.clear();
            try {
                ops.add(queue.take());
            } catch (InterruptedException e) {
                if (queue.isEmpty()) {
                    return;
                }
                continue;
            }
            queue.drainTo(ops, MAX_BATCH - 1);
            apply(ops);
        }
    }

    private void apply(List<Op> ops) {
        Map<String, String> writes = new LinkedHashMap<>();
        List<Op> inWrites = new ArrayList<>();
        for (Op op : ops) {
            if (op.marker() == null) {
                writeOps.increment();
            }
            if (op.marker() != null) {
                flushWrites(writes, inWrites);
                op.marker().countDown();
            } else if (op.slot().content == null) {
                flushWrites(writes, inWrites);
                persist(() -> delegate.delete(op.path()), op.path());
                pending.remove(op.path(), op.slot());
            } else {
                writes.put(op.path(), op.slot().content);
                inWrites.add(op);
            }
        }
        flushWrites(writes, inWrites);
    }

    private void flushWrites(Map<String, String> writes, List<Op> inWrites) {
        if (writes.isEmpty()) {
            return;
        }
        persist(() -> delegate.writeAll(writes), writes.size() + " ficheros");
        for (Op op : inWrites) {
            pending.remove(op.path(), op.slot());
        }
        writes.clear();
        inWrites.clear();
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private void persist(IoAction action, String what) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                action.run();
                return;
            } catch (IOException | RuntimeException e) {
                failures.increment();
                LOG.log(Level.SEVERE, e, () -> "SfStore: fallo al persistir " + what);
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    // cerrando: último intento inmediato
                }
            }
        }
        discarded.increment();
        LOG.severe("SfStore: se descarta la escritura tras 3 intentos: " + what);
    }
}
