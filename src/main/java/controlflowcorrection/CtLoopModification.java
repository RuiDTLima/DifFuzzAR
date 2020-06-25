package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import java.util.List;

public class CtLoopModification {
    private static final Logger logger = LoggerFactory.getLogger(CtLoopModification.class);

    public static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found a loop while traversing the method.");
        CtLoop loopStatement = (CtLoop) statement;
        ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, ((CtBlock<?>) loopStatement.getBody()), secretVariables, publicArguments);
    }
}