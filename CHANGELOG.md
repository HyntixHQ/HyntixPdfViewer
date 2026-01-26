# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2026-01-26

### Changed
- **License Change**: Relicensed from MIT to **GNU Affero General Public License v3.0 (AGPL-3.0)** to ensure maximum protection and community cooperation.

## [1.0.2] - 2026-01-22

### Added
- **Text-Based URL Detection**: Automatic detection of plain text URLs in PDF pages that are not explicit annotations.
- **Enhanced Link Handling**: New 3-stage fallback detection (Native -> Manual Annotation -> Text-based) for better reliability.
- **Page Text & Search**: Added `getPageText()` and `searchPage()` overloads in `PdfFile`.
- **Gesture Customization**: Added `GESTURE_THRESHOLD_DP` for tuning swipe/gesture sensitivity.

### Fixed
- **Improved URI Compatibility**: Automatically prepend `https://` to URIs missing a scheme (common in some PDF generators).

## [1.0.1] - 2026-01-21

### Fixed
- **Carousel Page Gestures**: Improved page-by-page mode with carousel-like swipe behavior - any directional swipe now smoothly transitions to next/previous page.
- **Zoom Page Change Prevention**: Fixed issue where pinch-to-zoom and double-tap zoom could inadvertently trigger page changes.
- **Page Display in Horizontal Mode**: Pages now display one at a time and centered when using horizontal scroll mode.

### Changed
- Scroll direction tracking now uses raw offset before constraints for better carousel gesture detection.
- Added `isZoomGesture` flag to properly track active zoom gestures.
- Added `resetScrollDir()` method for clearing scroll direction state.

## [1.0.0] - 2026-01-17

### Added
- Initial release of HyntixPdfViewer.
- Efficient core PDF rendering based on Pdfium.
- Support for Android 15+ 16KB page sizes.
- Multiple view modes: Vertical/Horizontal scrolling, Page Snapping, Page Fling.
- Zoom functionality: Pinch-to-zoom, Double-tap, Dynamic scaling.
- Text interaction: Long-press text selection with drag handles.
- Built-in `SearchManager` for text search.
- Navigation controls with Scroll Handle.
- Night mode support (color inversion).
- Support for various data sources: Assets, Files, URIs, Byte Arrays, Streams.
- Comprehensive callback system for loading, rendering, page changes, and errors.
- Text selection customization (colors).

### Changed
- Standardized JitPack build configuration with standalone Gradle wrapper.
- Cleaned up debug logs and legacy TODOs.
- Reduced library size through optimized resource handling.

