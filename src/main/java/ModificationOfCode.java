import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.ReturnOrThrowFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtReturnImpl;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class ModificationOfCode {
    private static Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);
    private static final String CLASS_NAME_ADDITION = "$Modification";

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String packageName = vulnerableMethodUsesCases.getFirstUseCasePackageName();
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info("The class name of the vulnerable method is {}.", className);

        String pathToCorrectedClass = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\";
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

    private static void modifyCode(String methodName, Factory factory, CtMethod<?> vulnerableMethod, CtModel model) {
        List<CtStatement> statementList = vulnerableMethod.getBody().getStatements();

        if (statementList.size() == 1 && !(statementList.get(0) instanceof CtIfImpl)) {
            String methodInvocation = ((CtReturnImpl) statementList.get(0)).getReturnedExpression().prettyprint();
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
        String finalReturn = returnList.get(lastIndex).toString()
                .replace("return ", "");

        String returnElement;

        // to avoid the wrong invocation of a method.
        if (finalReturn.contains("(") && !finalReturn.contains("+")) {
            List lastReturnInvocationArguments = ((CtInvocationImpl) ((CtReturnImpl) returnList.get(returnList.size() - 1))
                    .getReturnedExpression())
                    .getArguments();
            if (lastReturnInvocationArguments.stream().noneMatch(argument -> variableList.stream().anyMatch(variable -> variable.getReference().getDeclaration().getSimpleName().equals(argument)))) {
                returnElement = finalReturn;
            } else
                returnElement = "";
        } else if (finalReturn.contains("+"))
            returnElement = "";
        else
            returnElement = finalReturn;

        String variableName;

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
            modifiedMethodBody.removeStatement(returnAssignmentToRemove);
            modifiedMethodBody.addStatement(0, returnAssignmentToAdd);
            variableName = returnElement;
            logger.info("Change the position of the definition of the variable to be returned to the beginning of the method." +
                    "\n BE ADVISED: This can lead to problems since, the definition might require variable not defined.");
        }

        Iterator<CtCFlowBreak> returnsIterator = returnList.iterator();
        while (returnsIterator.hasNext()) {
            CtReturnImpl returnImpl = (CtReturnImpl) returnsIterator.next();
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
