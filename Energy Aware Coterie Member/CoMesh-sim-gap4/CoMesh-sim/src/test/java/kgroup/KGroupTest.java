package kgroup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class KGroupTest {
    @Test
    public void shouldReturnCorrectChargeStatus() {
        List<String> entitiesIDs = new ArrayList<String>();
        entitiesIDs.add("0");
        entitiesIDs.add("1");
        entitiesIDs.add("2");
        entitiesIDs.add("3");
        entitiesIDs.add("4");

        Map<String, Membership> membershipList = new HashMap<>();
        membershipList.put("0", Membership.ONLINE);

        KGroup kgroup = new KGroup(entitiesIDs, membershipList, null, 0, 0, 0, LeaderElectionPolicy.SMALLEST_HASH);

        List<String> testEntitiesIDs = new ArrayList<String>();
        entitiesIDs.add("0");
        entitiesIDs.add("3");

        assertTrue(kgroup.isInChargeOf(testEntitiesIDs));
        assertFalse(kgroup.isInChargeOf("5"));
    }
}
