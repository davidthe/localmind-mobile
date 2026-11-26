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
