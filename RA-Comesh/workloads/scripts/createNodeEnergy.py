# Gap 4: Energy-Aware Coterie Membership
#
# Generates a synthetic per-node energy profile (battery %, CPU load %)
# for a given node list, so the Gap 4 energy-aware coterie-membership
# constraint (-eam, -nef in Simulator) can be exercised and verified.
#
# A node is "energy-stable" iff battery >= 30% AND cpuLoad <= 70%
# (kept in sync with kgroup.energy.NodeEnergyRegistry's thresholds).
# Each node is independently marked low-energy with probability -lep;
# low-energy nodes get a battery/CPU draw that always fails at least one
# of those two thresholds, stable nodes get a draw that always passes both.

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    node_list_filename = None
    low_energy_prob = 0.5
    seed = 0
    output_filename = None

    for i, arg in enumerate(sys.argv):
        if arg == "-n" or arg == "-N" or arg == "-nodeList":
            node_list_filename = sys.argv[i + 1]
            print("Node list:", node_list_filename)
        elif arg == "-lep" or arg == "-LEP" or arg == "-lowEnergyProbability":
            low_energy_prob = float(sys.argv[i + 1])
            print("Low-energy node probability:", sys.argv[i + 1])
        elif arg == "-s" or arg == "-S" or arg == "-seed":
            seed = int(sys.argv[i + 1])
            print("Seed:", sys.argv[i + 1])
        elif arg == "-o" or arg == "-O" or arg == "-output":
            output_filename = sys.argv[i + 1]

    if node_list_filename is None:
        print("Missing -n <node list file>")
        sys.exit(-1)

    with open(node_list_filename, "r") as f:
        nodes = [line.split()[0] for line in f.readlines() if line.strip() != ""]

    if output_filename is None:
        output_filename = "workloads/node_energy_" + \
            node_list_filename.split("/")[-1].replace("node_list_", "").replace(".txt", "") + ".txt"

    rng = default_rng(seed)
    is_low_energy = rng.uniform(size=len(nodes)) < low_energy_prob

    lines = []
    low_energy_count = 0
    for node_id, low_energy in zip(nodes, is_low_energy):
        if low_energy:
            # guaranteed to violate at least one energy-stability threshold
            battery = round(rng.uniform(5.0, 29.9), 1)
            cpu_load = round(rng.uniform(50.0, 95.0), 1)
            low_energy_count += 1
        else:
            # guaranteed to satisfy both energy-stability thresholds
            battery = round(rng.uniform(30.0, 100.0), 1)
            cpu_load = round(rng.uniform(10.0, 70.0), 1)
        lines.append((node_id, battery, cpu_load))

    with open(output_filename, "w") as f:
        for node_id, battery, cpu_load in lines:
            f.write(str(node_id) + " " + str(battery) + " " + str(cpu_load) + "\n")

    print("node energy profile saved in", output_filename)
    print(str(low_energy_count) + "/" + str(len(nodes)) + " nodes generated as low-energy")
