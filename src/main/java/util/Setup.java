package util;

import spoon.Launcher;

public class Setup {
    public static Launcher setupLauncher(String inputResourcePath, String outputResourcePath) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputResourcePath);
        launcher.setSourceOutputDirectory(outputResourcePath);
        launcher.getEnvironment().setCommentEnabled(false); // To ignore the comments in the source code.
        launcher.getEnvironment().setAutoImports(true); // To include the imports in the model
        return launcher;
    }
}