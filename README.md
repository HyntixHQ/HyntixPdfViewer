# HyntixPdfViewer

A lightweight, efficient PDF viewer library for Android, built with Kotlin.

## Features

- **Efficient PDF Rendering**: Built on top of Google's Pdfium for fast and reliable rendering.
- **16KB Page Support**: Compatible with Android 15+ 16KB memory page sizes.
- **Flexible Data Sources**: Load PDFs from multiple sources:
  - Assets (`fromAsset`)
  - Files (`fromFile`)
  - URIs (`fromUri`) - supports Content Providers
  - Byte Arrays (`fromBytes`)
  - Input Streams (`fromStream`)
  - Custom Sources (`fromSource`)
- **View Modes**:
  - Vertical and Horizontal scrolling.
  - Single page snapping or continuous scrolling.
  - Page fling (momentum scrolling).
  - Night mode (color inversion).
  - Auto-spacing and customizable page spacing.
- **Zoom & Navigation**:
  - Pinch-to-zoom & Double-tap zoom with animation.
  - Dynamic scaling and "Fit to Screen" policies (Width, Height, Both).
  - Scroll handle for quick navigation.
  - Programmatic navigation (`jumpTo`, `zoomTo`).
- **Text & Content**:
  - **Text Search**: Built-in `SearchManager` for finding text.
  - **Text Selection**: Long-press to select, drag handles, customizable colors.
  - **Link Handling**: Support for URI and internal page links.
  - **Annotations**: Option to render PDF annotations.
  - **Password Protection**: Support for encrypted PDFs.
- **Callbacks & Listeners**:
  - `onLoad`, `onRender`, `onPageChange`, `onPageScroll`
  - `onTap`, `onLongPress`
  - `onError`, `onPageError`
  - `onDraw`, `onDrawAll` (for custom overlays)
  - `onTextSelected`
- **State Management**: Save and restore view state (page, zoom, scroll) across configuration changes.
- **Lightweight**: Minimal dependencies, optimized for size.

## Requirements

- Android Min SDK: 26 (Android 8.0 Oreo)
- Compile SDK: 36
- Java 21

## Installation

Add the dependency to your module's `build.gradle.kts`.

**Note:** This project depends on `KotlinPdfium`. You must clone it and add it to your project.

```bash
git clone https://github.com/HyntixHQ/KotlinPdfium.git
```

Then in your `settings.gradle.kts`, include it:
```kotlin
include(":KotlinPdfium")
project(":KotlinPdfium").projectDir = file("path/to/KotlinPdfium")
```

And finally in your module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":KotlinPdfium"))
}
```

## Usage

```kotlin
// Example usage code
val pdfView = HyntixPdfView(context)
pdfView.load(file)
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute to this project.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
