import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import kgroup.LeaderElectionPolicy;
import network.Network;
import routine.DumbRoutine;
import routine.Routine;
import routine.RoutineMetricSingleton;

public class Simulator {
    public static String helpText = "Help:\nTo run, type: mvn exec:java\nYou can set arguments by also typing: -Dexec.args=\"<arg0> ... <argN>\"";

    private static String getPythonScriptOutput(Process pr) {
        String output = "Standard output:\n", s;
        try {
            BufferedReader stdOutput = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            while ((s = stdOutput.readLine()) != null) {
                output += s + "\n";
            }

            output += "\nStandard error:\n";
            BufferedReader stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
            while ((s = stdError.readLine()) != null) {
                output += s + "\n";
            }
        } catch (IOException e) {
            System.out.println("Python script failed!");
            e.printStackTrace();
            System.exit(-1);
        }
        return output;
    }

    public static void createWorkloads(
            int seed,
            String deviceTopologyShape, String deviceTopologyDims,
            int deviceNo, String nodePercentage,
            String nodeChurnProbability, String nodeScheduleDistribution, String nodeScheduleClusters, int end,
            int routineNo, String routineDeviceMapDistribution, String avgDevicesPerRoutine, String deviceClusters,
            String routineLengthDistribution, String maxRoutineLength,
            String routineScheduleDistribution, String routineScheduleClusters) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript;

        // create device topology workload
        pythonScript = "python3 workloads/scripts/createDeviceTopology.py -s " + deviceTopologyShape + " -d "
                + deviceTopologyDims;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Device topology workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }

        // create node list workload
        String deviceTopologySetup = deviceTopologyShape + deviceTopologyDims;
        pythonScript = "python3 workloads/scripts/createDeviceTopologyToNodeSelection.py -dn "
                + String.valueOf(deviceNo) + " -s " + deviceTopologySetup + " -np " + nodePercentage + " -seed " + seed;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Node list workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }

        // create node schedule workload
        pythonScript = "python3 workloads/scripts/createNodeSchedule.py -dn " + String.valueOf(deviceNo) + " -np "
                + nodePercentage + " -cp " + nodeChurnProbability + " -d " + nodeScheduleDistribution
                + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + " -e "
                + String.valueOf(end) + " -s " + seed;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec("python3 workloads/scripts/createNodeSchedule.py -dn " + String.valueOf(deviceNo) + " -np "
                    + nodePercentage + " -cp " + nodeChurnProbability + " -d " + nodeScheduleDistribution
                    + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + " -e "
                    + String.valueOf(end) + " -s " + seed);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Node schedule workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }

        if (routineNo <= 0) {
            return;
        }

        // create routine to device map workload
        String devicesTopologyFilename = "workloads/device_topology/device_topology_d" + String.valueOf(deviceNo) + "_"
                + deviceTopologySetup + ".txt";
        pythonScript = "python3 workloads/scripts/createRoutinesToDevicesMap.py -r " + String.valueOf(routineNo)
                + " -d " + devicesTopologyFilename + " -p " + routineDeviceMapDistribution + " -a "
                + avgDevicesPerRoutine
                + (routineDeviceMapDistribution.equals("clusters") ? " -c " + deviceClusters : "") + " -s " + seed;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Routine to device map workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }

        // create routine lengths workload
        pythonScript = "python3 workloads/scripts/createRoutineLengths.py -r " + String.valueOf(routineNo) + " -d "
                + routineLengthDistribution + " -m " + maxRoutineLength + " -s " + seed;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Routine lengths workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }

        // create routine schedule workload
        pythonScript = "python3 workloads/scripts/createRoutineSchedule.py -r " + String.valueOf(routineNo) + " -d "
                + routineScheduleDistribution
                + (routineScheduleDistribution.equals("cluster") ? " -c " + routineScheduleClusters : "") + " -e "
                + String.valueOf(end) + " -s " + seed;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Routine schedule workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createInKGroupMessagesPlot(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/plot_msgs.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("In k-group messages' plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createInKGroupDelaysPlot(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/plot_in_kgroup_delays.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("In k-group delays' plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createClientSyncDelayPlot(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/plot_client_sync_delay.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Output client/sync delay's plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createBandwidthPlot(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/plot_bandwidth.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Bandwidth's plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createBandwidthBGPlot(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/bw_verification.py " +
                outputCsvFilename;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Bandwidth background checking plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createBandwidthAggregatedBGPlot(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/bw_verification.py " +
                outputCsvFilename + " " + seedNo;
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Bandwidth aggregated background checking plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void createBalanceCDF(String outputCsvFilename, int seedNo) {
        Runtime run = Runtime.getRuntime();
        Process pr;
        String pythonScript = "python3 outputs/scripts/plot_balance_cdf.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Balance's cdf plot creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    public static void simulate(
            String devicesTopologyFilename, String nodesListFilename, String nodesScheduleFilename,
            String routinesDevicesMapFilename, String routinesLengthsFilename, String routinesScheduleFilename,
            String outputFilename,
            int hopOWD, int F, int K, LeaderElectionPolicy electionPolicy, int epochLength,
            int routineNo, int routineMonitorPeriod, int end, int deviceKGroupRange, int routineKGroupRange,
            boolean debug, TestType testType) {
        // Device topology and network initialization
        Network network = new Network(devicesTopologyFilename, hopOWD, false);

        List<String> devicesIDs = new ArrayList<>();
        devicesIDs.addAll(network.getMembershipList());

        // Node initialization
        String[] line = {};
        List<String> nodesIDs = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(nodesListFilename))) {
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                nodesIDs.add(line[0]);
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Node list file " + nodesListFilename + " does not exist");
            System.exit(-1);
        }
        // if (debug)
        // System.out.println("Nodes: " + nodesIDs + "\n");

        SortedMap<Integer, List<Event>> events = new TreeMap<>();
        int eventTS;
        try (Scanner sc = new Scanner(new File(nodesScheduleFilename))) {
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                eventTS = Integer.parseInt(line[0]);
                if (events.get(eventTS) == null) {
                    events.put(eventTS, new ArrayList<Event>());
                }
                events.get(eventTS).add(new Event(line[1], line[2]));
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Node schedule file " + nodesScheduleFilename + " does not exist");
            System.exit(-1);
        }

        // Routine initialization
        String routineID;
        List<String> touchedDevicesIDs;
        Map<String, Routine> routines = new HashMap<>();
        int checkpoint_ts = 0;
        Event checkpoint_event = null;
        if (routineNo != 0) {
            try (Scanner sc = new Scanner(new File(routinesDevicesMapFilename))) {
                while (sc.hasNextLine()) {
                    line = sc.nextLine().split(" ");
                    routineID = line[0];
                    touchedDevicesIDs = new ArrayList<>();
                    for (int i = 1; i < line.length; i++) {
                        touchedDevicesIDs.add(line[i]);
                    }
                    routines.put(routineID, new DumbRoutine(touchedDevicesIDs));
                }
            } catch (FileNotFoundException ex) {
                System.out.println("Routine map file " + routinesDevicesMapFilename + " does not exist");
                System.exit(-1);
            }

            int length;
            try (Scanner sc = new Scanner(new File(routinesLengthsFilename))) {
                while (sc.hasNextLine()) {
                    line = sc.nextLine().split(" ");
                    routineID = line[0];
                    length = Integer.parseInt(line[1]);
                    routines.get(routineID).setLength(length);
                }
            } catch (FileNotFoundException ex) {
                System.out.println("Routine length file " + routinesLengthsFilename + " does not exist");
                System.exit(-1);
            }

            for (Entry<String, Routine> r : routines.entrySet()) {
                System.out.println("Routine " + r.getKey() + " takes " + r.getValue().getLength()
                        + " time units to complete and touches devices " + r.getValue().getTouchedDevicesIDs());
            }

            try (Scanner sc = new Scanner(new File(routinesScheduleFilename))) {
                boolean fst_rtn_started = false;
                while (sc.hasNextLine()) {
                    line = sc.nextLine().split(" ");
                    eventTS = Integer.parseInt(line[0]);
                    if (events.get(eventTS) == null) {
                        events.put(eventTS, new ArrayList<Event>());
                    }
                    events.get(eventTS).add(new Event(EventType.ROUTINE_TRIGGERED, line[1]));
                    System.out.println("Routine " + line[1] + " starts at time " + eventTS);
                    if (!fst_rtn_started && testType == TestType.BANDWIDTH_BG) {
                        checkpoint_ts = Math.max(0, eventTS - 1);
                        checkpoint_event = new Event(EventType.CHECKPOINT, line[1]);
                        fst_rtn_started = true;
                    }

                    if (testType == TestType.SYNC_DELAY) {
                        events.get(eventTS).add(new Event(EventType.ROUTINE_TRIGGERED, line[1]));
                        System.out.println("Routine " + line[1] + " starts at time " + eventTS);
                        break;
                    }
                }
            } catch (FileNotFoundException ex) {
                System.out.println("Routine schedule file " + routinesScheduleFilename + " does not exist");
                System.exit(-1);
            }
        }

        File outputFile = null;
        if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
            try {
                outputFile = new File(outputFilename);
                outputFile.createNewFile();
                FileWriter outputWriter = new FileWriter(outputFile);
                outputWriter.write("Device #," + devicesIDs.size() + "\nNode %,0.1\nK," + K + "\n");
                outputWriter.write("Leader Election time,Quorum time,State Transfer time,All k-group time");
                outputWriter.close();
            } catch (IOException e) {
                System.out.println("An error occurred while creating file " + outputFilename + ".");
                e.printStackTrace();
            }
        }

        Map<String, KGroupManager> nodes = new HashMap<>();
        for (String nodeID : nodesIDs) {
            nodes.put(nodeID,
                    new KGroupManager(nodeID, F, K, epochLength, routineMonitorPeriod, nodesIDs, devicesIDs, routines,
                            deviceKGroupRange, routineKGroupRange, network, 0, end, events, outputFilename, debug,
                            testType, electionPolicy));
        }
        if (testType.equals(TestType.BANDWIDTH_BG) && !nodesIDs.isEmpty()) {
            nodes.get(nodesIDs.get(0)).addEvent(checkpoint_ts, checkpoint_event);
        }

        boolean existUnprocessedEvents = true;
        int t_terminate = testType.equals(TestType.BANDWIDTH_BG) ? end * 2 : end;
        if (testType.equals(TestType.BANDWIDTH_BG) && !nodesIDs.isEmpty()) {
            nodes.get(nodesIDs.get(0)).addEvent(t_terminate, checkpoint_event);
        }
        for (int curTS = 0; curTS <= t_terminate || network.existUnreadMsgs() || existUnprocessedEvents; curTS++) {
            if (curTS % epochLength == 0)
                System.out.println("\n----------------EPOCH " + (curTS / epochLength + 1) + "----------------\n");

            existUnprocessedEvents = false;
            for (KGroupManager node : nodes.values()) {
                node.incrementTS();
                node.recvAndProcessMsgs(curTS);
                node.existUnprocessedEvents();
                if (!existUnprocessedEvents && node.existUnprocessedEvents()) {
                    existUnprocessedEvents = true;
                }
            }
        }

        if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
            for (KGroupManager node : nodes.values()) {
                node.closeOutputWriters();
            }

            try {
                FileWriter outputWriter = new FileWriter(outputFile, true);
                outputWriter.write("\nTotal messages sent in the network," + network.totalMessages());
                outputWriter
                        .write("\nAverage bandwidth of messages sent in the network," + network.averageBandwidth(end));
                outputWriter.close();
            } catch (IOException e) {
                System.out.println("An error occured while writing to the output file about the sent messages.");
            }
        }
        // Get routine delay metrics and write/append to file.
        else if (testType == TestType.CLIENT_DELAY) {
            RoutineMetricSingleton routineMetrics = RoutineMetricSingleton.getInstance();
            List<String> delayStrings = routineMetrics.getClientDelaysInString();
            try {
                File fout = new File(outputFilename);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("RoutineID,SeqNo,ClientDelayUsr,ClientDelaySys,ClientDelayAck\n");
                writer.close();
                writer = new FileWriter(fout, true);
                for (String delay : delayStrings) {
                    writer.write(delay + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (testType == TestType.SYNC_DELAY) {
            RoutineMetricSingleton routineMetrics = RoutineMetricSingleton.getInstance();
            List<String> delayStrings = routineMetrics.getSyncDelayInString();
            try {
                File fout = new File(outputFilename);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("RoutineID,SeqNo,SyncDelay\n");
                writer.close();
                writer = new FileWriter(fout, true);
                for (String delay : delayStrings) {
                    writer.write(delay + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (testType == TestType.BANDWIDTH) {
            network.metric.recordRawMsgData(outputFilename);
            network.metric.printAllMessageSummary();
        } else if (testType == TestType.BANDWIDTH_BG) {
            network.metric.recordRawMsgData(outputFilename);
        } else if (testType == TestType.BALANCING) {
            try {
                File fout = new File(outputFilename);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("id,leader,member,ld_and_mem,sing_leader,sing_member,sing_idle\n");
                writer.close();
                writer = new FileWriter(fout, true);
                // Align ending time across nodes for role time collection.
                int max_ts = 0;
                for (KGroupManager node : nodes.values())
                    if (node.getCurrentTime() > max_ts) {
                        max_ts = node.getCurrentTime();
                    }

                for (KGroupManager node : nodes.values()) {
                    node.finalizeRole();
                    node.finalizeRole(max_ts);
                    writer.write(node.getRoleTimeInString() + "\n");
                }
                writer.close();
            } catch (IOException e) {
                System.out.println("An error occurred while creating file " + outputFilename + ".");
                e.printStackTrace();
            }

            // Record Role count over time.
            for (NodeRole role : List.of(NodeRole.LEADER, NodeRole.MEMBER, NodeRole.NON_IDLE)) {
                String fname = outputFilename.substring(0, outputFilename.length() - 4) + "_" +
                        role.name().toLowerCase() + ".csv";
                try {
                    File fout = new File(fname);
                    if (!fout.exists()) {
                        fout.createNewFile();
                    }
                    FileWriter writer = new FileWriter(fout);
                    writer.write("num_role,time\n");
                    writer.close();
                    writer = new FileWriter(fout, true);

                    HashMap<Integer, Integer> sum_count = new HashMap<>();
                    for (KGroupManager node : nodes.values()) {
                        HashMap<Integer, Integer> role_count = node.getRoleCountOverTime(role);
                        role_count.forEach((time, count) -> sum_count.merge(time, count, Integer::sum));
                    }
                    for (Map.Entry<Integer, Integer> entry : sum_count.entrySet()) {
                        if (entry.getValue() > 0) {
                            writer.write(entry.getKey() + "," + entry.getValue() + "\n");
                        }
                    }
                    writer.close();
                } catch (IOException e) {
                    System.out.println("An error occurred while creating file " + fname + ".");
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(helpText);
            System.exit(0);
        }

        String arg;
        // default parameter values
        boolean debug = false;
        TestType testType = TestType.INKGROUP_BENCHMARK_WO_FAILURE;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seed = 0;
        int routineNo = 10, deviceNo = 1000;
        String deviceTopologyShape = "grid", deviceTopologyDims = "10,10,10";
        String nodePercentage = "0.05";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "", avgDevicesPerRoutine = "", deviceClusters = "";
        String routineLengthDistribution = "", maxRoutineLength = "";
        String routineScheduleDistribution = "", routineScheduleClusters = "";

        String devicesTopologyFilename = "workloads/sample_device_topology.txt";
        String nodesListFilename = "workloads/sample_node_list.txt";
        String nodesScheduleFilename = "workloads/sample_node_schedule.txt";
        String routinesDevicesMapFilename = "workloads/sample_routine_map.txt";
        String routinesLengthsFilename = "workloads/sample_routine_lengths.txt";
        String routinesScheduleFilename = "workloads/sample_routine_schedule.txt";
        int end = 30, F = 1, K = 5, epochLength = 100 /* time units */, routineMonitorPeriod = 10, hopOWD = 1;
        int deviceKGroupRange = 1, routineKGroupRange = 1;

        String outputFilename;

        try {
            for (int i = 0; i < args.length; i++) {
                arg = args[i];
                switch (arg) {
                    case "-e":
                    case "-E":
                    case "-end":
                        end = Integer.parseInt(args[++i]);
                        break;
                    case "-rn":
                    case "-RN":
                    case "-routineNo":
                        routineNo = Integer.parseInt(args[++i]);
                        break;
                    case "-dn":
                    case "-DN":
                    case "-deviceNo":
                        deviceNo = Integer.parseInt(args[++i]);
                        break;
                    case "-dts":
                    case "-DTS":
                    case "-deviceTopologyShape":
                        // arg: -dts grid
                        deviceTopologyShape = args[++i];
                        break;
                    case "-dtd":
                    case "-DTD":
                    case "-deviceTopologyDims":
                        // arg: -dtd 3,3
                        deviceTopologyDims = args[++i];
                        break;
                    case "-np":
                    case "-NP":
                    case "-nodePercentage":
                        // arg: -np 0.1
                        nodePercentage = args[++i];
                        break;
                    case "-cp":
                    case "-CP":
                    case "-churnProbability":
                        // arg: -cp 0.1
                        nodeChurnProbability = args[++i];
                        break;
                    case "-nsd":
                    case "-NSD":
                    case "-nodeScheduleDistribution":
                        // arg: -nsd uniform
                        nodeScheduleDistribution = args[++i];
                        break;
                    case "-nsc":
                    case "-NSC":
                    case "-nodeScheduleClusters":
                        // arg: -nsc 2
                        nodeScheduleClusters = args[++i];
                        break;
                    case "-rdmd":
                    case "-RDMD":
                    case "-routineDeviceMapDistribution":
                        // arg: -rdmd uniform
                        routineDeviceMapDistribution = args[++i];
                        break;
                    case "-adpr":
                    case "-ADPR":
                    case "-avgDevicesPerRoutine":
                        // arg: -mdpr 5
                        avgDevicesPerRoutine = args[++i];
                        break;
                    case "-dc":
                    case "-DC":
                    case "-deviceClusters":
                        // arg: -nsc 2
                        deviceClusters = args[++i];
                        break;
                    case "-rld":
                    case "-RLD":
                    case "-routineLengthDistribution":
                        // arg: -rld uniform
                        routineLengthDistribution = args[++i];
                        break;
                    case "-mrl":
                    case "-MRL":
                    case "-maxRoutineLength":
                        // arg: -mrl 6
                        maxRoutineLength = args[++i];
                        break;
                    case "-rsd":
                    case "-RSD":
                    case "-routineScheduleDistribution":
                        // arg: -rsd uniform
                        routineScheduleDistribution = args[++i];
                        break;
                    case "-rsc":
                    case "-RSC":
                    case "-routineScheduleClusters":
                        // arg: -rsc 2
                        routineScheduleClusters = args[++i];
                        break;
                    case "-rm":
                    case "-RM":
                    case "-routineMonitor":
                        // arg: -rm 10
                        routineMonitorPeriod = Integer.parseInt(args[++i]);
                        break;
                    case "-f":
                    case "-F":
                    case "-faults":
                        // arg: -f 1
                        F = Integer.parseInt(args[++i]);
                        break;
                    case "-k":
                    case "-K":
                    case "-kgroupSize":
                        // arg: -k 5
                        K = Integer.parseInt(args[++i]);
                        break;
                    case "-el":
                    case "-EL":
                    case "-epochLength":
                        // arg: -el 100
                        epochLength = Integer.parseInt(args[++i]);
                        break;
                    case "-owd":
                    case "-OWD":
                    case "-oneWayDelay":
                        hopOWD = Integer.parseInt(args[++i]);
                        break;
                    case "-RD":
                    case "-rd":
                    case "-rangeDeviceKGroup":
                        deviceKGroupRange = Integer.parseInt(args[++i]);
                        break;
                    case "-RR":
                    case "-rr":
                    case "-rangeRoutineKGroup":
                        routineKGroupRange = Integer.parseInt(args[++i]);
                        break;
                    case "-d":
                    case "-D":
                    case "-debug":
                        debug = true;
                        System.out.println("Debug mode is on");
                        break;
                    case "-lep":
                    case "-LEP":
                    case "-electionPolicy":
                        String lepStr = args[++i];
                        if (lepStr.equals("sh") || lepStr.equals("SH")) {
                            electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
                        } else if (lepStr.equals("sID") || lepStr.equals("SID")) {
                            electionPolicy = LeaderElectionPolicy.SMALLEST_ID;
                        } else if (lepStr.equals("cn") || lepStr.equals("CN")) {
                            electionPolicy = LeaderElectionPolicy.CENTRAL_NODE;
                        } else {
                            electionPolicy = null;
                        }
                        break;
                    case "-h":
                    case "-H":
                    case "-help":
                        System.out.println(helpText);
                        if (args.length == 1) {
                            System.exit(0);
                        }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(helpText);
            System.exit(-1);
        }

        createWorkloads(
                seed,
                deviceTopologyShape, deviceTopologyDims,
                deviceNo, nodePercentage,
                nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                routineLengthDistribution, maxRoutineLength,
                routineScheduleDistribution, routineScheduleClusters);

        devicesTopologyFilename = "workloads/device_topology_d" + String.valueOf(deviceNo) + "_" + deviceTopologyShape
                + deviceTopologyDims + ".txt";
        nodesListFilename = "workloads/node_list_d" + String.valueOf(deviceNo) + "_np" + nodePercentage + ".txt";
        nodesScheduleFilename = "workloads/node_schedule_d" + String.valueOf(deviceNo) + "_np" + nodePercentage + "_cp"
                + nodeChurnProbability + "_" + nodeScheduleDistribution
                + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + "_e"
                + String.valueOf(end) + ".txt";

        routinesDevicesMapFilename = "workloads/routine_device_map_r" + String.valueOf(routineNo) + "_d"
                + String.valueOf(deviceNo) + "_a" + avgDevicesPerRoutine + "_" + routineDeviceMapDistribution
                + (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "") + ".txt";
        routinesLengthsFilename = "workloads/routine_lengths_r" + String.valueOf(routineNo) + "_m" + maxRoutineLength
                + "_" + routineLengthDistribution + ".txt";
        routinesScheduleFilename = "workloads/routine_schedule_r" + String.valueOf(routineNo) + "_e"
                + String.valueOf(end) + (routineScheduleDistribution.equals("clusters") ? routineScheduleClusters : "")
                + ".txt";

        outputFilename = "outputs/d" + String.valueOf(deviceNo) + "_" + deviceTopologyShape + deviceTopologyDims + "_np"
                + nodePercentage + "_cp" + nodeChurnProbability + "_" + nodeScheduleDistribution
                + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + "_e"
                + String.valueOf(end) + "_r" + String.valueOf(routineNo) + "_a" + avgDevicesPerRoutine + "_"
                + routineDeviceMapDistribution + (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "")
                + "_m" + maxRoutineLength + "_" + routineLengthDistribution + "_e" + String.valueOf(end)
                + (routineScheduleDistribution.equals("clusters") ? routineScheduleClusters : "") + ".csv";

        simulate(
                devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                outputFilename,
                hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                deviceKGroupRange, routineKGroupRange,
                debug, testType);
    }
}
