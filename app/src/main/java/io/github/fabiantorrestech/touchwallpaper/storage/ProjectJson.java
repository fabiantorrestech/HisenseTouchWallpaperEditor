package io.github.fabiantorrestech.touchwallpaper.storage;

import android.graphics.Rect;
import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.CropState;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.TextColorChoice;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneMode;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneStyle;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Converts a project to and from project.json. Bump SCHEMA_VERSION and migrate on format changes (ADR 3).
 *
 * Schema 2 changes from 1:
 * - Zones gained "enabled"; the old OFF mode became enabled=false.
 * - The three quote zones became one "quote" zone with a preset.
 * - Crop scale is per original image pixel instead of per loaded pixel.
 */
public final class ProjectJson {
    private static final int SCHEMA_VERSION = 2;
    private static final String LEGACY_MODE_OFF = "OFF";
    private static final String QUOTE_ZONE_ID = "quote";
    /** Largest area first, because when several were on, the largest one wins. */
    private static final String[] LEGACY_QUOTE_ZONES = {"quote_full", "quote_chinese_most", "quote_chinese_last"};

    /** Provides what migrating an old project needs from outside the JSON. */
    public interface LegacyInfo {
        /** How much schema 1 shrank this project's image when loading it. */
        int legacySampleSize();
    }

    private ProjectJson() {
    }

    public static String toJson(Project project) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("id", project.id);
        root.put("name", project.name);
        root.put("updatedAtMillis", project.updatedAtMillis);
        root.put("sourceFileName", project.sourceFileName);
        root.put("crop", cropToJson(project.crop));
        root.put("lastSavedUri", project.lastSavedUri);
        root.put("lastSavedFolderUri", project.lastSavedFolderUri);
        root.put("lastSavedPath", project.lastSavedPath);

        JSONObject zones = new JSONObject();
        for (Map.Entry<String, ZoneStyle> entry : project.zoneStyles.entrySet()) {
            if (!entry.getValue().isDefault()) {
                zones.put(entry.getKey(), styleToJson(entry.getValue()));
            }
        }
        root.put("zones", zones);

        JSONObject textColors = new JSONObject();
        for (Band band : Band.values()) {
            textColors.put(band.name(), project.textColorChoices.get(band).name());
        }
        root.put("textColors", textColors);
        return root.toString(2);
    }

    /** Reads a project in the current format. An undo snapshot or render copy never needs migrating. */
    public static Project fromCurrentJson(String json) throws JSONException {
        return fromJson(json, () -> 1);
    }

    static Project fromJson(String json, LegacyInfo legacyInfo) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.getInt("schemaVersion");
        if (version > SCHEMA_VERSION) {
            throw new JSONException("This project was saved by a newer version of the app.");
        }
        Project project = new Project();
        project.id = root.getString("id");
        project.name = root.getString("name");
        project.updatedAtMillis = root.getLong("updatedAtMillis");
        project.sourceFileName = root.getString("sourceFileName");
        project.crop = cropFromJson(root.getJSONObject("crop"));
        project.lastSavedUri = optStringOrNull(root, "lastSavedUri");
        project.lastSavedFolderUri = optStringOrNull(root, "lastSavedFolderUri");
        project.lastSavedPath = optStringOrNull(root, "lastSavedPath");

        JSONObject zones = root.getJSONObject("zones");
        Iterator<String> ids = zones.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            project.zoneStyles.put(id, styleFromJson(zones.getJSONObject(id)));
        }

        JSONObject textColors = root.getJSONObject("textColors");
        for (Band band : Band.values()) {
            project.textColorChoices.put(band, TextColorChoice.valueOf(textColors.getString(band.name())));
        }
        if (version < 2) {
            migrateQuoteZones(project);
            project.crop.scale /= legacyInfo.legacySampleSize();
        }
        return project;
    }

    /** Keeps the largest quote zone that was on, or the largest that was set up if none were. */
    private static void migrateQuoteZones(Project project) {
        ZoneStyle chosen = null;
        String chosenPreset = null;
        for (String legacyId : LEGACY_QUOTE_ZONES) {
            ZoneStyle style = project.zoneStyles.remove(legacyId);
            if (style == null) {
                continue;
            }
            boolean betterThanChosen = chosen == null || (style.enabled && !chosen.enabled);
            if (betterThanChosen) {
                chosen = style;
                chosenPreset = legacyId;
            }
        }
        if (chosen != null) {
            chosen.presetId = chosenPreset;
            project.zoneStyles.put(QUOTE_ZONE_ID, chosen);
        }
    }

    private static String optStringOrNull(JSONObject json, String key) {
        return json.isNull(key) ? null : json.optString(key, null);
    }

    private static JSONObject cropToJson(CropState crop) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("scale", (double) crop.scale);
        json.put("centerX", (double) crop.centerX);
        json.put("centerY", (double) crop.centerY);
        json.put("quarterTurns", crop.quarterTurns);
        json.put("backgroundGray", crop.backgroundGray);
        return json;
    }

    private static CropState cropFromJson(JSONObject json) throws JSONException {
        CropState crop = new CropState();
        crop.scale = (float) json.getDouble("scale");
        crop.centerX = (float) json.getDouble("centerX");
        crop.centerY = (float) json.getDouble("centerY");
        crop.quarterTurns = json.getInt("quarterTurns");
        crop.backgroundGray = json.getInt("backgroundGray");
        return crop;
    }

    private static JSONObject styleToJson(ZoneStyle style) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("enabled", style.enabled);
        json.put("mode", style.mode.name());
        json.put("fillGray", style.fillGray);
        json.put("fillOpacityPercent", style.fillOpacityPercent);
        json.put("outlineGray", style.outlineGray);
        json.put("outlineWidth", style.outlineWidth);
        json.put("cornerRadius", style.cornerRadius);
        json.put("padding", style.padding);
        json.put("softEdge", style.softEdge);
        json.put("presetId", style.presetId);
        if (style.customRect != null) {
            Rect rect = style.customRect;
            json.put("customRect", new JSONArray().put(rect.left).put(rect.top).put(rect.right).put(rect.bottom));
        }
        return json;
    }

    private static ZoneStyle styleFromJson(JSONObject json) throws JSONException {
        ZoneStyle style = new ZoneStyle();
        String mode = json.getString("mode");
        if (LEGACY_MODE_OFF.equals(mode)) {
            style.enabled = false;
            style.mode = ZoneMode.FILL;
        } else {
            style.enabled = json.optBoolean("enabled", true);
            style.mode = ZoneMode.valueOf(mode);
        }
        style.fillGray = json.getInt("fillGray");
        style.fillOpacityPercent = json.getInt("fillOpacityPercent");
        style.outlineGray = json.getInt("outlineGray");
        style.outlineWidth = json.getInt("outlineWidth");
        style.cornerRadius = json.getInt("cornerRadius");
        style.padding = json.getInt("padding");
        style.softEdge = json.getInt("softEdge");
        style.presetId = optStringOrNull(json, "presetId");
        JSONArray rect = json.optJSONArray("customRect");
        if (rect != null) {
            style.customRect = new Rect(rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3));
        }
        return style;
    }
}
