import java.io.File;

import org.junit.jupiter.api.Test;

import kgroup.LeaderElectionPolicy;

public class SimulatorTest {
    public static String getDevicesTopologyFilename(
            int deviceNo, String deviceTopologyShape, String deviceTopologyDims) {
        return "workloads/device_topology/device_topology_d" +
                String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                deviceTopologyDims + ".txt";
    }

    public static String getNodesListFilename(
            int deviceNo, String nodePercentage, int seed) {
        return "workloads/node_list/node_list_d" + String.valueOf(deviceNo) +
                "_np" + nodePercentage + "_s" + String.valueOf(seed) + ".txt";
    }

    public static String getNodesScheduleFilename(
            int deviceNo, String nodePercentage, String nodeChurnProbability,
            String nodeScheduleDistribution, String nodeScheduleClusters, int end,
            int seed) {
        return "workloads/node_schedule/node_schedule_d" + String.valueOf(deviceNo) +
                "_np" + nodePercentage + "_" + "cp" + nodeChurnProbability +
                "_" + nodeScheduleDistribution +
                (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "")
                + "_e" + String.valueOf(end) + "_s" + String.valueOf(seed) + ".txt";
    }

    public static String getRoutinesDevicesMapFilename(
            int routineNo, int deviceNo, String avgDevicesPerRoutine,
            String routineDeviceMapDistribution, String deviceClusters, int seed) {
        return "workloads/routine_device_map/routine_device_map_r" +
                String.valueOf(routineNo) + "_d" + String.valueOf(deviceNo) +
                "_a" + avgDevicesPerRoutine + "_" + routineDeviceMapDistribution +
                (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "") +
                "_s" + String.valueOf(seed) + ".txt";
    }

    public static String getRoutinesLengthsFilename(
            int routineNo, String maxRoutineLength,
            String routineLengthDistribution, int seed) {
        return "workloads/routine_lengths/routine_lengths_r" +
                String.valueOf(routineNo) + "_m" + maxRoutineLength + "_" +
                routineLengthDistribution + "_s" + String.valueOf(seed) + ".txt";
    }

    public static String getRoutinesScheduleFilename(
            int routineNo, int end, String routineScheduleDistribution,
            String routineScheduleClusters, int seed) {
        return "workloads/routine_schedule/routine_schedule_r" +
                String.valueOf(routineNo) + "_e" + String.valueOf(end) + "_" +
                routineScheduleDistribution +
                (routineScheduleDistribution.equals("clusters") ? routineScheduleClusters : "") +
                "_s" + String.valueOf(seed) + ".txt";
    }

    public static String getOutputFilename(
            TestType testType, int deviceNo, String deviceTopologyShape,
            String deviceTopologyDims, String nodePercentage, String nodeChurnProbability,
            String nodeScheduleDistribution, String nodeScheduleClusters,
            int K, String electionPolicy, int epochLength, int end, int routineNo,
            String avgDevicesPerRoutine, String routineDeviceMapDistribution,
            String deviceClusters, String maxRoutineLength, String routineLengthDistribution,
            String routineScheduleDistribution, String routineScheduleClusters, int seed) {
        String testName = "";
        switch (testType) {
            case BALANCING:
                testName = "balance";
                break;
            case BANDWIDTH:
                testName = "bandwidth";
                break;
            case BANDWIDTH_BG:
                testName = "bandwidth_bg";
                break;
            case CLIENT_DELAY:
                testName = "client_delay";
                break;
            case INKGROUP_BENCHMARK_WO_FAILURE:
                testName = "in_kgroup";
                break;
            case INKGROUP_BENCHMARK_W_FAILURE:
                break;
            case LOCKING_TIME:
                break;
            case SYNC_DELAY:
                testName = "sync_delay";
                break;
            default:
                break;

        }

        return "outputs/" + testName + "/" + testName + "_d" +
                String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                deviceTopologyDims + "_np" + nodePercentage + "_cp" +
                nodeChurnProbability + "_" + nodeScheduleDistribution +
                (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") +
                "_k" + String.valueOf(K) + "_" + electionPolicy + "_l" + String.valueOf(epochLength) +
                "_e" + String.valueOf(end) + "_r" + String.valueOf(routineNo) +
                "_a" + avgDevicesPerRoutine + "_" + routineDeviceMapDistribution +
                (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "") +
                "_m" + maxRoutineLength + "_" + routineLengthDistribution +
                (routineScheduleDistribution.equals("clusters") ? routineScheduleClusters : "") +
                "_" + String.valueOf(seed) + ".csv";
    }

    @Test
    public void debuggingSimulatorTest() {
        boolean debug = true;
        TestType testType = TestType.BANDWIDTH;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seed = 0;
        int routineNo = 16, deviceNo = 250;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 1, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 27, routineKGroupRange = 1;

        String deviceTopologyShape = "grid", deviceTopologyDims = "5,5,10";
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        Simulator.createWorkloads(
                seed,
                deviceTopologyShape, deviceTopologyDims,
                deviceNo, nodePercentage,
                nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                routineLengthDistribution, maxRoutineLength,
                routineScheduleDistribution, routineScheduleClusters);

        String devicesTopologyFilename = getDevicesTopologyFilename(
                deviceNo, deviceTopologyShape, deviceTopologyDims);
        String nodesListFilename = getNodesListFilename(
                deviceNo, nodePercentage, seed);
        String nodesScheduleFilename = getNodesScheduleFilename(
                deviceNo, nodePercentage, nodeChurnProbability,
                nodeScheduleDistribution, nodeScheduleClusters, end, seed);

        String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                routineNo, deviceNo, avgDevicesPerRoutine,
                routineDeviceMapDistribution, deviceClusters, seed);
        String routinesLengthsFilename = getRoutinesLengthsFilename(
                routineNo, maxRoutineLength, routineLengthDistribution, seed);
        String routinesScheduleFilename = getRoutinesScheduleFilename(
                routineNo, end, routineScheduleDistribution,
                routineScheduleClusters, seed);

        String outputFilename = getOutputFilename(
                testType, deviceNo, deviceTopologyShape, deviceTopologyDims,
                nodePercentage, nodeChurnProbability, nodeScheduleDistribution,
                nodeScheduleClusters, K, electionPolicy.name, epochLength, end, routineNo,
                avgDevicesPerRoutine, routineDeviceMapDistribution, deviceClusters,
                maxRoutineLength, routineLengthDistribution,
                routineScheduleDistribution, routineScheduleClusters, seed);

        Simulator.simulate(
                devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                outputFilename,
                hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                deviceKGroupRange, routineKGroupRange,
                debug, testType);
    }

    @Test
    public void inKGroupBenchmarkWOFailureTest() {
        boolean debug = false;
        TestType testType = TestType.INKGROUP_BENCHMARK_WO_FAILURE;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int routineNo = 0, deviceNo = 1000, seedNo = 10;
        int F = 1, epochLength = 200 /* time units */, end = (100 - 1) * epochLength, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 1000, routineKGroupRange = 1;
        int[] Ks = { 5, 10, 15, 20, 25 };

        String deviceTopologyShape = "grid", deviceTopologyDims = "10,10,10";
        String[] nodePercentages = { "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0" };
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "", avgDevicesPerRoutine = "", deviceClusters = "";
        String routineLengthDistribution = "", maxRoutineLength = "";
        String routineScheduleDistribution = "", routineScheduleClusters = "";
        String devicesTopologyFilename = getDevicesTopologyFilename(
                deviceNo, deviceTopologyShape, deviceTopologyDims);

        String routinesDevicesMapFilename = "workloads/sample_routine_map.txt";
        String routinesLengthsFilename = "workloads/sample_routine_lengths.txt";
        String routinesScheduleFilename = "workloads/sample_routine_schedule.txt";

        for (Integer K : Ks) {
            for (String nodePercentage : nodePercentages) {
                System.out.println("F = " + F + ", K = " + K + ", NP = " + nodePercentage);

                for (int seed = 0; seed < seedNo; seed++) {
                    System.out.println("seed: " + seed);

                    String nodesListFilename = getNodesListFilename(
                            deviceNo, nodePercentage, seed);
                    String nodesScheduleFilename = getNodesScheduleFilename(
                            deviceNo, nodePercentage, nodeChurnProbability,
                            nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                    Simulator.createWorkloads(
                            seed,
                            deviceTopologyShape, deviceTopologyDims,
                            deviceNo, nodePercentage,
                            nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                            routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                            routineLengthDistribution, maxRoutineLength,
                            routineScheduleDistribution, routineScheduleClusters);

                    String outputFilename = getOutputFilename(
                            testType, deviceNo, deviceTopologyShape, deviceTopologyDims,
                            nodePercentage, nodeChurnProbability, nodeScheduleDistribution,
                            nodeScheduleClusters, K, electionPolicy.name, epochLength, end, routineNo,
                            avgDevicesPerRoutine, routineDeviceMapDistribution,
                            deviceClusters, maxRoutineLength, routineLengthDistribution,
                            routineScheduleDistribution, routineScheduleClusters, seed);

                    Simulator.simulate(
                            devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                            routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                            outputFilename,
                            hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                            deviceKGroupRange, routineKGroupRange,
                            debug, testType);
                }

                String inKGroupDelaysFilename = getOutputFilename(
                        testType, deviceNo, deviceTopologyShape, deviceTopologyDims,
                        nodePercentage, nodeChurnProbability, nodeScheduleDistribution,
                        nodeScheduleClusters, K, electionPolicy.name, epochLength, end, routineNo,
                        avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, 0);

                Simulator.createInKGroupDelaysPlot(inKGroupDelaysFilename, seedNo);

                String inKGroupDelaysPlotsFilename = "outputs/in_kgroup_figures/in_kgroup_d" +
                        String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                        deviceTopologyDims + "_np" + nodePercentage + "_cp" +
                        nodeChurnProbability + "_" + nodeScheduleDistribution +
                        (nodeScheduleDistribution.equals("clusters") ? " -c " +
                                nodeScheduleClusters : "")
                        + "_k" + String.valueOf(K) +
                        "_l" + epochLength + "_e" + String.valueOf(end) + "_r" +
                        String.valueOf(routineNo);

                System.out.println("Plots filename: " + inKGroupDelaysPlotsFilename);

                File inKGroupDelaysPlotsFile = new File(
                        inKGroupDelaysPlotsFilename + "_" + String.valueOf(seedNo) + ".png");
                System.out.println("In-k-group delays plot file exists? " + inKGroupDelaysPlotsFile.exists());
                System.out.println("In-k-group delays plot file length: " + inKGroupDelaysPlotsFile.length());
            }
        }

        Simulator.createInKGroupMessagesPlot(
                "outputs/in_kgroup/in_kgroup_d" + String.valueOf(deviceNo) + "_" + deviceTopologyShape
                        + deviceTopologyDims + "_np0.1_cp" + nodeChurnProbability + "_" + nodeScheduleDistribution
                        + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + "_k5_e"
                        + String.valueOf(end) + "_r" + String.valueOf(routineNo),
                seedNo);
    }

    @Test
    public void clientSyncDelayTest() {
        boolean debug = false;
        int seedNo = 10;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 1, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 27, routineKGroupRange = 1;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;

        String deviceTopologyShape = "grid";
        String[] deviceTopologyDims = { "5,5,2", "5,5,4", "5,5,6", "5,5,8", "5,5,10", "10,10,3", "5,7,10", "10,10,4",
                "5,9,10", "5,10,10", "11,5,10", "6,10,10", "5,10,13", "7,10,10", "5,10,15", "8,10,10", "5,10,17",
                "9,10,10", "5,10,19", "10,10,10" };
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        clientDelayTest(
                debug, seedNo, F, K, epochLength, end, hopOWD, routineMonitorPeriod,
                deviceKGroupRange, routineKGroupRange, electionPolicy, deviceTopologyShape,
                deviceTopologyDims, nodePercentage, nodeChurnProbability,
                nodeScheduleDistribution, nodeScheduleClusters,
                routineDeviceMapDistribution, avgDevicesPerRoutine,
                deviceClusters, routineLengthDistribution, maxRoutineLength,
                routineScheduleDistribution, routineScheduleClusters);
        syncDelayTest(
                debug, seedNo, F, K, epochLength, end, hopOWD, routineMonitorPeriod,
                deviceKGroupRange, routineKGroupRange, electionPolicy, deviceTopologyShape,
                deviceTopologyDims, nodePercentage, nodeChurnProbability,
                nodeScheduleDistribution, nodeScheduleClusters,
                routineDeviceMapDistribution, avgDevicesPerRoutine,
                deviceClusters, routineLengthDistribution, maxRoutineLength,
                routineScheduleDistribution, routineScheduleClusters);

        String outputFilename = getOutputFilename(
                TestType.CLIENT_DELAY, 50, deviceTopologyShape, deviceTopologyDims[0],
                nodePercentage, nodeChurnProbability, nodeScheduleDistribution,
                nodeScheduleClusters, K, electionPolicy.name, epochLength, end, 1,
                avgDevicesPerRoutine, routineDeviceMapDistribution, deviceClusters,
                maxRoutineLength, routineLengthDistribution, routineScheduleDistribution,
                routineScheduleClusters, 0);
        System.out.println(outputFilename);

        Simulator.createClientSyncDelayPlot(outputFilename, seedNo);
    }

    public void clientDelayTest(
            boolean debug, int seedNo, int F, int K, int epochLength, int end,
            int hopOWD, int routineMonitorPeriod,
            int deviceKGroupRange, int routineKGroupRange,
            LeaderElectionPolicy electionPolicy,
            String deviceTopologyShape, String[] deviceTopologyDims,
            String nodePercentage, String nodeChurnProbability,
            String nodeScheduleDistribution, String nodeScheduleClusters,
            String routineDeviceMapDistribution, String avgDevicesPerRoutine,
            String deviceClusters, String routineLengthDistribution,
            String maxRoutineLength, String routineScheduleDistribution,
            String routineScheduleClusters) {
        int routineNo = 1, deviceNo;

        for (int i = 0; i < 20; i++) {
            deviceNo = (i + 1) * 50;
            String devicesTopologyFilename = getDevicesTopologyFilename(
                    deviceNo, deviceTopologyShape, deviceTopologyDims[i]);

            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims[i],
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);
                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        TestType.CLIENT_DELAY, deviceNo, deviceTopologyShape,
                        deviceTopologyDims[i], nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, TestType.CLIENT_DELAY);
            }
        }
    }

    public void syncDelayTest(
            boolean debug, int seedNo, int F, int K, int epochLength, int end,
            int hopOWD, int routineMonitorPeriod,
            int deviceKGroupRange, int routineKGroupRange,
            LeaderElectionPolicy electionPolicy,
            String deviceTopologyShape, String[] deviceTopologyDims,
            String nodePercentage, String nodeChurnProbability,
            String nodeScheduleDistribution, String nodeScheduleClusters,
            String routineDeviceMapDistribution, String avgDevicesPerRoutine,
            String deviceClusters, String routineLengthDistribution,
            String maxRoutineLength, String routineScheduleDistribution,
            String routineScheduleClusters) {
        int routineNo = 1, deviceNo;

        for (int i = 0; i < 20; i++) {
            deviceNo = (i + 1) * 50;
            String devicesTopologyFilename = getDevicesTopologyFilename(
                    deviceNo, deviceTopologyShape, deviceTopologyDims[i]);

            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims[i],
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);
                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        TestType.SYNC_DELAY, deviceNo, deviceTopologyShape,
                        deviceTopologyDims[i], nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, TestType.SYNC_DELAY);
            }
        }
    }

    @Test
    public void clientDelayTest() {
        boolean debug = false;
        TestType testType = TestType.CLIENT_DELAY;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seedNo = 10;
        int routineNo = 1, deviceNo;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 1, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 27, routineKGroupRange = 1;

        String deviceTopologyShape = "grid";
        String[] deviceTopologyDims = { "5,5,2", "5,5,4", "5,5,6", "5,5,8", "5,5,10", "10,10,3", "5,7,10", "10,10,4",
                "5,9,10", "5,10,10", "11,5,10", "6,10,10", "5,10,13", "7,10,10", "5,10,15", "8,10,10", "5,10,17",
                "9,10,10", "5,10,19", "10,10,10" };
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        for (int i = 0; i < 20; i++) {
            deviceNo = (i + 1) * 50;
            String devicesTopologyFilename = getDevicesTopologyFilename(
                    deviceNo, deviceTopologyShape, deviceTopologyDims[i]);

            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims[i],
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);
                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        testType, deviceNo, deviceTopologyShape,
                        deviceTopologyDims[i], nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, testType);
            }
        }

        syncDelayTest();

        String outputFilename = getOutputFilename(
                TestType.CLIENT_DELAY, 50, deviceTopologyShape,
                deviceTopologyDims[0], nodePercentage, nodeChurnProbability,
                nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength, end,
                routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                deviceClusters, maxRoutineLength, routineLengthDistribution,
                routineScheduleDistribution, routineScheduleClusters, 0);

        Simulator.createClientSyncDelayPlot(outputFilename, seedNo);
    }

    @Test
    public void syncDelayTest() {
        boolean debug = false;
        TestType testType = TestType.SYNC_DELAY;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seedNo = 10;
        int routineNo = 1, deviceNo;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 1, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 27, routineKGroupRange = 1;

        String deviceTopologyShape = "grid";
        String[] deviceTopologyDims = { "5,5,2", "5,5,4", "5,5,6", "5,5,8", "5,5,10", "10,10,3", "5,7,10", "10,10,4",
                "5,9,10", "5,10,10", "11,5,10", "6,10,10", "5,10,13", "7,10,10", "5,10,15", "8,10,10", "5,10,17",
                "9,10,10", "5,10,19", "10,10,10" };
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        for (int i = 15; i < 20; i++) {
            deviceNo = (i + 1) * 50;
            String devicesTopologyFilename = getDevicesTopologyFilename(
                    deviceNo, deviceTopologyShape, deviceTopologyDims[i]);
            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims[i],
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);

                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        testType, deviceNo, deviceTopologyShape,
                        deviceTopologyDims[i], nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                if (i == 0 && seed == 4) {
                    debug = true;
                }

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, testType);

                if (i == 0 && seed == 4) {
                    debug = false;
                }
            }
        }
    }

    @Test
    public void bandwithTest() {
        boolean debug = false;
        TestType testType = TestType.BANDWIDTH;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seedNo = 10, routineNos = 5;
        int routineNo, deviceNo = 250;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 1, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 27, routineKGroupRange = 1;

        String deviceTopologyShape = "grid";
        String deviceTopologyDims = "5,5,10";
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        String devicesTopologyFilename = getDevicesTopologyFilename(
                deviceNo, deviceTopologyShape, deviceTopologyDims);

        for (int i = 0; i < routineNos; i++) {
            routineNo = (int) Math.pow(2, i);
            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims,
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);

                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        TestType.BANDWIDTH, deviceNo, deviceTopologyShape,
                        deviceTopologyDims, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, testType);
            }
        }

        String outputFilename = getOutputFilename(
                TestType.BANDWIDTH, deviceNo, deviceTopologyShape,
                deviceTopologyDims, nodePercentage, nodeChurnProbability,
                nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                end, 1, avgDevicesPerRoutine, routineDeviceMapDistribution,
                deviceClusters, maxRoutineLength, routineLengthDistribution,
                routineScheduleDistribution, routineScheduleClusters, 0);

        Simulator.createBandwidthPlot(outputFilename, seedNo);
    }

    @Test
    public void bandwithBackgroundTest() {
        boolean debug = false;
        TestType testType = TestType.BANDWIDTH_BG;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seedNo = 10, routineNos = 1;
        int routineNo, deviceNo = 250;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 20000, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 27, routineKGroupRange = 1;

        String deviceTopologyShape = "grid";
        String deviceTopologyDims = "5,5,10";
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        String devicesTopologyFilename = getDevicesTopologyFilename(
                deviceNo, deviceTopologyShape, deviceTopologyDims);

        for (int i = 0; i < routineNos; i++) {
            routineNo = (int) Math.pow(2, i);
            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims,
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);

                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        TestType.BANDWIDTH_BG, deviceNo, deviceTopologyShape,
                        deviceTopologyDims, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, testType);
                Simulator.createBandwidthBGPlot(outputFilename, seedNo);
            }
        }

        String outputFilename = getOutputFilename(
                TestType.BANDWIDTH_BG, deviceNo, deviceTopologyShape,
                deviceTopologyDims, nodePercentage, nodeChurnProbability,
                nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                end, 1, avgDevicesPerRoutine, routineDeviceMapDistribution,
                deviceClusters, maxRoutineLength, routineLengthDistribution,
                routineScheduleDistribution, routineScheduleClusters, seedNo - 1);
        Simulator.createBandwidthAggregatedBGPlot(outputFilename, seedNo);
    }

    @Test
    public void loadBalancingTest() {
        boolean debug = false;
        TestType testType = TestType.BALANCING;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        int seedNo = 1, routineNos = 1;
        int routineNo, deviceNo = 250;
        int F = 1, K = 5, epochLength = 200 /* time units */, end = 20000, hopOWD = 1 /* time units */,
                routineMonitorPeriod = 10;
        int deviceKGroupRange = 1, routineKGroupRange = 1;

        String deviceTopologyShape = "grid";
        String deviceTopologyDims = "5,5,10";
        String nodePercentage = "0.4";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "uniform", avgDevicesPerRoutine = "5", deviceClusters = "";
        String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        String devicesTopologyFilename = getDevicesTopologyFilename(
                deviceNo, deviceTopologyShape, deviceTopologyDims);

        for (int i = 0; i < routineNos; i++) {
            routineNo = (int) Math.pow(2, i);
            for (int seed = 0; seed < seedNo; seed++) {
                Simulator.createWorkloads(
                        seed,
                        deviceTopologyShape, deviceTopologyDims,
                        deviceNo, nodePercentage,
                        nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                        routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                        routineLengthDistribution, maxRoutineLength,
                        routineScheduleDistribution, routineScheduleClusters);

                String nodesListFilename = getNodesListFilename(
                        deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                        deviceNo, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                        routineNo, deviceNo, avgDevicesPerRoutine,
                        routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                        routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                        routineNo, end, routineScheduleDistribution,
                        routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                        TestType.BALANCING, deviceNo, deviceTopologyShape,
                        deviceTopologyDims, nodePercentage, nodeChurnProbability,
                        nodeScheduleDistribution, nodeScheduleClusters, K, electionPolicy.name, epochLength,
                        end, routineNo, avgDevicesPerRoutine, routineDeviceMapDistribution,
                        deviceClusters, maxRoutineLength, routineLengthDistribution,
                        routineScheduleDistribution, routineScheduleClusters, seed);

                System.out.println("output file name:" + outputFilename);

                Simulator.simulate(
                        devicesTopologyFilename, nodesListFilename, nodesScheduleFilename,
                        routinesDevicesMapFilename, routinesLengthsFilename, routinesScheduleFilename,
                        outputFilename,
                        hopOWD, F, K, electionPolicy, epochLength, routineNo, routineMonitorPeriod, end,
                        deviceKGroupRange, routineKGroupRange,
                        debug, testType);

                Simulator.createBalanceCDF(outputFilename, seed);
            }
        }

    }
}
