# Hisense Touch Lite Lock Screen Wallpaper Maker

Android app that builds lock screen wallpapers: Step 1 crops an image, Step 2 adds outlines or fills behind lock screen components. Projects stay editable.

## Device

- `HITV205N` (Hisense Touch Lite), Android 11 / API 30, non-root, arm64
- 720×1440, 320 dpi, e-ink
- This app targets this one device. Sideloaded. Release and debug builds are both signed with the shared debug key (`DEBUG_SHARED`, android-apps ADR 6); the debug build is a separate app, `io.github.fabiantorrestech.touchwallpaper.debug` (android-apps ADR 7).

## Build

- Run from native Windows. SDK: `%LOCALAPPDATA%\Android\Sdk`. JDK: Android Studio's `jbr`.
- `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk` (installs the `.debug` app). The release build is published to the app hub with `android-apps/scripts/publish-local.ps1 -App touchwallpaper`.

## Where things live

- `doc/adr/`: decisions that are costly to reverse. Read these before changing the stack or storage.
- `research/`: lock screen measurements, screenshots and font color test results.
- `app/src/main/assets/zones.json`: measured zone rectangles the app ships with.

## Rules

- **Font color behavior comes from measurements in `research/`, never from guesses.** The stock Android rule is only a hypothesis until tested on this ROM.
- Zones wrap the largest possible text. The wallpaper is static and the text changes, so glyph-hugging outlines are impossible.
- `WallpaperRenderer` is the only drawing code for both preview and export. Don't add a second path.
- E-ink UI: paged screens with Prev/Next, no scrolling, no swipe animations. Redraw lightly during drags and fully on finger lift. Steppers update only their number, then commit about 600 ms after the last change.
- Low memory: keep `android:hardwareAccelerated="false"`. On this device it cut the release build from 68 MB to 40 MB while editing. Reuse the renderer's fixed buffers and don't allocate in `onDraw`.
- `Project` JSON leaves out untouched zones (`ZoneStyle.isDefault`), so opening a zone isn't an undo step. Bump `SCHEMA_VERSION` and migrate in `ProjectJson` when the format changes.
- The system Files browse screen (`FilesActivity`) crashes on this ROM. Use `ACTION_OPEN_DOCUMENT` with `EXTRA_INITIAL_URI` to show a folder.
- The ROM blocks `forceClear()` for release builds of normal apps, so `EinkRefresher` falls back to a screen flash there. Don't work around the block.
- Other chats can share this PC's adb server, so check `claude-banner.exe who` if adb starts dropping or a hook reports the device in use.
- Device tests change the device. Back up and restore the lock wallpaper, and run `cmd battery reset` after any battery test.
- Lock with `input keyevent KEYCODE_SLEEP`, then read the log tag `Util|einkkeyguard` (`whiteCount`). `screencap` can't see the sleep lock screen.
- The local adb connection drops at times: run adb with a time limit and retry. The system file picker ignores injected taps on files; Cx File Explorer accepts them.
