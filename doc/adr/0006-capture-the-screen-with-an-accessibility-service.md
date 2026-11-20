# 6. Capture the screen with an accessibility service

Date: 2026-11-20

## Status

Accepted

## Context

The owner wants a see-through lock screen, like the Boox Palma's transparent screensaver: when the device sleeps, the lock screen shows what was on screen. The owner chose automatic capture only, with no lock button, driven by events rather than polling, and without making the lock slower.

A normal app can't catch the power button: the system handles it before accessibility key filtering, so the earliest signal is `ACTION_SCREEN_OFF`. On this ROM the e-ink keyguard appears while Android treats the display as off, and `screencap` returns black then (`research/lockscreen-zones.md`). Whether a screenshot taken on `ACTION_SCREEN_OFF` still has the app's pixels, and whether a file written then beats the keyguard's read, is unknown.

The options for taking screenshots of other apps were MediaProjection, which asks for consent every session and needs a foreground service, and `AccessibilityService.takeScreenshot()`, added in Android 11, which asks once when the user turns the service on and is limited to about one screenshot per second.

## Decision

We will capture with an accessibility service, `SeeThroughService`. Accessibility events, a 1×1 overlay that hears every touch as `ACTION_OUTSIDE`, and observed (never consumed) keys mark the screen as changed. After it has been still for about 1.2 s, or after 10 s if it never settles, the service captures the screen, draws the chosen style project's zones over it with `WallpaperRenderer`, and applies it. The lock screen image is therefore already current when the power button is pressed.

Capturing again on `ACTION_SCREEN_OFF` is an experimental setting, off by default, until the spike in `research/see-through-spike.md` shows it lands on the same lock.

Every app is shown unless the user hides it. Sensitive apps (money, messages, email, passwords, social) are suggested for hiding. While a hidden app is in front, a fallback wallpaper is applied instead. Screenshots are never written anywhere except the lock screen target, and automatic captures don't save a gallery PNG, unlike a manual Apply (ADR 4).

## Consequences

Locking adds no delay, and capture works in any app without asking again. The lock screen may show a screen from up to about 1.2 s before the power press, or up to 10 s on a screen that never stops changing. Apps that change their pixels without accessibility events or touches, such as a timed slideshow, aren't refreshed until the 10 s wait or the next touch.

The app now holds an always-on accessibility service and All Files Access together, which is a lot of trust; the service description says what it does and that screenshots stay on the device. While on, it keeps a renderer (about 8 MB) and a frame copy (about 4 MB) in memory, and each capture costs a PNG encode and a file write. Anyone who picks up the device sees the last screen, which the hidden apps list only partly guards against. Replacing the capture method later touches only `ScreenCapturer`'s implementation.
