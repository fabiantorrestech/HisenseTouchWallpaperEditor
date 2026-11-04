package io.github.fabiantorrestech.touchwallpaper.ui;

import android.net.Uri;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.export.SavedFile;
import io.github.fabiantorrestech.touchwallpaper.settings.AppSettings;
import java.io.IOException;

/**
 * The Save PNG flow, shared by both steps. Asks for a name, or opens the system's save screen when
 * the user chose "Ask each time". Create this while the activity is being constructed.
 */
final class PngSaveFlow {
    /** Produces the image on the worker thread, once the user has chosen where it goes. */
    interface PngProducer {
        byte[] produce() throws IOException;
    }

    interface Listener {
        void onPngSaved(SavedFile file);
    }

    private final BaseActivity activity;
    private final Listener listener;
    private final ActivityResultLauncher<String> saveScreen;
    private PngProducer pendingProducer;

    PngSaveFlow(BaseActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        saveScreen = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/png"), this::onDocumentCreated);
    }

    void start(String suggestedName, PngProducer producer) {
        pendingProducer = producer;
        if (new AppSettings(activity).askEachTime()) {
            saveScreen.launch(ImageSaver.fileNameFor(suggestedName));
        } else {
            askForName(suggestedName);
        }
    }

    private void askForName(String suggestedName) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(suggestedName);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.name_your_file)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = input.getText().toString();
                    runOnWorker(png -> new ImageSaver(activity).saveToDefaultLocation(png, name));
                })
                .show();
    }

    private void onDocumentCreated(Uri document) {
        if (document != null) {
            runOnWorker(png -> new ImageSaver(activity).saveToCreatedDocument(png, document));
        }
    }

    private interface Destination {
        SavedFile write(byte[] png) throws IOException;
    }

    private void runOnWorker(Destination destination) {
        PngProducer producer = pendingProducer;
        if (producer == null) {
            return;
        }
        Background.run(() -> {
            try {
                SavedFile saved = destination.write(producer.produce());
                activity.runOnUiThread(() -> {
                    listener.onPngSaved(saved);
                    SavedFileDialog.show(activity, activity.getString(R.string.saved_title), null,
                            saved.uri, saved.folderUri, saved.path);
                });
            } catch (IOException | RuntimeException e) {
                activity.runOnUiThread(() -> activity.showError(e));
            }
        });
    }
}
