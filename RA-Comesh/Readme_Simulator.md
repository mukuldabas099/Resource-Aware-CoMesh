# CoMesh-sim: Step-by-Step Setup & Run Guide

> **Looking for the Gap 3 (Smart Predicate Push-Down) experiment?** See
> [`GAP3_PREDICATE_PUSHDOWN.md`](GAP3_PREDICATE_PUSHDOWN.md) — it reuses
> the exact same build steps documented here.

---

## Prerequisites

You need **Linux/macOS** (or WSL on Windows).

### 1. Install JDK 15+

**Ubuntu/Debian:**
```bash
sudo apt-get install -y openjdk-21-jdk
```
**macOS (Homebrew):**
```bash
brew install openjdk@21
```
Verify:
```bash
java -version    # should show 15+
javac -version   # must be present (JDK, not just JRE)
```

### 2. Install Apache Maven 3.6.3+

**Ubuntu/Debian:**
```bash
sudo apt-get install -y maven
```
**macOS:**
```bash
brew install maven
```
Verify:
```bash
mvn -version
```

### 3. Install OpenJFX (JavaFX)

The code uses `javafx.util.Pair`, so JavaFX is required.

**Ubuntu/Debian:**
```bash
sudo apt-get install -y openjfx
```
**macOS:** JavaFX is bundled with Homebrew's OpenJDK or available via [Gluon](https://gluonhq.com/products/javafx/).

### 4. Install Python 3

```bash
python3 --version   # should already be present on most systems
```

---

## Clone the Repository

```bash
git clone https://github.com/annakaranika/CoMesh-sim.git
cd CoMesh-sim
```

---

## Compile the Project

Maven's central repo access may be restricted. Use **direct javac compilation** instead:

```bash
# Create output directory
mkdir -p target/classes

# Find all Java source files
find src/main/java -name "*.java" > /tmp/sources.txt

# Set JavaFX classpath (adjust path if different on your system)
JAVAFX_CP="/usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-graphics.jar"

# Compile
javac --release 15 \
  -cp "$JAVAFX_CP" \
  -sourcepath src/main/java \
  -d target/classes \
  @/tmp/sources.txt
```

You should see only `Note: ... unchecked operations` warnings — **no errors**.

> **If Maven works on your machine**, you can also just use:
> ```bash
> mvn compiler:compile
> ```

---

## Prepare Workload Files

The simulator calls Python scripts to generate workloads, but **writes them to subdirectories** while reading from the root `workloads/` folder. You must create the subdirs and copy files manually:

```bash
# Step A: Create all required subdirectories
mkdir -p workloads/device_topology \
         workloads/node_list \
         workloads/node_schedule \
         workloads/routine_device_map \
         workloads/routine_lengths \
         workloads/routine_schedule \
         outputs

# Step B: Create the device topology (3x3 grid = 9 devices)
python3 workloads/scripts/createDeviceTopology.py -s grid -d 3,3

# Step C: Create the node (smart device) selection list
python3 workloads/scripts/createDeviceTopologyToNodeSelection.py \
        -dn 9 -s grid3,3 -np 0.4 -seed 0

# Step D: Create the node churn/schedule
python3 workloads/scripts/createNodeSchedule.py \
        -dn 9 -np 0.4 -cp 0.0 -d uniform -e 0 -s 0

# Step E: Copy files to root workloads/ (where Simulator.java reads them)
cp "workloads/device_topology/device_topology_d9_grid3,3.txt" \
   "workloads/device_topology_d9_grid3,3.txt"

cp workloads/node_list/node_list_d9_np0.4_s0.txt \
   workloads/node_list_d9_np0.4.txt

cp workloads/node_schedule/node_schedule_d9_np0.4_cp0.0_uniform_e0_s0.txt \
   workloads/node_schedule_d9_np0.4_cp0.0_uniform_e0.txt
```

---

## Run the Simulation

```bash
JAVAFX_CP="/usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-graphics.jar"

java -cp "target/classes:$JAVAFX_CP" \
  -Xss256k -Xmx4096m \
  Simulator \
  -e 0 -rn 0 -dn 9 -dts grid -dtd 3,3 \
  -np 0.4 -cp 0.0 -nsd uniform \
  -f 1 -el 100 -rm 5 -rd 9
```

### What the arguments mean:

| Argument | Value | Meaning |
|---|---|---|
| `-e 0` | 0 | End time (0 = minimal run) |
| `-rn 0` | 0 | Number of routines |
| `-dn 9` | 9 | Number of devices |
| `-dts grid` | grid | Device topology shape |
| `-dtd 3,3` | 3,3 | Grid dimensions (3×3 = 9 devices) |
| `-np 0.4` | 0.4 | Fraction of devices that are "smart" (40%) |
| `-cp 0.0` | 0.0 | Churn probability (0 = no failures) |
| `-nsd uniform` | uniform | Node schedule distribution |
| `-f 1` | 1 | Fault tolerance level (tolerate 1 failure, coterie size k=3) |
| `-el 100` | 100 | Epoch length (time units) |
| `-rm 5` | 5 | Routine monitor period |
| `-rd 9` | 9 | Device k-group range |

> **Note on the README command:** The README uses shorthand combined flags (`-dt grid3,3`, `-ns cp0.0_uniform`, `-rtt 5`) that appear to be from a slightly different version of the code. Use the expanded equivalents above instead.

---

## Expected Output

You'll see:
```
----------------EPOCH 1----------------
Node hashes collide!!!!!!!
...
```

And a CSV results file is saved to:
```
outputs/d9_grid3,3_np0.4_cp0.0_uniform_e0_r0_a__m__e0.csv
```

---

## Troubleshooting

| Error | Fix |
|---|---|
| `javac: not found` | Install the JDK (`-jdk` package), not just JRE |
| `javafx.util.Pair` not found | Install `openjfx` and add to `-cp` |
| `Topology file ... does not exist` | Run Step E (copy files to root workloads dir) |
| Maven `403 Forbidden` from central | Use direct `javac` compilation as shown above |
| `Node hashes collide` in output | Normal — it's a debug message, not an error |
