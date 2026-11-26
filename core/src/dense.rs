use tantivy::DocAddress;
use usearch::{Index, IndexOptions, MetricKind, ScalarKind};

use crate::{embedding::DIMENSIONS, engine_error, CoreError};

pub(super) struct DenseIndex {
    index: Option<Index>,
    addresses: Vec<DocAddress>,
}

impl DenseIndex {
    pub(super) fn load(
        mut addresses: Vec<DocAddress>,
        read_vector: impl Fn(DocAddress) -> Result<Vec<f32>, CoreError>,
    ) -> Result<Self, CoreError> {
        addresses.sort_unstable();
        if addresses.is_empty() {
            return Ok(Self {
                index: None,
                addresses,
            });
        }
        let index = Index::new(&IndexOptions {
            dimensions: DIMENSIONS,
            metric: MetricKind::Cos,
            quantization: ScalarKind::F32,
            ..IndexOptions::default()
        })
        .map_err(engine_error)?;
        index
            .reserve_capacity_and_threads(addresses.len(), 1)
            .map_err(engine_error)?;

        for (key, address) in addresses.iter().enumerate() {
            let vector = read_vector(*address)?;
            index.add(key as u64, &vector).map_err(engine_error)?;
        }
        Ok(Self {
            index: Some(index),
            addresses,
        })
    }

    pub(super) fn search(
        &self,
        vector: &[f32],
        limit: usize,
    ) -> Result<Vec<DocAddress>, CoreError> {
        let Some(index) = &self.index else {
            return Ok(Vec::new());
        };
        let matches = index
            .search(vector, limit.min(self.addresses.len()))
            .map_err(engine_error)?;
        Ok(matches
            .keys
            .into_iter()
            .map(|key| self.addresses[key as usize])
            .collect())
    }
}
