Subsampling Scale Image View
===========================
A powerful custom image view for Android, inspired by Dave Morrissey's original library. It is
designed for seamless photo galleries and displaying massive images—such as maps and
blueprints—without risk of `OutOfMemoryError`.

By leveraging subsampling and tiling, the view loads a low-resolution base layer and dynamically
overlays high-resolution tiles for the visible area as you zoom. This ensures a smooth experience
with minimal memory overhead.

## Documentation

For detailed guides, examples, and API references, please visit our *
*[Wiki](https://github.com/SonAI-Team/ssiv/wiki)**.

## How it Works

SSIV uses a two-tier loading strategy to display massive images:

1. **The Base Layer:** A low-resolution version of the entire image is loaded first. This provides
   an immediate preview and acts as a fallback when zooming.
2. **The Tile Layer:** As you zoom in, the library calculates which regions of the image are visible
   and loads high-resolution "tiles" for only those specific areas.

## Features

* **Tiling & Subsampling:** Load massive images that exceed standard memory limits.
* **Modern Stack:** 100% Kotlin, Coroutines for async loading, and AndroidX support.
* **Gestures:** Fluid one-finger panning, precise two-finger pinch-to-zoom, quick scale, and fling
  momentum.
* **Animation:** Built-in methods for animating scale and focal point with adjustable duration and
  easing.
* **Easy Integration:** Built for `ViewPager` galleries, supports state restoration, and easy to
  extend.

## Supported Formats

SSIV is optimized for **pixel-based (raster) images**. It leverages the system's `ImageDecoder` (SDK 28+) and `BitmapRegionDecoder` to provide high-performance rendering of:

*   **Standard**: JPEG, PNG, BMP, WBMP, ICO.
*   **Web-Optimized**: WebP (Lossy, Lossless, and Transparent).
*   **High-Efficiency (SDK 30+)**: Full support for **HEIF** and **HEIC** containers.
*   **Modern & HDR (SDK 34+)**: Native support for **AVIF**, **DCI-P3**, **16-bit (F16)** color depth, and **Ultra HDR** (Gainmaps) with automatic window color mode switching.
*   **Pro Photography**: **RAW** images (DNG) are supported via the single-image decoder path.

> [!IMPORTANT]
> **Summary**: This library is designed for **static raster images**. It does **not** support vector
> graphics (SVG), video files, or animated formats (GIF/Animated WebP - only the first frame is shown).
> 
> **For extremely large, static pixel-based content, SSIV is the industry-standard approach.**

## Contributing

Contributions are welcome! Whether you're fixing a bug, suggesting a feature, or improving
documentation, please read our [Contributing Guide](CONTRIBUTING.md) to get started.

## About

Copyright 2026 SonAI-Team. Licensed under the Apache License, Version 2.0. Attribution is not
required but greatly appreciated. Star the repo if you find it useful!

---
*Created with focus by the SonAI Team.*
