package kgroup.sleep;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gap 7: Intermittent Device Support.
 *
 * <p>Section IV-C's failure detector and coterie-update logic cannot tell
 * a sleep-cycled sensor or a battery-saving actuator that goes quiet on
 * its own schedule apart from a device that has actually crashed: both
 * simply stop responding for a while. This registry is the "announced
 * sleep window" mechanism from the Gap 7 proposed direction: a device
 * (or its operator) declares, ahead of time, the [start, end) simulator
 * timestamps during which it expects to be unreachable, and every other
 * node consults this registry -- instead of relying solely on the
 * existing timeout/ground-truth-driven failure detector -- before
 * reacting to that device's absence as a failure.
 *
 * <p>Loaded from a device sleep-schedule file: one window per line,
 * whitespace-separated "deviceID sleepStartTs sleepEndTs" (a device may
 * appear on multiple lines for multiple, non-overlapping windows).
 *
 * <p>Default behavior (never loaded / not enabled) is a complete no-op:
 * isAnnouncedSleep(...) always returns false, so KGroupManager and
 * KGroup fall back to treating every absence as a genuine failure,
 * exactly as before.
 */
public final class DeviceSleepRegistry {

    private static volatile boolean enabled = false;
    // deviceID -> list of [start, end] (inclusive start, exclusive end) windows
    private static final Map<String, List<int[]>> windows = new HashMap<>();

    private DeviceSleepRegistry() {
    }

    public static synchronized void load(String filename) throws IOException {
        windows.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                String deviceID = parts[0];
                int start = Integer.parseInt(parts[1]);
                int end = Integer.parseInt(parts[2]);
                windows.computeIfAbsent(deviceID, k -> new ArrayList<>()).add(new int[] { start, end });
            }
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** True iff deviceID has announced a sleep window covering timestamp ts. */
    public static boolean isAnnouncedSleep(String deviceID, int ts) {
        List<int[]> deviceWindows = windows.get(deviceID);
        if (deviceWindows == null) {
            return false;
        }
        for (int[] w : deviceWindows) {
            if (ts >= w[0] && ts < w[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The end timestamp of the announced window covering ts for deviceID,
     * or -1 if ts is not inside any announced window for that device.
     * Used to schedule the device's automatic wake-up.
     */
    public static int windowEnd(String deviceID, int ts) {
        List<int[]> deviceWindows = windows.get(deviceID);
        if (deviceWindows == null) {
            return -1;
        }
        for (int[] w : deviceWindows) {
            if (ts >= w[0] && ts < w[1]) {
                return w[1];
            }
        }
        return -1;
    }

    public static boolean hasSchedule(String deviceID) {
        return windows.containsKey(deviceID);
    }

    /** Test/rerun hook: clears loaded windows and disables the feature. */
    public static synchronized void reset() {
        windows.clear();
        enabled = false;
    }
}
