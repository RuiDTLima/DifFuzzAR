import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.ReturnOrThrowFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import spoon.support.reflect.declaration.CtMethodImpl;
import java.util.*;
import java.util.stream.Stream;

public class ModificationOfCode {
    private static final Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);
    private static final String CLASS_NAME_ADDITION = "$Modification";
    private static final String VARIABLE_TO_ADD_NAME = "$1";
    private static CtElement cycleElement = null;

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String packageName = vulnerableMethodUsesCases.getFirstUseCasePackageName();
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info("The vulnerable method {} belongs to the class {}.", methodName, className);

        String pathToCorrectedClass = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\"; // The new class will add the necessary package
        String pathToVulnerableMethod = pathToCorrectedClass + packageName + "\\" + className + ".java";

        logger.info("The path to the vulnerable class is {}.", pathToVulnerableMethod);

        Launcher launcher = Setup.setupLauncher(pathToVulnerableMethod, pathToCorrectedClass);

        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();

        List<CtClass<?>> classList = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
        CtMethod<?> vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName)).first();

        CtClass<?> vulnerableClass;
        //if (classList.size() == 2) {
            vulnerableClass = classList.get(0);

            if (vulnerableMethod == null) {
                CtTypeReference<?> superClass = vulnerableClass.getSuperclass();
                String superclass = superClass.getQualifiedName().replace(".", "\\");
                className = superClass.getSimpleName();
                pathToVulnerableMethod = pathToCorrectedClass + superclass + ".java";
                launcher.addInputResource(pathToVulnerableMethod);
                launcher = Setup.setupLauncher(pathToVulnerableMethod, pathToCorrectedClass);
                model = launcher.buildModel();
                factory = launcher.getFactory();
                vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName)).first();
                classList = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
                if (classList.size() == 1)
                    vulnerableClass = classList.get(0);
            }
            vulnerableClass.setSimpleName(className + CLASS_NAME_ADDITION);

            modifyCode(factory, vulnerableMethod, model);
            launcher.prettyprint();
       /* } else
            logger.warn("The inspected file contains {} classes, can't detect the vulnerable one.", classList.size());*/
    }

    /**
     * Where the process of code modification takes place. Retrieves all the return statements of the vulnerableMethod and
     * modifies them one by one in order of appearance.
     * @param factory   The factory used to create code snippets to add.
     * @param vulnerableMethod  The method with the vulnerability that will be modified.
     * @param model The model of the code. Represents the file with the code to be modified.
     */
    private static void modifyCode(Factory factory, CtMethod<?> vulnerableMethod, CtModel model) {
        List<CtStatement> statementList = vulnerableMethod.getBody().getStatements();

        if (statementList.size() == 1 && !(statementList.get(0) instanceof CtIfImpl)) {
            String methodInvocation = ((CtReturnImpl<?>) statementList.get(0)).getReturnedExpression().prettyprint();
            String calledMethodName = methodInvocation.substring(0, methodInvocation.indexOf("("));
            vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(calledMethodName)).first();
            modifyCode(factory, vulnerableMethod, model);
            return;
        }

        CtMethod<?> modifiedMethod = vulnerableMethod.copyMethod();
        Refactoring.changeMethodName(modifiedMethod, vulnerableMethod.getSimpleName() + CLASS_NAME_ADDITION);

        List<CtCFlowBreak> returnList = modifiedMethod.getElements(new ReturnOrThrowFilter());
        returnList.removeIf(returnOrThrow -> !(returnOrThrow instanceof CtReturnImpl));
        List<CtLocalVariable<?>> variableList = modifiedMethod.getElements(new TypeFilter<>(CtLocalVariable.class));
        CtBlock<?> modifiedMethodBody = modifiedMethod.getBody();

        if (returnList.size() > 1) {
            logger.info("The method suffers from early-exit timing side-channel vulnerability since it " +
                    "has {} exit points.", returnList.size());
        }

        int lastIndex = returnList.size() - 1;
        CtReturnImpl<?> finalReturnElement = (CtReturnImpl<?>) returnList.get(lastIndex);
        CtExpression<?> returnedExpression = finalReturnElement.getReturnedExpression();
        String finalReturn = returnedExpression.toString();

        Optional<CtLocalVariable<?>> optionalReturnAssignment = variableList.stream()
                .filter(it -> it.getReference().toString().matches(".*\\b" + finalReturn + "\\b.*"))
                .findFirst();

        String returnElement = findValueToReturn(variableList, returnedExpression, finalReturn);

        String variableName = createReturnVariable(factory, modifiedMethod.getType(), variableList, modifiedMethodBody,
                optionalReturnAssignment, returnElement);

        CtElement parentOfCycleReturn = null;
        boolean afterCycleReturn = false;
        Iterator<CtCFlowBreak> returnsIterator = returnList.iterator();

        while (returnsIterator.hasNext()) {
            CtReturnImpl<?> returnImpl = (CtReturnImpl<?>) returnsIterator.next();
            CtElement parentElement = returnImpl.getParent().getParent();
            if (afterCycleReturn && !(parentElement instanceof CtMethodImpl)) {
                modifyCodeAfterReturnInCycle(factory, variableName, parentOfCycleReturn, returnImpl, parentElement);
            }
            if (!(parentElement instanceof CtMethodImpl) && isInsideCycle(returnImpl)) {    // If the return is inside a cycle
                afterCycleReturn = true;
                parentOfCycleReturn = parentElement;
            }
            if (parentElement instanceof CtIfImpl) {
                modifyIfCondition(factory, (CtIfImpl) parentElement);
            }
            if (!returnsIterator.hasNext()) {
                modifyLastReturn(factory, modifiedMethodBody, returnElement, variableName, returnImpl);
                break;
            }
            String returnedValue = returnImpl.getReturnedExpression().toString();
            CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " = " + returnedValue);
            returnImpl.replace(codeSnippetStatement);
        }
    }

    /**
     * Determines if the final return, returns the result of a method invocation. If so, it is important to ensure that
     * all arguments passed to the method are available at the start of the method execution. Otherwise the created variable
     * will be declared without a starting value.
     * @param variableList the list of variables in the method.
     * @param returnedExpression the expression returned in the last return of the method.
     * @param finalReturn   The value returned in the last return of the method.
     * @return The value to initiate the new variable with.
     */
    private static String findValueToReturn(List<CtLocalVariable<?>> variableList, CtExpression<?> returnedExpression, String finalReturn) {
        String returnElement;
        if (returnedExpression instanceof CtInvocation) {
            Stream<?> lastReturnInvocationArguments = ((CtInvocationImpl<?>) returnedExpression)
                    .getArguments()
                    .stream()
                    .flatMap(elem -> Arrays.stream(elem.toString().split("\\.")).limit(1));

            if (lastReturnInvocationArguments.noneMatch(argument -> variableList.stream().anyMatch(variable -> variable.getReference().getDeclaration().getSimpleName().equals(argument)))) {
                returnElement = finalReturn;
            } else
                returnElement = "";
        } else if (returnedExpression instanceof CtBinaryOperator)
            returnElement = "";
        else
            returnElement = finalReturn;
        return returnElement;
    }

    /**
     * Method where the variable used to contain the value to be returned in the end of the method is created. Or the existing
     * variable to be return is added to the beginning of the method to allow assignment anywhere in the method where a return
     * exists.
     * @param factory The factory used to create code snippets to add.
     * @param methodReturnType The return type of the method to modify.
     * @param variableList  The list of variables created inside the method
     * @param modifiedMethodBody    The body - a block of code - of the method modified. The new version of the method being modified
     * @param optionalReturnAssignment  If a value is present it means that a new variable to be returned needs to be created.
     * @param returnElement The element to return.
     * @return The variable to be returned, either an existing variable or the newly created one.
     */
    private static String createReturnVariable(Factory factory, CtTypeReference<?> methodReturnType, List<CtLocalVariable<?>> variableList,
                                               CtBlock<?> modifiedMethodBody,
                                               Optional<CtLocalVariable<?>> optionalReturnAssignment,
                                               String returnElement) {

        String variableName = "";

        if (!optionalReturnAssignment.isPresent()) {
            String instructionToAdd = VARIABLE_TO_ADD_NAME + (returnElement.equals("") ? returnElement : " = " + returnElement);
            CtCodeSnippetStatement $1  = factory.createCodeSnippetStatement(methodReturnType + instructionToAdd);
            modifiedMethodBody.addStatement(0, $1);
            variableName = VARIABLE_TO_ADD_NAME;
            logger.info("Added the instruction {}.", $1);
        } else {
            CtLocalVariable<?> returnAssignmentToRemove = optionalReturnAssignment.get();
            CtLocalVariable<?> returnAssignmentToAdd = returnAssignmentToRemove.clone();

            if (returnAssignmentToRemove.getDefaultExpression() instanceof CtNewArrayImpl) {    // In response to themis_dynatable_unsafe
                List<CtExpression<Integer>> dimensionExpressions = ((CtNewArrayImpl<?>) returnAssignmentToRemove.getDefaultExpression())
                        .getDimensionExpressions();

                for (CtLocalVariable<?> variable : variableList) {
                    if (dimensionExpressions.stream().anyMatch(arraySize -> variable.getSimpleName().equals(arraySize.toString()))) {
                        String returnName = returnAssignmentToRemove.getSimpleName();
                        CtExpression<?> assignment = returnAssignmentToRemove.getAssignment();
                        CtTypeReference<?> returnType = returnAssignmentToRemove.getType();

                        CtCodeSnippetStatement assignmentModification = factory.createCodeSnippetStatement(returnName + " = " + assignment);
                        returnAssignmentToRemove.replace(assignmentModification);
                        CtCodeSnippetStatement returnVariableDefinition = factory.createCodeSnippetStatement(returnType + " " + returnName);
                        modifiedMethodBody.addStatement(0, returnVariableDefinition);
                        variableName = returnElement;
                    }
                }
            } else {
                modifiedMethodBody.removeStatement(returnAssignmentToRemove);
                modifiedMethodBody.addStatement(0, returnAssignmentToAdd);
                variableName = returnElement;
            }
        }
        return variableName;
    }

    /**
     * The process to modify the code after a replacement of a return inside a cycle as occurred. Here it is important to
     * guarantee that the value assigned to the returned variable is not replaced, but also that if the returned variable
     * is not modified inside the cycle the correct value is returned
     * @param factory   The factory used to create code snippets to add.
     * @param variableName  The name of the variable to be returned.
     * @param parentOfCycleReturn   The instruction block to which the cycle belongs, to ensure that the return after the
     *                              cycle happens inside the same block
     * @param returnImpl   The return expression to be replaced.
     * @param parentElement The instruction block to which the current return belongs to.
     */
    private static void modifyCodeAfterReturnInCycle(Factory factory, String variableName, CtElement parentOfCycleReturn,
                                                     CtReturnImpl<?> returnImpl,
                                                     CtElement parentElement) {

        String afterCycleReturnedExpression = returnImpl.getReturnedExpression().toString();
        CtCodeSnippetStatement toReplace = factory.createCodeSnippetStatement(VARIABLE_TO_ADD_NAME + " = " + afterCycleReturnedExpression);
        ArrayList<CtElement> arrayList = new ArrayList<>();
        arrayList.add(toReplace);
        arrayList.add(cycleElement);
        cycleElement.replace(arrayList);    //  To avoid a semicolon after the cycle. As a response of test 26 of example github_authmereloaded.
        while (!(parentOfCycleReturn instanceof CtMethodImpl)) {
            if (parentElement == parentOfCycleReturn) {
                CtReturn<Object> objectCtReturn = factory.createReturn().setReturnedExpression(factory.createCodeSnippetExpression(variableName));
                returnImpl.replace(objectCtReturn);
                break;
            }
            parentOfCycleReturn = parentOfCycleReturn.getParent();
        }
    }

    /**
     * Checks if the current return instruction to be replaced occurs inside a cycle.
     * @param returnImpl    The return instruction to be replaced.
     * @return  true - if the return happens inside a cycle, false - otherwise.
     */
    private static boolean isInsideCycle(CtReturnImpl<?> returnImpl) {
        CtElement parent = returnImpl.getParent();
        while (!(parent instanceof CtMethodImpl)) {
            if (parent instanceof CtLoop) {
                cycleElement = parent;
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Modifies the condition of the if. This is important when accessing an array, to avoid IndexOutOfBoundsException.
     * Since it is likely that the condition to avoid the IndexOutOfBoundsException no longer work to avoid early-exit
     * timing side-channel.
     * @param factory   The factory used to create code snippets to add.
     * @param ifStatement   The if statement to be modified.
     */
    private static void modifyIfCondition(Factory factory, CtIfImpl ifStatement) {
        if (ifStatement.getCondition() instanceof CtBinaryOperator) {
            CtBinaryOperator<Boolean> condition = (CtBinaryOperator<Boolean>) ifStatement.getCondition();
            CtElement leftHandOperator = condition.getLeftHandOperand();
            CtElement rightHandOperator = condition.getRightHandOperand();

            if (leftHandOperator instanceof CtArrayReadImpl || rightHandOperator instanceof CtArrayReadImpl) {
                String newCondition = "";
                if (leftHandOperator instanceof CtArrayReadImpl) {
                    CtArrayReadImpl<?> arrayRead = ((CtArrayReadImpl<?>) leftHandOperator);
                    newCondition += String.format("%s < %s.length && ", arrayRead.getIndexExpression(), arrayRead.getTarget());
                }

                if (rightHandOperator instanceof CtArrayReadImpl) {
                    CtArrayReadImpl<?> arrayRead = (CtArrayReadImpl<?>) rightHandOperator;
                    newCondition += String.format("%s < %s.length && ", arrayRead.getIndexExpression(), arrayRead.getTarget());
                }

                newCondition += condition;
                ifStatement.setCondition(factory.createCodeSnippetExpression(newCondition));
            }
        }
    }

    /**
     * Modifies the final return of the vulnerable method. This needs to be different because it will not only eliminate
     * a return expression but modified. If the last return was an expression and it wasn't already assigned to the return
     * variable it is now, and a new return expression is created.
     * @param factory   The factory used to create code snippets to add.
     * @param modifiedMethodBody    The body - a block of code - of the method modified. The new version of the method being modified
     * @param returnElement The element to return.
     * @param variableName  The name of the variable to be returned.
     * @param returnImpl    The return instruction to be replaced.
     */
    private static void modifyLastReturn(Factory factory, CtBlock<?> modifiedMethodBody, String returnElement, String variableName,
                                         CtReturnImpl<?> returnImpl) {

        String returnedValue = returnImpl.getReturnedExpression().toString();
        CtReturn<Object> objectCtReturn = factory.createReturn().setReturnedExpression(factory.createCodeSnippetExpression(variableName));

        if (!returnElement.contains(returnedValue)) {
            CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " = " + returnedValue);
            returnImpl.replace(codeSnippetStatement);
            modifiedMethodBody.addStatement(objectCtReturn);
        } else {
            returnImpl.replace(objectCtReturn);
        }
    }
}
