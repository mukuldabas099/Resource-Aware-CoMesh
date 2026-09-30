# RA-CoMesh: Resource-Aware Performance Optimization for Decentralized Edge-IoT Meshes

**RA-CoMesh** extends the [CoMesh](https://ieeexplore.ieee.org/) simulator (*"There is more control in egalitarian edge IoT meshes"*, Karanika et al., IEEE TNSM 2026) with resource-aware coordination. The original CoMesh picks leaders, coterie members, and lock order using only device identity and arrival order. RA-CoMesh adds awareness of **battery, CPU load, task priority, and planned sleep**, while keeping CoMesh's core properties: **zero-message coordination, quorum safety, liveness, and locality preservation**.

This repository (`CoMesh-sim`) is a Maven project containing the modified Java simulator, the extension implementations, workloads, and verification logs.

> **Authors:** Debjani Ghosh, Saloni Vishvkarma, Mukul Dabas
> School of Computer Science Engineering and Technology, Bennett University, Greater Noida, India

---

## Table of Contents
1. [Background](#background)
2. [The Five Extensions](#the-five-extensions)
3. [Key Results](#key-results)
4. [Important: Quorum / Coterie-Size Discrepancy](#important-quorum--coterie-size-discrepancy)
5. [Getting the Code](#getting-the-code)
6. [Build, Run, Test](#build-run-test)
7. [CLI Parameters](#cli-parameters)
8. [Default Simulation Parameters](#default-simulation-parameters)
9. [Repository Layout](#repository-layout)
10. [Limitations and Future Work](#limitations-and-future-work)
11. [Citation](#citation)

---

## Background

Edge IoT deployments (smart farms, buildings, factories) need devices to coordinate locally without a cloud or a single gateway/cluster head. CoMesh does this with small device groups called **coteries**:

- Time is split into **epochs**. Within each epoch, coterie members and leaders are derived from `Hash(epoch, deviceID, targetID)`, so no messages are needed for election.
- A coterie is a subset of `K` devices around a target (a device or a routine). The leader is the member with the smallest hash (*SMALLEST HASH* policy).
- Device coteries use **locality-sensitive hashing (LSH)** to keep members physically close.
- Sense-Trigger-Actuate (STA) routines use trigger predicates and device locks (SLA/OLA scheme), with quorum-based agreement.

CoMesh assumes homogeneous, mains-powered devices. RA-CoMesh targets heterogeneous, battery-powered, intermittently available devices.

## The Five Extensions

| # | Gap in baseline CoMesh | RA-CoMesh extension | How it works |
|---|---|---|---|
| 1 | Leader chosen by hash only; weak or drained devices can lead | **Resource-aware leader election** | Filter candidates with `battery > 30%` and `CPU load < 70%`, then pick the lowest hash among eligible devices. If none are eligible, fall back to hash-only to preserve liveness. |
| 2 | LSH coteries can consist entirely of low-energy devices | **Energy-aware coterie membership** | After LSH bucketing, require at least one *stable* device (`battery ≥ 30%` and `CPU ≤ 70%`). If the primary bucket has none, search neighboring buckets. If still infeasible, fill to size `K` and log an infeasibility flag. |
| 3 | Routine leader evaluates the full predicate, causing heavy cross-coterie traffic | **Smart predicate push-down** | Device coteries evaluate predicates locally. `FALSE` results are suppressed. On `TRUE`/`UNKNOWN`, only the fields that influence the predicate are forwarded to the routine leader. |
| 4 | FIFO lock queue lets urgent routines wait behind unimportant ones | **Priority-aware lock scheduling** | Priority levels (HIGH=2 > NORMAL=1 > LOW=0) with **anti-starvation aging**: every `τ = 3` overtakes adds a priority bonus, capped at the maximum priority. Bounded waiting: a LOW request wins within 6 overtakes. Overhead is `O(log K)` per lock operation. |
| 5 | Intentionally sleeping devices are treated as failed | **Sleep-aware membership** | Devices send `SLEEP_ANNOUNCE(D, [t_start, t_end])` to their coterie leader. The failure detector consults the sleep registry and marks the device `SLEEPING` instead of `FAILED` when a timeout falls inside the window. Overhead is `O(D)` messages per sleep cycle. |

All five extensions add **zero extra coordination messages** beyond the sleep announcements (local computation only) and sit above the quorum protocol layer, so CoMesh's safety, liveness, and locality arguments are preserved (see the correctness theorems in the paper).

## Key Results

All results come from simulation (Java, CoMesh simulation platform), compared against baseline CoMesh under the same topology, workload, and failure conditions.

| Extension | Result |
|---|---|
| Resource-aware leader election | Re-elections reduced by **18.1%** and leadership-load fairness (Gini) improved by **23.8%** at S=100, T=20. Critically low-battery (≤30%) leaders dropped from 35.7% to 0.0% at S=150. |
| Energy-aware coterie membership | **100%** of hazardous all-low-energy formations eliminated (168/168 at S=100; 4,608/4,608 at S=150) with **0%** infeasibility. |
| Smart predicate push-down | **~99.2%** reduction in messages, hop-messages, and bytes at S=100 and S=500 (25 routines, about 10 devices per routine), with no added detection latency. Small-scale (16-node grid) runs show 99.5% for rarely-true and about 95% for frequently-true predicates. |
| Priority-aware lock scheduling | Correctness proven (deadlock-free, bounded waiting). Small-scale run showed 2 HIGH-priority overtakes and no aging promotions needed. |
| Sleep-aware membership | 8/8 announced sleeps recognized and 8/8 genuine failures processed correctly in a 9-node run, with sleeping devices retained across 36 coterie (re)formations. |

**Scale caveat:** at S=150, T=30 the eligibility filter concentrates leadership on fewer eligible devices, so re-elections rose by 20.0% relative to the hash baseline and the "healthy share" (leaders with battery above 50%) dropped. The critical-battery protection still held. See the Results section of the paper for the threshold-coupling analysis.

Extensions 4 and 5 have been validated at small scale and via formal arguments only. Large-scale empirical testing is future work.

## Important: Quorum / Coterie-Size Discrepancy

While building the extensions we found a mismatch between the CoMesh paper and its reference implementation:

| | CoMesh paper | Reference implementation |
|---|---|---|
| Coterie size | `K = 2f + 1` | `K ≥ 4f + 1` (`assert(k >= 4*F + 1)` in `KGroup.java`) |
| Quorum | `f + 1` | `⌊K/2⌋ + f + 1` (`KGroupManager.java`) |

Requiring `quorum ≤ K − f` (enough non-failed members to reach quorum) gives `⌊K/2⌋ + 2f + 1 ≤ K`, which forces **`K ≥ 4f + 1`**.

**All RA-CoMesh experiments use `K ≥ 4f + 1`.** Any `K = 2F + 1` notation in the paper's experiments is a notational convenience inherited from the CoMesh codebase. With the default `f = 1`, the minimum coterie size is `K = 5`.

## Getting the Code

These folders are snapshots of the **CoMesh-sim** Maven project. The fastest way to work with it is to clone from GitHub rather than use the zip files.

### Requirements
- JDK 15+
- Apache Maven 3.6.3+
- Git

### Clone
```bash
git clone https://github.com/mukuldabas099/Resource-Aware-CoMesh.git
cd Resource-Aware-CoMesh
```
If you only need one gap's snapshot, you can instead `unzip` the corresponding folder (e.g. `CoMesh-sim-Final/`) and `cd` into it.

## Build, Run, Test

**Build**
```bash
mvn compiler:compile
```

**Run a simulation** (9-device grid, 40% smart devices, `f = 1`, epoch length 100):
```bash
mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dt grid3,3 -np 0.4 -ns cp0.0_uniform -f 1 -el 100 -rtt 5 -rd 9"
```

**Run with energy-aware coterie membership** (requires a node energy profile file):
```bash
mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dt grid3,3 -np 0.4 -ns cp0.0_uniform -f 1 -el 100 -rtt 5 -rd 9 -eam -nef path/to/energy_profile.txt"
```
The energy profile format is one line per node: `nodeID batteryPct cpuLoadPct`.

**Run tests**
```bash
mvn test
```

## CLI Parameters

Flags relevant to the resource-aware extensions:

| Flag | Description | Default |
|---|---|---|
| `-k` | Coterie size `K` | 5 (assertion enforces `K ≥ 4f + 1`) |
| `-f` | Failure tolerance `f` | 1 |
| `-el` | Epoch length | 100 |
| `-nef <file>` | Node energy profile file (`nodeID batteryPct cpuLoadPct` per line) | None (required for energy-aware membership) |
| `-eam` | Enable energy-aware membership (absent = original hash-only behavior) | `false` |

> Earlier iterations used the flags `-eaw`, `-es`, `-et`, and `-tt`. These do **not** exist in the current codebase. The only validated flags for energy-aware membership are `-nef` and `-eam`.

## Default Simulation Parameters

| Parameter | Meaning | Default |
|---|---|---|
| `batteryThreshold` | Battery threshold for leader eligibility | 30% |
| `cpuThreshold` | CPU threshold for leader eligibility | 70% |
| `batteryStableThresholdPct` | Battery threshold for a "stable" coterie member | 30.0 |
| `cpuLoadStableThresholdPct` | CPU threshold for a "stable" coterie member | 70.0 |
| `hazardBase` | Baseline per-epoch leader failure hazard | 0.0015 |
| `hazardBatteryCoeff`, `hazardCpuCoeff` | Quadratic hazard coefficients for battery depletion and CPU load | 0.05, 0.05 |
| `leaderBatteryDrain` | Battery drain per epoch while leading | 14.0 |
| `idleBatteryRecharge` | Battery recharge per epoch while idle | 2.5 |
| `cpuBaseline` | Idle CPU baseline | 25.0 |
| `cpuReversion` | Mean-reversion rate of the CPU random walk | 0.15 |
| `cpuNoiseSigma` | CPU random-walk noise | 4.0 |
| `leaderCpuBump` | CPU increase while leading | 16.0 |

**Resource model used by the simulator**

- Failure hazard of a leader per epoch: `hazard(d) = hazardBase + hazardBatteryCoeff·((100 − b(d))/100)² + hazardCpuCoeff·(c(d)/100)²`
- Battery: `b(t+1) = b(t) − leaderBatteryDrain·[leading] + idleBatteryRecharge·[idle]`
- CPU: mean-reverting random walk around `cpuBaseline`, with a bump while leading.

**Network model:** one time unit = 1 ms, per-hop latency = 1 unit, link bandwidth = node default bandwidth divided by its one-hop neighbor count, routing via Dijkstra, 40% of devices are smart devices.

## Repository Layout

- `src/` : modified CoMesh simulator (Java, Maven)
- `GAP*_....md` : documentation for each research-gap extension (leader election, energy-aware coterie membership, predicate push-down, priority-aware lock scheduling, sleep-aware membership)
- `verification/` : result logs and reports for each gap's experiments
- `workloads/scripts` : generates the workloads used by the simulator automatically at runtime

## Limitations and Future Work

- **Simulation only.** Results need confirmation on physical hardware (e.g., the Raspberry Pi testbed used in the original CoMesh work).
- **Priority scheduling and sleep-aware membership** still need large-scale empirical evaluation.
- **Threshold coupling.** The leader-eligibility thresholds (`batteryThreshold`/`cpuThreshold`) and coterie-stability thresholds (`batteryStableThresholdPct`/`cpuLoadStableThresholdPct`) are independent parameters. Unifying them into a single resource-availability policy is planned.
- **Leader reuse at high coterie-to-device ratios.** Hysteresis or round-robin balancing among eligible leaders could give devices time to recharge and improve the healthy-share metric.

## Citation

If you use this work, please cite the RA-CoMesh paper and the original CoMesh paper:

```
Debjani Ghosh, Saloni Vishvkarma, Mukul Dabas,
"A Resource-Aware Performance Optimization Framework for Decentralized Edge-IoT Meshes."

A. Karanika, R. Yang, X. Ma, J. Wang, S. Sundram, and I. Gupta,
"There is more control in egalitarian edge IoT meshes,"
IEEE Transactions on Network and Service Management, vol. 23, pp. 896-909, 2026.
```
