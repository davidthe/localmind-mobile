#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "LocalMindLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaState {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    int32_t context_size = 512;
    int32_t threads = 2;
};

void batch_set_tokens(
    llama_batch_ext * batch,
    const llama_token * tokens,
    int32_t n_tokens,
    llama_pos pos_0
) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < n_tokens; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos_0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
    }
    llama_batch_ext_set_output_logits(batch, n_tokens - 1, true);
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_dev_localmind_app_llm_LlamaNativeBridge_nativeLoadModel(
    JNIEnv * env,
    jobject /*thiz*/,
    jstring jmodel_path,
    jint context_size,
    jint threads
) {
    const char * model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    if (!model_path) {
        return 0;
    }

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!model) {
        LOGE("Failed to load GGUF model");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = std::max<int32_t>(256, context_size);
    ctx_params.n_batch = std::max<int32_t>(256, context_size);
    ctx_params.n_ubatch = ctx_params.n_batch;
    ctx_params.n_threads = std::max<int32_t>(1, threads);
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.no_perf = true;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama_context");
        llama_model_free(model);
        return 0;
    }

    auto * state = new LlamaState{
        model,
        ctx,
        static_cast<int32_t>(ctx_params.n_ctx),
        static_cast<int32_t>(ctx_params.n_threads),
    };
    LOGI("Loaded GGUF model successfully (ctx=%d, threads=%d)", state->context_size, state->threads);
    return reinterpret_cast<jlong>(state);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_dev_localmind_app_llm_LlamaNativeBridge_nativeGenerate(
    JNIEnv * env,
    jobject /*thiz*/,
    jlong handle,
    jstring jprompt,
    jint max_tokens
) {
    auto * state = reinterpret_cast<LlamaState *>(handle);
    if (!state || !state->model || !state->ctx) {
        return env->NewStringUTF("");
    }

    const char * prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt_cstr) {
        return env->NewStringUTF("");
    }
    const std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    // Clear KV cache between requests
    llama_memory_clear(llama_get_memory(state->ctx), true);

    const llama_vocab * vocab = llama_model_get_vocab(state->model);
    const int n_prompt_est = -llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        nullptr, 0, true, true
    );
    if (n_prompt_est <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt_est));
    if (llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), true, true) < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("");
    }

    const int32_t n_prompt = static_cast<int32_t>(prompt_tokens.size());
    const int32_t limit_tokens = std::max<int32_t>(1, max_tokens);
    const int32_t max_total = std::min<int32_t>(state->context_size, n_prompt + limit_tokens);

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    llama_batch_ext * batch = llama_batch_ext_init(state->ctx);
    batch_set_tokens(batch, prompt_tokens.data(), n_prompt, 0);

    std::string output;
    int32_t n_tokens = n_prompt;
    for (int32_t n_pos = 0; n_pos + n_tokens < max_total; ) {
        if (llama_process(state->ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            LOGE("llama_process failed at pos %d", n_pos);
            break;
        }
        n_pos += n_tokens;

        const llama_token new_token = llama_sampler_sample(smpl, state->ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char buf[256];
        const int n_piece = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n_piece > 0) {
            output.append(buf, static_cast<size_t>(n_piece));
        }

        batch_set_tokens(batch, &new_token, 1, n_pos);
        n_tokens = 1;
    }

    llama_batch_ext_free(batch);
    llama_sampler_free(smpl);
    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_localmind_app_llm_LlamaNativeBridge_nativeFreeModel(
    JNIEnv * /*env*/,
    jobject /*thiz*/,
    jlong handle
) {
    auto * state = reinterpret_cast<LlamaState *>(handle);
    if (!state) return;
    if (state->ctx) llama_free(state->ctx);
    if (state->model) llama_model_free(state->model);
    delete state;
}
