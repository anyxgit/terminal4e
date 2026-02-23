package me.anyx.terminal4e.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.ITheme;

import me.anyx.terminal4e.Activator;

final class TerminalThemeStore {
    static final String MODE_FOLLOW = "follow";
    static final String MODE_FIXED = "fixed";
    static final String TERMINAL_FONT_DEFINITION_ID = "me.anyx.terminal4e.theme.font.terminal";

    private static final char SEP = '|';
    private static final char ESC = '\\';

    private static final String[] COLOR_KEYS = new String[] {
            "foreground", "background", "cursor", "cursorAccent", "selectionBackground",
            "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white",
            "brightBlack", "brightRed", "brightGreen", "brightYellow", "brightBlue", "brightMagenta",
            "brightCyan", "brightWhite" };

    private static final List<TerminalTheme> BUILTIN_THEMES = createBuiltins();

    private TerminalThemeStore() {
    }

    static String[] getColorKeys() {
        return Arrays.copyOf(COLOR_KEYS, COLOR_KEYS.length);
    }

    static List<TerminalTheme> getAllThemes(IPreferenceStore store) {
        List<TerminalTheme> all = new ArrayList<>(BUILTIN_THEMES);
        all.addAll(loadCustomThemes(store));
        return all;
    }

    static List<TerminalTheme> getBuiltinThemes() {
        return new ArrayList<>(BUILTIN_THEMES);
    }

    static List<TerminalTheme> loadCustomThemes(IPreferenceStore store) {
        if (store == null) {
            return Collections.emptyList();
        }
        String raw = store.getString(Activator.PREF_THEME_CUSTOM);
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<TerminalTheme> result = new ArrayList<>();
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            List<String> fields = splitLine(line);
            if (fields.size() < 3) {
                continue;
            }
            String id = fields.get(0);
            String name = fields.get(1);
            boolean dark = "1".equals(fields.get(2));
            Map<String, String> colors = new LinkedHashMap<>();
            for (int i = 0; i < COLOR_KEYS.length; i++) {
                int fieldIndex = i + 3;
                String value = fieldIndex < fields.size() ? fields.get(fieldIndex) : "";
                if (isValidHexColor(value)) {
                    colors.put(COLOR_KEYS[i], normalizeHex(value));
                }
            }
            result.add(new TerminalTheme(id, name, dark, false, completeColors(colors, dark)));
        }
        return result;
    }

    static void saveCustomThemes(IPreferenceStore store, List<TerminalTheme> themes) {
        if (store == null) {
            return;
        }
        if (themes == null || themes.isEmpty()) {
            store.setValue(Activator.PREF_THEME_CUSTOM, "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (TerminalTheme theme : themes) {
            if (theme == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(encode(theme.getId()));
            sb.append(SEP);
            sb.append(encode(theme.getName()));
            sb.append(SEP);
            sb.append(theme.isDark() ? '1' : '0');
            for (String key : COLOR_KEYS) {
                sb.append(SEP);
                sb.append(encode(theme.getColor(key)));
            }
        }
        store.setValue(Activator.PREF_THEME_CUSTOM, sb.toString());
    }

    static TerminalTheme findThemeById(IPreferenceStore store, String themeId) {
        if (themeId == null || themeId.trim().isEmpty()) {
            return null;
        }
        for (TerminalTheme theme : getAllThemes(store)) {
            if (themeId.equals(theme.getId())) {
                return theme;
            }
        }
        return null;
    }

    static TerminalTheme resolveActiveTheme(IPreferenceStore store) {
        String mode = store == null ? MODE_FOLLOW : store.getString(Activator.PREF_THEME_MODE);
        if (!MODE_FIXED.equals(mode)) {
            boolean dark = isEclipseDarkTheme();
            String themeId = dark
                    ? "dark-plus"
                    : "light-plus";

            for (TerminalTheme terminalTheme : BUILTIN_THEMES) {
                if (themeId.equals(terminalTheme.id)) {
                    return terminalTheme;
                }
            }
        }
        TerminalTheme fixed = findThemeById(store, store.getString(Activator.PREF_THEME_FIXED));
        if (fixed != null) {
            return fixed;
        }
        return findThemeById(store, "dark-plus");
    }

    static String toJsonTheme(TerminalTheme theme) {
        TerminalTheme safe = theme == null ? findThemeById(null, "dark-plus") : theme;
        if (safe == null) {
            safe = BUILTIN_THEMES.get(0);
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < COLOR_KEYS.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String key = COLOR_KEYS[i];
            sb.append('"').append(key).append('"').append(':');
            sb.append('"').append(normalizeHex(safe.getColor(key))).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    static TerminalFont resolveTerminalFont() {
        try {
            if (PlatformUI.isWorkbenchRunning() && PlatformUI.getWorkbench() != null
                    && PlatformUI.getWorkbench().getThemeManager() != null
                    && PlatformUI.getWorkbench().getThemeManager().getCurrentTheme() != null) {
                ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
                FontRegistry registry = theme.getFontRegistry();
                FontData[] fontData = registry == null ? null : registry.getFontData(TERMINAL_FONT_DEFINITION_ID);
                if (fontData != null && fontData.length > 0 && fontData[0] != null) {
                    FontData first = fontData[0];
                    int size = pointsToPixels(first.getHeight());
                    if (size <= 0) {
                        size = pointsToPixels(12);
                    }
                    String family = first.getName();
                    if (family == null || family.trim().isEmpty()) {
                        family = "monospace";
                    }
                    String weight = (first.getStyle() & SWT.BOLD) != 0 ? "bold" : "normal";
                    return new TerminalFont(family, size, weight);
                }
            }
        } catch (Exception ignored) {
        }
        return new TerminalFont("monospace", pointsToPixels(12), "normal");
    }

    static String toJsonFont(TerminalFont font) {
        TerminalFont safe = font == null ? new TerminalFont("monospace", 12, "normal") : font;
        String family = safe.getFamily();
        if (family == null || family.trim().isEmpty()) {
            family = "monospace";
        }
        int size = safe.getSize() <= 0 ? 12 : safe.getSize();
        String weight = safe.getWeight();
        if (weight == null || weight.trim().isEmpty()) {
            weight = "normal";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"family\":\"").append(escapeJson(family)).append("\"");
        sb.append(',');
        sb.append("\"size\":").append(size);
        sb.append(',');
        sb.append("\"weight\":\"").append(escapeJson(weight)).append("\"");
        sb.append('}');
        return sb.toString();
    }

    static boolean isEclipseDarkTheme() {
        try {
            if (PlatformUI.isWorkbenchRunning() && PlatformUI.getWorkbench() != null
                    && PlatformUI.getWorkbench().getThemeManager() != null
                    && PlatformUI.getWorkbench().getThemeManager().getCurrentTheme() != null) {
                ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
                Color bg = theme.getColorRegistry().get("org.eclipse.ui.workbench.HOVER_BACKGROUND");
                if (bg != null) {
                    double luminance = bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114;
                    return luminance < 150;
                }
            }
        } catch (Exception ignored) {
        }
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return true;
        }
        Color bg = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        if (bg == null) {
            return true;
        }
        double luminance = bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114;
        return luminance < 150;
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ESC || c == SEP || c == '\n') {
                sb.append(ESC);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == ESC) {
                escaped = true;
                continue;
            }
            if (c == SEP) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        fields.add(current.toString());
        return fields;
    }

    private static String normalizeHex(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "#000000";
        }
        String value = raw.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        if (value.length() == 4) {
            char r = value.charAt(1);
            char g = value.charAt(2);
            char b = value.charAt(3);
            value = "#" + r + r + g + g + b + b;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    static boolean isValidHexColor(String value) {
        if (value == null) {
            return false;
        }
        String raw = value.trim();
        if (!raw.startsWith("#")) {
            raw = "#" + raw;
        }
        return raw.matches("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$");
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int pointsToPixels(int points) {
        int pt = points <= 0 ? 12 : points;
        Display display = Display.getDefault();
        int dpiY = 96;
        if (display != null && !display.isDisposed()) {
            Point dpi = display.getDPI();
            if (dpi != null && dpi.y > 0) {
                dpiY = dpi.y;
            }
        }
        return (int) Math.round(pt * dpiY / 72.0d);
    }

    private static Map<String, String> completeColors(Map<String, String> source, boolean dark) {
        Map<String, String> defaults = dark
                ? findThemeById(null, "dark-plus").colors
                : findThemeById(null, "light-plus").colors;
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : COLOR_KEYS) {
            String value = source == null ? null : source.get(key);
            if (!isValidHexColor(value)) {
                value = defaults.get(key);
            }
            result.put(key, normalizeHex(value));
        }
        return result;
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> colors = new LinkedHashMap<>();
        for (int i = 0; i < COLOR_KEYS.length && i < values.length; i++) {
            colors.put(COLOR_KEYS[i], normalizeHex(values[i]));
        }
        return colors;
    }

    private static List<TerminalTheme> createBuiltins() {
        List<TerminalTheme> themes = new ArrayList<>();
        themes.add(new TerminalTheme("dark-plus", "Dark+", true, true,
                mapOf("#d4d4d4", "#1e1e1e", "#aeafad", "#1e1e1e", "#264f78",
                        "#000000", "#cd3131", "#0dbc79", "#e5e510", "#2472c8", "#bc3fbc", "#11a8cd", "#e5e5e5",
                        "#666666", "#f14c4c", "#23d18b", "#f5f543", "#3b8eea", "#d670d6", "#29b8db", "#e5e5e5")));
        themes.add(new TerminalTheme("light-plus", "Light+", false, true,
                mapOf("#333333", "#ffffff", "#000000", "#ffffff", "#add6ff",
                        "#000000", "#cd3131", "#00bc00", "#949800", "#0451a5", "#bc05bc", "#0598bc", "#555555",
                        "#666666", "#cd3131", "#14ce14", "#b5ba00", "#0451a5", "#bc05bc", "#0598bc", "#a5a5a5")));
        themes.add(new TerminalTheme("hc-black", "High Contrast", true, true,
                mapOf("#ffffff", "#000000", "#ffffff", "#000000", "#f38518",
                        "#000000", "#cd0000", "#00cd00", "#cdcd00", "#0000ee", "#cd00cd", "#00cdcd", "#e5e5e5",
                        "#7f7f7f", "#ff0000", "#00ff00", "#ffff00", "#5c5cff", "#ff00ff", "#00ffff", "#ffffff")));
        themes.add(new TerminalTheme("solarized-dark", "Solarized Dark", true, true,
                mapOf("#839496", "#002b36", "#93a1a1", "#002b36", "#073642",
                        "#073642", "#dc322f", "#859900", "#b58900", "#268bd2", "#d33682", "#2aa198", "#eee8d5",
                        "#002b36", "#cb4b16", "#586e75", "#657b83", "#839496", "#6c71c4", "#93a1a1", "#fdf6e3")));
        return themes;
    }

    static final class TerminalTheme {
        private final String id;
        private final String name;
        private final boolean dark;
        private final boolean builtin;
        private final Map<String, String> colors;

        TerminalTheme(String id, String name, boolean dark, boolean builtin, Map<String, String> colors) {
            this.id = id;
            this.name = name;
            this.dark = dark;
            this.builtin = builtin;
            this.colors = new LinkedHashMap<>();
            if (colors != null) {
                for (String key : COLOR_KEYS) {
                    String value = colors.get(key);
                    if (isValidHexColor(value)) {
                        this.colors.put(key, normalizeHex(value));
                    }
                }
            }
            if (this.colors.isEmpty()) {
                this.colors.putAll(completeColors(Collections.<String, String>emptyMap(), dark));
            }
        }

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }

        boolean isDark() {
            return dark;
        }

        boolean isBuiltin() {
            return builtin;
        }

        Map<String, String> getColors() {
            return new LinkedHashMap<>(colors);
        }

        String getColor(String key) {
            String color = colors.get(key);
            if (isValidHexColor(color)) {
                return normalizeHex(color);
            }
            return "#000000";
        }
    }

    static final class TerminalFont {
        private final String family;
        private final int size;
        private final String weight;

        TerminalFont(String family, int size, String weight) {
            this.family = family == null ? "monospace" : family;
            this.size = size;
            this.weight = weight == null ? "normal" : weight;
        }

        String getFamily() {
            return family;
        }

        int getSize() {
            return size;
        }

        String getWeight() {
            return weight;
        }
    }
}
