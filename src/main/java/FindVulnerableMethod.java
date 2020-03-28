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
    private static Logger logger = LoggerFactory.getLogger(FindVulnerableMethod.class);
    private static boolean afterMemClear = false;

    public static VulnerableMethodUses processDriver(String path) {
        Launcher launcher = Setup.setupLauncher(path, "");
        CtModel model = launcher.buildModel();

        List<CtMethod> methodList = model.filterChildren(new TypeFilter<>(CtMethod.class)).list();
        List<CtTypedElement> typedElementList = model.filterChildren(new TypeFilter<>(CtTypedElement.class)).list();

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

        VulnerableMethodUses vulnerableMethodUseCases = discoverMethodIdentification(iterator, safeMode, safeModeVariable, typedElementList);

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
     * @param typedElementList
     * @return The vulnerable method.
     */
    private static VulnerableMethodUses discoverMethodIdentification(Iterator<CtElement> iterator, boolean safeMode, String safeModeVariable, List<CtTypedElement> typedElementList) {
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

                        setVulnerableMethodUsesCase(typedElementList, vulnerableMethodUses, statement, statement.prettyprint());
                    }
                }
            } else if (codeLine.contains("Mem.clear()")) {
                afterMemClear = true;
                if (codeLine.contains("try")) {
                    List<CtBlock> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock ctBlock = elements.get(0);  // the code inside try block
                    Iterator ctBlockIterator = ctBlock.iterator();

                    return discoverMethodIdentification(ctBlockIterator, safeMode, safeModeVariable, typedElementList);
                }
            } else if (validAfterMemClear(codeLine)) {
                if (codeLine.contains("try")) { // Example in themis_pac4j_safe
                    List<CtBlock> elements = element.getElements(new TypeFilter<>(CtBlock.class));
                    CtBlock ctBlock = elements.get(0);  // the code inside try block
                    Iterator ctBlockIterator = ctBlock.iterator();

                    VulnerableMethodUses tempVulnerableMethodUses = discoverMethodIdentification(ctBlockIterator, safeMode, safeModeVariable, typedElementList);
                    vulnerableMethodUses.addFromOtherVulnerableMethodUses(tempVulnerableMethodUses);
                    if (vulnerableMethodUses.isValid())
                        return vulnerableMethodUses;
                } else {
                    afterMemClear = false;
                    setVulnerableMethodUsesCase(typedElementList, vulnerableMethodUses, element, codeLine);
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

    /**
     * Retrieves from the CtElement all the relevant information regarding the found vulnerable method. Retrieves the
     * vulnerable method's name, class name, and the arguments list.
     * @param typedElementList
     * @param vulnerableMethodUses
     * @param element
     * @param codeLine
     */
    private static void setVulnerableMethodUsesCase(List<CtTypedElement> typedElementList, VulnerableMethodUses vulnerableMethodUses, CtElement element, String codeLine) {
        logger.info("The line of code {} appears after the Mem.clear.", codeLine);
        String vulnerableMethodLine = element.prettyprint();    // To remove the full name of the case in use, so that it contains only the class and method names.
        String invocation = vulnerableMethodLine.substring(vulnerableMethodLine.indexOf("= ") + 1, vulnerableMethodLine.indexOf("("))
                .replace(" ", "");

        String[] arguments = vulnerableMethodLine.substring(vulnerableMethodLine.indexOf("(") + 1, vulnerableMethodLine.indexOf(")"))
                .split(",");

        String[] invocationParts = invocation.split("\\.");
        String sourceOfMethod = invocationParts[0];
        String methodName = invocationParts[1];
        String[] className = getClassName(typedElementList, sourceOfMethod);
        String packageName = className[0];
        if (packageName.equals("")) {
            if (element instanceof CtInvocationImpl) {
                CtExpression elementTarget = ((CtInvocationImpl) element).getTarget();
                if (elementTarget instanceof CtTypeAccessImpl) {
                    packageName = ((CtTypeAccessImpl)elementTarget).getAccessedType().getPackage().getSimpleName().replace(".", "\\");
                } else {
                    packageName = elementTarget.getType().getPackage().getSimpleName().replace(".", "\\");
                }

            } else if (element instanceof CtLocalVariableImpl) {
                CtExpression defaultExpression = ((CtLocalVariableImpl) element).getDefaultExpression();
                if (defaultExpression instanceof  CtInvocationImpl) {
                    CtPackageReference packageReference = ((CtInvocationImpl) defaultExpression).getTarget().getType().getPackage();
                    if (packageReference != null)
                        packageName = packageReference.getSimpleName().replace(".", "\\");
                }
            }
        }
        vulnerableMethodUses.setUseCase(packageName, className[1], methodName, arguments);
    }

    /**
     * Obtains the name of the class where the vulnerable method is defined.
     * @param typedElementList
     * @param sourceOfMethod
     * @return
     */
    private static String[] getClassName(List<CtTypedElement> typedElementList, String sourceOfMethod) {
        if (Character.isLowerCase(sourceOfMethod.codePointAt(0))) {
            Optional<CtTypedElement> objectCreation = typedElementList.stream().
                    filter(it -> !it.toString().contains("main") &&
                            it.toString().matches(".*\\b" + sourceOfMethod + "\\b.*") &&
                            it.toString().contains("="))
                    .findAny();

            if (objectCreation.get() instanceof CtAssignmentImpl) { // themis oacc unsafe
                CtAssignment ctLocalVariable = (CtAssignment) objectCreation.get();
                return new String[] {"", ((CtInvocationImpl) ctLocalVariable.getAssignment()).getTarget().prettyprint()};
            } else {
                CtLocalVariableImpl ctLocalVariable = (CtLocalVariableImpl) objectCreation.get();
                CtTypeReference assignmentType = ctLocalVariable.getAssignment().getType();
                return new String[] {assignmentType.getPackage().getQualifiedName().replace(".", "\\"), assignmentType.getSimpleName()};
            }
        }
        return new String[] {"", sourceOfMethod};
    }
}
