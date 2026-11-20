# 7. Pluggable lock screen targets per device profile

Date: 2026-11-20

## Status

Accepted

## Context

The app was built for one device, the Hisense Touch Lite, whose e-ink keyguard reads a ROM file (ADR 4). The owner wants see-through lock to be flexible enough for other e-ink devices. Those use other ROMs, other screen sizes and unmeasured lock screens, and many draw their own sleep screen. The wallpaper size of 720×1440 is a constant used in about 25 places, and the zones in `zones.json` were measured only on the Touch Lite.

## Decision

We will describe each device with a `DeviceProfile`, chosen by `DeviceProfiles.detect()`, which names its screen size, whether its zones and font color rule were measured, and its `LockScreenTarget`. The Touch Lite uses `HisenseRomFileTarget`, which wraps `WallpaperExporter`. Every other device uses `WallpaperManagerLockTarget`, the standard lock wallpaper, and gets the plain screenshot with no zones. Capturing sits behind `ScreenCapturer`. The editor and the `Screen` constants are left as they are for now.

## Consequences

See-through lock can run on any Android 11+ device, and a newly researched device needs only a profile and a target. On devices whose sleep screen ignores the standard lock wallpaper, it applies without visible effect, which the app can't detect. Zones on other devices, and an editor sized to other screens, still need the constants replaced by the profile's size; that is left for later.
