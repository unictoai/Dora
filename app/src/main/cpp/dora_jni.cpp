#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {
constexpr const char * kTag = "DoraNative";
std::once_flag backend_once;
std::atomic_bool cancel_requested{false};

void ensure_backend() {
    std::call_once(backend_once, [] { llama_backend_init(); });
}

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring string_to_jstring(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_dora_localai_engine_NativeLlamaEngine_nativeVersion(JNIEnv * env, jclass) {
    ensure_backend();
    return string_to_jstring(env, llama_version());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_dora_localai_engine_NativeLlamaEngine_nativeValidateModel(
    JNIEnv * env,
    jclass,
    jstring model_path
) {
    ensure_backend();
    const std::string path = jstring_to_string(env, model_path);
    if (path.empty()) return JNI_FALSE;

    auto params = llama_model_default_params();
    params.vocab_only = true;
    params.check_tensors = true;
    llama_model * model = llama_model_load_from_file(path.c_str(), params);
    if (model == nullptr) return JNI_FALSE;
    llama_model_free(model);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_dora_localai_engine_NativeLlamaEngine_nativeCancel(JNIEnv *, jclass) {
    cancel_requested.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_dora_localai_engine_NativeLlamaEngine_nativeGenerate(
    JNIEnv * env,
    jclass,
    jstring model_path,
    jstring prompt,
    jint max_tokens,
    jint threads
) {
    ensure_backend();
    cancel_requested.store(false);

    const std::string path = jstring_to_string(env, model_path);
    const std::string input = jstring_to_string(env, prompt);
    if (path.empty() || input.empty()) {
        return string_to_jstring(env, "Dora native error: model path and prompt are required.");
    }

    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.check_tensors = true;
    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        return string_to_jstring(env, "Dora native error: model could not be loaded.");
    }

    auto context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = threads > 0 ? threads : 4;
    context_params.n_threads_batch = context_params.n_threads;
    llama_context * context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        llama_model_free(model);
        return string_to_jstring(env, "Dora native error: context could not be created.");
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);
    int32_t token_count = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()), nullptr, 0, true, false);
    if (token_count <= 0) {
        llama_free(context);
        llama_model_free(model);
        return string_to_jstring(env, "Dora native error: prompt tokenization failed.");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(token_count));
    token_count = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()), prompt_tokens.data(), token_count, true, false);
    if (token_count <= 0) {
        llama_free(context);
        llama_model_free(model);
        return string_to_jstring(env, "Dora native error: prompt tokenization failed.");
    }
    prompt_tokens.resize(static_cast<size_t>(token_count));

    llama_batch prompt_batch = llama_batch_get_one(prompt_tokens.data(), token_count);
    if (llama_decode(context, prompt_batch) != 0) {
        llama_free(context);
        llama_model_free(model);
        return string_to_jstring(env, "Dora native error: prompt evaluation failed.");
    }

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    const int limit = max_tokens > 0 ? max_tokens : 256;
    for (int i = 0; i < limit && !cancel_requested.load(); ++i) {
        const llama_token next = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(vocab, next)) break;

        char piece[256];
        const int32_t piece_size = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (piece_size > 0) output.append(piece, static_cast<size_t>(piece_size));

        llama_batch next_batch = llama_batch_get_one(const_cast<llama_token *>(&next), 1);
        if (llama_decode(context, next_batch) != 0) break;
    }

    llama_sampler_free(sampler);
    llama_free(context);
    llama_model_free(model);

    if (cancel_requested.load()) output.append("\n\n[Generation stopped]");
    return string_to_jstring(env, output);
}
