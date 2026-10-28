package io.github.thebusybiscuit.slimefun4.storage.backend;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

    private synchronized Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = user == null || user.isEmpty() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    private static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    @Override
    public synchronized @Nullable String read(@Nonnull String path) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT content FROM sf_files WHERE path=?")) {
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
        try (PreparedStatement ps = conn().prepareStatement("SELECT 1 FROM sf_files WHERE path=?")) {
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
        String sql = postgres
            ? "INSERT INTO sf_files (path, dir, content) VALUES (?,?,?) ON CONFLICT (path) DO UPDATE SET content=EXCLUDED.content"
            : sqlite ? "INSERT OR REPLACE INTO sf_files (path, dir, content) VALUES (?,?,?)" : "REPLACE INTO sf_files (path, dir, content) VALUES (?,?,?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setString(2, dirOf(path));
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("SQL write " + path, e);
        }
    }

    @Override
    public synchronized void delete(@Nonnull String path) throws IOException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM sf_files WHERE path=?")) {
            ps.setString(1, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("SQL delete " + path, e);
        }
    }

    @Override
    public synchronized void forEach(@Nonnull String dir, @Nonnull BiConsumer<String, String> action) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT path, content FROM sf_files WHERE dir=?")) {
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
