import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class FindVulnerableMethod {
    private static final Logger logger = LoggerFactory.getLogger(FindVulnerableMethod.class);
    private static boolean afterMemClear = false;

    public static VulnerableMethodUses processDriver(String path) {
        Launcher launcher = Setup.setupLauncher(path, "");
        CtModel model = launcher.buildModel();

        List<CtMethod<?>> methodList = model.filterChildren(new TypeFilter<>(CtMethod.class)).list();
        List<CtTypedElement<?>> typedElementList = model.filterChildren(new TypeFilter<>(CtTypedElement.class)).list();
        List<CtVariable<?>> variables = model.getElements(new TypeFilter<>(CtVariable.class));

        if (methodList.isEmpty()) {
            logger.warn("The file should contain at least the main method, and it contains no methods.");
            return null;
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
            return null;
        }

        String safeModeVariable = null;
        boolean safeMode = false;

        List<CtField<?>> fieldList = model.filterChildren(new TypeFilter<>(CtField.class)).list();
        Optional<CtField<Boolean>> optionalSafeModeField = fieldList.stream()
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
            return null;
        }

        // TODO
        // First Vulnerable method use case
        /*String firstVulnerableMethodName = vulnerableMethodUseCases.getFirstUseCaseMethodName();
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

        logger.info(String.format("The private parameter in the vulnerable method %s is in position %d", firstVulnerableMethodName, idx));*/
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
     * @param typedElementList All the typed elements in the class, here will be the initialization of the variable
     *                         that represents the method.
     * @param variables All the variables in the method
     * @return The vulnerable method.
     */
    private static VulnerableMethodUses discoverMethodIdentification(Iterator<CtStatement> iterator, boolean safeMode,
                                                                     String safeModeVariable,
                                                                     List<CtTypedElement<?>> typedElementList,
                                                                     List<CtVariable<?>> variables) {

        VulnerableMethodUses vulnerableMethodUses = new VulnerableMethodUses();

        while (iterator.hasNext()) {
            CtElement element = iterator.next();
            String codeLine = element.prettyprint();

            if (safeModeVariable != null && codeLine.contains("if") && codeLine.contains(safeModeVariable) && !(element instanceof CtTry)) {
                List<CtBlock<CtStatement>> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                int idx = 0;
                if (codeLine.contains("if(!")) {
                    if (safeMode)
                        idx = 1;
                } else {
                    if (!safeMode)
                        idx = 1;
                }
                CtBlock<CtStatement> ctBlock = elements.get(idx);

                for (CtStatement statement : ctBlock) {
                    if (statement.toString().contains("Mem.clear()"))
                        afterMemClear = true;
                    else if (afterMemClear) {
                        afterMemClear = false;

                        setVulnerableMethodUsesCase(typedElementList, vulnerableMethodUses, statement, variables);
                    }
                }
            } else if (codeLine.contains("Mem.clear()")) {
                afterMemClear = true;
                if (codeLine.contains("try")) {
                    List<CtBlock<CtTry>> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock<CtTry> ctBlock = elements.get(0);  // the code inside try block
                    Iterator<CtStatement> ctBlockIterator = ctBlock.iterator();

                    return discoverMethodIdentification(ctBlockIterator, safeMode, safeModeVariable, typedElementList, variables);
                }
            } else if (validAfterMemClear(codeLine)) {
                if (codeLine.contains("try")) { // Example in themis_pac4j_safe
                    List<CtBlock<CtTry>> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock<CtTry> ctBlock = elements.get(0);  // the code inside try block
                    Iterator<CtStatement> ctBlockIterator = ctBlock.iterator();

                    VulnerableMethodUses tempVulnerableMethodUses = discoverMethodIdentification(ctBlockIterator, safeMode, safeModeVariable, typedElementList, variables);
                    vulnerableMethodUses.addFromOtherVulnerableMethodUses(tempVulnerableMethodUses);
                    if (vulnerableMethodUses.isValid())
                        return vulnerableMethodUses;
                } else {
                    afterMemClear = false;
                    setVulnerableMethodUsesCase(typedElementList, vulnerableMethodUses, element, variables);
                }
            }
        }
        return vulnerableMethodUses;
    }

    /**
     * Validates the instruction after a MemClear. That instruction can't be an assignment of a value to a variable other
     * than a method call, except for a object creation.
     *
     * @param line  The line believed to contain the invocation of the vulnerable method
     * @return  true if the line represents the invocation of the vulnerable method, false otherwise.
     */
    private static boolean validAfterMemClear(String line) {
        return afterMemClear && !line.equals("") && !(
                line.contains("=") && (!line.contains("(") ||
                        (line.contains("(") && line.contains("new"))));
    }

    /**
     * Retrieves from the CtElement all the relevant information regarding the found vulnerable method. Retrieves the
     * vulnerable method's name, class name, and the arguments list.
     * @param typedElementList  All the typed elements in the class, here will be the initialization of the variable
     *                          that represents the method.
     * @param vulnerableMethodUses  The object that contains all necessary information about the two invocations of the
     *                              vulnerable method.
     * @param element   The element that represents the invocation of the vulnerable method.
     * @param variables All the variables in the method
     */
    private static void setVulnerableMethodUsesCase(List<CtTypedElement<?>> typedElementList, VulnerableMethodUses vulnerableMethodUses,
                                                    CtElement element,
                                                    List<CtVariable<?>> variables) {

        String vulnerableMethodLine = element.prettyprint();    // To remove the full name of the case in use, so that it contains only the class and method names.

        logger.info("The line of code {} appears after the Mem.clear.", vulnerableMethodLine);

        String invocation = vulnerableMethodLine.substring(vulnerableMethodLine.indexOf("= ") + 1, vulnerableMethodLine.indexOf("("))
                .replace(" ", "");

        String[] arguments = vulnerableMethodLine.substring(vulnerableMethodLine.indexOf("(") + 1, vulnerableMethodLine.indexOf(")"))
                .split(",");

        String[] invocationParts = invocation.split("\\.");
        String sourceOfMethod = invocationParts[0];
        String methodName = invocationParts[1];
        String[] className = getClassName(typedElementList, sourceOfMethod, variables);
        String packageName = className[0];
        if (packageName.equals("")) {
            if (element instanceof CtInvocationImpl) {
                CtExpression<?> elementTarget = ((CtInvocationImpl<?>) element).getTarget();
                if (elementTarget instanceof CtTypeAccessImpl) {
                    packageName = ((CtTypeAccessImpl<?>)elementTarget).getAccessedType().getPackage().getSimpleName().replace(".", "\\");
                } else {
                    packageName = elementTarget.getType().getPackage().getSimpleName().replace(".", "\\");
                }

            } else if (element instanceof CtLocalVariableImpl) {
                CtExpression<?> defaultExpression = ((CtLocalVariableImpl<?>) element).getDefaultExpression();
                if (defaultExpression instanceof  CtInvocationImpl) {
                    CtPackageReference packageReference = ((CtInvocationImpl<?>) defaultExpression).getTarget().getType().getPackage();
                    if (packageReference != null && packageReference.getParent().toString().equals(className[1]))
                        packageName = packageReference.getSimpleName().replace(".", "\\");
                }
            }
        }
        vulnerableMethodUses.setUseCase(packageName, className[1], methodName, arguments);
    }

    /**
     * Obtains the name of the class where the vulnerable method is defined.
     * @param typedElementList  All the typed elements in the class, here will be the initialization of the variable
     *                          that represents the method.
     * @param sourceOfMethod    The name of the element used to invoke the method, if it's an instance that element will
     *                          be a variable
     * @param variables All the variables in the method
     * @return An array with two elements where the first element is the package name where the class with the vulnerable
     * method is, and the second element the name of the class
     */
    private static String[] getClassName(List<CtTypedElement<?>> typedElementList, String sourceOfMethod, List<CtVariable<?>> variables) {
        if (variables.stream().anyMatch(variable -> variable.getSimpleName().equals(sourceOfMethod))) {
            Optional<CtTypedElement<?>> objectCreation = typedElementList.stream().
                    filter(it -> !it.toString().contains("main") &&
                            it.toString().matches(".*\\b" + sourceOfMethod + "\\b.*") &&
                            it.toString().contains("="))
                    .findAny();

            CtTypedElement<?> ctTypedElement = objectCreation.get();
            if (ctTypedElement instanceof CtAssignmentImpl) { // themis oacc unsafe
                CtAssignment<?, ?> ctAssignment = (CtAssignment<?, ?>) ctTypedElement;
                return new String[] {"", ((CtInvocationImpl<?>) ctAssignment.getAssignment()).getTarget().prettyprint()};
            } else {
                CtLocalVariableImpl<?> ctLocalVariable = (CtLocalVariableImpl<?>) ctTypedElement;
                CtTypeReference<?> assignmentType = ctLocalVariable.getAssignment().getType();
                return new String[] {assignmentType.getPackage().getQualifiedName().replace(".", "\\"), assignmentType.getSimpleName()};
            }
        }
        return new String[] {"", sourceOfMethod};
    }
}