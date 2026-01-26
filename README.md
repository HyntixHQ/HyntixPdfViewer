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

### Via JitPack (Recommended)

1. Add the JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. Add the dependency to your app level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.HyntixHQ:HyntixPdfViewer:1.0.3")
}
```

### Manual Installation

If you prefer to include the library as a local module:

1. Clone `KotlinPdfium` and `HyntixPdfViewer` into your libs directory.
2. In your `settings.gradle.kts`:
```kotlin
include(":KotlinPdfium")
project(":KotlinPdfium").projectDir = file("path/to/KotlinPdfium")
include(":HyntixPdfViewer")
project(":HyntixPdfViewer").projectDir = file("path/to/HyntixPdfViewer")
```
3. In your app `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":HyntixPdfViewer"))
}
```

## Usage

```kotlin
// Example usage code
val pdfView = HyntixPdfView(context)
pdfView.load(file)
```

> **Note:** For a complete sample application demonstrating how to use this library, please check [HyntixHQ/PDFManager](https://github.com/HyntixHQ/PDFManager).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute to this project.

## License

This project is licensed under the GNU Affero General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
