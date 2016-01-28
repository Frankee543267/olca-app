package org.openlca.app.editors.reports;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.openlca.app.M;
import org.openlca.app.editors.reports.model.Report;
import org.openlca.app.rcp.html.HtmlPage;
import org.openlca.app.rcp.html.HtmlView;
import org.openlca.app.util.Editors;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.util.UI;
import org.openlca.core.model.ModelType;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class ReportViewer extends FormEditor {

	public static String ID = "ReportViewer";
	private Logger log = LoggerFactory.getLogger(getClass());
	private Report report;

	public static void open(Report report) {
		if (report == null)
			return;
		Editors.open(new EditorInput(report), ID);
	}

	public Report getReport() {
		return report;
	}

	@Override
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		super.init(site, input);
		try {
			EditorInput editorInput = (EditorInput) input;
			this.report = editorInput.getReport();
		} catch (Exception e) {
			String message = "failed to init report viewer";
			log.error(message, e);
			throw new PartInitException(message, e);
		}
	}

	@Override
	protected void addPages() {
		try {
			addPage(new Page());
		} catch (Exception e) {
			log.error("failed to add start page", e);
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
	}

	@Override
	public void doSaveAs() {
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	private static class EditorInput implements IEditorInput {
		private final Report report;

		public EditorInput(Report report) {
			this.report = report;
		}

		public Report getReport() {
			return report;
		}

		@Override
		@SuppressWarnings("rawtypes")
		public Object getAdapter(Class adapter) {
			return null;
		}

		@Override
		public boolean exists() {
			return report != null;
		}

		@Override
		public ImageDescriptor getImageDescriptor() {
			return Images.descriptor(ModelType.PROJECT);
		}

		@Override
		public String getName() {
			String name = report.title != null ? report.title
					: M.Report;
			return Strings.cut(name, 75);
		}

		@Override
		public IPersistableElement getPersistable() {
			return null;
		}

		@Override
		public String getToolTipText() {
			return report.title != null ? report.title
					: M.Report;
		}
	}

	private class Page extends FormPage implements HtmlPage {

		private Browser browser;

		public Page() {
			super(ReportViewer.this, "olca.ReportPreview.Page",
					M.ReportView);
		}

		@Override
		public String getUrl() {
			return HtmlView.REPORT_VIEW.getUrl();
		}

		@Override
		public void onLoaded() {
			Gson gson = new Gson();
			String json = gson.toJson(report);
			System.out.println(json);
			String messages = M.asJson();
			String command = "setData(" + json + ", " + messages + ")";
			try {
				browser.evaluate(command);
			} catch (Exception e) {
				log.error("failed to set report data to browser", e);
			}
		}

		@Override
		protected void createFormContent(IManagedForm managedForm) {
			ScrolledForm form = managedForm.getForm();
			Composite composite = form.getBody();
			composite.setLayout(new FillLayout());
			browser = UI.createBrowser(composite, this);
		}
	}
}
