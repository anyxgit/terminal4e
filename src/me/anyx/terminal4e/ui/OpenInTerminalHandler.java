package me.anyx.terminal4e.ui;

import java.nio.file.Path;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

public class OpenInTerminalHandler extends AbstractHandler {
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		if (window == null) {
			return null;
		}
		IWorkbenchPage page = window.getActivePage();
		if (page == null) {
			return null;
		}
		Path workingDirectory = resolveWorkingDirectory(HandlerUtil.getCurrentSelection(event));
		try {
			TerminalView view = (TerminalView) page.showView(TerminalView.ID);
			if (view != null) {
				view.openNewSessionAtPath(workingDirectory);
			}
		} catch (Exception ex) {
			return null;
		}
		return null;
	}

	private Path resolveWorkingDirectory(ISelection selection) {
		IResource resource = resolveResource(selection);
		if (resource == null) {
			return null;
		}
		if (resource.getType() == IResource.FILE && resource.getParent() != null) {
			IPath parentLocation = resource.getParent().getLocation();
			return parentLocation == null ? null : parentLocation.toFile().toPath();
		}
		IPath location = resource.getLocation();
		return location == null ? null : location.toFile().toPath();
	}

	private IResource resolveResource(ISelection selection) {
		if (!(selection instanceof IStructuredSelection)) {
			return null;
		}
		Object element = ((IStructuredSelection) selection).getFirstElement();
		if (element instanceof IResource) {
			return (IResource) element;
		}
		if (element instanceof IAdaptable) {
			return ((IAdaptable) element).getAdapter(IResource.class);
		}
		return null;
	}
}
