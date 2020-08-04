package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtForImpl;
import java.util.Iterator;

class CtForHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtForHandle.class);

    static boolean handleFor(Factory factory,
                             Iterator<CtCFlowBreak> returnsIterator,
                             CtLocalVariable<?> variable,
                             CtExpression<?> returnElement,
                             boolean afterCycleReturn,
                             CtBlock<?> newBody,
                             CtStatement currentStatement) {

        logger.info("Handling a 'for' statement.");
        CtForImpl forStatement = (CtForImpl) currentStatement;
        CtFor newForStatement = forStatement.clone();
        CtBlock<?> body = (CtBlock<?>) newForStatement.getBody();
        CtExpression<Boolean> stoppingCondition = forStatement.getExpression();

        if (body.getStatements().size() == 1 && !(body.getStatement(0) instanceof CtIf)) {
            CtLoop newLoop = CtLoopHandle.updateLoop(factory, forStatement, stoppingCondition);
            body = (CtBlock<?>) newLoop.getBody();
        }
        CtBlock<?> newForBody = EarlyExitVulnerabilityCorrection.handleStatements(factory, body.iterator(), returnsIterator, variable, returnElement, afterCycleReturn);

        newForStatement.setBody(newForBody);
        newBody.addStatement(newForStatement);
        return false;
    }
}