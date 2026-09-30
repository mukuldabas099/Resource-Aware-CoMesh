package kgroup.energy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kgroup.DeviceKGroup;
import kgroup.LeaderElectionPolicy;
import kgroup.Membership;

/**
 * Gap 4: Energy-Aware Coterie Membership -- isolated, deterministic proof.
 *
 * Exercises kgroup.KGroup.newKGroupMembers(...) directly (the same
 * "hash-rank and take the lowest K" primitive DeviceKGroup/RoutineKGroup
 * both use to form/migrate a coterie) with a small, fixed set of 7
 * synthetic online nodes (A-G): only "E" and "G" are given a stable
 * energy profile, every other node is drained (10% battery / 90% CPU).
 *
 * For a range of "entity" IDs (which, together with the epoch number,
 * seed the SHA-1 hash ranking -- see KGroup.hash), it calls
 * newKGroupMembers(desired = 5) once with the Gap 4 constraint disabled
 * and once with it enabled, and prints every case where the disabled
 * (locality-only) run picked zero energy-stable members, together with
 * what the enabled run produced instead.
 *
 * Run with (no JUnit / test framework required):
 *   java -cp target/classes kgroup.energy.Gap4DeterministicProof
 */
public class Gap4DeterministicProof {

    public static void main(String[] args) {
        List<String> onlineNodes = List.of("A", "B", "C", "D", "E", "F", "G");
        Map<String, Membership> membershipList = new HashMap<>();
        for (String node : onlineNodes) {
            membershipList.put(node, Membership.ONLINE);
        }

        // Only E and G are energy-stable; everyone else is drained.
        Map<String, double[]> energy = new HashMap<>();
        for (String node : onlineNodes) {
            if (node.equals("E") || node.equals("G")) {
                energy.put(node, new double[] { 85.0, 20.0 }); // stable
            } else {
                energy.put(node, new double[] { 10.0, 90.0 }); // drained
            }
        }
        writeEnergyRegistryDirectly(energy);

        int desired = 5;
        int discoveredAllLowEnergyCases = 0;

        for (int entityIndex = 0; entityIndex < 50; entityIndex++) {
            List<String> entityIDs = List.of("device_" + entityIndex);

            // Baseline: locality-only hash ranking (Gap 4 constraint disabled).
            NodeEnergyRegistry.setEnabled(false);
            Gap4Metrics.reset();
            DeviceKGroup baselineKGroup = new DeviceKGroup(entityIDs, membershipList, 1, desired, 0,
                    LeaderElectionPolicy.SMALLEST_HASH);
            List<String> baseline = baselineKGroup.newKGroupMembers(membershipList, desired);

            boolean baselineHasStable = false;
            for (String nodeID : baseline) {
                if (isStable(energy, nodeID)) {
                    baselineHasStable = true;
                    break;
                }
            }
            if (baselineHasStable) {
                continue; // not one of the "all low-energy bucket" cases we're looking for
            }
            discoveredAllLowEnergyCases++;

            // Same entity/epoch, energy-aware constraint enabled.
            NodeEnergyRegistry.setEnabled(true);
            Gap4Metrics.reset();
            DeviceKGroup energyAwareKGroup = new DeviceKGroup(entityIDs, membershipList, 1, desired, 0,
                    LeaderElectionPolicy.SMALLEST_HASH);
            List<String> energyAware = energyAwareKGroup.newKGroupMembers(membershipList, desired);

            System.out.println("baseline (all low energy): " + baseline);
            System.out.println("energy-aware fix: " + energyAware);
        }

        NodeEnergyRegistry.setEnabled(false);
        NodeEnergyRegistry.reset();

        if (discoveredAllLowEnergyCases == 0) {
            System.out.println("No all-low-energy baseline bucket found in 50 scanned entity IDs.");
            System.exit(1);
        }
        System.out.println(
                "Found " + discoveredAllLowEnergyCases + " all-low-energy baseline bucket(s) out of 50 scanned entity IDs.");
    }

    private static boolean isStable(Map<String, double[]> energy, String nodeID) {
        double[] p = energy.get(nodeID);
        return p[0] >= NodeEnergyRegistry.BATTERY_THRESHOLD && p[1] <= NodeEnergyRegistry.CPU_THRESHOLD;
    }

    // Writes the fixed A-G energy profile into a temp file and loads it through
    // the real NodeEnergyRegistry.load(...) path, so this proof exercises the
    // exact same loading code the -nef Simulator flag uses.
    private static void writeEnergyRegistryDirectly(Map<String, double[]> energy) {
        try {
            java.io.File tmp = java.io.File.createTempFile("gap4_deterministic_proof_energy", ".txt");
            tmp.deleteOnExit();
            try (java.io.FileWriter w = new java.io.FileWriter(tmp)) {
                for (Map.Entry<String, double[]> e : energy.entrySet()) {
                    w.write(e.getKey() + " " + e.getValue()[0] + " " + e.getValue()[1] + "\n");
                }
            }
            NodeEnergyRegistry.load(tmp.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
