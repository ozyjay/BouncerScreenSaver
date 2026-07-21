# Bouncer Live Wallpaper

A high-performance, highly customizable Android Live Wallpaper featuring bouncing, glowing balls with realistic physics.

## 🌟 Features

- **High-Performance Engine:** Uses a dedicated background render thread with a 60 FPS cap to ensure silky-smooth animations without impacting system UI responsiveness.
- **Advanced Physics:** 
    - Toggleable solid-body collisions.
    - **Spatial Grid Optimization ($O(N)$):** Efficient collision detection that allows for high ball counts without melting your battery.
- **Visual Customization:**
    - **Color Palettes:** Choose from Neon, Ocean, Fire, Pastel, Forest, or full Random modes.
    - **Size Dynamics:** Set balls to shrink or grow over time. Each ball has a unique variability factor, meaning some grow/shrink faster than others for a more chaotic, natural look.
    - **Natural Flow:** Each ball is assigned a randomized lifetime (50% to 150% of the average) and velocity, ensuring a dynamic, asynchronous visual flow.
    - **High Density:** Supports anywhere from 1 to 1,000 balls simultaneously.
- **Modern Settings UI:** A Jetpack Compose-based settings screen with real-time preview and intuitive navigation.
- **Interactive Gamification:** Toggleable "Destroy on Touch" mode that allows users to pop balls in proximity to their touch points.
- **Battery Optimized:** Automatically kills the render process when the wallpaper is not visible.

## 🚀 How to Use

1. **Launch:** Open the app from your launcher to access the "Set Wallpaper" dashboard.
2. **Setup:** Click "Set Wallpaper" to open the system Live Wallpaper picker.
3. **Customize:** Tap the **Settings (Gear Icon)** to adjust physics, colors, and behavior.
4. **Apply:** Confirm for your Home and/or Lock screen.

## 🔋 Battery Impact Estimates

Estimates represent additional battery drain per hour of **screen-on time**. Note that background usage is **0%**.

| Configuration | Settings | CPU Usage | Est. Hourly Drain |
| :--- | :--- | :--- | :--- |
| **Eco** | 20-50 Balls, Physics ON, Static Size | < 3% | ~2% - 3% |
| **Busy** | 200 Balls, Physics ON, Dynamic Size | 8% - 12% | ~5% - 7% |
| **Chaos** | 1000 Balls, Physics OFF | 15% - 20% | ~8% - 12% |
| **Extreme** | 1000 Balls, Physics ON | 30% - 50%+ | ~15% - 25%+ |

*Note: Results may vary based on device resolution and screen refresh rate.*

## 🛠 Technical Details

- **Core:** Kotlin & Android WallpaperService.
- **Graphics:** Android Canvas API with **Bitmap Caching** (glow effects are pre-rendered, with drawing objects reused to minimize allocation pressure).
- **Physics:** Spatial Binning (Grid-based partitioning).
- **Architecture:** 
    - Dedicated `RenderThread` for graphics/logic.
    - `SettingsManager` for centralized, type-safe preference handling.
    - Jetpack Compose for the configuration UI.
