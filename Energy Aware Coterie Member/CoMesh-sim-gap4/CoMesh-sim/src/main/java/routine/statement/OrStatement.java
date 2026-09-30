package routine.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class OrStatement extends RoutineStatement {
    List<RoutineStatement> innerStatements;

    public OrStatement(RoutineStatement statement) {
        innerStatements = new ArrayList<RoutineStatement>();
        innerStatements.add(statement);
    }

    public OrStatement(List<RoutineStatement> statements) {
        innerStatements = statements;
    }

    public void add(RoutineStatement statement) {
        innerStatements.add(statement);
    }

    public void addAll(List<RoutineStatement> statements) {
        innerStatements.addAll(statements);
    }

    @Override
    public RoutineStatement negate() {
        ListIterator<RoutineStatement> listIterator = innerStatements.listIterator();
        while (listIterator.hasNext()) {
            listIterator.set(listIterator.next().negate());
        }
        return new AndStatement(innerStatements);
    }

    public List<RoutineStatement> getInnerStatements() {
        return innerStatements;
    }

    @Override
    public String toString() {
        String strRepr = "[";
        for (RoutineStatement innerStatement : innerStatements) {
            strRepr += innerStatement.toString() + " or ";
        }
        return strRepr.substring(0, strRepr.length() - 4).toString() + "]";
    }
}
