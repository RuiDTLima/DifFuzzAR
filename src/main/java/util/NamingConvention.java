package util;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;

public class NamingConvention {
    private static final String CLASS_NAME_ADDITION = "$Modification";
    private static final String NAME_FOR_VARIABLE = "$";
    private static CtLocalVariable<?> localVariable = null;
    private static int counter = 0;

    public static String getClassNameAddition() {
        return CLASS_NAME_ADDITION;
    }

    public static String getNameForVariable() {
        return NAME_FOR_VARIABLE + counter;
    }

    public static CtLocalVariable<?> getLocalVariable() {
        return localVariable;
    }

    public static String produceNewVariable() {
        counter++;
        return NAME_FOR_VARIABLE + counter;
    }

    public static CtLocalVariable<?> produceNewVariable(Factory factory, CtTypeReference returnType, CtExpression<?> defaultExpression) {
        String variableName = produceNewVariable();
        localVariable = factory.createLocalVariable(returnType, variableName, defaultExpression);
        return localVariable;
    }

    public static void resetCounter() {
        counter = 0;
    }
}