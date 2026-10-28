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

    @Override
    public SfStore unwrap() {
        return delegate.unwrap();
    }

    @Override
    public @Nullable String read(@Nonnull String path) {
        Slot s = pending.get(path);
        if (s != null) {
            return s.content;
        }
        if (cacheable(path)) {
            s = cache.get(path);
            if (s != null) {
                return s.content;
            }
            String v = delegate.read(path);
            cache.putIfAbsent(path, new Slot(v));
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
            cache.put(path, slot);
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
                this.cache.putIfAbsent(dir + "/" + name, new Slot(content));
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
                LOG.log(Level.SEVERE, e, () -> "SfStore: fallo al persistir " + what);
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    // cerrando: último intento inmediato
                }
            }
        }
        LOG.severe("SfStore: se descarta la escritura tras 3 intentos: " + what);
    }
}
