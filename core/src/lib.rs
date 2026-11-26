use rusqlite::{Connection, params};
use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use thiserror::Error;

uniffi::setup_scaffolding!();

#[derive(Debug, Error)]
#[uniffi::Error]
pub enum CoreError {
    #[error("Database error: {0}")]
    Database(String),
    #[error("Invalid input: {0}")]
    InvalidInput(String),
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct FileRecord {
    pub id: String,
    pub display_name: String,
    pub mime_type: String,
    pub modified_epoch: i64,
    pub platform_uri: String,
    pub extracted_text: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct SearchHit {
    pub id: String,
    pub display_name: String,
    pub mime_type: String,
    pub platform_uri: String,
    pub snippet: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum SearchScope {
    All,
    FileNames,
    InsideFiles,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Record)]
pub struct SearchOptions {
    pub scope: SearchScope,
    pub case_sensitive: bool,
    pub whole_word: bool,
    pub use_regex: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Record)]
pub struct SmallLlmResponse {
    pub model_name: String,
    pub answer: String,
    pub search_supported: bool,
    pub query: String,
    pub scope: SearchScope,
}

/// Generates a deterministic small-LLM placeholder response explaining that
/// live VS Code-style local file search is not yet enabled in this starter build.
#[uniffi::export]
pub fn respond_with_small_llm(query: String, options: SearchOptions) -> SmallLlmResponse {
    let trimmed = query.trim().to_string();
    let scope_label = match options.scope {
        SearchScope::All => "file names and file contents",
        SearchScope::FileNames => "file names",
        SearchScope::InsideFiles => "inside files",
    };

    let answer = if trimmed.is_empty() {
        "LocalMind Small LLM (v0.1): Enter a query in the search line or tap a search widget option. Live local file searching is not yet supported.".to_string()
    } else {
        format!(
            "LocalMind Small LLM (v0.1): Searching for \"{}\" ({}) is not yet supported in this preview build. File indexing and VS Code-style matching will be connected in the next milestone.",
            trimmed, scope_label
        )
    };

    SmallLlmResponse {
        model_name: "LocalMind-TinyLLM (on-device stub)".to_string(),
        answer,
        search_supported: false,
        query: trimmed,
        scope: options.scope,
    }
}

#[derive(uniffi::Object)]
pub struct LocalMindEngine {
    db: Mutex<Connection>,
}

#[uniffi::export]
impl LocalMindEngine {
    #[uniffi::constructor]
    pub fn new(database_path: String) -> Result<Self, CoreError> {
        let db = Connection::open(database_path).map_err(db_err)?;
        db.execute_batch(
            "PRAGMA journal_mode=WAL;
             CREATE TABLE IF NOT EXISTS files (
               id TEXT PRIMARY KEY, display_name TEXT NOT NULL, mime_type TEXT NOT NULL,
               modified_epoch INTEGER NOT NULL, platform_uri TEXT NOT NULL, extracted_text TEXT NOT NULL
             );
             CREATE VIRTUAL TABLE IF NOT EXISTS files_fts USING fts5(id UNINDEXED, display_name, extracted_text);"
        ).map_err(db_err)?;
        Ok(Self { db: Mutex::new(db) })
    }

    pub fn upsert_file(&self, file: FileRecord) -> Result<(), CoreError> {
        let db = self.db.lock().map_err(|e| CoreError::Database(e.to_string()))?;
        db.execute("INSERT INTO files(id,display_name,mime_type,modified_epoch,platform_uri,extracted_text) VALUES(?1,?2,?3,?4,?5,?6) ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,mime_type=excluded.mime_type,modified_epoch=excluded.modified_epoch,platform_uri=excluded.platform_uri,extracted_text=excluded.extracted_text",
            params![file.id, file.display_name, file.mime_type, file.modified_epoch, file.platform_uri, file.extracted_text]).map_err(db_err)?;
        db.execute("DELETE FROM files_fts WHERE id=?1", params![file.id]).map_err(db_err)?;
        db.execute("INSERT INTO files_fts(id,display_name,extracted_text) VALUES(?1,?2,?3)", params![file.id, file.display_name, file.extracted_text]).map_err(db_err)?;
        Ok(())
    }

    pub fn search_files(&self, query: String, limit: u32) -> Result<Vec<SearchHit>, CoreError> {
        if query.trim().is_empty() { return Ok(vec![]); }
        let db = self.db.lock().map_err(|e| CoreError::Database(e.to_string()))?;
        let mut stmt = db.prepare("SELECT f.id,f.display_name,f.mime_type,f.platform_uri, snippet(files_fts,2,'[',']','…',12) FROM files_fts JOIN files f ON f.id=files_fts.id WHERE files_fts MATCH ?1 ORDER BY rank LIMIT ?2").map_err(db_err)?;
        let rows = stmt.query_map(params![query, limit.min(100)], |r| Ok(SearchHit {
            id: r.get(0)?, display_name: r.get(1)?, mime_type: r.get(2)?, platform_uri: r.get(3)?, snippet: r.get(4)?
        })).map_err(db_err)?;
        rows.collect::<Result<Vec<_>,_>>().map_err(db_err)
    }
}

fn db_err(e: rusqlite::Error) -> CoreError { CoreError::Database(e.to_string()) }

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn small_llm_reports_search_not_supported() {
        let resp = respond_with_small_llm(
            "MainActivity.kt".to_string(),
            SearchOptions {
                scope: SearchScope::FileNames,
                case_sensitive: false,
                whole_word: false,
                use_regex: false,
            },
        );
        assert!(!resp.search_supported);
        assert!(resp.answer.contains("not yet supported"));
        assert!(resp.answer.contains("file names"));
    }

    #[test]
    fn fts_indexes_and_finds_file() {
        let engine = LocalMindEngine::new(":memory:".to_string()).unwrap();
        engine
            .upsert_file(FileRecord {
                id: "1".to_string(),
                display_name: "notes.md".to_string(),
                mime_type: "text/markdown".to_string(),
                modified_epoch: 100,
                platform_uri: "file:///notes.md".to_string(),
                extracted_text: "LocalMind VS Code style search".to_string(),
            })
            .unwrap();
        let hits = engine.search_files("LocalMind".to_string(), 10).unwrap();
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].display_name, "notes.md");
    }
}
