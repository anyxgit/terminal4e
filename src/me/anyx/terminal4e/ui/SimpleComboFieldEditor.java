package me.anyx.terminal4e.ui;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class SimpleComboFieldEditor extends FieldEditor {
	private final String[][] entries;
	private final Map<String, String> labelToValue = new HashMap<>();
	private Composite comboContainer;
	private Combo combo;
	private Label label;
	private Label hintLabel;
	private Font hintFont;
	private String hintText;
	private boolean comboEditable = false;

    public SimpleComboFieldEditor(String name, String labelText, String[][] entries, boolean comboEditable, Composite parent) {
        init(name, labelText);
        this.entries = entries == null ? new String[0][0] : entries;
        for (String[] entry : this.entries) {
            if (entry != null && entry.length >= 2) {
                labelToValue.put(entry[0], entry[1]);
            }
        }
        this.comboEditable = comboEditable;
        createControl(parent);
    }

    public SimpleComboFieldEditor(String name, String labelText, String[][] entries, Composite parent) {
        init(name, labelText);
        this.entries = entries == null ? new String[0][0] : entries;
        for (String[] entry : this.entries) {
            if (entry != null && entry.length >= 2) {
                labelToValue.put(entry[0], entry[1]);
            }
        }
        createControl(parent);
    }

	public SimpleComboFieldEditor(String name, String labelText, String[][] entries, String hintText, Composite parent) {
		init(name, labelText);
		this.entries = entries == null ? new String[0][0] : entries;
		for (String[] entry : this.entries) {
			if (entry != null && entry.length >= 2) {
				labelToValue.put(entry[0], entry[1]);
			}
		}
		this.hintText = hintText;
		createControl(parent);
	}

	public SimpleComboFieldEditor(String name, String labelText, String[][] entries, boolean comboEditable, String hintText,
			Composite parent) {
		init(name, labelText);
		this.entries = entries == null ? new String[0][0] : entries;
		for (String[] entry : this.entries) {
			if (entry != null && entry.length >= 2) {
				labelToValue.put(entry[0], entry[1]);
			}
		}
		this.comboEditable = comboEditable;
		this.hintText = hintText;
		createControl(parent);
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		GridData data = (GridData) comboContainer.getLayoutData();
		data.horizontalSpan = Math.max(1, numColumns - 1);
	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		label = new Label(parent, SWT.NONE);
		label.setText(getLabelText());

		comboContainer = new Composite(parent, SWT.NONE);
		comboContainer.setLayout(new GridLayout(hasHint() ? 2 : 1, false));
		comboContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		combo = new Combo(comboContainer, comboEditable ? SWT.NONE : SWT.READ_ONLY);
		combo.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		for (String[] entry : entries) {
			if (entry != null && entry.length >= 1) {
				combo.add(entry[0]);
			}
		}

		if (hasHint()) {
			hintLabel = new Label(comboContainer, SWT.NONE);
			hintLabel.setText("( *" + hintText + " ) ");
			//  字体加粗斜体显示
			FontData[] fontData = hintLabel.getFont().getFontData();
			if (fontData != null && fontData.length > 0) {
				FontData derived = new FontData(fontData[0].getName(),
						Math.max(6, fontData[0].getHeight() - 1),
						fontData[0].getStyle() | SWT.BOLD | SWT.ITALIC);
				hintFont = new Font(hintLabel.getDisplay(), derived);
				hintLabel.setFont(hintFont);
				hintLabel.addDisposeListener(event -> disposeHintFont());
			}
			hintLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		}
	}

	@Override
	public void dispose() {
		disposeHintFont();
		super.dispose();
	}

	@Override
	protected void doLoad() {
		if (combo == null) {
			return;
		}
		String value = getPreferenceStore().getString(getPreferenceName());
		String labelValue = getLabelForValue(value);
		if (labelValue != null) {
			combo.setText(labelValue);
		} else {
			combo.setText(value);
		}
	}

	@Override
	protected void doLoadDefault() {
		if (combo == null) {
			return;
		}
		String value = getPreferenceStore().getDefaultString(getPreferenceName());
		String labelValue = getLabelForValue(value);
		if (labelValue != null) {
			combo.setText(labelValue);
		} else {
			combo.setText(value);
		}
	}

	@Override
	protected void doStore() {
		if (combo == null) {
			return;
		}
		String text = combo.getText();
		String value = labelToValue.getOrDefault(text, text);
		getPreferenceStore().setValue(getPreferenceName(), value);
	}

	@Override
	public int getNumberOfControls() {
		return 2;
	}

	@Override
	public void setEnabled(boolean enabled, Composite parent) {
		super.setEnabled(enabled, parent);
		if (combo != null) {
			combo.setEnabled(enabled);
		}
		if (label != null) {
			label.setEnabled(enabled);
		}
		if (comboContainer != null) {
			comboContainer.setEnabled(enabled);
		}
		if (hintLabel != null) {
			hintLabel.setEnabled(enabled);
		}
	}

	private boolean hasHint() {
		return hintText != null && !hintText.isEmpty();
	}

	private void disposeHintFont() {
		if (hintFont != null && !hintFont.isDisposed()) {
			hintFont.dispose();
		}
		hintFont = null;
	}

	private String getLabelForValue(String value) {
		if (value == null) {
			return null;
		}
		for (String[] entry : entries) {
			if (entry != null && entry.length >= 2 && value.equals(entry[1])) {
				return entry[0];
			}
		}
		return null;
	}
}
