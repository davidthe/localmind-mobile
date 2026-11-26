# LocalMind Mobile

A privacy-first, offline-first local search engine for **Android** and **iOS** inspired by **VS Code** (search **file names** like Quick Open and **inside files** like Find in Files), presented in a clean **Google / Gemini-style search bar** UI with native home-screen search widgets and an on-device small LLM.

---

## Current Status (v0.1 Milestone)

| Area | Status | Details |
| :--- | :--- | :--- |
| **Android App** (`apps/android/`) | Built & Unit-Tested | Clean Google/Gemini pill search bar UI in Jetpack Compose, scope/match toggles (`All`, `File Names`, `Inside Files`, `Aa`, `ab`, `.*`), Android Home Screen search widget, and on-device **SmolLM2-135M-Instruct** (`Q4_K_M`) inference via `llama.cpp` JNI. |
| **iOS App** (`apps/ios/`) | Scaffolded | Matching SwiftUI Google/Gemini pill search bar UI, `localmind://search` deep linking, `WidgetKit` home/lock-screen search widget, `XCTest` suite, and XcodeGen `project.yml`. Requires macOS + Xcode to build. |
| **Rust Core** (`core/`) | Search API | Tantivy full-text search and USearch vector retrieval behind UniFFI. Rust generates page and query embeddings locally with EmbeddingGemma 2. |
| **Live File Indexing** | Not Yet Connected | As requested for this milestone, submitting any search returns an on-device small-LLM answer explaining that live device file searching is not yet supported. |

---

## Repository Structure

```text
localmind-mobile/
├── README.md                        # Project overview, setup, architecture, and widget/LLM docs
├── docs/
│   └── ROADMAP.md                   # Phased roadmap from starter to full hybrid search + RAG
├── scripts/
│   ├── build-android.sh             # Convenience Android debug APK build script
│   ├── fetch-llm-deps.ps1           # Windows: downloads SmolLM2 GGUF + clones llama.cpp
│   └── fetch-llm-deps.sh            # macOS/Linux: downloads SmolLM2 GGUF + clones llama.cpp
├── core/
│   ├── README.md                    # Shared search API and usage
│   ├── Cargo.lock
│   ├── Cargo.toml
│   ├── src/                         # Tantivy, USearch, and UniFFI API
│   └── tests/                       # Search, indexing, and deletion integration tests
├── apps/
│   ├── android/
│   │   ├── README.md                # Android app, JNI, widget, and test documentation
│   │   ├── gradlew / gradlew.bat    # Gradle 8.11.1 wrapper
│   │   ├── app/build.gradle.kts     # Compose + NDK 28 + CMake 3.31 + GGUF noCompress config
│   │   └── app/src/
│   │       ├── main/cpp/            # CMakeLists.txt + localmind_llama_jni.cpp (llama.cpp JNI)
│   │       ├── main/java/.../       # MainActivity, SearchModels, SearchViewModel, LlamaCppLlmResponder, Widget
│   │       ├── main/res/            # Home screen widget layouts, drawables, XML metadata
│   │       └── test/.../            # JUnit unit tests (SearchUnitTests.kt)
│   └── ios/
│       ├── README.md                # iOS app, WidgetKit, and XcodeGen documentation
│       ├── project.yml              # XcodeGen specification (LocalMind + LocalMindWidget + LocalMindTests)
│       ├── LocalMind/               # SwiftUI app, SearchModels, SearchViewModel
│       ├── LocalMindWidget/         # WidgetKit Small, Medium & Lock Screen search widgets
│       └── LocalMindTests/          # XCTest unit tests
```

---

## Architecture Overview

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                    Home Screen Search Widget (Android / iOS)            │
│   Tap Search Bar ──▶ localmind://search?scope=all&focus=true            │
│   Tap File Names ──▶ localmind://search?scope=filenames&focus=true      │
│   Tap Inside Files ─▶ localmind://search?scope=contents&focus=true      │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ Deep Link
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│             Native UI (Jetpack Compose on Android / SwiftUI on iOS)     │
│   • Clean Google/Gemini Pill Search Bar + Circular Submit Button        │
│   • Scope Pills: All · File Names · Inside Files                        │
│   • VS Code Match Toggles: Aa (Case) · ab (Word) · .* (Regex)           │
│   • Suggestion Chips & "+ Widget" Pin/Guide Action                      │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ SearchViewModel
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     SmallLlmResponder Interface                         │
│   • Android Runtime: LlamaCppLlmResponder                               │
│       └── JNI (liblocalmind_llama.so) -> SmolLM2-135M-Instruct Q4_K_M   │
│   • JVM Tests / iOS Starter: TinyLocalLlmResponder (deterministic stub) │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Deep-Link & Widget Protocol (`localmind://search`)

Both Android (`LocalMindSearchWidgetProvider`) and iOS (`LocalMindSearchWidget`) communicate with the main app using a shared URI scheme:

| Query Parameter | Values | Default | Description |
| :--- | :--- | :--- | :--- |
| `scope` | `all`, `filenames`, `contents` | `all` | Pre-selects **All**, **File Names**, or **Inside Files** |
| `focus` | `true`, `false` | `true` | Auto-focuses the search bar input when the app opens |
| `q` | URL-encoded string | `""` | Pre-fills the search query |
| `submit` | `true`, `false` | `false` | Immediately submits the search on launch |
| `fromWidget` | `true`, `false` | `true` | Marks the launch as widget-originated and shows a status notice |

---

## Quick Start

### 1. Android

**Prerequisites:** JDK 17 or 21, Android SDK (`platforms;android-35`, `build-tools;35.0.0`, `ndk;28.2.13676358`, `cmake;3.31.0`), Git, and `curl`.

Fetch `llama.cpp` and the `SmolLM2-135M-Instruct-Q4_K_M.gguf` model (~105 MB, excluded from git via `.gitignore`):

```powershell
# Windows (PowerShell)
.\scripts\fetch-llm-deps.ps1
```

```bash
# macOS / Linux
./scripts/fetch-llm-deps.sh
```

Run unit tests and build the debug APK:

```bash
cd apps/android
./gradlew testDebugUnitTest assembleDebug
```

Install onto a connected Android device or running emulator:

```bash
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

> **Note on Android Emulator (Windows):** Hardware virtualization (**SVM Mode** on AMD / **VT-x** on Intel) must be enabled in BIOS to run AEHD/WHPX-accelerated x86_64 emulators. If BIOS virtualization is disabled, install the APK directly on a physical Android device via USB debugging.

See [apps/android/README.md](apps/android/README.md) for detailed Android, JNI, and widget instructions.

### 2. iOS

**Prerequisites:** macOS with Xcode 15+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
cd apps/ios
xcodegen generate
open LocalMind.xcodeproj
```

See [apps/ios/README.md](apps/ios/README.md) for WidgetKit, deep linking, and test instructions.

### 3. Shared Rust Core

**Prerequisites:** Rust toolchain (`rustup` / `cargo`), CMake, libclang, and a C++ toolchain.

```bash
cd core
cargo test --locked
```

See [core/README.md](core/README.md) for the shared search API.

---

## Privacy & Security

- **100% Offline-First:** No network permissions are requested in `AndroidManifest.xml`, and no telemetry or external inference API calls are made.
- **Permission-Scoped Future Indexing:** When live file search is connected (see [docs/ROADMAP.md](docs/ROADMAP.md)), file access will only use explicit Android Storage Access Framework / MediaStore permissions and iOS Files / Photos pickers.
