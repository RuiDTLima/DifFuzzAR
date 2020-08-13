import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import util.Setup;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class FindVulnerableMethod {
    private static final Logger logger = LoggerFactory.getLogger(FindVulnerableMethod.class);
    private static boolean afterMemClear = false;

    public static Optional<VulnerableMethodUses> processDriver(String path) {
        Launcher launcher = Setup.setupLauncher(path, "");
        CtModel model = launcher.buildModel();

        List<CtMethod<?>> methodList = model.filterChildren(new TypeFilter<>(CtMethod.class)).list();
        List<CtTypedElement<?>> typedElementList = model.filterChildren(new TypeFilter<>(CtTypedElement.class)).list();
        List<CtVariable<?>> variables = model.getElements(new TypeFilter<>(CtVariable.class));

        if (methodList.isEmpty()) {
            logger.warn("The file should contain at least the main method, and it contains no methods.");
            return Optional.empty();
        }

        CtMethod<?> mainMethod = null;

        for (CtMethod<?> method : methodList) {
            if (method.getSimpleName().equals("main")) {
                mainMethod = method;
                break;
            }
        }

        if (mainMethod == null) {
            logger.warn("The Driver provided did not contain a main method.");
            return Optional.empty();
        }

        String safeModeVariable = null;
        boolean safeMode = false;

        List<CtField<?>> fieldList = model.filterChildren(new TypeFilter<>(CtField.class)).list();
        Optional<CtField<Boolean>> optionalSafeModeField = fieldList
                .stream()
                .filter(field -> field.isFinal() && field.getType().getSimpleName().equals("boolean"))
                .findAny()
                .map(ctField -> (CtField<Boolean>) ctField);

        if (optionalSafeModeField.isPresent()) {
            CtField<Boolean> safeModeField = optionalSafeModeField.get();
            safeModeVariable = safeModeField.getSimpleName();
            safeMode = ((CtLiteralImpl<Boolean>) safeModeField.getAssignment()).getValue();
        }

        Iterator<CtStatement> iterator = mainMethod.getBody().iterator();

        VulnerableMethodUses vulnerableMethodUseCases = discoverMethodIdentification(iterator, safeMode, safeModeVariable, typedElementList, variables);

        if (!vulnerableMethodUseCases.isValid()) {
            logger.warn("The tool could not discover the vulnerable method.");
            return Optional.empty();
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
            return Optional.empty();
        }

        logger.info(String.format("The private parameter in the vulnerable method %s is in position %d", firstVulnerableMethodName, idx));
        return Optional.of(vulnerableMethodUseCases);
    }

    /**
     * Travels trough the AST of the main method, trying to find any of the patterns that indicates the presence of the
     * vulnerable method like it's presented in <a href="https://github.com/RuiDTLima/DifFuzzAR/issues/1">GitHub issue #1</a>.
     *
     * @param iterator          An iterator of AST of the method where the vulnerable method is present.
     * @param safeMode          Indicates if in this method it is used the safe or unsafe variations of the vulnerable
     *                          methods.
     * @param safeModeVariable  The name of the variable that indicates if the safeMode is in action.
     * @param typedElementList  All the typed elements in the class, here will be the initialization of the variable
     *                          that represents the method.
     * @param variables         All the variables in the method
     * @return                  The vulnerable method.
     */
    private static VulnerableMethodUses discoverMethodIdentification(Iterator<CtStatement> iterator,
                                                                     boolean safeMode,
                                                                     String safeModeVariable,
                                                                     List<CtTypedElement<?>> typedElementList,
                                                                     List<CtVariable<?>> variables) {

        VulnerableMethodUses vulnerableMethodUses = new VulnerableMethodUses();

        while (iterator.hasNext()) {
            CtElement element = iterator.next();
            String codeLine = element.prettyprint();

            if (safeModeVariable != null && (element instanceof CtIf) && codeLine.contains(safeModeVariable)) {
                List<CtBlock<CtStatement>> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                int idx = 0;
                if (codeLine.contains("!")) {
                    if (safeMode) {
                        idx = 1;
                    }
                } else {
                    if (!safeMode) {
                        idx = 1;
                    }
                }
                CtBlock<CtStatement> ctBlock = elements.get(idx);

                for (CtStatement statement : ctBlock) {
                    if (statement.toString().contains("Mem.clear()")) {
                        afterMemClear = true;
                    } else if (afterMemClear) {
                        afterMemClear = false;
                        setVulnerableMethodUsesCase(typedElementList, vulnerableMethodUses, statement, variables);
                    }
                }
            } else if (codeLine.contains("Mem.clear()")) {
                afterMemClear = true;
                if (element instanceof CtTry) {
                    CtTry tryElement = (CtTry) element;
                    Iterator<CtStatement> statementsIterator = tryElement.getBody().getStatements().iterator();
                    return discoverMethodIdentification(statementsIterator, safeMode, safeModeVariable, typedElementList, variables);
                }
            } else if (element instanceof CtTry) {
                CtTry tryElement = (CtTry) element;
                List<CtStatement> statements = tryElement.getBody().getStatements();
                Iterator<CtStatement> iteratorStatement = statements.iterator();
                VulnerableMethodUses returnedVulnerableMethodUses = discoverMethodIdentification(iteratorStatement, safeMode, safeModeVariable, typedElementList, variables);
                vulnerableMethodUses.addFromOtherVulnerableMethodUses(returnedVulnerableMethodUses);
                if (vulnerableMethodUses.isValid()) {
                    return vulnerableMethodUses;
                }
            } else if (validate(element)) {
                afterMemClear = false;
                setVulnerableMethodUsesCase(typedElementList, vulnerableMethodUses, element, variables);
            }
        }
        return vulnerableMethodUses;
    }

    /**
     * Validates the instruction after a MemClear. That instruction can't be an assignment of a value to a variable other
     * than a method call, except for a object creation.
     *
     * @param element   The element believed to be the invocation of the vulnerable method.
     * @return          Returns true if the line represents the invocation of the vulnerable method. False otherwise.
     */
    private static boolean validate(CtElement element) {
        if (!afterMemClear) {
            return false;
        }
        if (element instanceof CtLocalVariable) {
            CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
            CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
            return defaultExpression instanceof CtInvocation;
        }

        return element instanceof CtInvocation;
    }

    /**
     * Retrieves from the CtElement all the relevant information regarding the found vulnerable method. Retrieves the
     * vulnerable method's name, class name, and the arguments list.
     * @param typedElementList      All the typed elements in the class, here will be the initialization of the variable
     *                              that represents the method.
     * @param vulnerableMethodUses  The object that contains all necessary information about the two invocations of the
     *                              vulnerable method.
     * @param element               The element that represents the invocation of the vulnerable method.
     * @param variables             All the variables in the method
     */
    private static void setVulnerableMethodUsesCase(List<CtTypedElement<?>> typedElementList,
                                                    VulnerableMethodUses vulnerableMethodUses,
                                                    CtElement element,
                                                    List<CtVariable<?>> variables) {
        CtInvocation<?> invocation;
        if (element instanceof CtLocalVariable) {
            logger.info("Vulnerable method invocation creates a variable.");
            CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
            CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
            invocation = (CtInvocation<?>) defaultExpression;
        } else if (element instanceof CtAssignment) {
            logger.info("Vulnerable method invocation saves the result in a variable.");
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) element;
            CtExpression<?> defaultExpression = assignment.getAssignment();
            invocation = (CtInvocation<?>) defaultExpression;

        } else {
            logger.info("Vulnerable method invocation does not save the result.");
            invocation = (CtInvocation<?>) element;
        }

        List<CtExpression<?>> invocationArguments = invocation.getArguments();
        logger.info("Obtained the arguments.");
        String methodName = invocation.getExecutable().getSimpleName();

        String[] fullClassName = getFullClassName(typedElementList, variables, invocation);
        String packageName = fullClassName[0];
        String className = fullClassName[1];
        String[] arguments = invocationArguments.stream().map(Object::toString).toArray(String[]::new);
        vulnerableMethodUses.setUseCase(packageName, className, methodName, arguments);
    }

    /**
     * Obtains the name of the class where the vulnerable method is defined.
     * @param typedElementList  All the typed elements in the class, here will be the initialization of the variable
     *                          that represents the method.
     * @param variables         All the variables in the method
     * @param invocation        The variable containing the invocation of the vulnerable method.
     * @return                  An array with two elements where the first element is the package name where the class
     *                          with the vulnerable method is, and the second element the name of the class
     */
    private static String[] getFullClassName(List<CtTypedElement<?>> typedElementList,
                                             List<CtVariable<?>> variables,
                                             CtInvocation<?> invocation) {

        String sourceOfMethod = invocation.getTarget().toString();
        String packageName = "";
        String className = sourceOfMethod;
        if (variables.stream().anyMatch(variable -> variable.getSimpleName().equals(sourceOfMethod))) {
            Optional<CtTypedElement<?>> objectCreation = typedElementList
                    .stream()
                    .filter(it -> !it.toString().contains("main") &&
                            it.toString().matches(".*\\b" + sourceOfMethod + "\\b.*") &&
                            it.toString().contains("="))
                    .findFirst();

            CtTypedElement<?> ctTypedElement = objectCreation.get();
            if (ctTypedElement instanceof CtAssignmentImpl) { // themis oacc unsafe
                CtAssignment<?, ?> ctAssignment = (CtAssignment<?, ?>) ctTypedElement;
                CtExpression<?> target = ((CtInvocationImpl<?>) ctAssignment.getAssignment()).getTarget();
                if (target instanceof CtTypeReference) {
                    CtTypeReference<?> targetType = (CtTypeReference<?>) target;
                    packageName = targetType.getPackage().getSimpleName();
                    className = targetType.getSimpleName();
                } else {
                    packageName = invocation.getTarget().getType().getPackage().getSimpleName().replace(".", "\\");
                    className = target.prettyprint();
                }
            } else {
                CtLocalVariableImpl<?> ctLocalVariable = (CtLocalVariableImpl<?>) ctTypedElement;
                CtTypeReference<?> assignmentType = ctLocalVariable.getAssignment().getType();
                packageName = assignmentType.getPackage().getQualifiedName().replace(".", "\\");
                className = assignmentType.getSimpleName();
            }
        } else if (variables.stream().anyMatch(variable -> variable.getType().getSimpleName().equals(sourceOfMethod))) {
            Optional<CtVariable<?>> first = variables.stream().filter(variable -> variable.getType().getSimpleName().equals(sourceOfMethod)).findFirst();
            if (first.isPresent()) {
                CtTypeReference<?> variableType = first.get().getType();
                packageName = variableType.getPackage().getSimpleName().replace(".", "\\");
                className = variableType.getSimpleName();
            }
        } else {
            CtExpression<?> elementTarget = invocation.getTarget();
            if (elementTarget instanceof CtTypeAccessImpl) {
                packageName = ((CtTypeAccessImpl<?>)elementTarget).getAccessedType().getPackage().getSimpleName().replace(".", "\\");
            } else {
                packageName = elementTarget.getType().getPackage().getSimpleName().replace(".", "\\");
            }
        }
        return new String[] {packageName, className};
    }
}