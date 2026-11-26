use localmind_core::{
    IndexDocument, IndexPage, LocalMindEngine, SearchRequest, TextBlock, TextSource,
};

fn document(id: &str, name: &str, text: &str) -> IndexDocument {
    IndexDocument {
        document_id: id.into(),
        display_name: name.into(),
        pages: vec![IndexPage {
            page_index: 4,
            blocks: vec![
                TextBlock {
                    text: "Rent is due monthly.".into(),
                    source: TextSource::Embedded,
                },
                TextBlock {
                    text: text.into(),
                    source: TextSource::Ocr,
                },
            ],
        }],
    }
}

fn request(query: &str) -> SearchRequest {
    SearchRequest {
        query: query.into(),
        limit: 10,
    }
}

#[test]
fn finds_a_handwritten_phrase_on_the_correct_page_and_the_file_by_name() {
    let dir = tempfile::tempdir().unwrap();
    let engine = LocalMindEngine::new(dir.path().to_str().unwrap().into(), None).unwrap();
    engine
        .index(document("lease", "Lease.pdf", "Ask about parking"))
        .unwrap();

    let hits = engine.search(request("\"about parking\"")).unwrap();
    assert_eq!(hits.len(), 1);
    assert_eq!(hits[0].document_id, "lease");
    assert_eq!(hits[0].page_index, Some(4));
    assert!(hits[0].snippet.contains("parking"));

    let names = engine.search(request("Lease.pdf")).unwrap();
    assert_eq!(names.len(), 1);
    assert_eq!(names[0].page_index, None);
}

#[test]
fn replacement_and_deletion_survive_reopening() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().to_str().unwrap().to_string();
    let engine = LocalMindEngine::new(path.clone(), None).unwrap();
    engine
        .index(document("lease", "Old.pdf", "garage"))
        .unwrap();
    engine
        .index(document("lease", "New.pdf", "driveway"))
        .unwrap();
    assert!(engine.search(request("garage")).unwrap().is_empty());
    assert!(engine.search(request("Old.pdf")).unwrap().is_empty());
    drop(engine);

    let engine = LocalMindEngine::new(path.clone(), None).unwrap();
    assert_eq!(
        engine.search(request("driveway")).unwrap()[0].display_name,
        "New.pdf"
    );
    engine.delete("lease".into()).unwrap();
    engine.delete("lease".into()).unwrap();
    drop(engine);

    let engine = LocalMindEngine::new(path, None).unwrap();
    assert!(engine.search(request("driveway")).unwrap().is_empty());
}

#[test]
fn invalid_replacement_leaves_the_current_document_searchable() {
    let dir = tempfile::tempdir().unwrap();
    let engine = LocalMindEngine::new(dir.path().to_str().unwrap().into(), None).unwrap();
    engine
        .index(document("lease", "Lease.pdf", "garage"))
        .unwrap();
    let mut replacement = document("lease", "Lease.pdf", "driveway");
    replacement.pages.push(IndexPage {
        page_index: 4,
        blocks: vec![],
    });
    assert!(engine.index(replacement).is_err());
    assert_eq!(engine.search(request("garage")).unwrap().len(), 1);
    assert!(engine.search(request("driveway")).unwrap().is_empty());
}

#[test]
fn keeps_added_ocr_text_without_repeating_embedded_text() {
    let dir = tempfile::tempdir().unwrap();
    let engine = LocalMindEngine::new(dir.path().to_str().unwrap().into(), None).unwrap();
    engine
        .index(document(
            "lease",
            "Lease.pdf",
            "rent is due monthly.\nAsk about parking",
        ))
        .unwrap();

    let hits = engine.search(request("parking")).unwrap();
    assert!(hits[0].snippet.contains("Rent is due monthly."));
    assert!(hits[0].snippet.contains("Ask about parking"));
    assert_eq!(
        hits[0]
            .snippet
            .to_lowercase()
            .matches("rent is due monthly")
            .count(),
        1
    );
}

#[test]
fn rejects_an_unreadable_model_file() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("missing.gguf").to_str().unwrap().into();
    assert!(LocalMindEngine::new(dir.path().to_str().unwrap().into(), Some(path)).is_err());
}

#[test]
fn opening_the_engine_defers_reading_model_weights_until_search() {
    let dir = tempfile::tempdir().unwrap();
    let model = dir.path().join("invalid.gguf");
    std::fs::write(&model, b"invalid model").unwrap();
    let index = dir.path().join("index");
    let engine = LocalMindEngine::new(
        index.to_str().unwrap().into(),
        Some(model.to_str().unwrap().into()),
    )
    .unwrap();

    assert!(engine.search(request("")).unwrap().is_empty());
    assert!(engine.search(request("parking")).is_err());
}

#[test]
#[ignore = "requires EMBEDDINGGEMMA_MODEL to name the local EmbeddingGemma 2 GGUF"]
fn finds_and_deletes_a_semantic_match_after_reopening() {
    let model = std::env::var("EMBEDDINGGEMMA_MODEL").expect("set EMBEDDINGGEMMA_MODEL");
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().to_str().unwrap().to_string();
    let engine = LocalMindEngine::new(path.clone(), Some(model.clone())).unwrap();
    engine
        .index(document(
            "lease",
            "Lease.pdf",
            "Parking spaces are located in the underground garage.",
        ))
        .unwrap();
    engine
        .index(document(
            "recipe",
            "Recipe.pdf",
            "The cake uses flour, eggs, and vanilla.",
        ))
        .unwrap();
    drop(engine);

    let keyword_only = LocalMindEngine::new(path.clone(), None).unwrap();
    assert!(keyword_only
        .search(request("vehicle storage"))
        .unwrap()
        .is_empty());
    drop(keyword_only);

    let engine = LocalMindEngine::new(path, Some(model)).unwrap();
    let hits = engine.search(request("vehicle storage")).unwrap();
    assert_eq!(hits[0].document_id, "lease");
    assert_eq!(hits[0].page_index, Some(4));
    engine.delete("lease".into()).unwrap();
    assert!(engine
        .search(request("vehicle storage"))
        .unwrap()
        .iter()
        .all(|hit| hit.document_id != "lease"));
}
