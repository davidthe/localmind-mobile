use std::collections::HashMap;

use tantivy::DocAddress;

pub(super) fn fuse(lexical: Vec<DocAddress>, dense: Vec<DocAddress>) -> Vec<DocAddress> {
    if lexical.is_empty() {
        return dense;
    }
    if dense.is_empty() {
        return lexical;
    }

    let rank_score = |position: usize| 1.0 / (61 + position) as f64;
    let mut scores: HashMap<_, _> = lexical
        .into_iter()
        .enumerate()
        .map(|(position, address)| (address, (rank_score(position), position)))
        .collect();
    for (position, address) in dense.into_iter().enumerate() {
        scores.entry(address).or_insert((0.0, usize::MAX)).0 += rank_score(position);
    }
    let mut ranked: Vec<_> = scores.into_iter().collect();
    ranked.sort_by(|(left_address, left), (right_address, right)| {
        right
            .0
            .total_cmp(&left.0)
            .then_with(|| left.1.cmp(&right.1))
            .then_with(|| left_address.cmp(right_address))
    });
    ranked.into_iter().map(|(address, _)| address).collect()
}

#[cfg(test)]
mod tests;
