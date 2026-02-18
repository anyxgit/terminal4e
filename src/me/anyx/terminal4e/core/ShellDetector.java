package me.anyx.terminal4e.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.anyx.terminal4e.Messages;

public final class ShellDetector {
	private ShellDetector() {
	}

	public static List<ShellDescriptor> detect() {
		List<ShellDescriptor> shells = new ArrayList<>();

		String comSpec = System.getenv("ComSpec");
		if (comSpec != null && !comSpec.isEmpty()) {
			shells.add(new ShellDescriptor("cmd", Messages.Shell_CommandPrompt, comSpec, null));
		} else {
			shells.add(new ShellDescriptor("cmd", Messages.Shell_CommandPrompt, "cmd.exe", null));
		}

		shells.add(new ShellDescriptor("powershell", Messages.Shell_WindowsPowerShell, "powershell.exe",
				Arrays.asList("-NoLogo")));
		shells.add(new ShellDescriptor("pwsh", Messages.Shell_PowerShell7, "pwsh.exe",
				Arrays.asList("-NoLogo")));

		addWsl(shells);
		addGitBash(shells);

		return shells;
	}

	private static void addGitBash(List<ShellDescriptor> shells) {
		Path gitFromWhere = findGitFromWhere();
		if (gitFromWhere != null) {
			Path gitDir = gitFromWhere.getParent();
			if (gitDir != null) {
				Path bashInDir = gitDir.resolve("bash.exe");
				Path bashInBin = gitDir.resolve("..\\bin\\bash.exe").normalize();
				Path bashInUsrBin = gitDir.resolve("..\\usr\\bin\\bash.exe").normalize();
				if (Files.exists(bashInDir)) {
					String iconPath = resolveGitBashIconPath(bashInDir);
					shells.add(new ShellDescriptor("git-bash", Messages.Shell_GitBash, bashInDir.toString(),
							Arrays.asList("--login", "-i"), null, iconPath));
					return;
				}
				if (Files.exists(bashInBin)) {
					String iconPath = resolveGitBashIconPath(bashInBin);
					shells.add(new ShellDescriptor("git-bash", Messages.Shell_GitBash, bashInBin.toString(),
							Arrays.asList("--login", "-i"), null, iconPath));
					return;
				}
				if (Files.exists(bashInUsrBin)) {
					String iconPath = resolveGitBashIconPath(bashInUsrBin);
					shells.add(new ShellDescriptor("git-bash", Messages.Shell_GitBash, bashInUsrBin.toString(),
							Arrays.asList("--login", "-i"), null, iconPath));
					return;
				}
			}
		}

		List<Path> candidates = new ArrayList<>();
		String programFiles = getenv("ProgramFiles");
		String programFilesX86 = getenv("ProgramFiles(x86)");
		String programW6432 = getenv("ProgramW6432");

		addCandidate(candidates, programFiles, "Git", "bin", "bash.exe");
		addCandidate(candidates, programFiles, "Git", "usr", "bin", "bash.exe");
		addCandidate(candidates, programFilesX86, "Git", "bin", "bash.exe");
		addCandidate(candidates, programFilesX86, "Git", "usr", "bin", "bash.exe");
		addCandidate(candidates, programW6432, "Git", "bin", "bash.exe");
		addCandidate(candidates, programW6432, "Git", "usr", "bin", "bash.exe");

		for (Path path : candidates) {
			if (path != null && Files.exists(path)) {
				String iconPath = resolveGitBashIconPath(path);
				shells.add(new ShellDescriptor("git-bash", Messages.Shell_GitBash, path.toString(),
						Arrays.asList("--login", "-i"), null, iconPath));
				return;
			}
		}
	}

	private static String resolveGitBashIconPath(Path bashPath) {
		Path current = bashPath == null ? null : bashPath.getParent();
		for (int i = 0; i < 5 && current != null; i++) {
			Path icon = current.resolve("mingw64\\share\\git\\git-for-windows.ico");
			if (Files.exists(icon)) {
				return icon.toString();
			}
			current = current.getParent();
		}
		return null;
	}

	private static Path findGitFromWhere() {
		String osName = System.getProperty("os.name");
		if (osName == null || !osName.toLowerCase().contains("windows")) {
			return null;
		}
		ProcessBuilder builder = new ProcessBuilder("where", "git");
		builder.redirectErrorStream(true);
		try {
			Process process = builder.start();
			try (java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String trimmed = line.trim();
					if (trimmed.isEmpty()) {
						continue;
					}
					Path path = Paths.get(trimmed);
					String lower = trimmed.toLowerCase();
					if (Files.exists(path)
								&& (lower.endsWith("git.exe") || lower.endsWith("git.cmd") || lower.endsWith("git.bat"))) {
						return path;
					}
					if (Files.exists(path)) {
						return path;
					}
				}
			}
			process.destroy();
		} catch (Exception ex) {
			// ignore
		}
		return null;
	}

	private static void addWsl(List<ShellDescriptor> shells) {
		List<Path> candidates = new ArrayList<>();
		String systemRoot = getenv("SystemRoot");
		addCandidate(candidates, systemRoot, "System32", "wsl.exe");
		addCandidate(candidates, systemRoot, "Sysnative", "wsl.exe");
		addCandidate(candidates, systemRoot, "SysWOW64", "wsl.exe");
		addCandidate(candidates, "C:\\Windows", "System32", "wsl.exe");

		for (Path path : candidates) {
			if (path != null && Files.exists(path)) {
				addWslDistributions(shells, path);
				return;
			}
		}
	}

	private static void addWslDistributions(List<ShellDescriptor> shells, Path wslPath) {
		List<String> distributions = listWslDistributions(wslPath);
		if (distributions.isEmpty()) {
			return;
		}
		Set<String> usedIds = new HashSet<>();
		int index = 1;
		for (String distribution : distributions) {
			String label = distribution + "(WSL)";
			String id = buildWslId(distribution, index++, usedIds);
			List<String> args = Arrays.asList("-d", distribution);
			shells.add(new ShellDescriptor(id, label, wslPath.toString(), args));
		}
	}

	private static String buildWslId(String distribution, int index, Set<String> usedIds) {
		String base = distribution == null ? "" : distribution.trim().toLowerCase();
		base = base.replaceAll("[^a-z0-9]+", "-");
		if (base.isEmpty() || "-".equals(base)) {
			base = "wsl";
		}
		String id = "wsl-" + base;
		if (usedIds.add(id)) {
			return id;
		}
		String candidate = id + "-" + index;
		while (!usedIds.add(candidate)) {
			index++;
			candidate = id + "-" + index;
		}
		return candidate;
	}

	private static List<String> listWslDistributions(Path wslPath) {
		List<String> distributions = new ArrayList<>();
		ProcessBuilder builder = new ProcessBuilder(wslPath.toString(), "-l", "-q");
		builder.redirectErrorStream(true);
		try {
			Process process = builder.start();
			try (java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_16LE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String trimmed = line.trim();
					if (!trimmed.isEmpty()) {
						distributions.add(trimmed);
					}
				}
			}
			process.destroy();
		} catch (Exception ex) {
			// ignore
		}
		return distributions;
	}

	private static void addCandidate(List<Path> candidates, String base, String... parts) {
		if (base == null || base.isEmpty()) {
			return;
		}
		Path path = Paths.get(base, parts);
		candidates.add(path);
	}

	private static String getenv(String name) {
		String value = System.getenv(name);
		return value == null ? "" : value;
	}
}
