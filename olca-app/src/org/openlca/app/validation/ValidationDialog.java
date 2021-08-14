package org.openlca.app.validation;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.forms.FormDialog;
import org.eclipse.ui.forms.IManagedForm;
import org.openlca.app.M;
import org.openlca.app.db.Database;
import org.openlca.app.util.Controls;
import org.openlca.app.util.MsgBox;
import org.openlca.app.util.UI;
import org.openlca.core.database.IDatabase;
import org.openlca.validation.Validation;

public class ValidationDialog extends FormDialog {

	private final IDatabase db;
	private int maxItems = 1000;
	private boolean skipWarnings = false;

	public static void show() {
		var db = Database.get();
		if (db == null) {
			MsgBox.error(M.NoDatabaseOpened);
			return;
		}
		var dialog = new ValidationDialog(db);
		dialog.open();
	}

	private ValidationDialog(IDatabase db) {
		super(UI.shell());
		this.db = db;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Validate database " + db.getName());
	}

	@Override
	protected Point getInitialSize() {
		return UI.initialSizeOf(this, 450, 250);
	}

	@Override
	protected void createFormContent(IManagedForm mform) {
		var tk = mform.getToolkit();
		var body = UI.formBody(mform.getForm(), tk);
		UI.gridLayout(body, 2);

		// max. items
		UI.formLabel(body, tk, "Maximum number of reported issues");
		var spinner = new Spinner(body, SWT.BORDER);
		tk.adapt(spinner);
		spinner.setValues(maxItems, 0, Integer.MAX_VALUE, 0, 100, 1000);
		Controls.onSelect(spinner, e -> {
			maxItems = spinner.getSelection();
			System.out.println(maxItems);
		});

		// skip warnings
		var check = UI.formCheckBox(body, tk, "Skip warnings");
		Controls.onSelect(check, e -> {
			skipWarnings = check.getSelection();
			System.out.println(skipWarnings);
		});
	}

	@Override
	protected void okPressed() {
		var validation = Validation.on(db)
			.maxItems(maxItems)
			.skipWarnings(skipWarnings);
		// TODO: currently blocking
		validation.run();
		ValidationResultView.open(validation.getItems());
		super.okPressed();
	}
}
