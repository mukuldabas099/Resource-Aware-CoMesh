package routine.statement;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import routine.statement.condition.ConditionRelation;
import routine.statement.condition.DeviceReadingCondition;

public class RoutineStatementTest {
    @Test
    public void statementsTest() {
        RoutineStatement smallStmt = new Statement(new DeviceReadingCondition("2", ConditionRelation.GREATER, 7));
        System.out.println(smallStmt);

        RoutineStatement smallNotStmt = new NotStatement(smallStmt);
        System.out.println(smallNotStmt);

        List<RoutineStatement> orInnerStatements = new ArrayList<>();
        orInnerStatements.add(smallNotStmt);
        orInnerStatements.add(new Statement(new DeviceReadingCondition("3", ConditionRelation.LESS_EQUAL, 6)));
        
        List<RoutineStatement> andInnerStatements = new ArrayList<>();
        andInnerStatements.add(new Statement(new DeviceReadingCondition("1", ConditionRelation.EQUAL, 3)));
        andInnerStatements.add(new OrStatement(orInnerStatements));
        
        RoutineStatement andStmt = new AndStatement(andInnerStatements);
        System.out.println(andStmt.toString());
        
        RoutineStatement bigNotStmt = new NotStatement(andStmt);
        System.out.println(bigNotStmt.toString());
    }
}
