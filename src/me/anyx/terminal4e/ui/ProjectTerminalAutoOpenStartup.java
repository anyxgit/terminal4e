package me.anyx.terminal4e.ui;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import me.anyx.terminal4e.Activator;

public class ProjectTerminalAutoOpenStartup implements IStartup, IResourceChangeListener {
	@Override
	public void earlyStartup() {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if (!isAutoOpenEnabled()) {
			return;
		}
		IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return;
		}
		Set<IProject> targets = new LinkedHashSet<>();
		try {
			delta.accept(child -> {
				IResource resource = child.getResource();
				if (resource instanceof IProject) {
					IProject project = (IProject) resource;
					if (shouldOpenForDelta(child, project)) {
						targets.add(project);
					}
					return false;
				}
				return true;
			});
		} catch (CoreException ignored) {
			return;
		}
		if (targets.isEmpty()) {
			return;
		}
		Display display = Display.getDefault();
		display.asyncExec(() -> openTerminals(targets));
	}

	private boolean isAutoOpenEnabled() {
		Activator plugin = Activator.getDefault();
		if (plugin == null) {
			return false;
		}
		IPreferenceStore store = plugin.getPreferenceStore();
		return store.getBoolean(Activator.PREF_AUTO_OPEN_PROJECT_TERMINAL);
	}

	private boolean shouldOpenForDelta(IResourceDelta delta, IProject project) {
		if (project == null || !project.exists()) {
			return false;
		}
		if (delta.getKind() == IResourceDelta.ADDED) {
			return project.isOpen();
		}
		if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
			return project.isOpen();
		}
		return false;
	}

	private void openTerminals(Set<IProject> projects) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		if (window == null) {
			IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();
			if (windows.length > 0) {
				window = windows[0];
			}
		}
		if (window == null) {
			return;
		}
		IWorkbenchPage page = window.getActivePage();
		if (page == null && window.getPages().length > 0) {
			page = window.getPages()[0];
		}
		if (page == null) {
			return;
		}
		TerminalView view;
		try {
			view = (TerminalView) page.showView(TerminalView.ID);
		} catch (Exception ignored) {
			return;
		}
		if (view == null) {
			return;
		}
		for (IProject project : projects) {
			IPath location = project.getLocation();
			if (location == null) {
				continue;
			}
			Path workingDirectory = location.toFile().toPath();
			view.openNewSessionAtPath(workingDirectory);
		}
	}
}
