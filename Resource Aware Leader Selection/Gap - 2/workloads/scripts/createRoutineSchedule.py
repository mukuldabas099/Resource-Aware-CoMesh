# create the routine trigger schedule
# based on the number of routines and distribution
# Uniform, zipf, poisson, exponential, heavy-tail (e.g. TCP traffic, Pareto), clustered

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
            print("Distribution:", distr)
        elif arg == "-c" or arg == "-C" or arg == "-clusters":
            clusters = int(sys.argv[i+1])
            print("Number of clusters:", sys.argv[i+1])
        elif arg == "-e" or arg == "-E" or arg == "-end":
            end = int(sys.argv[i+1])
            # timestamps are integers in [0, end]
            print("Schedule length:", sys.argv[i+1])
        elif arg == "-s" or arg == "S" or arg == "-seed":
            seed = int(sys.argv[i+1])
    
    rng = default_rng(seed)

    if routine_no == 1:
        schedule = [(int(rng.uniform(high=end)), 0)]
    elif distr == "uniform":
        temp = rng.uniform(size=routine_no)
        schedule = [(int((p-min(temp))/(max(temp)-min(temp))*end), r) for r, p in sorted(enumerate(temp), key=lambda x: x[1])]
    elif distr == "poisson":
        temp = rng.poisson(lam=end/2, size=routine_no)
        schedule = [(int((p-min(temp))/(max(temp)-min(temp))*end), r) for r, p in sorted(enumerate(temp), key=lambda x: x[1])]
    elif distr == "exponential":
        temp = rng.exponential(scale=1.0, size=routine_no)
        schedule = [(int((p-min(temp))/(max(temp)-min(temp))*end), r) for r, p in sorted(enumerate(temp), key=lambda x: x[1])]
    elif distr == "zipf": # heavy-tail
        temp = rng.zipf(a=2, size=routine_no)
        schedule = [(int((p-min(temp))/(max(temp)-min(temp))*end), r) for r, p in sorted(enumerate(temp), key=lambda x: x[1])]
    elif distr == "pareto": # heavy-tail
        temp = rng.pareto(a=2, size=routine_no)
        schedule = [(int((p-min(temp))/(max(temp)-min(temp))*end), r) for r, p in sorted(enumerate(temp), key=lambda x: x[1])]
    elif distr == "power":
        temp = rng.power(a=2, size=routine_no)
        schedule = [(int((p-min(temp))/(max(temp)-min(temp))*end), r) for r, p in sorted(enumerate(temp), key=lambda x: x[1])]
    elif distr == "cluster":
        schedule = []
        for c in range(clusters):
            temp = rng.zipf(a=3, size=routine_no//clusters)
            schedule.extend([(int(c*(end//clusters) + ((p-min(temp))/(1 if max(temp)-min(temp) == 0 else max(temp)-min(temp)))*(end//clusters-1)), r) for r, p in enumerate(temp, start=c*(routine_no//clusters))])
        schedule.extend([(end-i, routine_no-i) for i in range(routine_no%clusters)])
    else:
        exit()

    schedule = sorted(schedule)
    # print(schedule)
    for ts, r in schedule:
        print(ts, ": ", r, sep="")
    
    filename = "workloads/routine_schedule/routine_schedule_r" + str(routine_no) + "_e" + str(end) + "_" + distr + (str(clusters) if distr=="cluster" else "") + "_s" + str(seed) + ".txt"
    with open(filename, "w") as f:
        for ts, r in schedule:
            f.write(str(ts) + " " + str(r) + "\n")
    
    print("routine schedule saved in", filename)
