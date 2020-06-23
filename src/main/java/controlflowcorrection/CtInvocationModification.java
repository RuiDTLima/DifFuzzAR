package controlflowcorrection;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import java.util.List;

public class CtInvocationModification {
    static CtStatement modifyInvocation(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        CtInvocation<?> invocation = (CtInvocation<?>) element;
        List<CtExpression<?>> invocationArguments = invocation.getArguments();
        CtExpression<?> target = invocation.getTarget();

        return null;
    }
}
