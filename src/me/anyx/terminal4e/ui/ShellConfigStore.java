package me.anyx.terminal4e.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

import me.anyx.terminal4e.Activator;
import me.anyx.terminal4e.core.ShellDescriptor;

final class ShellConfigStore {
	private static final char SEP = '|';
	private static final char ESC = '\\';

	private ShellConfigStore() {
	}

	static List<ShellConfig> loadShellConfigs(IPreferenceStore store) {
		if (store == null) {
			return Collections.emptyList();
		}
		String raw = store.getString(Activator.PREF_SHELLS);
		if (raw == null || raw.trim().isEmpty()) {
			return Collections.emptyList();
		}
		List<ShellConfig> configs = new ArrayList<>();
		String[] lines = raw.split("\n");
		for (String line : lines) {
			if (line == null || line.trim().isEmpty()) {
				continue;
			}
			List<String> fields = splitLine(line);
			if (fields.size() < 5) {
				continue;
			}
			String iconPath = fields.size() >= 6 ? fields.get(5) : "";
			ShellConfig config = new ShellConfig(fields.get(0), fields.get(1), fields.get(2), fields.get(3),
					fields.get(4), iconPath);
			configs.add(config);
		}
		return configs;
	}

	static void saveShellConfigs(IPreferenceStore store, List<ShellConfig> configs) {
		if (store == null) {
			return;
		}
		if (configs == null || configs.isEmpty()) {
			store.setValue(Activator.PREF_SHELLS, "");
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (ShellConfig config : configs) {
			if (config == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(encode(config.getId()));
			sb.append(SEP);
			sb.append(encode(config.getLabel()));
			sb.append(SEP);
			sb.append(encode(config.getCommand()));
			sb.append(SEP);
			sb.append(encode(config.getArgs()));
			sb.append(SEP);
			sb.append(encode(config.getCharset()));
			sb.append(SEP);
			sb.append(encode(config.getIconPath()));
		}
		store.setValue(Activator.PREF_SHELLS, sb.toString());
	}

	static List<ShellDescriptor> toShellDescriptors(List<ShellConfig> configs) {
		if (configs == null || configs.isEmpty()) {
			return Collections.emptyList();
		}
		List<ShellDescriptor> shells = new ArrayList<>();
		for (ShellConfig config : configs) {
			if (config == null || config.getCommand() == null || config.getCommand().trim().isEmpty()) {
				continue;
			}
			List<String> args = parseArgs(config.getArgs());
			shells.add(new ShellDescriptor(config.getId(), config.getLabel(), config.getCommand(), args,
					config.getCharset(), config.getIconPath()));
		}
		return shells;
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

	private static List<String> parseArgs(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return Collections.emptyList();
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

	static final class ShellConfig {
		private final String id;
		private String label;
		private String command;
		private String args;
		private String charset;
		private String iconPath;

		ShellConfig(String id, String label, String command, String args, String charset, String iconPath) {
			this.id = id;
			this.label = label;
			this.command = command;
			this.args = args;
			this.charset = charset;
			this.iconPath = iconPath;
		}

		String getId() {
			return id;
		}

		String getLabel() {
			return label;
		}

		void setLabel(String label) {
			this.label = label;
		}

		String getCommand() {
			return command;
		}

		void setCommand(String command) {
			this.command = command;
		}

		String getArgs() {
			return args;
		}

		void setArgs(String args) {
			this.args = args;
		}

		String getCharset() {
			return charset;
		}

		void setCharset(String charset) {
			this.charset = charset;
		}

		String getIconPath() {
			return iconPath;
		}

		void setIconPath(String iconPath) {
			this.iconPath = iconPath;
		}
	}
}
