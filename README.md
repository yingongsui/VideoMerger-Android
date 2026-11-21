# VideoMerger

An Android application for losslessly merging multiple videos with the same resolution and codec using FFmpeg (`-f concat -c copy`).

## Features

- **Lossless & Fast Merging**: Concatenates videos directly without re-encoding via FFmpeg (`-c copy`).
- **Multi-Video Selection**: Pick one or multiple video files at once using the system document picker.
- **Metadata Inspection**: Automatically probes resolution, duration, frame count, and video codec via `FFprobe`.
- **Easy Reordering & Management**: Reorder clips up/down or remove individual items directly in the list.
- **Real-Time Progress & Cancellation**: Tracks merge progress percentage and elapsed time with support for cancelling an in-progress merge.
- **Material 3 UI**: Clean card-based interface with automatic Light/Dark mode, responsive portrait & landscape layouts, and Android 13+ themed monochrome app icon.

## Tech Stack

- **Language**: Kotlin (AGP Built-in Kotlin)
- **UI**: Material Components 3 (`Theme.Material3.DayNight.NoActionBar`), ViewBinding, `RecyclerView`, `ConstraintLayout`
- **Concurrency**: Kotlin Coroutines (`lifecycleScope`)
- **Media Engine**: [`com.mrljdx:ffmpeg-kit-full:6.1.4`](https://mvnrepository.com/artifact/com.mrljdx/ffmpeg-kit-full) (16 KB page size compatible, `arm64-v8a`)
- **Build Toolchain**:
  - Gradle `9.3.1`
  - Android Gradle Plugin `9.0.1`
  - JDK `17` – `25`
  - `minSdk 29` (Android 10) / `targetSdk 35` / `compileSdk 36`

## Project Structure

```text
app/src/main/
├── java/com/videotools/videomerger/
│   ├── MainActivity.kt        # Main UI, file picking, FFmpeg async merge & progress tracking
│   ├── VideoItem.kt           # Video metadata model
│   ├── VideoListAdapter.kt    # RecyclerView adapter with reorder/remove controls
│   └── VideoUtils.kt          # URI path resolution, FFprobe metadata parsing, formatting helpers
└── res/
    ├── layout/                # Portrait layout & video item card
    ├── layout-land/           # Landscape two-pane layout
    ├── mipmap-*/              # Adaptive & monochrome launcher icons
    └── values/ & values-night/ # Light & Dark Material 3 themes and colors
```

## Build & Run

### Prerequisites

- JDK 17, 21, or 25 (`JAVA_HOME` configured)
- Android SDK with Platform 36 & Build-Tools (`ANDROID_HOME` configured, or `sdk.dir` in `local.properties`)

### Commands

```bash
# Make Gradle wrapper executable (macOS / Linux, first time only)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Build and install directly to a connected Android device/emulator
./gradlew installDebug
```

The built APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage Notes

- **Permissions**: Requires *All files access* (`MANAGE_EXTERNAL_STORAGE`) on Android 11+ so FFmpeg can read source videos and write the merged output file directly.
- **Video Compatibility**: Because merging uses stream copy (`-c copy`), input videos should share the same codec, resolution, and frame rate for best results.
- **ABI**: Currently targets `arm64-v8a` (standard for modern Android devices and Apple Silicon Android emulators).
