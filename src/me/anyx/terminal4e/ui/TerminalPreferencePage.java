package me.anyx.terminal4e.ui;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import me.anyx.terminal4e.Activator;
import me.anyx.terminal4e.Messages;
import me.anyx.terminal4e.NLS;
import me.anyx.terminal4e.core.ShellDescriptor;
import me.anyx.terminal4e.core.ShellDetector;

public class TerminalPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	private List<ShellConfigStore.ShellConfig> shellConfigs;
	private TableViewer shellViewer;
	private Button editButton;
	private Button removeButton;
	private Button moveUpButton;
	private Button moveDownButton;
	private String[][] languageEntries;
	private String[][] charsetEntries;
	private Composite editorParent;
	private String defaultShellId;

	public TerminalPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription(Messages.TerminalPreference_Description);
	}

	@Override
	public void init(IWorkbench workbench) {
		// no-op
	}

	@Override
	protected void createFieldEditors() {
		editorParent = getFieldEditorParent();
		languageEntries = buildLanguageEntries();
		addField(new SimpleComboFieldEditor(Activator.PREF_LANGUAGE, Messages.TerminalPreference_Language,
				languageEntries, Messages.TerminalPreference_LanguageRestartHint, editorParent));
		charsetEntries = buildCharsetEntries();
		addField(new SimpleComboFieldEditor(Activator.PREF_CHARSET, Messages.TerminalPreference_DefaultCharset,
				charsetEntries, true, editorParent));
		addField(new BooleanFieldEditor(Activator.PREF_CONFIRM_MULTILINE_PASTE,
				Messages.TerminalPreference_ConfirmMultilinePaste, editorParent));
		addField(new BooleanFieldEditor(Activator.PREF_CONFIRM_CLOSE_WITH_PROCESS,
				Messages.TerminalPreference_ConfirmCloseWithRunning, editorParent));
		addField(new BooleanFieldEditor(Activator.PREF_AUTO_CLOSE_ON_EXIT,
				Messages.TerminalPreference_AutoCloseOnExit, editorParent));
		addField(new BooleanFieldEditor(Activator.PREF_AUTO_OPEN_PROJECT_TERMINAL,
				Messages.TerminalPreference_AutoOpenProjectTerminal, editorParent));
		addField(new BooleanFieldEditor(Activator.PREF_RESTORE_SESSIONS,
				Messages.TerminalPreference_RestoreSessionsOnStartup, editorParent));
		createShellSection(editorParent);
	}

	@Override
	public boolean performOk() {
		String previousLanguage = nullToEmpty(getPreferenceStore().getString(Activator.PREF_LANGUAGE));
		ShellConfigStore.saveShellConfigs(getPreferenceStore(), shellConfigs);
		getPreferenceStore().setValue(Activator.PREF_DEFAULT_SHELL, nullToEmpty(defaultShellId));
		boolean ok = super.performOk();
		if (!ok) {
			return false;
		}
		String currentLanguage = nullToEmpty(getPreferenceStore().getString(Activator.PREF_LANGUAGE));
		if (!previousLanguage.equals(currentLanguage)) {
			boolean restartNow = MessageDialog.openQuestion(getShell(),
					Messages.TerminalPreference_LanguageRestartTitle,
					Messages.TerminalPreference_LanguageRestartMessage);
			if (restartNow) {
				PlatformUI.getWorkbench().restart();
			}
		}
		return true;
	}

	@Override
	protected void performDefaults() {
		shellConfigs = new ArrayList<>();
		defaultShellId = "";
		shellViewer.setInput(shellConfigs);
		updateButtons();
		super.performDefaults();
	}

	private void createShellSection(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText(Messages.TerminalPreference_ShellGroupTitle);
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		group.setLayout(new org.eclipse.swt.layout.GridLayout(2, false));

		shellViewer = new TableViewer(group, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.CHECK);
		Table table = shellViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 180;
		table.setLayoutData(tableData);

		createColumns();
		shellViewer.setContentProvider(ArrayContentProvider.getInstance());

		Composite buttons = new Composite(group, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		buttons.setLayout(new org.eclipse.swt.layout.GridLayout(1, false));

		Button addButton = new Button(buttons, SWT.PUSH);
		addButton.setText(Messages.TerminalPreference_Add);
		GridData addData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		addData.widthHint = 80;
		addButton.setLayoutData(addData);
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleAddShell();
			}
		});

		Button autoDetectButton = new Button(buttons, SWT.PUSH);
		autoDetectButton.setText(Messages.TerminalPreference_AutoDetect);
		GridData autoDetectData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		autoDetectData.widthHint = 80;
		autoDetectButton.setLayoutData(autoDetectData);
		autoDetectButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleAutoDetectShells();
			}
		});

		editButton = new Button(buttons, SWT.PUSH);
		editButton.setText(Messages.TerminalPreference_Edit);
		GridData editData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		editData.widthHint = 80;
		editButton.setLayoutData(editData);
		editButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleEditShell();
			}
		});

		removeButton = new Button(buttons, SWT.PUSH);
		removeButton.setText(Messages.TerminalPreference_Remove);
		GridData removeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		removeData.widthHint = 80;
		removeButton.setLayoutData(removeData);
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleRemoveShell();
			}
		});

		moveUpButton = new Button(buttons, SWT.PUSH);
		moveUpButton.setText(Messages.TerminalPreference_MoveUp);
		GridData moveUpData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		moveUpData.widthHint = 80;
		moveUpButton.setLayoutData(moveUpData);
		moveUpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleMoveUp();
			}
		});

		moveDownButton = new Button(buttons, SWT.PUSH);
		moveDownButton.setText(Messages.TerminalPreference_MoveDown);
		GridData moveDownData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		moveDownData.widthHint = 80;
		moveDownButton.setLayoutData(moveDownData);
		moveDownButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleMoveDown();
			}
		});


		shellViewer.addSelectionChangedListener(event -> updateButtons());
		shellViewer.addDoubleClickListener(event -> handleEditShell());
		table.addListener(SWT.Selection, event -> {
			if (event.detail != SWT.CHECK) {
				return;
			}
			TableItem item = (TableItem) event.item;
			ShellConfigStore.ShellConfig config = (ShellConfigStore.ShellConfig) item.getData();
			if (config != null) {
				defaultShellId = config.getId();
				shellViewer.refresh();
				updateDefaultChecks();
				updateButtons();
			}
		});

		loadShellConfigs();
		shellViewer.setInput(shellConfigs);
		updateDefaultChecks();
		updateButtons();
	}

	private void createColumns() {
		createColumn(Messages.TerminalPreference_DefaultColumn, 60, shell -> "");
		createNameColumn(Messages.TerminalPreference_ShellName, 140);
		createColumn(Messages.TerminalPreference_ShellCommand, 220,
				shell -> nullToEmpty(shell.getCommand()));
		createColumn(Messages.TerminalPreference_ShellArgs, 160,
				shell -> nullToEmpty(shell.getArgs()));
		createColumn(Messages.TerminalPreference_ShellCharset, 120,
				shell -> nullToEmpty(shell.getCharset()));
	}

	private void createNameColumn(String title, int width) {
		TableViewerColumn column = new TableViewerColumn(shellViewer, SWT.NONE);
		TableColumn tableColumn = column.getColumn();
		tableColumn.setText(title);
		tableColumn.setWidth(width);
		column.setLabelProvider(new org.eclipse.jface.viewers.ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return formatShellName((ShellConfigStore.ShellConfig) element);
			}

			@Override
			public org.eclipse.swt.graphics.Image getImage(Object element) {
				ShellConfigStore.ShellConfig shell = (ShellConfigStore.ShellConfig) element;
				return ShellIconProvider.getShellImage(shell.getCommand(), shell.getIconPath());
			}

			@Override
			public org.eclipse.swt.graphics.Font getFont(Object element) {
				ShellConfigStore.ShellConfig shell = (ShellConfigStore.ShellConfig) element;
				if (shell.getId().equals(nullToEmpty(defaultShellId))) {
					return JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
				}
				return super.getFont(element);
			}
		});
	}

	private void createColumn(String title, int width, java.util.function.Function<ShellConfigStore.ShellConfig, String> extractor) {
		TableViewerColumn column = new TableViewerColumn(shellViewer, SWT.NONE);
		TableColumn tableColumn = column.getColumn();
		tableColumn.setText(title);
		tableColumn.setWidth(width);
		column.setLabelProvider(new org.eclipse.jface.viewers.ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return extractor.apply((ShellConfigStore.ShellConfig) element);
			}

			@Override
			public org.eclipse.swt.graphics.Font getFont(Object element) {
				ShellConfigStore.ShellConfig shell = (ShellConfigStore.ShellConfig) element;
				if (shell.getId().equals(nullToEmpty(defaultShellId))) {
					return JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
				}
				return super.getFont(element);
			}
		});
	}

	private void loadShellConfigs() {
		shellConfigs = new ArrayList<>(ShellConfigStore.loadShellConfigs(getPreferenceStore()));
		defaultShellId = getPreferenceStore().getString(Activator.PREF_DEFAULT_SHELL);
		if (!shellConfigs.isEmpty()) {
			ensureDefaultShell();
			return;
		}
		List<ShellDescriptor> detected = ShellDetector.detect();
		String defaultCharset = getPreferenceStore().getString(Activator.PREF_CHARSET);
		for (ShellDescriptor shell : detected) {
			shellConfigs.add(new ShellConfigStore.ShellConfig(shell.getId(), shell.getLabel(), shell.getCommand(),
					joinArgs(shell.getArgs()), defaultCharset, nullToEmpty(shell.getIconPath())));
		}
		ensureDefaultShell();
	}

	private void handleAddShell() {
		ShellEditDialog dialog = new ShellEditDialog(editorParent.getShell(), null, charsetEntries);
		if (dialog.open() == TitleAreaDialog.OK) {
			ShellConfigStore.ShellConfig config = dialog.getResult();
			if (config != null) {
				shellConfigs.add(config);
				if (defaultShellId == null || defaultShellId.isEmpty()) {
					defaultShellId = config.getId();
				}
				shellViewer.refresh();
				updateDefaultChecks();
			}
		}
	}

	private void handleEditShell() {
		ShellConfigStore.ShellConfig selected = getSelectedShell();
		if (selected == null) {
			return;
		}
		ShellEditDialog dialog = new ShellEditDialog(editorParent.getShell(), selected, charsetEntries);
		if (dialog.open() == TitleAreaDialog.OK) {
			shellViewer.refresh();
			updateDefaultChecks();
		}
	}

	private void handleRemoveShell() {
		ShellConfigStore.ShellConfig selected = getSelectedShell();
		if (selected == null) {
			return;
		}
		String label = formatShellName(selected);
		boolean confirmed = MessageDialog.openConfirm(editorParent.getShell(),
				Messages.TerminalPreference_RemoveConfirmTitle,
				NLS.bind(Messages.TerminalPreference_RemoveConfirmMessage, label));
		if (!confirmed) {
			return;
		}
		int index = shellConfigs.indexOf(selected);
		shellConfigs.remove(selected);
		if (selected.getId().equals(defaultShellId)) {
			if (!shellConfigs.isEmpty()) {
				int newIndex = Math.min(index, shellConfigs.size() - 1);
				defaultShellId = shellConfigs.get(newIndex).getId();
			} else {
				defaultShellId = "";
			}
		}
		shellViewer.refresh();
		updateDefaultChecks();
		updateButtons();
	}

	private void handleMoveUp() {
		int index = getSelectedIndex();
		if (index <= 0) {
			return;
		}
		ShellConfigStore.ShellConfig item = shellConfigs.remove(index);
		shellConfigs.add(index - 1, item);
		shellViewer.refresh();
		updateDefaultChecks();
		shellViewer.setSelection(new StructuredSelection(item), true);
		updateButtons();
	}

	private void handleMoveDown() {
		int index = getSelectedIndex();
		if (index < 0 || index >= shellConfigs.size() - 1) {
			return;
		}
		ShellConfigStore.ShellConfig item = shellConfigs.remove(index);
		shellConfigs.add(index + 1, item);
		shellViewer.refresh();
		updateDefaultChecks();
		shellViewer.setSelection(new StructuredSelection(item), true);
		updateButtons();
	}

	private void handleAutoDetectShells() {
		List<ShellDescriptor> detected = ShellDetector.detect();
		if (detected == null || detected.isEmpty()) {
			return;
		}
		String defaultCharset = getPreferenceStore().getString(Activator.PREF_CHARSET);
		Set<String> existingKeys = new LinkedHashSet<>();
		Set<String> existingIds = new LinkedHashSet<>();
		for (ShellConfigStore.ShellConfig config : shellConfigs) {
			if (config == null) {
				continue;
			}
			existingIds.add(nullToEmpty(config.getId()));
			existingKeys.add(buildShellKey(config.getCommand(), config.getArgs()));
		}
		boolean added = false;
		for (ShellDescriptor shell : detected) {
			if (shell == null) {
				continue;
			}
			String command = shell.getCommand();
			String args = joinArgs(shell.getArgs());
			String key = buildShellKey(command, args);
			if (existingKeys.contains(key)) {
				continue;
			}
			String id = resolveShellId(shell.getId(), existingIds);
			String charset = nullToEmpty(shell.getCharsetName());
			if (charset.isEmpty()) {
				charset = defaultCharset;
			}
			shellConfigs.add(new ShellConfigStore.ShellConfig(id, shell.getLabel(), command, args, charset,
					nullToEmpty(shell.getIconPath())));
			existingKeys.add(key);
			existingIds.add(id);
			added = true;
		}
		if (added) {
			ensureDefaultShell();
			shellViewer.refresh();
			updateDefaultChecks();
			updateButtons();
		}
	}


	private ShellConfigStore.ShellConfig getSelectedShell() {
		IStructuredSelection selection = shellViewer.getStructuredSelection();
		if (selection == null || selection.isEmpty()) {
			return null;
		}
		return (ShellConfigStore.ShellConfig) selection.getFirstElement();
	}

	private void updateButtons() {
		ShellConfigStore.ShellConfig selected = getSelectedShell();
		boolean hasSelection = selected != null;
		if (editButton != null) {
			editButton.setEnabled(hasSelection);
		}
		if (removeButton != null) {
			removeButton.setEnabled(hasSelection);
		}
		int index = getSelectedIndex();
		if (moveUpButton != null) {
			moveUpButton.setEnabled(index > 0);
		}
		if (moveDownButton != null) {
			moveDownButton.setEnabled(index >= 0 && index < shellConfigs.size() - 1);
		}
	}

	private int getSelectedIndex() {
		ShellConfigStore.ShellConfig selected = getSelectedShell();
		if (selected == null) {
			return -1;
		}
		return shellConfigs.indexOf(selected);
	}

	private void ensureDefaultShell() {
		if (shellConfigs.isEmpty()) {
			return;
		}
		if (defaultShellId == null || defaultShellId.isEmpty()) {
			defaultShellId = shellConfigs.get(0).getId();
			return;
		}
		for (ShellConfigStore.ShellConfig config : shellConfigs) {
			if (defaultShellId.equals(config.getId())) {
				return;
			}
		}
		defaultShellId = shellConfigs.get(0).getId();
	}

	private String formatShellName(ShellConfigStore.ShellConfig shell) {
		return nullToEmpty(shell.getLabel());
	}

	private void updateDefaultChecks() {
		Table table = shellViewer.getTable();
		String currentDefaultId = nullToEmpty(defaultShellId);
		for (TableItem item : table.getItems()) {
			ShellConfigStore.ShellConfig config = (ShellConfigStore.ShellConfig) item.getData();
			boolean checked = config != null && config.getId().equals(currentDefaultId);
			item.setChecked(checked);
		}
	}

	private String joinArgs(List<String> args) {
		if (args == null || args.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String arg : args) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			if (arg.contains(" ")) {
				sb.append('"').append(arg).append('"');
			} else {
				sb.append(arg);
			}
		}
		return sb.toString();
	}

	private String buildShellKey(String command, String args) {
		String normalizedCommand = nullToEmpty(command).trim().toLowerCase();
		String normalizedArgs = joinArgs(parseArgs(args)).trim();
		return normalizedCommand + "\n" + normalizedArgs;
	}

	private List<String> parseArgs(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return java.util.Collections.emptyList();
		}
		List<String> args = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '"') {
				quoted = !quoted;
				continue;
			}
			if (Character.isWhitespace(c) && !quoted) {
				if (current.length() > 0) {
					args.add(current.toString());
					current.setLength(0);
				}
				continue;
			}
			current.append(c);
		}
		if (current.length() > 0) {
			args.add(current.toString());
		}
		return args;
	}

	private String resolveShellId(String candidate, Set<String> existingIds) {
		String cleaned = nullToEmpty(candidate);
		if (!cleaned.isEmpty() && !existingIds.contains(cleaned)) {
			return cleaned;
		}
		return UUID.randomUUID().toString();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String[][] buildCharsetEntries() {
		List<String[]> entries = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		Charset defaultCharset = Charset.defaultCharset();
		entries.add(new String[] { NLS.bind(Messages.Charset_Default, defaultCharset.name()),
				defaultCharset.name() });
		seen.add(defaultCharset.name());

		String[] candidates = new String[] { "UTF-8", "GBK", "GB18030", "Windows-1252", "UTF-16LE",
				"UTF-16BE", "Shift_JIS", "EUC-KR" };
		for (String name : candidates) {
			if (!Charset.isSupported(name)) {
				continue;
			}
			Charset charset = Charset.forName(name);
			if (seen.add(charset.name())) {
				entries.add(new String[] { charset.name(), charset.name() });
			}
		}
		return entries.toArray(new String[0][0]);
	}

	private String[][] buildLanguageEntries() {
		List<String[]> entries = new ArrayList<>();
		entries.add(new String[] { Messages.TerminalPreference_LanguageAuto, "auto" });
		entries.add(new String[] { "English", "en" });
		entries.add(new String[] { "简体中文", "zh_CN" });
		return entries.toArray(new String[0][0]);
	}

	private static final class ShellEditDialog extends TitleAreaDialog {
		private final ShellConfigStore.ShellConfig original;
		private final String[][] charsetEntries;
		private Text nameText;
		private Text commandText;
		private Text argsText;
		private Text iconText;
		private Button iconBrowseButton;
		private Button iconClearButton;
		private Combo charsetCombo;
		private ShellConfigStore.ShellConfig result;

		ShellEditDialog(Shell parentShell, ShellConfigStore.ShellConfig original, String[][] charsetEntries) {
			super(parentShell);
			this.original = original;
			this.charsetEntries = charsetEntries == null ? new String[0][0] : charsetEntries;
		}

		@Override
		public void create() {
			super.create();
			if (original == null) {
				setTitle(Messages.TerminalPreference_DialogAddTitle);
			} else {
				setTitle(Messages.TerminalPreference_DialogEditTitle);
			}
			setMessage(Messages.TerminalPreference_DialogMessage);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayout(new org.eclipse.swt.layout.GridLayout(2, false));
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Label nameLabel = new Label(container, SWT.NONE);
			nameLabel.setText(Messages.TerminalPreference_DialogName);
			nameText = new Text(container, SWT.BORDER);
			nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label commandLabel = new Label(container, SWT.NONE);
			commandLabel.setText(Messages.TerminalPreference_DialogCommand);
			commandText = new Text(container, SWT.BORDER);
			commandText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label argsLabel = new Label(container, SWT.NONE);
			argsLabel.setText(Messages.TerminalPreference_DialogArgs);
			argsText = new Text(container, SWT.BORDER);
			argsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label iconLabel = new Label(container, SWT.NONE);
			iconLabel.setText(Messages.TerminalPreference_DialogIcon);
			Composite iconRow = new Composite(container, SWT.NONE);
			iconRow.setLayout(new org.eclipse.swt.layout.GridLayout(3, false));
			iconRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			iconText = new Text(iconRow, SWT.BORDER | SWT.READ_ONLY);
			iconText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			iconBrowseButton = new Button(iconRow, SWT.PUSH);
			iconBrowseButton.setText(Messages.TerminalPreference_DialogIconBrowse);
			iconBrowseButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					openIconFileDialog();
				}
			});

			iconClearButton = new Button(iconRow, SWT.PUSH);
			iconClearButton.setText(Messages.TerminalPreference_DialogIconClear);
			iconClearButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					iconText.setText("");
				}
			});

			Label charsetLabel = new Label(container, SWT.NONE);
			charsetLabel.setText(Messages.TerminalPreference_DialogCharset);
			charsetCombo = new Combo(container, SWT.NONE);
			charsetCombo.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			for (String[] entry : charsetEntries) {
				if (entry != null && entry.length > 0) {
					charsetCombo.add(entry[0]);
				}
			}

			if (original != null) {
				nameText.setText(nullToEmpty(original.getLabel()));
				commandText.setText(nullToEmpty(original.getCommand()));
				argsText.setText(nullToEmpty(original.getArgs()));
				iconText.setText(nullToEmpty(original.getIconPath()));
				selectCharset(original.getCharset());
			} else if (charsetCombo.getItemCount() > 0) {
				charsetCombo.select(0);
			}

			return area;
		}

		@Override
		protected void okPressed() {
			String name = nameText.getText().trim();
			String command = commandText.getText().trim();
			String args = argsText.getText().trim();
			String iconPath = iconText.getText().trim();
			String charset = resolveSelectedCharset();

			if (name.isEmpty()) {
				setErrorMessage(Messages.TerminalPreference_DialogInvalidName);
				return;
			}
			if (command.isEmpty()) {
				setErrorMessage(Messages.TerminalPreference_DialogInvalidCommand);
				return;
			}
			if (charset == null || charset.isEmpty()) {
				setErrorMessage(Messages.TerminalPreference_DialogInvalidCharset);
				return;
			}

			if (original == null) {
				result = new ShellConfigStore.ShellConfig(UUID.randomUUID().toString(), name, command, args,
						charset, iconPath);
			} else {
				original.setLabel(name);
				original.setCommand(command);
				original.setArgs(args);
				original.setCharset(charset);
				original.setIconPath(iconPath);
				result = original;
			}
			super.okPressed();
		}

		ShellConfigStore.ShellConfig getResult() {
			return result;
		}

		private void selectCharset(String value) {
			if (value == null) {
				return;
			}
			for (int i = 0; i < charsetEntries.length; i++) {
				String[] entry = charsetEntries[i];
				if (entry != null && entry.length >= 2 && value.equals(entry[1])) {
					charsetCombo.select(i);
					return;
				}
			}
		}

		private String resolveSelectedCharset() {
			int index = charsetCombo.getSelectionIndex();
			if (index < 0 || index >= charsetEntries.length) {
				return "";
			}
			String[] entry = charsetEntries[index];
			return entry != null && entry.length >= 2 ? entry[1] : "";
		}

		private void openIconFileDialog() {
			FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
			dialog.setText(Messages.TerminalPreference_DialogIconBrowse);
			dialog.setFilterExtensions(new String[] { "*.*", "*.exe", "*.cmd", "*.bat", "*.lnk", "*.ico", "*.png",
					"*.jpg", "*.jpeg" });
			dialog.setFilterNames(new String[] { "All Files", "Executable Files", "Command Files", "Batch Files",
					"Shortcut Files", "Icon Files", "PNG Images", "JPEG Images", "JPEG Images" });
			String initialPath = resolveInitialIconDirectory();
			if (initialPath != null && !initialPath.trim().isEmpty()) {
				dialog.setFilterPath(initialPath);
			}
			String selected = dialog.open();
			if (selected != null && !selected.trim().isEmpty()) {
				iconText.setText(selected.trim());
			}
		}

		private String resolveInitialIconDirectory() {
			String iconPath = iconText.getText().trim();
			java.nio.file.Path icon = resolvePath(iconPath);
			if (icon != null) {
				java.nio.file.Path parent = icon.getParent();
				return parent == null ? null : parent.toString();
			}
			String command = commandText.getText().trim();
			java.nio.file.Path commandPath = resolvePath(command);
			if (commandPath != null) {
				java.nio.file.Path parent = commandPath.getParent();
				return parent == null ? null : parent.toString();
			}
			return null;
		}

		private java.nio.file.Path resolvePath(String value) {
			if (value == null || value.trim().isEmpty()) {
				return null;
			}
			String trimmed = value.trim();
			if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
				trimmed = trimmed.substring(1, trimmed.length() - 1);
			}
			try {
				return java.nio.file.Paths.get(trimmed);
			} catch (Exception ex) {
				return null;
			}
		}

		private static String nullToEmpty(String value) {
			return value == null ? "" : value;
		}
	}
}
