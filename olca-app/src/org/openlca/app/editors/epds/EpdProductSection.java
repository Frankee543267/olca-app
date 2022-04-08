package org.openlca.app.editors.epds;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.M;
import org.openlca.app.components.ModelLink;
import org.openlca.app.util.Controls;
import org.openlca.app.util.UI;
import org.openlca.core.model.EpdProduct;
import org.openlca.core.model.Flow;

public record EpdProductSection(EpdEditor editor) {

	void render(Composite body, FormToolkit tk) {

		var comp = UI.formSection(body, tk, "Declared product");
		var flowLink = ModelLink.of(Flow.class)
			.renderOn(comp, tk, M.Flow)
			.setModel(product().flow);

		// amount
		UI.formLabel(comp, M.Amount);
		var amountComp = tk.createComposite(comp);
		UI.gridLayout(amountComp, 2, 5, 0);
		var amountText = tk.createText(amountComp, "", SWT.BORDER);
		UI.gridData(amountText, false, false).widthHint = 100;
		Controls.set(amountText, product().amount, amount -> {
			product().amount = amount;
			editor.setDirty();
		});

		// unit
		new UnitHandler(this).renderCombo(amountComp);

		// on flow change
		flowLink.onChange(flow -> {
			var product = product();
			product.flow = flow;
			product.property = flow != null
				? flow.referenceFlowProperty
				: null;
			product.unit = flow != null
				? flow.getReferenceUnit()
				: null;
			editor.emitEvent("product.changed");
			editor.setDirty();
		});
	}

	private EpdProduct product() {
		var epd = editor.getModel();
		if (epd.product == null) {
			epd.product = new EpdProduct();
			epd.product.amount = 1;
		}
		return epd.product;
	}

	private record UnitHandler(EpdProductSection section) {

		void renderCombo(Composite comp) {
			var combo = new Combo(comp, SWT.READ_ONLY);
			UI.gridData(combo, false, false);
			var units = UnitCombo.of(combo);
			update(units);

			units.listen(item -> {
				var product = section.product();
				product.property = item.property();
				product.unit = item.unit();
				var editor = section.editor;
				editor.emitEvent("unit.changed");
				editor.setDirty();
			});

			section.editor.onEvent("product.changed", () -> update(units));
		}

		private void update(UnitCombo combo) {
			combo.clear();
			var p = section.product();
			if (p != null && p.flow != null) {
				combo.fill(p.flow);
				combo.select(p.unit, p.property);
			}
			combo.pack();
		}
	}
}
