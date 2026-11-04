# 4. Apply by replacing the ROM wallpaper file

Date: 2026-11-04

## Status

Accepted

## Context

The wallpaper lock screen on this device is Hisense's own e-ink keyguard. Disassembly and device tests show it reads `Pictures/.lockscreen_wallpaper/lockscreen_wallpaper_custom.png` and reloads when that file's modified time differs from the global setting `fw_paper_modifed_time`. `WallpaperManager.setBitmap` with `FLAG_LOCK` does not change that file and did not trigger a reload, so the public API cannot apply a wallpaper to this screen. Exact pixels also matter, because text color can be forced by changing individual sampled pixels, and a re-encoding system picker could undo that. Whether the system picker re-encodes was not tested.

## Decision

We will apply a wallpaper by writing a lossless PNG to a temporary file in that folder and renaming it over the ROM's file, which requires All Files Access (`MANAGE_EXTERNAL_STORAGE`) and a target SDK of 30. The owner chose this over saving a PNG for the system picker. The app will also save a PNG to the gallery on every apply, and will keep a copy of the wallpaper it first replaced.

## Consequences

One tap applies exact pixels, and reading `fw_paper_modifed_time` lets the app report whether the lock screen has loaded the new file. The app depends on an undocumented path and setting that a firmware update could change, and it holds a broad storage permission. The gallery PNG is the fallback if the path stops working. An app-written file reloaded in only 1 of 3 probe attempts, while `adb push` plus `touch` always did. A dropped adb connection may explain the misses, but this is unproven, so it must be retested with the real app. Replacing this approach would mean rewriting only the export code, since the renderer does not depend on it.
