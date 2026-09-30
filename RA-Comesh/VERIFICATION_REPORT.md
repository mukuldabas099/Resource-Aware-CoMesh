# Independent Verification Report — CoMesh-sim Gap Implementations

This report documents an independent check of the gap fixes in this
simulator against `CoMesh_Research_Gap_Analysis.docx` (Gaps 2, 3, 4, 5, 7)
and against the unmodified base simulator (`CoMesh-sim.zip`). No code was
changed as a result of this review — the implementation already in this
zip was found to be correct and working. This report is additive
documentation only.

## What was checked

1. **Build.** Both the base and this (final) simulator were compiled from
   scratch with `javac` (Maven Central was not reachable in the review
   sandbox; direct `javac` compilation — already documented in
   `Readme_Simulator.md` — was used instead, per the project's own
   troubleshooting notes). Both compile cleanly with no errors.
2. **Existing test suite.** `MessageTest`, `DijkstraTest`, and
   `RoutineStatementTest` pass on both base and final. `KGroupManagerTest`,
   `KGroupTest`, and `NetworkTest` fail identically on **both** base and
   final (hardcoded relative workload paths / a pre-existing `K < 4F+1`
   guard) — these are pre-existing issues in the base simulator, not
   regressions introduced by the gap work.
3. **Each gap's deterministic proof** (`Gap4DeterministicProof`,
   `Gap5DeterministicProof`, `Gap7DeterministicProof`) was run standalone:
   all scenarios pass.
4. **Each gap's documented verification command** was independently
   re-run from a clean build and reproduced the exact numbers already
   recorded in `CoMesh_Gap4/5/7_Verification_RESULTS.md` and
   `GAP2_RESOURCE_AWARE_SELECTION.md` / `GAP3_PREDICATE_PUSHDOWN.md`.
5. **All three live flags together** (`-eam -pas -isc`, i.e. Gap 4 + 5 + 7
   simultaneously) were run in a single simulation to check for
   integration conflicts. No crashes; all three summaries printed
   correctly.
6. **Line-by-line diff review** against the base simulator of every
   touched file (`KGroup.java`, `KGroupManager.java`, `DeviceLock.java`,
   `LockRequest.java`, `Membership.java`, `DeviceKGroup.java`,
   `Simulator.java`, message payload classes) to confirm each change is
   additive/backward-compatible and gated behind an opt-in flag that
   defaults to off.

## Findings, by gap

| Gap | Integration style | Verdict |
|---|---|---|
| **Gap 2** — Resource-aware leader selection | Standalone experiment (`kgroup.resource.ResourceAwareLeaderSimulator`), **not** wired into the live `Simulator`/`KGroup` event loop | Runs correctly, reproduces documented numbers. Demonstrates the policy and its trade-offs, but does not change live simulator behavior. |
| **Gap 3** — Predicate push-down | Standalone experiment (`routine.pushdown.PredicatePushdownSimulator`), **not** wired into the live event loop | Runs correctly, reproduces documented numbers (~99% message/byte reduction, no latency cost). |
| **Gap 4** — Energy-aware coterie membership | **Live**, in-place change to `KGroup.newKGroupMembers(...)`, opt-in via `-eam`/`-nef` | Verified correct: default-off is a byte-for-byte no-op; deterministic proof passes; swap-in logic only triggers when a bucket is entirely low-energy and a stable candidate exists elsewhere. |
| **Gap 5** — Priority-aware lock scheduling | **Live**, in-place change to `DeviceLock`/`KGroupManager`/`DeviceKGroup`, opt-in via `-pas`/`-rpf` | Verified correct: default-off is a byte-for-byte no-op; deterministic proof (including the anti-starvation aging bound) passes; the three subtle bugs the implementation calls out (stale `queue.firstKey()` assumption, "self-defeating tick" re-peek, priority lost on admission) are real and the fixes are sound. |
| **Gap 7** — Intermittent device support | **Live**, in-place change to `KGroupManager.nodeFailureDetected`/`nodeJoined`/`requestDeviceLockForRoutine` and `KGroup.newKGroupMembers`, opt-in via `-isc`/`-dsf` | Verified correct: default-off is a byte-for-byte no-op; deterministic proof passes; reproduced the documented 9-device scenario exactly (8 genuine failures, 8 suppressed false-failures, 36 coterie retentions). |

## The one substantive gap between "documented" and "fully live"

Gaps 2 and 3 are implemented as **standalone, self-contained experiment
programs** that reuse pieces of the real simulator (the SHA-1 selection
hash, `dijkstra.Dijkstra` routing, `network.message.Message`
serialization) but run as separate `main()` entry points, not through
`Simulator`'s actual event loop. This is explicitly and honestly disclosed
in `GAP2_RESOURCE_AWARE_SELECTION.md` and `GAP3_PREDICATE_PUSHDOWN.md`,
with a stated reason: the underlying data model each gap needs (per-device
battery/CPU fields for Gap 2; live per-device state/reading updates
actually driving predicate evaluation for Gap 3) does not exist anywhere
in the base simulator's live device/routine runtime state — `KGroupManager
.checkOnRoutine(...)`, the method that would evaluate a real predicate,
has no caller anywhere in the base simulator, and predicate-relevant
`state`/`reading` fields are never updated from their initial values. Gap
4/5/7, by contrast, each name a mechanism that is already live in the base
simulator (LSH-style hash bucket selection, the per-device FIFO lock
queue, the failure detector) — so those three could be, and were, extended
in place.

This is a real, defensible distinction (verified by re-reading the base
simulator's own event loop and confirming `checkOnRoutine` is indeed
dead code there), not an inconsistency introduced by mistake — but it does
mean Gap 2 and Gap 3's "fix" is a proof-of-concept demonstrating the
policy works and quantifying its benefit, not a change to the simulator's
actual runtime behavior the way Gap 4/5/7 are. If a live-in-the-event-loop
version of Gap 2/3 is required (i.e. actually adding battery/CPU state to
`Membership`/`NodeMetrics` and wiring `checkOnRoutine` into the main
event loop), that is additional engineering work beyond what is in this
zip.

## Net verdict

No bugs found; no code changes were necessary. Gaps 4, 5, and 7 are
correctly, safely, and verifiably wired into the live simulator behind
opt-in flags that are proven no-ops by default. Gaps 2 and 3 are correct,
reproducible standalone demonstrations of their respective policies, with
an honestly documented (and, on review, accurate) reason why they were not
wired into the live event loop.
