# Bouncer

Bouncer is an Android live wallpaper with glowing balls, configurable colours, touch interaction, and optional collision physics.

## Features

- Dedicated wallpaper render thread with explicit visibility, surface, and destruction state handling.
- Glowing ball rendering using a cached bitmap per render thread.
- Optional solid-body collisions with grid-based broad-phase checks.
- Configurable ball count, base speed, palette, size behaviour, lifespan, and touch interaction.
- Compose-based dashboard and settings screens.

## Performance Notes

Performance depends on device hardware, display resolution, refresh rate, selected ball count, collision physics, and size behaviour. The wallpaper targets 60 fps and adapts its performance budget when Android presents the surface at a sustained slower cadence.

- Low and medium ball counts are generally the safest options for broad testing.
- High counts, especially 250 to 300 balls, are intentionally available but demanding.
- Collision physics increases CPU work as population density rises.
- The launcher dashboard uses a lightweight background simulation and pauses when the activity is not visible.

## Lifecycle Design

The wallpaper engine tracks three external lifecycle signals before rendering:

- wallpaper visibility
- surface readiness
- engine destruction

Rendering starts only when all three allow it. Lifecycle decisions are centralized through a render-state controller so that:

- duplicate start requests do not create multiple render threads;
- a stopping thread cannot block a later restart forever;
- stale thread exits cannot clear a newer renderer reference;
- a visible engine automatically restarts after a delayed thread shutdown finishes.

The render thread uses a bounded join during stop requests so Android callbacks are not blocked indefinitely.

## Settings

Bouncer stores wallpaper preferences in `SharedPreferences` and validates values in `SettingsManager` before the renderer reads them. Existing palette strings from earlier builds remain readable and are migrated logically to stable palette identifiers.

Backup is enabled for the wallpaper preference file so users keep their settings across device restore and transfer flows.

## Testing

### Local verification

On Windows:

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat bundleRelease
```

If a device or emulator is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### Lifecycle stress checks

1. Apply Bouncer as the live wallpaper.
2. Switch repeatedly between the launcher and other apps.
3. Lock and unlock the device repeatedly.
4. Enter and leave wallpaper preview.
5. Open and close wallpaper settings.
6. Test 10, 50, 250, and 300 balls with physics both on and off.
7. Confirm the wallpaper always resumes rendering when visible and stops rendering when hidden.
8. Watch Logcat for `BouncerWallpaper` lifecycle, stop-timeout, and restart messages.
