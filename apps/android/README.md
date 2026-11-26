# LocalMind Android App (`apps/android`)

Native Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, an Android **`AppWidgetProvider`**, and an embedded **`llama.cpp` JNI** runtime executing **`SmolLM2-135M-Instruct` (`Q4_K_M`)** on-device.

---

## Key Components

| File | Responsibility |
| :--- | :--- |
| `app/src/main/java/dev/localmind/app/MainActivity.kt` | Google/Gemini-inspired Compose UI (`LocalMindScreen`), deep-link intent handling (`onCreate` & `onNewIntent`), model initialization, and Compose `@Preview`s (`LocalMindEmptyPreview`, `LocalMindScreenPreview`). |
| `app/src/main/java/dev/localmind/app/search/SearchModels.kt` | `SearchScope` (`ALL`, `FILE_NAMES`, `INSIDE_FILES`), `SearchOptions` (`Aa`, `ab`, `.*`), `LocalMindPromptBuilder` (ChatML prompt formatting & stop-token cleanup), `SmallLlmResponder` interface, `TinyLocalLlmResponder` fallback, and `SearchDeepLink` URI parser/builder. |
| `app/src/main/java/dev/localmind/app/search/SearchViewModel.kt` | State holder (`SearchUiState`) managing query text, VS Code toggles, async LLM execution (`isGenerating`), widget presets, and model load status (`ModelRuntimeState`). |
| `app/src/main/java/dev/localmind/app/llm/LlamaCppLlmResponder.kt` | Copies `assets/models/smollm2-135m-instruct-q4_k_m.gguf` into `context.filesDir` on first run and calls `LlamaNativeBridge` (`liblocalmind_llama.so`) on `Dispatchers.IO`. |
| `app/src/main/java/dev/localmind/app/widget/LocalMindSearchWidgetProvider.kt` | Android Home Screen widget provider wiring `PendingIntent`s for the search bar, **File Names**, and **Inside Files** pills, plus `requestPinWidget()` helper for Android 8.0+. |
| `app/src/main/cpp/CMakeLists.txt` | CMake build configuration statically linking `llama.cpp` into `liblocalmind_llama.so` for `arm64-v8a` and `x86_64`. |
| `app/src/main/cpp/localmind_llama_jni.cpp` | JNI implementation (`nativeLoadModel`, `nativeGenerate`, `nativeFreeModel`) using `llama.cpp` C API (`llama_batch_ext`, greedy sampler, KV cache clearing between queries). |
| `app/src/main/res/layout/widget_localmind_search.xml` | Pill-shaped dark widget layout matching the in-app Google/Gemini search bar. |
| `app/src/test/java/dev/localmind/app/SearchUnitTests.kt` | JUnit 4 tests for `TinyLocalLlmResponder`, `LocalMindPromptBuilder`, `SearchDeepLink`, and `SearchViewModel`. |

---

## Setup & Build

### 1. Fetch Native LLM Dependencies

Because `llama.cpp` and the `105 MB` GGUF model are excluded from Git, run the fetch script once from the repository root:

```powershell
# Windows
.\scripts\fetch-llm-deps.ps1
```

```bash
# macOS / Linux
./scripts/fetch-llm-deps.sh
```

This populates:
- `apps/android/app/src/main/cpp/llama.cpp/`
- `apps/android/app/src/main/assets/models/smollm2-135m-instruct-q4_k_m.gguf`

### 2. SDK Requirements

- JDK 17 or 21 (`JAVA_HOME`)
- Android SDK (`ANDROID_HOME`):
  - `platforms;android-35`
  - `build-tools;35.0.0`
  - `ndk;28.2.13676358`
  - `cmake;3.31.0`

### 3. Run Unit Tests & Build Debug APK

```powershell
cd apps\android
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

- APK output: `apps/android/app/build/outputs/apk/debug/app-debug.apk`
- JUnit XML report: `apps/android/app/build/test-results/testDebugUnitTest/TEST-dev.localmind.app.SearchUnitTests.xml`

### 4. Render Compose UI Previews (Headless)

Using Google Android CLI (`android.exe`):

```powershell
android preview render "?:app" `
  --fqn=dev.localmind.app.MainActivityKt.LocalMindEmptyPreview `
  --fqn=dev.localmind.app.MainActivityKt.LocalMindScreenPreview `
  --output=.\preview-out
```

### 5. Deploy to Device / Emulator

```powershell
adb install -r apps\android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n dev.localmind.app/.MainActivity
```

To test widget deep links directly via `adb`:

```powershell
adb shell am start -a android.intent.action.VIEW -d "localmind://search?scope=filenames&focus=true&fromWidget=true" dev.localmind.app
```
