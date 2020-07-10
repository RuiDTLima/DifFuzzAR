package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import java.util.List;

public class CtLoopModification {
    private static final Logger logger = LoggerFactory.getLogger(CtLoopModification.class);

    static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found a loop while traversing the method.");
        CtLoop loopStatement = (CtLoop) statement;

        CtBlock<?>[] returnBlocks = new CtBlock[2];
        returnBlocks[0] = new CtBlockImpl<>();
        returnBlocks[1] = new CtBlockImpl<>();
        returnBlocks[0].addStatement(loopStatement.clone());

        CtBlock<?>[] returnedStatements = ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, ((CtBlock<?>) loopStatement.getBody()), secretVariables, publicArguments);

        CtBlock<?> oldBlock = returnedStatements[0];
        oldBlock.getStatements().forEach(element -> returnBlocks[0].addStatement(element.clone()));

        CtBlock<?> newBlock = returnedStatements[1];
        newBlock.getStatements().forEach(element -> returnBlocks[1].addStatement(element.clone()));
        return returnBlocks;
    }
}