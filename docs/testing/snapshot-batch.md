# Bounded inventory snapshots (#597)

`BlockStorage` now copies up to 16 ordinary menus in each server-thread handoff.
Writes remain on the calling thread. The main-thread shutdown path and subclasses
overriding `save(Location)` retain their existing behavior. The unused inventory
budget is preserved, including a zero budget. Abandoned or failed snapshot batches
are marked dirty again, and later machine changes remain dirty after a write.

Run `mvn test -Dtest=TestBlockMenuAsyncSnapshot` with Java 21 on `main`, Java 25
on `port-26x`, through the site's bounded build wrapper. The controlled scheduler
compares 128 individual handoffs with eight batched handoffs; this counts scheduling
operations and does not measure production save time.

For a runtime check, use a disposable Paper/Purpur server with fresh worlds and no
player data. Never install this fixture in production or an existing gameplay world.

1. Build the plugin and test classes. Install the built Slimefun JAR in the
   disposable server, disable automatic updates, and bind only to localhost.
2. Package `SnapshotBatchProbe.class` and `SnapshotBatchProbe$1.class` from
   `target/test-classes` into a separate JAR, preserving their package paths.
   Add `plugin.yml` with `name: SnapshotBatchProbe`, `version: '1.0'`,
   `api-version: '1.21'`, `depend: [Slimefun]`, and `main` set to the fixture's
   full class name for that branch.
3. Start the server. Require `Done` and `T597_PASS WRITE menus=128` in the log.
   The fixture creates synthetic inventory records without placing world blocks
   or touching any player's inventory. It validates quantities and synthetic PDC,
   recording total write time and the maximum server heartbeat gap.
4. Stop through the console, restart the same fixture, and require
   `T597_PASS RESTART menus=128`. This reopens the persisted records and must still
   find seven diamonds and the original PDC marker in every inventory.
5. Stop through the console. Keep the result and artifact SHA-256 as evidence.
   A successful fixture does not establish the duration of a full production save.

Revert the code commit to restore individual snapshot scheduling. No data migration,
item ID change, or inventory format change is needed.

## Recorded runtime result

On 2026-11-04, the universal `port-26x` fixture passed on disposable Paper
26.2 build 133, Java 25, with a fresh SQLite backend. It wrote and verified 128
synthetic inventories in 1,091 ms, then reopened and verified all 128 after a
clean stop and restart. Both runs reached `Done` with zero errors or exceptions.
The sampled heartbeat's maximum gap was 117 ms with a 1.5-CPU process limit;
that includes scheduling delays and is not a tick execution-time measurement.
This result does not measure Dallas or validate its complete autosave cycle.
