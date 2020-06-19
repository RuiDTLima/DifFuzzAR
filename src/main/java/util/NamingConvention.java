package util;

public class NamingConvention {
    private static final String CLASS_NAME_ADDITION = "$Modification";
    private static final String NAME_FOR_VARIABLE = "$";
    private static int counter = 0;

    public static String getClassNameAddition() {
        return CLASS_NAME_ADDITION;
    }

    public static String getNameForVariable() {
        return NAME_FOR_VARIABLE + counter;
    }

    public static int increaseCounter() {
        return ++counter;
    }

    public static int getCounter() {
        return counter;
    }

    public static String produceNewVariableName() {
        counter++;
        return NAME_FOR_VARIABLE + counter;
    }

    // TODO remove
    public static void resetCounter() {
        counter = 0;
    }
}
