# See-through Lock Spike (HITV205N)

Status: **partly run** (S2–S5 on 2026-11-23, `KEYCODE_SLEEP` only). Fill in the rest of the results table, then decide whether "capture on screen off" can be turned on by default (ADR 6).

Evidence labels as in `lockscreen-zones.md`: **tested**, **code**, **estimate**.

## Why

A normal app can't catch the power button, so the app keeps the lock screen current by capturing after the screen settles. These tests decide whether a last-moment capture on `ACTION_SCREEN_OFF` also works on this ROM, and set the timing constants in `SeeThroughService`.

## Setup

1. Build and install the debug app (`./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`).
2. Back up the lock wallpaper first (CLAUDE.md):

   ```
   adb pull /sdcard/Pictures/.lockscreen_wallpaper/lockscreen_wallpaper_custom.png research/backup/spike-before.png
   ```

3. In the app: Home > See-through lock. Allow All Files Access, tick the switch, then turn on "Touch Wallpaper see-through lock" in Accessibility.
4. Watch the log in a second terminal (Windows):

   ```
   adb logcat -v threadtime | findstr "SeeThrough einkkeyguard whiteCount"
   ```

5. Lock with `adb shell input keyevent KEYCODE_SLEEP` unless a step says power button. Run adb with a time limit and retry if it drops.

The keyguard's vote identifies which image it loaded: all white gives `whiteCount` +15931 per band, all black gives −15931 (`lockscreen-zones.md`).

## Tests

| # | Question | How | Pass |
|---|---|---|---|
| S1 | Does the service ever see `KEYCODE_POWER`? | Press the real power button 3 times; look for `saw the power key` | Expected never |
| S2 | Does a screenshot taken on screen off still hold the app's pixels? | Tick "Also capture when the screen turns off", change the screen, lock within 1 s. Look for `screen-off applied … dark N%` or `skipped: black frame after screen off` | Real pixels (dark well under 99%) |
| S3 | Does a file written on screen off reach that same lock? | Debug build: tick the "Test: write black…" box. Wake (writes white), then lock (writes black). Repeat 10 times with KEYCODE_SLEEP and 5 with the power button | Vote −15931 on ≥ 9 of 10 |
| S4 | How long does each step take? | Normal use; read `applied: capture … scan … compose … apply` and `encode … write` | Record medians |
| S5 | Do e-reader page turns trigger a capture? | Stock reader and KOReader: turn a page by tap, swipe and page key; look for `applied` within ~2 s | Record per app |
| S6 | Memory, and does the service survive? | `adb shell dumpsys meminfo io.github.fabiantorrestech.touchwallpaper.debug` with see-through on and off; reboot, then check it still logs | Within ~15 MB of off; survives reboot |
| S7 | What do secure screens (FLAG_SECURE) give? | Open a banking app that blocks screenshots; read `dark N%` | Record |
| S8 | Why is KOReader black? | Open a book in KOReader, then `adb exec-out screencap -p > research/raw/koreader.png` and take a system screenshot (power + volume down). Read `black frame from org.koreader.launcher` in the log | If both are black too, KOReader's screen isn't visible to Android screenshots on this ROM and only the fallback can be shown |

Untick the test box when done, turn see-through off (it puts the fallback back), and compare the ROM file with the backup.

## Results

| # | Result | Evidence | Notes |
|---|---|---|---|
| S1 | | | |
| S2 | Not needed: by the time `ACTION_SCREEN_OFF` arrives, the keyguard has already read the file | tested 2026-11-23, `KEYCODE_SLEEP` only | Screenshots taken in the ~1 s between the key and the broadcast come back black, with the app or `null` in front. Until fixed, these were read as a hidden app and put the fallback up |
| S3 | **Fails.** The keyguard logs `FW, time` (its file check) at once on the key; the service logs `screen off` about 1.2 s later. A file written on screen off is only seen at the next lock | tested 2026-11-23, log timestamps, `KEYCODE_SLEEP` only | Power button not tested. "Capture on screen off" is now debug-only |
| S4 | Mihon page (≈800–960 KB): capture 30–70 ms, scan 25–65, compose 20, encode **450–490 → 145–175 ms** with `FastPngEncoder`, write 35–140 ms. Tap → file written: **≈1.9 s → ≈0.9 s** | tested 2026-11-23 | Quiet 1200 → 500 ms, `notificationTimeout` 300 → 100 ms, plus one follow-up capture 1.5 s after each capture |
| S5 | Mihon (`app.mihon`): tap page turns are captured. Mihon turns the page 0.3–0.45 s after the tap (most likely waiting for a double tap), so a 300 ms quiet captured the old page and lost the 1.1 s screenshot slot | tested 2026-11-23 | With 500 ms: lock shows the new page when power comes ≳0.85 s after the tap; at 0.55–0.75 s the write lands 50–240 ms too late and that one lock shows the previous page |
| S6 | | | |
| S7 | | | |
| S8 | Black lock screen in KOReader (owner, 2026-11-20). Now shows the hidden-app wallpaper instead. Cause not confirmed | owner | |

## Decisions to make from the results

- **S2 and S3 pass:** turn "capture on screen off" on by default and consider a shorter quiet period, because the screen-off capture covers the last moment.
- **S2 or S3 fails:** leave it off, and hide the setting in release builds.
- **S4:** set `QUIET_MILLIS` to roughly the time a typical screen takes to settle; if encode plus write is over ~500 ms, look at a faster PNG writer.
- **S5 fails for a reader:** add that reader's page-turn signal, or rely on the 10 s maximum wait.
