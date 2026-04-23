Subsampling Scale Image View
===========================
A powerful custom image view for Android, inspired by Dave Morrissey's original library. It is designed for seamless photo galleries and displaying massive images—such as maps and blueprints—without risk of `OutOfMemoryError`.

By leveraging subsampling and tiling, the view loads a low-resolution base layer and dynamically overlays high-resolution tiles for the visible area as you zoom. This ensures a smooth experience with minimal memory overhead. Tiling can be disabled for smaller images or direct bitmap objects.

## Features

#### Image display

* Load images from assets, resources, file system, or bitmaps
* Automatic EXIF rotation for gallery and camera images
* Manual 90° rotation increments
* Support for rendering specific image regions
* Preview image support during high-res loading
* Dynamic image swapping at runtime
* Custom bitmap decoder integration

*With tiling enabled:*

* Display massive images that exceed standard memory limits
* Sharp high-resolution details upon zooming
* Tested up to 20,000x20,000px (larger images supported)

#### Gesture detection

* Fluid one-finger panning
* Precise two-finger pinch-to-zoom
* Quick scale (one-finger zoom)
* Simultaneous panning while zooming
* Seamless transitions between gestures
* Fling momentum for natural movement
* Double-tap to toggle zoom levels
* Configurable pan and zoom gesture locks

#### Animation

* Built-in methods for animating scale and focal point
* Adjustable duration and easing interpolation
* Support for uninterruptible animation sequences

#### Overridable event detection

* Native `OnClickListener` and `OnLongClickListener` support
* Advanced event interception via `GestureDetector` and `OnTouchListener`
* Fully extendable for custom gesture logic

#### Easy integration

* Built for `ViewPager` gallery implementations
* Automatic state restoration (scale/center) after screen rotation
* Easily add synchronized overlays and graphics
* Native support for view resizing and `wrap_content`

## About

Copyright 2026 SonAI-Team. Licensed under the Apache License, Version 2.0. Attribution is not required but greatly appreciated. Star the repo if you find it useful!