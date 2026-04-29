Subsampling Scale Image View
===========================
A powerful custom image view for Android, inspired by Dave Morrissey's original library. It is
designed for seamless photo galleries and displaying massive images—such as maps and
blueprints—without risk of `OutOfMemoryError`.

By leveraging subsampling and tiling, the view loads a low-resolution base layer and dynamically
overlays high-resolution tiles for the visible area as you zoom. This ensures a smooth experience
with minimal memory overhead.

## Documentation

For detailed guides, examples, and API references, please visit our **[Wiki](https://github.com/SonAI-Team/ssiv/wiki)**.

## How it Works

SSIV uses a two-tier loading strategy to display massive images:

1. **The Base Layer:** A low-resolution version of the entire image is loaded first. This provides
   an immediate preview and acts as a fallback when zooming.
2. **The Tile Layer:** As you zoom in, the library calculates which regions of the image are visible
   and loads high-resolution "tiles" for only those specific areas.

## Features

* **Tiling & Subsampling:** Load massive images that exceed standard memory limits.
* **Expanded Sources:** Load images from assets, resources, files, and even directly from **ZIP archives** using `ImageSource.zipEntry`.
* **Jetpack Compose Support:** Native `SubsamplingImage` Composable for idiomatic integration.
* **Coil Integration:** Seamlessly use Coil's disk cache and loading pipeline for your images.
* **Modern Stack:** 100% Kotlin, Coroutines for async loading, and AndroidX support (**minSdk 23**).
* **Enhanced Gestures & Input:** 
    * Fluid one-finger panning and two-finger pinch-to-zoom.
    * **Mouse Support:** Zoom seamlessly using the scroll wheel.
    * **Keyboard/D-pad:** Navigate and scale using arrow keys, D-pad, and +/- keys.
    * Quick scale and fling momentum.
* **Animation:** Built-in methods for animating scale and focal point with support for custom **Interpolators** and easing styles.
* **Smart Memory Management:** Built-in support for **downsampling** and parallel decoding via `SkiaPooledImageRegionDecoder`.
* **State Management:** Automatic **state restoration** across configuration changes (orientation, theme, etc.) and `bindToLifecycle` support.

## Installation

Add the dependency to your `build.gradle` file:

```kotlin
dependencies {
    // Core library
    implementation("io.github.sonai-team:ssiv:1.1.1")

    // Jetpack Compose support (optional)
    implementation("io.github.sonai-team:ssiv-compose:1.1.1")

    // Coil integration (optional)
    implementation("io.github.sonai-team:ssiv-coil:1.1.1")
}
```

## SDK Support Matrix

The library automatically adapts its decoding engine and features based on the device's Android version:

| Feature / Format         | SDK 23+ (6.0) | SDK 26+ (8.0) | SDK 28+ (9.0) | SDK 31+ (12) | SDK 34+ (14) |
|:-------------------------|:-------------:|:-------------:|:-------------:|:------------:|:------------:|
| **Core Tiling & Zoom**   |       ✅       |       ✅       |       ✅       |      ✅       |      ✅       |
| **ZIP Archive Support**  |       ✅       |       ✅       |       ✅       |      ✅       |      ✅       |
| **Mouse & Keyboard**     |       ✅       |       ✅       |       ✅       |      ✅       |      ✅       |
| **Parallel Decoding**    |       ✅       |       ✅       |       ✅       |      ✅       |      ✅       |
| **Hardware Bitmaps**     |       ❌       |       ✅       |       ✅       |      ✅       |      ✅       |
| **Wide Gamut (P3)**      |       ❌       |       ✅       |       ✅       |      ✅       |      ✅       |
| **ImageDecoder API**     |       ❌       |       ❌       |       ✅       |      ✅       |      ✅       |
| **AVIF Support**         |       ❌       |       ❌       |       ❌       |      ✅       |      ✅       |
| **Ultra HDR (Gainmaps)** |       ❌       |       ❌       |       ❌       |      ❌       |      ✅       |

## Supported Formats

SSIV is optimized for **pixel-based (raster) images**. It dynamically chooses between `ImageDecoder` (SDK 28+) and `BitmapFactory` to ensure maximum compatibility:

*   **Standard (SDK 23+)**: JPEG, PNG, BMP, WBMP, ICO.
*   **Web-Optimized (SDK 23+)**: WebP (Lossy, Lossless, and Transparent).
*   **High-Efficiency (SDK 30+)**: Full support for **HEIF** and **HEIC** containers.
*   **Next-Gen AVIF (SDK 31+)**: Native support for high-compression **AVIF** files.
*   **Pro & HDR (SDK 34+)**: Support for **Ultra HDR** (Gainmaps), **DCI-P3**, and **16-bit (F16)** color depth with automatic window color mode switching.
*   **Photography**: **RAW** images (DNG) are supported via the single-image decoder path.

> [!IMPORTANT]
> **Summary**: This library is designed for **static raster images**. It does **not** support vector
> graphics (SVG), video files, or animated formats (GIF/Animated WebP - only the first frame is shown).

## Contributing

Contributions are welcome! Whether you're fixing a bug, suggesting a feature, or improving
documentation, please read our [Contributing Guide](CONTRIBUTING.md) to get started.

## About

Copyright 2026 SonAI-Team. Licensed under the Apache License, Version 2.0. Attribution is not
required but greatly appreciated. Star the repo if you find it useful!

---
*Created with focus by the SonAI Team.*
