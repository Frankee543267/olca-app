package org.openlca.app.editors;

import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.progress.UIJob;
import org.openlca.app.App;
import org.openlca.app.M;
import org.openlca.app.devtools.python.PythonEditor;
import org.openlca.app.devtools.sql.SqlEditor;
import org.openlca.app.logging.LogFileEditor;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.Actions;
import org.openlca.app.util.ErrorReporter;
import org.openlca.core.model.CategorizedEntity;

public class Editors {

	private Editors() {
	}

	/**
	 * Adds a refresh function to the tool-bar of the given form (content of a
	 * editor page). When this function is executed the given editor is closed and
	 * opened again.
	 */
	public static void addRefresh(ScrolledForm form, ModelEditor<?> editor) {
		if (form == null || editor == null)
			return;
		CategorizedEntity model = editor.getModel();
		Action refresh = Actions.create(M.Reload, Icon.REFRESH.descriptor(),
				() -> {
					App.closeEditor(model);
					App.openEditor(model);
				});
		IToolBarManager toolbar = form.getToolBarManager();
		toolbar.add(refresh);
	}

	/**
	 * Closes all editors (except start page, Log editor and script editors)
	 */
	public static boolean closeAll() {
		try {
			var refs = new ArrayList<IEditorReference>();
			for (var ref : getReferences()) {
				var editor = ref.getEditor(false);

				// editors that we do not close when
				// closing a database
				if (editor instanceof SqlEditor)
					continue;
				if (editor instanceof PythonEditor)
					continue;
				if (editor instanceof StartPage)
					continue;
				if (editor instanceof LogFileEditor)
					continue;

				refs.add(ref);
			}
			if (refs.size() == 0)
				return true;
			var restArray = refs.toArray(new IEditorReference[0]);
			return getActivePage().closeEditors(restArray, true);
		} catch (Exception e) {
			ErrorReporter.on("Failed to close editors", e);
			return false;
		}
	}

	/**
	 * Calls the save action on the currently opened editor. You should only call
	 * this method when you are sure that there is an active editor opened (e.g.
	 * from a page of this editor).
	 */
	public static void callSaveActive() {
		try {
			IWorkbenchPage page = getActivePage();
			IEditorPart editor = page.getActiveEditor();
			page.saveEditor(editor, true /* confirm */);
		} catch (Exception e) {
			ErrorReporter.on("failed to call `save` on the active editor", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends IEditorPart> T getActive() {
		try {
			return (T) getActivePage().getActiveEditor();
		} catch (ClassCastException e) {
			ErrorReporter.on("Failed to get active editor", e);
			return null;
		}
	}

	public static void open(IEditorInput input, String editorId) {
		new OpenInUIJob(input, editorId).schedule();
	}

	public static void close(IEditorReference ref) {
		try {
			getActivePage().closeEditor(ref.getEditor(false), true);
		} catch (Exception e) {
			ErrorReporter.on("Failed to close an editor", e);
		}
	}

	public static IEditorReference[] getReferences() {
		try {
			return getActivePage().getEditorReferences();
		} catch (Exception e) {
			ErrorReporter.on("Failed to get editor references", e);
			return new IEditorReference[0];
		}
	}

	public static IWorkbenchPage getActivePage() {
		var workbench = PlatformUI.getWorkbench();
		if (workbench == null)
			return null;
		var window = workbench.getActiveWorkbenchWindow();
		if (window == null) {
			var windows = workbench.getWorkbenchWindows();
			if (windows == null || windows.length == 0)
				return null;
			window = windows[0];
		}
		var page = window.getActivePage();
		if (page != null)
			return page;
		var pages = window.getPages();
		return pages == null || pages.length == 0
				? null
				: pages[0];
	}

	private static class OpenInUIJob extends UIJob {

		private final IEditorInput input;
		private final String editorId;

		public OpenInUIJob(IEditorInput input, String editorId) {
			super(M.OpenEditor);
			this.input = input;
			this.editorId = editorId;
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			try {
				getActivePage().openEditor(input, editorId);
				return Status.OK_STATUS;
			} catch (Exception e) {
				ErrorReporter.on("Open editor " + editorId + " failed.", e);
				return Status.CANCEL_STATUS;
			}
		}
	}

}
