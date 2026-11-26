use std::fs;
use std::path::Path;

use tantivy::collector::{DocSetCollector, TopDocs};
use tantivy::directory::MmapDirectory;
use tantivy::indexer::UserOperation;
use tantivy::query::{QueryParser, TermQuery};
use tantivy::schema::{
    Field, IndexRecordOption, Schema, TantivyDocument, Value, INDEXED, STORED, STRING, TEXT,
};
use tantivy::snippet::SnippetGenerator;
use tantivy::{DocAddress, Index, IndexReader, IndexWriter, ReloadPolicy, Searcher, Term};

use crate::{embedding::DIMENSIONS, engine_error, CoreError, SearchHit};

const SNIPPET_LENGTH: usize = 200;

pub(super) struct IndexedPage {
    pub page_index: u32,
    pub text: String,
    pub embedding: Option<Vec<f32>>,
}

pub(super) struct FullTextIndex {
    fields: Fields,
    parser: QueryParser,
    writer: Option<IndexWriter>,
    reader: IndexReader,
    needs_reload: bool,
}

pub(super) struct SearchSnapshot<'a> {
    fields: &'a Fields,
    parser: &'a QueryParser,
    searcher: Searcher,
}

struct Fields {
    document_id: Field,
    display_name: Field,
    filename: Field,
    page_index: Field,
    text: Field,
    embedding: Field,
    has_embedding: Field,
}

impl FullTextIndex {
    pub(super) fn open(path: &Path) -> Result<Self, CoreError> {
        fs::create_dir_all(path).map_err(engine_error)?;

        let mut schema = Schema::builder();
        let fields = Fields {
            document_id: schema.add_text_field("document_id", STRING | STORED),
            display_name: schema.add_text_field("display_name", STORED),
            filename: schema.add_text_field("filename", TEXT),
            page_index: schema.add_u64_field("page_index", STORED),
            text: schema.add_text_field("text", TEXT | STORED),
            embedding: schema.add_bytes_field("embeddinggemma2", STORED),
            has_embedding: schema.add_bool_field("has_embedding", INDEXED),
        };
        let directory = MmapDirectory::open(path).map_err(engine_error)?;
        let index = Index::open_or_create(directory, schema.build()).map_err(engine_error)?;
        let writer = index
            .writer_with_num_threads(1, 20_000_000)
            .map_err(engine_error)?;
        let reader = index
            .reader_builder()
            .reload_policy(ReloadPolicy::Manual)
            .try_into()
            .map_err(engine_error)?;
        let mut parser = QueryParser::for_index(&index, vec![fields.filename, fields.text]);
        parser.set_field_boost(fields.filename, 2.0);
        Ok(Self {
            fields,
            parser,
            writer: Some(writer),
            reader,
            needs_reload: false,
        })
    }

    pub(super) fn replace(
        &mut self,
        document_id: &str,
        display_name: &str,
        pages: Vec<IndexedPage>,
    ) -> Result<(), CoreError> {
        let fields = &self.fields;
        let id = Term::from_field_text(fields.document_id, document_id);
        let mut filename = fields.record(document_id, display_name);
        filename.add_text(fields.filename, display_name);
        let mut operations = vec![UserOperation::Delete(id), UserOperation::Add(filename)];
        for page in pages {
            let mut record = fields.record(document_id, display_name);
            record.add_u64(fields.page_index, u64::from(page.page_index));
            if let Some(vector) = page.embedding {
                let bytes: Vec<u8> = vector.into_iter().flat_map(f32::to_le_bytes).collect();
                record.add_bytes(fields.embedding, &bytes);
                record.add_bool(fields.has_embedding, true);
            }
            record.add_text(fields.text, page.text);
            operations.push(UserOperation::Add(record));
        }
        self.commit(operations)
    }

    pub(super) fn delete(&mut self, document_id: &str) -> Result<(), CoreError> {
        let id = Term::from_field_text(self.fields.document_id, document_id);
        self.commit(vec![UserOperation::Delete(id)])
    }

    pub(super) fn snapshot(&mut self) -> Result<SearchSnapshot<'_>, CoreError> {
        if self.needs_reload {
            self.reader.reload().map_err(engine_error)?;
            self.needs_reload = false;
        }
        Ok(SearchSnapshot {
            fields: &self.fields,
            parser: &self.parser,
            searcher: self.reader.searcher(),
        })
    }

    fn commit(&mut self, operations: Vec<UserOperation>) -> Result<(), CoreError> {
        let writer = self
            .writer
            .as_mut()
            .ok_or_else(|| engine_error("Index writer is unavailable; reopen the index"))?;
        if let Err(error) = writer.run(operations).and_then(|_| writer.commit()) {
            if let Err(rollback_error) = writer.rollback() {
                self.writer = None;
                return Err(engine_error(format!(
                    "{error}; rollback failed: {rollback_error}"
                )));
            }
            return Err(engine_error(error));
        }
        self.needs_reload = true;
        Ok(())
    }
}

impl SearchSnapshot<'_> {
    pub(super) fn search(
        &self,
        text: &str,
        limit: usize,
    ) -> Result<(Vec<DocAddress>, SnippetGenerator), CoreError> {
        let (query, _) = self.parser.parse_query_lenient(text);
        let addresses = self
            .searcher
            .search(&query, &TopDocs::with_limit(limit))
            .map_err(engine_error)?
            .into_iter()
            .map(|(_, address)| address)
            .collect();
        let mut snippets =
            SnippetGenerator::create(&self.searcher, query.as_ref(), self.fields.text)
                .map_err(engine_error)?;
        snippets.set_max_num_chars(SNIPPET_LENGTH);
        Ok((addresses, snippets))
    }

    pub(super) fn vector_addresses(&self) -> Result<Vec<DocAddress>, CoreError> {
        let query = TermQuery::new(
            Term::from_field_bool(self.fields.has_embedding, true),
            IndexRecordOption::Basic,
        );
        Ok(self
            .searcher
            .search(&query, &DocSetCollector)
            .map_err(engine_error)?
            .into_iter()
            .collect())
    }

    pub(super) fn vector(&self, address: DocAddress) -> Result<Vec<f32>, CoreError> {
        let record: TantivyDocument = self.searcher.doc(address).map_err(engine_error)?;
        let bytes = record
            .get_first(self.fields.embedding)
            .and_then(|value| value.as_bytes())
            .filter(|bytes| bytes.len() == DIMENSIONS * 4)
            .ok_or_else(|| engine_error("Invalid stored embedding"))?;
        Ok(bytes
            .chunks_exact(4)
            .map(|bytes| f32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
            .collect())
    }

    pub(super) fn hit(
        &self,
        address: DocAddress,
        snippets: &SnippetGenerator,
    ) -> Result<SearchHit, CoreError> {
        let record: TantivyDocument = self.searcher.doc(address).map_err(engine_error)?;
        let document_id = record
            .get_first(self.fields.document_id)
            .and_then(|value| value.as_str())
            .ok_or_else(|| engine_error("Search record has no document ID"))?;
        let display_name = record
            .get_first(self.fields.display_name)
            .and_then(|value| value.as_str())
            .ok_or_else(|| engine_error("Search record has no display name"))?;
        let page_index = record
            .get_first(self.fields.page_index)
            .and_then(|value| value.as_u64())
            .map(|page| page as u32);
        let text = record
            .get_first(self.fields.text)
            .and_then(|value| value.as_str())
            .unwrap_or(display_name);
        let excerpt = snippets.snippet(text);
        let snippet = if excerpt.fragment().is_empty() {
            text.chars().take(SNIPPET_LENGTH).collect()
        } else {
            excerpt.fragment().to_owned()
        };
        Ok(SearchHit {
            document_id: document_id.into(),
            display_name: display_name.into(),
            page_index,
            snippet,
        })
    }
}

impl Fields {
    fn record(&self, id: &str, name: &str) -> TantivyDocument {
        let mut record = TantivyDocument::default();
        record.add_text(self.document_id, id);
        record.add_text(self.display_name, name);
        record
    }
}
