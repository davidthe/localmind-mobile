# LocalMind shared search core

An offline Rust library with a UniFFI API for Kotlin and Swift. The app opens files, extracts PDF text, runs OCR, and maps document IDs to file locations. The core receives text and returns file and page matches.

## API

| Call | Behavior |
| --- | --- |
| `LocalMindEngine::new(index_directory, model_path)` | Opens a Tantivy index in an app-private directory. Pass the local EmbeddingGemma 2 GGUF path for hybrid search, or omit it for keyword search. Reuse one engine instance per directory. |
| `index(document)` | Replaces a document in one commit. Send a stable ID, display name, and pages with zero-based indices and text blocks. Rust deduplicates OCR lines and generates page embeddings when the model is loaded. |
| `search(request)` | Takes a text query and a limit capped at 100. Rust embeds the query when the model is loaded. Results carry the document ID, name, optional matching page, and a plain-text snippet. A filename hit has no page. |
| `delete(document_id)` | Removes the document from search. Repeated deletes are safe. |

Use a worker thread or coroutine dispatcher for these blocking calls. Pass an empty page list to index a filename on its own.

## Search behavior

Tantivy indexes filenames and page text with its default tokenizer, which splits on non-alphanumeric characters and discards tokens longer than 40 bytes. Languages that typically omit spaces between words may need a dedicated tokenizer.

Rust runs [EmbeddingGemma 2](https://huggingface.co/google/embeddinggemma-2) through `llama.cpp` on CPU. Use the [text-only Q8_0 GGUF](https://huggingface.co/ggml-org/embeddinggemma-2-GGUF/blob/main/embeddinggemma-2-Q8_0.gguf) (310 MB). Pass its local path when opening the engine. Rust uses Google's retrieval prompts and generates normalized 256-dimensional vectors. Embedding inference uses up to 2,048 tokens per page or query. Tantivy indexes the full page.

The first embedding request loads the model. Rust reuses it across requests and releases it after 30 seconds without inference. The next embedding request loads it again.

USearch ranks pages by cosine similarity. Rust combines the vector and keyword rankings with reciprocal rank fusion. Reindex documents when enabling embeddings on an existing keyword-only library or changing the model checkpoint.

Tantivy stores the canonical text and embeddings together. USearch builds an in-memory index on the first vector query after opening or changing the index. That keeps updates and deletes consistent across both search methods. The rebuild cost grows with the number of embedded pages.

Use a fresh index directory when moving from the SQLite preview or the earlier Tantivy schema that accepted caller-supplied vectors, then reindex the source documents.

## Verify locally

Requires Rust 1.89 or later, Cargo, CMake, libclang, and a C++ toolchain.

The pinned `llama-cpp-sys-4` build currently targets Android API 28 and Armv8.7-A for ARM64. Verify the Android native build against the app's API 26 minimum and intended devices before packaging it.

```sh
cargo test --locked --manifest-path core/Cargo.toml
EMBEDDINGGEMMA_MODEL=/path/to/embeddinggemma-2-Q8_0.gguf \
  cargo test --locked --manifest-path core/Cargo.toml -- --include-ignored
```
