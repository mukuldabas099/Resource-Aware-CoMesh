# pick a random length for each routine based on the distribution that is asked

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    seed = 0
    for i, arg in enumerate(sys.argv):
        if arg == "-r" or arg == "-R" or arg == "-routines":
            routine_no = int(sys.argv[i+1])
            print("Routines:", sys.argv[i+1])
        elif arg == "-d" or arg == "-D" or arg == "-distribution":
            distr = sys.argv[i+1]
        elif arg == "-m" or arg == "-M" or arg == "-maximum":
            max_len = int(sys.argv[i+1])
            print("Maximum routine length:", sys.argv[i+1])
        elif arg == "-s" or arg == "S" or arg == "-seed":
            seed = int(sys.argv[i+1])
    
    rng = default_rng(seed)

    if distr == "uniform":
        lengths = [int(l) for l in rng.uniform(high=max_len, size=routine_no)]
    elif distr == "right-heavy-tail": # (right) heavy-tailed: low length for most routines
        lengths = [int(l) for l in rng.zipf(a=2, size=routine_no)]
    elif distr == "left-heavy-tail": # (left) heavy-tailed: high length for most routines
        lengths = [int(l) for l in (rng.power(a=2, size=routine_no) * max_len)]
    else:
        exit()
    
    # print(routines)
    filename = "workloads/routine_lengths/routine_lengths_r" + str(routine_no) + "_m" + str(max_len) + "_" + distr + "_s" + str(seed) + ".txt"
    with open(filename, "w") as f:
        for r, l in enumerate(lengths):
            f.write(str(r) + " " + str(l) + "\n")
    
    print("routines lengths workload saved in", filename)
