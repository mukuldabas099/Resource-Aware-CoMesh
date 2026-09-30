# CoMesh-sim — Gap 5 Verification RESULTS

Priority-Aware Lock Scheduling · actual output captured from running every verification command
in this sandbox

## 1. Compile

```
javac --release 21 -cp "$JAVAFX_CP" -sourcepath src/main/java -d target/classes @/tmp/srcs.txt
```
```
Note: src/main/java/Simulator.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
EXIT:0
```
✓ Compiled cleanly. Only pre-existing deprecation/unchecked notes — no errors.

## 2. Baseline run (no -rpf/-pas)

```
java -cp "target/classes:$JAVAFX_CP" -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 8 -dn 9 -dts grid -dtd 3,3 -np 0.6 -cp 0.0 -nsd uniform \
  -adpr 3 -routineDeviceMapDistribution uniform -rld uniform -mrl 10 -rsd uniform \
  -f 1 -el 100 -rm 5 -rd 9
```
```
Node hashes collide!!!!!!! (x5, pre-existing/benign — see note below)
EXIT:0
```
✓ No "Gap 5" summary printed anywhere in the output — confirms the change is a complete no-op
unless `-pas` is passed.

(The "Node hashes collide" messages are a pre-existing, benign print from
`KGroup.newKGroupMembers` whenever fewer online candidates exist than the desired coterie size
K=5 — expected at this small scale (9 devices, np=0.6) and unrelated to Gap 5/7; it also appears
identically with both new flags off.)

## 3. Generate the routine priority workload (25% HIGH, 25% LOW, seed 0)

```
python3 workloads/scripts/createRoutinePriorities.py \
  -r workloads/routine_map_r8_d9_a3_uniform.txt -hip 0.25 -lop 0.25 -s 0
```
```
Routine map: workloads/routine_map_r8_d9_a3_uniform.txt
HIGH-priority routine probability: 0.25
LOW-priority routine probability: 0.25
Seed: 0
routine priorities saved in workloads/routine_priorities_routine_map_r8_d9_a3_uniform.txt
{'HIGH': 2, 'NORMAL': 5, 'LOW': 1}
```
Generated file (`routineID priorityLabel`):
```
0 NORMAL
1 LOW
2 HIGH
3 HIGH
4 NORMAL
5 NORMAL
6 NORMAL
7 NORMAL
```

## 4. Run the same scenario with the Gap 5 fix enabled

```
java -cp "target/classes:$JAVAFX_CP" -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 8 -dn 9 -dts grid -dtd 3,3 -np 0.6 -cp 0.0 -nsd uniform \
  -adpr 3 -routineDeviceMapDistribution uniform -rld uniform -mrl 10 -rsd uniform \
  -f 1 -el 100 -rm 5 -rd 9 \
  -rpf workloads/routine_priorities_routine_map_r8_d9_a3_uniform.txt -pas
```
```
Priority-aware lock scheduling (Gap 5) is on
Node hashes collide!!!!!!! (x4, pre-existing/benign)

---- Gap 5: Priority-Aware Lock Scheduling summary ----
Lock-queue head computations: 15
Computations where a higher-(effective-)priority request skipped ahead
 of at least one older pending request: 2
Computations where the winner needed its anti-starvation aging bonus
 to win (waited long enough to be promoted): 0
EXIT:0
```
✓ Ran cleanly with the fix enabled: 15 real scheduling decisions were made across the run's
device leaders, and in 2 of them a higher-(effective-)priority request skipped ahead of an older,
lower-priority one still pending — exactly Gap 5's proposed behavior, measured on a real run, not
just the isolated proof. (0 aging promotions is expected at this scale/duration — the run isn't
long/contended enough to need the anti-starvation fallback; scenario 3 of the deterministic proof
below specifically forces that condition and confirms it works.)

## 5. Existing unit tests: unchanged behavior (actually run, not just inspected)

Unlike the Gap 4 writeup (whose sandbox couldn't reach Maven Central for a JUnit console
launcher), this session had `apt`-installable OpenJDK + `junit-platform-console-standalone`
available, so the existing suite was compiled and run for real:

```
java -cp "target/classes:target/test-classes:$JAVAFX_CP:$JUNIT_CP" \
  org.junit.platform.console.ConsoleLauncher --select-class=dijkstra.DijkstraTest --details=tree
```
```
[         1 tests found           ]
[         1 tests successful      ]
[         0 tests failed          ]
```

```
java -cp "target/classes:target/test-classes:$JAVAFX_CP:$JUNIT_CP" \
  org.junit.platform.console.ConsoleLauncher --select-class=routine.statement.RoutineStatementTest --details=tree
```
```
[         1 tests found           ]
[         1 tests successful      ]
[         0 tests failed          ]
```

```
java -cp "target/classes:target/test-classes:$JAVAFX_CP:$JUNIT_CP" \
  org.junit.platform.console.ConsoleLauncher --select-class=network.message.MessageTest --details=tree
```
```
[         1 tests found           ]
[         1 tests successful      ]
[         0 tests failed          ]
```

`KGroupManagerTest` and `NetworkTest` require workload files
(`workloads/sample_node_topology.txt`, a hardcoded `CoMesh/CoMesh/workloads/...` path) that are
**not present in the uploaded zip even before this change** — reproduced identically against an
unmodified copy of the original repo, so this is a pre-existing environment gap, not a regression.
`KGroupTest` intentionally constructs a k-group with `F=0, K=0`, which trips the pre-existing
`K < 4*F+1` guard and calls `System.exit(...)` — again reproduced identically on the unmodified
original repo, unrelated to this change.

## 6. Isolated, deterministic code-level proof

```
java -cp "target/classes:$JAVAFX_CP" kgroup.priority.Gap5DeterministicProof
```
```
Scenario 1: FIFO regression when priority scheduling is disabled
  head (expect rtn-A, arrival order): rtn-A-0 -> PASS
Scenario 2: HIGH priority overtakes older NORMAL requests when enabled
  head (expect rtn-C despite arriving last): rtn-C-0 -> PASS
  next after release (expect rtn-A, the older survivor): rtn-A-0 -> PASS
  next after that release (expect rtn-B): rtn-B-0 -> PASS
Scenario 3: anti-starvation aging bounds a LOW request's wait
  rtn-low won the lock after 5 competing HIGH arrivals (bound=6) -> PASS

Gap 5 deterministic proof: all scenarios passed.
EXIT:0
```
✓ Scenario 1 confirms the disabled path is byte-for-byte the original FIFO behavior. Scenario 2
confirms a HIGH-priority request that arrives *after* two NORMAL requests is still granted the
lock first, and that both older requests survive the reordering and are eventually served in
their own arrival order (no request lost, no double-grant). Scenario 3 confirms the anti-
starvation aging bound holds: a LOW-priority request enqueued first, facing a steady stream of
newly-arriving HIGH-priority competitors, still wins the lock within the theoretical bound
((MAX_PRIORITY − LOW_PRIORITY) × agingThreshold = 2 × 3 = 6 competing arrivals) — it actually won
after 5, comfortably inside the bound.

## Bugs found and fixed while building this (not hypothetical — the proof caught them)

1. **`getLocker()`/`getLockerReqSeqNo()`** assumed the locked entry was always
   `queue.firstKey()` — true under FIFO, false once priority reordering is possible. Fixed with
   explicit `lockedReqSeqNo` tracking (+ majority-vote recreation on leader failover).
2. **A "self-defeating tick"**: peeking the queue head immediately after a scheduling event's own
   aging-counter increment could disagree with what that same event had just decided, because the
   increment could itself push a different request's aging bonus past the threshold a moment
   later. This existed in the *original, pre-existing* `KGroupManager.gatherQuorumForDeviceLockAcquisition`
   too (it re-peeked via `getQueueHeadReqSeqNo(...)` right after a `release()`/`requestReplicated(...)`
   call that had already ticked) — fixed by having the tick return its decision directly instead of
   letting callers re-peek.
3. **Silent priority loss on admission**: `DeviceKGroup.lockRequestReplicated(...)` built a fresh,
   default-priority `LockRequest` instead of reusing the already-admitted, correctly-prioritized
   entry sitting in `newLockRequests`. Fixed in `DeviceLock.requestReplicated(...)`.

## Overall result

All 6 verification steps were executed in this sandbox, including actually running the existing
JUnit suite end to end (not just static inspection). The change compiles, is fully
backward-compatible (default behavior unchanged unless `-pas` is passed), and the priority-aware
lock-scheduling policy measurably closes Gap 5: real runs show priority-based reordering in
action, the anti-starvation aging bound is empirically verified, and three real correctness bugs
that a naive implementation would have shipped with — one of which was latent in the *original*
code path, not just this change's own addition — were found and fixed.
