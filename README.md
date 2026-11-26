# LocalMind Mobile (POC starter)

A privacy-first Android/iOS mobile assistant scaffold. Native UI per platform; shared Rust core via UniFFI.

## POC scope
- Native chat/search screen scaffolds
- File access is permission-scoped: Android MediaStore + Storage Access Framework; iOS Photos limited/full library and Files picker
- Rust core starter with SQLite FTS5 text search and indexing metadata
- Local inference is behind an adapter boundary (not wired to a model runtime in this starter)

## Repository layout
- `core/` shared Rust library and UniFFI scaffolding
- `apps/android/` Kotlin + Compose app
- `apps/ios/` SwiftUI app starter

## Build status / next steps
This is a starter scaffold, not yet a complete installable app. Complete UniFFI generation/bridging and choose a local inference runtime (e.g. llama.cpp) before building release APK/IPA. iOS app requires Xcode on macOS. Android requires Android SDK.

## Privacy design
No network requests are included in the app scaffold. File content access is only through explicit platform permissions/pickers. Do not index sensitive files without user consent.
