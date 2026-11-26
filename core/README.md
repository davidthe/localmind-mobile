# LocalMind Shared Core (`core`)

Shared Rust library (`localmind-core`) providing **SQLite FTS5** local search storage and **UniFFI** bindings for Android (Kotlin) and iOS (Swift).

---

## Exported API (`src/lib.rs`)

- **`LocalMindEngine`**:
  - `new(database_path: String) -> Result<Self, CoreError>` — opens SQLite with WAL mode and initializes `files` + `files_fts` (FTS5 virtual table indexing `display_name` and `extracted_text`).
  - `upsert_file(file: FileRecord) -> Result<(), CoreError>` — inserts or updates a file record and refreshes its FTS5 row.
  - `search_files(query: String, limit: u32) -> Result<Vec<SearchHit>, CoreError>` — runs FTS5 `MATCH` ranked search and returns highlighted snippets.
- **Search & Small-LLM Contracts**:
  - `SearchScope` (`All`, `FileNames`, `InsideFiles`)
  - `SearchOptions` (`scope`, `case_sensitive`, `whole_word`, `use_regex`)
  - `SmallLlmResponse` and `respond_with_small_llm(query, options)`

---

## Running Tests

Requires `rustup` / `cargo` and a platform C linker (needed to compile `rusqlite` with `bundled` SQLite):

```bash
cd core
cargo test
```

## UniFFI Notes

`Cargo.toml` enables `uniffi = { version = "0.29", features = ["cli"] }` and `build.rs` calls `uniffi::build()`. Binding generation scripts for Kotlin/Swift XCFramework will be added when live file indexing is wired to the mobile apps.
