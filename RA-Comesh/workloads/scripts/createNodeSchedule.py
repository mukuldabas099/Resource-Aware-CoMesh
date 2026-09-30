# based on the node number and requested probability for node churn
# create the node churn schedule

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    seed = 0
    for i, arg in enumerate(sys.argv):
        if arg == "-dn" or arg == "-DN" or arg == "-devices":
            device_no = int(sys.argv[i+1])
            print("Devices:", sys.argv[i+1])
        elif arg == "-np" or arg == "-NP" or arg == "-nodesProbability":
            node_prob = float(sys.argv[i+1])
            print("Node selection probability:", sys.argv[i+1])
        elif arg == "-cp" or arg == "-CP" or arg == "-churnProbability":
            churn_prob = float(sys.argv[i+1])
            print("Probability for node churn:", churn_prob)
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
            print('Seed:', sys.argv[i+1])

    with open("workloads/node_list/node_list_d" + str(device_no) + "_np" + str(node_prob) + "_s" + str(seed) + ".txt" ,"r") as f:
        lines = f.readlines()
        nodes = [int(line.split()[0]) for line in lines]

    rng = default_rng(seed)
    churn_probs = rng.uniform(size=len(nodes))
    churn_type = ["j" if temp > 0.5 else "f" for temp in rng.uniform(size=len(nodes))]
    
    if distr == "uniform":
        time = [int(p) for p in rng.uniform(low=0, high=end, size=len(nodes))]
    elif distr == "poisson":
        temp = rng.poisson(lam=end/2, size=len(nodes))
        time = [int((p-min(temp))/(max(temp)-min(temp))*end) for p in sorted(temp)]
    elif distr == "exponential":
        temp = rng.exponential(scale=1.0, size=len(nodes))
        time = [int((p-min(temp))/(max(temp)-min(temp))*end) for p in sorted(temp)]
    elif distr == "zipf": # heavy-tail
        temp = rng.zipf(a=2, size=len(nodes))
        time = [int((p-min(temp))/(max(temp)-min(temp))*end) for p in sorted(temp)]
    elif distr == "pareto": # heavy-tail
        temp = rng.pareto(a=2, size=len(nodes))
        time = [int((p-min(temp))/(max(temp)-min(temp))*end) for p in sorted(temp)]
    elif distr == "power":
        temp = rng.power(a=2, size=len(nodes))
        time = [int((p-min(temp))/(max(temp)-min(temp))*end) for p in sorted(temp)]
    elif distr == "cluster":
        time = []
        for c in range(clusters):
            temp = rng.zipf(a=3, size=len(nodes)//clusters)
            time.extend([int(c*(end//clusters) + ((p-min(temp))/(1 if max(temp)-min(temp) == 0 else max(temp)-min(temp)))*(end//clusters-1)) for p in temp])
        time.extend([end-i for i in range(len(nodes)%clusters)])
    else:
        exit()
    
    schedule = [(time[i], churn_type[i], nodes[i]) for i in range(len(nodes)) if churn_probs[i] < churn_prob]

    schedule = sorted(schedule)
    for ts, t, n in schedule:
        print(ts, ": ", t, " ", n, sep="")
    
    filename = "workloads/node_schedule/node_schedule_d" + str(device_no) + "_np" + str(node_prob) + "_cp" + str(churn_prob) + "_" + distr + (str(clusters) if distr=="cluster" else "") + "_e" + str(end) + "_s" + str(seed) + ".txt"
    with open(filename, "w") as f:
        for ts, t, n in schedule:
            f.write(str(ts) + " " + str(t) + " " + str(n) + "\n")
    
    print("node schedule saved in", filename)
