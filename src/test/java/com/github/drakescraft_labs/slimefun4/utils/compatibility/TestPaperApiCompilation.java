package com.github.drakescraft_labs.slimefun4.utils.compatibility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Compiles the whole plugin against each supported Paper API, one test per version, so a
 * failing build names the exact version that broke.
 * <p>
 * Every test runs Maven with the matching {@code paper-api-*} profile from the pom, which
 * resolves that API together with its own dependencies (Adventure, Guava...) and builds into
 * {@code target/paper-api-compile/<version>}. The output stays Java 21 bytecode.
 * <p>
 * Each run takes about a minute, so the tests only run with {@code -DpaperApiCompile=true}
 * (add {@code -DpaperApiCompile.offline=true} to build without network).
 * The 26.x APIs are Java 25 class files: when Maven itself runs on an older JDK, point
 * {@code JDK25_HOME} (or {@code -DpaperApiCompile.jdk25}) to a JDK 25. On GitHub Actions,
 * {@code JAVA_HOME_25_X64} from setup-java is picked up too.
 */
@EnabledIfSystemProperty(named = "paperApiCompile", matches = "true")
class TestPaperApiCompilation {

    private static final long TIMEOUT_MINUTES = 15;

    @Test
    @DisplayName("Compiles against Paper API 1.21.11")
    void testCompilesAgainstPaper_1_21_11() throws IOException, InterruptedException {
        compile("1.21.11", 21);
    }

    @Test
    @DisplayName("Compiles against Paper API 26.1")
    void testCompilesAgainstPaper_26_1() throws IOException, InterruptedException {
        compile("26.1", 25);
    }

    @Test
    @DisplayName("Compiles against Paper API 26.2")
    void testCompilesAgainstPaper_26_2() throws IOException, InterruptedException {
        compile("26.2", 25);
    }

    private static void compile(String version, int requiredJdk) throws IOException, InterruptedException {
        Path log = Paths.get("target", "paper-api-compile", version + ".log").toAbsolutePath();
        Files.createDirectories(log.getParent());

        List<String> command = new ArrayList<>();
        command.add(findMaven());
        command.add("-B");
        command.add("-ntp");

        // -DpaperApiCompile.offline=true when every dependency is already in the local repository
        if (Boolean.parseBoolean(System.getProperty("paperApiCompile.offline"))) {
            command.add("-o");
        }

        command.add("compile");
        command.add("-P");
        command.add("paper-api-" + version);
        command.add("-Dmaven.test.skip=true");
        command.add("-f");
        command.add(Paths.get("pom.xml").toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("JAVA_HOME", findJdk(requiredJdk));
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());

        Process process = builder.start();

        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            Assertions.fail("Compiling against Paper API " + version + " timed out after " + TIMEOUT_MINUTES + " minutes, see " + log);
        }

        if (process.exitValue() != 0) {
            Assertions.fail("The plugin does not compile against Paper API " + version + " (full log: " + log + "):\n" + errorsFrom(log));
        }
    }

    private static String findMaven() {
        String home = System.getProperty("maven.home");
        Assertions.assertNotNull(home, "maven.home is not set; run this test through Maven");

        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        File maven = Paths.get(home, "bin", windows ? "mvn.cmd" : "mvn").toFile();
        Assertions.assertTrue(maven.exists(), "Maven not found at " + maven);
        return maven.getAbsolutePath();
    }

    private static String findJdk(int requiredJdk) {
        if (Runtime.version().feature() >= requiredJdk) {
            return System.getProperty("java.home");
        }

        String[] candidates = { System.getProperty("paperApiCompile.jdk25"), System.getenv("JDK25_HOME"), System.getenv("JAVA_HOME_25_X64") };

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && new File(candidate, "bin").isDirectory()) {
                return candidate;
            }
        }

        return Assertions.fail("Paper API " + requiredJdk + "+ needs JDK " + requiredJdk + " to compile, but the tests run on JDK "
            + Runtime.version().feature() + ". Set JDK25_HOME or -DpaperApiCompile.jdk25 to a JDK " + requiredJdk + '.');
    }

    private static String errorsFrom(Path log) throws IOException {
        List<String> errors = new ArrayList<>();

        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            if (line.startsWith("[ERROR]") && errors.size() < 40) {
                errors.add(line);
            }
        }

        return String.join("\n", errors);
    }
}
