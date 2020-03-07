import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.ReturnOrThrowFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtBlockImpl;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ModificationOfCode {
    private static Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info(String.format("The class name of the vulnerable method is %s.", className));

        String pathToCorrectedClass1 = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\";
        String pathToVulnerableMethod = pathToCorrectedClass1+ className + ".java";

        logger.info(String.format("The path to the vulnerable class is %s.", pathToVulnerableMethod));

        Launcher launcher = new Launcher();
        launcher.addInputResource(pathToVulnerableMethod);
        launcher.setSourceOutputDirectory(pathToCorrectedClass1);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setAutoImports(true);

        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        List<CtClass> ctQuery = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
        ctQuery.get(0).setSimpleName(className + "$Modification");

        CtMethod vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<CtMethod>(methodName)).first();
        List<CtTypedElement> typedElementList = model.filterChildren(new TypeFilter<>(CtTypedElement.class)).list();

        CtMethod modifiedMethod = vulnerableMethod.copyMethod();
        modifiedMethod.setSimpleName(vulnerableMethod.getSimpleName() + "$Modification");
        List<CtCFlowBreak> elem = vulnerableMethod.getElements(new ReturnOrThrowFilter());
        List<CtAssignment> elements1 = vulnerableMethod.getElements(new TypeFilter<>(CtAssignment.class));

        if (elem.size() > 1) {
            logger.info(String.format("The method %s suffers from early-exit timing side-channel vulnerability since it" +
                    "has %d exit points.", methodName, elem.size()));
        }

        String returnElement = elem.get(elem.size() - 1).toString().replace("return", "").replace(" ", "");


        //CtLocalVariable $1 = factory.createLocalVariable(modifiedMethod.getType(), "$1", factory.createLiteral(returnElement));
        CtCodeSnippetStatement $1  = factory.createCodeSnippetStatement(modifiedMethod.getType() + " $1 = " + returnElement);

        String variableName;
        if (elements1.stream().noneMatch(it -> it.getAssigned().toString().matches(".*\\b" + returnElement + "\\b.*"))) {
            logger.info(String.format("Added to the method %s, the instruction %s", methodName, $1));
            modifiedMethod.getBody().addStatement(0, $1);
            variableName = "$1";
        } else
            variableName = returnElement;

        Iterator iterator = modifiedMethod.getBody().iterator();
        AtomicInteger returnReplacements = new AtomicInteger(0);
        while (iterator.hasNext()) {
            CtStatement next = (CtStatement) iterator.next();
            String lineString = next.toString();

            if (lineString.contains("return") && returnReplacements.get() == elem.size() - 1) {
                CtReturn<Object> objectCtReturn = factory.createReturn().setReturnedExpression(factory.createCodeSnippetExpression(variableName));
                next.replace(objectCtReturn);
            }

            if (lineString.contains("return") && returnReplacements.get() != elem.size() - 1) {
                if (lineString.contains("for") || lineString.contains("if")) {
                    List<CtBlockImpl> elements = next.getElements(new TypeFilter<>(CtBlockImpl.class));
                    elements.forEach(it -> {
                        if (!it.toString().contains("if") && it.toString().contains("return")) {
                            String line = it.toString();
                            String value = line.substring(line.indexOf("return") + "return".length(), line.indexOf(";"));
                            CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " =" + value);
                            it.replace(codeSnippetStatement);
                            returnReplacements.incrementAndGet();
                        }
                        else if (it.toString().contains("return") && (it.toString().contains("while") || it.toString().contains("for") || it.toString().contains("if"))) {
                            List<CtBlock> innerElements = it.getElements(new TypeFilter<>(CtBlock.class));
                            for (CtBlock innerElement : innerElements) {
                                if (!innerElement.toString().contains("if") && innerElement.toString().contains("return")) {
                                    String line = innerElement.toString();
                                    String value = line.substring(line.indexOf("return") + "return".length(), line.indexOf(";"));
                                    CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " =" + value);
                                    innerElement.replace(codeSnippetStatement);
                                    returnReplacements.incrementAndGet();
                                } else {
                                    List<CtBlock> innerElements1 = innerElement.getElements(new TypeFilter<>(CtBlock.class));
                                    innerElements1.forEach(it1 -> {
                                        if (!it1.toString().contains("if") && it1.toString().contains("return")) {
                                            String line = it1.toString();
                                            String value = line.substring(line.indexOf("return") + "return".length(), line.indexOf(";"));
                                            CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " =" + value);
                                            it1.replace(codeSnippetStatement);
                                            returnReplacements.incrementAndGet();
                                        }
                                    });
                                }
                            }
                        }
                    });
                }
            }
        }
        launcher.prettyprint();
    }
}
