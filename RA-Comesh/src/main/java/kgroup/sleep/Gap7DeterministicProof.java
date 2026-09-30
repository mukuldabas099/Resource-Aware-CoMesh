package kgroup.sleep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionPolicy;
import kgroup.Membership;

/**
 * Gap 7: Intermittent Device Support -- isolated, deterministic proof.
 *
 * Exercises kgroup.KGroup.newKGroupMembers(...) directly (the exact
 * coterie-update mechanism named in Section IV-C) and
 * kgroup.sleep.DeviceSleepRegistry, with no Simulator/network involved.
 *
 * Every scenario below is set up so the coterie needs EXACTLY K eligible
 * candidates -- that makes the outcome deterministic and independent of
 * the underlying SHA-1 hash ranking (with exactly K candidates for K
 * seats, every eligible candidate is selected, full stop), so the proof
 * doesn't depend on guessing hash values.
 *
 * Run with (no JUnit / test framework required):
 *   java -cp target/classes kgroup.sleep.Gap7DeterministicProof
 */
public class Gap7DeterministicProof {

    public static void main(String[] args) throws IOException {
        boolean ok = true;
        ok &= scenario1_genuineFailureStillEvictsIncumbent();
        ok &= scenario2_announcedSleepRetainsIncumbent();
        ok &= scenario3_featureDisabledFallsBackToEviction();
        ok &= scenario4_registryWindowSemantics();

        if (!ok) {
            System.out.println("\nGap 7 deterministic proof FAILED.");
            System.exit(1);
        }
        System.out.println("\nGap 7 deterministic proof: all scenarios passed.");
    }

    private static KGroup freshKGroup(List<String> incumbents) {
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add("device-under-test");
        Map<String, Membership> membershipList = new HashMap<>();
        membershipList.put("bootstrap", Membership.ONLINE);
        KGroup kgroup = new KGroup(entitiesIDs, membershipList, KGroupType.DEVICE, 1, 5, 0,
                LeaderElectionPolicy.SMALLEST_HASH);
        kgroup.curNodesIDs.clear();
        kgroup.curNodesIDs.addAll(incumbents);
        return kgroup;
    }

    private static boolean scenario1_genuineFailureStillEvictsIncumbent() {
        System.out.println("Scenario 1: a genuine (unannounced) failure still evicts the incumbent (regression)");
        DeviceSleepRegistry.reset();
        DeviceSleepRegistry.setEnabled(false);

        List<String> incumbents = List.of("n0", "n1", "n2", "n3", "n4");
        KGroup kgroup = freshKGroup(incumbents);

        Map<String, Membership> membershipList = new HashMap<>();
        membershipList.put("n0", Membership.OFFLINE); // genuinely failed, no announced window
        membershipList.put("n1", Membership.ONLINE);
        membershipList.put("n2", Membership.ONLINE);
        membershipList.put("n3", Membership.ONLINE);
        membershipList.put("n4", Membership.ONLINE);

        List<String> newMembers = kgroup.newKGroupMembers(membershipList, 5);
        boolean pass = !newMembers.contains("n0") && newMembers.size() == 4;
        System.out.println("  new members (expect n1..n4, NOT n0): " + newMembers + " -> " + (pass ? "PASS" : "FAIL"));
        return pass;
    }

    private static boolean scenario2_announcedSleepRetainsIncumbent() {
        System.out.println("Scenario 2: an announced-sleep incumbent is retained instead of evicted");
        DeviceSleepRegistry.reset();
        DeviceSleepRegistry.setEnabled(true);
        Gap7Metrics.reset();

        List<String> incumbents = List.of("n0", "n1", "n2", "n3", "n4");
        KGroup kgroup = freshKGroup(incumbents);

        Map<String, Membership> membershipList = new HashMap<>();
        membershipList.put("n0", Membership.SLEEPING); // announced, planned absence
        membershipList.put("n1", Membership.ONLINE);
        membershipList.put("n2", Membership.ONLINE);
        membershipList.put("n3", Membership.ONLINE);
        membershipList.put("n4", Membership.ONLINE);

        List<String> newMembers = kgroup.newKGroupMembers(membershipList, 5);
        boolean pass = newMembers.contains("n0") && newMembers.size() == 5
                && Gap7Metrics.getInstance().getCoterieRetentions() > 0;
        System.out.println("  new members (expect n0..n4, n0 retained while asleep): " + newMembers + " -> "
                + (pass ? "PASS" : "FAIL"));
        System.out.println("  coterie retentions recorded: " + Gap7Metrics.getInstance().getCoterieRetentions());
        return pass;
    }

    private static boolean scenario3_featureDisabledFallsBackToEviction() {
        System.out.println("Scenario 3: SLEEPING membership without the feature enabled still evicts (fail-closed default)");
        DeviceSleepRegistry.reset();
        DeviceSleepRegistry.setEnabled(false); // feature off, even though n0 is (somehow) SLEEPING

        List<String> incumbents = List.of("n0", "n1", "n2", "n3", "n4");
        KGroup kgroup = freshKGroup(incumbents);

        Map<String, Membership> membershipList = new HashMap<>();
        membershipList.put("n0", Membership.SLEEPING);
        membershipList.put("n1", Membership.ONLINE);
        membershipList.put("n2", Membership.ONLINE);
        membershipList.put("n3", Membership.ONLINE);
        membershipList.put("n4", Membership.ONLINE);

        List<String> newMembers = kgroup.newKGroupMembers(membershipList, 5);
        boolean pass = !newMembers.contains("n0") && newMembers.size() == 4;
        System.out.println("  new members (expect n1..n4, NOT n0, feature disabled): " + newMembers + " -> "
                + (pass ? "PASS" : "FAIL"));
        return pass;
    }

    private static boolean scenario4_registryWindowSemantics() throws IOException {
        System.out.println("Scenario 4: DeviceSleepRegistry window boundary semantics");
        DeviceSleepRegistry.reset();

        java.io.File tmp = java.io.File.createTempFile("gap7_sleep_schedule", ".txt");
        tmp.deleteOnExit();
        try (java.io.FileWriter w = new java.io.FileWriter(tmp)) {
            w.write("sensor-7 100 200\n");
            w.write("sensor-7 500 600\n");
            w.write("actuator-3 50 75\n");
        }
        DeviceSleepRegistry.load(tmp.getAbsolutePath());
        DeviceSleepRegistry.setEnabled(true);

        boolean pass = true;
        pass &= check("before first window (ts=99)", !DeviceSleepRegistry.isAnnouncedSleep("sensor-7", 99));
        pass &= check("at window start (ts=100, inclusive)", DeviceSleepRegistry.isAnnouncedSleep("sensor-7", 100));
        pass &= check("inside first window (ts=150)", DeviceSleepRegistry.isAnnouncedSleep("sensor-7", 150));
        pass &= check("at window end (ts=200, exclusive)", !DeviceSleepRegistry.isAnnouncedSleep("sensor-7", 200));
        pass &= check("between windows (ts=300)", !DeviceSleepRegistry.isAnnouncedSleep("sensor-7", 300));
        pass &= check("inside second window (ts=550)", DeviceSleepRegistry.isAnnouncedSleep("sensor-7", 550));
        pass &= check("window end lookup (ts=150 -> 200)", DeviceSleepRegistry.windowEnd("sensor-7", 150) == 200);
        pass &= check("unrelated device unaffected (ts=60)", DeviceSleepRegistry.isAnnouncedSleep("actuator-3", 60));
        pass &= check("device with no schedule never sleeps", !DeviceSleepRegistry.isAnnouncedSleep("unknown-device", 150));
        return pass;
    }

    private static boolean check(String label, boolean condition) {
        System.out.println("  " + label + " -> " + (condition ? "PASS" : "FAIL"));
        return condition;
    }
}
