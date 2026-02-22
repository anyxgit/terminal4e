package me.anyx.terminal4e.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class TerminalChildProcessMonitor {
    private static final String DEBUG_KEY = "terminal4e.debug.childMonitor";
    private static final long ACTIVE_DEBOUNCE_MS = 1000;
    private static final long INACTIVE_THROTTLE_MS = 5000;
    private static final boolean DEBUG_ENABLED = resolveDebugEnabled();

    private final long rootPid;
    private final WslContext wslContext;
    private final Set<String> sessionIgnoredNames;
    private final Consumer<DetectionState> onStateChanged;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private volatile DetectionState lastKnownState;
    private volatile long lastInactiveRefreshAt;
    private volatile ScheduledFuture<?> pendingActiveRefresh;

    TerminalChildProcessMonitor(long rootPid, ShellDescriptor shell, String sessionMarker,
            Consumer<DetectionState> onStateChanged) {
        this.rootPid = rootPid;
        this.wslContext = WslContext.from(shell, sessionMarker);
        this.sessionIgnoredNames = buildSessionIgnoredNames(shell, this.wslContext);
        this.onStateChanged = onStateChanged;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "terminal-child-monitor");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    void start() {
        scheduleRefresh(0);
    }

    void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> pending = pendingActiveRefresh;
        if (pending != null) {
            pending.cancel(false);
        }
        executor.shutdownNow();
    }

    void handleInput() {
        if (stopped.get()) {
            return;
        }
        ScheduledFuture<?> pending = pendingActiveRefresh;
        if (pending != null) {
            pending.cancel(false);
        }
        pendingActiveRefresh = scheduleRefresh(ACTIVE_DEBOUNCE_MS);
    }

    void handleOutput() {
        if (stopped.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastInactiveRefreshAt) < INACTIVE_THROTTLE_MS) {
            return;
        }
        lastInactiveRefreshAt = now;
        scheduleRefresh(0);
    }

    private ScheduledFuture<?> scheduleRefresh(long delayMs) {
        if (stopped.get()) {
            return null;
        }
        return executor.schedule(this::refreshNow, delayMs, TimeUnit.MILLISECONDS);
    }

    private void refreshNow() {
        if (stopped.get()) {
            return;
        }
        InspectionResult inspection = ProcessTreeInspector.inspect(rootPid, wslContext, sessionIgnoredNames);
        DetectionState next = DetectionState.fromInspection(inspection);
        DetectionState previous = lastKnownState;
        if (previous == null || !previous.equals(next)) {
            lastKnownState = next;
            printDiagnostics(inspection);
            onStateChanged.accept(next);
        }
    }

    private void printDiagnostics(InspectionResult inspection) {
        if (!DEBUG_ENABLED) {
            return;
        }
        String state = inspection.hasMeaningfulProcesses() ? "BLOCKING" : "IDLE";
        System.out.println("[terminal4e-monitor] state=" + state + " rootPid=" + rootPid + " shell="
                + (shellSummary()) + " source=" + inspection.source);
        if (inspection.matches.isEmpty()) {
            System.out.println("[terminal4e-monitor] matches=(none)");
            return;
        }
        for (ProcessEntry match : inspection.matches) {
            System.out.println("[terminal4e-monitor] match pid=" + match.pid + " ppid=" + match.parentPid + " tty="
                    + match.tty + " name=" + match.name + " cmd=" + match.commandLine + " source=" + match.source);
        }
    }

    private String shellSummary() {
        if (wslContext != null && wslContext.isEnabled()) {
            return "wsl:" + wslContext.distribution;
        }
        return "native";
    }

    private static Set<String> buildSessionIgnoredNames(ShellDescriptor shell, WslContext wslContext) {
        Set<String> result = new HashSet<>();
        if (shell != null) {
            addProcessNameVariants(result, shell.getCommand());
        }
        if (wslContext != null && wslContext.isEnabled()) {
            result.add("wsl.exe");
            result.add("wsl");
        }
        return result;
    }

    private static void addProcessNameVariants(Set<String> result, String commandPath) {
        if (result == null || commandPath == null || commandPath.trim().isEmpty()) {
            return;
        }
        String normalized = commandPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return;
        }
        result.add(lower);
        if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat")) {
            result.add(lower.substring(0, lower.lastIndexOf('.')));
        }
    }

    private static boolean resolveDebugEnabled() {
        String property = System.getProperty(DEBUG_KEY);
        if (isTruthy(property)) {
            return true;
        }
        String env = System.getenv("TERMINAL4E_DEBUG_CHILD_MONITOR");
        return isTruthy(env);
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    private static final class ProcessTreeInspector {
        private static final Set<String> WINDOWS_IGNORED = new HashSet<>(Arrays.asList(
                "conhost.exe",
                "openconsole.exe",
                "winpty.exe",
                "winpty-agent.exe",
                "wslhost.exe"));

        private static final Set<String> UNIX_IGNORED = new HashSet<>(Arrays.asList(
                "login",
                "systemd",
                "init",
                "launchd"));

        private static final Set<String> WSL_SHELL_NAMES = new HashSet<>(Arrays.asList(
                "bash",
                "zsh",
                "fish",
                "sh",
                "dash",
                "ksh",
                "tcsh",
                "csh"));

        private ProcessTreeInspector() {
        }

        static InspectionResult inspect(long rootPid, WslContext wslContext, Set<String> sessionIgnoredNames) {
            List<ProcessEntry> matches = new ArrayList<>();
            if (rootPid <= 0) {
                collectMeaningfulWslProcesses(wslContext, matches, sessionIgnoredNames);
                return new InspectionResult("wsl-only", matches);
            }
            List<ProcessEntry> entries = isWindows() ? listWindowsProcesses() : listUnixProcesses();
            if (entries.isEmpty()) {
                collectMeaningfulWslProcesses(wslContext, matches, sessionIgnoredNames);
                return new InspectionResult("wsl-fallback", matches);
            }
            Map<Long, List<ProcessEntry>> byParent = new HashMap<>();
            for (ProcessEntry entry : entries) {
                byParent.computeIfAbsent(Long.valueOf(entry.parentPid), key -> new ArrayList<>()).add(entry);
            }

            ArrayDeque<ProcessEntry> queue = new ArrayDeque<>();
            List<ProcessEntry> directChildren = byParent.get(Long.valueOf(rootPid));
            if (directChildren != null) {
                queue.addAll(directChildren);
            }

            Set<Long> visited = new HashSet<>();
            while (!queue.isEmpty()) {
                ProcessEntry current = queue.removeFirst();
                if (!visited.add(Long.valueOf(current.pid))) {
                    continue;
                }
                if (!isIgnoredProcess(current.name, sessionIgnoredNames)) {
                    current.source = "native-tree";
                    matches.add(current);
                }
                List<ProcessEntry> children = byParent.get(Long.valueOf(current.pid));
                if (children != null && !children.isEmpty()) {
                    queue.addAll(children);
                }
            }
            collectMeaningfulWslProcesses(wslContext, matches, sessionIgnoredNames);
            return new InspectionResult("native+optional-wsl", matches);
        }

        private static void collectMeaningfulWslProcesses(WslContext wslContext, List<ProcessEntry> matches,
            Set<String> sessionIgnoredNames) {
            if (wslContext == null || !wslContext.isEnabled()) {
                return;
            }
            List<String> lines = runCommand(Arrays.asList(
                    wslContext.command,
                    "-d",
                    wslContext.distribution,
                    "-e",
                    "sh",
                    "-lc",
                    "ps -eo pid=,ppid=,tty=,comm=,args="), 6);
            if (lines.isEmpty()) {
                return;
            }
            List<ProcessEntry> entries = parseWslProcessEntries(lines);
            if (entries.isEmpty()) {
                return;
            }

            Long sessionRootPid = resolveWslSessionRootPid(wslContext, entries);
            if (sessionRootPid == null || sessionRootPid.longValue() <= 0) {
                return;
            }

            Map<Long, List<ProcessEntry>> byParent = new HashMap<>();
            for (ProcessEntry entry : entries) {
                byParent.computeIfAbsent(Long.valueOf(entry.parentPid), key -> new ArrayList<>()).add(entry);
            }

            ArrayDeque<ProcessEntry> queue = new ArrayDeque<>();
            List<ProcessEntry> directChildren = byParent.get(sessionRootPid);
            if (directChildren != null) {
                queue.addAll(directChildren);
            }

            Set<Long> visited = new HashSet<>();
            while (!queue.isEmpty()) {
                ProcessEntry current = queue.removeFirst();
                if (!visited.add(Long.valueOf(current.pid))) {
                    continue;
                }
                if (!current.tty.startsWith("pts/")) {
                    List<ProcessEntry> children = byParent.get(Long.valueOf(current.pid));
                    if (children != null) {
                        queue.addAll(children);
                    }
//                    continue;
                }
                if (current.name.isEmpty()) {
                    continue;
                }
                if (sessionIgnoredNames != null && sessionIgnoredNames.contains(current.name)) {
                    // ignore shell bridge itself
                } else if (!"ps".equals(current.name)
                        && !"sh".equals(current.name)
                        && !WSL_SHELL_NAMES.contains(current.name)) {
                    current.source = "wsl-subtree";
                    matches.add(current);
                }
                List<ProcessEntry> children = byParent.get(Long.valueOf(current.pid));
                if (children != null) {
                    queue.addAll(children);
                }
            }
        }

        private static List<ProcessEntry> parseWslProcessEntries(List<String> lines) {
            List<ProcessEntry> entries = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 5);
                if (parts.length < 4) {
                    continue;
                }
                Long pid = parseLong(parts[0]);
                Long ppid = parseLong(parts[1]);
                if (pid == null || ppid == null) {
                    continue;
                }
                String tty = parts[2] == null ? "" : parts[2].trim().toLowerCase(Locale.ROOT);
                String command = parts[3] == null ? "" : parts[3].trim().toLowerCase(Locale.ROOT);
                String args = parts.length >= 5 && parts[4] != null ? parts[4].trim() : command;
                entries.add(new ProcessEntry(pid.longValue(), ppid.longValue(), command, tty, args));
            }
            return entries;
        }

        private static Long resolveWslSessionRootPid(WslContext wslContext, List<ProcessEntry> entries) {
            if (wslContext == null || !wslContext.hasSessionMarker()) {
                return null;
            }
            String escapedMarker = shellSingleQuote(wslContext.sessionMarker);
            String script = "for p in /proc/[0-9]*; do "
                    + "[ -r \"$p/environ\" ] || continue; "
                    + "if tr '\\0' '\\n' < \"$p/environ\" 2>/dev/null | grep -Fxq 'TERMINAL4E_SESSION_ID=" + escapedMarker + "'; then "
                    + "printf '%s\\n' \"${p##*/}\"; "
                    + "fi; "
                    + "done";
            List<String> lines = runCommand(Arrays.asList(
                    wslContext.command,
                    "-d",
                    wslContext.distribution,
                    "-e",
                    "sh",
                    "-lc",
                    script), 6);
            if (lines.isEmpty()) {
                return null;
            }

            Set<Long> candidatePids = new HashSet<>();
            for (String line : lines) {
                Long pid = parseLong(line);
                if (pid != null && pid.longValue() > 0) {
                    candidatePids.add(pid);
                }
            }
            if (candidatePids.isEmpty()) {
                return null;
            }

            Map<Long, ProcessEntry> byPid = new HashMap<>();
            for (ProcessEntry entry : entries) {
                byPid.put(Long.valueOf(entry.pid), entry);
            }

            Long best = null;
            for (Long candidate : candidatePids) {
                ProcessEntry entry = byPid.get(candidate);
                if (entry == null) {
                    continue;
                }
                if (candidatePids.contains(Long.valueOf(entry.parentPid))) {
                    continue;
                }
                best = candidate;
                break;
            }
            if (best != null) {
                return best;
            }
            return candidatePids.iterator().next();
        }

        private static String shellSingleQuote(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("'", "'\\''");
        }

        private static boolean isIgnoredProcess(String name, Set<String> sessionIgnoredNames) {
            if (name == null || name.trim().isEmpty()) {
                return false;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            if (sessionIgnoredNames != null && sessionIgnoredNames.contains(normalized)) {
                return true;
            }
            return isWindows() ? WINDOWS_IGNORED.contains(normalized) : UNIX_IGNORED.contains(normalized);
        }

        private static List<ProcessEntry> listUnixProcesses() {
            List<String> lines = runCommand(Arrays.asList("ps", "-eo", "pid=,ppid=,comm="), 3);
            if (lines.isEmpty()) {
                lines = runCommand(Arrays.asList("ps", "-ax", "-o", "pid=", "-o", "ppid=", "-o", "comm="), 3);
            }
            if (lines.isEmpty()) {
                return Collections.emptyList();
            }

            List<ProcessEntry> result = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 3);
                if (parts.length < 3) {
                    continue;
                }
                Long pid = parseLong(parts[0]);
                Long ppid = parseLong(parts[1]);
                if (pid == null || ppid == null) {
                    continue;
                }
                result.add(new ProcessEntry(pid.longValue(), ppid.longValue(), parts[2].trim()));
            }
            return result;
        }

        private static List<ProcessEntry> listWindowsProcesses() {
            List<String> lines = runCommand(
                    Arrays.asList("wmic", "process", "get", "ProcessId,ParentProcessId,Name", "/FORMAT:CSV"), 5);
            List<ProcessEntry> fromWmic = parseWindowsCsv(lines);
            if (!fromWmic.isEmpty()) {
                return fromWmic;
            }

            List<String> psLines = runCommand(Arrays.asList(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "Get-CimInstance Win32_Process | Select-Object Name,ParentProcessId,ProcessId | ConvertTo-Csv -NoTypeInformation"),
                    6);
            return parseWindowsCsv(psLines);
        }

        private static List<ProcessEntry> parseWindowsCsv(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return Collections.emptyList();
            }
            List<ProcessEntry> result = new ArrayList<>();
            Map<String, Integer> headerIndex = null;
            for (String rawLine : lines) {
                if (rawLine == null) {
                    continue;
                }
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                if (cols.isEmpty()) {
                    continue;
                }
                if (headerIndex == null) {
                    headerIndex = buildHeaderIndex(cols);
                    continue;
                }
                Integer pidIndex = headerIndex.get("processid");
                Integer ppidIndex = headerIndex.get("parentprocessid");
                Integer nameIndex = headerIndex.get("name");
                if (pidIndex == null || ppidIndex == null || nameIndex == null) {
                    continue;
                }
                if (pidIndex.intValue() >= cols.size() || ppidIndex.intValue() >= cols.size()
                        || nameIndex.intValue() >= cols.size()) {
                    continue;
                }
                Long pid = parseLong(cols.get(pidIndex.intValue()));
                Long ppid = parseLong(cols.get(ppidIndex.intValue()));
                if (pid == null || ppid == null) {
                    continue;
                }
                String name = cols.get(nameIndex.intValue()) == null ? "" : cols.get(nameIndex.intValue()).trim();
                result.add(new ProcessEntry(pid.longValue(), ppid.longValue(), name));
            }
            return result;
        }

        private static Map<String, Integer> buildHeaderIndex(List<String> cols) {
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                String value = cols.get(i);
                if (value == null) {
                    continue;
                }
                map.put(value.trim().toLowerCase(Locale.ROOT), Integer.valueOf(i));
            }
            return map;
        }

        private static List<String> parseCsvLine(String line) {
            List<String> values = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (c == ',' && !inQuotes) {
                    values.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            values.add(current.toString());
            return values;
        }

        private static Long parseLong(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Long.valueOf(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static List<String> runCommand(List<String> command, int timeoutSeconds) {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = null;
            try {
                process = builder.start();
                List<String> lines;
                try (InputStream input = process.getInputStream();
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(input, Charset.defaultCharset()))) {
                    lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return Collections.emptyList();
                }
                return lines;
            } catch (Exception ignored) {
                return Collections.emptyList();
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        }
    }

    private static final class WslContext {
        private final String command;
        private final String distribution;
        private final String sessionMarker;

        private WslContext(String command, String distribution, String sessionMarker) {
            this.command = command;
            this.distribution = distribution;
            this.sessionMarker = sessionMarker;
        }

        private boolean isEnabled() {
            return command != null && !command.trim().isEmpty() && distribution != null
                    && !distribution.trim().isEmpty();
        }

        private boolean hasSessionMarker() {
            return sessionMarker != null && !sessionMarker.trim().isEmpty();
        }

        private static WslContext from(ShellDescriptor shell, String sessionMarker) {
            if (shell == null) {
                return disabled();
            }
            String command = shell.getCommand();
            if (command == null || command.trim().isEmpty()) {
                return disabled();
            }
            String normalized = command.replace('\\', '/').toLowerCase(Locale.ROOT);
            String id = shell.getId() == null ? "" : shell.getId().toLowerCase(Locale.ROOT);
            if (!normalized.endsWith("/wsl.exe") && !"wsl".equals(id) && !id.startsWith("wsl-")) {
                return disabled();
            }
            String distribution = extractDistribution(shell.getArgs());
            if (distribution == null || distribution.trim().isEmpty()) {
                return disabled();
            }
            return new WslContext(command, distribution, sessionMarker);
        }

        private static String extractDistribution(List<String> args) {
            if (args == null || args.isEmpty()) {
                return null;
            }
            for (int i = 0; i < args.size(); i++) {
                String value = args.get(i);
                if (value == null) {
                    continue;
                }
                if (("-d".equals(value) || "--distribution".equals(value)) && i + 1 < args.size()) {
                    String distro = args.get(i + 1);
                    if (distro != null && !distro.trim().isEmpty()) {
                        return distro.trim();
                    }
                }
            }
            return null;
        }

        private static WslContext disabled() {
            return new WslContext(null, null, null);
        }
    }

    private static final class ProcessEntry {
        private final long pid;
        private final long parentPid;
        private final String name;
        private final String tty;
        private final String commandLine;
        private String source = "unknown";

        private ProcessEntry(long pid, long parentPid, String name) {
            this(pid, parentPid, name, "", name);
        }

        private ProcessEntry(long pid, long parentPid, String name, String tty, String commandLine) {
            this.pid = pid;
            this.parentPid = parentPid;
            this.name = name;
            this.tty = tty == null ? "" : tty;
            this.commandLine = commandLine == null ? "" : commandLine;
        }
    }

    private static final class InspectionResult {
        private final String source;
        private final List<ProcessEntry> matches;

        private InspectionResult(String source, List<ProcessEntry> matches) {
            this.source = source == null ? "unknown" : source;
            this.matches = matches == null ? Collections.emptyList() : new ArrayList<>(matches);
        }

        private boolean hasMeaningfulProcesses() {
            return !matches.isEmpty();
        }
    }

    static final class DetectionState {
        private final boolean hasChildProcesses;
        private final List<String> processNames;

        private DetectionState(boolean hasChildProcesses, List<String> processNames) {
            this.hasChildProcesses = hasChildProcesses;
            this.processNames = processNames == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(processNames));
        }

        static DetectionState fromInspection(InspectionResult inspection) {
            if (inspection == null || inspection.matches == null || inspection.matches.isEmpty()) {
                return new DetectionState(false, Collections.emptyList());
            }
            List<String> names = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ProcessEntry entry : inspection.matches) {
                if (entry == null || entry.name == null) {
                    continue;
                }
                String name = entry.name.trim();
                if (name.isEmpty()) {
                    continue;
                }
                String normalized = name.toLowerCase(Locale.ROOT);
                if (seen.add(normalized)) {
                    names.add(name);
                }
            }
            return new DetectionState(!names.isEmpty(), names);
        }

        boolean hasChildProcesses() {
            return hasChildProcesses;
        }

        List<String> getProcessNames() {
            return processNames;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DetectionState)) {
                return false;
            }
            DetectionState other = (DetectionState) obj;
            return hasChildProcesses == other.hasChildProcesses && processNames.equals(other.processNames);
        }

        @Override
        public int hashCode() {
            return Boolean.valueOf(hasChildProcesses).hashCode() * 31 + processNames.hashCode();
        }
    }
}
