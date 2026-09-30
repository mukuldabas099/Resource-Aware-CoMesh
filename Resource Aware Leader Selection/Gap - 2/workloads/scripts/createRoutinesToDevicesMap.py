# based on the device topology, the number of routines, and the average number of devices per routine
# create a routine -> devices map
# clustered, uniform, heavy-tail

# Example executions
# python3 workloads/routinesToDevices.py -r 10 -d workloads/sample_device_topology.txt -distribution uniform -a 5 -> workloads/routine_map_r10_d9_uniform.txt
# python3 workloads/routinesToDevices.py -r 10 -d workloads/sample_device_topology.txt -distribution left-heavy-tail -a 5 -> workloads/routine_map_r10_d9_left-heavy-tail.txt
# python3 workloads/routinesToDevices.py -r 10 -d workloads/sample_device_topology.txt -distribution right-heavy-tail -a 5 -> workloads/routine_map_r10_d9_right-heavy-tail.txt
# python3 workloads/routinesToDevices.py -r 10 -d workloads/sample_device_topology.txt -distribution clusters -clusters 0,3,7 -a 5 -> workloads/routine_map_r10_d9_clusters.txt

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    seed = 0
    for i, arg in enumerate(sys.argv):
        if arg == "-r" or arg == "-R" or arg == "-routines":
            routine_no = int(sys.argv[i+1])
            print("Routines:", sys.argv[i+1])
        elif arg == "-d" or arg == "-D" or arg == "-devices":
            device_topology_filename = sys.argv[i+1]
            with open(device_topology_filename, "r") as f:
                devices = []
                for line in f.readlines():
                    devices.append(int(line.split()[0]))
            device_no = len(devices)
            # print("Devices:", devices)
        elif arg == "-p" or arg == "-p" or arg == "-distribution":
            distr = sys.argv[i+1]
        elif arg == "-a" or arg == "-A" or arg == "-average":
            avg_dpr = int(sys.argv[i+1])
            print("Average number of devices per routine:", sys.argv[i+1])
        elif arg == "-c" or arg == "-C" or arg == "-clusters":
            clusters = int(sys.argv[i+1])
        elif arg == "-s" or arg == "S" or arg == "-seed":
            seed = int(sys.argv[i+1])
    
    rng = default_rng(seed)

    # 1. pick a random probability for each device based on the distribution that is asked
    # 2. then pick randomly a device for each routine based on a normal distribution
    # where the center is the random probability of the device that was initially calculated

    if distr == "right-heavy-tail": # (right) heavy-tailed: low probability for most devices
        temp = rng.zipf(a=2.5, size=device_no)
        centers = [p / max(temp) for p in temp]
    elif distr == "left-heavy-tail": # (left) heavy-tailed: high probability for most devices
        centers = rng.power(a=2, size=device_no)
    elif distr == "clusters": # big probability for specified devices
        centers = []
        for i in range(device_no):
            if i < clusters:
                centers.append(0.75)
            else:
                centers.append(0.25)
    elif distr != "uniform":
        exit()

    routines = {}
    for r in range(routine_no):
        routines[r] = []
        for d in devices:
            if distr == "uniform":
                if rng.uniform() > 1 - avg_dpr/device_no:
                    routines[r].append(d)
            else:
                if rng.normal(loc=centers[d], scale=0.2) > 1 - avg_dpr/device_no:
                    routines[r].append(d)
    
    # print(routines)
    filename = "workloads/routine_device_map/routine_device_map_r" + str(routine_no) + "_d" + str(device_no) + "_a" + str(avg_dpr) + "_" + distr + (str(clusters) if distr=="clusters" else "") + "_s" + str(seed) + ".txt"
    with open(filename, "w") as f:
        for r, ds in routines.items():
            f.write(str(r) + " " + " ".join([str(d) for d in ds]) + "\n")
    
    print("routines->devices map workload saved in", filename)
