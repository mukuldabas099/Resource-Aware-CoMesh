# Gap 7: Intermittent Device Support
#
# Generates a synthetic device sleep schedule for a given device topology,
# so the Gap 7 announced-sleep-window mechanism (-isc, -dsf in Simulator)
# can be exercised and verified.
#
# Each device is independently marked as "intermittent" with probability
# -ip. Every intermittent device gets one or more announced sleep windows
# (whose count, spacing, and duration are controlled by -w/-minLen/
# -maxLen) scattered across [0, end). Devices not listed in the output
# file are never treated as asleep (see kgroup.sleep.DeviceSleepRegistry),
# matching how Gap 4's node energy file leaves unlisted nodes at their
# default (energy-stable).

import sys
from numpy.random import default_rng

if __name__ == "__main__":
    device_topology_filename = None
    intermittent_prob = 0.3
    windows_per_device = 2
    min_len = 20
    max_len = 60
    end = 500
    seed = 0
    output_filename = None

    for i, arg in enumerate(sys.argv):
        if arg == "-d" or arg == "-D" or arg == "-deviceTopology":
            device_topology_filename = sys.argv[i + 1]
            print("Device topology:", device_topology_filename)
        elif arg == "-ip" or arg == "-IP" or arg == "-intermittentProbability":
            intermittent_prob = float(sys.argv[i + 1])
            print("Intermittent device probability:", sys.argv[i + 1])
        elif arg == "-w" or arg == "-W" or arg == "-windowsPerDevice":
            windows_per_device = int(sys.argv[i + 1])
            print("Sleep windows per intermittent device:", sys.argv[i + 1])
        elif arg == "-minLen":
            min_len = int(sys.argv[i + 1])
        elif arg == "-maxLen":
            max_len = int(sys.argv[i + 1])
        elif arg == "-e" or arg == "-E" or arg == "-end":
            end = int(sys.argv[i + 1])
            print("Schedule horizon:", sys.argv[i + 1])
        elif arg == "-s" or arg == "-S" or arg == "-seed":
            seed = int(sys.argv[i + 1])
            print("Seed:", sys.argv[i + 1])
        elif arg == "-o" or arg == "-O" or arg == "-output":
            output_filename = sys.argv[i + 1]

    if device_topology_filename is None:
        print("Missing -d <device topology file>")
        sys.exit(-1)

    with open(device_topology_filename, "r") as f:
        devices = [line.split()[0] for line in f.readlines() if line.strip() != ""]

    if output_filename is None:
        output_filename = "workloads/device_sleep_schedule_" + \
            device_topology_filename.split("/")[-1].replace("device_topology_", "").replace(".txt", "") + ".txt"

    rng = default_rng(seed)
    is_intermittent = rng.uniform(size=len(devices)) < intermittent_prob

    lines = []
    intermittent_count = 0
    for device_id, intermittent in zip(devices, is_intermittent):
        if not intermittent:
            continue
        intermittent_count += 1
        # non-overlapping windows: carve [0, end) into windows_per_device
        # equal slots and place one randomly-sized window inside each, so
        # windows for the same device never overlap or need re-sorting.
        slot_len = end // windows_per_device
        for w in range(windows_per_device):
            slot_start = w * slot_len
            window_len = min(int(rng.uniform(min_len, max_len + 1)), max(1, slot_len - 1))
            start = slot_start + int(rng.uniform(0, max(1, slot_len - window_len)))
            stop = start + window_len
            lines.append((device_id, start, stop))

    with open(output_filename, "w") as f:
        for device_id, start, stop in lines:
            f.write(str(device_id) + " " + str(start) + " " + str(stop) + "\n")

    print("device sleep schedule saved in", output_filename)
    print(str(intermittent_count) + "/" + str(len(devices)) + " devices generated as intermittent ("
          + str(len(lines)) + " total sleep windows)")
