package io.github.fabiantorrestech.touchwallpaper.storage;

import android.content.Context;
import android.graphics.Rect;
import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.model.ZonePreset;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads the measured zones and the ROM's quote list from assets. Each is read once, on first use. */
public final class ZoneCatalog {
    private static List<ZoneDefinition> zones;
    private static List<String> quotes;

    private ZoneCatalog() {
    }

    public static synchronized List<ZoneDefinition> loadZones(Context context) throws IOException, JSONException {
        if (zones == null) {
            JSONArray items = new JSONObject(readAsset(context, "zones.json")).getJSONArray("zones");
            List<ZoneDefinition> parsed = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                parsed.add(parseZone(items.getJSONObject(i)));
            }
            zones = Collections.unmodifiableList(parsed);
        }
        return zones;
    }

    /** Only the preview's text needs these, so they aren't read until then. */
    public static synchronized List<String> loadQuotes(Context context) throws IOException, JSONException {
        if (quotes == null) {
            JSONObject items = new JSONObject(readAsset(context, "mottos.json"));
            List<String> parsed = new ArrayList<>();
            Iterator<String> names = items.keys();
            while (names.hasNext()) {
                parsed.add(items.getString(names.next()));
            }
            quotes = Collections.unmodifiableList(parsed);
        }
        return quotes;
    }

    private static ZoneDefinition parseZone(JSONObject item) throws JSONException {
        List<ZonePreset> presets = new ArrayList<>();
        JSONArray presetItems = item.optJSONArray("presets");
        if (presetItems != null) {
            for (int i = 0; i < presetItems.length(); i++) {
                JSONObject preset = presetItems.getJSONObject(i);
                presets.add(new ZonePreset(
                        preset.getString("id"),
                        preset.getString("label"),
                        preset.getString("note"),
                        parseRect(preset.getJSONArray("rect"))));
            }
        }
        return new ZoneDefinition(
                item.getString("id"),
                item.getString("label"),
                Band.valueOf(item.getString("band").toUpperCase(Locale.ROOT)),
                parseRect(item.getJSONArray("rect")),
                item.getString("evidence"),
                item.getBoolean("textColor"),
                item.getBoolean("readable"),
                presets);
    }

    private static Rect parseRect(JSONArray rect) throws JSONException {
        return new Rect(rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3));
    }

    private static String readAsset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
