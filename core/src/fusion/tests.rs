use super::fuse;
use tantivy::DocAddress;

#[test]
fn hybrid_ranking_prefers_shared_hits_and_breaks_ties_with_keyword_rank() {
    let vector_only = DocAddress::new(0, 0);
    let keyword_only = DocAddress::new(0, 1);
    let shared = DocAddress::new(0, 2);
    assert_eq!(
        fuse(vec![keyword_only, shared], vec![vector_only, shared]),
        vec![shared, keyword_only, vector_only],
    );
}
