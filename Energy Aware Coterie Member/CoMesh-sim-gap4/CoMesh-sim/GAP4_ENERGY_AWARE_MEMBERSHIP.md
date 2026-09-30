# Gap 4: Energy-Aware Coterie Membership

This document describes the changes made to the CoMesh simulator to close
**Gap 4** from the Research Gap Analysis:

> **Current State:** Coterie members are chosen using Locality-Sensitive
> Hashing (LSH), which clusters members by physical proximity to keep
> communication local (Section IV-D of the paper).
>
> **The Gap:** LSH optimizes for locality only. If, by chance, every
> device that LSH places into a coterie happens to be low-energy, the
> elected leader will repeatedly fail and trigger repeated re-elections,
> since the paper's failure-response mechanism only reacts after the fact
> (Section IV-C) rather than preventing the situation.
>
> **Proposed Direction:** Add an energy constraint on top of LSH bucket
> selection: when forming or migrating a coterie, guarantee that at least
> one energy-stable (sufficient battery/CPU headroom) device is included
> in the bucket, even if it is not the nearest candidate by locality alone.

## Why this change is different from the Gap 3 change

Gap 3's proposed mechanism (predicate push-down) turned out not to be
wired into the live event loop at all, so that change added a new,
separate standalone experiment instead of touching the core simulator.

Gap 4 is the opposite situation: the mechanism the gap is about —
`KGroup.newKGroupMembers(...)`, the hash-based bucket-selection routine
that stands in for the paper's LSH-based locality clustering — **is**
live, and is called every epoch from `KGroupManager` (both for normal
coterie migration via `updateKGroup()` and for post-failure replacement
via `replaceFailedNodes()`). So this change modifies that method directly,
rather than bolting on a side experiment.

To keep the change safe:
* The simulator previously had **no notion of node energy at all** — no
  battery, no CPU load. This change adds it as a new, optional, additive
  concept (`kgroup.NodeEnergyProfile`), not by touching any existing
  field.
* Every touched constructor (`KGroup`, `DeviceKGroup`, `RoutineKGroup`,
  `KGroupManager`) keeps its **original signature working exactly as
  before**, via an overload. The old constructors delegate to the new
  ones with `energyAware=false` and an empty profile map, which makes
  `newKGroupMembers` skip the new code path entirely and reproduces the
  original hash-only behavior bit-for-bit. All existing unit tests
  (`KGroupTest`, `KGroupManagerTest`, etc.), which call the old
  constructors directly, are unaffected.
* Energy profiles are **static per run** (loaded once from a workload
  file), not dynamically drained during the run by the live decentralized
  loop. Every node in the mesh independently recomputes the same
  coterie/leader decisions from the same deterministic inputs (that's how
  the existing hash-only selection already achieves "consensus" without
  real communication); if energy were drained locally inside each node's
  own replica of a k-group, the same shared battery would get drained
  once per *observing* node instead of once globally, and different
  nodes' local views could diverge. A static, non-uniform, file-driven
  battery/CPU distribution is enough to reproduce and validate exactly
  the scenario the gap describes (some devices in a coterie are, by
  chance, all low-energy) without introducing that consistency bug.

## What was added/changed

| File | Change |
|---|---|
| `src/main/java/kgroup/NodeEnergyProfile.java` | **New.** Per-node resource state: `batteryPct`, `cpuLoadPct`, `isEnergyStable(batteryThreshold, cpuThreshold)`, and a `drain(...)` helper for anyone who wants to model dynamic drain in a more controlled context (e.g. a standalone harness). |
| `src/main/java/kgroup/KGroup.java` | Added `energyProfiles` / `energyAwareMembership` fields, a backward-compatible constructor overload, and `applyEnergyConstraint(...)` — called from `newKGroupMembers(...)` only when energy-awareness is enabled. Also added static counters (`totalMembershipFormations`, `energyForcedSwaps`, `energyConstraintInfeasible`) for verification/metrics. |
| `src/main/java/kgroup/DeviceKGroup.java` | Added a constructor overload forwarding the energy map/flag to `KGroup`. |
| `src/main/java/kgroup/RoutineKGroup.java` | Same overload, for routine coteries. |
| `src/main/java/KGroupManager.java` | Added `energyProfiles` / `energyAwareMembership` fields, a constructor overload, and now passes them into every `DeviceKGroup`/`RoutineKGroup` it creates. |
| `src/main/java/Simulator.java` | Added a `simulate(...)` overload that accepts `nodeEnergyFilename` + `energyAwareMembership`, loads the workload file into a single shared `Map<String, NodeEnergyProfile>`, passes it to every node's `KGroupManager`, and prints a Gap 4 summary at the end of the run. Added CLI flags `-nef <file>` / `-eam` in `main()`. |
| `workloads/scripts/createNodeEnergy.py` | **New.** Generates a `nodeID batteryPct cpuLoadPct` workload file from an existing node-list file, with a `-lep` (low-energy probability) knob to deliberately reproduce the "unlucky, all-low-energy coterie" scenario from the gap. |
| `workloads/sample_node_energy.txt` | **New.** Example energy file matching `workloads/sample_node_list.txt`. |

No existing behavior changes unless you opt in with `-eam` (and supply
`-nef`). Nothing about message formats, the network layer, locking,
leader election *policy* (`LeaderElectionPolicy`/Gap 2 is untouched),
predicate handling, or failure detection was modified.

## The algorithm (`KGroup.applyEnergyConstraint`)

`newKGroupMembers(membershipList, desired)` first computes the bucket
exactly as before: hash every online candidate node with
`hash(nodeID, epoch, N)` and take the `desired` smallest hashes (this is
the simulator's stand-in for LSH-based locality clustering). When
`energyAwareMembership` is on, the following runs *after* that:

1. If any node already in the chosen bucket is "energy-stable"
   (`batteryPct >= batteryStableThresholdPct` **and**
   `cpuLoadPct <= cpuLoadStableThresholdPct`, defaults 30% / 70%),
   do nothing — locality is left completely untouched. This is the common
   case and matches the gap's "even if it is not the nearest candidate by
   locality alone" framing: we only override locality when we have to.
2. Otherwise, walk the *same* hash order used for locality among the
   online candidates that are **not already in the bucket**, and find the
   first (closest-by-hash) one that is energy-stable.
3. If one is found, swap it in for the bucket's *worst*-hash (least
   local) current member, preserving bucket size (`K` or, for
   `replaceFailedNodes`, `F`). This is exactly "guarantee that at least
   one energy-stable device is included in the bucket, even if it is not
   the nearest candidate by locality alone."
4. If no energy-stable candidate exists anywhere online, the constraint
   is infeasible (every device really is low on energy) and the original,
   locality-only bucket is left as-is — there's nothing better to put in
   it. This is counted separately
   (`KGroup.getEnergyConstraintInfeasible()`) so a run can distinguish
   "fixed it" from "couldn't fix it."

Because `replaceFailedNodes()` (used after a node failure, e.g. from a
low-battery device actually dying) also calls `newKGroupMembers(...)`
internally, post-failure coterie repair benefits from the same
energy-aware guarantee automatically, with no separate code path needed.

## How to generate an energy workload and run it

```bash
# Generate a per-node energy profile for an existing node list, with 70%
# of nodes deliberately drawn as "low energy" to reproduce the scenario
# the gap describes.
python3 workloads/scripts/createNodeEnergy.py \
  -n workloads/node_list_d25_np0.9.txt -lep 0.7 -s 3 \
  -o workloads/node_energy_d25_np0.9_demo.txt
```

Then run the simulator with `-nef <file>` and `-eam`:

```bash
JAVAFX_CP="/usr/share/openjfx/lib/javafx.base.jar:/usr/share/openjfx/lib/javafx.controls.jar:/usr/share/openjfx/lib/javafx.graphics.jar"

java -cp "target/classes:$JAVAFX_CP" -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 0 -dn 25 -dts grid -dtd 5,5 \
  -np 0.9 -cp 0.0 -nsd uniform \
  -f 1 -el 100 -rm 5 -rd 25 \
  -nef workloads/node_energy_d25_np0.9_demo.txt -eam
```

At the end of the run you'll see a summary like:

```
---- Gap 4: Energy-Aware Coterie Membership summary ----
Coterie (re)formations checked: 138
Formations where locality-only hashing picked an all-low-energy bucket and the energy constraint swapped a member in: 23
Formations where every online candidate was low-energy (constraint infeasible): 0
```

`23` formations (~17% of the 138 checked, for this particular seed/node
list) would have produced a coterie with *no* energy-stable member at all
under the original hash-only selection; the constraint fixed every one of
them for this run (`0` infeasible).

## Verifying the change

### 1. It compiles and doesn't change default behavior

```bash
# Baseline run (identical flags, no -nef/-eam): behaves exactly as the
# unmodified simulator, no Gap 4 summary is printed.
java -cp "target/classes:$JAVAFX_CP" -Xss4m -Xmx4096m Simulator \
  -e 500 -rn 0 -dn 25 -dts grid -dtd 5,5 -np 0.9 -cp 0.0 -nsd uniform \
  -f 1 -el 100 -rm 5 -rd 25
```
No `Gap 4` output appears, and the resulting `outputs/*.csv` is produced
the same way as before -- confirming the change is a no-op unless you opt
in.

### 2. Existing unit tests still pass/behave the same

```bash
find src/test/java -name "*.java" > /tmp/test_srcs.txt
javac --release 21 -cp "target/classes:$JAVAFX_CP:/usr/share/java/junit-platform-console-standalone.jar" \
  -sourcepath src/test/java -d target/test-classes @/tmp/test_srcs.txt

java -cp "target/classes:target/test-classes:$JAVAFX_CP:/usr/share/java/junit-platform-console-standalone.jar" \
  org.junit.platform.console.ConsoleLauncher -c kgroup.KGroupTest --details=tree
java -cp "target/classes:target/test-classes:$JAVAFX_CP:/usr/share/java/junit-platform-console-standalone.jar" \
  org.junit.platform.console.ConsoleLauncher -c dijkstra.DijkstraTest --details=tree
```
(`KGroupManagerTest`, `SimulatorTest`, and `network.NetworkTest` already
fail on the unmodified, unzipped repo too — they reference workload
subdirectories/files that aren't present until you run the workload-prep
steps in `Readme_Simulator.md`; that's pre-existing and unrelated to this
change.)

### 3. The fix actually does what Gap 4 asks (deterministic, isolated check)

The clearest way to see the exact "all-low-energy bucket avoided" event
is a tiny, deterministic Java snippet using the real `DeviceKGroup` class
directly (no full simulation needed): construct 7 online nodes, give only
2 of them ("E" and "G") a stable energy profile and the rest a drained
one (10% battery, 90% CPU), then call `newKGroupMembers(...)` once with
`energyAware=false` and once with `energyAware=true` for several entity
IDs/epochs:

```
entity=dev19
  baseline (hash-only, ALL LOW ENERGY): [F, D, A, B, C]
  energy-aware fix:                    [F, D, A, B, G]  stable=true
entity=dev29
  baseline (hash-only, ALL LOW ENERGY): [D, C, B, A, F]
  energy-aware fix:                    [D, C, B, A, G]  stable=true
```
This shows, node-for-node, the hash-only selection landing on a bucket
with zero energy-stable members, and the energy-aware version swapping
in a stable device (the paper's "guarantee at least one energy-stable
device is included in the bucket, even if it is not the nearest candidate
by locality alone") while leaving every other member's placement (i.e.
locality) untouched.

### 4. Full-run comparison (realistic scenario, 22 nodes)

Run the same `-e 500 ... -dn 25 -np 0.9` command from step 1 above twice,
once without and once with `-nef workloads/node_energy_d25_np0.9_demo.txt
-eam`. The energy-aware run's summary (`Coterie (re)formations checked /
energyForcedSwaps / energyConstraintInfeasible`) tells you directly how
many times, over the whole run, the fix mattered — a non-zero
`energyForcedSwaps` count together with `energyConstraintInfeasible == 0`
demonstrates the mesh had spare energy-stable capacity that pure
locality-based hashing was failing to use.

### 5. Tune the thresholds/scenario

* `KGroup.batteryStableThresholdPct` / `cpuLoadStableThresholdPct`
  (defaults 30 / 70) define "energy-stable"; lower the battery threshold
  or raise the CPU one to make the constraint easier to satisfy, or the
  reverse to stress-test the "constraint infeasible" path.
* `createNodeEnergy.py -lep <0..1>` controls how many nodes are
  generated in the low-energy regime — raise it toward 1.0 to reproduce
  network-wide energy scarcity and observe `energyConstraintInfeasible`
  start to increase.
