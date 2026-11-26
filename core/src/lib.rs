mod api;
mod dense;
mod embedding;
mod full_text;
mod fusion;

pub use api::{
    CoreError, IndexDocument, IndexPage, LocalMindEngine, SearchHit, SearchRequest, TextBlock,
    TextSource,
};

uniffi::setup_scaffolding!();

fn engine_error(error: impl std::fmt::Display) -> CoreError {
    CoreError::Engine(error.to_string())
}
