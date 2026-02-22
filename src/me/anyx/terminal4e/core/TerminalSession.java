package me.anyx.terminal4e.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

public class TerminalSession {
	private final AtomicBoolean running = new AtomicBoolean(false);
	private PtyProcess process;
	private OutputStream stdin;
	private Thread stdoutThread;
	private Thread waitThread;
	private Charset charset = Charset.defaultCharset();
	private volatile int columns = 80;
	private volatile int rows = 24;
	private volatile Path workingDirectory;
	private volatile ShellDescriptor shell;
	private volatile Map<String, String> environment = Collections.emptyMap();
	private volatile long shellPid = -1;
	private volatile boolean hasChildProcesses;
	private volatile List<String> activeProcessNames = Collections.emptyList();
	private volatile TerminalChildProcessMonitor childProcessMonitor;
	private volatile Consumer<ProcessActivity> processActivityListener;
	private final AtomicBoolean exitNotified = new AtomicBoolean(false);

	public boolean isRunning() {
		return running.get();
	}

	public Path getWorkingDirectory() {
		return workingDirectory;
	}

	public ShellDescriptor getShell() {
		return shell;
	}

	public Map<String, String> getEnvironment() {
		return environment;
	}

	public long getShellPid() {
		return shellPid;
	}

	public boolean hasChildProcesses() {
		return hasChildProcesses;
	}

	public List<String> getActiveProcessNames() {
		return activeProcessNames;
	}

	public void setProcessActivityListener(Consumer<ProcessActivity> listener) {
		this.processActivityListener = listener;
		notifyProcessActivity();
	}

	public void start(ShellDescriptor shell, Consumer<String> output) throws IOException {
		start(shell, Charset.defaultCharset(), null, output);
	}

	public void start(ShellDescriptor shell, Charset charset, Consumer<String> output) throws IOException {
		start(shell, charset, null, output);
	}

	public void start(ShellDescriptor shell, Charset charset, Path workingDirectory, Consumer<String> output)
			throws IOException {
		start(shell, charset, workingDirectory, null, output, null);
	}

	public void start(ShellDescriptor shell, Charset charset, Path workingDirectory, Map<String, String> environment,
			Consumer<String> output, Consumer<Integer> exitHandler) throws IOException {
		if (running.get()) {
			throw new IllegalStateException("Session already running");
		}
		this.shell = shell;
		this.charset = charset == null ? Charset.defaultCharset() : charset;
		this.workingDirectory = workingDirectory;
		this.exitNotified.set(false);
		PtyProcessBuilder builder = new PtyProcessBuilder(buildCommand(shell).toArray(new String[0]));
		if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
			builder.setDirectory(workingDirectory.toAbsolutePath().toString());
		}
		Map<String, String> env = environment == null ? buildEnvironment(shell) : new HashMap<>(environment);
		this.environment = Collections.unmodifiableMap(env);
		builder.setEnvironment(env);
		builder.setRedirectErrorStream(true);
		process = builder.start();
		stdin = process.getOutputStream();
		running.set(true);
		shellPid = resolveProcessPid(process);
		hasChildProcesses = false;
		activeProcessNames = Collections.emptyList();
		notifyProcessActivity();
		startChildProcessMonitor();
		applyWindowSize();

		stdoutThread = createReaderThread(process.getInputStream(), output, "terminal-pty");
		stdoutThread.start();
		waitThread = createWaitThread(exitHandler);
		waitThread.start();
	}

	public void stop() {
		running.set(false);
		stopChildProcessMonitor();
		hasChildProcesses = false;
		activeProcessNames = Collections.emptyList();
		notifyProcessActivity();
		if (process != null) {
			process.destroy();
		}
	}

	public void setWindowSize(int columns, int rows) {
		if (columns <= 0 || rows <= 0) {
			return;
		}
		this.columns = columns;
		this.rows = rows;
		applyWindowSize();
	}

	public void send(String data) throws IOException {
		if (!running.get() || stdin == null || data == null || data.isEmpty()) {
			return;
		}
		notifyInput();
		stdin.write(data.getBytes(charset));
		stdin.flush();
	}

	public void sendLine(String line) throws IOException {
		if (line == null) {
			return;
		}
		send(line + System.lineSeparator());
	}

	private Thread createReaderThread(java.io.InputStream input, Consumer<String> output, String name) {
		Thread thread = new Thread(() -> {
			char[] buffer = new char[4096];
			try (InputStreamReader reader = new InputStreamReader(input, charset)) {
				int read;
				while (running.get() && (read = reader.read(buffer)) != -1) {
					if (read > 0) {
						notifyOutput();
						String chunk = new String(buffer, 0, read);
						output.accept(chunk);
					}
				}
			} catch (IOException ignored) {
			}
		}, name);
		thread.setDaemon(true);
		return thread;
	}

	private Thread createWaitThread(Consumer<Integer> exitHandler) {
		Thread thread = new Thread(() -> {
			if (process == null) {
				return;
			}
			int code = -1;
			try {
				code = process.waitFor();
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			} finally {
				running.set(false);
				stopChildProcessMonitor();
				hasChildProcesses = false;
				activeProcessNames = Collections.emptyList();
				shellPid = -1;
				notifyProcessActivity();
				if (exitHandler != null && exitNotified.compareAndSet(false, true)) {
					exitHandler.accept(code);
				}
			}
		}, "terminal-exit");
		thread.setDaemon(true);
		return thread;
	}

	private void applyWindowSize() {
		if (process == null) {
			return;
		}
		try {
			process.setWinSize(new WinSize(columns, rows));
		} catch (Exception ignored) {
		}
	}

	private List<String> buildCommand(ShellDescriptor shell) {
		List<String> args = shell.getArgs();
		List<String> cmd = new java.util.ArrayList<>();
		cmd.add(shell.getCommand());
		cmd.addAll(args);
		return cmd;
	}

	private Map<String, String> buildEnvironment(ShellDescriptor shell) {
		Map<String, String> env = new HashMap<>(System.getenv());
		env.putIfAbsent("TERM", "xterm-256color");
		String shellId = shell == null ? null : shell.getId();
		String command = shell == null ? null : shell.getCommand();
		if (isGitBash(shellId, command)) {
			env.putIfAbsent("MSYSTEM", "MSYS");
			env.putIfAbsent("MSYS2_PATH_TYPE", "inherit");
			env.putIfAbsent("CHERE_INVOKING", "1");
			env.putIfAbsent("MSYS", "enable_pcon");
			env.putIfAbsent("COLORTERM", "truecolor");
		}
		return env;
	}

	private boolean isGitBash(String shellId, String command) {
		if (shellId != null && shellId.toLowerCase().contains("git-bash")) {
			return true;
		}
		if (command == null) {
			return false;
		}
		String lower = command.replace('\\', '/').toLowerCase();
		return lower.contains("/git/") && lower.endsWith("/bash.exe");
	}

	private void startChildProcessMonitor() {
		if (shellPid <= 0) {
			return;
		}
		TerminalChildProcessMonitor monitor = new TerminalChildProcessMonitor(shellPid, shell,
				state -> {
					hasChildProcesses = state.hasChildProcesses();
					activeProcessNames = state.getProcessNames();
					notifyProcessActivity();
				});
		childProcessMonitor = monitor;
		monitor.start();
	}

	private void stopChildProcessMonitor() {
		TerminalChildProcessMonitor monitor = childProcessMonitor;
		childProcessMonitor = null;
		if (monitor != null) {
			monitor.stop();
		}
	}

	private void notifyInput() {
		TerminalChildProcessMonitor monitor = childProcessMonitor;
		if (monitor != null) {
			monitor.handleInput();
		}
	}

	private void notifyOutput() {
		TerminalChildProcessMonitor monitor = childProcessMonitor;
		if (monitor != null) {
			monitor.handleOutput();
		}
	}

	private long resolveProcessPid(PtyProcess process) {
		if (process == null) {
			return -1;
		}
		String[] methodCandidates = new String[] { "pid", "getPid", "getProcessId" };
		for (String methodName : methodCandidates) {
			try {
				Method method = process.getClass().getMethod(methodName);
				Object value = method.invoke(process);
				if (value instanceof Number) {
					return ((Number) value).longValue();
				}
			} catch (Exception ignored) {
			}
		}
		return -1;
	}

	private void notifyProcessActivity() {
		Consumer<ProcessActivity> listener = processActivityListener;
		if (listener == null) {
			return;
		}
		listener.accept(new ProcessActivity(hasChildProcesses, activeProcessNames));
	}

	public static final class ProcessActivity {
		private final boolean hasChildProcesses;
		private final List<String> processNames;

		public ProcessActivity(boolean hasChildProcesses, List<String> processNames) {
			this.hasChildProcesses = hasChildProcesses;
			this.processNames = processNames == null ? Collections.emptyList()
					: Collections.unmodifiableList(new java.util.ArrayList<>(processNames));
		}

		public boolean hasChildProcesses() {
			return hasChildProcesses;
		}

		public List<String> getProcessNames() {
			return processNames;
		}
	}
}
