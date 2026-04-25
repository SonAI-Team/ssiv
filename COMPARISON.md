# Comparison: SSIV vs. Dave Morrissey's Original

This document outlines the key differences and internal improvements of this version of the Subsampling Scale Image View compared to the [original implementation](https://github.com/davemorrissey/subsampling-scale-image-view) by Dave Morrissey.

## 1. Modern Technology Stack

The most significant change is the shift to a modern Android development stack:

-   **Kotlin First:** The entire library is now 100% Kotlin, leveraging modern language features like extension functions, null safety, and property delegates.
-   **Coroutines & Flow:** Replaced legacy `AsyncTask` and complex `Handler` logic with Kotlin Coroutines for asynchronous tile loading and image decoding. Events are now optionally exposed via `SharedFlow` for reactive integrations.
-   **AndroidX:** Built natively against AndroidX libraries, ensuring compatibility with current development standards.

## 2. Decoupled Architecture

The original implementation was characterized by a very large single class (`SubsamplingScaleImageView.java`) containing thousands of lines of code. This version has been significantly refactored into a modular architecture:

-   **TileManager:** Logic for calculating tile grids, visibility, and sample sizes is encapsulated here.
-   **AnimationBuilder:** A dedicated builder pattern for configuring and launching animations, removing animation state management from the main View class.
-   **Utility Objects:** Shared logic for EXIF handling (`ExifUtils`) and complex easing/distance math (`MathUtils`) has been extracted.
-   **Separated Decoders:** Decoder implementations are moved to a dedicated package, making it easier to provide custom Skia-based or third-party implementations.

## 3. Improved Performance & Memory Handling

While the core principles of subsampling and tiling remain the same, the implementation has been refined:

-   **Parallel Decoding:** The `SkiaPooledImageRegionDecoder` leverages Coroutines and a managed pool of decoders to provide true parallel loading of image tiles on multi-core devices, leading to faster detail rendering during rapid zooms.
-   **Smart Garbage Collection:** Improved synchronization and lifecycle-aware resource management (`bindToLifecycle`) ensure that Bitmaps are recycled promptly and memory leaks are prevented.
-   **Dynamic Thresholds:** Memory and CPU core counts are used dynamically to determine the optimal number of parallel decoders for a given device.

## 4. Enhanced Gesture & Animation Engine

-   **Refactored Touch Handling:** The complex `onTouchEvent` logic has been broken down into specific handlers for pinch, pan, and quick-scale, reducing bugs and making it easier to modify gesture behavior.
-   **Fluent Animations:** The `AnimationBuilder` provides a much cleaner API for developers to trigger complex view transitions.

## 5. Developer Experience

-   **Detekt Integration:** The project follows strict static analysis rules to ensure high code quality.
-   **Standardized Constants:** Internal magic numbers have been replaced with well-named constants, making the inner workings of the view much easier to understand and tune.
-   **Better Event Listeners:** Multiple listeners can now be attached to the same view without overwriting each other using `CopyOnWriteArrayList`.

## Summary

This version is not just a port; it's a modern reimagining designed to be cleaner, faster, and more maintainable while respecting the robust core logic of the original library that developers have trusted for years.
