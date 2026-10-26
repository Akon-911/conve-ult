package com.conveult;

import com.conveult.gui.ConveUltApp;

/**
 * Universal Entry Point for conve-ult.
 * Dispatches to JavaFX GUI if requested or launched interactively without flags,
 * or directly to CLI mode if flags/files are specified.
 */
public class ConveUltMain {

    public static void main(String[] args) {
        boolean hasExplicitGuiFlag = args != null && args.length > 0 &&
                (args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("--gui"));

        boolean noArgs = args == null || args.length == 0;
        boolean hasDisplay = System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;

        // Launch GUI if explicitly asked or launched with no arguments in a desktop session
        if (hasExplicitGuiFlag || (noArgs && hasDisplay)) {
            try {
                String[] guiArgs = hasExplicitGuiFlag ?
                        java.util.Arrays.copyOfRange(args, 1, args.length) :
                        (args == null ? new String[0] : args);
                ConveUltApp.launchApp(guiArgs);
                return;
            } catch (Throwable t) {
                if (hasExplicitGuiFlag) {
                    System.err.println("Error launching JavaFX GUI: " + t.getMessage());
                    if (System.getProperty("debug") != null) t.printStackTrace();
                    System.exit(1);
                }
                // If implicit launch failed, fallback smoothly to CLI help
            }
        }

        // Run CLI command
        ConveUlt.main(args);
    }
}
