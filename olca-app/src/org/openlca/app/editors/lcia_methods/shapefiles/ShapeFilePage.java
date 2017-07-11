package org.openlca.app.editors.lcia_methods.shapefiles;

import java.awt.Desktop;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.M;
import org.openlca.app.components.FileChooser;
import org.openlca.app.editors.lcia_methods.ImpactMethodEditor;
import org.openlca.app.editors.parameters.ModelParameterPage;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.util.Actions;
import org.openlca.app.util.Colors;
import org.openlca.app.util.Controls;
import org.openlca.app.util.Error;
import org.openlca.app.util.Info;
import org.openlca.app.util.Question;
import org.openlca.app.util.UI;
import org.openlca.app.util.tables.Tables;
import org.openlca.app.util.viewers.Viewers;
import org.openlca.core.model.ImpactMethod;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Parameter;
import org.openlca.core.model.ParameterScope;
import org.openlca.core.model.Uncertainty;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows imported shape-files and parameters from these shape-files that can be
 * used in a localized LCIA method.
 */
public class ShapeFilePage extends FormPage {

	private Logger log = LoggerFactory.getLogger(getClass());
	private FormToolkit tk;
	private Composite body;
	private ShapeFileSection[] sections;
	private ScrolledForm form;
	private ImpactMethodEditor editor;

	public ShapeFilePage(ImpactMethodEditor editor) {
		super(editor, "ShapeFilePage", "Shape files (beta)");
		this.editor = editor;
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		form = UI.formHeader(managedForm, "Shape file parameters");
		tk = managedForm.getToolkit();
		body = UI.formBody(form, tk);
		createFileSection();
		List<String> shapeFiles = ShapeFileUtils.getShapeFiles(method());
		sections = new ShapeFileSection[shapeFiles.size()];
		for (int i = 0; i < shapeFiles.size(); i++)
			sections[i] = new ShapeFileSection(i, shapeFiles.get(i));
		form.reflow(true);
	}

	private void createFileSection() {
		Composite comp = UI.formSection(body, tk, "Files");
		createFolderLink(comp);
		UI.filler(comp, tk);
		Button importButton = tk.createButton(comp, M.Import, SWT.NONE);
		importButton.setImage(Icon.IMPORT.get());
		Controls.onSelect(importButton, (e) -> {
			File file = FileChooser.forImport("*.shp");
			if (file != null)
				checkRunImport(file);
		});
		UI.filler(comp, tk);
		Button evaluateButton = tk.createButton(comp,
				M.EvaluateLocations, SWT.NONE);
		evaluateButton.setImage(Icon.EXPRESSION.get());
		Controls.onSelect(evaluateButton, (e) -> {
			try {
				new ProgressMonitorDialog(UI.shell()).run(true, true,
						new EvaluateLocationsJob(method()));
			} catch (Exception ex) {
				log.error("Failed to evaluate locations", ex);
			}
		});
	}

	private void createFolderLink(Composite composite) {
		UI.formLabel(composite, tk, "Location");
		ImageHyperlink link = tk.createImageHyperlink(composite, SWT.TOP);
		File folder = ShapeFileUtils.getFolder(method());
		link.setText(Strings.cut(folder.getAbsolutePath(), 75));
		link.setImage(Icon.FOLDER.get());
		link.setForeground(Colors.linkBlue());
		link.setToolTipText(folder.getAbsolutePath());
		Controls.onClick(link, e -> {
			try {
				if (folder.exists() && folder.isDirectory())
					Desktop.getDesktop().open(folder);
			} catch (Exception ex) {
				log.error("failed to open shape-file folder", ex);
			}
		});
	}

	private List<ShapeFileParameter> checkRunImport(File file) {
		if (!ShapeFileUtils.isValid(file)) {
			org.openlca.app.util.Error.showBox("Invalid file", "The file "
					+ file.getName() + " is not a valid shape file.");
			return Collections.emptyList();
		}
		if (ShapeFileUtils.alreadyExists(method(), file)) {
			org.openlca.app.util.Error
					.showBox("File already exists", "A shape file with the given "
							+ "name already exists for this method.");
			return Collections.emptyList();
		}
		try {
			return runImport(file);
		} catch (Exception e) {
			log.error("Failed to import shape file " + file, e);
			return Collections.emptyList();
		}
	}

	private List<ShapeFileParameter> runImport(File file) throws Exception {
		String shapeFile = ShapeFileUtils.importFile(method(), file);
		List<ShapeFileParameter> params = ShapeFileUtils.getParameters(method(),
				shapeFile);
		for (ShapeFileParameter parameter : params) {
			if (!Parameter.isValidName(parameter.name)) {
				org.openlca.app.util.Error.showBox("Invalid parameter",
						"The parameter name '" + parameter.name
								+ "' is not supported");
				ShapeFileUtils.deleteFile(method(), shapeFile);
				return Collections.emptyList();
			}
		}
		ShapeFileSection section = new ShapeFileSection(sections.length,
				shapeFile);
		ShapeFileSection[] newSections = new ShapeFileSection[sections.length
				+ 1];
		System.arraycopy(sections, 0, newSections, 0, sections.length);
		newSections[sections.length] = section;
		this.sections = newSections;
		section.parameterTable.viewer.setInput(params);
		form.reflow(true);
		editor.getParameterSupport().evaluate();
		return params;
	}

	private ImpactMethod method() {
		return editor.getModel();
	}

	private class ShapeFileSection {

		private int index;
		private String shapeFile;
		private Section section;
		private ShapeFileParameterTable parameterTable;

		ShapeFileSection(int index, String shapeFile) {
			this.index = index;
			this.shapeFile = shapeFile;
			render();
		}

		private void render() {
			section = UI.section(body, tk, M.Parameters + " - " + shapeFile);
			Composite composite = UI.sectionClient(section, tk);
			parameterTable = new ShapeFileParameterTable(shapeFile, composite);
			Action delete = Actions.onRemove(() -> {
				if (delete()) {
					removeExternalSourceReferences();
					save();
				}
			});
			Action update = new Action(M.Update) {

				@Override
				public ImageDescriptor getImageDescriptor() {
					return Icon.REFRESH.descriptor();

				}

				@Override
				public void run() {
					File file = FileChooser.forImport("*.shp");
					if (file == null)
						return;
					Set<String> previouslyLinked = getReferencedParameters();
					delete(true);
					List<ShapeFileParameter> parameters = checkRunImport(file);
					Set<String> stillLinked = getReferencedParameters();
					Map<String, ShapeFileParameter> nameToParam = new HashMap<>();
					for (ShapeFileParameter parameter : parameters)
						if (previouslyLinked.contains(parameter.name)) {
							stillLinked.add(parameter.name);
							nameToParam.put(parameter.name, parameter);
						}
					updateExternalSourceReferences(stillLinked, nameToParam);
					save();
				}
			};
			ShowMapAction showAction = new ShowMapAction(this);
			AddParamAction addAction = new AddParamAction(this);
			Actions.bind(section, showAction, delete, update);
			Actions.bind(parameterTable.viewer, showAction, addAction);
		}

		private void save() {
			editor.doSave(null);
		}

		private boolean delete() {
			return delete(false);
		}

		private boolean delete(boolean force) {
			boolean del = force
					|| Question.ask(M.DeleteShapeFile, M.ReallyDeleteShapeFile);
			if (!del)
				return false;
			ShapeFileUtils.deleteFile(method(), shapeFile);
			section.dispose();
			ShapeFileSection[] newSections = new ShapeFileSection[sections.length
					- 1];
			System.arraycopy(sections, 0, newSections, 0, index);
			if ((index + 1) < sections.length) {
				System.arraycopy(sections, index + 1, newSections, index,
						newSections.length - index);
			}
			sections = newSections;
			form.reflow(true);
			return true;
		}

		private void removeExternalSourceReferences() {
			for (Parameter parameter : method().parameters) {
				if (!parameter.isInputParameter())
					continue;
				if (shapeFile.equals(parameter.getExternalSource())) {
					parameter.setExternalSource(null);
				}
			}
			editor.getParameterSupport().evaluate();
			editor.setDirty(true);
		}

		/*
		 * stillLinked: names of parameters that were linked to the shape file
		 * before updating and afterwards (only set those)
		 */
		private void updateExternalSourceReferences(Set<String> stillLinked,
				Map<String, ShapeFileParameter> nameToParam) {
			for (Parameter parameter : method().parameters) {
				if (!parameter.isInputParameter())
					continue;
				if (!shapeFile.equals(parameter.getExternalSource()))
					continue;
				if (!stillLinked.contains(parameter.getName())) {
					parameter.setExternalSource(null);
				} else {
					ShapeFileParameter param = nameToParam.get(parameter.getName());
					if (param == null)
						continue;
					parameter.setValue((param.min + param.max) / 2);
					parameter.setUncertainty(Uncertainty.uniform(
							param.min, param.max));
				}
			}
			editor.getParameterSupport().evaluate();
		}

		private Set<String> getReferencedParameters() {
			Set<String> names = new HashSet<>();
			for (Parameter parameter : method().parameters)
				if (parameter.isInputParameter())
					if (shapeFile.equals(parameter.getExternalSource()))
						names.add(parameter.getName());
			return names;
		}
	}

	private class ShapeFileParameterTable {

		private String[] columns = { M.Name, M.Minimum,
				M.Maximum };
		private TableViewer viewer;
		private List<ShapeFileParameter> params;

		ShapeFileParameterTable(String shapeFile, Composite parent) {
			viewer = Tables.createViewer(parent, columns);
			viewer.setLabelProvider(new ShapeFileParameterLabel());
			Tables.bindColumnWidths(viewer, 0.4, 0.3, 0.3);
			try {
				params = ShapeFileUtils.getParameters(method(), shapeFile);
				viewer.setInput(params);
			} catch (Exception e) {
				log.error("failed to read parameteres for shape file "
						+ shapeFile, e);
			}
		}

		private class ShapeFileParameterLabel extends LabelProvider implements
				ITableLabelProvider {

			@Override
			public Image getColumnImage(Object o, int i) {
				if (i == 0)
					return Images.get(ModelType.PARAMETER);
				else
					return null;
			}

			@Override
			public String getColumnText(Object o, int i) {
				if (!(o instanceof ShapeFileParameter))
					return null;
				ShapeFileParameter p = (ShapeFileParameter) o;
				switch (i) {
				case 0:
					return p.name;
				case 1:
					return Double.toString(p.min);
				case 2:
					return Double.toString(p.max);
				default:
					return null;
				}
			}
		}
	}

	private class ShowMapAction extends Action {

		private ShapeFileSection section;

		public ShowMapAction(ShapeFileSection section) {
			this.section = section;
			setToolTipText(M.ShowInMap);
			setText(M.ShowInMap);
			setImageDescriptor(Icon.MAP.descriptor());
		}

		@Override
		public void run() {
			if (section == null || section.parameterTable == null)
				return;
			ShapeFileParameter param = Viewers
					.getFirstSelected(section.parameterTable.viewer);
			if (param == null)
				ShapeFileUtils.openFileInMap(method(), section.shapeFile);
			else
				ShapeFileUtils.openFileInMap(method(), section.shapeFile, param);
		}
	}

	private class AddParamAction extends Action {

		private ShapeFileSection section;

		public AddParamAction(ShapeFileSection section) {
			this.section = section;
			setToolTipText(M.AddToMethodParameters);
			setText(M.AddToMethodParameters);
			setImageDescriptor(Icon.ADD.descriptor());
		}

		@Override
		public void run() {
			ShapeFileParameter param = getSelected();
			if (param == null)
				return;
			if (exists(param)) {
				Info.showBox(M.ParameterAlreadyAdded,
						M.SelectedParameterWasAlreadyAdded);
				return;
			}
			if (otherExists(param)) {
				Error.showBox(M.ParameterWithSameNameExists,
						M.ParameterWithSameNameExistsInMethod);
				return;
			}
			if (!Parameter.isValidName(param.name)) {
				Error.showBox(M.InvalidParameterName, param.name + " "
						+ M.IsNotValidParameterName);
				return;
			}
			addParam(param);
		}

		private ShapeFileParameter getSelected() {
			if (section == null || section.parameterTable == null)
				return null;
			ShapeFileParameter param = Viewers
					.getFirstSelected(section.parameterTable.viewer);
			if (param == null) {
				Error.showBox(M.NoParameterSelected, M.NoShapefileParameterSelected);
				return null;
			}
			return param;
		}

		private boolean exists(ShapeFileParameter param) {
			for (Parameter realParam : method().parameters) {
				if (Strings.nullOrEqual(param.name, realParam.getName())
						&& Strings.nullOrEqual("SHAPE_FILE",
								realParam.getSourceType())
						&& Strings.nullOrEqual(section.shapeFile,
								realParam.getExternalSource()))
					return true;
			}
			return false;
		}

		private boolean otherExists(ShapeFileParameter param) {
			for (Parameter p : method().parameters) {
				if (Strings.nullOrEqual(param.name, p.getName())
						&& !Strings.nullOrEqual(section.shapeFile,
								p.getExternalSource()))
					return true;
			}
			return false;
		}

		private void addParam(ShapeFileParameter shapeParam) {
			Parameter p = new Parameter();
			p.setRefId(UUID.randomUUID().toString());
			p.setExternalSource(section.shapeFile);
			p.setInputParameter(true);
			p.setName(shapeParam.name);
			p.setDescription("from shapefile: " + section.shapeFile);
			p.setValue((shapeParam.min + shapeParam.max) / 2);
			p.setUncertainty(Uncertainty.uniform(shapeParam.min,
					shapeParam.max));
			p.setScope(ParameterScope.IMPACT_METHOD);
			p.setSourceType("SHAPE_FILE");
			method().parameters.add(p);
			editor.setDirty(true);
			editor.setActivePage(ModelParameterPage.ID);
			editor.getParameterSupport().evaluate();
		}
	}

}
