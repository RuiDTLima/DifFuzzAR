package earlyexitcorrection;

import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtWhileImpl;
import java.util.Iterator;

class CtWhileHandle {
    static boolean handleWhile(Factory factory, Iterator<CtCFlowBreak> returnsIterator, CtLocalVariable<?> variable, CtExpression<?> returnElement, boolean afterCycleReturn, CtBlock<?> newBody, CtStatement currentStatement) {
        CtWhileImpl whileStatement = (CtWhileImpl) currentStatement;
        CtWhile newWhileStatement = whileStatement.clone();
        CtExpression<Boolean> stoppingCondition = whileStatement.getLoopingExpression();

        CtLoop newLoop = CtLoopHandle.updateLoop(factory, whileStatement, stoppingCondition);
        CtBlock<?> body = (CtBlock<?>) newLoop.getBody();

        CtBlock<?> newWhileBody = EarlyExitVulnerabilityCorrection.handleStatements(factory, body.iterator(), returnsIterator, variable, returnElement, afterCycleReturn);

        newWhileStatement.setBody(newWhileBody);
        newBody.addStatement(newWhileStatement);
        return true;
    }
}