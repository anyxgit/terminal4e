package me.anyx.terminal4e.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;
import org.osgi.framework.Bundle;

import me.anyx.terminal4e.Activator;
import me.anyx.terminal4e.Images;
import me.anyx.terminal4e.Messages;
import me.anyx.terminal4e.NLS;
import me.anyx.terminal4e.core.ShellDescriptor;
import me.anyx.terminal4e.core.ShellDetector;
import me.anyx.terminal4e.core.TerminalSession;

public class TerminalView extends ViewPart {
	public static final String ID = "me.anyx.terminal4e.view";
	private static final char SNAPSHOT_SEP = '|';
	private static final char SNAPSHOT_ESC = '\\';
	private static final char ENV_PAIR_SEP = ';';
	private static final char ENV_KV_SEP = '=';
	private CTabFolder tabFolder;
	private List<ShellDescriptor> shells;
	private int sessionCounter = 1;
	private Path extractedWebRoot;

	@Override
	public void createPartControl(Composite parent) {
		Composite root = new Composite(parent, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(1).spacing(8, 8).applyTo(root);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(root);
		setTitleImage(Images.getImage(Images.ICON_TERMINAL));

		tabFolder = new CTabFolder(root, SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tabFolder);
		tabFolder.setSimple(false);
		tabFolder.setUnselectedCloseVisible(true);
		hookTabFolderEvents();

		loadShells();
		contributeToolbar();
		if (!restoreSessionsIfNeeded()) {
			createNewTab(resolveDefaultShell(), true, resolvePreferredWorkingDirectory());
		}
	}

	private void loadShells() {
		List<ShellConfigStore.ShellConfig> configs = ShellConfigStore.loadShellConfigs(getPreferenceStore());
		shells = ShellConfigStore.toShellDescriptors(configs);
		if (shells == null || shells.isEmpty()) {
			shells = ShellDetector.detect();
		}
	}

	private void contributeToolbar() {
		IToolBarManager manager = getViewSite().getActionBars().getToolBarManager();
		Action newSessionAction = new Action("", IAction.AS_DROP_DOWN_MENU) {
			@Override
			public void run() {
				loadShells();
				ShellDescriptor defaultShell = resolveDefaultShell();
				createNewTab(defaultShell, true, resolvePreferredWorkingDirectory());
			}
		};
		newSessionAction.setImageDescriptor(Images.getImageDescriptor(Images.ICON_TERMINAL));
		newSessionAction.setToolTipText(Messages.TerminalView_NewSession);
		newSessionAction.setMenuCreator(new IMenuCreator() {
			@Override
			public void dispose() {
				// no-op
			}

			@Override
			public Menu getMenu(Control parent) {
				loadShells();
				ShellDescriptor defaultShell = resolveDefaultShell();
				String defaultId = defaultShell == null ? "" : defaultShell.getId();
				MenuManager manager = new MenuManager();
				if (shells == null || shells.isEmpty()) {
					Action emptyAction = new Action(Messages.TerminalView_NoShellsDetected) {
						@Override
						public void run() {
							// no-op
						}
					};
					emptyAction.setEnabled(false);
					manager.add(emptyAction);
				} else {
					for (ShellDescriptor shell : shells) {
						String label = shell.getLabel();
						if (shell.getId() != null && shell.getId().equals(defaultId)) {
							label = label + " (" + Messages.TerminalPreference_DefaultTag + ")";
						}
						Action action = new Action(label) {
							@Override
							public void run() {
								createNewTab(shell, true, resolvePreferredWorkingDirectory());
							}
						};
						Image shellImage = ShellIconProvider.getShellImage(shell.getCommand(), shell.getIconPath());
						if (shellImage != null) {
							action.setImageDescriptor(ImageDescriptor.createFromImage(shellImage));
						} else {
                            action.setImageDescriptor(Images.getImageDescriptor(Images.ICON_TERMINAL));
                        }
						manager.add(action);
					}
				}
				return manager.createContextMenu(parent);
			}

			@Override
			public Menu getMenu(Menu parent) {
				return null;
			}
		});
		manager.add(newSessionAction);
		Action duplicateSessionAction = new Action("", IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				duplicateSelectedTab();
			}
		};
		duplicateSessionAction.setImageDescriptor(Images.getImageDescriptor(Images.ICON_DUPLICATE_TERMINAL));
		duplicateSessionAction.setToolTipText(Messages.TerminalView_DuplicateSession);
		manager.add(duplicateSessionAction);
		Action closeSessionAction = new Action("", IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				closeSelectedTab();
			}
		};
		closeSessionAction.setImageDescriptor(Images.getImageDescriptor(Images.ICON_DISCONNECT));
		closeSessionAction.setToolTipText(Messages.TerminalView_CloseSession);
		manager.add(closeSessionAction);
		Action openPreferencesAction = new Action("", IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				PreferencesUtil.createPreferenceDialogOn(getSite().getShell(),
						"me.anyx.terminal4e.preferences", null, null).open();
			}
		};
		openPreferencesAction.setImageDescriptor(Images.getImageDescriptor(Images.ICON_SETTINGS));
		openPreferencesAction.setToolTipText(Messages.TerminalView_OpenPreferences);
		manager.add(openPreferencesAction);
	}

	private TerminalTab createNewTab(ShellDescriptor preferredShell, boolean autoStart, Path workingDirectory) {
		return createNewTab(preferredShell, autoStart, workingDirectory, null, null);
	}

	private TerminalTab createNewTab(ShellDescriptor preferredShell, boolean autoStart, Path workingDirectory,
			Map<String, String> environment, Charset charset) {
		TerminalTab tab = new TerminalTab();
		tab.session = new TerminalSession();
		tab.pendingOutput = new StringBuilder();

		CTabItem item = new CTabItem(tabFolder, SWT.NONE);
		item.setText(NLS.bind(Messages.TerminalView_SessionTitle, Integer.toString(sessionCounter++)));
		tab.fallbackTitle = item.getText();
		item.setData("tab", tab);
		item.setShowClose(true);
		tab.item = item;

		Composite content = new Composite(tabFolder, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(1).spacing(8, 8).applyTo(content);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(content);

		tab.browser = new Browser(content, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tab.browser);

		if (shells == null || shells.isEmpty()) {
			appendOutput(tab, Messages.TerminalView_NoShellsDetected + System.lineSeparator());
			promptOpenPreferences();
		} else if (autoStart) {
			ShellDescriptor shell = preferredShell != null ? preferredShell : resolveDefaultShell();
			if (shell != null) {
				Charset targetCharset = charset != null ? charset : resolveCharset(shell);
				startSession(tab, shell, targetCharset, workingDirectory, environment);
			}
		}

        initializeBrowser(tab);
        item.setControl(content);
        tabFolder.setSelection(item);
        
		updateSessionSnapshot();
		return tab;
	}

	public void openNewSessionAtPath(Path workingDirectory) {
		Path target = workingDirectory != null ? workingDirectory : resolvePreferredWorkingDirectory();
		loadShells();
		ShellDescriptor defaultShell = resolveDefaultShell();
		createNewTab(defaultShell, true, target);
	}

	private void promptOpenPreferences() {
		boolean open = MessageDialog.openQuestion(getSite().getShell(),
				Messages.TerminalView_NoShellsPromptTitle,
				Messages.TerminalView_NoShellsPromptMessage);
		if (open) {
			PreferencesUtil.createPreferenceDialogOn(getSite().getShell(),
					"me.anyx.terminal4e.preferences", null, null).open();
		}
	}

	private ShellDescriptor resolveDefaultShell() {
		if (shells == null || shells.isEmpty()) {
			return null;
		}
		int defaultIndex = findDefaultShellIndex();
		if (defaultIndex >= 0 && defaultIndex < shells.size()) {
			return shells.get(defaultIndex);
		}
		return shells.get(0);
	}

	private void hookTabFolderEvents() {
		tabFolder.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.detail == SWT.CLOSE && e.item instanceof CTabItem) {
					closeTab((CTabItem) e.item);
				}
			}
		});
		tabFolder.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				if (e.button != 2) {
					return;
				}
				CTabItem item = tabFolder.getItem(new Point(e.x, e.y));
				if (item != null) {
					closeTab(item);
				}
			}
		});
	}

	private void initializeBrowser(TerminalTab tab) {
		if (tab.browser == null || tab.browser.isDisposed()) {
			return;
		}
		Activator.getLogger().info("Initializing browser for new tab: " + tab.shell.getLabel());
		tab.browserReady = false;
		tab.browser.addLocationListener(LocationListener.changingAdapter(e -> {
		    if (!e.top) {
		        return;
		    }
		
    		tab.readyFunction = new BrowserFunction(tab.browser, "terminal4eReady") {
    			@Override
    			public Object function(Object[] arguments) {
    				tab.browserReady = true;
    				flushPendingOutput(tab);
    				focusBrowser(tab);
    				return null;
    			}
    		};
    		tab.inputFunction = new BrowserFunction(tab.browser, "terminal4eSendInput") {
    			@Override
    			public Object function(Object[] arguments) {
    				String data = arguments != null && arguments.length > 0 && arguments[0] != null
    						? String.valueOf(arguments[0])
    						: "";
    				handleInput(tab, data);
    				return null;
    			}
    		};
    		tab.resizeFunction = new BrowserFunction(tab.browser, "terminal4eResize") {
    			@Override
    			public Object function(Object[] arguments) {
    				if (arguments == null || arguments.length < 2) {
    					return null;
    				}
    				int cols = toInt(arguments[0]);
    				int rows = toInt(arguments[1]);
    				if (tab.session != null) {
    					tab.session.setWindowSize(cols, rows);
    				}
    				return null;
    			}
    		};
    		tab.titleFunction = new BrowserFunction(tab.browser, "terminal4eSetTitle") {
    			@Override
    			public Object function(Object[] arguments) {
    				String title = arguments != null && arguments.length > 0 && arguments[0] != null
    						? String.valueOf(arguments[0])
    						: "";
    				updateTabTitle(tab, title);
    				return null;
    			}
    		};
    		tab.confirmFunction = new BrowserFunction(tab.browser, "terminal4eConfirm") {
    			@Override
    			public Object function(Object[] arguments) {
    				String title = arguments != null && arguments.length > 0 && arguments[0] != null
    						? String.valueOf(arguments[0])
    						: "确认";
    				String message = arguments != null && arguments.length > 1 && arguments[1] != null
    						? String.valueOf(arguments[1])
    						: "";
    				final boolean[] result = new boolean[] { false };
    				Display display = tab.browser.getDisplay();
    				if (display == null || display.isDisposed()) {
    					return Boolean.FALSE;
    				}
    				display.syncExec(() -> {
    					if (tab.browser == null || tab.browser.isDisposed()) {
    						return;
    					}
    					result[0] = MessageDialog.openConfirm(tab.browser.getShell(), title, message);
    				});
    				return Boolean.valueOf(result[0]);
    			}
    		};
    		tab.confirmMultilinePasteFunction = new BrowserFunction(tab.browser, "terminal4eConfirmMultilinePaste") {
    			@Override
    			public Object function(Object[] arguments) {
    				String title = arguments != null && arguments.length > 0 && arguments[0] != null
    						? String.valueOf(arguments[0])
    						: "确认";
    				String message = arguments != null && arguments.length > 1 && arguments[1] != null
    						? String.valueOf(arguments[1])
    						: "";
    				String content = arguments != null && arguments.length > 2 && arguments[2] != null
    						? String.valueOf(arguments[2])
    						: "";
    				return openMultilinePasteDialog(tab, title, message, content);
    			}
    		};
    		tab.getMessageFunction = new BrowserFunction(tab.browser, "terminal4eGetMessage") {
    			@Override
    			public Object function(Object[] arguments) {
    				if (arguments == null || arguments.length == 0 || arguments[0] == null) {
    					return "";
    				}
    				String key = String.valueOf(arguments[0]);
    				Object[] args = new Object[Math.max(0, arguments.length - 1)];
    				for (int i = 0; i < args.length; i++) {
    					args[i] = arguments[i + 1];
    				}
    				return Messages.getMessage(key, args);
    			}
    		};
    		tab.getPreferenceFunction = new BrowserFunction(tab.browser, "terminal4eGetPreference") {
    			@Override
    			public Object function(Object[] arguments) {
    				if (arguments == null || arguments.length == 0 || arguments[0] == null) {
    					return "";
    				}
    				String key = String.valueOf(arguments[0]);
    				if (Activator.PREF_CONFIRM_MULTILINE_PASTE.equals(key)) {
    					return Boolean.valueOf(getPreferenceStore().getBoolean(Activator.PREF_CONFIRM_MULTILINE_PASTE));
    				}
    				return "";
    			}
    		};
    		tab.readClipboardFunction = new BrowserFunction(tab.browser, "terminal4eReadClipboard") {
    			@Override
    			public Object function(Object[] arguments) {
    				Display display = tab.browser.getDisplay();
    				if (display == null || display.isDisposed()) {
    					return "";
    				}
    				return readClipboardText(display);
    			}
    		};
    		tab.writeClipboardFunction = new BrowserFunction(tab.browser, "terminal4eWriteClipboard") {
    			@Override
    			public Object function(Object[] arguments) {
    				if (arguments == null || arguments.length == 0 || arguments[0] == null) {
    					return null;
    				}
    				Display display = tab.browser.getDisplay();
    				if (display == null || display.isDisposed()) {
    					return null;
    				}
    				writeClipboardText(display, String.valueOf(arguments[0]));
    				return null;
    			}
    		};
    		tab.openLinkFunction = new BrowserFunction(tab.browser, "terminal4eOpenLink") {
    			@Override
    			public Object function(Object[] arguments) {
    				if (arguments == null || arguments.length == 0 || arguments[0] == null) {
    					return null;
    				}
    				String url = String.valueOf(arguments[0]);
    				Program.launch(url);
    				return null;
    			}
    		};

        }));
		loadTerminalPage(tab);
	}

	private void loadTerminalPage(TerminalTab tab) {
		try {
			Bundle bundle = Platform.getBundle(Activator.PLUGIN_ID);
			Path webRoot = ensureWebAssetsExtracted(bundle);
			Path terminalHtml = webRoot.resolve("terminal.html");
			if (!Files.exists(terminalHtml)) {
				appendOutput(tab, "Failed to locate terminal UI." + System.lineSeparator());
				return;
			}
			tab.browser.setUrl(terminalHtml.toUri().toString());
		} catch (IOException ex) {
			appendOutput(tab, "Failed to load terminal UI: " + ex.getMessage() + System.lineSeparator());
		}
	}

	private Path ensureWebAssetsExtracted(Bundle bundle) throws IOException {
		if (extractedWebRoot != null && Files.exists(extractedWebRoot.resolve("terminal.html"))) {
			return extractedWebRoot;
		}
		IPath stateLocation = Platform.getStateLocation(bundle);
		Path targetRoot = Paths.get(stateLocation.toOSString(), "web");
		Files.createDirectories(targetRoot);
		Enumeration<URL> entries = bundle.findEntries("web", "*", true);
		if (entries == null) {
			extractedWebRoot = targetRoot;
			return targetRoot;
		}
		while (entries.hasMoreElements()) {
			URL entryUrl = entries.nextElement();
			String entryPath = entryUrl.getPath();
			int index = entryPath.indexOf("/web/");
			if (index < 0) {
				continue;
			}
			String relativePath = entryPath.substring(index + 5);
			if (relativePath.isEmpty()) {
				continue;
			}
			Path destination = targetRoot.resolve(relativePath);
			if (relativePath.endsWith("/")) {
				Files.createDirectories(destination);
				continue;
			}
			Path parent = destination.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (InputStream input = entryUrl.openStream()) {
				Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		extractedWebRoot = targetRoot;
		return targetRoot;
	}

	private void startSession(TerminalTab tab, ShellDescriptor shell, Charset charset, Path workingDirectory,
			Map<String, String> environment) {
		try {
			appendOutput(tab, NLS.bind(Messages.TerminalView_StartingShell, shell.getLabel())
					+ System.lineSeparator());
			if (tab != null && tab.item != null && !tab.item.isDisposed()) {
				tab.item.setImage(ShellIconProvider.getShellImage(shell.getCommand(), shell.getIconPath()));
			}
			setDefaultTabTitle(tab, shell);
			tab.shell = shell;
			tab.charset = charset;
			tab.workingDirectory = workingDirectory;
			tab.environment = environment;
			tab.session.start(shell, charset, workingDirectory, environment, text -> appendOutput(tab, text),
				exitCode -> handleSessionExit(tab, exitCode));
			if (tab.environment == null) {
				tab.environment = tab.session.getEnvironment();
			}
			updateSessionSnapshot();
		} catch (IOException ex) {
			appendOutput(tab,
					NLS.bind(Messages.TerminalView_FailedToStartShell, ex.getMessage())
							+ System.lineSeparator());
		}
	}

	private void setDefaultTabTitle(TerminalTab tab, ShellDescriptor shell) {
		if (tab == null || tab.item == null || tab.item.isDisposed() || shell == null) {
			return;
		}
		String title = shell.getLabel();
		if (title == null || title.trim().isEmpty()) {
			title = tab.fallbackTitle;
		}
		tab.defaultTitle = title;
		tab.item.setText(title);
	}

	private void updateTabTitle(TerminalTab tab, String title) {
		if (tab == null || tab.item == null || tab.item.isDisposed()) {
			return;
		}
		String trimmed = title == null ? "" : title.trim();
		String nextTitle = trimmed.isEmpty() ? tab.defaultTitle : extractExecutableName(trimmed);
		if (nextTitle == null || nextTitle.trim().isEmpty()) {
			nextTitle = tab.fallbackTitle;
		}
		final String finalTitle = nextTitle;
		Display display = tab.item.getDisplay();
		if (display == null || display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (tab.item != null && !tab.item.isDisposed()) {
				tab.item.setText(finalTitle);
			}
		});
	}

	private void handleInput(TerminalTab tab, String data) {
		if (data == null || data.isEmpty()) {
			return;
		}
		try {
			if (tab.session == null || !tab.session.isRunning()) {
				appendOutput(tab, Messages.TerminalView_NoActiveSession + System.lineSeparator());
				return;
			}
			tab.session.send(data);
		} catch (IOException ex) {
			appendOutput(tab,
					NLS.bind(Messages.TerminalView_FailedToSendInput, ex.getMessage())
							+ System.lineSeparator());
		}
	}

	private String extractExecutableName(String title) {
		if (title == null) {
			return "";
		}
		String value = title.trim();
		if (value.isEmpty()) {
			return value;
		}
		String[] separators = new String[] { " - ", " — ", " | " };
		for (String separator : separators) {
			int index = value.indexOf(separator);
			if (index > 0) {
				value = value.substring(0, index).trim();
				break;
			}
		}
		if (value.startsWith("-")) {
			value = value.substring(1).trim();
		}
		if (value.equals("-")) {
			return "";
		}
		if (value.startsWith("\"") || value.startsWith("'")) {
			char quote = value.charAt(0);
			int end = value.indexOf(quote, 1);
			if (end > 1) {
				value = value.substring(1, end);
			}
		}
		int spaceIndex = value.indexOf(' ');
		if (spaceIndex > 0) {
			value = value.substring(0, spaceIndex);
		}
		try {
			return Paths.get(value).getFileName().toString();
		} catch (Exception ignored) {
			return value;
		}
	}

	private String openMultilinePasteDialog(TerminalTab tab, String title, String message, String content) {
		if (tab == null || tab.browser == null || tab.browser.isDisposed()) {
			return null;
		}
		Display display = tab.browser.getDisplay();
		if (display == null || display.isDisposed()) {
			return null;
		}
		final String[] result = new String[] { null };
		display.syncExec(() -> {
			if (tab.browser == null || tab.browser.isDisposed()) {
				return;
			}
			MultilinePasteDialog dialog = new MultilinePasteDialog(tab.browser.getShell(), title, message, content);
			if (dialog.open() == TitleAreaDialog.OK) {
				result[0] = dialog.getResultText();
			}
		});
		return result[0];
	}

	private int findDefaultShellIndex() {
		String defaultId = getPreferenceStore().getString(Activator.PREF_DEFAULT_SHELL);
		if (defaultId == null || defaultId.isEmpty() || shells == null) {
			return -1;
		}
		for (int i = 0; i < shells.size(); i++) {
			ShellDescriptor shell = shells.get(i);
			if (defaultId.equals(shell.getId())) {
				return i;
			}
		}
		return -1;
	}

	private Charset resolveCharset(ShellDescriptor shell) {
		String shellCharset = shell == null ? null : shell.getCharsetName();
		if (shellCharset != null && !shellCharset.trim().isEmpty() && Charset.isSupported(shellCharset)) {
			return Charset.forName(shellCharset);
		}
		String prefValue = getPreferenceStore().getString(Activator.PREF_CHARSET);
		if (prefValue != null && !prefValue.trim().isEmpty() && Charset.isSupported(prefValue)) {
			return Charset.forName(prefValue);
		}
		return Charset.defaultCharset();
	}

	private IPreferenceStore getPreferenceStore() {
		return Activator.getDefault().getPreferenceStore();
	}

	private void appendOutput(TerminalTab tab, String text) {
		Display.getDefault().asyncExec(() -> {
			if (tab.browser == null || tab.browser.isDisposed()) {
				return;
			}
			if (!tab.browserReady) {
				tab.pendingOutput.append(text == null ? "" : text);
				return;
			}
			writeToTerminal(tab, text);
		});
	}

	private void writeToTerminal(TerminalTab tab, String text) {
		if (text == null || text.isEmpty()) {
			return;
		}
		String payload = escapeForJavaScript(text);
		tab.browser.execute("window.terminal4eWrite && window.terminal4eWrite('" + payload + "');");
	}

	private void flushPendingOutput(TerminalTab tab) {
		if (tab.pendingOutput == null || tab.pendingOutput.length() == 0) {
			return;
		}
		String data = tab.pendingOutput.toString();
		tab.pendingOutput.setLength(0);
		writeToTerminal(tab, data);
	}

	private void focusBrowser(TerminalTab tab) {
		if (tab.browser == null || tab.browser.isDisposed()) {
			return;
		}
		tab.browser.setFocus();
		tab.browser.execute("window.terminal4eFocus && window.terminal4eFocus();");
	}

	private void closeSelectedTab() {
		CTabItem selection = tabFolder.getSelection();
		if (selection == null) {
			return;
		}
		closeTab(selection, CloseReason.USER_ACTION);
	}

	private void closeTab(CTabItem item) {
		closeTab(item, CloseReason.USER_ACTION);
	}

	private void closeTab(CTabItem item, CloseReason reason) {
		if (item == null || item.isDisposed()) {
			return;
		}
		TerminalTab tab = (TerminalTab) item.getData("tab");
		if (tab != null && tab.closing) {
			return;
		}
		if (reason == CloseReason.USER_ACTION && shouldConfirmClose(tab)) {
			boolean confirmed = MessageDialog.openConfirm(getSite().getShell(),
					Messages.TerminalView_CloseConfirmTitle,
					Messages.TerminalView_CloseConfirmMessage);
			if (!confirmed) {
				return;
			}
		}
		if (tab != null) {
			tab.closing = true;
		}
		if (tab != null && tab.session != null && tab.session.isRunning()) {
			tab.session.stop();
		}
		disposeBrowserFunctions(tab);
		item.dispose();
		updateSessionSnapshot();
		if (tabFolder.getItemCount() == 0) {
			createNewTab(resolveDefaultShell(), true, resolvePreferredWorkingDirectory());
		}
	}

	private boolean shouldConfirmClose(TerminalTab tab) {
		if (tab == null || tab.session == null) {
			return false;
		}
		if (!getPreferenceStore().getBoolean(Activator.PREF_CONFIRM_CLOSE_WITH_PROCESS)) {
			return false;
		}
		if (tab.exited) {
			return false;
		}
		return tab.session.isRunning();
	}

	private void handleSessionExit(TerminalTab tab, int exitCode) {
		if (tab == null) {
			return;
		}
		if (tabFolder == null || tabFolder.isDisposed()) {
			return;
		}
		Display display = tabFolder.getDisplay();
		if (display == null || display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (tab.item == null || tab.item.isDisposed()) {
				return;
			}
			tab.exited = true;
			if (getPreferenceStore().getBoolean(Activator.PREF_AUTO_CLOSE_ON_EXIT)) {
				closeTab(tab.item, CloseReason.PROCESS_EXIT);
			}
		});
	}

	private void duplicateSelectedTab() {
		CTabItem selection = tabFolder.getSelection();
		if (selection == null) {
			return;
		}
		TerminalTab source = (TerminalTab) selection.getData("tab");
		ShellDescriptor shell = source != null && source.shell != null ? source.shell : resolveDefaultShell();
		Path workingDirectory = source != null && source.workingDirectory != null
				? source.workingDirectory
				: resolvePreferredWorkingDirectory();
		Charset charset = source != null && source.charset != null
				? source.charset
				: resolveCharset(shell);
		Map<String, String> environment = source != null ? source.environment : null;
		createNewTab(shell, true, workingDirectory, environment, charset);
	}

	private void disposeBrowserFunctions(TerminalTab tab) {
		if (tab == null) {
			return;
		}
		if (tab.inputFunction != null) {
			tab.inputFunction.dispose();
			tab.inputFunction = null;
		}
		if (tab.resizeFunction != null) {
			tab.resizeFunction.dispose();
			tab.resizeFunction = null;
		}
		if (tab.titleFunction != null) {
			tab.titleFunction.dispose();
			tab.titleFunction = null;
		}
		if (tab.confirmFunction != null) {
			tab.confirmFunction.dispose();
			tab.confirmFunction = null;
		}
		if (tab.confirmMultilinePasteFunction != null) {
			tab.confirmMultilinePasteFunction.dispose();
			tab.confirmMultilinePasteFunction = null;
		}
		if (tab.readyFunction != null) {
			tab.readyFunction.dispose();
			tab.readyFunction = null;
		}
		if (tab.getMessageFunction != null) {
			tab.getMessageFunction.dispose();
			tab.getMessageFunction = null;
		}
		if (tab.getPreferenceFunction != null) {
			tab.getPreferenceFunction.dispose();
			tab.getPreferenceFunction = null;
		}
		if (tab.readClipboardFunction != null) {
			tab.readClipboardFunction.dispose();
			tab.readClipboardFunction = null;
		}
		if (tab.writeClipboardFunction != null) {
			tab.writeClipboardFunction.dispose();
			tab.writeClipboardFunction = null;
		}
		if (tab.openLinkFunction != null) {
			tab.openLinkFunction.dispose();
			tab.openLinkFunction = null;
		}
	}

	private String readClipboardText(Display display) {
		Clipboard clipboard = new Clipboard(display);
		try {
			Object data = clipboard.getContents(TextTransfer.getInstance());
			return data instanceof String ? (String) data : "";
		} finally {
			clipboard.dispose();
		}
	}

	private void writeClipboardText(Display display, String text) {
		if (text == null) {
			return;
		}
		Clipboard clipboard = new Clipboard(display);
		try {
			clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			clipboard.dispose();
		}
	}

	private Path resolvePreferredWorkingDirectory() {
		IResource resource = getSelectedResource();
		if (resource != null) {
			IProject project = resource.getProject();
			if (project != null) {
				IPath projectLocation = project.getLocation();
				if (projectLocation != null) {
					return projectLocation.toFile().toPath();
				}
			}
		}
		IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		if (workspaceLocation != null) {
			return workspaceLocation.toFile().toPath();
		}
		String userHome = System.getProperty("user.home");
		if (userHome != null && !userHome.trim().isEmpty()) {
			return Paths.get(userHome);
		}
		return null;
	}

	private IResource getSelectedResource() {
		IWorkbenchWindow window = getSite().getWorkbenchWindow();
		if (window == null) {
			return null;
		}
		ISelectionService selectionService = window.getSelectionService();
		if (selectionService == null) {
			return null;
		}
		ISelection selection = selectionService.getSelection();
		if (!(selection instanceof IStructuredSelection)) {
			return null;
		}
		Object element = ((IStructuredSelection) selection).getFirstElement();
		if (element instanceof IResource) {
			return (IResource) element;
		}
		if (element instanceof IAdaptable) {
			return ((IAdaptable) element).getAdapter(IResource.class);
		}
		return null;
	}

	@Override
	public void setFocus() {
		CTabItem selection = tabFolder.getSelection();
		if (selection == null) {
			return;
		}
		TerminalTab tab = (TerminalTab) selection.getData("tab");
		if (tab != null) {
			focusBrowser(tab);
		}
	}

	@Override
	public void dispose() {
		updateSessionSnapshot();
		if (tabFolder != null && !tabFolder.isDisposed()) {
			for (CTabItem item : tabFolder.getItems()) {
				TerminalTab tab = (TerminalTab) item.getData("tab");
				if (tab != null && tab.session != null && tab.session.isRunning()) {
					tab.closing = true;
					tab.session.stop();
				}
				disposeBrowserFunctions(tab);
			}
		}
		super.dispose();
	}

	private boolean restoreSessionsIfNeeded() {
		IPreferenceStore store = getPreferenceStore();
		if (store == null || !store.getBoolean(Activator.PREF_RESTORE_SESSIONS)) {
			return false;
		}
		List<SessionSnapshot> snapshots = loadSessionSnapshot(store);
		if (snapshots.isEmpty()) {
			return false;
		}
		Path fallbackWorkingDirectory = resolvePreferredWorkingDirectory();
		for (SessionSnapshot snapshot : snapshots) {
			ShellDescriptor shell = resolveShellById(snapshot.shellId);
			Charset charset = resolveCharsetForSnapshot(shell, snapshot.charsetName);
			Path workingDirectory = snapshot.workingDirectory != null
					? snapshot.workingDirectory
					: fallbackWorkingDirectory;
			TerminalTab tab = createNewTab(shell, true, workingDirectory, snapshot.environment, charset);
			applySnapshotTitle(tab, snapshot.title);
		}
		return true;
	}

	private ShellDescriptor resolveShellById(String shellId) {
		if (shellId == null || shellId.trim().isEmpty() || shells == null) {
			return resolveDefaultShell();
		}
		for (ShellDescriptor shell : shells) {
			if (shellId.equals(shell.getId())) {
				return shell;
			}
		}
		return resolveDefaultShell();
	}

	private Charset resolveCharsetForSnapshot(ShellDescriptor shell, String charsetName) {
		if (charsetName != null && !charsetName.trim().isEmpty() && Charset.isSupported(charsetName)) {
			return Charset.forName(charsetName);
		}
		if (shell != null) {
			return resolveCharset(shell);
		}
		return Charset.defaultCharset();
	}

	private List<SessionSnapshot> loadSessionSnapshot(IPreferenceStore store) {
		String raw = store.getString(Activator.PREF_SESSION_SNAPSHOT);
		if (raw == null || raw.trim().isEmpty()) {
			return new ArrayList<>();
		}
		List<SessionSnapshot> snapshots = new ArrayList<>();
		String[] lines = raw.split("\n");
		for (String line : lines) {
			if (line == null || line.trim().isEmpty()) {
				continue;
			}
			List<String> fields = splitSnapshotLine(line);
			String shellId = fields.size() > 0 ? fields.get(0) : "";
			String workingDir = fields.size() > 1 ? fields.get(1) : "";
			String charset = fields.size() > 2 ? fields.get(2) : "";
			String title = fields.size() > 3 ? fields.get(3) : "";
			String environment = fields.size() > 4 ? fields.get(4) : "";
			Path path = parsePath(workingDir);
			Map<String, String> envMap = parseEnvironment(environment);
			snapshots.add(new SessionSnapshot(shellId, path, charset, title, envMap));
		}
		return snapshots;
	}

	private void updateSessionSnapshot() {
		IPreferenceStore store = getPreferenceStore();
		if (store == null) {
			return;
		}
		if (!store.getBoolean(Activator.PREF_RESTORE_SESSIONS)) {
			store.setValue(Activator.PREF_SESSION_SNAPSHOT, "");
			return;
		}
		if (tabFolder == null || tabFolder.isDisposed()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (CTabItem item : tabFolder.getItems()) {
			if (item == null || item.isDisposed()) {
				continue;
			}
			TerminalTab tab = (TerminalTab) item.getData("tab");
			if (tab == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('\n');
			}
			String shellId = tab.shell != null ? nullToEmpty(tab.shell.getId()) : "";
			String workingDir = tab.workingDirectory != null ? tab.workingDirectory.toString() : "";
			String charsetName = tab.charset != null ? tab.charset.name() : "";
			String title = item.getText();
			String environment = serializeEnvironment(tab.environment);
			sb.append(encodeSnapshotField(shellId));
			sb.append(SNAPSHOT_SEP);
			sb.append(encodeSnapshotField(workingDir));
			sb.append(SNAPSHOT_SEP);
			sb.append(encodeSnapshotField(charsetName));
			sb.append(SNAPSHOT_SEP);
			sb.append(encodeSnapshotField(title));
			sb.append(SNAPSHOT_SEP);
			sb.append(encodeSnapshotField(environment));
		}
		store.setValue(Activator.PREF_SESSION_SNAPSHOT, sb.toString());
	}

	private static String encodeSnapshotField(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == SNAPSHOT_ESC || c == SNAPSHOT_SEP || c == '\n') {
				sb.append(SNAPSHOT_ESC);
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static List<String> splitSnapshotLine(String line) {
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
			if (c == SNAPSHOT_ESC) {
				escaped = true;
				continue;
			}
			if (c == SNAPSHOT_SEP) {
				fields.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		fields.add(current.toString());
		return fields;
	}

	private void applySnapshotTitle(TerminalTab tab, String title) {
		if (tab == null || tab.item == null || tab.item.isDisposed()) {
			return;
		}
		String trimmed = title == null ? "" : title.trim();
		if (trimmed.isEmpty()) {
			return;
		}
		tab.defaultTitle = trimmed;
		tab.item.setText(trimmed);
	}

	private static String serializeEnvironment(Map<String, String> environment) {
		if (environment == null || environment.isEmpty()) {
			return "";
		}
		List<String> keys = new ArrayList<>(environment.keySet());
		Collections.sort(keys);
		StringBuilder sb = new StringBuilder();
		for (String key : keys) {
			if (key == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(ENV_PAIR_SEP);
			}
			sb.append(encodeEnvironmentField(key));
			sb.append(ENV_KV_SEP);
			String value = environment.get(key);
			sb.append(encodeEnvironmentField(value == null ? "" : value));
		}
		return sb.toString();
	}

	private static Map<String, String> parseEnvironment(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return null;
		}
		Map<String, String> map = new java.util.LinkedHashMap<>();
		List<String> pairs = splitEnvironmentPairs(raw);
		for (String pair : pairs) {
			if (pair == null || pair.isEmpty()) {
				continue;
			}
			int index = findUnescaped(pair, ENV_KV_SEP);
			String key;
			String value;
			if (index < 0) {
				key = decodeEnvironmentField(pair);
				value = "";
			} else {
				key = decodeEnvironmentField(pair.substring(0, index));
				value = decodeEnvironmentField(pair.substring(index + 1));
			}
			if (!key.isEmpty()) {
				map.put(key, value);
			}
		}
		return map.isEmpty() ? null : map;
	}

	private static List<String> splitEnvironmentPairs(String raw) {
		List<String> pairs = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean escaped = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (escaped) {
				current.append(c);
				escaped = false;
				continue;
			}
			if (c == SNAPSHOT_ESC) {
				escaped = true;
				continue;
			}
			if (c == ENV_PAIR_SEP) {
				pairs.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		pairs.add(current.toString());
		return pairs;
	}

	private static int findUnescaped(String value, char target) {
		boolean escaped = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == SNAPSHOT_ESC) {
				escaped = true;
				continue;
			}
			if (c == target) {
				return i;
			}
		}
		return -1;
	}

	private static String encodeEnvironmentField(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == SNAPSHOT_ESC || c == ENV_PAIR_SEP || c == ENV_KV_SEP || c == '\n') {
				sb.append(SNAPSHOT_ESC);
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static String decodeEnvironmentField(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		boolean escaped = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (escaped) {
				sb.append(c);
				escaped = false;
				continue;
			}
			if (c == SNAPSHOT_ESC) {
				escaped = true;
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static Path parsePath(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return null;
		}
		try {
			return Paths.get(raw);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static int toInt(Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static String escapeForJavaScript(String text) {
		StringBuilder sb = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '\\':
					sb.append("\\\\");
					break;
				case '\'':
					sb.append("\\\'");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\u2028':
					sb.append("\\u2028");
					break;
				case '\u2029':
					sb.append("\\u2029");
					break;
				default:
					sb.append(c);
			}
		}
		return sb.toString();
	}

	private static final class TerminalTab {
		private CTabItem item;
		private Browser browser;
		private TerminalSession session;
		private StringBuilder pendingOutput;
		private boolean browserReady;
		private BrowserFunction readyFunction;
		private BrowserFunction inputFunction;
		private BrowserFunction resizeFunction;
		private BrowserFunction titleFunction;
		private BrowserFunction confirmFunction;
		private BrowserFunction confirmMultilinePasteFunction;
		private BrowserFunction getMessageFunction;
		private BrowserFunction getPreferenceFunction;
		private BrowserFunction readClipboardFunction;
		private BrowserFunction writeClipboardFunction;
		private BrowserFunction openLinkFunction;
		private String defaultTitle;
		private String fallbackTitle;
		private ShellDescriptor shell;
		private Charset charset;
		private Path workingDirectory;
		private Map<String, String> environment;
		private boolean closing;
		private boolean exited;
	}

	private static final class SessionSnapshot {
		private final String shellId;
		private final Path workingDirectory;
		private final String charsetName;
		private final String title;
		private final Map<String, String> environment;

		private SessionSnapshot(String shellId, Path workingDirectory, String charsetName, String title,
				Map<String, String> environment) {
			this.shellId = shellId == null ? "" : shellId;
			this.workingDirectory = workingDirectory;
			this.charsetName = charsetName == null ? "" : charsetName;
			this.title = title == null ? "" : title;
			this.environment = environment;
		}
	}

	private enum CloseReason {
		USER_ACTION,
		PROCESS_EXIT,
		SILENT
	}

	private static final class MultilinePasteDialog extends TitleAreaDialog {
		private final String title;
		private final String message;
		private final String content;
		private Text contentText;
		private String resultText;

		private MultilinePasteDialog(Shell parentShell, String title, String message, String content) {
			super(parentShell);
			this.title = title;
			this.message = message;
			this.content = content == null ? "" : content;
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			setTitle(title);
			setMessage(message);
			setHelpAvailable(false);
			Composite container = new Composite(area, SWT.NONE);
			GridDataFactory.fillDefaults().grab(true, true).applyTo(container);
			GridLayoutFactory.swtDefaults().numColumns(1).applyTo(container);

			Label label = new Label(container, SWT.NONE);
			label.setText("");
			GridDataFactory.fillDefaults().applyTo(label);

			contentText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
			contentText.setText(content);
			GridDataFactory.fillDefaults().grab(true, true).hint(640, 240).applyTo(contentText);
			return area;
		}

		@Override
		protected void okPressed() {
			resultText = contentText == null ? "" : contentText.getText();
			super.okPressed();
		}

		private String getResultText() {
			return resultText;
		}
	}
}
