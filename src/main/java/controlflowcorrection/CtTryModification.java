package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import java.util.List;

public class CtTryModification {
    private static final Logger logger = LoggerFactory.getLogger(CtTryModification.class);

    /**
     * This method is invoked when a 'try' statement is found. The body of the try will be analysed in search for a
     * possible source of the vulnerability.
     * @param statement			The 'try' statement found.
     * @param factory			The factory used to create new instructions.
     * @param secretVariables	A list containing the secret variables.
     * @param publicArguments	The list containing the public arguments.
     * @return					Returns an array of blocks where in the first index is the original body of the 'try'
     * 							statement with slight modifications and in the second index a completely modified
     * 							version of the body of the 'try' statement.
     */
    static CtBlock<?>[] traverseStatement(CtStatement statement,
                                          Factory factory,
                                          List<CtVariable<?>> secretVariables,
                                          List<CtParameter<?>> publicArguments) {
        logger.info("Found a 'try' statement while traversing the method.");
        CtTry tryStatement = (CtTry) statement;
        CtTry oldTryStatement = factory.createTry();
        CtTry newTryStatement = factory.createTry();
        List<CtCatch> catchers = tryStatement.getCatchers();
        CtBlock<?> finalizer = tryStatement.getFinalizer();
        CtBlock<?> tryStatementBody = tryStatement.getBody();

        oldTryStatement.setCatchers(catchers);
        oldTryStatement.setFinalizer(finalizer);

        newTryStatement.setCatchers(catchers);
        newTryStatement.setFinalizer(finalizer);

        CtBlock<?>[] returnBlocks = new CtBlock[2];
        returnBlocks[0] = new CtBlockImpl<>();
        returnBlocks[1] = new CtBlockImpl<>();

        CtBlock<?>[] returnedStatements = ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, tryStatementBody, secretVariables, publicArguments);

        CtBlock<?> oldBlock = returnedStatements[0];
        oldTryStatement.setBody(oldBlock);

        CtBlock<?> newBlock = returnedStatements[1];
        newTryStatement.setBody(newBlock);

        returnBlocks[0].addStatement(oldTryStatement);
        returnBlocks[1].addStatement(newTryStatement);
        return returnBlocks;
    }
}