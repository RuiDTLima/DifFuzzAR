package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtWhileImpl;
import java.util.Iterator;

class CtWhileHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtWhileHandle.class);

    /**
     * The method where a 'while' statement is handled. The body of the 'while' might be modified to protect any variable
     * access and then its body will iterated over to see if it requires any changes.
     * @param factory           The factory used to create new instructions.
     * @param returnsIterator   An iterator over the returns of the method.
     * @param returnVariable    The variable to be returned.
     * @param returnElement     The valid return expression returned in the final return statement.
     * @param afterWhileReturn  Indicates if this 'while' statement happens after a 'while' statement.
     * @param newBody           A block of statements that will become the new body of the vulnerable method.
     * @param currentStatement  The 'while' statement under analysis.
     * @return                  Indicates that next statement will be after a 'while' cycle.
     */
    static boolean handleWhile(Factory factory,
                               Iterator<CtCFlowBreak> returnsIterator,
                               CtLocalVariable<?> returnVariable,
                               CtExpression<?> returnElement,
                               boolean afterWhileReturn,
                               CtBlock<?> newBody,
                               CtStatement currentStatement) {

        logger.info("Handling a while statement.");
        CtWhileImpl whileStatement = (CtWhileImpl) currentStatement;
        CtWhile newWhileStatement = whileStatement.clone();
        CtExpression<Boolean> stoppingCondition = whileStatement.getLoopingExpression();

        CtLoop newLoop = CtLoopHandle.updateLoop(factory, whileStatement, stoppingCondition);
        CtBlock<?> body = (CtBlock<?>) newLoop.getBody();

        CtBlock<?> newWhileBody = EarlyExitVulnerabilityCorrection
                .handleStatements(factory, body.iterator(), returnsIterator, returnVariable, returnElement, afterWhileReturn);

        newWhileStatement.setBody(newWhileBody);
        newBody.addStatement(newWhileStatement);
        return true;
    }
}