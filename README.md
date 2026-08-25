# CoMesh-sim — Getting the Code from GitHub

These folders (`Gap-2`, `Gap-4`, `CoMesh-Final`) are snapshots of the **CoMesh-sim** Maven project.
If the repo lives on GitHub, here's the fastest way to get it and run it locally instead of
working from the zip files.

## 1. Requirements
- JDK 15+
- Apache Maven 3.6.3+
- Git

## 2. Clone the repo
```bash
git clone <your-github-repo-url>.git
cd <repo-folder>
```
If you only need one gap's snapshot, you can instead just `unzip` the corresponding
folder here (e.g. `CoMesh-sim-Final/`) and `cd` into it.

## 3. Build
```bash
mvn compiler:compile
```

## 4. Run a simulation
```bash
mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dt grid3,3 -np 0.4 -ns cp0.0_uniform -f 1 -el 100 -rtt 5 -rd 9"
```

## 5. Run tests
```bash
mvn test
```

## Notes
- Each `GAP*_....md` file documents a specific research-gap extension (e.g. predicate
  pushdown, energy-aware coterie membership, priority-aware lock scheduling).
- `verification/` folders contain result logs/reports for that gap's experiments.
- `workloads/scripts` generates the workloads used by the simulator automatically at runtime.
