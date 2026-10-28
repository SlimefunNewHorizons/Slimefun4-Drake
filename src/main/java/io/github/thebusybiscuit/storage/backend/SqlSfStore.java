package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Backend JDBC (SQLite, MySQL/MariaDB o PostgreSQL) con una sola tabla {@code sf_files(path, dir, content)}.
 * Una conexión sincronizada que se reabre sola; SQLite va en WAL con {@code synchronous=FULL}, así cada
 * guardado es una transacción durable.
 */
public class SqlSfStore implements SfStore {

    private final String url;
    private final String user;
    private final String password;
    private final boolean sqlite;
    private final boolean postgres;
    private Connection connection;

    private final java.util.concurrent.atomic.LongAdder ops = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder nanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder slow = new java.util.concurrent.atomic.LongAdder();
    private volatile long worstMillis;

    public SqlSfStore(@Nonnull String url, @Nullable String user, @Nullable String password) throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        this.sqlite = url.startsWith("jdbc:sqlite:");
        this.postgres = url.startsWith("jdbc:postgresql:");
        try (Statement st = conn().createStatement()) {
            if (sqlite) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=FULL");
            }
            String text = postgres || sqlite ? "TEXT" : "LONGTEXT";
            st.execute("CREATE TABLE IF NOT EXISTS sf_files (path VARCHAR(512) NOT NULL PRIMARY KEY, dir VARCHAR(400) NOT NULL, content " + text + " NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS sf_files_dir ON sf_files (dir)");
        } catch (SQLException e) {
            // MySQL no soporta IF NOT EXISTS en CREATE INDEX: el índice ya existe o se crea aquí
            if (!String.valueOf(e.getMessage()).toLowerCase().contains("syntax")) {
                throw e;
            }
            try (Statement st = conn().createStatement()) {
                st.execute("CREATE INDEX sf_files_dir ON sf_files (dir)");
            } catch (SQLException ignored) {
                // ya existía
            }
        }
    }

    private long lastOk;
    private long failedAt;

    private synchronized Connection conn() throws SQLException {
        long now = System.nanoTime();
        // isValid() es un ida y vuelta de red: solo se comprueba si la conexion lleva >10 s sin usarse
        if (connection != null && now - lastOk < 10_000_000_000L && !connection.isClosed()) {
            lastOk = now;
            return connection;
        }
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            // con la BD caida no se reintenta en cada operacion (el hilo principal se quedaria esperando el connect-timeout)
            if (failedAt != 0 && now - failedAt < 5_000_000_000L) {
                throw new SQLException("BD no disponible (reintento en breve)");
            }
            try {
                java.util.Properties props = new java.util.Properties();
                if (user != null && !user.isEmpty()) {
                    props.setProperty("user", user);
                    props.setProperty("password", password == null ? "" : password);
                }
                if (sqlite) {
                    props.setProperty("busy_timeout", "5000");
                } else if (postgres) {
                    props.setProperty("connectTimeout", "5");
                    props.setProperty("socketTimeout", "15");
                } else {
                    props.setProperty("connectTimeout", "5000");
                    props.setProperty("socketTimeout", "15000");
                }
                connection = DriverManager.getConnection(url, props);
                failedAt = 0;
            } catch (SQLException e) {
                failedAt = now;
                connection = null;
                throw e;
            }
        }
        lastOk = now;
        return connection;
    }

    private PreparedStatement prep(String sql) throws SQLException {
        PreparedStatement ps = conn().prepareStatement(sql);
        ps.setQueryTimeout(10);
        return ps;
    }

    /** Perfilado ligero de consultas (idea de SQLProfiler/SlowSqlCheckTask de Gugu): avisa de las lentas (&gt;250 ms). */
    private void track(String path, long t0) {
        long n = System.nanoTime() - t0;
        ops.increment();
        nanos.add(n);
        long ms = n / 1_000_000L;
        if (ms > worstMillis) {
            worstMillis = ms;
        }
        if (ms > 250) {
            slow.increment();
            io.github.thebusybiscuit.slimefun4.implementation.Slimefun.logger().log(java.util.logging.Level.WARNING, "Consulta SQL lenta ({0} ms): {1}", new Object[] { ms, path });
        }
    }

    public String stats() {
        long o = ops.sum();
        return o + " consultas, media " + (o == 0 ? 0 : nanos.sum() / o / 1000) + " us, peor " + worstMillis + " ms, lentas " + slow.sum();
    }

    private static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    @Override
    public synchronized @Nullable String read(@Nonnull String path) {
        long t0 = System.nanoTime();
        try {
            return doRead(path);
        } finally {
            track(path, t0);
        }
    }

    private @Nullable String doRead(String path) {
        try (PreparedStatement ps = prep("SELECT content FROM sf_files WHERE path=?")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public synchronized boolean exists(@Nonnull String path) {
        try (PreparedStatement ps = prep("SELECT 1 FROM sf_files WHERE path=?")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public synchronized void write(@Nonnull String path, @Nonnull String content) throws IOException {
        long t0 = System.nanoTime();
        try {
            doWrite(path, content);
        } finally {
            track(path, t0);
        }
    }

    private void doWrite(String path, String content) throws IOException {
        String sql = postgres
            ? "INSERT INTO sf_files (path, dir, content) VALUES (?,?,?) ON CONFLICT (path) DO UPDATE SET content=EXCLUDED.content"
            : sqlite ? "INSERT OR REPLACE INTO sf_files (path, dir, content) VALUES (?,?,?)" : "REPLACE INTO sf_files (path, dir, content) VALUES (?,?,?)";
        try (PreparedStatement ps = prep(sql)) {
            ps.setString(1, path);
            ps.setString(2, dirOf(path));
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("SQL write " + path, e);
        }
    }

    @Override
    public synchronized void writeAll(@Nonnull Map<String, String> batch) throws IOException {
        if (batch.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        String sql = postgres
            ? "INSERT INTO sf_files (path, dir, content) VALUES (?,?,?) ON CONFLICT (path) DO UPDATE SET content=EXCLUDED.content"
            : sqlite ? "INSERT OR REPLACE INTO sf_files (path, dir, content) VALUES (?,?,?)" : "REPLACE INTO sf_files (path, dir, content) VALUES (?,?,?)";
        Connection c = null;
        try {
            c = conn();
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(30);
                for (Map.Entry<String, String> e : batch.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, dirOf(e.getKey()));
                    ps.setString(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new IOException("SQL batch write (" + batch.size() + ")", e);
        } finally {
            track("batch x" + batch.size(), t0);
        }
    }

    @Override
    public synchronized void delete(@Nonnull String path) throws IOException {
        try (PreparedStatement ps = prep("DELETE FROM sf_files WHERE path=?")) {
            ps.setString(1, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("SQL delete " + path, e);
        }
    }

    @Override
    public synchronized void forEach(@Nonnull String dir, @Nonnull BiConsumer<String, String> action) {
        try (PreparedStatement ps = prep("SELECT path, content FROM sf_files WHERE dir=?")) {
            ps.setString(1, dir);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString(1);
                    action.accept(path.substring(path.lastIndexOf('/') + 1), rs.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQL list " + dir, e);
        }
    }

    /** Importa en una sola transacción todos los ficheros bajo {@code roots} si la tabla está vacía. @return nº de ficheros importados. */
    public synchronized int importFromDisk(@Nonnull String... roots) throws SQLException, IOException {
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sf_files")) {
            if (rs.next() && rs.getLong(1) > 0) {
                return 0;
            }
        }
        Connection c = conn();
        c.setAutoCommit(false);
        int n = 0;
        try {
            for (String root : roots) {
                java.nio.file.Path base = java.nio.file.Paths.get(root);
                if (!java.nio.file.Files.isDirectory(base)) {
                    continue;
                }
                try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(base)) {
                    for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) walk.filter(java.nio.file.Files::isRegularFile)::iterator) {
                        String name = f.getFileName().toString();
                        if (name.endsWith(".sfb") || name.endsWith(".sfc") || name.endsWith(".sfi")) {
                            write(f.toString().replace('\\', '/'), java.nio.file.Files.readString(f));
                            n++;
                        }
                    }
                }
            }
            c.commit();
        } catch (IOException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
        return n;
    }

    /** Vuelca todas las filas a ficheros bajo {@code outDir} (misma estructura relativa). @return nº de ficheros. */
    public synchronized int exportToDisk(@Nonnull String outDir) {
        int n = 0;
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery("SELECT path, content FROM sf_files")) {
            while (rs.next()) {
                java.nio.file.Path target = java.nio.file.Paths.get(outDir, rs.getString(1));
                java.nio.file.Files.createDirectories(target.getParent());
                java.nio.file.Files.writeString(target, rs.getString(2));
                n++;
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("export", e);
        }
        return n;
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // cierre best-effort
        }
    }
}
