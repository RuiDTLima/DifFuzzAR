import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtLiteralImpl;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class DifFuzzAR {
    private static Logger logger = LoggerFactory.getLogger(DifFuzzAR.class);
    private static boolean afterMemClear = false;

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.warn("There needs to be at least one argument for the application.");
            return;
        }
        String driverPath = args[0];
        logger.info(String.format("The passed path was %s.", driverPath));

        Launcher launcher = new Launcher();
        launcher.addInputResource(driverPath);
        launcher.getEnvironment().setCommentEnabled(false);
        CtModel model = launcher.buildModel();
        List<CtMethod> methodList = model.filterChildren(new TypeFilter<>(CtMethod.class)).list();

        if (methodList.size() == 0) {
            logger.warn("The file should contain at least the main method, and it contains no methods.");
            return;
        }

        CtMethod mainMethod = null;

        for (CtMethod method : methodList) {
            if (method.getSimpleName().equals("main")) {
                mainMethod = method;
                break;
            }
        }

        if (mainMethod == null) {
            logger.warn("The Driver provided did not contain a main method.");
            return;
        }

        String safeModeVariable = null;
        boolean safeMode = false;

        List<CtField> fieldList = model.filterChildren(new TypeFilter<>(CtField.class)).list();
        Optional<CtField> optionalSafeModeField = fieldList.stream()
                .filter(field -> field.isFinal() && field.getType().getSimpleName().equals("boolean"))
                .findAny();

        if (optionalSafeModeField.isPresent()) {
            CtField safeModeField = optionalSafeModeField.get();
            safeModeVariable = safeModeField.getSimpleName();
            safeMode = (boolean) ((CtLiteralImpl) safeModeField.getAssignment()).getValue();
        }

        Iterator<CtElement> iterator = mainMethod.getBody().iterator();

        String vulnerableMethod = discoverMethod(iterator, safeMode, safeModeVariable);

        if (vulnerableMethod == null) {
            logger.warn("The tool could not discover the vulnerable method.");
            return;
        }
        String methodName = vulnerableMethod.subSequence(vulnerableMethod.indexOf("= ") + 1, vulnerableMethod.indexOf("(")).toString();
        logger.info(String.format("The vulnerable method is %s", methodName));
    }

    static String discoverMethod(Iterator<CtElement> iterator, boolean safeMode, String safeModeVariable) {
        while (iterator.hasNext()) {
            CtElement element = iterator.next();
            String codeLine = element.toString();

            if (safeModeVariable != null && codeLine.contains("if") && codeLine.contains(safeModeVariable) && !(element instanceof CtTry)) {
                List<CtBlock> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                int idx = 0;
                if (codeLine.contains("if(!")) {
                    if (safeMode)
                        idx = 1;
                    else idx = 0;
                } else {
                    if (safeMode)
                        idx = 0;
                    else idx = 1;
                }
                CtBlock ctBlock = elements.get(idx);


                Iterator ctBlockIterator = ctBlock.iterator();
                while (ctBlockIterator.hasNext()) {
                    CtStatement statement = (CtStatement) ctBlockIterator.next();
                    if (statement.toString().contains("Mem.clear()"))
                        afterMemClear = true;
                    else if (afterMemClear) {
                        afterMemClear = false;
                        String vulnerableMethodLine = statement.prettyprint();
                        logger.info(String.format("The line of code %s appears after the Mem.clear.", vulnerableMethodLine));
                        return vulnerableMethodLine;
                    }
                }
            } else if (codeLine.contains("Mem.clear()")) {
                afterMemClear = true;
            } else if (validAfterMemClear(codeLine)) {
                if (codeLine.contains("try")) { // Example in themis_pac4j_safe
                    List<CtBlock> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock ctBlock = elements.get(0);// the code inside try block
                    Iterator ctBlockIterator = ctBlock.iterator();

                    return discoverMethod(ctBlockIterator, safeMode, safeModeVariable);
                }
                afterMemClear = false;
                logger.info(String.format("The line of code %s appears after the Mem.clear.", codeLine));
                String pretyElement = element.prettyprint();
                return pretyElement;
            }
        }
        return null;
    }

    private static boolean validAfterMemClear(String line) {
        return afterMemClear && !line.equals("") && !(
                line.contains("=") && (!line.contains("(") ||
                        (line.contains("(") && line.contains("new"))));
    }
}
