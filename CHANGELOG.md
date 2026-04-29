# Changelog

## [1.1.1] - 2026-04-29

### Fixed
- **Backward Compatibility**: Restored access to previously public fields and methods in `SubsamplingScaleImageView` that were made private in 1.1.0.
    - Fields like `sWidth`, `sHeight`, `scale`, `maxScale`, `minScale`, `panLimit`, and `orientation` are now public properties again.
    - Restored `getCenter()`, `getState()`, and other getter methods.
    - Restored `setOrientation()`, `setMaxScale()`, etc., for compatibility, while favoring the property-based API.
- **Missing Classes**: Re-added `DefaultOnImageEventListener` and `ImageDecodeException`.
- **API Signatures**: Updated `OnImageEventListener` to use `Throwable` instead of `Exception` in error callbacks to match original signatures.
- **Decoder Factories**: Re-added `Factory` classes for `SkiaImageRegionDecoder` and `SkiaSSIVImageDecoder` to support legacy configuration patterns.
- **Inheritance**: Marked `recycle()` as `open` to allow overrides in subclasses.

## [1.1.0] - 2026-04-28

### Added
- **Jetpack Compose Support**: Introduced the `:library-compose` module.
    - `SubsamplingImage` Composable for easy integration into Compose UI.
    - `SubsamplingImageState` for idiomatic state management (scale, center).
    - Optimized `ImageSource` with `equals` and `hashCode` for efficient recomposition.
- **Coil Integration**: Introduced the `:library-coil` module.
    - `CoilImageRegionDecoder` leveraging Coil's disk cache and network pipeline.
    - `setCoilImage` extension function for simplified image loading via Coil.
- **CI/CD Infrastructure**:
    - GitHub Actions workflow (`ci.yml`) for automated builds, linting (Detekt), and unit tests.
    - Screenshot testing integration using Paparazzi.
- **Sample App Redesign**: 
    - Completely rewritten in Jetpack Compose.
    - New dashboard UI for easier navigation between sample features.
    - Integration with LeakCanary for memory leak detection.

### Changed
- **Minimum SDK**: Increased `minSdk` from 21 to **23** (Android 6.0).
- **Modernized Stack**: 
    - Upgraded to **Java 21**.
    - Updated to the latest Compose Compiler plugin.
    - Switched to Gradle Version Catalog for dependency management.
- **Documentation**: 
    - Comprehensive update to `README.md` with new features and installation guides.
    - Added SDK support matrix for advanced features (Ultra HDR, AVIF, etc.).

### Fixed
- Resolved multiple IDE warnings regarding unused dependencies and incubating Gradle APIs.
- Improved memory management in the sample application.

---
[1.1.0]: https://github.com/SonAI-Team/ssiv/compare/1.0.2...1.1.0
