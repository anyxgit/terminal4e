package me.anyx.terminal4e.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.PreferencesUtil;

import me.anyx.terminal4e.Activator;
import me.anyx.terminal4e.Messages;
import me.anyx.terminal4e.NLS;

public class TerminalThemePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    private static final int SWATCH_WIDTH = 48;
    private static final int SWATCH_HEIGHT = 18;
    private static final int COMPACT_MARGIN = 6;
    private static final int COMPACT_SPACING = 4;
    private static final String FOLLOW_OPTION_ID = "__follow_eclipse__";

    private Combo themeSelectorCombo;
    private Text customThemeNameText;

    private Composite colorPreviewGrid;
    private final Map<String, String> previewColorValues = new LinkedHashMap<>();
    private final Map<String, Canvas> previewColorButtons = new LinkedHashMap<>();

    private final List<TerminalThemeStore.TerminalTheme> customThemes = new ArrayList<>();
    private final LinkedHashMap<String, TerminalThemeStore.TerminalTheme> themesById = new LinkedHashMap<>();
    private String selectedThemeId;

    public TerminalThemePreferencePage() {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription(Messages.TerminalThemePreference_Description);
    }

    @Override
    public void init(IWorkbench workbench) {
        // no-op
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        loadThemes();
        readSelectionFromStore();
        createUnifiedThemeGroup(container);

        loadPreviewColorsFromSelection();
        resetCustomThemeName();
        refreshPreview();
        return container;
    }

    private void loadThemes() {
        themesById.clear();
        for (TerminalThemeStore.TerminalTheme theme : TerminalThemeStore.getBuiltinThemes()) {
            themesById.put(theme.getId(), theme);
        }
        customThemes.clear();
        customThemes.addAll(TerminalThemeStore.loadCustomThemes(getPreferenceStore()));
        for (TerminalThemeStore.TerminalTheme theme : customThemes) {
            themesById.put(theme.getId(), theme);
        }
    }

    private void readSelectionFromStore() {
        String mode = getPreferenceStore().getString(Activator.PREF_THEME_MODE);
        if (TerminalThemeStore.MODE_FOLLOW.equals(mode)) {
            selectedThemeId = FOLLOW_OPTION_ID;
            return;
        }
        String fixed = getPreferenceStore().getString(Activator.PREF_THEME_FIXED);
        if (fixed != null && themesById.containsKey(fixed)) {
            selectedThemeId = fixed;
            return;
        }
        selectedThemeId = "dark-plus";
    }

    private void createUnifiedThemeGroup(Composite parent) {
        Composite group = new Composite(parent, SWT.NONE);
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = COMPACT_MARGIN;
        layout.marginHeight = COMPACT_MARGIN;
        layout.horizontalSpacing = COMPACT_SPACING;
        layout.verticalSpacing = COMPACT_SPACING;
        group.setLayout(layout);

        new Label(group, SWT.NONE).setText(Messages.TerminalThemePreference_SelectTheme);

        Composite selectorRow = new Composite(group, SWT.NONE);
        selectorRow.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
        GridLayout selectorLayout = new GridLayout(2, false);
        selectorLayout.marginWidth = 0;
        selectorLayout.marginHeight = 0;
        selectorLayout.horizontalSpacing = COMPACT_SPACING;
        selectorRow.setLayout(selectorLayout);

        themeSelectorCombo = new Combo(selectorRow, SWT.DROP_DOWN | SWT.READ_ONLY);
        themeSelectorCombo.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
        refillThemeSelector();
        themeSelectorCombo.addListener(SWT.Selection, e -> {
            selectedThemeId = getSelectedThemeId(themeSelectorCombo);
            loadPreviewColorsFromSelection();
            resetCustomThemeName();
            refreshPreview();
        });

        Composite saveRow = new Composite(group, SWT.NONE);
        saveRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        GridLayout saveLayout = new GridLayout(5, false);
        saveLayout.marginWidth = 0;
        saveLayout.marginHeight = 0;
        saveLayout.horizontalSpacing = COMPACT_SPACING;
        saveRow.setLayout(saveLayout);

        new Label(saveRow, SWT.NONE).setText(Messages.TerminalThemePreference_CustomName);
        customThemeNameText = new Text(saveRow, SWT.BORDER);
        customThemeNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button saveCustomButton = new Button(saveRow, SWT.PUSH);
        saveCustomButton.setText(Messages.TerminalThemePreference_CustomizeSaveAs);
        saveCustomButton.addListener(SWT.Selection, e -> saveCurrentAsCustomTheme());

        Button editCustomButton = new Button(saveRow, SWT.PUSH);
        editCustomButton.setText(Messages.TerminalPreference_Edit);
        editCustomButton.addListener(SWT.Selection, e -> updateCurrentCustomTheme());

        Button deleteCustomButton = new Button(saveRow, SWT.PUSH);
        deleteCustomButton.setText(Messages.TerminalPreference_Remove);
        deleteCustomButton.addListener(SWT.Selection, e -> deleteCurrentCustomTheme());

        colorPreviewGrid = new Composite(group, SWT.NONE);
        colorPreviewGrid.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, true, false, 2, 1));
        colorPreviewGrid.setLayout(new GridLayout(1, false));

        Composite fontRow = new Composite(group, SWT.NONE);
        fontRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        GridLayout fontLayout = new GridLayout(1, false);
        fontLayout.marginWidth = 0;
        fontLayout.marginHeight = 0;
        fontLayout.horizontalSpacing = COMPACT_SPACING;
        fontRow.setLayout(fontLayout);

        Link openFontSettingsLink = new Link(fontRow, SWT.NONE);
        openFontSettingsLink.setText(Messages.TerminalThemePreference_FontHint
            + "<a>" + Messages.TerminalThemePreference_OpenFontSettings + "</a>");
        openFontSettingsLink.addListener(SWT.Selection, e -> PreferencesUtil
            .createPreferenceDialogOn(getShell(), "org.eclipse.ui.preferencePages.ColorsAndFonts", null, null)
            .open());
    }

    private void refillThemeSelector() {
        themeSelectorCombo.removeAll();
        themeSelectorCombo.add(Messages.TerminalThemePreference_ModeFollowEclipse);
        themeSelectorCombo.setData(Integer.toString(0), FOLLOW_OPTION_ID);

        for (TerminalThemeStore.TerminalTheme theme : themesById.values()) {
            String name = normalizeThemeDisplayName(theme.getName());
            String marker = theme.isBuiltin()
                    ? Messages.TerminalThemePreference_ThemeBuiltin
                    : Messages.TerminalThemePreference_ThemeCustom;
            themeSelectorCombo.add(name + " [" + marker + "]");
            themeSelectorCombo.setData(Integer.toString(themeSelectorCombo.getItemCount() - 1), theme.getId());
        }
        selectTheme(themeSelectorCombo, selectedThemeId);
    }

    private String normalizeThemeDisplayName(String name) {
        if (name == null) {
            return "<Unnamed>";
        }
        return name.trim();
    }

    private void selectTheme(Combo combo, String themeId) {
        if (combo == null || combo.isDisposed() || combo.getItemCount() == 0) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object value = combo.getData(Integer.toString(i));
            if (themeId != null && themeId.equals(value)) {
                combo.select(i);
                return;
            }
        }
        combo.select(0);
    }

    private String getSelectedThemeId(Combo combo) {
        int index = combo.getSelectionIndex();
        if (index < 0) {
            return FOLLOW_OPTION_ID;
        }
        Object value = combo.getData(Integer.toString(index));
        return value instanceof String ? (String) value : FOLLOW_OPTION_ID;
    }

    private void loadPreviewColorsFromSelection() {
        TerminalThemeStore.TerminalTheme theme = resolveSelectedThemeForPreview();

        previewColorValues.clear();
        if (theme == null) {
            return;
        }
        for (String key : TerminalThemeStore.getColorKeys()) {
            previewColorValues.put(key, theme.getColor(key));
        }
    }

    private void resetCustomThemeName() {
        if (customThemeNameText == null || customThemeNameText.isDisposed()) {
            return;
        }
        TerminalThemeStore.TerminalTheme base = resolveSelectedThemeForPreview();
        String name = base == null ? "Custom Theme" : normalizeThemeDisplayName(base.getName()) + " Copy";
        customThemeNameText.setText(name);
    }

    private void saveCurrentAsCustomTheme() {
        String name = customThemeNameText == null ? "" : customThemeNameText.getText().trim();
        if (name.isEmpty()) {
            MessageDialog.openWarning(getShell(), Messages.TerminalThemePreference_InvalidColorTitle,
                    Messages.TerminalThemePreference_InvalidNameMessage);
            return;
        }
        for (TerminalThemeStore.TerminalTheme theme : themesById.values()) {
            if (theme.getName().equalsIgnoreCase(name)) {
                MessageDialog.openWarning(getShell(), Messages.TerminalThemePreference_DuplicateNameTitle,
                        Messages.TerminalThemePreference_DuplicateNameMessage);
                return;
            }
        }
        for (Map.Entry<String, String> entry : previewColorValues.entrySet()) {
            if (!TerminalThemeStore.isValidHexColor(entry.getValue())) {
                MessageDialog.openWarning(getShell(), Messages.TerminalThemePreference_InvalidColorTitle,
                        Messages.TerminalThemePreference_InvalidColorMessage + " " + entry.getKey());
                return;
            }
        }

        TerminalThemeStore.TerminalTheme base = FOLLOW_OPTION_ID.equals(selectedThemeId)
                ? TerminalThemeStore.resolveActiveTheme(getPreferenceStore())
                : themesById.get(selectedThemeId);
        boolean dark = base != null && base.isDark();

        String id = "custom-" + UUID.randomUUID().toString().replace("-", "");
        TerminalThemeStore.TerminalTheme custom = new TerminalThemeStore.TerminalTheme(id, name, dark, false,
                new LinkedHashMap<>(previewColorValues));

        customThemes.add(custom);
        themesById.put(custom.getId(), custom);
        selectedThemeId = custom.getId();

        refillThemeSelector();
        loadPreviewColorsFromSelection();
        resetCustomThemeName();
        refreshPreview();
    }

    private void updateCurrentCustomTheme() {
        if (!isSelectedCustomTheme()) {
            MessageDialog.openInformation(getShell(), Messages.TerminalThemePreference_InvalidColorTitle,
                    Messages.TerminalThemePreference_OnlyCustomEditable);
            return;
        }

        String name = customThemeNameText == null ? "" : customThemeNameText.getText().trim();
        if (name.isEmpty()) {
            MessageDialog.openWarning(getShell(), Messages.TerminalThemePreference_InvalidColorTitle,
                    Messages.TerminalThemePreference_InvalidNameMessage);
            return;
        }

        TerminalThemeStore.TerminalTheme current = themesById.get(selectedThemeId);
        if (current == null) {
            return;
        }

        for (TerminalThemeStore.TerminalTheme theme : themesById.values()) {
            if (theme == null || theme.getId().equals(current.getId())) {
                continue;
            }
            if (theme.getName().equalsIgnoreCase(name)) {
                MessageDialog.openWarning(getShell(), Messages.TerminalThemePreference_DuplicateNameTitle,
                        Messages.TerminalThemePreference_DuplicateNameMessage);
                return;
            }
        }

        for (Map.Entry<String, String> entry : previewColorValues.entrySet()) {
            if (!TerminalThemeStore.isValidHexColor(entry.getValue())) {
                MessageDialog.openWarning(getShell(), Messages.TerminalThemePreference_InvalidColorTitle,
                        Messages.TerminalThemePreference_InvalidColorMessage + " " + entry.getKey());
                return;
            }
        }

        TerminalThemeStore.TerminalTheme updated = new TerminalThemeStore.TerminalTheme(current.getId(), name,
                current.isDark(), false, new LinkedHashMap<>(previewColorValues));

        for (int index = 0; index < customThemes.size(); index++) {
            TerminalThemeStore.TerminalTheme theme = customThemes.get(index);
            if (theme != null && current.getId().equals(theme.getId())) {
                customThemes.set(index, updated);
                break;
            }
        }
        themesById.put(updated.getId(), updated);
        selectedThemeId = updated.getId();

        refillThemeSelector();
        loadPreviewColorsFromSelection();
        resetCustomThemeName();
        refreshPreview();
    }

    private void deleteCurrentCustomTheme() {
        if (!isSelectedCustomTheme()) {
            MessageDialog.openInformation(getShell(), Messages.TerminalThemePreference_InvalidColorTitle,
                    Messages.TerminalThemePreference_OnlyCustomDeletable);
            return;
        }
        TerminalThemeStore.TerminalTheme current = themesById.get(selectedThemeId);
        if (current == null) {
            return;
        }

        boolean confirm = MessageDialog.openQuestion(getShell(), Messages.TerminalPreference_RemoveConfirmTitle,
            NLS.bind(Messages.TerminalThemePreference_DeleteConfirmMessage, current.getName()));
        if (!confirm) {
            return;
        }

        customThemes.removeIf(theme -> theme != null && current.getId().equals(theme.getId()));
        themesById.remove(current.getId());
        selectedThemeId = FOLLOW_OPTION_ID;

        refillThemeSelector();
        loadPreviewColorsFromSelection();
        resetCustomThemeName();
        refreshPreview();
    }

    private boolean isSelectedCustomTheme() {
        if (selectedThemeId == null || FOLLOW_OPTION_ID.equals(selectedThemeId)) {
            return false;
        }
        TerminalThemeStore.TerminalTheme theme = themesById.get(selectedThemeId);
        return theme != null && !theme.isBuiltin();
    }

    private TerminalThemeStore.TerminalTheme resolveSelectedThemeForPreview() {
        TerminalThemeStore.TerminalTheme theme;
        if (FOLLOW_OPTION_ID.equals(selectedThemeId)) {
            theme = TerminalThemeStore.resolveActiveTheme(getPreferenceStore());
        } else {
            theme = themesById.get(selectedThemeId);
        }
        if (theme == null) {
            return themesById.get("dark-plus");
        }
        return theme;
    }

    private void refreshPreview() {
        disposePreviewButtonColors();
        previewColorButtons.clear();
        for (Control child : colorPreviewGrid.getChildren()) {
            child.dispose();
        }
        if (previewColorValues.isEmpty()) {
            colorPreviewGrid.layout(true, true);
            return;
        }

        Group generalGroup = new Group(colorPreviewGrid, SWT.NONE);
        generalGroup.setText(Messages.TerminalThemePreference_GeneralColors);
        generalGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        generalGroup.setLayout(createCompactGroupLayout(4));

        createGeneralPreviewItem(generalGroup, Messages.TerminalThemePreference_TextColor, "foreground");
        createGeneralPreviewItem(generalGroup, Messages.TerminalThemePreference_BackgroundColor, "background");
        createGeneralPreviewItem(generalGroup, Messages.TerminalThemePreference_SelectionColor, "selectionBackground");
        createGeneralPreviewItem(generalGroup, Messages.TerminalThemePreference_SelectedTextColor, "brightWhite");
        createGeneralPreviewItem(generalGroup, Messages.TerminalThemePreference_CursorColor, "cursor");
        createGeneralPreviewItem(generalGroup, Messages.TerminalThemePreference_CursorAccentColor, "cursorAccent");

        Group paletteGroup = new Group(colorPreviewGrid, SWT.NONE);
        paletteGroup.setText(Messages.TerminalThemePreference_PaletteColors);
        paletteGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        paletteGroup.setLayout(createCompactGroupLayout(8));

        String[] row1 = new String[] { "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white" };
        String[] row2 = new String[] { "brightBlack", "brightRed", "brightGreen", "brightYellow", "brightBlue",
                "brightMagenta", "brightCyan", "brightWhite" };
        for (String key : row1) {
            createPalettePreviewItem(paletteGroup, key);
        }
        for (String key : row2) {
            createPalettePreviewItem(paletteGroup, key);
        }

        colorPreviewGrid.layout(true, true);
    }

    private void createGeneralPreviewItem(Composite parent, String label, String key) {
        Label text = new Label(parent, SWT.NONE);
        text.setText(label);
        text.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Canvas swatch = new Canvas(parent, SWT.NONE);
        GridData swatchData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        swatchData.widthHint = SWATCH_WIDTH;
        swatchData.heightHint = SWATCH_HEIGHT;
        swatch.setLayoutData(swatchData);
        swatch.setData("colorKey", key);
        swatch.addListener(SWT.MouseDown, event -> openPreviewColorDialog(swatch));
        swatch.addPaintListener(event -> paintSwatch(event.gc, swatch));
        previewColorButtons.put(key, swatch);
        applyPreviewSwatchColor(swatch, key);
    }

    private void createPalettePreviewItem(Composite parent, String key) {
        Canvas swatch = new Canvas(parent, SWT.NONE);
        GridData swatchData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        swatchData.widthHint = SWATCH_WIDTH;
        swatchData.heightHint = SWATCH_HEIGHT;
        swatch.setLayoutData(swatchData);
        swatch.setData("colorKey", key);
        swatch.addListener(SWT.MouseDown, event -> openPreviewColorDialog(swatch));
        swatch.addPaintListener(event -> paintSwatch(event.gc, swatch));
        previewColorButtons.put(key, swatch);
        applyPreviewSwatchColor(swatch, key);
    }

    private void paintSwatch(org.eclipse.swt.graphics.GC gc, Canvas swatch) {
        if (gc == null || swatch == null || swatch.isDisposed()) {
            return;
        }
        org.eclipse.swt.graphics.Rectangle area = swatch.getClientArea();
        if (area.width <= 0 || area.height <= 0) {
            return;
        }
        Color fill = (Color) swatch.getData("swatchColor");
        if (fill != null && !fill.isDisposed()) {
            gc.setBackground(fill);
            gc.fillRectangle(1, 1, Math.max(0, area.width - 2), Math.max(0, area.height - 2));
        }
        gc.setForeground(swatch.getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
        gc.drawRectangle(0, 0, Math.max(0, area.width - 1), Math.max(0, area.height - 1));
    }

    private void applyPreviewSwatchColor(Canvas swatch, String key) {
        String colorHex = previewColorValues.get(key);
        Color color = toColor(colorHex);
        if (color != null) {
            swatch.setData("swatchColor", color);
            swatch.setToolTipText(key + " " + colorHex);
            swatch.redraw();
        }
    }

    private void openPreviewColorDialog(Canvas swatch) {
        if (swatch == null || swatch.isDisposed()) {
            return;
        }
        Object keyObject = swatch.getData("colorKey");
        if (!(keyObject instanceof String)) {
            return;
        }
        String key = (String) keyObject;

        ColorDialog dialog = new ColorDialog(getShell());
        RGB current = toRgb(previewColorValues.get(key));
        if (current != null) {
            dialog.setRGB(current);
        }
        RGB selected = dialog.open();
        if (selected == null) {
            return;
        }
        String hex = String.format("#%02x%02x%02x", selected.red, selected.green, selected.blue);
        previewColorValues.put(key, hex);
        refreshPreview();
    }

    private GridLayout createCompactGroupLayout(int columns) {
        GridLayout layout = new GridLayout(columns, false);
        layout.marginWidth = COMPACT_MARGIN;
        layout.marginHeight = COMPACT_MARGIN;
        layout.horizontalSpacing = COMPACT_SPACING;
        layout.verticalSpacing = COMPACT_SPACING;
        return layout;
    }

    private RGB toRgb(String hex) {
        if (!TerminalThemeStore.isValidHexColor(hex)) {
            return null;
        }
        String raw = hex.startsWith("#") ? hex.substring(1) : hex;
        if (raw.length() == 3) {
            raw = "" + raw.charAt(0) + raw.charAt(0) + raw.charAt(1) + raw.charAt(1) + raw.charAt(2)
                    + raw.charAt(2);
        }
        int r = Integer.parseInt(raw.substring(0, 2), 16);
        int g = Integer.parseInt(raw.substring(2, 4), 16);
        int b = Integer.parseInt(raw.substring(4, 6), 16);
        return new RGB(r, g, b);
    }

    private Color toColor(String hex) {
        RGB rgb = toRgb(hex);
        if (rgb == null) {
            return null;
        }
        return new Color(getShell().getDisplay(), rgb);
    }

    private void disposePreviewButtonColors() {
        for (Canvas swatch : previewColorButtons.values()) {
            if (swatch == null || swatch.isDisposed()) {
                continue;
            }
            Color color = (Color) swatch.getData("swatchColor");
            if (color != null && !color.isDisposed()) {
                color.dispose();
            }
        }
    }

    @Override
    public boolean performOk() {
        if (FOLLOW_OPTION_ID.equals(selectedThemeId)) {
            getPreferenceStore().setValue(Activator.PREF_THEME_MODE, TerminalThemeStore.MODE_FOLLOW);
            getPreferenceStore().setValue(Activator.PREF_THEME_LIGHT, "light-plus");
            getPreferenceStore().setValue(Activator.PREF_THEME_DARK, "dark-plus");
        } else {
            getPreferenceStore().setValue(Activator.PREF_THEME_MODE, TerminalThemeStore.MODE_FIXED);
            getPreferenceStore().setValue(Activator.PREF_THEME_FIXED, selectedThemeId);
        }
        TerminalThemeStore.saveCustomThemes(getPreferenceStore(), customThemes);
        return super.performOk();
    }

    @Override
    protected void performDefaults() {
        selectedThemeId = FOLLOW_OPTION_ID;
        themesById.clear();
        for (TerminalThemeStore.TerminalTheme theme : TerminalThemeStore.getBuiltinThemes()) {
            themesById.put(theme.getId(), theme);
        }
        customThemes.clear();
        refillThemeSelector();
        loadPreviewColorsFromSelection();
        resetCustomThemeName();
        refreshPreview();
        super.performDefaults();
    }

    @Override
    public void dispose() {
        disposePreviewButtonColors();
        super.dispose();
    }
}