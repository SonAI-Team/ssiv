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

## Contributing

Contributions are welcome! Whether you're fixing a bug, suggesting a feature, or improving
documentation, please read our [Contributing Guide](CONTRIBUTING.md) to get started.

## About

Copyright 2026 SonAI-Team. Licensed under the Apache License, Version 2.0. Attribution is not
required but greatly appreciated. Star the repo if you find it useful!

---
*Created with focus by the SonAI Team.*
