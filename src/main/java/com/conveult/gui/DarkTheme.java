package com.conveult.gui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.io.InputStream;
import java.net.URL;

/**
 * Styling and dark theme constants for conve-ult GUI.
 */
public class DarkTheme {

    public static final String COLOR_BG_DARK = "#13141f";
    public static final String COLOR_SURFACE = "#1a1c2b";
    public static final String COLOR_SURFACE_ALT = "#212437";
    public static final String COLOR_BORDER = "#282c42";
    public static final String COLOR_ACCENT = "#6366f1";
    public static final String COLOR_ACCENT_HOVER = "#4f46e5";
    public static final String COLOR_TEXT_PRIMARY = "#f1f5f9";
    public static final String COLOR_TEXT_MUTED = "#94a3b8";
    public static final String COLOR_SUCCESS = "#10b981";
    public static final String COLOR_DANGER = "#ef4444";

    // Common SVG Icons (crisp at all DPIs)
    public static final String ICON_UPLOAD = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z";
    public static final String ICON_FILE = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
    public static final String ICON_CONVERT = "M19 8l-4 4h3c0 3.31-2.69 6-6 6-1.01 0-1.97-.25-2.8-.7l-1.46 1.46C8.97 19.54 10.43 20 12 20c4.42 0 8-3.58 8-8h3l-4-4zM6 12c0-3.31 2.69-6 6-6 1.01 0 1.97.25 2.8.7l1.46-1.46C15.03 4.46 13.57 4 12 4c-4.42 0-8 3.58-8 8H1l4 4 4-4H6z";
    public static final String ICON_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    public static final String ICON_FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    public static final String ICON_INFO = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";
    public static final String ICON_TRASH = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    public static final String ICON_EXTERNAL = "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z";

    /**
     * Applies the dark mode stylesheet to the scene.
     */
    public static void apply(Scene scene) {
        URL cssResource = DarkTheme.class.getResource("dark-theme.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
    }

    /**
     * Creates an SVG icon with specified fill color and scaling.
     */
    public static SVGPath createIcon(String svgContent, String hexColor, double scale) {
        SVGPath path = new SVGPath();
        path.setContent(svgContent);
        path.setFill(Color.web(hexColor));
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }
}
