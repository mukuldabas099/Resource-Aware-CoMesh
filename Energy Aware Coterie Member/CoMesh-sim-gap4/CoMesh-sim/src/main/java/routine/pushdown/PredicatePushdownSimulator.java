package routine.pushdown;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;

import dijkstra.Dijkstra;
import dijkstra.Graph;
import dijkstra.Node;

import network.message.Message;
import network.message.MessageType;
import network.message.payload.monitor.DeviceReadingUpdateMessagePayload;
import network.message.payload.monitor.DevicePredicateUpdateMessagePayload;

import routine.statement.AndStatement;
import routine.statement.RoutineStatement;
import routine.statement.Statement;
import routine.statement.condition.ConditionRelation;
import routine.statement.condition.DeviceReadingCondition;

/**
 * Gap 3: Smart Predicate Push-Down
 * ---------------------------------
 * This is a standalone experiment harness (it does not touch the main
 * KGroupManager/Simulator event loop) built to measure, under CoMesh's own
 * mesh topology and hop-latency model, the concrete cost of the paper's
 * "Current State" (every device state update is shipped in full to the
 * routine leader, which evaluates the whole predicate) versus the
 * "Proposed Direction" (the device coterie evaluates the leaf condition(s)
 * that concern its own device locally, and only forwards a single bit to
 * the routine leader, and only when that bit actually flips).
 *
 * For every routine (read from a routine_device_map-style workload file)
 * we build the predicate: AND over all of the routine's touched devices of
 * (reading > threshold). This mirrors the paper's "arbitrary Boolean
 * clauses involving multiple device readings" (Sec. I) using the
 * DeviceReadingCondition/AndStatement classes that already exist in the
 * codebase but (in the current simulator) are never actually driven by
 * live sensor data.
 *
 * Two forwarding policies are simulated side-by-side, over the *same*
 * synthetic sensor trace, so the comparison is apples-to-apples:
 *
 *  BASELINE   - the device coterie leader forwards its raw reading to the
 *               routine leader every time the reading changes (this is
 *               how "ship everything" systems, and the paper's Current
 *               State, behave). The routine leader caches raw readings
 *               and re-evaluates the whole predicate on every arrival.
 *
 *  PUSHDOWN   - the device coterie leader evaluates
 *               "reading > threshold" itself and forwards only the
 *               resulting bit, and only when it changes from the last bit
 *               it sent. The routine leader caches these bits and
 *               re-evaluates the predicate from the cached bits only.
 *
 * Metrics reported per routine (and overall): number of cross-coterie
 * messages, hop-messages (message count times shortest-path hop distance,
 * i.e. the same "hop-to-hop" bandwidth notion used elsewhere in this
 * simulator/paper), serialized bytes-on-the-wire, and the average
 * detection latency (in time units) from the moment the real,
 * ground-truth predicate becomes true to the moment the routine leader
 * observes it as triggered.
 */
public class PredicatePushdownSimulator {

    private static class RoutineSpec {
        String routineID;
        List<String> touchedDevices;
        String routineLeader;
        RoutineStatement predicate; // AND of (reading > threshold) per touched device
    }

    private static class ScheduledUpdate {
        String deviceID;
        float rawValue; // used in baseline mode
        boolean boolValue; // used in push-down mode

        ScheduledUpdate(String deviceID, float rawValue, boolean boolValue) {
            this.deviceID = deviceID;
            this.rawValue = rawValue;
            this.boolValue = boolValue;
        }
    }

    private static class RoutineResult {
        String routineID;
        int numDevices;
        int groundTruthTriggers;
        long baselineMsgs, baselineHopMsgs, baselineBytes;
        long pushdownMsgs, pushdownHopMsgs, pushdownBytes;
        List<Integer> baselineLatencies = new ArrayList<>();
        List<Integer> pushdownLatencies = new ArrayList<>();
    }

    // ---------------------------------------------------------------
    // Topology loading (re-implemented directly on top of the existing
    // dijkstra package rather than network.Network, so that this harness
    // has no dependency on javafx.util.Pair).
    // ---------------------------------------------------------------
    private static Graph loadTopology(String topologyFilename) {
        Graph graph = new Graph();
        Map<String, Node> nodesById = new HashMap<>();
        List<String[]> adjacency = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(topologyFilename))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (!nodesById.containsKey(parts[0])) {
                    Node node = new Node(parts[0]);
                    nodesById.put(parts[0], node);
                    graph.addNode(node);
                }
                adjacency.add(parts);
            }
        } catch (IOException ex) {
            System.out.println("Topology file " + topologyFilename + " does not exist");
            System.exit(-1);
        }
        for (String[] parts : adjacency) {
            Node src = nodesById.get(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                Node dst = nodesById.get(parts[i]);
                if (dst == null) {
                    // neighbor referenced before being declared as its own line; create it
                    dst = new Node(parts[i]);
                    nodesById.put(parts[i], dst);
                    graph.addNode(dst);
                }
                src.addDestination(dst, 1);
            }
        }
        return graph;
    }

    // hop-distance cache: leaderID -> (deviceID -> hop distance)
    private static final Map<String, Map<String, Integer>> hopDistanceCache = new HashMap<>();

    private static int hopDistance(Graph topology, String from, String to) {
        if (from.equals(to)) {
            return 0;
        }
        Map<String, Integer> distances = hopDistanceCache.get(from);
        if (distances == null) {
            Graph tempTopology = new Graph(topology);
            Node source = tempTopology.getNodeByID(from);
            if (source == null) {
                System.out.println("Unknown node '" + from + "' in topology");
                System.exit(-1);
            }
            Dijkstra.calculateShortestPathFromSource(tempTopology, source);
            distances = new HashMap<>();
            for (Node n : tempTopology.getNodes()) {
                distances.put(n.getID(), n.getDistance());
            }
            hopDistanceCache.put(from, distances);
        }
        Integer d = distances.get(to);
        if (d == null || d == Integer.MAX_VALUE) {
            System.out.println("No route from " + from + " to " + to + " - is the topology connected?");
            System.exit(-1);
        }
        return d;
    }

    // ---------------------------------------------------------------
    // Routine loading: routine_device_map file format is
    //   routineID device1 device2 ...
    // (identical to the format used by the main Simulator.)
    // The routine's representative device (and hence its coterie leader,
    // per Sec. IV-D of the paper: "For routines, we pick a random device
    // from its trigger set as its representative") is chosen with a
    // dedicated, seeded RNG so the choice is reproducible but independent
    // of the sensor-trace RNG stream.
    // ---------------------------------------------------------------
    private static List<RoutineSpec> loadRoutines(String routineMapFilename, float threshold, long seed) {
        List<RoutineSpec> routines = new ArrayList<>();
        Random repRng = new Random(seed ^ 0x5EED5EEDL);
        try (Scanner sc = new Scanner(new File(routineMapFilename))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                RoutineSpec spec = new RoutineSpec();
                spec.routineID = parts[0];
                spec.touchedDevices = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    spec.touchedDevices.add(parts[i]);
                }
                spec.routineLeader = spec.touchedDevices.get(repRng.nextInt(spec.touchedDevices.size()));

                List<RoutineStatement> leaves = new ArrayList<>();
                for (String deviceID : spec.touchedDevices) {
                    leaves.add(new Statement(new DeviceReadingCondition(deviceID, ConditionRelation.GREATER, threshold)));
                }
                spec.predicate = new AndStatement(leaves);

                routines.add(spec);
            }
        } catch (IOException ex) {
            System.out.println("Routine map file " + routineMapFilename + " does not exist");
            System.exit(-1);
        }
        return routines;
    }

    // ---------------------------------------------------------------
    // Synthetic sensor trace generation: a two-regime (LOW/HIGH) Markov
    // process with continuous per-tick noise, so that the *raw* reading
    // changes almost every tick (forcing the baseline policy to forward
    // constantly) while the *boolean* "reading > threshold" outcome only
    // flips on the (rarer) regime transitions that cross the threshold -
    // exactly the "predicates that are rarely true" scenario the paper
    // calls out as the case where push-down helps most.
    // ---------------------------------------------------------------
    private static Map<String, float[]> generateTrace(List<String> deviceIDs, int end, float threshold,
            double highProb, double flipProb, double noiseAmplitude, long seed) {
        Random rng = new Random(seed);
        double lowCenter = Math.max(0.0, threshold - 0.3);
        double highCenter = Math.min(1.0, threshold + 0.3);
        Map<String, float[]> trace = new LinkedHashMap<>();
        for (String deviceID : deviceIDs) {
            float[] values = new float[end + 1];
            boolean regimeHigh = rng.nextDouble() < highProb;
            for (int t = 0; t <= end; t++) {
                if (rng.nextDouble() < flipProb) {
                    regimeHigh = rng.nextDouble() < highProb;
                }
                double center = regimeHigh ? highCenter : lowCenter;
                double noise = (rng.nextDouble() * 2 - 1) * noiseAmplitude;
                double v = Math.max(0.0, Math.min(1.0, center + noise));
                values[t] = (float) v;
            }
            trace.put(deviceID, values);
        }
        return trace;
    }

    private static void saveTrace(Map<String, float[]> trace, int end, String filename) {
        try (FileWriter fw = new FileWriter(new File(filename))) {
            fw.write("END " + end + "\n");
            for (Map.Entry<String, float[]> e : trace.entrySet()) {
                StringBuilder sb = new StringBuilder(e.getKey());
                for (float v : e.getValue()) {
                    sb.append(' ').append(v);
                }
                fw.write(sb.toString() + "\n");
            }
        } catch (IOException ex) {
            System.out.println("Could not save trace to " + filename);
            ex.printStackTrace();
        }
    }

    private static Map<String, float[]> loadTrace(String filename, int[] endOut) {
        Map<String, float[]> trace = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String first = br.readLine();
            int end = Integer.parseInt(first.trim().split("\\s+")[1]);
            endOut[0] = end;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                float[] values = new float[end + 1];
                for (int t = 0; t <= end; t++) {
                    values[t] = Float.parseFloat(parts[t + 1]);
                }
                trace.put(parts[0], values);
            }
        } catch (IOException ex) {
            System.out.println("Could not load trace from " + filename);
            System.exit(-1);
        }
        return trace;
    }

    // ---------------------------------------------------------------
    // Local (leaf/AND/OR) predicate evaluation helpers. These deliberately
    // duplicate the logic already present in KGroupManager's
    // isConditionSatisfied/isStatementSatisfied methods: those methods
    // read from KGroupManager's private, per-node deviceReadings map and
    // so cannot be reused directly by this standalone harness.
    // ---------------------------------------------------------------
    private static boolean isSatisfiedRaw(RoutineStatement stmt, Map<String, Float> cachedRaw, float threshold) {
        if (stmt instanceof AndStatement) {
            for (RoutineStatement s : ((AndStatement) stmt).getInnerStatements()) {
                if (!isSatisfiedRaw(s, cachedRaw, threshold)) {
                    return false;
                }
            }
            return true;
        } else if (stmt instanceof Statement) {
            DeviceReadingCondition cond = (DeviceReadingCondition) ((Statement) stmt).getCondition();
            Float v = cachedRaw.get(cond.deviceID);
            if (v == null) {
                return false;
            }
            return v > cond.value;
        }
        return false;
    }

    private static boolean isSatisfiedBool(RoutineStatement stmt, Map<String, Boolean> cachedBool) {
        if (stmt instanceof AndStatement) {
            for (RoutineStatement s : ((AndStatement) stmt).getInnerStatements()) {
                if (!isSatisfiedBool(s, cachedBool)) {
                    return false;
                }
            }
            return true;
        } else if (stmt instanceof Statement) {
            DeviceReadingCondition cond = (DeviceReadingCondition) ((Statement) stmt).getCondition();
            return cachedBool.getOrDefault(cond.deviceID, false);
        }
        return false;
    }

    private static boolean groundTruth(RoutineSpec routine, Map<String, float[]> trace, int t, float threshold) {
        for (String deviceID : routine.touchedDevices) {
            if (!(trace.get(deviceID)[t] > threshold)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Per-message byte size: computed once per (deviceID, routineLeader)
    // pair via real Java serialization of an actual Message+Payload
    // object (like Message.getByteSize() elsewhere in this simulator),
    // then reused for every occurrence of that message, since payload
    // byte size does not depend on the numeric/boolean content, only on
    // the (fixed-length) field types and the (fixed, for a given
    // src/dst/routine) String field lengths.
    // ---------------------------------------------------------------
    private static int rawUpdateByteSize(String deviceID, String routineLeader) {
        DeviceReadingUpdateMessagePayload payload = new DeviceReadingUpdateMessagePayload(deviceID, 0f);
        Message msg = new Message(deviceID, 0, routineLeader, 0, MessageType.DEVICE_READING_UPDATE, payload);
        return msg.getByteSize();
    }

    private static int boolUpdateByteSize(String deviceID, String routineLeader) {
        DevicePredicateUpdateMessagePayload payload = new DevicePredicateUpdateMessagePayload(deviceID, false);
        Message msg = new Message(deviceID, 0, routineLeader, 0, MessageType.DEVICE_PREDICATE_UPDATE, payload);
        return msg.getByteSize();
    }

    // ---------------------------------------------------------------
    // Core per-routine simulation.
    // ---------------------------------------------------------------
    private static RoutineResult simulateRoutine(RoutineSpec routine, Graph topology, Map<String, float[]> trace,
            int end, float threshold, int hopOWD) {
        RoutineResult result = new RoutineResult();
        result.routineID = routine.routineID;
        result.numDevices = routine.touchedDevices.size();

        Map<String, Integer> hopDist = new HashMap<>();
        for (String deviceID : routine.touchedDevices) {
            hopDist.put(deviceID, hopDistance(topology, deviceID, routine.routineLeader));
        }

        int rawBytesPerMsg = rawUpdateByteSize(routine.touchedDevices.get(0), routine.routineLeader);
        int boolBytesPerMsg = boolUpdateByteSize(routine.touchedDevices.get(0), routine.routineLeader);

        // BASELINE state
        Map<String, Float> lastSentRaw = new HashMap<>();
        Map<String, Float> cachedRaw = new HashMap<>();
        Map<Integer, List<ScheduledUpdate>> arrivalsBaseline = new TreeMap<>();
        boolean triggeredBaseline = false;

        // PUSHDOWN state
        Map<String, Boolean> lastSentBool = new HashMap<>();
        Map<String, Boolean> cachedBool = new HashMap<>();
        Map<Integer, List<ScheduledUpdate>> arrivalsPushdown = new TreeMap<>();
        boolean triggeredPushdown = false;

        Integer pendingWindowStart = null;
        boolean prevGroundTruth = false;

        for (int t = 0; t <= end; t++) {
            // 1. track ground-truth trigger windows (computed directly from the raw
            // trace, independent of what either policy has observed so far) so we
            // can measure detection latency and count "real" triggerings. This must
            // happen before arrivals are processed below so that a same-tick
            // (e.g. zero-hop) detection can still be matched against the window
            // that just opened at this very tick.
            boolean gt = groundTruth(routine, trace, t, threshold);
            if (gt && !prevGroundTruth) {
                pendingWindowStart = t;
                result.groundTruthTriggers++;
            } else if (!gt) {
                pendingWindowStart = null;
            }
            prevGroundTruth = gt;

            // 2. device coteries decide what (if anything) to forward, based on the
            // reading at this tick, and schedule arrival at the routine leader after
            // the appropriate number of network hops.
            for (String deviceID : routine.touchedDevices) {
                float v = trace.get(deviceID)[t];
                int delay = hopDist.get(deviceID) * hopOWD;

                Float prevRaw = lastSentRaw.get(deviceID);
                if (prevRaw == null || prevRaw.floatValue() != v) {
                    lastSentRaw.put(deviceID, v);
                    arrivalsBaseline.computeIfAbsent(t + delay, k -> new ArrayList<>())
                            .add(new ScheduledUpdate(deviceID, v, false));
                    result.baselineMsgs++;
                    result.baselineHopMsgs += hopDist.get(deviceID);
                    result.baselineBytes += rawBytesPerMsg;
                }

                boolean satisfiedLocally = v > threshold;
                Boolean prevBool = lastSentBool.get(deviceID);
                if (prevBool == null || prevBool.booleanValue() != satisfiedLocally) {
                    lastSentBool.put(deviceID, satisfiedLocally);
                    arrivalsPushdown.computeIfAbsent(t + delay, k -> new ArrayList<>())
                            .add(new ScheduledUpdate(deviceID, 0f, satisfiedLocally));
                    result.pushdownMsgs++;
                    result.pushdownHopMsgs += hopDist.get(deviceID);
                    result.pushdownBytes += boolBytesPerMsg;
                }
            }

            // 3. routine leader processes anything that arrives exactly at this tick
            // (this naturally also covers same-tick, zero-hop deliveries scheduled
            // just above, since arrivalsBaseline/arrivalsPushdown for tick t are
            // populated before this point in the loop).
            List<ScheduledUpdate> arrivedBaseline = arrivalsBaseline.remove(t);
            if (arrivedBaseline != null) {
                for (ScheduledUpdate u : arrivedBaseline) {
                    cachedRaw.put(u.deviceID, u.rawValue);
                }
                boolean satisfied = isSatisfiedRaw(routine.predicate, cachedRaw, threshold);
                if (satisfied && !triggeredBaseline) {
                    triggeredBaseline = true;
                    if (pendingWindowStart != null) {
                        result.baselineLatencies.add(t - pendingWindowStart);
                    }
                } else if (!satisfied) {
                    triggeredBaseline = false;
                }
            }

            List<ScheduledUpdate> arrivedPushdown = arrivalsPushdown.remove(t);
            if (arrivedPushdown != null) {
                for (ScheduledUpdate u : arrivedPushdown) {
                    cachedBool.put(u.deviceID, u.boolValue);
                }
                boolean satisfied = isSatisfiedBool(routine.predicate, cachedBool);
                if (satisfied && !triggeredPushdown) {
                    triggeredPushdown = true;
                    if (pendingWindowStart != null) {
                        result.pushdownLatencies.add(t - pendingWindowStart);
                    }
                } else if (!satisfied) {
                    triggeredPushdown = false;
                }
            }
        }

        return result;
    }

    private static double avg(List<Integer> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        long sum = 0;
        for (int v : values) {
            sum += v;
        }
        return sum / (double) values.size();
    }

    private static String fmt(double d) {
        return Double.isNaN(d) ? "NA" : String.format("%.3f", d);
    }

    private static double reductionPct(long baseline, long pushdown) {
        if (baseline == 0) {
            return 0.0;
        }
        return 100.0 * (baseline - pushdown) / (double) baseline;
    }

    public static void main(String[] args) {
        String topologyFilename = "workloads/device_topology_d16_grid4,4.txt";
        String routineMapFilename = "workloads/routine_device_map_r5_d16_a2_uniform.txt";
        int end = 2000;
        float threshold = 0.5f;
        double highProb = 0.1;
        double flipProb = 0.02;
        double noiseAmplitude = 0.05;
        long seed = 42;
        int hopOWD = 1;
        String outFilename = null;
        String saveTraceFilename = null;
        String loadTraceFilename = null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-topology":
                        topologyFilename = args[++i];
                        break;
                    case "-routineMap":
                        routineMapFilename = args[++i];
                        break;
                    case "-end":
                        end = Integer.parseInt(args[++i]);
                        break;
                    case "-threshold":
                        threshold = Float.parseFloat(args[++i]);
                        break;
                    case "-highProb":
                        highProb = Double.parseDouble(args[++i]);
                        break;
                    case "-flipProb":
                        flipProb = Double.parseDouble(args[++i]);
                        break;
                    case "-noiseAmplitude":
                        noiseAmplitude = Double.parseDouble(args[++i]);
                        break;
                    case "-seed":
                        seed = Long.parseLong(args[++i]);
                        break;
                    case "-hopOWD":
                        hopOWD = Integer.parseInt(args[++i]);
                        break;
                    case "-out":
                        outFilename = args[++i];
                        break;
                    case "-saveTrace":
                        saveTraceFilename = args[++i];
                        break;
                    case "-loadTrace":
                        loadTraceFilename = args[++i];
                        break;
                    case "-h":
                    case "-help":
                        printHelp();
                        return;
                    default:
                        System.out.println("Unknown argument: " + args[i]);
                        printHelp();
                        System.exit(-1);
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            printHelp();
            System.exit(-1);
        }

        if (outFilename == null) {
            outFilename = "outputs/gap3_pushdown_" + new File(routineMapFilename).getName().replace(".txt", "")
                    + "_e" + end + "_hp" + highProb + "_fp" + flipProb + ".csv";
        }

        Graph topology = loadTopology(topologyFilename);
        List<RoutineSpec> routines = loadRoutines(routineMapFilename, threshold, seed);

        List<String> allTouchedDevices = new ArrayList<>();
        for (RoutineSpec r : routines) {
            for (String d : r.touchedDevices) {
                if (!allTouchedDevices.contains(d)) {
                    allTouchedDevices.add(d);
                }
            }
        }

        Map<String, float[]> trace;
        if (loadTraceFilename != null) {
            int[] loadedEnd = new int[1];
            trace = loadTrace(loadTraceFilename, loadedEnd);
            end = loadedEnd[0];
            System.out.println("Loaded trace from " + loadTraceFilename + " (end=" + end + ")");
        } else {
            trace = generateTrace(allTouchedDevices, end, threshold, highProb, flipProb, noiseAmplitude, seed);
            if (saveTraceFilename != null) {
                saveTrace(trace, end, saveTraceFilename);
                System.out.println("Saved generated trace to " + saveTraceFilename);
            }
        }

        System.out.println("Gap 3 - Smart Predicate Push-Down experiment");
        System.out.println("Topology: " + topologyFilename);
        System.out.println("Routine map: " + routineMapFilename);
        System.out.println("Routines: " + routines.size() + ", devices with traces: " + allTouchedDevices.size());
        System.out.println("end=" + end + " threshold=" + threshold + " highProb=" + highProb + " flipProb="
                + flipProb + " noiseAmplitude=" + noiseAmplitude + " seed=" + seed + " hopOWD=" + hopOWD);
        System.out.println();

        List<RoutineResult> results = new ArrayList<>();
        for (RoutineSpec routine : routines) {
            results.add(simulateRoutine(routine, topology, trace, end, threshold, hopOWD));
        }

        long totalBaselineMsgs = 0, totalBaselineHopMsgs = 0, totalBaselineBytes = 0;
        long totalPushdownMsgs = 0, totalPushdownHopMsgs = 0, totalPushdownBytes = 0;
        int totalGroundTruthTriggers = 0;
        List<Integer> allBaselineLatencies = new ArrayList<>();
        List<Integer> allPushdownLatencies = new ArrayList<>();

        try {
            File outFile = new File(outFilename);
            outFile.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(outFile);
            writer.write("routineID,numDevices,groundTruthTriggers,"
                    + "baselineMsgs,baselineHopMsgs,baselineBytes,baselineAvgLatency,"
                    + "pushdownMsgs,pushdownHopMsgs,pushdownBytes,pushdownAvgLatency,"
                    + "msgReductionPct,hopMsgReductionPct,byteReductionPct\n");

            for (RoutineResult r : results) {
                double msgRed = reductionPct(r.baselineMsgs, r.pushdownMsgs);
                double hopRed = reductionPct(r.baselineHopMsgs, r.pushdownHopMsgs);
                double byteRed = reductionPct(r.baselineBytes, r.pushdownBytes);

                writer.write(String.format("%s,%d,%d,%d,%d,%d,%s,%d,%d,%d,%s,%.3f,%.3f,%.3f\n",
                        r.routineID, r.numDevices, r.groundTruthTriggers,
                        r.baselineMsgs, r.baselineHopMsgs, r.baselineBytes, fmt(avg(r.baselineLatencies)),
                        r.pushdownMsgs, r.pushdownHopMsgs, r.pushdownBytes, fmt(avg(r.pushdownLatencies)),
                        msgRed, hopRed, byteRed));

                totalBaselineMsgs += r.baselineMsgs;
                totalBaselineHopMsgs += r.baselineHopMsgs;
                totalBaselineBytes += r.baselineBytes;
                totalPushdownMsgs += r.pushdownMsgs;
                totalPushdownHopMsgs += r.pushdownHopMsgs;
                totalPushdownBytes += r.pushdownBytes;
                totalGroundTruthTriggers += r.groundTruthTriggers;
                allBaselineLatencies.addAll(r.baselineLatencies);
                allPushdownLatencies.addAll(r.pushdownLatencies);
            }

            writer.write(String.format("TOTAL,,%d,%d,%d,%d,%s,%d,%d,%d,%s,%.3f,%.3f,%.3f\n",
                    totalGroundTruthTriggers,
                    totalBaselineMsgs, totalBaselineHopMsgs, totalBaselineBytes, fmt(avg(allBaselineLatencies)),
                    totalPushdownMsgs, totalPushdownHopMsgs, totalPushdownBytes, fmt(avg(allPushdownLatencies)),
                    reductionPct(totalBaselineMsgs, totalPushdownMsgs),
                    reductionPct(totalBaselineHopMsgs, totalPushdownHopMsgs),
                    reductionPct(totalBaselineBytes, totalPushdownBytes)));
            writer.close();
        } catch (IOException ex) {
            System.out.println("Could not write output file " + outFilename);
            ex.printStackTrace();
        }

        System.out.println(String.format("%-10s %12s %12s %12s %10s", "routineID", "baselineMsgs", "pushdownMsgs",
                "msgReduc%", "avgLatB/P"));
        for (RoutineResult r : results) {
            System.out.println(String.format("%-10s %12d %12d %11.1f%% %5s/%s",
                    r.routineID, r.baselineMsgs, r.pushdownMsgs, reductionPct(r.baselineMsgs, r.pushdownMsgs),
                    fmt(avg(r.baselineLatencies)), fmt(avg(r.pushdownLatencies))));
        }
        System.out.println(String.format("%-10s %12d %12d %11.1f%%", "TOTAL", totalBaselineMsgs, totalPushdownMsgs,
                reductionPct(totalBaselineMsgs, totalPushdownMsgs)));
        System.out.println();
        System.out.println("Hop-messages: baseline=" + totalBaselineHopMsgs + " pushdown=" + totalPushdownHopMsgs
                + " (" + String.format("%.1f", reductionPct(totalBaselineHopMsgs, totalPushdownHopMsgs))
                + "% reduction)");
        System.out.println("Bytes on wire: baseline=" + totalBaselineBytes + " pushdown=" + totalPushdownBytes
                + " (" + String.format("%.1f", reductionPct(totalBaselineBytes, totalPushdownBytes))
                + "% reduction)");
        System.out.println("Wrote results to " + outFilename);
    }

    private static void printHelp() {
        System.out.println("Gap 3 - Smart Predicate Push-Down experiment\n"
                + "Usage: mvn exec:java -Dexec.mainClass=\"routine.pushdown.PredicatePushdownSimulator\" -Dexec.args=\"<args>\"\n"
                + "  -topology <file>       device topology file (default workloads/device_topology_d16_grid4,4.txt)\n"
                + "  -routineMap <file>     routine_device_map file (default workloads/routine_device_map_r5_d16_a2_uniform.txt)\n"
                + "  -end <int>             number of simulated ticks (default 2000)\n"
                + "  -threshold <float>     predicate threshold (default 0.5)\n"
                + "  -highProb <float>      steady-state probability a device is in the HIGH regime (default 0.1)\n"
                + "  -flipProb <float>      per-tick probability of reconsidering the regime (default 0.02)\n"
                + "  -noiseAmplitude <float> per-tick sensor noise amplitude (default 0.05)\n"
                + "  -seed <long>           RNG seed (default 42)\n"
                + "  -hopOWD <int>          one-way delay per network hop, in time units (default 1)\n"
                + "  -out <file>            output CSV path (default outputs/gap3_pushdown_...csv)\n"
                + "  -saveTrace <file>      save the generated sensor trace for reuse/inspection\n"
                + "  -loadTrace <file>      load a previously saved sensor trace instead of generating one\n");
    }
}
