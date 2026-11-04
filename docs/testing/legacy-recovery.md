# Legacy recovery after addon shutdown

Run only on a disposable server with fresh worlds and no player data. The fixture
places a test enchanting table at the origin and writes a small configuration file.
It belongs in a test JAR, never in the production plugin artifact.

1. Build and run `mvn clean package -Dtest=TestLegacyProcessingRecovery` with the
   JDK required by the branch (21 on `main`, 25 on `port-26x`).
2. Install the resulting Slimefun JAR on a compatible disposable Paper/Purpur server.
3. Package `LegacyRecoveryProbe.class`, `LegacyRecoveryProbe$Machine.class` and
   `LegacyRecoveryProbe$LateField.class` from `target/test-classes` in a separate JAR,
   preserving package directories. Add this `plugin.yml`, adapting `main` to the
   fixture's package on the branch:

   ```yaml
   name: LegacyRecoveryProbe
   version: 1.0
   main: com.github.drakescraft_labs.slimefun4.core.machines.LegacyRecoveryProbe
   api-version: '1.21'
   depend: [Slimefun]
   ```

4. Start the server. Wait for `Done` and `T552_QUEUED` (one operation promising
   three gold blocks). Shut down with the console command `stop`; do not signal
   the JVM. The fixture addon must disable before Slimefun, which must report
   exactly one completed legacy operation without a closed-JAR exception.
5. Start the same disposable server again. Require
   `T552_PASS: exactly three persisted gold blocks`, then stop through the console.
   A further start must still report three, demonstrating no duplicate delivery.

The unit regression uses a separate `URLClassLoader` and closes its real JAR
before recovering an inherited processing map. A field type exists only inside
that JAR, reproducing the lazy linkage that previously failed during shutdown.
The cache stores descriptors, not operation contents: maps are read at shutdown
and successfully delivered entries are removed exactly once. `ClassValue` allows
the descriptors and their addon classloader to be collected together.
