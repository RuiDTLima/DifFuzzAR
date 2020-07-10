package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtFieldReadImpl;
import java.util.List;

public class CtFieldReadModification {
    private static final Logger logger = LoggerFactory.getLogger(CtFieldReadModification.class);

    static CtExpression<?> modifyFieldRead(Factory factory, CtExpression<?> assignment, List<String> dependableVariables) {
        logger.info("Modifying a field read.");

        CtFieldReadImpl<?> fieldRead = (CtFieldReadImpl<?>) assignment;
        CtFieldReference<?> variable = fieldRead.getVariable();
        String variableName = fieldRead.getTarget().toString();
        CtTypeReference<?> declaringType = variable.getDeclaringType();
        if (dependableVariables.contains(variableName)) {
            if (declaringType.isPrimitive()) {
                if (declaringType.getSimpleName().equals("String")) {
                    assignment = factory.createLiteral("");
                } else if (declaringType.getSimpleName().equals("boolean")) {
                    assignment = factory.createLiteral(false);
                } else
                    assignment = factory.createLiteral(0);
            }
        }
        return assignment;
    }
}