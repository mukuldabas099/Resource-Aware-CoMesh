# Gap 2: Energy / Resource-Aware Leader and Member Selection

This document describes the changes made to the CoMesh simulator to close
**Gap 2** from the Research Gap Analysis:

> **Current State:** Coterie membership and leadership are decided purely
> by a consistent hash, `Hash(epoch, deviceID, targetID)`. Every smart
> device is equally likely to be selected as a coterie member or elected
> leader, regardless of its actual resource state.
>
> **The Gap:** Treating all devices as equally capable ignores battery
> level, battery discharge rate, and CPU load. A low-battery or
> heavily-loaded device can still be hashed into the leader role, causing
> it to fail (or drain) sooner — triggering avoidable leader-election and
> state-transfer overhead exactly when the system can least afford it.
>
> **Proposed Direction:** Extend the selection function to be
> resource-aware: weight or filter candidate devices using battery life,
> battery load, and CPU load before applying the hash, so that the
> lowest-hashed *eligible* (sufficiently resourced) device becomes leader,
> rather than the lowest-hashed device overall.

## Why a new, standalone experiment module (same approach as Gap 3)

The real selection logic lives in `kgroup.KGroup`:
`hash(nodeID, epochNo, N)` builds `Hash(epoch, deviceID, targetID)` (the
`entitiesIDs`/`type` of the `KGroup` instance stand in for "targetID"),
`newKGroupMembers(...)` ranks candidates by that hash and takes the
lowest-hashed `K`, and `chooseLeader(SMALLEST_HASH)` picks the
lowest-hashed member as leader. None of this reads battery, discharge
rate, or CPU load — those fields don't exist anywhere in `KGroup`,
`Membership`, or `NodeMetrics` today, so "current state" for this gap is,
like Gap 3, scaffolding-shaped-not-present: there is no resource model to
retrofit in place without inventing one from scratch inside the live
quorum/leader-election/lock event loop (which is out of scope for this
gap and risks destabilizing well-tested machinery unrelated to selection
policy).

Instead, this change adds a small, self-contained experiment harness,
`kgroup.resource.ResourceAwareLeaderSimulator`, that:

* Reuses the **exact same selection primitive** as `KGroup` — a SHA-1
  based `Hash(epoch, deviceID, targetID)` and "rank ascending, lowest
  hash wins" — so the comparison is apples-to-apples with how the real
  simulator already selects members/leaders.
* Adds the resource/hazard model the gap analysis calls for (battery,
  CPU load, and a failure hazard driven by both), and implements the two
  competing policies side-by-side over the *same* per-device randomness.
* Implements the exact mechanisms named in the gap spec (mapped below).

## What was added

| File | Change |
|---|---|
| `src/main/java/kgroup/resource/ResourceAwareLeaderSimulator.java` | New standalone experiment (see below) |

No existing file was modified. `KGroup.java`, `KGroupManager.java`, and
the rest of the live event loop are untouched — this closes the gap the
same way Gap 3 did: as an additive, isolated experiment, not a change to
production selection code (which would need the full resource model
wired into every device's runtime state to do safely).

## Mechanisms implemented (mapped to the spec)

| Spec item | Where it lives |
|---|---|
| `Hash(epoch, deviceID, targetID)` | `hash(epoch, deviceID, targetID)` |
| `hashRanks()` / `selectCoterie()` | `hashRanks(...)`, `selectCoterie(...)` |
| Lowest-hashed member = leader (baseline) | `Policy.RANDOM_HASH` path in `selectCoterie` (no filtering applied) |
| Epoch-based migration (Algorithm 1) | epoch-boundary re-selection in `runOnce`, costed via `migrationCostPerChangedMember` per changed member |
| Failure-triggered re-election | leader-failure handling in `runOnce`, costed via `reconstructionCostMult * K` per re-election |
| New `RESOURCE_AWARE` policy | `selectCoterie`: filters to `battery > batteryThreshold AND cpuLoad < cpuThreshold` before hash ranking; falls back to `RANDOM_HASH` over the full pool if fewer than `K` candidates qualify |

## Resource / hazard model

Implemented exactly per spec, in `runOnce`:

* **Battery** — an epoch spent leading applies a net energy deficit
  (`leaderBatteryDrain`, no offsetting recharge that epoch); an epoch
  *not* spent leading applies a small passive recharge
  (`idleBatteryRecharge`) that approximately offsets idle drain.
* **CPU load** — a noisy, mean-reverting random walk around an idle
  baseline (`cpuBaseline`, `cpuReversion`, `cpuNoiseSigma`), plus a
  persistent upward bump while leading (`leaderCpuBump`).
* **Failure hazard** — increases quadratically with battery depletion
  `((100 - battery)/100)^2` and with CPU load `(cpuLoad/100)^2`:
  `hazard = hazardBase + hazardBatteryCoeff*depletion^2 + hazardCpuCoeff*cpuFrac^2`.
  Each epoch, a device currently leading one or more coteries is checked
  against this hazard; on failure it is marked failed (excluded from
  selection until it "reboots" after `recoveryEpochs`), and every coterie
  it was leading counts one re-election plus `reconstructionCostMult * K`
  of state-transfer overhead.

## Experiment structure

* `S = 100` devices, partitioned each epoch into `T = S / K` independent
  coteries/targets (`K = 2F + 1`, default `F = 2` → `K = 5`, `T = 20`),
  mirroring CoMesh's per-entity `KGroup` model where each device/routine
  "target" gets its own coterie and leader.
* `G = 30` epochs per run. At every epoch boundary, every target's
  coterie is re-selected from scratch from the currently-alive devices
  (Algorithm 1-style epoch migration), and the membership delta versus
  the previous epoch is costed.
* Leadership "quality" (healthy-device leadership share, Gini) is
  measured **at the moment of selection** — i.e. it reflects whether the
  *policy* picked a healthy device, not whether that device happened to
  still be healthy after a subsequent epoch's own drain.
* Metrics reported per run: re-election count, state-transfer overhead
  (migration cost + reconstruction cost, in abstract "units"), the Gini
  coefficient of per-device leadership-epoch counts (lower = more evenly
  distributed leadership load), and the healthy-device leadership share
  (fraction of leader-selections where the selected device's battery was
  above a *fixed* reference threshold, `healthyRefThreshold = 50%`,
  independent of the policy's own eligibility threshold, so the metric
  stays comparable across the threshold sweep).
* Seeds: 30 for the headline run, 20 per sweep point (per spec),
  `RANDOM_HASH` and `RESOURCE_AWARE` are run over the *same* seed so
  each seed's initial battery/CPU draws are shared between both
  policies.

## Building and running

This file has no dependency on `javafx` and no third-party dependencies,
so it builds standalone even without Maven Central access:

```bash
mkdir -p target/classes
javac --release 17 -d target/classes \
  src/main/java/kgroup/resource/ResourceAwareLeaderSimulator.java

java -cp target/classes kgroup.resource.ResourceAwareLeaderSimulator
```

To build/run it as part of the full project (needed if you also want to
touch other CoMesh classes, since those do need `javafx.util.Pair`):

```bash
# Option A: Maven (if you have full Maven Central access)
mvn compile
mvn exec:java -Dexec.mainClass="kgroup.resource.ResourceAwareLeaderSimulator"

# Option B: direct javac (works even without Maven Central access)
JAVAFX_CP="/usr/share/openjfx/lib/javafx.base.jar:/usr/share/openjfx/lib/javafx.controls.jar:/usr/share/openjfx/lib/javafx.graphics.jar"
# (on some environments the jars instead live at
#  /usr/share/java/javafx-base.jar etc. - use whichever path your
#  `openjfx`/`javafx` package installed)
find src/main/java -name "*.java" > /tmp/sources.txt
mkdir -p target/classes
javac --release 17 -cp "$JAVAFX_CP" -sourcepath src/main/java -d target/classes @/tmp/sources.txt

java -cp "target/classes:$JAVAFX_CP" kgroup.resource.ResourceAwareLeaderSimulator
```

Either way prints the headline comparison table (`RANDOM_HASH` vs.
`RESOURCE_AWARE` at the default `batteryThreshold=30%` /
`cpuThreshold=70%`) and the `batteryThreshold` sweep (10 → 70, in steps
of 10, `cpuThreshold` fixed at 70%), and writes the sweep data to
`gap2_sweep_results.csv` in the working directory.

## Verifying the gap is closed

Run it and check:

1. **Headline table** — at the spec's default thresholds,
   `RESOURCE_AWARE` should show *fewer* re-elections, *lower*
   state-transfer overhead, a *lower* Gini coefficient (leadership spread
   more evenly across healthy devices instead of concentrated), and a
   *higher* healthy-device leadership share than `RANDOM_HASH`.
2. **Threshold sweep** — at low/moderate `batteryThreshold` (≤ 30–40),
   `RESOURCE_AWARE` should win cleanly on (most of) the metrics above,
   since the eligible pool is still large enough that filtering mostly
   just excludes genuinely depleted/overloaded devices. At high
   `batteryThreshold` (≥ 50–70), the eligible pool shrinks enough that
   the same handful of always-fully-charged devices get reused
   repeatedly — you should see the benefit erode or invert (higher
   Gini, more re-elections) as more coteries fall back to `RANDOM_HASH`
   because fewer than `K` devices qualify. This is the expected,
   realistic failure mode of an eligibility filter that's set too
   strict, not a bug.
3. `gap2_sweep_results.csv` gives you the raw per-threshold numbers to
   plot/inspect directly if you want to see the shape of the curve
   yourself instead of just the printed summary.

Exact numeric values depend on the calibration constants in `Params`
(drain/recharge rates, hazard coefficients, etc.) — these are documented
inline in `ResourceAwareLeaderSimulator.Params` and are safe to retune
for a different hardware/deployment assumption; the qualitative pattern
above (clean win at low thresholds, collapse at high thresholds) is what
should be checked, not exact numbers.

## Limitations / honest caveats

* This is a **standalone** harness, like the Gap 3 module — it does not
  re-run CoMesh's live quorum/lock/state-transfer message machinery, only
  the selection-policy + resource/hazard dynamics that Gap 2 is actually
  about. Migration/reconstruction costs are abstract "units" scaled by
  `K`, not real message byte counts (Gap 3's harness is the one that
  models real message bytes/hops, for the predicate-pushdown question).
* Coteries are independent per target and may overlap (a device can be a
  member — or leader — of more than one coterie in the same epoch),
  mirroring CoMesh's actual per-entity `KGroup` model rather than a
  single global partition.
* The `RESOURCE_AWARE` fallback rule (revert to `RANDOM_HASH` when fewer
  than `K` devices are eligible) is a deliberate, spec-following design
  choice, not a bug: forcing insufficiently-resourced devices into a
  coterie just to hit the fallback-free ideal would reintroduce the exact
  problem the gap is about.
