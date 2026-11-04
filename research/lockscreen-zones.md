# Lock Screen Research (HITV205N, Android 11)

Measured 2026-11-04. Coordinates are pixels on the 720×1440 screen.

Evidence labels: **tested** = run on the device and confirmed in its log. **code** = read from the disassembled `Vision_SystemUI.apk`. **layout** = view bounds from `uiautomator`. **estimate** = calculated, not seen.

## Key limits

- **The wallpaper lock screen can't be screenshotted.** It exists only on the panel while Android treats the display as off, and `screencap` returns black then. `Util.posterInThread` starts a thread with the rendered bitmap; no file path for it was found (code), and no new image file appeared on storage (tested).
- With no secure lock, waking goes straight to the launcher.
- With a PIN, waking shows only the PIN pad on a plain white background, with its own clock and date (tested). Pressing Back puts the screen to sleep. The user's wallpaper and the quote don't appear on it. So only the sleep lock screen matters for this app.
- The owner confirmed by eye on the panel: forced text turned black, the forcing dots aren't visible, and the battery, ringer and hint follow the bottom band. Everything else here is from the log and code.

## Which file the sleep lock screen reads

| Finding | Evidence |
|---|---|
| It reads `/sdcard/Pictures/.lockscreen_wallpaper/lockscreen_wallpaper_custom.png` | code, tested |
| If `Download/.hmcttheme/com.hmct.theme/lock_screen_wallpaper/lockscreen_wallpaper_fixed.png` exists and is newer, that wins instead | code |
| It reloads when the file's modified time differs from global setting `fw_paper_modifed_time` | code, tested |
| `WallpaperManager.setBitmap(FLAG_LOCK)` didn't change that file or trigger a reload | tested |
| An app can replace the file with All Files Access (`MANAGE_EXTERNAL_STORAGE`), and is denied without it | tested |
| Opening the file directly truncates it first, so write to a temp file, then rename | tested |

**Retested with the real app:** three applies (fill, forced color, restore) all reloaded on the next sleep, and the logged votes matched the app's prediction. The earlier probe misses (2 of 3) weren't reproduced, and their cause wasn't proven. The device was locked with `input keyevent KEYCODE_SLEEP`, not the power button.

## Font color rule (sleep lock screen)

`Util.isWhiteAreaOfView`, called from `EinkKeyguardController.updateFullWaperTextColor`. Evidence: code, and the log counts match in every case below.

- **Two bands:** top `0 < y < 360`, bottom `1080 < y < 1440`. The middle half has no effect.
- **Sampling:** only pixels where `x % 4 == 0` and `y % 4 == 0`, with `x > 0`. That's 15,931 samples per band.
- **Vote:** `red > 192` adds 1, anything else subtracts 1. Green and blue are ignored.
- **Result:** count above 0 gives black text, otherwise white text. A tie gives white text.

| Band | Controls |
|---|---|
| Top | Clock, AM/PM, date, battery status, weather, sleep icon, notifications |
| Bottom | Quote, unlock hint |

| Test image | Top | Bottom |
|---|---|---|
| White | +15931 | +15931 |
| Black | -15931 | -15931 |
| Gray 192 | -15931 | -15931 |
| Gray 193 | +15931 | +15931 |
| Red (255,0,0) | +15931 | +15931 |
| Cyan (0,255,255) | -15931 | -15931 |
| Middle half white only | -15931 | -15931 |
| Top half white, bottom half black | +15931 | -15931 |
| Top half black, bottom half white | -15931 | +15931 |
| Black, sampled pixels only (193,0,0) | +15931 | +15931 |
| White, sampled pixels only (192,255,255) | -15931 | -15931 |
| User's stars wallpaper | -12209 | -12943 |

The last two test images show the text color can be set by changing only 1/16 of the pixels. The owner checked the panel and can't see the dots.

**Panel test applied** (`tools/make_force_color_test.py`): stars plus white panels behind the clock and quote.

| Band | Vote with panels only | Pixels changed | Final logged vote |
|---|---|---|---|
| Top | -4221 | 2,211 (under 1% of the band) | +201 |
| Bottom | +4555 | 0 | +4555 |

A full-width white quote panel flips the bottom band without any forcing.

## Zones

| Zone | Rectangle (l,t,r,b) | Evidence | Notes |
|---|---|---|---|
| Clock | 60,164,347,264 | layout | Measured at `10:49`. Widest time not measured |
| AM/PM | 347,231,383,264 | layout | Moves with clock width |
| Date | 60,294,202,332 | layout | Measured at `Wed, Nov 4`. Longest date not measured |
| Notifications | 50,385,680,445 | layout | One row. Maximum height not measured |
| Quote, full box | 0,1065,720,1289 | layout, code | Fixed height, 5 lines at most, bottom-aligned |
| Unlock hint | about 145,1376,575,1416 | owner + estimate | Bottom-center, about 415x30; follows the bottom band |
| Status bar | 0,0,720,48 | layout | Window frame, not its icons |
| Battery % | about 20,1376,110,1416 | owner + estimate | Bottom-left, about 60x20; follows the bottom band |
| Ringer icon | about 670,1376,700,1416 | owner + estimate | Bottom-right, about 18x30; follows the bottom band |

## Quote layout

All 100 quotes came from the APK (`mottos.json`) and were laid out with a real `TextView` set up like `motto_aera` (`motto_layout.csv`). Each is English, then a line break, then Chinese. This assumes 14dp text from `MottoText_style`; a different size set at runtime would shift these numbers.

Text always ends at `y = 1289`. The side margins are `28dp` (56 px), leaving 608 px for text. The widest line reported 615 px, more than that, probably because the measurement counts trailing spaces. Not verified.

| Chinese starts at y | Quotes |
|---|---|
| 1249 (1 line) | 62 |
| 1206 (2 lines) | 4 |
| 1198 (2 lines) | 33 |
| 1155 (3 lines) | 1 |

| Whole quote starts at y | Quotes |
|---|---|
| 1202 | 30 |
| 1159 | 34 |
| 1157 | 1 |
| 1108 | 17 |
| 1073 | 1 |
| 1065 | 17 |

**Verdict: the Chinese/English boundary moves, so no fixed zone separates them cleanly.**

- A band from 1198 to 1289 holds all the Chinese for 99 of 100 quotes. On 62 quotes it also covers the last English line.
- A band from 1249 to 1289 is Chinese only on all 100, but cuts off the upper Chinese lines on 38.

## Other settings seen

| Setting | Value | Meaning |
|---|---|---|
| `eink_keyguard_time_style_wallpaper` | 0 | Style for the wallpaper lock screen |
| `eink_keyguard_motto_wallpaper` | 1 | Quote is shown |
| `ro.hmct.epd.type` | 2 | Single-screen e-ink product, per the log |

## State after testing

- Lock wallpaper file restored, checksum matches `research/backup/`. Its modified time changed.
- AOSP lock wallpaper was overwritten and put back from a backup through `setBitmap`, so it's re-encoded.
- Probe uninstalled, its working folder removed.
- Lock type changed from None to swipe, then to the PIN the user asked for.
- The stars wallpaper is back (checksum matches `research/backup/`), restored through the app.
- Battery, ringer and notification tests haven't been run.

## Device behavior found while building the overhaul

| Finding | Evidence |
|---|---|
| `IEpdManager$Stub.asInterface` throws `NoSuchMethodException` in a non-debuggable app, so `forceClear()` isn't reachable from a release build | tested |
| A three-step black, white, content flash shows as three panel updates in the display log | tested in the log; not judged by eye |
| `FilesActivity` crashes for any folder or root link: `IllegalStateException: Launcher activity not found for package com.android.documentsui` | tested |
| The file picker (`PickActivity`) works but ignores injected taps on files; Cx File Explorer accepts them | tested |

| Build, editor open on a real project | Memory (PSS) | Launch |
|---|---|---|
| Old debug build | 85 MB | 1.12 s |
| New debug build | 89 MB | 1.11 s |
| New release build, hardware rendering | 68 MB | 0.66 s |
| New release build, software rendering | 40 MB | 0.70 s |
