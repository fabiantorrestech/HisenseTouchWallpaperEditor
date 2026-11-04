package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Context;
import androidx.annotation.StringRes;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;
import io.github.fabiantorrestech.touchwallpaper.model.TextColorChoice;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneStyle;
import io.github.fabiantorrestech.touchwallpaper.render.RenderResult;
import io.github.fabiantorrestech.touchwallpaper.settings.AppSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns a render's predicted text colors into plain messages that say what happens and what to do. */
final class ReadabilityAdvisor {
    private static final int MID_GRAY = 128;
    private static final int MIN_OPACITY_TO_CHECK = 50;

    private final Context context;
    private final AppSettings settings;
    private final Map<Band, TextColor> previousColors = new EnumMap<>(Band.class);
    private final List<String> flipMessages = new ArrayList<>();

    ReadabilityAdvisor(Context context, AppSettings settings) {
        this.context = context;
        this.settings = settings;
    }

    /** Call once per render, so a change in text color is only announced once. */
    void noteRender(RenderResult result) {
        flipMessages.clear();
        for (Band band : Band.values()) {
            TextColor color = result.predictedTextColor(band);
            TextColor previous = previousColors.put(band, color);
            if (previous != null && previous != color) {
                flipMessages.add(context.getString(R.string.advice_flipped, itemsIn(band), previous.label, color.label));
            }
        }
    }

    /** Everything, for the preview screen. */
    String adviceForPreview(RenderResult result, Project project, List<ZoneDefinition> zones) {
        List<String> lines = new ArrayList<>();
        for (Band band : Band.values()) {
            lines.add(context.getString(R.string.advice_band, itemsIn(band), result.predictedTextColor(band).label));
        }
        lines.addAll(flipMessages);
        for (ZoneDefinition zone : zones) {
            addContrastLine(lines, zone, project, result);
        }
        for (Band band : Band.values()) {
            addNoPanelLine(lines, band, project, zones, result);
        }
        if (settings.technicalDetails()) {
            lines.add(context.getString(R.string.advice_votes, result.voteFor(Band.TOP), result.voteFor(Band.BOTTOM)));
        }
        return String.join("\n", lines);
    }

    /** Only what concerns this zone, for the edit screen. Problems come first, since space is short. */
    String adviceForZone(RenderResult result, Project project, ZoneDefinition zone, List<ZoneDefinition> zones) {
        List<String> lines = new ArrayList<>();
        addContrastLine(lines, zone, project, result);
        if (zone.hasTextColor) {
            addNoPanelLine(lines, zone.band, project, zones, result);
            if (lines.isEmpty()) {
                lines.add(context.getString(R.string.advice_band, itemsIn(zone.band), result.predictedTextColor(zone.band).label));
            }
        }
        return String.join("\n", lines);
    }

    private void addContrastLine(List<String> lines, ZoneDefinition zone, Project project, RenderResult result) {
        ZoneStyle style = project.zoneStyles.get(zone.id);
        if (style == null || !style.enabled || !zone.hasTextColor
                || !style.mode.drawsFill || style.fillOpacityPercent < MIN_OPACITY_TO_CHECK) {
            return;
        }
        boolean fillIsDark = style.fillGray < MID_GRAY;
        boolean textIsBlack = result.predictedTextColor(zone.band) == TextColor.BLACK;
        if (fillIsDark == textIsBlack) {
            @StringRes int message = textIsBlack ? R.string.advice_contrast_light : R.string.advice_contrast_dark;
            lines.add(context.getString(message, zone.label));
        }
    }

    private void addNoPanelLine(List<String> lines, Band band, Project project, List<ZoneDefinition> zones, RenderResult result) {
        if (project.textColorChoices.get(band) == TextColorChoice.AUTO || bandHasPanel(band, project, zones)) {
            return;
        }
        lines.add(context.getString(R.string.advice_no_panel, itemsIn(band), result.predictedTextColor(band).label));
    }

    private static boolean bandHasPanel(Band band, Project project, List<ZoneDefinition> zones) {
        for (ZoneDefinition zone : zones) {
            ZoneStyle style = project.zoneStyles.get(zone.id);
            if (zone.band == band && zone.hasTextColor && style != null && style.enabled && style.mode.drawsFill) {
                return true;
            }
        }
        return false;
    }

    private String itemsIn(Band band) {
        return context.getString(band == Band.TOP ? R.string.band_top_items : R.string.band_bottom_items);
    }
}
