package me.anyx.terminal4e.core;

import java.util.Collections;
import java.util.List;

public final class ShellDescriptor {
	private final String id;
	private final String label;
	private final String command;
	private final List<String> args;
	private final String charsetName;
	private final String iconPath;

	public ShellDescriptor(String id, String label, String command, List<String> args) {
		this(id, label, command, args, null, null);
	}

	public ShellDescriptor(String id, String label, String command, List<String> args, String charsetName) {
		this(id, label, command, args, charsetName, null);
	}

	public ShellDescriptor(String id, String label, String command, List<String> args, String charsetName,
			String iconPath) {
		this.id = id;
		this.label = label;
		this.command = command;
		this.args = args == null ? Collections.emptyList() : Collections.unmodifiableList(args);
		this.charsetName = charsetName;
		this.iconPath = iconPath;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public String getCommand() {
		return command;
	}

	public List<String> getArgs() {
		return args;
	}

	public String getCharsetName() {
		return charsetName;
	}

	public String getIconPath() {
		return iconPath;
	}
}
