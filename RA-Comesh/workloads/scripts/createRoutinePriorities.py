# Gap 5: Priority-Aware Lock Scheduling
#
# Generates a synthetic per-routine priority file for a given routine
# map, so the Gap 5 priority-aware lock-scheduling policy (-pas, -rpf in
# Simulator) can be exercised and verified.
#
# Each routine is independently assigned HIGH priority with probability
# -hip, LOW priority with probability -lop, and NORMAL priority
# otherwise. Routines not listed in the output file default to NORMAL
# (see kgroup.priority.PriorityLockScheduler.parsePriority), matching
# how Gap 4's node energy file leaves unlisted nodes at their default.

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    routine_map_filename = None
    high_prob = 0.2
    low_prob = 0.2
    seed = 0
    output_filename = None

    for i, arg in enumerate(sys.argv):
        if arg == "-r" or arg == "-R" or arg == "-routineMap":
            routine_map_filename = sys.argv[i + 1]
            print("Routine map:", routine_map_filename)
        elif arg == "-hip" or arg == "-HIP" or arg == "-highProbability":
            high_prob = float(sys.argv[i + 1])
            print("HIGH-priority routine probability:", sys.argv[i + 1])
        elif arg == "-lop" or arg == "-LOP" or arg == "-lowProbability":
            low_prob = float(sys.argv[i + 1])
            print("LOW-priority routine probability:", sys.argv[i + 1])
        elif arg == "-s" or arg == "-S" or arg == "-seed":
            seed = int(sys.argv[i + 1])
            print("Seed:", sys.argv[i + 1])
        elif arg == "-o" or arg == "-O" or arg == "-output":
            output_filename = sys.argv[i + 1]

    if routine_map_filename is None:
        print("Missing -r <routine map file>")
        sys.exit(-1)

    if high_prob + low_prob > 1.0:
        print("high probability + low probability cannot exceed 1.0")
        sys.exit(-1)

    with open(routine_map_filename, "r") as f:
        routines = [line.split()[0] for line in f.readlines() if line.strip() != ""]

    if output_filename is None:
        output_filename = "workloads/routine_priorities_" + \
            routine_map_filename.split("/")[-1].replace("routine_device_map_", "").replace(".txt", "") + ".txt"

    rng = default_rng(seed)
    draws = rng.uniform(size=len(routines))

    lines = []
    counts = {"HIGH": 0, "NORMAL": 0, "LOW": 0}
    for routine_id, draw in zip(routines, draws):
        if draw < high_prob:
            label = "HIGH"
        elif draw < high_prob + low_prob:
            label = "LOW"
        else:
            label = "NORMAL"
        counts[label] += 1
        lines.append((routine_id, label))

    with open(output_filename, "w") as f:
        for routine_id, label in lines:
            f.write(str(routine_id) + " " + label + "\n")

    print("routine priorities saved in", output_filename)
    print(counts)
