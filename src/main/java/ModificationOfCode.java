import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCFlowBreak;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.ReturnOrThrowFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.Iterator;
import java.util.List;

public class ModificationOfCode {
    private static Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info(String.format("The class name of the vulnerable method is %s.", className));

        String pathToVulnerableMethod = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\" + className + ".java";
        String pathToCorrectedClass1 = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\";

        logger.info(String.format("The path to the vulnerable class is %s.", pathToVulnerableMethod));

        Launcher launcher = new Launcher();
        launcher.addInputResource(pathToVulnerableMethod);
        launcher.setSourceOutputDirectory(pathToCorrectedClass1);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setAutoImports(true);
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        List<CtClass> ctQuery = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
        ctQuery.get(0).setSimpleName("User2");

        CtMethod vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<CtMethod>(methodName)).first();

        List<CtCFlowBreak> elem = vulnerableMethod.getElements(new ReturnOrThrowFilter());
        if (elem.size() > 1) {
            logger.info(String.format("The method %s suffers from early-exit timing side-channel vulnerability since it" +
                    "has %d exit points.", methodName, elem.size()));
        }

        Iterator iterator1 = vulnerableMethod.getBody().iterator();

        String variableName = elem.get(elem.size() - 1).toString().replace("return", "");

        int returnReplacements = 0;
        while (iterator1.hasNext()) {
            //factory.createLocalVariable(declaringType, "$temp", )
            CtStatement next = (CtStatement) iterator1.next();
            String lineString = next.toString();

            if (lineString.contains("return") && returnReplacements != elem.size() - 1) {
                if (lineString.contains("for")) {
                    List<CtBlock> elements = next.getElements(new TypeFilter<>(CtBlock.class));
                    elements.forEach(it -> {
                        if (!it.toString().contains("if") && it.toString().contains("return")) {
                            String line = it.toString();
                            String value = line.substring(line.indexOf("return") + "return".length(), line.indexOf(";"));
                            CtCodeSnippetStatement codeSnippetStatement = factory.Code().createCodeSnippetStatement(variableName + " =" + value);
                            it.replace(codeSnippetStatement);
                        }
                    });
                }
                returnReplacements++;
            }
        }
        launcher.prettyprint();
    }
}
