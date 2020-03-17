import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.ReturnOrThrowFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtBlockImpl;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ModificationOfCode {
    private static Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);
    private static final String CLASS_NAME_ADDITION = "$Modification";

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String packageName = vulnerableMethodUsesCases.getFirstUseCasePackageName();
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info(String.format("The class name of the vulnerable method is %s.", className));

        String pathToCorrectedClass = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\";
        String pathToVulnerableMethod = pathToCorrectedClass + packageName + "\\" + className + ".java";

        logger.info(String.format("The path to the vulnerable class is %s.", pathToVulnerableMethod));

        Launcher launcher = Setup.setupLauncher(pathToVulnerableMethod, pathToCorrectedClass);

        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        List<CtClass<?>> ctQuery = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
        ctQuery.get(0).setSimpleName(className + CLASS_NAME_ADDITION);  // TODO remove zero
        // TODO Add method from superClass.
        modifyCode(methodName, factory, model);
        launcher.prettyprint();
    }

    private static void modifyCode(String methodName, Factory factory, CtModel model) {
        CtMethod<?> vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName)).first();

        CtMethod<?> modifiedMethod = vulnerableMethod.copyMethod();
        modifiedMethod.setSimpleName(vulnerableMethod.getSimpleName() + CLASS_NAME_ADDITION);
        List<CtCFlowBreak> returnList = vulnerableMethod.getElements(new ReturnOrThrowFilter());
        List<CtAssignment<?, ?>> assignmentList = vulnerableMethod.getElements(new TypeFilter<>(CtAssignment.class));

        if (returnList.size() > 1) {
            logger.info(String.format("The method %s suffers from early-exit timing side-channel vulnerability since it" +
                    "has %d exit points.", methodName, returnList.size()));
        }

        String returnElement = returnList.get(returnList.size() - 1).toString().replace("return", "").replace(" ", "");
        CtCodeSnippetStatement $1  = factory.createCodeSnippetStatement(modifiedMethod.getType() + " $1 = " + returnElement);

        String variableName;
        if (assignmentList.stream().noneMatch(it -> it.getAssigned().toString().matches(".*\\b" + returnElement + "\\b.*"))) {
            logger.info(String.format("Added to the method %s, the instruction %s", methodName, $1));
            modifiedMethod.getBody().addStatement(0, $1);
            variableName = "$1";
        } else
            variableName = returnElement;

        Iterator<?> iterator = modifiedMethod.getBody().iterator();
        AtomicInteger returnReplacements = new AtomicInteger(0);
        iterateCode(factory, returnList, variableName, iterator, returnReplacements);
        List<CtCFlowBreak> modifiedReturnList = modifiedMethod.getElements(new ReturnOrThrowFilter());

        if (modifiedReturnList.stream().noneMatch(it -> it.toString().matches(".*\\breturn\\b.*"))) {
            CtCodeSnippetStatement returnStatement = factory.createCodeSnippetStatement("return " + variableName);
            modifiedMethod.getBody().addStatement(returnStatement);
        }
    }

    private static void iterateCode(Factory factory, List<CtCFlowBreak> returnList, String variableName, Iterator<?> iterator, AtomicInteger returnReplacements) {
        while (iterator.hasNext()) {
            CtStatement currentStatement = (CtStatement) iterator.next();
            String lineString = currentStatement.toString();

            if (lineString.contains("return") && returnReplacements.get() < returnList.size()) {
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
                } else if (returnReplacements.get() != returnList.size() - 1) {
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
        CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " =" + value);
        blockStatement.replace(codeSnippetStatement);
        returnReplacements.incrementAndGet();
    }
}
