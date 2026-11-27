# LocalMind iOS App (`apps/ios`)

Native iOS application scaffold built with **SwiftUI**, **`WidgetKit`**, and **`XCTest`**, mirroring the Android Google/Gemini search bar UX and VS Code-style search scope/options.

> **Platform requirement:** Compiling, previewing, and running iOS and WidgetKit targets requires **macOS** with **Xcode 15+** (iOS 17.0 deployment target).

---

## Key Components

| File | Responsibility |
| :--- | :--- |
| `LocalMind/LocalMindApp.swift` | SwiftUI `@main` entry point handling `.onOpenURL` deep links (`localmind://search?...`) from the WidgetKit extension. |
| `LocalMind/ContentView.swift` | Clean Google/Gemini-style capsule search bar, scope segmented/pill selector (`All`, `File Names`, `Inside Files`), VS Code toggles (`Aa`, `ab`, `.*`), suggestion pills, Widget Guide sheet, and response stream. |
| `LocalMind/SearchModels.swift` | `SearchScope`, `SearchOptions`, `SmallLlmResponse`, `SmallLlmResponding`, `TinyLocalLlmResponder`, `SearchLaunchRequest`, and `SearchDeepLink` URL builder/parser. |
| `LocalMind/SearchViewModel.swift` | `@MainActor` `ObservableObject` managing query state, focus trigger (`focusRequestCounter`), widget presets, and deep-link launches. |
| `LocalMindWidget/LocalMindSearchWidget.swift` | `WidgetKit` extension supporting `.systemSmall`, `.systemMedium` (with `File Names` and `Inside Files` deep-link buttons), and `.accessoryRectangular` lock-screen widgets. |
| `LocalMindTests/SearchViewModelTests.swift` | `XCTest` unit tests verifying `TinyLocalLlmResponder`, `SearchDeepLink` round-trip parsing, and `SearchViewModel` widget presets. |
| `project.yml` | [XcodeGen](https://github.com/yonaskolb/XcodeGen) definition generating `LocalMind.xcodeproj` with `LocalMind`, `LocalMindWidget`, and `LocalMindTests` targets plus the `localmind` URL scheme. |

---

## Setup & Build (macOS)

1. Install XcodeGen (if not already installed):

   ```bash
   brew install xcodegen
   ```

2. Generate the Xcode project and open it:

   ```bash
   cd apps/ios
   xcodegen generate
   open LocalMind.xcodeproj
   ```

3. Run unit tests on an iOS Simulator:

   ```bash
   xcodebuild test \
     -project LocalMind.xcodeproj \
     -scheme LocalMind \
     -destination 'platform=iOS Simulator,name=iPhone 16'
   ```

---

## Next Steps for iOS Local LLM Integration

1. Bundle `llama.cpp` as an `XCFramework` (built with Metal support on Apple Silicon / iOS devices) and implement a `LlamaCppLlmResponder: SmallLlmResponding` conforming type in `LocalMind/`.
2. Connect the shared Rust `core/` library via UniFFI (`localmind_core.xcframework`) to index user-picked files (`fileImporter`) and Photos metadata.
