package io.github.fabiantorrestech.touchwallpaper.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import io.github.fabiantorrestech.touchwallpaper.R;

/** Three short pages explaining the app. Shown once on first launch, and any time from Help. */
public final class WelcomeActivity extends BaseActivity {
    private static final int[][] PAGES = {
            {R.string.welcome_1_title, R.string.welcome_1_body},
            {R.string.welcome_2_title, R.string.welcome_2_body},
            {R.string.welcome_3_title, R.string.welcome_3_body},
    };

    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        findViewById(R.id.welcome_back).setOnClickListener(view -> showPage(page - 1));
        findViewById(R.id.welcome_next).setOnClickListener(view -> {
            if (page == PAGES.length - 1) {
                finish();
            } else {
                showPage(page + 1);
            }
        });
        showPage(0);
    }

    private void showPage(int index) {
        page = index;
        ((TextView) findViewById(R.id.welcome_title)).setText(PAGES[page][0]);
        ((TextView) findViewById(R.id.welcome_body)).setText(PAGES[page][1]);
        findViewById(R.id.welcome_back).setVisibility(page == 0 ? View.INVISIBLE : View.VISIBLE);
        ((MaterialButton) findViewById(R.id.welcome_next))
                .setText(page == PAGES.length - 1 ? R.string.get_started : R.string.next_page);
    }
}
