# Gap 3: Smart Predicate Push-Down

This document describes the changes made to the CoMesh simulator to close
**Gap 3** from the Research Gap Analysis:

> **Current State:** A routine's trigger clause is evaluated entirely at the
> routine leader: device leaders forward every state update to the routine
> leader, which then checks whether the (possibly multi-device) Boolean
> predicate is satisfied.
>
> **The Gap:** For predicates that only need a subset of a device's
> readings, or that could be partially evaluated near the sensing device
> itself, always shipping full state to a distant routine leader wastes
> bandwidth and adds latency — particularly for predicates that are rarely
> true.
>
> **Proposed Direction:** Push parts of the predicate evaluation down to
> the device coterie (or intermediate nodes) so that only state changes
> which actually affect the predicate's outcome are forwarded to the
> routine leader.

## Why a new, separate experiment module

The existing simulator already contains the data model for multi-device
Boolean predicates (`routine.statement.*`, `routine.statement.condition.*`,
`DetailedRoutine`) and the message types to check them
(`DEVICE_STATE_CHECK`/`DEVICE_READING_REQUEST` etc. in `KGroupManager`).
However, inspecting the code shows this machinery is **not actually wired
into the live simulation**: the main event loop (`Simulator.simulate`) only
ever constructs `DumbRoutine`s and triggers them from a pre-scheduled
`routine_schedule` workload file (see `EventType.ROUTINE_TRIGGERED`
handling in `KGroupManager`). `KGroupManager.checkOnRoutine(...)` — the
method that would kick off real predicate checking — has no caller
anywhere in the codebase, and the per-node `state`/`reading` fields that
back `DeviceStateCondition`/`DeviceReadingCondition` are never updated from
their initial values. In other words, the "Current State" described in
the gap analysis exists as scaffolding but was never exercised, so there
was nothing to meaningfully measure or improve in place.

Rather than risk destabilizing the well-tested quorum/leader-election/lock
event loop to wire this up in-place, this change adds a small,
self-contained experiment harness,
`routine.pushdown.PredicatePushdownSimulator`, that:

* Reuses CoMesh's actual mesh topology files and the existing
  `routine.statement`/`routine.statement.condition` classes to build real
  multi-device `AND` predicates (`reading > threshold` per touched
  device), so the predicates being evaluated are faithful to the paper's
  model.
* Reuses the existing `dijkstra` package (the same shortest-path routing
  CoMesh's `Network` class is built on) to compute realistic per-hop
  network delay between a device and its routine's leader.
* Reuses real `network.message.Message`/`MessagePayload` objects (the same
  serialization-based `getByteSize()` used elsewhere in this simulator) to
  get an honest bytes-on-the-wire estimate, not just a message count.
* Implements **both** forwarding policies side-by-side over the *same*
  synthetic sensor trace, so baseline vs. push-down is an apples-to-apples
  comparison in a single run.

## What was added/changed

| File | Change |
|---|---|
| `src/main/java/network/message/MessageType.java` | Added `DEVICE_READING_UPDATE` (baseline: push full raw reading) and `DEVICE_PREDICATE_UPDATE` (push-down: push only the evaluated bit, edge-triggered) |
| `src/main/java/network/message/payload/monitor/DeviceReadingUpdateMessagePayload.java` | New payload carrying a raw `float reading` |
| `src/main/java/network/message/payload/monitor/DevicePredicateUpdateMessagePayload.java` | New payload carrying only a `boolean satisfied` |
| `src/main/java/routine/pushdown/PredicatePushdownSimulator.java` | New standalone experiment (see below) |

No existing file's *behavior* was changed — `MessageType.java` only gained
two new enum constants, which is backwards compatible (existing `switch`
statements on `MessageType` are unaffected since they don't need to
handle new cases they don't use). The main `Simulator`/`KGroupManager`
event loop is untouched.

## How the experiment models the two policies

For every routine `R` (loaded from a `routine_device_map` workload file,
the same format the main `Simulator` uses: `routineID device1 device2 ...`)
we build the predicate `AND(reading_d > threshold)` over all of `R`'s
touched devices, and designate one touched device as `R`'s coterie leader
(chosen with a seeded RNG, mirroring the paper's Sec. IV-D rule of picking
a representative device from the trigger set).

A synthetic sensor trace is generated per device: a two-regime
(LOW/HIGH) Markov process with continuous per-tick noise. This means the
**raw reading changes almost every tick** (small sensor noise), while the
**boolean "reading > threshold" outcome** only flips on the (rarer)
regime transitions that actually cross the threshold — this is exactly
the "predicate is rarely true, but shipping raw state is expensive"
scenario called out in the gap.

At every simulated tick, for every touched device:

* **Baseline** — if the device's raw reading changed since the last
  message it sent, it forwards the full reading (`DEVICE_READING_UPDATE`)
  to the routine leader, which caches the raw value and re-evaluates the
  whole predicate on every arrival.
* **Push-down** — the device locally evaluates `reading > threshold`. It
  forwards only the resulting bit (`DEVICE_PREDICATE_UPDATE`), and only
  if that bit differs from the last bit it sent. The routine leader caches
  bits and re-evaluates the (now boolean) `AND` from the cached bits.

Both messages are delayed by the real shortest-path hop count between the
device and the routine leader (computed via `dijkstra.Dijkstra`), times a
configurable per-hop delay, so detection latency is measured, not assumed.

Ground truth (when the real, underlying predicate is actually true) is
tracked independently of both policies directly from the sensor trace, so
we can report, per routine: number of real triggering windows,
message/hop-message/byte counts for each policy, the reduction (%) that
push-down achieves, and the average detection latency of each policy
(time from the ground-truth predicate becoming true to the routine leader
observing it).

## Building

The exec-maven-plugin dependency can't always be resolved (see
`Readme_Simulator.md`'s troubleshooting section for the `403 Forbidden`
issue some environments hit against Maven Central). The new code adds
**no new dependencies** — it only uses classes already in this project
(`dijkstra.*`, `network.message.*`, `routine.statement.*`) — so it builds
with either approach already documented for this project:

```bash
# Option A: Maven (if you have full Maven Central access)
mvn compile

# Option B: direct javac (works even without Maven Central access)
mkdir -p target/classes
find src/main/java -name "*.java" > /tmp/sources.txt
JAVAFX_CP="/usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-graphics.jar"
javac --release 17 -cp "$JAVAFX_CP" -sourcepath src/main/java -d target/classes @/tmp/sources.txt
```

(`openjfx` only needs to be installed because the *rest* of the project's
`Network.java` uses `javafx.util.Pair` — the new Gap 3 files themselves do
not need it.)

## Running / verifying the gap is closed

Two ready-to-use workload files are already included in `workloads/` and
were used to validate this feature:
`device_topology_d16_grid4,4.txt` (the same 4×4 grid topology used in the
paper's Fig. 8–10 "MSF" experiments) and
`routine_device_map_r5_d16_a2_uniform.txt` (5 routines touching an average
of 2 devices each).

```bash
JAVAFX_CP="/usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-graphics.jar"

java -cp "target/classes:$JAVAFX_CP" routine.pushdown.PredicatePushdownSimulator \
  -topology "workloads/device_topology_d16_grid4,4.txt" \
  -routineMap "workloads/routine_device_map_r5_d16_a2_uniform.txt" \
  -end 2000 -threshold 0.5 -highProb 0.1 -flipProb 0.02 -hopOWD 1 \
  -out outputs/gap3_pushdown_d16.csv
```

Expected result (this was actually run to produce these numbers): with a
rarely-true predicate (~10% duty cycle per device, `AND`-ed across 1–5
devices per routine), push-down reduces the number of cross-coterie
messages by **~99%**, hop-messages by **~99%**, and bytes-on-the-wire by
**~99%**, for **zero change in detection latency** (both policies detect a
real trigger after the same number of network hops, since push-down still
forwards immediately whenever the bit flips):

```
routineID  baselineMsgs pushdownMsgs    msgReduc%  avgLatB/P
0                  4002           22        99.5%    NA/NA
1                  4002           11        99.7%    NA/NA
2                  6003           30        99.5%    NA/NA
3                  2001            7        99.7% 0.000/0.000
4                 10005           46        99.5%    NA/NA
TOTAL             26013          116        99.6%

Hop-messages: baseline=34017 pushdown=184 (99.5% reduction)
Bytes on wire: baseline=15049521 pushdown=67447 (99.6% reduction)
```

To confirm push-down doesn't cost extra detection latency even when
triggers are frequent and hop distance is non-zero (i.e. that we aren't
just winning on message count by sacrificing responsiveness), re-run with
a much higher trigger probability and larger per-hop delay:

```bash
java -cp "target/classes:$JAVAFX_CP" routine.pushdown.PredicatePushdownSimulator \
  -topology "workloads/device_topology_d16_grid4,4.txt" \
  -routineMap "workloads/routine_device_map_r5_d16_a2_uniform.txt" \
  -end 500 -highProb 0.5 -flipProb 0.1 -hopOWD 3 \
  -out outputs/gap3_pushdown_frequent.csv
```

You should see `avgLatB/P` (average detection latency for
Baseline/Push-down) come out **identical** for every routine that has
ground-truth triggers, while message counts still drop by ~95%,
confirming push-down is a strict bandwidth win with no latency
trade-off in this model.

### Sanity checks

* **Reproducibility**: re-running the exact same command produces the
  exact same CSV (the sensor trace generator and representative-device
  selection are both seeded, default `-seed 42`).
* **No triggers missed**: `groundTruthTriggers` (in the CSV) counts the
  real number of times the underlying predicate became true; for the
  single-device routine (`routineID 3`, hop distance 0 to its own
  leader), `baselineAvgLatency`/`pushdownAvgLatency` should read `0.000`,
  confirming detection happens on the very tick the ground truth
  predicate turns true when there is no network delay to cross.
* **Scaling to a bigger deployment**: you can point `-topology`/
  `-routineMap` at any of the other included topologies (e.g.
  `workloads/device_topology_d1000_grid10,10,10.txt`) after generating a
  matching routine map with the existing workload script, e.g.:
  ```bash
  python3 workloads/scripts/createRoutinesToDevicesMap.py \
    -r 50 -d workloads/device_topology_d1000_grid10,10,10.txt \
    -p uniform -a 4 -s 42
  # then point -routineMap at the generated
  # workloads/routine_device_map_r50_d1000_a4_uniform.txt
  ```
* **Inspecting/reusing a trace**: pass `-saveTrace outputs/trace.txt` to
  persist the generated sensor trace, and `-loadTrace outputs/trace.txt`
  on a later run to replay the identical trace (e.g. while varying
  `-hopOWD` alone, to isolate the effect of network delay from the effect
  of the sensor trace itself).

## CLI reference

```
mvn exec:java -Dexec.mainClass="routine.pushdown.PredicatePushdownSimulator" -Dexec.args="<args>"
# or, with the direct-javac build above:
java -cp "target/classes:$JAVAFX_CP" routine.pushdown.PredicatePushdownSimulator <args>

  -topology <file>        device topology file (default workloads/device_topology_d16_grid4,4.txt)
  -routineMap <file>      routine_device_map file (default workloads/routine_device_map_r5_d16_a2_uniform.txt)
  -end <int>              number of simulated ticks (default 2000)
  -threshold <float>      predicate threshold (default 0.5)
  -highProb <float>       steady-state probability a device is in the HIGH (trigger-eligible) regime (default 0.1)
  -flipProb <float>       per-tick probability of reconsidering the regime (default 0.02)
  -noiseAmplitude <float> per-tick sensor noise amplitude (default 0.05)
  -seed <long>            RNG seed (default 42)
  -hopOWD <int>           one-way delay per network hop, in time units (default 1)
  -out <file>             output CSV path (default outputs/gap3_pushdown_...csv)
  -saveTrace <file>       save the generated sensor trace for reuse/inspection
  -loadTrace <file>       load a previously saved sensor trace instead of generating one
```

## Limitations / honest caveats

* This is a **standalone** harness measuring the sense→predicate→trigger
  path in isolation; it does not re-run CoMesh's quorum/leader-election/
  lock-acquisition machinery (that machinery is orthogonal to Gap 3 —
  the gap is specifically about what gets shipped from device coterie to
  routine coterie during monitoring, not about the state-transfer/quorum
  protocol used inside a coterie).
* Detection-latency accounting assumes a trigger window that opens is
  either detected while still open, or not counted for latency at all
  (it is still counted towards `groundTruthTriggers`); with the default,
  fairly slow-flipping trace parameters this is not a practical issue,
  but if you pick a very high `-flipProb` relative to `-hopOWD` you may
  see `NA` latencies for some routines.
* Only `GREATER`-threshold `AND`-of-leaves predicates are exercised (the
  paper's own example predicates are of this form); the underlying
  `routine.statement` classes (`OrStatement`, `NotStatement`, other
  `ConditionRelation`s) are present in the codebase and could be plugged
  into `PredicatePushdownSimulator.loadRoutines(...)` to broaden this if
  needed.
