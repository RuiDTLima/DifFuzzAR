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
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtReturnImpl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class ModificationOfCode {
    private static Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);
    private static final String CLASS_NAME_ADDITION = "$Modification";
    private static final List<String> returns = new ArrayList<>();

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
        List<CtCFlowBreak> returnList = vulnerableMethod.getElements(new ReturnOrThrowFilter());
        List<CtLocalVariable<?>> variableList = vulnerableMethod.getElements(new TypeFilter<>(CtLocalVariable.class));
        CtBlock<?> modifiedMethodBody = modifiedMethod.getBody();

        if (returnList.size() > 1) {
            logger.info("The method {} suffers from early-exit timing side-channel vulnerability since it " +
                    "has {} exit points.", methodName, returnList.size());
        }

        String temp = returnList.get(returnList.size() - 1).toString()
                .replace("return", "").replace(" ", "");

        String returnElement;

        // to avoid the wrong invocation of a method.
        if (temp.contains("(")) {
            returnElement = "";
        } else
            returnElement = " = " + temp;

        String variableName;

        // Create a variable to hold the return value.
        Optional<CtLocalVariable<?>> optionalReturnAssignment = variableList.stream().filter(it -> it.getReference().toString().matches(".*\\b" + temp + "\\b.*")).findFirst();
        if (!optionalReturnAssignment.isPresent()) {
            CtCodeSnippetStatement $1  = factory.createCodeSnippetStatement(modifiedMethod.getType() + " $1" + returnElement);
            modifiedMethodBody.addStatement(0, $1);
            variableName = "$1";
            logger.info("Added to the method {}, the instruction {}.", methodName, $1);
        } else {
            CtLocalVariable<?> returnAssignmentToRemove = optionalReturnAssignment.get();
            CtLocalVariable<?> returnAssignmentToAdd = returnAssignmentToRemove.clone();
            modifiedMethodBody.removeStatement(returnAssignmentToRemove);
            modifiedMethodBody.addStatement(0, returnAssignmentToAdd);
            variableName = temp;
            logger.info("Change the position of the definition of the variable to be returned to the beginning of the method." +
                    "\n BE ADVISED: This can lead to problems since, the definition might require variable not defined.");
        }

        Iterator<?> iterator = modifiedMethodBody.iterator();
        AtomicInteger returnReplacements = new AtomicInteger(0);
        iterateCode(factory, returnList, variableName, iterator, returnReplacements);
        List<CtCFlowBreak> modifiedReturnList = modifiedMethod.getElements(new ReturnOrThrowFilter());

        if (modifiedReturnList.stream().noneMatch(it -> it.toString().matches(".*\\breturn\\b.*"))) {
            CtCodeSnippetStatement returnStatement = factory.createCodeSnippetStatement("return " + variableName);
            modifiedMethodBody.addStatement(returnStatement);
        }
    }

    private static void iterateCode(Factory factory, List<CtCFlowBreak> returnList, String variableName, Iterator<?> iterator, AtomicInteger returnReplacements) {
        while (iterator.hasNext()) {
            CtStatement currentStatement = (CtStatement) iterator.next();
            String lineString = currentStatement.toString();

            // Incompatibility of the examples blazer_sanity_unsafe and themis_oacc_unsafe
            if (lineString.contains("return") && !lineString.matches(".*\\b" + variableName + "\\b.*") && returns.stream().noneMatch(it -> lineString.matches(".*\\b" + it + "\\b.*"))) {//returnReplacements.get() < returnList.size()) {
                if (lineString.contains("for") || lineString.contains("if")) {
                    List<CtBlockImpl<CtStatement>> elements = currentStatement.getElements(new TypeFilter<>(CtBlockImpl.class));
                    for (CtBlockImpl<CtStatement> blockElement : elements) {
                        for (CtStatement blockStatement : blockElement) {
                            if (!blockStatement.toString().contains("if") && blockStatement.toString().contains("return")) {
                                String line = blockStatement.toString();
                                returnReplacement(factory, variableName, returnReplacements, blockStatement, line);
                            }
                        }
                    }
                } else if (returnReplacements.get() != returnList.size()) {
                    returnReplacement(factory, variableName, returnReplacements, currentStatement, lineString);
                } else {
                    CtReturn<Object> objectCtReturn = factory.createReturn().setReturnedExpression(factory.createCodeSnippetExpression(variableName));
                    currentStatement.replace(objectCtReturn);
                }
            }
        }
    }

    private static void returnReplacement(Factory factory, String variableName, AtomicInteger returnReplacements, CtStatement blockStatement, String line) {
        String value = line.substring(line.indexOf("return") + "return".length());
        returns.add(value);
        CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " =" + value);
        blockStatement.replace(codeSnippetStatement);
        returnReplacements.incrementAndGet();
    }
}
