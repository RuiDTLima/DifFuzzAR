import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DifFuzzAR {
    private static final Logger logger = LoggerFactory.getLogger(DifFuzzAR.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.warn("There needs to be at least one argument for the application.");
            return;
        }
        String driverPath = args[0];
        logger.info("The passed path was {}.", driverPath);

        VulnerableMethodUses vulnerableMethodUsesCases = FindVulnerableMethod.processDriver(driverPath);

        if (vulnerableMethodUsesCases == null) {
            logger.warn("DifFuzzAR could not find the vulnerable method indicated in the Driver {}.", driverPath);
            return;
        }

        ModificationOfCode.processVulnerableClass(driverPath, vulnerableMethodUsesCases);
        logger.info("Finish the automatic repair.\n BE ADVISED: The corrected code may be logical flawed. Use the modified code as a template.");
    }
}
