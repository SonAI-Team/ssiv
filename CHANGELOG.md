# Changelog

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
