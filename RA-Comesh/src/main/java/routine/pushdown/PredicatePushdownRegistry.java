package routine.pushdown;

/**
 * Gap 3: Smart Predicate Push-Down -- global on/off switch for the *live*
 * KGroupManager integration.
 *
 * Mirrors kgroup.energy.NodeEnergyRegistry / kgroup.sleep.DeviceSleepRegistry:
 * a small static registry rather than a field threaded through every
 * KGroupManager constructor, since the push-down policy is a single
 * global choice for the whole run (every device leader in the mesh
 * applies the same forwarding policy), exactly like the existing
 * Gap 4/7 switches.
 *
 * Default behavior (never enabled) is a complete no-op for the baseline
 * "Current State" push path (KGroupManager.setReading(...) still forwards
 * every raw reading change via DEVICE_READING_UPDATE, unchanged) -- Gap 3
 * only changes what gets forwarded once explicitly turned on.
 */
public final class PredicatePushdownRegistry {

    private static volatile boolean enabled = false;

    private PredicatePushdownRegistry() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Test/rerun hook. */
    public static void reset() {
        enabled = false;
    }
}
