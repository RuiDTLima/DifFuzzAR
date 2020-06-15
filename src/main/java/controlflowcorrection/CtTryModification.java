package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import java.util.List;

public class CtTryModification {
    private static final Logger logger = LoggerFactory.getLogger(CtTryModification.class);

    public static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found a 'try' statement while traversing the method.");
        CtTry tryStatement = (CtTry) statement;
        CtBlock<?> tryStatementBody = tryStatement.getBody();
        ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, tryStatementBody, secretVariables, publicArguments);
    }
}
