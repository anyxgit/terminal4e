package me.anyx.terminal4e;

import java.lang.reflect.Field;

public final class Messages extends NLS {
	public static final String BUNDLE_NAME = "me.anyx.terminal4e.messages";

	public static String TerminalView_NewSession;
	public static String TerminalView_CloseSession;
	public static String TerminalView_OpenPreferences;
	public static String TerminalView_SessionTitle;
	public static String TerminalView_Send;
	public static String TerminalView_NoShellsDetected;
	public static String TerminalView_NoShellsPromptTitle;
	public static String TerminalView_NoShellsPromptMessage;
	public static String TerminalView_StartingShell;
	public static String TerminalView_FailedToStartShell;
	public static String TerminalView_NoActiveSession;
	public static String TerminalView_FailedToSendInput;
	public static String TerminalView_CloseConfirmTitle;
	public static String TerminalView_CloseConfirmMessage;
	public static String TerminalView_DuplicateSession;

	public static String TerminalView_ConfirmPasteTitle;
	public static String TerminalView_ConfirmPasteMessage;
	public static String TerminalView_OmittedSuffix;
	public static String TerminalView_RunningProcessesTooltipTitle;
	public static String TerminalView_RunningProcessesTooltipItemPrefix;

	public static String TerminalPreference_Description;
	public static String TerminalPreference_Language;
	public static String TerminalPreference_LanguageAuto;
	public static String TerminalPreference_LanguageRestartHint;
	public static String TerminalPreference_LanguageRestartTitle;
	public static String TerminalPreference_LanguageRestartMessage;
	public static String TerminalPreference_DefaultCharset;
	public static String TerminalPreference_ConfirmMultilinePaste;
	public static String TerminalPreference_ConfirmCloseWithRunning;
	public static String TerminalPreference_AutoCloseOnExit;
	public static String TerminalPreference_AutoOpenProjectTerminal;
	public static String TerminalPreference_RestoreSessionsOnStartup;
	public static String TerminalPreference_ShellGroupTitle;
	public static String TerminalPreference_ShellName;
	public static String TerminalPreference_ShellCommand;
	public static String TerminalPreference_ShellArgs;
	public static String TerminalPreference_ShellCharset;
	public static String TerminalPreference_Add;
	public static String TerminalPreference_AutoDetect;
	public static String TerminalPreference_Edit;
	public static String TerminalPreference_Remove;
	public static String TerminalPreference_MoveUp;
	public static String TerminalPreference_MoveDown;
	public static String TerminalPreference_SetDefault;
	public static String TerminalPreference_DefaultTag;
	public static String TerminalPreference_DefaultColumn;
	public static String TerminalPreference_DialogAddTitle;
	public static String TerminalPreference_DialogEditTitle;
	public static String TerminalPreference_DialogMessage;
	public static String TerminalPreference_DialogName;
	public static String TerminalPreference_DialogCommand;
	public static String TerminalPreference_DialogArgs;
	public static String TerminalPreference_DialogIcon;
	public static String TerminalPreference_DialogIconBrowse;
	public static String TerminalPreference_DialogIconClear;
	public static String TerminalPreference_DialogCharset;
	public static String TerminalPreference_DialogInvalidName;
	public static String TerminalPreference_DialogInvalidCommand;
	public static String TerminalPreference_DialogInvalidCharset;
	public static String TerminalPreference_RemoveConfirmTitle;
	public static String TerminalPreference_RemoveConfirmMessage;

	public static String TerminalThemePreference_Description;
	public static String TerminalThemePreference_ModeFollowEclipse;
	public static String TerminalThemePreference_SelectTheme;
	public static String TerminalThemePreference_CustomizeSaveAs;
	public static String TerminalThemePreference_CustomName;
	public static String TerminalThemePreference_InvalidNameMessage;
	public static String TerminalThemePreference_InvalidColorTitle;
	public static String TerminalThemePreference_InvalidColorMessage;
	public static String TerminalThemePreference_DuplicateNameTitle;
	public static String TerminalThemePreference_DuplicateNameMessage;
	public static String TerminalThemePreference_GeneralColors;
	public static String TerminalThemePreference_PaletteColors;
	public static String TerminalThemePreference_Presets;
	public static String TerminalThemePreference_LoadPreset;
	public static String TerminalThemePreference_TextColor;
	public static String TerminalThemePreference_BackgroundColor;
	public static String TerminalThemePreference_SelectionColor;
	public static String TerminalThemePreference_SelectedTextColor;
	public static String TerminalThemePreference_CursorColor;
	public static String TerminalThemePreference_CursorAccentColor;
	public static String TerminalThemePreference_ThemeBuiltin;
	public static String TerminalThemePreference_ThemeCustom;
	public static String TerminalThemePreference_ThemeLight;
	public static String TerminalThemePreference_ThemeDark;

	public static String Charset_Default;

	public static String Shell_CommandPrompt;
	public static String Shell_WindowsPowerShell;
	public static String Shell_PowerShell7;
	public static String Shell_GitBash;
	public static String Shell_Wsl;
	public static String Shell_Bash;
	public static String Shell_Zsh;
	public static String Shell_Fish;
	public static String Shell_Sh;

	private Messages() {
	}

	public static String getMessage(String key, Object... args) {
		if (key == null || key.trim().isEmpty()) {
			return "";
		}
		try {
			Field field = Messages.class.getField(key);
			Object value = field.get(null);
			if (value instanceof String) {
				return NLS.bind((String) value, args);
			}
		} catch (Exception ignored) {
		}
		return key;
	}
}
