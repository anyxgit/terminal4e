package me.anyx.terminal4e;

import java.util.Locale;

import org.eclipse.core.runtime.ILog;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {
	public static final String PLUGIN_ID = "me.anyx.terminal4e";
	public static final String PREF_CHARSET = "terminal.charset";
	public static final String PREF_LANGUAGE = "terminal.language";
	public static final String PREF_SHELLS = "terminal.shells";
	public static final String PREF_DEFAULT_SHELL = "terminal.shells.default";
	public static final String PREF_CONFIRM_MULTILINE_PASTE = "terminal.paste.confirmMultiline";
	public static final String PREF_CONFIRM_CLOSE_WITH_PROCESS = "terminal.close.confirmRunning";
	public static final String PREF_AUTO_CLOSE_ON_EXIT = "terminal.close.autoOnExit";
	public static final String PREF_AUTO_OPEN_PROJECT_TERMINAL = "terminal.project.autoOpen";
	public static final String PREF_RESTORE_SESSIONS = "terminal.sessions.restoreOnStartup";
	public static final String PREF_SESSION_SNAPSHOT = "terminal.sessions.snapshot";

    private static Activator plugin;
    private static ILog logger;

	public Activator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		logger = getLog();
		initializeDefaults();
		initializeMessages();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	public static Activator getDefault() {
		return plugin;
	}

	public static ILog getLogger() {
        return logger;
    }

	private void initializeDefaults() {
		getPreferenceStore().setDefault(PREF_CHARSET, java.nio.charset.Charset.defaultCharset().name());
		getPreferenceStore().setDefault(PREF_LANGUAGE, "auto");
		getPreferenceStore().setDefault(PREF_SHELLS, "");
		getPreferenceStore().setDefault(PREF_DEFAULT_SHELL, "");
		getPreferenceStore().setDefault(PREF_CONFIRM_MULTILINE_PASTE, true);
		getPreferenceStore().setDefault(PREF_CONFIRM_CLOSE_WITH_PROCESS, true);
		getPreferenceStore().setDefault(PREF_AUTO_CLOSE_ON_EXIT, true);
		getPreferenceStore().setDefault(PREF_AUTO_OPEN_PROJECT_TERMINAL, false);
		getPreferenceStore().setDefault(PREF_RESTORE_SESSIONS, true);
		getPreferenceStore().setDefault(PREF_SESSION_SNAPSHOT, "");
	}

	private void initializeMessages() {
        String lang = getPreferenceStore().getString(Activator.PREF_LANGUAGE);
        logger.info("Initializing messages for language: " + lang);

        if (lang == null || lang.equals("auto")) {
            NLS.initializeMessages(Messages.BUNDLE_NAME, Messages.class);
            return;
        }

        String[] parts = lang.split("_");
        if (parts.length < 1) {
            NLS.initializeMessages(Messages.BUNDLE_NAME, Messages.class);
            return;
        }

        String language = parts[0];
        String country = parts.length > 1 ? parts[1] : "";
        String variant = parts.length > 2 ? parts[2] : "";

        Locale locale = new Locale(language, country, variant);
        Locale defaultLang = Locale.getDefault();

        if (locale.equals(defaultLang)) {
            NLS.initializeMessages(Messages.BUNDLE_NAME, Messages.class);
            return;
        }

        NLS.initializeMessages(Messages.BUNDLE_NAME, Messages.class, locale);
    }
}
