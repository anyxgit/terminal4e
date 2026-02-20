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

		if (isWindows()) {
			addCmd(shells);
			addPowershell(shells);
			addPwsh(shells);
			addWsl(shells);
			addGitBash(shells);
		} else {
			addUnixShells(shells);
		}

		return shells;
	}

	private static void addUnixShells(List<ShellDescriptor> shells) {
		Set<String> addedCommands = new HashSet<>();
		addUnixShellFromEnv(shells, addedCommands);
		addUnixShell(shells, addedCommands, "zsh", Messages.Shell_Zsh);
		addUnixShell(shells, addedCommands, "bash", Messages.Shell_Bash);
		addUnixShell(shells, addedCommands, "fish", Messages.Shell_Fish);
		addUnixShell(shells, addedCommands, "sh", Messages.Shell_Sh);
	}

	private static void addUnixShellFromEnv(List<ShellDescriptor> shells, Set<String> addedCommands) {
		String shellEnv = getenv("SHELL");
		if (shellEnv.isEmpty()) {
			return;
		}
		Path shellPath = Paths.get(shellEnv);
		if (!Files.exists(shellPath)) {
			return;
		}
		String command = shellPath.toAbsolutePath().normalize().toString();
		if (!addedCommands.add(command)) {
			return;
		}
		String executable = shellPath.getFileName() == null ? "shell" : shellPath.getFileName().toString();
		String lower = executable.toLowerCase();
		String id = lower.replaceAll("[^a-z0-9]+", "-");
		if (id.isEmpty() || "-".equals(id)) {
			id = "shell";
		}
		String label;
		if ("zsh".equals(lower)) {
			label = Messages.Shell_Zsh;
		} else if ("bash".equals(lower)) {
			label = Messages.Shell_Bash;
		} else if ("fish".equals(lower)) {
			label = Messages.Shell_Fish;
		} else if ("sh".equals(lower)) {
			label = Messages.Shell_Sh;
		} else {
			label = executable;
		}
		shells.add(new ShellDescriptor(id, label, command, null));
	}

	private static void addUnixShell(List<ShellDescriptor> shells, Set<String> addedCommands, String executableName,
			String label) {
		Path path = findExecutable(executableName);
		if (path == null) {
			path = findUnixShellByCommonPaths(executableName);
		}
		if (path == null) {
			return;
		}
		String command = path.toAbsolutePath().normalize().toString();
		if (!addedCommands.add(command)) {
			return;
		}
		shells.add(new ShellDescriptor(executableName, label, command, null));
	}

	private static Path findUnixShellByCommonPaths(String executableName) {
		List<Path> candidates = Arrays.asList(Paths.get("/bin", executableName), Paths.get("/usr/bin", executableName),
				Paths.get("/usr/local/bin", executableName), Paths.get("/opt/homebrew/bin", executableName));
		for (Path candidate : candidates) {
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static void addPwsh(List<ShellDescriptor> shells) {
		Path pwshPath = findExecutable("pwsh.exe");
		if (pwshPath == null) {
			List<Path> pwshCandidates = new ArrayList<>();
			String programFiles = getenv("ProgramFiles");
			String programFilesX86 = getenv("ProgramFiles(x86)");
			String programW6432 = getenv("ProgramW6432");
			addCandidate(pwshCandidates, programFiles, "PowerShell", "7", "pwsh.exe");
			addCandidate(pwshCandidates, programFiles, "PowerShell", "7-preview", "pwsh.exe");
			addCandidate(pwshCandidates, programFilesX86, "PowerShell", "7", "pwsh.exe");
			addCandidate(pwshCandidates, programFilesX86, "PowerShell", "7-preview", "pwsh.exe");
			addCandidate(pwshCandidates, programW6432, "PowerShell", "7", "pwsh.exe");
			addCandidate(pwshCandidates, programW6432, "PowerShell", "7-preview", "pwsh.exe");
			pwshPath = findFirstExisting(pwshCandidates);
		}
		if (pwshPath != null) {
			shells.add(new ShellDescriptor("pwsh", Messages.Shell_PowerShell7, pwshPath.toString(),
					Arrays.asList("-NoLogo")));
		}
	}

	private static void addPowershell(List<ShellDescriptor> shells) {
		Path powershellPath = findExecutable("powershell.exe");
		if (powershellPath == null) {
			List<Path> powershellCandidates = new ArrayList<>();
			String systemRoot = getenv("SystemRoot");
			addCandidate(powershellCandidates, systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			addCandidate(powershellCandidates, systemRoot, "Sysnative", "WindowsPowerShell", "v1.0", "powershell.exe");
			addCandidate(powershellCandidates, systemRoot, "SysWOW64", "WindowsPowerShell", "v1.0", "powershell.exe");
			addCandidate(powershellCandidates, "C:\\Windows", "System32", "WindowsPowerShell", "v1.0",
					"powershell.exe");
			powershellPath = findFirstExisting(powershellCandidates);
		}
		if (powershellPath != null) {
			shells.add(new ShellDescriptor("powershell", Messages.Shell_WindowsPowerShell, powershellPath.toString(),
					Arrays.asList("-NoLogo")));
		}
	}

	private static void addCmd(List<ShellDescriptor> shells) {
		String comSpec = System.getenv("ComSpec");
		if (comSpec != null && !comSpec.isEmpty()) {
			shells.add(new ShellDescriptor("cmd", Messages.Shell_CommandPrompt, comSpec, null));
		} else {
			shells.add(new ShellDescriptor("cmd", Messages.Shell_CommandPrompt, "cmd.exe", null));
		}
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
		return findExecutable("git");
	}

	private static Path findExecutable(String executableName) {
		ProcessBuilder builder = new ProcessBuilder(isWindows() ? "where" : "which", executableName);
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
					if (!Files.exists(path)) {
						continue;
					}
					if (!isWindows()) {
						return path;
					}
					if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat")) {
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

	private static Path findFirstExisting(List<Path> candidates) {
		for (Path path : candidates) {
			if (path != null && Files.exists(path)) {
				return path;
			}
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

	private static boolean isWindows() {
		String osName = System.getProperty("os.name");
		return osName != null && osName.toLowerCase().contains("windows");
	}
}
