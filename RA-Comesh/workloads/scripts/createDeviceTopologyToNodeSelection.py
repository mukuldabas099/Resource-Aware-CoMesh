# based on the device number and the percentage of computing nodes we want
# select a set of devices as computing nodes

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    seed = 0
    for i, arg in enumerate(sys.argv):
        if arg == "-dn" or arg == "-DN" or arg == "-devices":
            device_no = int(sys.argv[i+1])
            print("Devices:", sys.argv[i+1])
        elif arg == "-s" or arg == "-S" or arg == "-shape":
            shape = sys.argv[i+1]
        elif arg == "-np" or arg == "-NP" or arg == "-nodeProbability":
            node_prob = float(sys.argv[i+1])
            print("Probability for node selection:", node_prob)
        elif arg == "-seed":
            seed = int(sys.argv[i+1])
    
    with open("workloads/device_topology/device_topology_d" + str(device_no) + "_" + shape + ".txt", "r") as f:
        lines = f.readlines()
        devices = [int(line.split()[0]) for line in lines]
    
    rng = default_rng(seed)
    probs = rng.uniform(size=device_no)
    nodes = [devices[i] for i in range(device_no) if probs[i] < node_prob]

    # print(nodes)

    filename = "workloads/node_list/node_list_d" + str(device_no) + "_np" + str(node_prob) + "_s" + str(seed) + ".txt"
    with open(filename, "w") as f:
        f.write("\n".join([str(n) for n in nodes]))
    
    print("node list workload saved in", filename)
