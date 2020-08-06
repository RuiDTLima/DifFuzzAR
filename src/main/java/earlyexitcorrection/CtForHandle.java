package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtForImpl;
import java.util.Iterator;

class CtForHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtForHandle.class);

    /**
     * The method where a 'for' is handled, and a new version of the 'for' statement is added to the new body of the method
     * in construction. If the body is not an 'if' statement then the body will be analysed to see if an 'if' statement
     * is needed as protection. The body of the 'for' statement will always be iterated over to see if any changes are
     * required.
     * @param factory   The factory used to create new instructions.
     * @param returnsIterator   An iterator over the returns of the method.
     * @param returnVariable    The variable to be returned.
     * @param returnElement The valid return expression returned in the final return statement. Can't be a binary operator
     *                      nor an invocation that uses a variable.
     * @param afterWhileReturn  Indicates if this 'for' statement happens after a 'while' statement.
     * @param newBody   A block of statements that will become the new body of the vulnerable method.
     * @param currentStatement  The 'for' statement under analysis.
     * @return  Indicates if the next statement will be after a 'while' cycle.
     */
    static boolean handleFor(Factory factory,
                             Iterator<CtCFlowBreak> returnsIterator,
                             CtLocalVariable<?> returnVariable,
                             CtExpression<?> returnElement,
                             boolean afterWhileReturn,
                             CtBlock<?> newBody,
                             CtStatement currentStatement) {

        logger.info("Handling a 'for' statement.");
        CtForImpl forStatement = (CtForImpl) currentStatement;
        CtFor newForStatement = forStatement.clone();
        CtBlock<?> body = (CtBlock<?>) newForStatement.getBody();
        CtExpression<Boolean> stoppingCondition = forStatement.getExpression();

        if (body.getStatements().size() >= 1 && !(body.getStatement(0) instanceof CtIf)) {
            CtLoop newLoop = CtLoopHandle.updateLoop(factory, forStatement, stoppingCondition);
            body = (CtBlock<?>) newLoop.getBody();
        }
        CtBlock<?> newForBody = EarlyExitVulnerabilityCorrection.handleStatements(factory, body.iterator(), returnsIterator, returnVariable, returnElement, afterWhileReturn);

        newForStatement.setBody(newForBody);
        newBody.addStatement(newForStatement);
        return false;
    }
}