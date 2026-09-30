# based on the requested shape and dimensions
# create the device topology

import sys

if __name__ == "__main__":
    for i, arg in enumerate(sys.argv):
        if arg == "-d" or arg == "-D" or arg == "-dimensions":
            dims = [int(d) for d in sys.argv[i+1].split(",")]
        elif arg == "-s" or arg == "-S" or arg == "-shape":
            shape = sys.argv[i+1]

    neighbors = {}
    if shape == "grid":
        device_no = dims[0]
        for dim in dims[1:]:
            device_no *= dim
        for d in range(device_no):
            neighbors[d] = []
            if len(dims) == 3 and d >= dims[0] * dims[1]:
                neighbors[d].append(d - dims[0] * dims[1])
            if len(dims) >= 2 and d >= dims[0]:
                neighbors[d].append(d - dims[0])
            if d % dims[0] > 0:
                neighbors[d].append(d - 1)
            if d % dims[0] < dims[0] - 1:
                neighbors[d].append(d + 1)
            if len(dims) >= 2 and d < device_no - dims[0]:
                neighbors[d].append(d + dims[0])
            if len(dims) == 3 and d < device_no - dims[0] * dims[1]:
                neighbors[d].append(d + dims[0] * dims[1])

    filename = "workloads/device_topology/device_topology_d" + str(device_no) + "_" + shape + ",".join([str(dim) for dim in dims]) + ".txt"
    with open(filename, "w") as f:
        for d, ns in neighbors.items():
            f.write(str(d) + " " + " ".join([str(neighbor) for neighbor in ns]) + "\n")
    
    print("Device topology workload saved in", filename)
