# Implementation roadmap

1. Complete UniFFI bindings and generated Kotlin/Swift APIs; connect native screens to Rust.
2. Add Android file discovery: MediaStore for media; SAF document picker for user-selected documents. Handle Android version permission differences.
3. Add iOS Photos permission flow and Photos enumeration; document access through Files picker/security-scoped URLs. Explain iOS background limits.
4. Add file extractors (PDF, plain text, DOCX metadata/text, images via OCR) behind a safe, permission-aware pipeline.
5. Integrate local model runtime (llama.cpp first candidate), model download/import UI, model storage, cancellation, memory budgeting, quantized model selection.
6. Add embedding provider, vector index, hybrid lexical+vector retrieval, model/index versioning, chunking and ranking.
7. Build app chat with citations to indexed files, explicit permission for file access, deletion controls, indexing status and battery-aware scheduling.
8. Add Android home-screen widget. iOS widget should be a WidgetKit extension; due OS restrictions, widgets cannot run long interactive inference directly—use a deep link into app and supported App Intents for lightweight actions.
9. Tests, benchmarks, accessibility, privacy review, and CI builds for Android/iOS.
