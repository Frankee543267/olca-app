package org.openlca.app.editors.lcia.geo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openlca.core.database.IDatabase;
import org.openlca.geo.geojson.FeatureCollection;
import org.openlca.jsonld.Json;

class Setup {

	/**
	 * The parameters / attributes of the geographic features.
	 */
	final List<GeoParam> params = new ArrayList<>();

	/**
	 * The elementary flows that are bound to parameters / attributes of the
	 * geographic features file via formulas. These formulas are then used to
	 * calculate the regionalized characterization factors.
	 */
	final List<GeoFlowBinding> bindings = new ArrayList<>();

	final FeatureCollection features;

	private Setup(FeatureCollection features) {
		this.features = features;
	}

	/**
	 * Try to read or generate a setup for calculating regionalized
	 * characterization factors from the given file. The file may contain a
	 * serialized setup or valid GeoJSON. In the former case, a new setup is
	 * generated.
	 */
	static Setup read(File file, IDatabase db) {
		if (file == null)
			return null;
		var json = Json.readObject(file).orElse(null);
		if (json == null)
			return null;
		if(json.has("setup") && json.has("features"))
			return fromSerialized(json, db);

		var features = FeatureCollection.fromJson(json);
		var setup = new Setup(features);
		setup.params.addAll(GeoParam.collectFrom(features));
		return setup;
	}

	private static Setup fromSerialized(JsonObject json, IDatabase db) {
		var featureObj = Json.getObject(json, "features");
		var features = featureObj == null
			? new FeatureCollection()
			: FeatureCollection.fromJson(featureObj);
		var setup = new Setup(features);
		var setupObj = Json.getObject(json, "setup");
		if (setupObj == null)
			return setup;

		var properties = Json.getArray(setupObj, "properties");
		if (properties != null) {
			Json.stream(properties)
				.filter(JsonElement::isJsonObject)
				.map(JsonElement::getAsJsonObject)
				.map(GeoParam::fromJson)
				.filter(Objects::nonNull)
				.forEach(setup.params::add);
		}

		var bindings = Json.getArray(setupObj, "bindings");
		if (bindings != null) {
			Json.stream(bindings)
				.filter(JsonElement::isJsonObject)
				.map(JsonElement::getAsJsonObject)
				.map(obj -> GeoFlowBinding.fromJson(obj, db))
				.filter(Objects::nonNull)
				.forEach(setup.bindings::add);
		}

		return  setup;
	}
}
