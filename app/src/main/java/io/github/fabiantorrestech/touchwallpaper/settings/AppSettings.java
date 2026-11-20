package io.github.fabiantorrestech.touchwallpaper.settings;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
    private static final String KEY_SEE_THROUGH = "see_through";
    private static final String KEY_SEE_THROUGH_STYLE = "see_through_style";
    private static final String KEY_SEE_THROUGH_STYLE_STAMP = "see_through_style_stamp";
    private static final String KEY_SEE_THROUGH_FALLBACK = "see_through_fallback";
    private static final String KEY_SEE_THROUGH_HIDDEN_APPS = "see_through_hidden_apps";
    private static final String KEY_SEE_THROUGH_ON_SCREEN_OFF = "see_through_on_screen_off";
    private static final String KEY_SEE_THROUGH_PROBE = "see_through_probe";

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

    // See-through lock (ADR 6). The service reads these through a change listener.

    public boolean seeThroughEnabled() {
        return preferences.getBoolean(KEY_SEE_THROUGH, false);
    }

    public void setSeeThroughEnabled(boolean value) {
        preferences.edit().putBoolean(KEY_SEE_THROUGH, value).apply();
    }

    /** The project whose zones are drawn over the screenshot, or null for the plain screenshot. */
    public String seeThroughStyleProjectId() {
        return preferences.getString(KEY_SEE_THROUGH_STYLE, null);
    }

    public void setSeeThroughStyleProjectId(String projectId) {
        preferences.edit().putString(KEY_SEE_THROUGH_STYLE, projectId).apply();
    }

    /** Changes a value the service listens to, so it reloads the style project after edits. */
    public void touchSeeThroughStyle() {
        preferences.edit().putLong(KEY_SEE_THROUGH_STYLE_STAMP, System.currentTimeMillis()).apply();
    }

    /** The hidden-app wallpaper: null for Automatic, {@code FallbackWallpaper.ORIGINAL} or {@code IMAGE}, or a project id. */
    public String seeThroughFallbackId() {
        return preferences.getString(KEY_SEE_THROUGH_FALLBACK, null);
    }

    public void setSeeThroughFallbackId(String id) {
        preferences.edit().putString(KEY_SEE_THROUGH_FALLBACK, id).apply();
    }

    /** Apps whose screen never appears on the lock screen. */
    public Set<String> seeThroughHiddenApps() {
        return Collections.unmodifiableSet(new HashSet<>(
                preferences.getStringSet(KEY_SEE_THROUGH_HIDDEN_APPS, Collections.emptySet())));
    }

    public void setSeeThroughHiddenApps(Set<String> packages) {
        // A copy, because SharedPreferences mustn't be handed a set it already owns.
        preferences.edit().putStringSet(KEY_SEE_THROUGH_HIDDEN_APPS, new HashSet<>(packages)).apply();
    }

    /** Experimental: also capture when the screen turns off. Off until the device spike proves it works. */
    public boolean seeThroughOnScreenOff() {
        return preferences.getBoolean(KEY_SEE_THROUGH_ON_SCREEN_OFF, false);
    }

    public void setSeeThroughOnScreenOff(boolean value) {
        preferences.edit().putBoolean(KEY_SEE_THROUGH_ON_SCREEN_OFF, value).apply();
    }

    /** Debug builds only: write solid test images on screen off and on (research/see-through-spike.md). */
    public boolean seeThroughProbe() {
        return preferences.getBoolean(KEY_SEE_THROUGH_PROBE, false);
    }

    public void setSeeThroughProbe(boolean value) {
        preferences.edit().putBoolean(KEY_SEE_THROUGH_PROBE, value).apply();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
