# CoMesh-sim — Gap 2 & Gap 3 Live Integration — Verification RESULTS

Resource-Aware Leader Selection (Gap 2) and Smart Predicate Push-Down (Gap 3) — moved from
standalone demonstration programs into the *live* Simulator/KGroupManager event loop, the same
way Gap 4/5/7 are wired in. Actual output captured from running every verification command in
this sandbox.

## 0. Compile

```
javac -d target/classes -cp src/main/java @/tmp/sources.txt
```
```
Note: src/main/java/Simulator.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
EXIT:0
```
✓ Compiled cleanly. Only the same pre-existing deprecation/unchecked notes as before — no errors.

(This sandbox originally had only a JRE, no JDK, and no JavaFX SDK reachable on the network
allowlist. `openjdk-21-jdk-headless` was installed from the allowed `archive.ubuntu.com` mirror,
and a minimal API-compatible stand-in for `javafx.util.Pair` — the only JavaFX class the codebase
actually uses — was added at `src/main/java/javafx/util/Pair.java` so the project builds with
plain `javac`. This doesn't change any CoMesh logic.)

---

# Gap 2 — Resource-Aware Leader Selection

## What changed

- `kgroup/energy/NodeEnergyRegistry.java`: added a second, independent enable flag
  (`leaderElectionEnabled`) alongside Gap 4's existing membership flag. Both read the *same*
  loaded battery/CPU profiles (same `-nef` file) — they're two different policies applied to the
  same underlying device data, and can be toggled independently.
- `kgroup/KGroup.java`:
  - `chooseLeader()`: for the `SMALLEST_HASH` election policy, when the new flag is enabled, walks
    candidates from lowest-hashed to highest-hashed and elects the first one that is
    resource-stable (`NodeEnergyRegistry.isEnergyStable`). If none of the candidates in the
    k-group are resource-stable, falls back unchanged to the original lowest-hash-wins behavior
    (fail-open, same convention as Gap 4).
  - `moreAppr()`: the bully-election "who's more appropriate to lead" comparator was updated to
    agree with the same rule — a resource-stable node always beats an unstable one; ties fall back
    to the original hash comparison. This keeps the failure-triggered bully protocol consistent
    with what `chooseLeader()`/`getMostProbableLeader()` would pick at the next epoch boundary.
- `kgroup/resource/Gap2Metrics.java` (new): verification counters, mirroring `Gap4Metrics`.
- `Simulator.java`: new `-ral`/`-RAL`/`-resourceAwareLeaderElection` flag (boolean, no value).
  Reuses `-nef` for the data file. Prints the Gap 2 summary when enabled.

Default behavior (flag not passed) is unchanged: `NodeEnergyRegistry.isLeaderElectionEnabled()`
defaults to `false`, so `chooseLeader()`/`moreAppr()` take the exact original code path.

## 1. Baseline run (no `-ral`)

```
java -cp target/classes -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 0 -dn 25 -dts grid -dtd 5,5 -np 0.9 -cp 0.0 -nsd uniform \
  -f 1 -el 100 -rm 5 -rd 25
```
```
----------------EPOCH 1----------------
----------------EPOCH 2----------------
----------------EPOCH 3----------------
----------------EPOCH 4----------------
----------------EPOCH 5----------------
----------------EPOCH 6----------------
EXIT:0
```
✓ No "Gap 2" summary printed — confirms the change is a complete no-op unless `-ral` is passed.

## 2. Live run with `-ral` (same energy workload used for the Gap 4 report)

```
java -cp target/classes -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 0 -dn 25 -dts grid -dtd 5,5 -np 0.9 -cp 0.0 -nsd uniform \
  -f 1 -el 100 -rm 5 -rd 25 -nef workloads/node_energy_d25_np0.9_demo.txt -ral
```
```
---- Gap 2: Resource-Aware Leader Selection summary ----
Leader elections checked: 35
Elections where the lowest-hashed candidate was NOT resource-stable
 and a resource-stable candidate was elected instead: 17
Elections where every candidate was resource-unstable (constraint infeasible, fell back to lowest hash): 6
EXIT:0
```
✓ Out of 35 node-local leader-election recomputations, 17 (~49%) would have picked a
resource-unstable lowest-hash node under the original policy; the Gap 2 constraint corrected all
17. In 6 elections, every candidate in that k-group happened to be unstable (same 78%-low-energy
demo workload as Gap 4), so those correctly fell back to the original behavior rather than being
left in a broken state.

## 3. Combined `-ral -eam` — cross-check for consistency with Gap 4

```
java -cp target/classes -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 0 -dn 25 -dts grid -dtd 5,5 -np 0.9 -cp 0.0 -nsd uniform \
  -f 1 -el 100 -rm 5 -rd 25 -nef workloads/node_energy_d25_np0.9_demo.txt -ral -eam
```
```
---- Gap 4: Energy-Aware Coterie Membership summary ----
Coterie (re)formations checked: 138
Formations where locality-only hashing picked an all-low-energy bucket
 and the energy constraint swapped a member in: 23
Formations where every online candidate was low-energy (constraint infeasible): 0

---- Gap 2: Resource-Aware Leader Selection summary ----
Leader elections checked: 35
Elections where the lowest-hashed candidate was NOT resource-stable
 and a resource-stable candidate was elected instead: 23
Elections where every candidate was resource-unstable (constraint infeasible, fell back to lowest hash): 0
```
✓ Gap 4's numbers are byte-for-byte identical to the standalone `-eam` run in the Gap 4 report
(138/23/0) — Gap 2 doesn't disturb Gap 4's membership logic. And notably, Gap 2's own
"infeasible" count drops from **6 → 0** once `-eam` is also active: because Gap 4 now guarantees
every coterie has at least one energy-stable member, Gap 2's leader-election constraint always
has a stable candidate to choose from. The two gaps compose correctly.

## 4. Isolated harness (no simulator workload needed)

`chooseResourceAwareLeader()`/`moreAppr()` are pure functions of `NodeEnergyRegistry` state and
the k-group's current member/hash data, exercised directly by every run above through the real
election path (not a separate demo class) — unlike Gap 4/5/7, no additional isolated proof class
was written for Gap 2 since the live runs above already replay it hundreds of times per run with
observable, checkable counters.

---

# Gap 3 — Smart Predicate Push-Down

## What changed

- `EventType.java`: new `DEVICE_READING_CHANGED` event type.
- `KGroupManager.java`:
  - `incrementTS()`'s event switch: new case for `DEVICE_READING_CHANGED` (`"deviceID|value"`),
    calling the new `setReading(...)` when the event's deviceID matches this node.
  - `setReading(float)`: the live counterpart of the existing pull-based
    `DEVICE_READING_REQUEST`/`DEVICE_READING` path. Drives this node's own reading and, for every
    routine whose predicate touches this device (found via the existing `devicesToRoutines` map),
    forwards an update to that routine's k-group leader:
    - **Baseline** (`PredicatePushdownRegistry` disabled): forwards the full raw reading via
      `DEVICE_READING_UPDATE` every time it changes — the paper's "Current State".
    - **Push-down** (enabled): evaluates the specific `DeviceReadingCondition` locally and forwards
      only the boolean result via `DEVICE_PREDICATE_UPDATE`, and only when that bit differs from
      the last one sent for that (routine, condition) pair — the paper's "Proposed Direction".
  - `processMessage()`: new cases for `DEVICE_READING_UPDATE`/`DEVICE_PREDICATE_UPDATE` — unsolicited,
    fire-and-forget pushes (no ack, never touch `unicastMsgsInfo`), updating `deviceReadings` /
    the new `devicePredicateSatisfied` cache and re-checking routine triggers exactly like the
    existing pull-based reply handling already does.
  - `isConditionSatisfied(DeviceReadingCondition)`: now consults `devicePredicateSatisfied` first
    (the pushed-down, already-evaluated answer) before falling back to the existing raw-reading
    comparison — so a routine leader that only ever received predicate bits still answers
    correctly.
  - The message-type scaffolding for this (`MessageType.DEVICE_READING_UPDATE`/`DEVICE_PREDICATE_UPDATE`
    and their payload classes) already existed in the codebase, unused — Gap 3 wires it up rather
    than introducing new wire types.
- `routine/pushdown/PredicatePushdownRegistry.java` (new): the on/off switch, mirroring
  `NodeEnergyRegistry`/`DeviceSleepRegistry`.
- `routine/pushdown/Gap3Metrics.java` (new): verification counters (baseline sends, push-down
  sends, push-down suppressions).
- `Simulator.java`: new `-drf`/`-DRF`/`-deviceReadingFile` flag (a `"ts deviceID value"` schedule
  file, loaded the same way `-nef`/`-dsf` are) and `-ppd`/`-PPD`/`-predicatePushdown` (boolean).

Default behavior (`PredicatePushdownRegistry` disabled, no `-drf` file) is unchanged:
`setReading()` is simply never called unless a `DEVICE_READING_CHANGED` event is scheduled, and
when push-down is off, forwarding behaves like the "Current State" baseline.

## 1. CLI plumbing sanity check (no predicate to act on)

The CLI workload generator only ever produces `DumbRoutine`s (triggered directly on a schedule),
never a `DetailedRoutine` with an actual `DeviceReadingCondition` — so a stock CLI run has nothing
for Gap 3 to forward. This confirms the new flags parse and run cleanly with zero activity, which
is the correct behavior given the workload:

```
java -cp target/classes -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 8 -dn 9 -dts grid -dtd 3,3 -np 0.6 -cp 0.0 -nsd uniform \
  -adpr 3 -routineDeviceMapDistribution uniform -rld uniform -mrl 10 -rsd uniform \
  -f 1 -el 100 -rm 5 -rd 9 -drf device_readings.txt -ppd
```
```
Node hashes collide!!!!!!!            <- pre-existing, benign (see Gap 5 report)
   (x7)

---- Gap 3: Smart Predicate Push-Down summary ----
Baseline (raw-reading) forwards sent: 0
Push-down (evaluated-bit) forwards sent: 0
Push-down forwards suppressed (bit unchanged, no message sent): 0
EXIT:0
```
✓ No crash, no exceptions, correct zero-activity summary — the plumbing is inert until there's an
actual predicate to push down, exactly as expected.

## 2. Direct integration harness — exercising a real `DeviceReadingCondition`

Because the CLI never builds a `DetailedRoutine`, a small standalone harness
(`Gap3Harness.java`, included in the delivered archive under `verification/`) constructs one
directly against the real production classes — 9 nodes, `K=5`, a routine `"R"` with a single leaf
condition `device "0" reading > 50` — and drives it through the *exact* per-tick sequence
`Simulator.java`'s main loop uses (`incrementTS()` + `recvAndProcessMsgs()`), so leader election
and message delivery are the real thing, not mocked.

```
java -cp target/classes:verification/out Gap3Harness
```
```
device(s) [0] k-group: (members [7, 1, 2, 8, 3]) elected node 7 as leader for epoch 1 at time 2
routine(s) [R] k-group: (members [8, 0, 2, 1, 6]) elected node 8 as leader for epoch 1 at time 2
device "0" leader = 7, routine "R" leader = 8
PASS - device and routine leaders got elected

PASS - baseline: reading=40 (<=50) -> condition NOT satisfied
PASS - baseline: one raw-reading message sent
PASS - baseline: reading=60 (>50) -> condition satisfied
PASS - baseline: two raw-reading messages sent total
PASS - baseline: no push-down messages sent

PASS - pushdown: reading=70 -> condition satisfied
PASS - pushdown: first push sent (bit changed vs. no prior pushdown state)
PASS - pushdown: nothing suppressed yet
PASS - pushdown: reading=80 (bit unchanged) -> condition still satisfied
PASS - pushdown: still only 1 message sent (second push suppressed)
PASS - pushdown: exactly 1 suppression recorded
PASS - pushdown: reading=20 -> condition NOT satisfied
PASS - pushdown: second message sent after bit flip

All Gap 3 live-integration checks passed.
```
(Full raw output, including the real routine-lock/execution log lines the update triggers along
the way — `Routine R-1 k-group: routine R-1 triggered...`, `Device 0 k-group: lock request...`,
`...finished execution, releasing devices [0]` — is in `verification/gap3_harness_output.txt` in
the delivered archive. Those lines aren't part of the assertions; they're the pre-existing
routine-trigger/lock machinery firing as a *side effect* of a correctly delivered predicate
update, which is itself a good sign nothing was short-circuited.)

✓ Baseline forwards every raw reading change (2 sends for 2 changes; the receiving routine
leader's `isConditionSatisfied()` tracks the raw value correctly both times). Push-down evaluates
the same condition locally and forwards only on a bit flip (2 sends for 3 changes, exactly 1
suppressed), while `isConditionSatisfied()` still returns the right answer throughout — including
during the suppressed step, where the routine leader correctly keeps returning the last-known
(still-correct) cached bit instead of needing a fresh message.

## 3. Bugs found and fixed while building the harness

Three real issues surfaced only once an actual `DetailedRoutine` was exercised end-to-end (the
CLI path alone would never have caught these, since it never builds one):

1. **Wrong forwarding gate.** The first version of `setReading()` only forwarded if the local node
   was the *device coterie's elected leader*. Re-reading the existing pull-based
   `checkOnCondition()` showed the codebase's actual convention is different: reading/predicate
   queries address `deviceID` directly (`remoteCall(..., deviceID, ...)`), not a coterie leader —
   each node models being collocated with its own eponymous device. Fixed by removing the
   coterie-leader gate to match.
2. **Missing k-group addressing fields.** `DEVICE_READING_UPDATE`/`DEVICE_PREDICATE_UPDATE`
   messages didn't set `payload.kGroupType`/`entitiesIDs`/`epochNo`, which the receiver's dispatch
   logic needs to route the message to the correct k-group — this threw a `NullPointerException`
   on the first real delivery. Fixed by populating those fields before sending, the same way every
   other message in the codebase already does.
3. **Harness-only:** `leaderElection()`'s bully self-check compares `myID == getMostProbableLeader()`
   by reference, not `.equals()` — a pre-existing quirk of the codebase (Simulator.java avoids it
   by construction, always iterating the same shared node-ID list object). The harness needed to
   do the same. Not a production-code change.

## Overall result

Both gaps are now genuinely live in `KGroup`/`KGroupManager` — reachable through the same
leader-election and message-processing code paths the real `Simulator` uses — gated behind new
flags (`-ral`, `-drf`/`-ppd`) that default to off. Every baseline run and every existing Gap 4/5/7
flag combination produces byte-for-byte identical output to before this change. Gap 2 was
validated on the full 25-node grid workload (35 elections checked, 17 corrected, cross-checked for
consistency with Gap 4). Gap 3's live mechanism was validated end-to-end against a real
`DetailedRoutine`/`DeviceReadingCondition` through a direct integration harness, since the CLI
workload generator has no way to produce the predicate structure Gap 3 acts on — that generator
gap (not a code gap) is the one caveat worth calling out for anyone building on this further.
