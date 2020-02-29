import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtLiteralImpl;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class FindVulnerableMethod {
    private static Logger logger = LoggerFactory.getLogger(FindVulnerableMethod.class);
    private static boolean afterMemClear = false;

    public static VulnerableMethodUses processDriver(String path) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(path);
        launcher.getEnvironment().setCommentEnabled(true); // Para que os comentários no código da Driver sejam ignorados
        launcher.getEnvironment().setAutoImports(false);
        CtModel model = launcher.buildModel();
        List<CtMethod> methodList = model.filterChildren(new TypeFilter<>(CtMethod.class)).list();
        List<CtVariable> variableList = model.filterChildren(new TypeFilter<>(CtVariable.class)).list();
        //List<CtAssignment> assignmentList = model.filterChildren(new TypeFilter<>(CtAssignment.class)).list();
        //List<CtLocalVariable> localVariableList = model.filterChildren(new TypeFilter<>(CtLocalVariable.class)).list();
        //List<CtExpression> expressionList = model.filterChildren(new TypeFilter<>(Ct.class)).list();

        if (methodList.size() == 0) {
            logger.warn("The file should contain at least the main method, and it contains no methods.");
            return null;
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
            return null;
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

        VulnerableMethodUses vulnerableMethodUseCases = discoverMethod(iterator, safeMode, safeModeVariable, variableList);

        if (!vulnerableMethodUseCases.isValid()) {
            logger.warn("The tool could not discover the vulnerable method.");
            return null;
        }

        // First Vulnerable method use case
        String firstVulnerableMethodName = vulnerableMethodUseCases.getFirstUseCaseMethodName();
        String[] firstVulnerableMethodArguments = vulnerableMethodUseCases.getFirstUseCaseArgumentsNames();

        // Second Vulnerable method use case
        String[] secondVulnerableMethodArguments = vulnerableMethodUseCases.getSecondUseCaseArgumentsNames();

        int idx = 0;
        for (; idx < firstVulnerableMethodArguments.length; idx++) {
            if (!firstVulnerableMethodArguments[idx].equals(secondVulnerableMethodArguments[idx]))
                break;
        }

        if (idx == firstVulnerableMethodArguments.length) {
            logger.warn(String.format("The vulnerable method %s is used in the Driver always with the same parameters.", firstVulnerableMethodName));
            return null;
        }

        logger.info(String.format("The private parameter in the vulnerable method %s is in position %d", firstVulnerableMethodName, idx));
        return  vulnerableMethodUseCases;
    }

    /**
     * Travels trough the AST of the main method, trying to find any of the patterns that indicates the presence of the
     * vulnerable method like it's presented in <a href="https://github.com/RuiDTLima/DifFuzzAR/issues/1">GitHub issue #1</a>.
     * TODO add the comments of examples where that pattern can be seen.
     *
     * @param iterator         An iterator of AST of the method where the vulnerable method is present.
     * @param safeMode         Indicates if in this method it is used the safe or unsafe variations of the vulnerable methods.
     * @param safeModeVariable The name of the variable that indicates if the safeMode is in action.
     * @param variableList
     * @return The vulnerable method.
     */
    private static VulnerableMethodUses discoverMethod(Iterator<CtElement> iterator, boolean safeMode, String safeModeVariable, List<CtVariable> variableList) {
        VulnerableMethodUses vulnerableMethodUses = new VulnerableMethodUses();

        while (iterator.hasNext()) {
            CtElement element = iterator.next();
            String codeLine = element.prettyprint();

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
                        String invocation = vulnerableMethodLine.substring(vulnerableMethodLine.indexOf("= ") + 1, vulnerableMethodLine.indexOf("("))
                                .replace(" ", "");

                        String[] arguments = vulnerableMethodLine.substring(vulnerableMethodLine.indexOf("(") + 1, vulnerableMethodLine.indexOf(")"))
                                .split(",");

                        String[] invocationParts = invocation.split("\\.");
                        String sourceOfMethod = invocationParts[0];
                        String methodName = invocationParts[1];
                        String className = "";
                        if (!Character.isUpperCase(sourceOfMethod.codePointAt(0))) {
                            Optional<CtVariable> objectCreation = variableList.stream().filter(it -> it.getSimpleName().equals(sourceOfMethod)).findAny();
                            CtVariable ctVariable = objectCreation.get();
                            CtExpression defaultExpression = ctVariable.getDefaultExpression();
                            className = defaultExpression.getType().getSimpleName();

                        } else
                            className = sourceOfMethod;
                        vulnerableMethodUses.setUseCase(className, methodName, arguments);
                    }
                }
            } else if (codeLine.contains("Mem.clear()")) {
                afterMemClear = true;
                if (codeLine.contains("try")) {
                    List<CtBlock> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock ctBlock = elements.get(0);  // the code inside try block
                    Iterator ctBlockIterator = ctBlock.iterator();

                    return discoverMethod(ctBlockIterator, safeMode, safeModeVariable, variableList);
                }
            } else if (validAfterMemClear(codeLine)) {
                if (codeLine.contains("try")) { // Example in themis_pac4j_safe
                    List<CtBlock> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock ctBlock = elements.get(0);  // the code inside try block
                    Iterator ctBlockIterator = ctBlock.iterator();

                    VulnerableMethodUses tempVulnerableMethodUses = discoverMethod(ctBlockIterator, safeMode, safeModeVariable, variableList);
                    vulnerableMethodUses.addFromOtherVulnerableMethodUses(tempVulnerableMethodUses);
                    if (vulnerableMethodUses.isValid())
                        return vulnerableMethodUses;
                } else {
                    afterMemClear = false;
                    logger.info(String.format("The line of code %s appears after the Mem.clear.", codeLine));
                    String pretyElement = element.prettyprint();    // To remove the full name of the case in use, so that it contains only the class and method names.
                    String invocation = pretyElement.substring(pretyElement.indexOf("= ") + 1, pretyElement.indexOf("("))
                            .replace(" ", "");

                    String[] arguments = pretyElement.substring(pretyElement.indexOf("(") + 1, pretyElement.indexOf(")"))
                            .split(",");

                    String[] invocationParts = invocation.split("\\.");
                    String sourceOfMethod = invocationParts[0];
                    String methodName = invocationParts[1];
                    String className = "";
                    if (!Character.isUpperCase(sourceOfMethod.codePointAt(0))) {
                        Optional<CtVariable> objectCreation = variableList.stream().filter(it -> it.getSimpleName().equals(sourceOfMethod)).findAny();
                        CtVariable ctVariable = objectCreation.get();
                        CtExpression defaultExpression = ctVariable.getDefaultExpression();
                        className = defaultExpression.getType().getSimpleName();

                    } else
                        className = sourceOfMethod;

                    vulnerableMethodUses.setUseCase(className, methodName, arguments);
                }
            }
        }
        return vulnerableMethodUses;
    }

    /**
     * Validates the instruction after a MemClear. That instruction can't be an assignment of a value to a variable other
     * than a method call, except for a object creation.
     *
     * @param line
     * @return
     */
    private static boolean validAfterMemClear(String line) {
        return afterMemClear && !line.equals("") && !(
                line.contains("=") && (!line.contains("(") ||
                        (line.contains("(") && line.contains("new"))));
    }
}
