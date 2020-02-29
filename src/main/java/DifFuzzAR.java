import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO code simplification and beautification.
public class DifFuzzAR {
    private static Logger logger = LoggerFactory.getLogger(DifFuzzAR.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.warn("There needs to be at least one argument for the application.");
            return;
        }
        String driverPath = args[0];
        logger.info(String.format("The passed path was %s.", driverPath));

        VulnerableMethodUses vulnerableMethodUsesCases = FindVulnerableMethod.processDriver(driverPath);

        if (vulnerableMethodUsesCases == null) {
            logger.warn(String.format("DifFuzzAR could not find the vulnerable method indicated in the Driver %s", driverPath));
            return;
        }

        ModificationOfCode.processVulnerableClass(driverPath, vulnerableMethodUsesCases);
    }
}
