use std::num::NonZeroU32;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, OnceLock, Weak};
use std::thread;
use std::time::{Duration, Instant};

use llama_cpp_4::context::params::LlamaContextParams;
use llama_cpp_4::llama_backend::LlamaBackend;
use llama_cpp_4::llama_batch::LlamaBatch;
use llama_cpp_4::model::params::LlamaModelParams;
use llama_cpp_4::model::{AddBos, LlamaModel, Special};

use crate::{engine_error, CoreError};

pub(super) const DIMENSIONS: usize = 256;
const MAX_TOKENS: usize = 2048;
const MAX_INPUT_BYTES: usize = 32 * 1024;
const MODEL_DIMENSIONS: usize = 768;
const MODEL_IDLE_TIMEOUT: Duration = Duration::from_secs(30);

static BACKEND: OnceLock<Result<LlamaBackend, String>> = OnceLock::new();

pub(super) struct EmbeddingGemma2 {
    path: PathBuf,
    resident: Arc<Mutex<Option<ResidentModel>>>,
    idle_timeout: Duration,
}

struct ResidentModel {
    model: LlamaModel,
    last_used: Instant,
}

impl EmbeddingGemma2 {
    pub(super) fn open(path: &Path) -> Result<Self, CoreError> {
        check_model_file(path)?;
        Ok(Self {
            path: path.into(),
            resident: Arc::new(Mutex::new(None)),
            idle_timeout: MODEL_IDLE_TIMEOUT,
        })
    }

    pub(super) fn document(&self, title: &str, text: &str) -> Result<Vec<f32>, CoreError> {
        let title = prefix(title, 1024);
        let text = prefix(text, MAX_INPUT_BYTES);
        self.embed(format!("title: {title} | text: {text}"))
    }

    pub(super) fn query(&self, text: &str) -> Result<Vec<f32>, CoreError> {
        let text = prefix(text, MAX_INPUT_BYTES);
        self.embed(format!("task: search result | query: {text}"))
    }

    fn embed(&self, prompt: String) -> Result<Vec<f32>, CoreError> {
        let mut slot = self.resident.lock().map_err(engine_error)?;
        let resident = match slot.as_mut() {
            Some(resident) => resident,
            None => {
                let model = load_model(&self.path)?;
                let state = Arc::downgrade(&self.resident);
                let idle_timeout = self.idle_timeout;
                thread::Builder::new()
                    .name("localmind-model-idle".into())
                    .spawn(move || unload_when_idle(state, idle_timeout))
                    .map_err(engine_error)?;
                slot.insert(ResidentModel {
                    model,
                    last_used: Instant::now(),
                })
            }
        };
        let result = embed_prompt(&resident.model, prompt);
        resident.last_used = Instant::now();
        result
    }
}

fn unload_when_idle(state: Weak<Mutex<Option<ResidentModel>>>, idle_timeout: Duration) {
    let mut remaining = idle_timeout;
    loop {
        thread::sleep(remaining);
        // The timer must never keep the model alive after its engine is dropped.
        let Some(state) = state.upgrade() else { return };
        let Ok(mut slot) = state.lock() else { return };
        let Some(resident) = slot.as_ref() else {
            return;
        };
        remaining = idle_timeout.saturating_sub(resident.last_used.elapsed());
        if remaining.is_zero() {
            *slot = None;
            return;
        }
    }
}

fn load_model(path: &Path) -> Result<LlamaModel, CoreError> {
    check_model_file(path)?;
    let model = LlamaModel::load_from_file(
        backend()?,
        path,
        &LlamaModelParams::default().with_n_gpu_layers(0),
    )
    .map_err(engine_error)?;
    let architecture = model
        .meta_val_str("general.architecture", 64)
        .map_err(engine_error)?;
    if architecture != "gemma-embedding2" || model.n_embd_out() as usize != MODEL_DIMENSIONS {
        return Err(CoreError::InvalidInput(
            "Expected the EmbeddingGemma 2 GGUF".into(),
        ));
    }
    Ok(model)
}

fn check_model_file(path: &Path) -> Result<(), CoreError> {
    if !path.is_file() {
        return Err(CoreError::InvalidInput(
            "Embedding model file is missing".into(),
        ));
    }
    Ok(())
}

fn embed_prompt(model: &LlamaModel, prompt: String) -> Result<Vec<f32>, CoreError> {
    // PDF extractors may leave NUL bytes. Keep model markup in source text literal.
    let prompt = if prompt.contains('\0') {
        prompt.replace('\0', " ")
    } else {
        prompt
    };
    let mut tokens = model
        .str_to_token_with_special(&prompt, AddBos::Always, Special::Plaintext)
        .map_err(engine_error)?;
    if tokens.len() > MAX_TOKENS {
        tokens.truncate(MAX_TOKENS - 1);
        tokens.push(model.token_eos());
    }
    let token_count = tokens.len() as u32;
    let params = LlamaContextParams::default()
        .with_n_ctx(NonZeroU32::new(token_count))
        .with_n_batch(token_count)
        .with_n_ubatch(token_count)
        .with_n_threads(2)
        .with_n_threads_batch(2)
        .with_embeddings(true);
    let mut context = model
        .new_context(backend()?, params)
        .map_err(engine_error)?;
    let mut batch = LlamaBatch::new(tokens.len(), 1);
    batch.add_sequence(&tokens, 0, true).map_err(engine_error)?;
    context.decode(&mut batch).map_err(engine_error)?;

    let output = context.embeddings_seq_ith(0).map_err(engine_error)?;
    let leading = output
        .get(..DIMENSIONS)
        .ok_or_else(|| engine_error("EmbeddingGemma 2 returned an unexpected vector width"))?;
    let norm = leading
        .iter()
        .map(|&value| f64::from(value).powi(2))
        .sum::<f64>()
        .sqrt();
    if !norm.is_finite() || norm == 0.0 {
        return Err(engine_error("EmbeddingGemma 2 returned an invalid vector"));
    }
    Ok(leading
        .iter()
        .map(|&value| (f64::from(value) / norm) as f32)
        .collect())
}

fn prefix(text: &str, max_bytes: usize) -> &str {
    let mut end = text.len().min(max_bytes);
    while !text.is_char_boundary(end) {
        end -= 1;
    }
    &text[..end]
}

fn backend() -> Result<&'static LlamaBackend, CoreError> {
    BACKEND
        .get_or_init(|| {
            let mut backend = LlamaBackend::init().map_err(|error| error.to_string())?;
            backend.void_logs();
            Ok(backend)
        })
        .as_ref()
        .map_err(engine_error)
}

#[cfg(all(test, unix))]
mod tests;
