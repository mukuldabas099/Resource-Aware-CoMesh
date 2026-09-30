# Gap 4: Energy-Aware Coterie Membership
#
# Based on a node list, generate a per-node energy profile file
# ("nodeID batteryPct cpuLoadPct" per line) that Simulator's -nef flag can
# load. Supports a "-lowEnergyProbability" knob so the workload can
# deliberately recreate the scenario described in the gap: a network where,
# by chance, most devices are low on energy, so a purely locality-based
# (hash) bucket selection risks forming a coterie with no energy-stable
# member at all.
#
# Example executions
# python3 workloads/scripts/createNodeEnergy.py -n workloads/sample_node_list.txt -lep 0.6 -s 0 \
#   -> workloads/sample_node_energy.txt
# python3 workloads/scripts/createNodeEnergy.py -n workloads/node_list_d25_np0.9.txt -lep 0.7 -s 1 \
#   -> workloads/node_energy_d25_np0.9_lep0.7_s1.txt

import sys
import os
from numpy.random import default_rng

if __name__ == "__main__":
    seed = 0
    low_energy_prob = 0.5          # fraction of nodes drawn from the "low energy" regime
    low_battery_range = (5, 25)    # battery% range for low-energy nodes
    high_battery_range = (60, 100)  # battery% range for energy-stable nodes
    low_cpu_range = (10, 45)
    high_cpu_range = (45, 95)      # CPU load range for the (struggling) low-energy nodes
    out_filename = None

    for i, arg in enumerate(sys.argv):
        if arg == "-n" or arg == "-N" or arg == "-nodeList":
            node_list_filename = sys.argv[i + 1]
            print("Node list:", node_list_filename)
        elif arg == "-lep" or arg == "-LEP" or arg == "-lowEnergyProbability":
            low_energy_prob = float(sys.argv[i + 1])
            print("Low-energy node probability:", low_energy_prob)
        elif arg == "-s" or arg == "-S" or arg == "-seed":
            seed = int(sys.argv[i + 1])
            print("Seed:", seed)
        elif arg == "-o" or arg == "-O" or arg == "-output":
            out_filename = sys.argv[i + 1]

    with open(node_list_filename, "r") as f:
        node_ids = [line.split()[0] for line in f.readlines() if line.strip()]

    rng = default_rng(seed)
    is_low_energy = rng.uniform(size=len(node_ids)) < low_energy_prob

    lines = []
    for node_id, low in zip(node_ids, is_low_energy):
        if low:
            battery = rng.uniform(*low_battery_range)
            cpu = rng.uniform(*low_cpu_range if rng.uniform() < 0.5 else high_cpu_range)
        else:
            battery = rng.uniform(*high_battery_range)
            cpu = rng.uniform(*low_cpu_range)
        lines.append(f"{node_id} {battery:.1f} {cpu:.1f}")

    if out_filename is None:
        base = os.path.splitext(os.path.basename(node_list_filename))[0]
        out_filename = "workloads/" + base.replace("node_list", "node_energy") \
            + "_lep" + str(low_energy_prob) + "_s" + str(seed) + ".txt"

    with open(out_filename, "w") as f:
        f.write("\n".join(lines) + "\n")

    print("node energy profile saved in", out_filename)
    print(str(int(sum(is_low_energy))) + "/" + str(len(node_ids)) + " nodes generated as low-energy")
