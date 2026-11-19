# Mecha Loop Unlocker (Infinix GT 20 Pro)

Custom Kotlin Android application to unlock custom RGB colors and automatic music-reactive lighting (triggered when **YouTube Morphe**, YouTube Music, Spotify, or local audio starts playing) on the **Infinix GT 20 Pro (X6871)**.

## Features
- **Full RGB Hex Color Mixer**: Unlock colors beyond the 8 stock XOS Mecha Loop presets.
- **YouTube Morphe / MediaSession Auto-Trigger**: `MediaTriggerListener` watches active Android `MediaSession` states and automatically launches the beat-sync service the moment playback begins.
- **Real-Time FFT Bass Visualizer**: Hooks into global audio session `0` (`android.media.audiofx.Visualizer`) to pulse the Mecha Loop LEDs in sync with bass frequencies.
- **Multi-Layer Hardware Bridge**:
  1. Direct `/sys/class/leds/` kernel sysfs control (for Rooted / Magisk / KernelSU devices).
  2. Transsion / XOS `Settings.System` overrides + `com.transsion.lighting.ACTION_UPDATE_LED` broadcast intents (for Non-Root devices via ADB/Shizuku).

## GitHub Actions Build
1. Upload the contents of this ZIP to a GitHub repository.
2. Open the **Actions** tab and run **Build Mecha Loop Controller APK** (or push any commit to `main`).
3. Download `MechaLoopController-debug-apk` from the workflow **Artifacts** section.

## Non-Root ADB Unlock Command (Optional)
If your GT 20 Pro is not rooted, grant `WRITE_SECURE_SETTINGS` once via ADB or Shizuku so the app can override XOS system lighting keys:
```bash
adb shell pm grant com.custom.mechaloop android.permission.WRITE_SECURE_SETTINGS
```
