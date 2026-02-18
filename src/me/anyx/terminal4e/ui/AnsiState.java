package me.anyx.terminal4e.ui;

import org.eclipse.swt.graphics.Color;

public final class AnsiState {
	private Color foreground;
	private Color background;

	public Color getForeground() {
		return foreground;
	}

	public void setForeground(Color foreground) {
		this.foreground = foreground;
	}

	public Color getBackground() {
		return background;
	}

	public void setBackground(Color background) {
		this.background = background;
	}

	public void reset() {
		foreground = null;
		background = null;
	}
}
