package controlflowcorrection;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import java.util.List;

public class CtLoopModification {

    public static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        CtLoop loopStatement = (CtLoop) statement;
        ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, ((CtBlock<?>) loopStatement.getBody()), secretVariables, publicArguments);
    }
}
