package me.anyx.terminal4e.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

import net.sf.image4j.codec.ico.ICODecoder;

final class ShellIconProvider {
	private static final String REGISTRY_PREFIX = "terminal.shell.icon.";

	private ShellIconProvider() {
	}

	static Image getShellImage(String command) {
		return getShellImage(command, null);
	}

	static Image getShellImage(String command, String iconPath) {
		String iconKey = normalizeKey(iconPath);
		String commandKey = normalizeKey(command);
		if ((iconKey == null || iconKey.isEmpty()) && (commandKey == null || commandKey.isEmpty())) {
			return null;
		}
		String key = REGISTRY_PREFIX + (iconKey != null && !iconKey.isEmpty() ? "icon:" + iconKey : "cmd:" + commandKey);
		ImageRegistry registry = JFaceResources.getImageRegistry();
		Image cached = registry.get(key);
		if (cached != null) {
			return cached;
		}
		ImageDescriptor descriptor = createDescriptor(iconPath, command);
		if (descriptor == null) {
			descriptor = me.anyx.terminal4e.Images.getImageDescriptor(
					me.anyx.terminal4e.Images.ICON_TERMINAL);
		}
		registry.put(key, descriptor);
		return registry.get(key);
	}

	private static ImageDescriptor createDescriptor(String iconPath, String command) {
		Path iconFile = resolveIconPath(iconPath, command);
		if (iconFile == null || !Files.exists(iconFile)) {
			return null;
		}
		ImageData data = isImageFile(iconFile) ? getImageFileImageData(iconFile.toFile())
				: getFileIconImageData(iconFile.toFile());
		if (data == null) {
			return null;
		}
		return ImageDescriptor.createFromImageDataProvider((zoom) -> {
	         return zoom == 100 ? data : null;
	      });
	}

	private static Path resolveIconPath(String iconPath, String command) {
		String normalizedIcon = normalizePath(iconPath);
		if (normalizedIcon != null && !normalizedIcon.isEmpty()) {
			Path direct = Paths.get(normalizedIcon);
			if (Files.exists(direct)) {
				return direct;
			}
		}
		return resolveExecutablePath(command);
	}

	private static Path resolveExecutablePath(String command) {
		String normalized = normalizeCommand(command);
		Path direct = Paths.get(normalized);
		if (Files.exists(direct)) {
			return direct;
		}
		String pathEnv = System.getenv("PATH");
		if (pathEnv != null) {
			String[] parts = pathEnv.split(";");
			for (String part : parts) {
				if (part == null || part.trim().isEmpty()) {
					continue;
				}
				Path candidate = Paths.get(part.trim(), normalized);
				if (Files.exists(candidate)) {
					return candidate;
				}
				if (!normalized.toLowerCase().endsWith(".exe")) {
					Path exeCandidate = Paths.get(part.trim(), normalized + ".exe");
					if (Files.exists(exeCandidate)) {
						return exeCandidate;
					}
				}
			}
		}
		String systemRoot = System.getenv("SystemRoot");
		if (systemRoot != null && !systemRoot.trim().isEmpty()) {
			Path system32 = Paths.get(systemRoot, "System32", normalized);
			if (Files.exists(system32)) {
				return system32;
			}
			if (!normalized.toLowerCase().endsWith(".exe")) {
				Path system32Exe = Paths.get(systemRoot, "System32", normalized + ".exe");
				if (Files.exists(system32Exe)) {
					return system32Exe;
				}
			}
		}
		return null;
	}

	private static String normalizeCommand(String command) {
		String trimmed = command == null ? "" : command.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static String normalizePath(String path) {
		if (path == null) {
			return "";
		}
		return normalizeCommand(path);
	}

	private static String normalizeKey(String value) {
		String normalized = normalizePath(value);
		return normalized == null ? "" : normalized.trim().toLowerCase();
	}

	private static ImageData getFileIconImageData(File file) {
		try {
			Icon icon = FileSystemView.getFileSystemView().getSystemIcon(file);
			if (icon == null) {
				return null;
			}
			BufferedImage image = toBufferedImage(icon);
			if (image == null) {
				return null;
			}
			return toImageData(image);
		} catch (Exception ex) {
			return null;
		}
	}

	private static ImageData getImageFileImageData(File file) {
		try {
		    BufferedImage image;
		    if (file.getName().toLowerCase(Locale.ROOT).endsWith(".ico")) {
                // Java ImageIO does not support ICO format by default
		        image = ICODecoder.read(file).stream().findFirst().orElse(null);
            } else {
                image = ImageIO.read(file);
            }
			if (image == null) {
				return null;
			}
			return toImageData(image);
		} catch (Exception ex) {
			return null;
		}
	}

	private static boolean isImageFile(Path path) {
		if (path == null) {
			return false;
		}
		String name = path.getFileName() == null ? "" : path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot <= 0 || dot >= name.length() - 1) {
			return false;
		}
		String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
		return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) || "gif".equals(ext)
				|| "bmp".equals(ext) || "ico".equals(ext);
	}

	private static BufferedImage toBufferedImage(Icon icon) {
		int width = Math.max(1, icon.getIconWidth());
		int height = Math.max(1, icon.getIconHeight());
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		try {
			icon.paintIcon(null, g2d, 0, 0);
		} finally {
			g2d.dispose();
		}
		return image;
	}

	private static ImageData toImageData(BufferedImage buffered) {
		int width = buffered.getWidth();
		int height = buffered.getHeight();
		PaletteData palette = new PaletteData(0x00FF0000, 0x0000FF00, 0x000000FF);
		ImageData data = new ImageData(width, height, 32, palette);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = buffered.getRGB(x, y);
				int alpha = (argb >> 24) & 0xFF;
				int red = (argb >> 16) & 0xFF;
				int green = (argb >> 8) & 0xFF;
				int blue = argb & 0xFF;
				int pixel = (red << 16) | (green << 8) | blue;
				data.setPixel(x, y, pixel);
				data.setAlpha(x, y, alpha);
			}
		}
		return data;
	}
}
