package me.anyx.terminal4e.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

public final class AnsiTextRenderer {
	private AnsiTextRenderer() {
	}

	public static void append(StyledText target, String chunk, AnsiState state) {
		if (chunk == null || chunk.isEmpty()) {
			return;
		}
		Display display = target.getDisplay();
		List<Segment> segments = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		int i = 0;
		while (i < chunk.length()) {
			char c = chunk.charAt(i);
			if (c == 0x1B && i + 1 < chunk.length() && chunk.charAt(i + 1) == '[') {
				if (plain.length() > 0) {
					segments.add(new Segment(plain.toString(), state.getForeground(), state.getBackground()));
					plain.setLength(0);
				}
				int mIndex = chunk.indexOf('m', i + 2);
				if (mIndex == -1) {
					plain.append(chunk.substring(i));
					break;
				}
				String codes = chunk.substring(i + 2, mIndex);
				applyCodes(codes, state, display);
				i = mIndex + 1;
				continue;
			}
			plain.append(c);
			i++;
		}
		if (plain.length() > 0) {
			segments.add(new Segment(plain.toString(), state.getForeground(), state.getBackground()));
		}

		int offset = target.getCharCount();
		for (Segment segment : segments) {
			if (segment.text.isEmpty()) {
				continue;
			}
			target.append(segment.text);
			if (segment.foreground != null || segment.background != null) {
				StyleRange style = new StyleRange();
				style.start = offset;
				style.length = segment.text.length();
				style.foreground = segment.foreground;
				style.background = segment.background;
				target.setStyleRange(style);
			}
			offset += segment.text.length();
		}
		target.setSelection(target.getCharCount());
	}

	private static void applyCodes(String codes, AnsiState state, Display display) {
		if (codes == null || codes.isEmpty()) {
			state.reset();
			return;
		}
		String[] parts = codes.split(";");
		for (String part : parts) {
			if (part.isEmpty()) {
				state.reset();
				continue;
			}
			int code;
			try {
				code = Integer.parseInt(part);
			} catch (NumberFormatException ex) {
				continue;
			}
			if (code == 0) {
				state.reset();
			} else if (code == 39) {
				state.setForeground(null);
			} else if (code == 49) {
				state.setBackground(null);
			} else if (code >= 30 && code <= 37) {
				state.setForeground(mapColor(display, code - 30, false));
			} else if (code >= 90 && code <= 97) {
				state.setForeground(mapColor(display, code - 90, true));
			} else if (code >= 40 && code <= 47) {
				state.setBackground(mapColor(display, code - 40, false));
			} else if (code >= 100 && code <= 107) {
				state.setBackground(mapColor(display, code - 100, true));
			}
		}
	}

	private static Color mapColor(Display display, int index, boolean bright) {
		switch (index) {
			case 0:
				return display.getSystemColor(bright ? SWT.COLOR_DARK_GRAY : SWT.COLOR_BLACK);
			case 1:
				return display.getSystemColor(bright ? SWT.COLOR_RED : SWT.COLOR_DARK_RED);
			case 2:
				return display.getSystemColor(bright ? SWT.COLOR_GREEN : SWT.COLOR_DARK_GREEN);
			case 3:
				return display.getSystemColor(bright ? SWT.COLOR_YELLOW : SWT.COLOR_DARK_YELLOW);
			case 4:
				return display.getSystemColor(bright ? SWT.COLOR_BLUE : SWT.COLOR_DARK_BLUE);
			case 5:
				return display.getSystemColor(bright ? SWT.COLOR_MAGENTA : SWT.COLOR_DARK_MAGENTA);
			case 6:
				return display.getSystemColor(bright ? SWT.COLOR_CYAN : SWT.COLOR_DARK_CYAN);
			case 7:
				return display.getSystemColor(bright ? SWT.COLOR_WHITE : SWT.COLOR_GRAY);
			default:
				return null;
		}
	}

	private static final class Segment {
		private final String text;
		private final Color foreground;
		private final Color background;

		private Segment(String text, Color foreground, Color background) {
			this.text = text;
			this.foreground = foreground;
			this.background = background;
		}
	}
}
