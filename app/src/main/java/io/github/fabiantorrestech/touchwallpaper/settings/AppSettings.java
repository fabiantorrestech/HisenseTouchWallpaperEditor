package io.github.fabiantorrestech.touchwallpaper.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** The user's app-wide choices. E-ink options default to on, because this app targets e-ink devices. */
public final class AppSettings {
    private static final String FILE = "settings";
    private static final String KEY_EINK_LOOK = "eink_look";
    private static final String KEY_NO_ANIMATIONS = "no_animations";
    private static final String KEY_REFRESH_ON_SWITCH = "refresh_on_switch";
    private static final String KEY_TECHNICAL_DETAILS = "technical_details";
    private static final String KEY_ASK_EACH_TIME = "ask_each_time";
    private static final String KEY_EXPORT_FOLDER = "export_folder";
    private static final String KEY_EXPORT_FOLDER_LABEL = "export_folder_label";
    private static final String KEY_WELCOME_SHOWN = "welcome_shown";

    private final SharedPreferences preferences;

    public AppSettings(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean einkLook() {
        return preferences.getBoolean(KEY_EINK_LOOK, true);
    }

    public void setEinkLook(boolean value) {
        preferences.edit().putBoolean(KEY_EINK_LOOK, value).apply();
    }

    public boolean noAnimations() {
        return preferences.getBoolean(KEY_NO_ANIMATIONS, true);
    }

    public void setNoAnimations(boolean value) {
        preferences.edit().putBoolean(KEY_NO_ANIMATIONS, value).apply();
    }

    public boolean refreshOnSwitch() {
        return preferences.getBoolean(KEY_REFRESH_ON_SWITCH, true);
    }

    public void setRefreshOnSwitch(boolean value) {
        preferences.edit().putBoolean(KEY_REFRESH_ON_SWITCH, value).apply();
    }

    public boolean technicalDetails() {
        return preferences.getBoolean(KEY_TECHNICAL_DETAILS, false);
    }

    public void setTechnicalDetails(boolean value) {
        preferences.edit().putBoolean(KEY_TECHNICAL_DETAILS, value).apply();
    }

    public boolean askEachTime() {
        return preferences.getBoolean(KEY_ASK_EACH_TIME, false);
    }

    public void setAskEachTime(boolean value) {
        preferences.edit().putBoolean(KEY_ASK_EACH_TIME, value).apply();
    }

    /** The chosen default folder's tree address, or null for Pictures/TouchWallpaper. */
    public String exportFolderUri() {
        return preferences.getString(KEY_EXPORT_FOLDER, null);
    }

    public String exportFolderLabel() {
        return preferences.getString(KEY_EXPORT_FOLDER_LABEL, null);
    }

    public void setExportFolder(String uri, String label) {
        preferences.edit().putString(KEY_EXPORT_FOLDER, uri).putString(KEY_EXPORT_FOLDER_LABEL, label).apply();
    }

    public boolean welcomeShown() {
        return preferences.getBoolean(KEY_WELCOME_SHOWN, false);
    }

    public void setWelcomeShown() {
        preferences.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply();
    }
}
