use std::fs;
use std::os::unix::fs::symlink;
use std::thread;
use std::time::{Duration, Instant};

use super::{EmbeddingGemma2, DIMENSIONS};

#[test]
#[ignore = "requires EMBEDDINGGEMMA_MODEL to name the local EmbeddingGemma 2 GGUF"]
fn reuses_the_model_then_unloads_and_reloads_after_idling() {
    let checkpoint = std::env::var("EMBEDDINGGEMMA_MODEL").expect("set EMBEDDINGGEMMA_MODEL");
    let checkpoint = fs::canonicalize(checkpoint).unwrap();
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("model.gguf");
    symlink(&checkpoint, &path).unwrap();
    let model = EmbeddingGemma2 {
        idle_timeout: Duration::from_secs(1),
        ..EmbeddingGemma2::open(&path).unwrap()
    };

    assert_eq!(model.query("parking").unwrap().len(), DIMENSIONS);
    fs::remove_file(&path).unwrap();
    assert_eq!(model.query("parking").unwrap().len(), DIMENSIONS);

    let deadline = Instant::now() + Duration::from_secs(5);
    while model.resident.lock().unwrap().is_some() {
        assert!(Instant::now() < deadline, "idle model stayed loaded");
        thread::sleep(Duration::from_millis(20));
    }
    assert!(model.query("parking").is_err());
    symlink(checkpoint, path).unwrap();
    assert_eq!(model.query("parking").unwrap().len(), DIMENSIONS);
}
