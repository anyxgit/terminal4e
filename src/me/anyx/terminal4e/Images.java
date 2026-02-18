package me.anyx.terminal4e;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

/**
 * Images
 *
 * @version 1.0
 * @author anyx
 */
public final class Images {

    private Images() {
    }

    public static final String ICON_TERMINAL = "/icons/terminal-16.png";
    public static final String ICON_DISCONNECT = "/icons/disconnect-16.png";
    public static final String ICON_SETTINGS = "/icons/settings-16.png";
    public static final String ICON_DUPLICATE_TERMINAL = "/icons/duplicate-terminal-16.png";

    public static Image getImage(String filePath) {
        return getImageDescriptor(filePath).createImage();
    }

    public static ImageDescriptor getImageDescriptor(String filePath) {
        return ImageDescriptor.createFromFile(Images.class, filePath);
    }
}
