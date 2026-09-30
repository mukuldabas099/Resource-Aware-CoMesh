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
> in the bucket, even if it is not the nearest candidate by locality
> alone.

## Why this is a live, in-place change (unlike Gap 2 / Gap 3)

Gap 4 explicitly names the mechanism it wants extended -- "LSH bucket
selection" -- and this simulator's stand-in for that mechanism already
exists and is already live: `kgroup.KGroup.newKGroupMembers(...)` ranks
every online candidate node by `hash(nodeID, epochNo, N)` (a SHA-1 based
consistent hash -- the paper's Hash(epoch, deviceID, targetID)) and takes
the lowest-hashed `desired` nodes as the new coterie. It is called from
`updateKGroup(...)` at every epoch boundary (coterie formation/migration)
and from `replaceFailedNodes(...)` after a failure (coterie repair) --
i.e. exactly "when forming or migrating a coterie". Unlike Gap 2/3, there
was no missing data model to invent from scratch here, just a missing
constraint on an existing, live selection path -- so this change is made
directly in `KGroup.java`, in place.

## What was added / changed

| File | Change |
|---|---|
| `src/main/java/kgroup/energy/NodeEnergyRegistry.java` | New. Static registry of per-node `(battery %, cpuLoad %)` profiles + the energy-stability policy (`battery >= 30% AND cpuLoad <= 70%`) + the global on/off switch for the constraint. |
| `src/main/java/kgroup/energy/Gap4Metrics.java` | New. Verification counters (formations checked / swapped / infeasible) + summary printer. |
| `src/main/java/kgroup/energy/Gap4DeterministicProof.java` | New. Standalone, JUnit-free deterministic proof (see verification step 6). |
| `src/main/java/kgroup/KGroup.java` | `newKGroupMembers(...)` now calls `applyEnergyConstraint(...)` (new private method) whenever the registry is enabled. |
| `src/main/java/Simulator.java` | New CLI flags `-nef <file>` (load a node energy file) and `-eam` (turn the constraint on); prints the Gap 4 summary at the end of a run when `-eam` was passed. |
| `workloads/scripts/createNodeEnergy.py` | New. Generates a synthetic node energy file from a node list, a low-energy probability, and a seed. |

No other file was touched. `KGroupManager.java`, `Dijkstra.java`, `Graph.java`,
and the rest of the live event loop / message-passing machinery are
unchanged, as is the pre-existing `K < 4*F+1` guard in the `KGroup`
constructor.

## How the constraint works

`newKGroupMembers(membershipList, desired)` already builds `hashes`, a
`SortedMap<hash, nodeID>` of every online candidate ascending by hash
value, and takes the first `desired` entries as the new bucket
(`newMembers`). When the registry is enabled, `applyEnergyConstraint(...)`
is applied on top of that bucket:

1. If `newMembers` already contains at least one energy-stable node
   (`battery >= 30% AND cpuLoad <= 70%`), nothing changes -- the
   locality-only choice already satisfies Gap 4 and is left untouched.
2. Otherwise, scan the full ascending-hash candidate list for the
   lowest-hashed (best-locality) energy-stable node **not already in the
   bucket**. If one exists, swap it in for the *worst-hashed* member
   currently in the bucket (the last element of `newMembers`, since the
   bucket is exactly the lowest `desired` hashes) -- every other member's
   locality-driven placement is left unchanged.
3. If no energy-stable candidate exists anywhere in the online pool, the
   constraint is infeasible for this formation; the original
   locality-only bucket is kept as-is (and it's counted as an
   infeasible formation in the summary, not silently ignored).

A node with no energy profile on record is treated as energy-stable
(fail-open), so loading a partial energy file, or turning on `-eam`
without `-nef`, never changes behavior for nodes the registry has no data
for.

### Why counts are per-node, not per-formation

Every node in the simulator keeps its own local `KGroupManager` and
independently recomputes the same deterministic hash ranking for every
k-group it knows about. `Gap4Metrics` intentionally counts every one of
those node-local recomputations (not a de-duplicated, single count per
logical formation event), since each is a real, independent application
of the constraint from that node's point of view. With `N` online nodes
and a k-group that (re)forms once per epoch, expect roughly
`N x (#epochs)` formation checks in the summary -- this is exactly what
the verification run below shows (23 online nodes x 6 epochs = 138).

## New CLI flags (`Simulator`)

| Flag | Meaning |
|---|---|
| `-nef <file>` / `-nodeEnergyFile <file>` | Loads a node energy file (`nodeID batteryPct cpuLoadPct` per line) into the registry. |
| `-eam` / `-energyAwareMembership` | Turns the Gap 4 constraint on. Takes no value. Default: off (complete no-op, byte-for-byte the original locality-only selection). |

## Node energy workload generator

```bash
python3 workloads/scripts/createNodeEnergy.py \
  -n workloads/node_list_d25_np0.9.txt -lep 0.7 -s 3 \
  -o workloads/node_energy_d25_np0.9_demo.txt
```

`-n` a node list file (same format as the rest of the workload pipeline),
`-lep` the probability a node is generated as low-energy, `-s` a seed.
Low-energy nodes get a `(battery, cpuLoad)` draw guaranteed to fail at
least one of the two thresholds; stable nodes get a draw guaranteed to
pass both.

See `CoMesh_Gap4_Verification_RESULTS.md` for full, actual command output
from running every verification step in this sandbox.
