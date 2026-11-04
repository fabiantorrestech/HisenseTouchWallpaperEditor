package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.eink.EinkRefresher;
import io.github.fabiantorrestech.touchwallpaper.settings.AppSettings;

/** Applies the e-ink settings and provides the top bar that every screen shares. */
abstract class BaseActivity extends AppCompatActivity {
    protected AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new AppSettings(this);
        setTheme(settings.einkLook() ? R.style.Theme_TouchWallpaper : R.style.Theme_TouchWallpaper_Standard);
        super.onCreate(savedInstanceState);
    }

    /** Call after setContentView. Screens that include the top bar get Home and Refresh. */
    protected void setUpTopBar(String title) {
        ((TextView) findViewById(R.id.bar_title)).setText(title);
        findViewById(R.id.bar_home).setOnClickListener(view -> goHome());
        findViewById(R.id.bar_refresh).setOnClickListener(view -> EinkRefresher.refresh(this));
    }

    protected void goHome() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        super.startActivity(intent, options);
        skipTransitionIfWanted();
    }

    @Override
    public void finish() {
        super.finish();
        skipTransitionIfWanted();
    }

    private void skipTransitionIfWanted() {
        if (settings != null && settings.noAnimations()) {
            overridePendingTransition(0, 0);
        }
    }

    void showError(Exception error) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(error.getMessage())
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    void showErrorAndClose(Exception error) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(error.getMessage())
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                .show();
    }
}
