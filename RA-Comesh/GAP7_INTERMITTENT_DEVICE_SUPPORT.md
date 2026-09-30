# Gap 7: Intermittent Device Support

This document describes the changes made to the CoMesh simulator to close
**Gap 7** from the Research Gap Analysis:

> **Current State:** CoMesh assumes device locations and membership are
> known and relatively stable; it tolerates churn (joins/failures) and
> "moderate mobility", but devices are still expected to be reachable and
> continuously part of the ad-hoc mesh once deployed.
>
> **The Gap:** Many real edge devices are intermittently available by
> design -- sleep-cycled sensors, battery-saving actuators, or devices
> that disconnect on a schedule rather than failing. CoMesh's failure
> detector and coterie-update logic (Section IV-C) cannot currently
> distinguish a sleeping device from a failed one, which can trigger
> unnecessary re-elections and state transfers.
>
> **Proposed Direction:** Design explicit support for scheduled /
> intermittent connectivity: let a device announce expected sleep windows
> so that device and routine coteries suppress false-failure triggers and
> lock requests during planned absence, instead of relying solely on the
> existing timeout-based failure detector.

## Why this is a live, in-place change

This simulator's stand-in for "the existing timeout-based failure
detector" is `KGroupManager.nodeFailureDetected(...)`, driven by
deterministic churn events (`f`/`j` lines) in the node schedule file --
every node's own `KGroupManager` reacts to the same scheduled absence the
same way, by flipping that node's `Membership` to `OFFLINE`. And "the
coterie-update logic" is `KGroup.newKGroupMembers(...)` (the same live
mechanism Gap 4 already extends), which excludes anything not `ONLINE`
from the next coterie. Both already exist and are live, so -- like
Gap 4/5 -- this change is made directly at those two points, plus a new,
self-contained "announced sleep window" registry next to them.

## What was added / changed

| File | Change |
|---|---|
| `src/main/java/kgroup/sleep/DeviceSleepRegistry.java` | New. Static registry of per-device announced sleep windows (`[start, end)` simulator timestamps), loaded from a sleep-schedule file, + the global on/off switch. |
| `src/main/java/kgroup/sleep/Gap7Metrics.java` | New. Verification counters (genuine failures / suppressed false-failures / coterie retentions / deferred+flushed lock requests) + summary printer. |
| `src/main/java/kgroup/sleep/DeferredLockRequest.java` | New. Tiny value type for a lock request parked while its target device is asleep. |
| `src/main/java/kgroup/sleep/Gap7DeterministicProof.java` | New. Standalone, JUnit-free deterministic proof (see verification step 6). |
| `src/main/java/kgroup/Membership.java` | Added a third state, `SLEEPING`, alongside `ONLINE`/`OFFLINE`. |
| `src/main/java/kgroup/KGroup.java` | `newKGroupMembers(...)`'s eligibility check now also admits a `SLEEPING` node as a candidate *if it is already an incumbent member of this k-group* (an announced sleep retains membership; a genuine failure still doesn't). |
| `src/main/java/KGroupManager.java` | `nodeFailureDetected(...)` checks the registry first: if the absence falls inside an announced window, the device is marked `SLEEPING` (not `OFFLINE`) and its automatic wake-up is scheduled, instead of running the genuine-failure path. `nodeJoined(...)` flushes any lock requests that were deferred while the device slept. `requestDeviceLockForRoutine(...)` defers (instead of sending) a lock request whose target device is currently `SLEEPING`. |
| `src/main/java/Simulator.java` | New CLI flags `-dsf <file>` (load a device sleep-schedule file) and `-isc` (turn intermittent-device support on); prints the Gap 7 summary at the end of a run when `-isc` was passed. |
| `workloads/scripts/createDeviceSleepSchedule.py` | New. Generates a synthetic device sleep schedule from a device topology, an intermittent-device probability, and a seed. |

No other file was touched.

## How it works

1. **Distinguishing sleep from failure.** The node schedule file's `f`
   events are this simulator's stand-in for "the failure detector fired".
   `nodeFailureDetected(deviceID, ...)` now asks
   `DeviceSleepRegistry.isAnnouncedSleep(deviceID, ts)` first. If true,
   this is a planned absence: `Membership.SLEEPING` is recorded (not
   `OFFLINE`), the device's own automatic wake-up is scheduled for the
   end of its announced window (reusing the exact same event-scheduling
   machinery the simulator already drives churn with -- a synthetic
   `NODE_JOINED` event), and the function returns *without* running the
   genuine-failure path below it. Otherwise -- an unannounced absence, or
   the feature is off -- behavior is byte-for-byte the original code:
   `Membership.OFFLINE`.
2. **Coterie retention.** `KGroup.newKGroupMembers(...)` already builds
   its candidate pool from every `ONLINE` node. The fix adds one more
   case: a `SLEEPING` node is *also* eligible, but only if it is already
   a member of this k-group (i.e. an incumbent riding out its own
   announced absence), never as a fresh pick for a k-group it wasn't
   already part of. A genuinely `OFFLINE` node is still excluded either
   way -- Gap 7 only changes what happens to *known, planned* absences.
3. **Lock-request deferral.** `requestDeviceLockForRoutine(...)` is the
   one place a message actually gets sent *to* the physical device (or
   whichever of its k-group members is currently standing in as leader).
   If that device is currently `SLEEPING`, the request is parked in a
   small per-device list instead of being sent into the void; when the
   device's scheduled wake-up fires (`nodeJoined(...)`), every parked
   request for it is automatically resent.

### Why quorum-internal traffic didn't need special-casing

A device's *lock state* is itself managed by a k-group of several nodes
(for fault tolerance), and one member of that k-group being temporarily
`SLEEPING` is exactly the kind of transient unavailability the existing
F-fault-tolerant quorum protocol (F+1 acks out of K) was already built to
tolerate -- no new logic was needed there. Only the two points above (the
failure-vs-sleep classification itself, and the one place a message goes
directly to the device entity) needed a change.

### A known, documented limitation

If the device that goes to sleep happens to be its own k-group's elected
leader, its own communications naturally stall until it wakes (bounded by
its own announced window) rather than triggering an early hand-off --
this is a deliberate trade-off (avoiding exactly the "unnecessary
re-election" Gap 7 complains about) rather than an oversight, but it does
mean latency for that k-group is bounded by the sleep window's length,
not by the usual election timeout. A cooperative pre-sleep leader
hand-off is a natural follow-up but out of scope for this change.

## New CLI flags (`Simulator`)

| Flag | Meaning |
|---|---|
| `-dsf <file>` / `-deviceSleepFile <file>` | Loads a device sleep-schedule file (`deviceID sleepStartTs sleepEndTs` per line, one line per window) into the registry. |
| `-isc` / `-intermittentDeviceSupport` | Turns Gap 7 support on. Takes no value. Default: off (complete no-op -- every absence is still handled as a genuine failure, exactly as before). |

## Device sleep-schedule workload generator

```bash
python3 workloads/scripts/createDeviceSleepSchedule.py \
  -d "workloads/device_topology_d9_grid3,3.txt" -ip 0.4 -w 2 -minLen 20 -maxLen 40 -e 500 -s 0
```

`-d` a device topology file, `-ip` the probability a device is generated
as intermittent, `-w` how many non-overlapping sleep windows to give each
intermittent device, `-minLen`/`-maxLen` window-length bounds, `-e` the
schedule horizon, `-s` a seed. Devices not listed in the output file
never sleep (fail-open, same convention as Gap 4's node energy file).

See `CoMesh_Gap7_Verification_RESULTS.md` for full, actual command output
from running every verification step in this sandbox.
