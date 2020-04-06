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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ModificationOfCode {
    private static Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);
    private static final String CLASS_NAME_ADDITION = "$Modification";

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String packageName = vulnerableMethodUsesCases.getFirstUseCasePackageName();
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info("The class name of the vulnerable method is {}.", className);

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

            modifyCode(methodName, factory, vulnerableMethod, model);
            launcher.prettyprint();
       /* } else
            logger.warn("The inspected file contains {} classes, can't detect the vulnerable one.", classList.size());*/
    }

    // TODO make private
    protected static void modifyCode(String methodName, Factory factory, CtMethod<?> vulnerableMethod, CtModel model) {
        List<CtStatement> statementList = vulnerableMethod.getBody().getStatements();

        if (statementList.size() == 1 && !(statementList.get(0) instanceof CtIfImpl)) {
            String methodInvocation = ((CtReturnImpl<?>) statementList.get(0)).getReturnedExpression().prettyprint();
            String calledMethodName = methodInvocation.substring(0, methodInvocation.indexOf("("));
            vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(calledMethodName)).first();
            modifyCode(calledMethodName, factory, vulnerableMethod, model);
            return;
        }

        CtMethod<?> modifiedMethod = vulnerableMethod.copyMethod();
        Refactoring.changeMethodName(modifiedMethod, vulnerableMethod.getSimpleName() + CLASS_NAME_ADDITION);

        List<CtCFlowBreak> returnList = modifiedMethod.getElements(new ReturnOrThrowFilter());
        returnList.removeIf(returnOrThrow -> !(returnOrThrow instanceof CtReturnImpl));
        List<CtLocalVariable<?>> variableList = modifiedMethod.getElements(new TypeFilter<>(CtLocalVariable.class));
        CtBlock<?> modifiedMethodBody = modifiedMethod.getBody();

        if (returnList.size() > 1) {
            logger.info("The method {} suffers from early-exit timing side-channel vulnerability since it " +
                    "has {} exit points.", methodName, returnList.size());
        }

        int lastIndex = returnList.size() - 1;
        CtReturnImpl finalReturnElement = (CtReturnImpl) returnList.get(lastIndex);
        String finalReturn = finalReturnElement.toString()
                .replace("return ", "");

        String returnElement;

        // to avoid the wrong invocation of a method.
        if (finalReturn.contains("(") && !finalReturn.contains("+")) {
            Stream<?> lastReturnInvocationArguments = ((CtInvocationImpl<?>) ((CtReturnImpl<?>) returnList.get(returnList.size() - 1))
                    .getReturnedExpression())
                    .getArguments()
                    .stream()
                    .flatMap(elem -> Arrays.stream(elem.toString().split("\\.")).limit(1));

            if (lastReturnInvocationArguments.noneMatch(argument ->  variableList.stream().anyMatch(variable -> variable.getReference().getDeclaration().getSimpleName().equals(argument)))) {
                returnElement = finalReturn;
            } else
                returnElement = "";
        } else if (finalReturnElement.getReturnedExpression() instanceof CtBinaryOperator)//if (finalReturn.contains("+"))
            returnElement = "";
        else
            returnElement = finalReturn;

        String variableName = "";

        // Create a variable to hold the return value.
        Optional<CtLocalVariable<?>> optionalReturnAssignment = variableList.stream().filter(it -> it.getReference().toString().matches(".*\\b" + finalReturn + "\\b.*")).findFirst();
        if (!optionalReturnAssignment.isPresent()) {
            String instructionToAdd = " $1" + (returnElement.equals("") ? returnElement : " = " + returnElement);
            CtCodeSnippetStatement $1  = factory.createCodeSnippetStatement(modifiedMethod.getType() + instructionToAdd);
            modifiedMethodBody.addStatement(0, $1);
            variableName = "$1";
            logger.info("Added to the method {}, the instruction {}.", methodName, $1);
        } else {
            CtLocalVariable<?> returnAssignmentToRemove = optionalReturnAssignment.get();
            CtLocalVariable<?> returnAssignmentToAdd = returnAssignmentToRemove.clone();
            if (returnAssignmentToRemove.getDefaultExpression() instanceof CtNewArrayImpl) {    // In response to themis_dynatable_unsafe
                List<CtExpression<Integer>> dimensionExpressions = ((CtNewArrayImpl<?>) returnAssignmentToRemove.getDefaultExpression()).getDimensionExpressions();

                for (CtLocalVariable<?> variable : variableList) {
                    if (dimensionExpressions.stream().anyMatch(arraySize -> variable.getSimpleName().equals(arraySize.toString()))) {
                        String returnName = returnAssignmentToRemove.getSimpleName();
                        CtExpression<?> assignment = returnAssignmentToRemove.getAssignment();
                        CtTypeReference<?> returnType = returnAssignmentToRemove.getType();
                        CtCodeSnippetStatement assignmentModification  = factory.createCodeSnippetStatement(returnName + " = " + assignment);
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

        Iterator<CtCFlowBreak> returnsIterator = returnList.iterator();
        while (returnsIterator.hasNext()) {
            CtReturnImpl<?> returnImpl = (CtReturnImpl<?>) returnsIterator.next();
            CtElement parentElement = returnImpl.getParent().getParent();
            if (parentElement instanceof CtIfImpl) {
                CtIfImpl parentIfStatement = (CtIfImpl) parentElement;
                if (parentIfStatement.getCondition() instanceof CtBinaryOperator) {
                    CtBinaryOperator<Boolean> condition = (CtBinaryOperator<Boolean>) parentIfStatement.getCondition();
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
                        parentIfStatement.setCondition(factory.createCodeSnippetExpression(newCondition));
                    }
                }
            }
            String returnedValue = returnImpl.getReturnedExpression().toString();
            if (!returnsIterator.hasNext()) {
                if (!returnElement.contains(returnedValue)) {
                    CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " = " + returnedValue);
                    returnImpl.replace(codeSnippetStatement);
                    CtCodeSnippetStatement returnStatement = factory.createCodeSnippetStatement("return " + variableName);
                    modifiedMethodBody.addStatement(returnStatement);
                    break;
                }
                CtReturn<Object> objectCtReturn = factory.createReturn().setReturnedExpression(factory.createCodeSnippetExpression(variableName));
                returnImpl.replace(objectCtReturn);
                break;
            }
            CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " = " + returnedValue);
            returnImpl.replace(codeSnippetStatement);
        }
    }
}
