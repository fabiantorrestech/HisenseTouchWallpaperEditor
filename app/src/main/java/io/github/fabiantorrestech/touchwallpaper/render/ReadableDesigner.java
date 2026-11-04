package io.github.fabiantorrestech.touchwallpaper.render;

import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;
import io.github.fabiantorrestech.touchwallpaper.model.TextColorChoice;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneMode;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Make readable": adds a panel behind each main lock screen item, matched to the text color the
 * image already produces. Dark areas get white text on black panels; bright areas get black text on
 * white panels. Panels reinforce the existing vote, so the text normally won't flip. The result is
 * checked afterward, and a band close enough to a tie to flip gets its color forced.
 */
public final class ReadableDesigner {
    private static final int BLACK_GRAY = 0;
    private static final int WHITE_GRAY = 255;
    private static final int FULL_OPACITY = 100;

    private ReadableDesigner() {
    }

    public static void apply(Project project, SourceImage source, List<ZoneDefinition> zones, WallpaperRenderer renderer) {
        RenderResult cropOnly = renderer.renderCropOnly(source, project);
        Map<Band, TextColor> colors = new EnumMap<>(Band.class);
        for (Band band : Band.values()) {
            colors.put(band, cropOnly.predictedTextColor(band));
            project.textColorChoices.put(band, TextColorChoice.AUTO);
        }
        for (ZoneDefinition zone : zones) {
            if (zone.includedInMakeReadable) {
                addPanel(project.styleFor(zone.id), colors.get(zone.band));
            }
        }
        RenderResult designed = renderer.render(source, project, zones);
        for (Band band : Band.values()) {
            if (designed.predictedTextColor(band) != colors.get(band)) {
                project.textColorChoices.put(band,
                        colors.get(band) == TextColor.BLACK ? TextColorChoice.FORCE_BLACK : TextColorChoice.FORCE_WHITE);
            }
        }
    }

    private static void addPanel(ZoneStyle style, TextColor textColor) {
        style.enabled = true;
        style.mode = ZoneMode.FILL;
        style.fillGray = textColor == TextColor.WHITE ? BLACK_GRAY : WHITE_GRAY;
        style.fillOpacityPercent = FULL_OPACITY;
        style.softEdge = 0;
    }
}
