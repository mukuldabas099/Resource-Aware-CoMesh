# Gap 5: Priority-Aware Lock Scheduling

This document describes the changes made to the CoMesh simulator to close
**Gap 5** from the Research Gap Analysis:

> **Current State:** A device's lock queue is served strictly FIFO: when a
> lock is released, the device leader hands it to whichever routine has
> been waiting longest (Section V-B).
>
> **The Gap:** FIFO does not distinguish between a routine of high
> operational importance (e.g. a safety or security action) and a
> low-priority convenience routine. A long-waiting low-priority routine
> can therefore delay a newly-arrived but more critical one.
>
> **Proposed Direction:** Introduce a priority field per routine and
> replace (or augment) the FIFO wait list with a priority-aware
> scheduling policy, while re-examining the paper's deadlock-freedom
> proof (Theorem 2) to ensure priority-based reordering does not
> reintroduce cycles.

## Why this is a live, in-place change (like Gap 4)

Gap 5 names the exact mechanism it wants extended: the per-device wait
list. This simulator's stand-in for it already exists and is live:
`kgroup.state.DeviceLock` keeps every device's pending requests in a
`SortedMap<Integer reqSeqNo, LockRequest> queue` keyed by admission
order, and "serve strictly FIFO" is exactly `queue.firstKey()` --
consulted every time a lock is granted (`lock(...)`) or released
(`release(...)`). So, as with Gap 4, there is no missing data model to
invent -- just a missing scheduling policy on an existing, live path.
The change is made directly in `kgroup/state/DeviceLock.java`, plus a
new, self-contained scheduling-policy class next to it.

## What was added / changed

| File | Change |
|---|---|
| `src/main/java/kgroup/priority/PriorityLockScheduler.java` | New. The scheduling policy itself: `peekHead(...)` (pure) picks the pending request with the highest *effective* priority (declared priority + an anti-starvation aging bonus); `tick(...)` advances the aging clock by one real scheduling event and returns the decided head. Global on/off switch + configurable aging threshold. |
| `src/main/java/kgroup/priority/Gap5Metrics.java` | New. Verification counters (head computations / priority overtakes / aging promotions) + summary printer. |
| `src/main/java/kgroup/priority/Gap5DeterministicProof.java` | New. Standalone, JUnit-free deterministic proof (see verification step 6). |
| `src/main/java/kgroup/state/LockRequest.java` | Added a `priority` field (default `NORMAL`), a 3-arg constructor, and a getter. Identity (`equals`/`contains`) is unaffected -- still purely `(routineID, routineSeqNo)`. |
| `src/main/java/kgroup/state/DeviceLock.java` | `getQueueHead()`/`getQueueHeadReqSeqNo()` now consult `PriorityLockScheduler.peekHead(...)` instead of `queue.firstKey()` directly. `requestReplicated(...)` and `release(...)` call `PriorityLockScheduler.tick(...)` once per real admission/release event. `lock(...)`'s "next expected reqSeqNo" check is relaxed under priority scheduling (see below). Added explicit `lockedReqSeqNo` tracking (see "Correctness fixes" below) and a majority-vote recreation of it in `addLocalState(...)`. The lock-queue repair loop in `release`/`replicateRelease`/`replicateLock` (which used to prune every entry below the just-granted reqSeqNo) is skipped when priority scheduling is enabled. |
| `src/main/java/kgroup/DeviceKGroup.java` | `addNewLockRequest(...)` and `replicateLockRequest(...)` gained priority-carrying overloads; `lockRequestReplicated(...)` now returns the aging tick's decided head (see below). |
| `src/main/java/network/message/payload/lock/LockRequestMessagePayload.java`, `LockRequestQuorumMessagePayload.java` | Added a `priority` field with a backward-compatible constructor overload defaulting to `NORMAL`. |
| `src/main/java/routine/Routine.java` | Added a `priority` field (default `NORMAL`) + getter/setter. |
| `src/main/java/KGroupManager.java` | `requestDeviceLockForRoutine(...)` reads the routine's declared priority and threads it through `LockRequestMessagePayload`/`gatherQuorumForDeviceLockRequest(...)`/`LockRequestQuorumMessagePayload`. The `LOCK_REQUEST_QUORUM_ACK` handler's "is the device free and is this newly-admitted request next" check no longer assumes `reqSeqNo == lastLockedReqSeqNo + 1` when priority scheduling is enabled (see below). `gatherQuorumForDeviceLockAcquisition(...)` looks up a request's reqSeqNo by content (`getLockRequestSeqNo`) instead of re-peeking the scheduler (see "Correctness fixes"). |
| `src/main/java/Simulator.java` | New CLI flags `-rpf <file>` (load a routine priority file), `-pas` (turn priority-aware scheduling on), `-pasAging <n>` (aging threshold); prints the Gap 5 summary at the end of a run when `-pas` was passed. |
| `workloads/scripts/createRoutinePriorities.py` | New. Generates a synthetic routine priority file from a routine map, HIGH/LOW probabilities, and a seed. |

No other file was touched.

## How the scheduling policy works

Every pending request's **effective priority** is `declaredPriority +
floor(overtakeCount / agingThreshold)`, where `overtakeCount` is how many
times that request has been passed over. `peekHead(...)` picks the
pending entry with the highest effective priority, ties broken by lowest
reqSeqNo (oldest first, i.e. the original FIFO tie-break). This is a pure
function of the queue's current contents plus the current overtake
counts -- calling it twice in a row without anything happening in
between always gives the same answer.

`tick(...)` is the only thing allowed to *advance* aging: called once per
real event (a new request being admitted, or a release opening the
device up), it computes the round's head via `peekHead`, increments the
overtake count of every request that was **not** the winner, and returns
the winner. Separating "peek" (read-only, safe to call any number of
times) from "tick" (mutates, called exactly once per real event) turned
out to matter -- see "Correctness fixes" below.

### Re-examining Theorem 2 (deadlock/starvation freedom)

* **Safety (mutual exclusion) is untouched.** Exactly one reqSeqNo is
  ever marked locked at a time, gated by the same F+1 quorum-replication
  protocol as before; priority only changes *which* pending reqSeqNo the
  leader chooses to grant next, never *whether* more than one can be
  granted concurrently.
* **Liveness (no starvation) is what the aging bonus buys back.**
  Without it, a steady stream of HIGH-priority arrivals could in
  principle starve an older LOW-priority request forever -- a genuine
  regression versus FIFO's trivial bounded wait. With aging, every
  pending request's effective priority is non-decreasing over time and
  strictly increases every `agingThreshold` times it is passed over, so
  after at most `(MAX_PRIORITY - itsOwnPriority) * agingThreshold`
  overtakes it reaches the maximum effective priority, at which point it
  can only still be passed over by *other* already-maxed-out requests --
  among which the tie-break is lowest reqSeqNo, so it wins the very next
  time it's the oldest such request. Bounded wait therefore still holds
  for every request, exactly as Theorem 2 requires; only the bound
  changes from "queue position" to "queue position AND age", and the
  proof in `Gap5DeterministicProof` (scenario 3) demonstrates this bound
  empirically, not just asserts it.

### Correctness fixes this change needed (found via the proof, not assumed)

Building this surfaced three real bugs that a naive implementation would
have shipped with -- all three are things that were previously invisible
because FIFO granting order made them unobservable:

1. **`getLocker()`/`getLockerReqSeqNo()` assumed the locked entry was
   always `queue.firstKey()`.** True under FIFO, false once a
   higher-priority request can be granted while an older, lower-priority
   one is still queued behind it. Fixed by tracking `lockedReqSeqNo`
   explicitly (with majority-vote recreation on leader failover, mirroring
   how `reqSeqNo` is already recreated in `addLocalState`).
2. **A "self-defeating tick": peeking the head right after a
   scheduling event's own aging increment could disagree with what that
   very event decided.** E.g. `release()` used to call `tick()` (which
   increments overtake counts) and then immediately call
   `getQueueHeadReqSeqNo()` again to report "next" -- but the increments
   `tick()` just applied could themselves push a different request's
   aging bonus past the threshold, changing the answer a moment later.
   Fixed by having `tick()`/`requestReplicated(...)` return the decided
   head directly, so callers never re-peek after a mutation. The same
   bug existed in production code
   (`KGroupManager.gatherQuorumForDeviceLockAcquisition`, which re-peeked
   via `getQueueHeadReqSeqNo(...)` right after `release()`/
   `requestReplicated(...)` had already ticked) -- fixed the same way,
   by looking the reqSeqNo up by content (`getLockRequestSeqNo`, which
   was already an existing method) instead.
3. **Priority silently lost on admission.** `DeviceKGroup`'s
   `lockRequestReplicated(...)` built a fresh `LockRequest(routineID,
   routineSeqNo)` (default priority) instead of reusing the
   already-admitted, correctly-prioritized entry sitting in
   `newLockRequests`. Fixed in `DeviceLock.requestReplicated(...)`, which
   now prefers the stored entry.

All three are exercised (and would have failed loudly) in
`Gap5DeterministicProof`'s scenario 2.

## New CLI flags (`Simulator`)

| Flag | Meaning |
|---|---|
| `-rpf <file>` / `-routinePriorityFile <file>` | Loads a routine priority file (`routineID LOW\|NORMAL\|HIGH` per line) and applies it to the routines built for this run. |
| `-pas` / `-priorityAwareScheduling` | Turns the Gap 5 scheduling policy on. Takes no value. Default: off (complete no-op, byte-for-byte the original FIFO queue). |
| `-pasAging <n>` | Anti-starvation aging threshold (default 3): how many times a request must be passed over before its effective priority is bumped by one level. |

## Routine priority workload generator

```bash
python3 workloads/scripts/createRoutinePriorities.py \
  -r workloads/routine_map_r8_d9_a3_uniform.txt -hip 0.25 -lop 0.25 -s 0
```

`-r` a routine->devices map file (same one already used elsewhere in the
pipeline), `-hip`/`-lop` the probability a routine is generated as
HIGH/LOW priority (the remainder is NORMAL), `-s` a seed. Routines not
listed in the output file default to NORMAL (fail-open, same convention
as Gap 4's node energy file).

See `CoMesh_Gap5_Verification_RESULTS.md` for full, actual command output
from running every verification step in this sandbox.
