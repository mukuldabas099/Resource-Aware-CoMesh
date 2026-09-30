# Verification artifacts — Gap 2 & Gap 3 live integration

These support `../CoMesh_Gap2_Gap3_Live_Integration_RESULTS.md`.

- `Gap3Harness.java` — a standalone (no JUnit needed) integration test that constructs a real
  `DetailedRoutine` with a `DeviceReadingCondition` and drives a 9-node mesh through the exact
  per-tick sequence `Simulator.java`'s main loop uses, to exercise Gap 3's `setReading()` /
  push-down logic end-to-end against the real production classes. The CLI workload generator
  can't build a `DetailedRoutine`, so this is the only way to exercise that code path today.
- `topology.txt` — a small 9-node fully-connected topology used by the harness.
- `device_readings.txt` — a sample Gap 3 device-reading schedule file (`ts deviceID value`), for
  the `-drf` Simulator flag.
- `gap3_harness_output.txt` — captured output from an actual run of the harness in this sandbox.

To rerun (from the project root, after building `target/classes`):

```
javac -cp target/classes -d verification/out verification/Gap3Harness.java
java -cp target/classes:verification/out Gap3Harness
```

Gap 2 (`chooseLeader`/`moreAppr`'s resource-aware constraint) doesn't need a separate harness —
it's exercised directly by every normal `Simulator` run via the real leader-election path; see
section "Gap 2" of the results doc for the exact commands and captured output.
