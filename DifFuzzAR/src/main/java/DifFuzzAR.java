import com.sun.tools.javac.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;

public class DifFuzzAR {
    private static Logger logger = LoggerFactory.getLogger(DifFuzzAR.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.warn("There needs to be at least one argument for the application.");
            return;
        }
        String driverPath = args[0];
        logger.info(String.format("The passed path was %s.", driverPath));

        String methodLine = null;
        try {
            methodLine = discoverMethod(new FileReader(driverPath));
        } catch (FileNotFoundException e) {
            logger.warn("Could not find the requested file.");
            return;
        }
        if (methodLine == null) {
            logger.warn("The tool could not discover the vulnerable method.");
            return;
        }
        String methodName = methodLine.subSequence(methodLine.indexOf("= ") + 2, methodLine.indexOf("(")).toString();
        logger.info(String.format("The vulnerable method is %s", methodName));
    }

    static String discoverMethod(Reader fileReader) {//String driverPath) {
        try(BufferedReader reader = new BufferedReader(fileReader)) {//new FileReader(driverPath))) {
            int lines = 0;
            String line = "";
            String safeModeVariable = "";
            boolean afterMemClear = false;
            boolean safeMode = true;
            while (true) {
                line = reader.readLine();
                if (line == null)
                    break;
                lines++;
                if (line.contains("boolean") && line.contains("final")) {
                    String beforeName = "boolean ";
                    safeModeVariable = line.substring(line.indexOf(beforeName) + beforeName.length(), line.indexOf(" ="));
                    safeMode = line.toLowerCase().contains("true");
                    logger.info(String.format("This driver contains a safeMode variable named %s with the value %b.", safeModeVariable, safeMode));
                } else if (line.contains(String.format("if (%s)", safeModeVariable))) {
                    if (!safeMode) {
                        while (!line.contains("else")) {
                            lines++;
                            line = reader.readLine();
                        }
                    } else {
                        while (!line.contains("else")) {
                            lines++;
                            line = reader.readLine();
                            if (line.contains("Mem.clear()")) {
                                afterMemClear = true;
                                logger.info(String.format("The instruction Mem.clear() is in line %d", lines));
                            } else if (afterMemClear) {
                                // String method = line.subSequence(line.indexOf("= ") + 2, line.indexOf("(")).toString();
                                return line;
                                // logger.info(String.format("The vulnerable method is %s", line));
                            }
                        }
                        lines++;
                        line = reader.readLine();
                        while (!line.contains("}")) {
                            lines++;
                            line = reader.readLine();
                        }
                    }
                }else if (line.contains("Mem.clear()")) {
                    afterMemClear = true;
                    logger.info(String.format("The instruction Mem.clear() is in line %d", lines));
                } else if (!line.equals("") && !line.contains("try") && afterMemClear) {
                    // String method = line.subSequence(line.indexOf("= ") + 2, line.indexOf("(")).toString();
                    // logger.info(String.format("The vulnerable method is %s", method));
                    return line;
                }
            }
        } catch (IOException e) {
            logger.warn("There was an error while reading the Driver.");
        }
        return null;
    }
}
