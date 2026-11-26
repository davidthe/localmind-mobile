use std::collections::HashSet;
use std::path::Path;
use std::sync::Mutex;

use crate::dense::DenseIndex;
use crate::embedding::EmbeddingGemma2;
use crate::engine_error;
use crate::full_text::{FullTextIndex, IndexedPage};
use crate::fusion::fuse;

const MAX_RESULTS: usize = 100;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("Invalid input: {0}")]
    InvalidInput(String),
    #[error("Search engine error: {0}")]
    Engine(String),
}

#[derive(Debug, uniffi::Record)]
pub struct IndexDocument {
    pub document_id: String,
    pub display_name: String,
    pub pages: Vec<IndexPage>,
}

#[derive(Debug, uniffi::Record)]
pub struct IndexPage {
    pub page_index: u32,
    pub blocks: Vec<TextBlock>,
}

#[derive(Debug, uniffi::Record)]
pub struct TextBlock {
    pub text: String,
    pub source: TextSource,
}

#[derive(Debug, PartialEq, Eq, uniffi::Enum)]
pub enum TextSource {
    Embedded,
    Ocr,
}

#[derive(Debug, uniffi::Record)]
pub struct SearchRequest {
    pub query: String,
    pub limit: u32,
}

#[derive(Debug, uniffi::Record)]
pub struct SearchHit {
    pub document_id: String,
    pub display_name: String,
    pub page_index: Option<u32>,
    pub snippet: String,
}

#[derive(uniffi::Object)]
pub struct LocalMindEngine {
    model: Option<EmbeddingGemma2>,
    state: Mutex<SearchState>,
}

struct SearchState {
    full_text: FullTextIndex,
    dense: Option<DenseIndex>,
}

#[uniffi::export]
impl LocalMindEngine {
    #[uniffi::constructor]
    pub fn new(index_directory: String, model_path: Option<String>) -> Result<Self, CoreError> {
        if index_directory.trim().is_empty() {
            return Err(CoreError::InvalidInput(
                "Index directory is required".into(),
            ));
        }
        let model = model_path
            .as_deref()
            .map(Path::new)
            .map(EmbeddingGemma2::open)
            .transpose()?;
        let full_text = FullTextIndex::open(Path::new(&index_directory))?;

        Ok(Self {
            model,
            state: Mutex::new(SearchState {
                full_text,
                dense: None,
            }),
        })
    }

    /// Replaces the complete document in one Tantivy commit.
    pub fn index(&self, document: IndexDocument) -> Result<(), CoreError> {
        let IndexDocument {
            document_id,
            display_name,
            pages,
        } = document;
        validate_document_id(&document_id)?;
        if display_name.trim().is_empty() {
            return Err(CoreError::InvalidInput("Display name is required".into()));
        }

        let mut page_indices = HashSet::new();
        let mut indexed_pages = Vec::new();
        for page in pages {
            if !page_indices.insert(page.page_index) {
                return Err(CoreError::InvalidInput(
                    "Page indices must be unique".into(),
                ));
            }
            let text = merge_text(&page.blocks);
            if text.is_empty() {
                continue;
            }
            let embedding = self
                .model
                .as_ref()
                .map(|model| model.document(&display_name, &text))
                .transpose()?;
            indexed_pages.push(IndexedPage {
                page_index: page.page_index,
                text,
                embedding,
            });
        }

        let mut state = self.state.lock().map_err(engine_error)?;
        state
            .full_text
            .replace(&document_id, &display_name, indexed_pages)?;
        state.dense = None;
        Ok(())
    }

    /// Searches by keyword and, when configured, by semantic similarity.
    pub fn search(&self, request: SearchRequest) -> Result<Vec<SearchHit>, CoreError> {
        let limit = (request.limit as usize).min(MAX_RESULTS);
        let query = request.query.trim();
        if limit == 0 || query.is_empty() {
            return Ok(Vec::new());
        }
        let embedding = self
            .model
            .as_ref()
            .map(|model| model.query(query))
            .transpose()?;
        let candidate_limit = if embedding.is_some() {
            MAX_RESULTS
        } else {
            limit
        };

        let mut state = self.state.lock().map_err(engine_error)?;
        let SearchState { full_text, dense } = &mut *state;
        let snapshot = full_text.snapshot()?;
        let (lexical, snippets) = snapshot.search(query, candidate_limit)?;
        let semantic = match embedding {
            Some(vector) => {
                // Dense keys refer to records in this reader snapshot.
                let dense = match dense {
                    Some(index) => index,
                    None => dense
                        .insert(DenseIndex::load(snapshot.vector_addresses()?, |address| {
                            snapshot.vector(address)
                        })?),
                };
                dense.search(&vector, candidate_limit)?
            }
            None => Vec::new(),
        };
        fuse(lexical, semantic)
            .into_iter()
            .take(limit)
            .map(|address| snapshot.hit(address, &snippets))
            .collect()
    }

    /// Removes the filename, pages, and vectors for a document.
    pub fn delete(&self, document_id: String) -> Result<(), CoreError> {
        validate_document_id(&document_id)?;
        let mut state = self.state.lock().map_err(engine_error)?;
        state.full_text.delete(&document_id)?;
        state.dense = None;
        Ok(())
    }
}

fn validate_document_id(id: &str) -> Result<(), CoreError> {
    if id.trim().is_empty() || id.len() > 1024 {
        return Err(CoreError::InvalidInput(
            "Document ID must be 1–1024 bytes".into(),
        ));
    }
    Ok(())
}

fn merge_text(blocks: &[TextBlock]) -> String {
    let normalize = |line: &str| {
        line.split_whitespace()
            .collect::<Vec<_>>()
            .join(" ")
            .to_lowercase()
    };
    let embedded: HashSet<_> = blocks
        .iter()
        .filter(|block| block.source == TextSource::Embedded)
        .flat_map(|block| block.text.lines())
        .map(normalize)
        .collect();
    let mut lines = Vec::new();
    for block in blocks {
        for line in block
            .text
            .lines()
            .map(str::trim)
            .filter(|line| !line.is_empty())
        {
            if block.source == TextSource::Embedded || !embedded.contains(&normalize(line)) {
                lines.push(line);
            }
        }
    }
    lines.join("\n")
}
