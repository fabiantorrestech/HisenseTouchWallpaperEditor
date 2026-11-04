package io.github.fabiantorrestech.touchwallpaper.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import java.util.List;

/** The Zones list: switch each zone or all of them on or off, or open one to edit. Toggling keeps a zone's style. */
final class ZonesListPanel {
    interface Host {
        io.github.fabiantorrestech.touchwallpaper.model.Project project();

        List<ZoneDefinition> zones();

        void onSettingsChanged();

        void onEditZone(ZoneDefinition zone);
    }

    private static final int ROWS_PER_PAGE = 4;
    private static final int EDIT_BUTTON_WIDTH_DP = 76;
    private static final int ROW_HEIGHT_DP = 48;

    private final Activity activity;
    private final Host host;
    private final LinearLayout rows;
    private final TextView pageLabel;
    private final View previous;
    private final View next;
    private int page;

    ZonesListPanel(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        rows = activity.findViewById(R.id.zone_rows);
        pageLabel = activity.findViewById(R.id.zones_page);
        previous = activity.findViewById(R.id.zones_previous);
        next = activity.findViewById(R.id.zones_next);
        previous.setOnClickListener(view -> showPage(page - 1));
        next.setOnClickListener(view -> showPage(page + 1));
        activity.findViewById(R.id.all_on).setOnClickListener(view -> setAll(true));
        activity.findViewById(R.id.all_off).setOnClickListener(view -> setAll(false));
    }

    void show() {
        showPage(page);
    }

    private void setAll(boolean enabled) {
        for (ZoneDefinition zone : host.zones()) {
            host.project().styleFor(zone.id).enabled = enabled;
        }
        host.onSettingsChanged();
        showPage(page);
    }

    private int pageCount() {
        return (host.zones().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
    }

    private void showPage(int requested) {
        page = Math.max(0, Math.min(pageCount() - 1, requested));
        rows.removeAllViews();
        List<ZoneDefinition> zones = host.zones();
        int end = Math.min(zones.size(), (page + 1) * ROWS_PER_PAGE);
        for (int i = page * ROWS_PER_PAGE; i < end; i++) {
            rows.addView(createRow(zones.get(i)));
        }
        pageLabel.setText(activity.getString(R.string.page_of, page + 1, pageCount()));
        previous.setEnabled(page > 0);
        next.setEnabled(page < pageCount() - 1);
    }

    private View createRow(ZoneDefinition zone) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);

        CheckBox toggle = new CheckBox(activity);
        toggle.setText(zone.label);
        toggle.setChecked(host.project().styleFor(zone.id).enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            host.project().styleFor(zone.id).enabled = checked;
            host.onSettingsChanged();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton edit = new MaterialButton(activity);
        edit.setText(R.string.edit);
        edit.setOnClickListener(view -> host.onEditZone(zone));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                Math.round(EDIT_BUTTON_WIDTH_DP * density), Math.round(ROW_HEIGHT_DP * density));
        int gap = activity.getResources().getDimensionPixelSize(R.dimen.gap_button);
        editParams.setMargins(gap, gap, gap, gap);
        row.addView(edit, editParams);
        return row;
    }
}
