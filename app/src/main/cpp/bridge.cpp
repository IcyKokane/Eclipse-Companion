#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"
#include "prompt_cache_policy.h"

namespace {
std::mutex gate;
std::atomic<bool> stop_requested{false};
llama_model* model = nullptr;
llama_context* ctx = nullptr;
bool initialized = false;
constexpr int kContext = 4096;
// Cache is process-local, encrypted-file independent, and always invalidated on unload.
std::vector<llama_token> cached_prompt_tokens;
std::string last_reply_metrics;

std::string to_string(JNIEnv* env, jstring input) {
    if (!input) return "";
    // Use Java's real UTF-8 encoder, not JNI's modified UTF-8 (important for emoji).
    jclass clazz = env->FindClass("java/lang/String");
    jmethodID getBytes = env->GetMethodID(clazz, "getBytes", "(Ljava/lang/String;)[B");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(input, getBytes, encoding));
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(clazz);
    if (!bytes) return "";
    jsize len = env->GetArrayLength(bytes);
    std::string result(static_cast<size_t>(len), '\0');
    if (len > 0) env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte*>(&result[0]));
    env->DeleteLocalRef(bytes);
    return result;
}

jstring from_utf8(JNIEnv* env, const std::string& s) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte*>(s.data()));
    jclass clazz = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(clazz, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(clazz, ctor, bytes, encoding));
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(clazz);
    env->DeleteLocalRef(bytes);
    return result;
}
void unload() {
    cached_prompt_tokens.clear();
    last_reply_metrics.clear();
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
}
std::string format_chat(const std::vector<std::string>& roles, const std::vector<std::string>& contents) {
    std::vector<llama_chat_message> messages;
    for (size_t i = 0; i < roles.size(); ++i) messages.push_back({roles[i].c_str(), contents[i].c_str()});
    const char* tmpl = llama_model_chat_template(model, nullptr);
    if (!tmpl) return "";
    int32_t size = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
    if (size <= 0 || size > 120000) return "";
    std::vector<char> buf(size + 1);
    const auto written = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true,
                                                    buf.data(), static_cast<int32_t>(buf.size()));
    if (written <= 0 || written > size) return "";
    return std::string(buf.data(), written);
}

bool prepare(llama_batch_ext* batch, const llama_token* tokens, int32_t count, int32_t start) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < count; ++i) {
        auto idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        llama_pos pos = start + i;
        if (idx < 0 || !llama_batch_ext_set_pos(batch, idx, &pos)) return false;
    }
    return llama_batch_ext_set_output_logits(batch, count - 1, true);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_privatecompanion_eclipse_LocalModel_nativeLoad(JNIEnv* env, jobject, jstring jpath,
                                                         jint threads, jint contextSize) {
    std::lock_guard<std::mutex> guard(gate);
    stop_requested.store(false, std::memory_order_release);
    unload();
    if (!initialized) { llama_backend_init(); initialized = true; }
    const std::string path = to_string(env, jpath);
    if (path.empty()) return JNI_FALSE;
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // Conservative CPU-only baseline; GPU backend can be added later.
    model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) return JNI_FALSE;
    if (llama_model_has_encoder(model) || llama_model_is_diffusion(model)) { unload(); return JNI_FALSE; }
    auto cp = llama_context_default_params();
    cp.n_ctx = std::clamp(static_cast<int>(contextSize), 512, kContext);
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = std::clamp(static_cast<int>(threads), 1, 8);
    cp.n_threads_batch = cp.n_threads;
    ctx = llama_init_from_model(model, cp);
    if (!ctx) { unload(); return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_privatecompanion_eclipse_LocalModel_nativeReply(JNIEnv* env, jobject, jobjectArray jroles,
                                                           jobjectArray jcontents, jint maxTokens,
                                                           jfloat temperature, jobject listener) {
    std::lock_guard<std::mutex> guard(gate);
    if (!ctx || !model) return from_utf8(env, "ECLIPSE_ERROR: Model is not loaded");
    if (!jroles || !jcontents) return from_utf8(env, "ECLIPSE_ERROR: Chat messages unavailable");
    jsize n = env->GetArrayLength(jroles);
    if (n != env->GetArrayLength(jcontents) || n < 2 || n > 45)
        return from_utf8(env, "ECLIPSE_ERROR: Invalid chat history");
    // Optional UI preview: emit partial text without saving it to persistent storage.
    jmethodID onPartial = nullptr;
    if (listener) {
        jclass clazz = env->GetObjectClass(listener);
        if (!clazz) return from_utf8(env, "ECLIPSE_ERROR: Unable to open progress callback");
        onPartial = env->GetMethodID(clazz, "onPartial", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(clazz);
        if (!onPartial || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return from_utf8(env, "ECLIPSE_ERROR: Progress callback unavailable");
        }
    }
    std::vector<std::string> roles, contents;
    roles.reserve(n); contents.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        jstring r = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        jstring c = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
        roles.push_back(to_string(env, r)); contents.push_back(to_string(env, c));
        env->DeleteLocalRef(r); env->DeleteLocalRef(c);
        if (roles.back() != "system" && roles.back() != "user" && roles.back() != "assistant")
            return from_utf8(env, "ECLIPSE_ERROR: Invalid chat role");
    }
    if (roles.front() != "system" || roles.back() != "user")
        return from_utf8(env, "ECLIPSE_ERROR: Invalid conversation boundaries");
    // Elide the oldest chat turns when the encoded prompt exceeds context.
    // Never drop the system instructions or the newest user message.
    const auto* vocab = llama_model_get_vocab(model);
    const int32_t predict = std::clamp(static_cast<int>(maxTokens), 16, 512);
    std::string prompt;
    int32_t nPrompt = 0;
    for (;;) {
        prompt = format_chat(roles, contents);
        if (prompt.empty()) return from_utf8(env, "ECLIPSE_ERROR: Model chat template unsupported");
        nPrompt = -llama_tokenize(vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
                                   nullptr, 0, true, true);
        if (nPrompt > 0 && nPrompt + predict <= kContext) break;
        if (roles.size() <= 2) return from_utf8(env,
            "ECLIPSE_ERROR: System instructions or latest message exceed local context limit");
        // Where possible, drop a completed user/assistant pair together.
        size_t discard = roles.size() > 3 && roles[1] == "user" && roles[2] == "assistant" ? 2 : 1;
        roles.erase(roles.begin() + 1, roles.begin() + 1 + discard);
        contents.erase(contents.begin() + 1, contents.begin() + 1 + discard);
    }
    if (stop_requested.load(std::memory_order_acquire))
        return from_utf8(env, "ECLIPSE_ERROR: Generation cancelled");
    std::vector<llama_token> tokens(nPrompt);
    if (llama_tokenize(vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
                       tokens.data(), nPrompt, true, true) != nPrompt)
        return from_utf8(env, "ECLIPSE_ERROR: Unable to tokenize conversation");

    // Preserve only an identical, fully processed prefix from the preceding prompt.
    // Generated tokens may remain in KV memory; truncate them before processing
    // this prompt. If partial removal is unsupported (e.g. hybrid recurrent model),
    // fall back to a full prefill. This never caches private data on disk.
    const auto* mem = llama_get_memory(ctx);
    size_t reused = eclipse_cache::reusable_prefix(cached_prompt_tokens, tokens);
    if (reused > 0 &&
        (llama_memory_seq_pos_min(mem, 0) != 0 ||
         llama_memory_seq_pos_max(mem, 0) < static_cast<llama_pos>(reused - 1) ||
         !llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(reused), -1))) {
        reused = 0;
    }
    if (reused == 0) llama_memory_clear(mem, true);
    cached_prompt_tokens.clear();
    const auto prefill_started = std::chrono::steady_clock::now();
    auto* batch = llama_batch_ext_init(ctx);
    if (!batch) return from_utf8(env, "ECLIPSE_ERROR: Unable to allocate inference batch");
    auto* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) { llama_batch_ext_free(batch); return from_utf8(env, "ECLIPSE_ERROR: Unable to create sampler"); }
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.92f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::clamp(static_cast<float>(temperature), 0.1f, 1.5f)));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string answer;
    bool ok = true;
    int pos = static_cast<int>(reused);
    while (pos < nPrompt) {
        if (stop_requested.load(std::memory_order_acquire)) { ok = false; break; }
        const int chunk = std::min(512, nPrompt - pos);
        if (!prepare(batch, tokens.data() + pos, chunk, pos) ||
            llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) { ok = false; break; }
        pos += chunk;
    }
    const auto prefill_finished = std::chrono::steady_clock::now();
    int generated = 0;
    if (ok) {
        for (int i = 0; i < predict; ++i) {
            if (stop_requested.load(std::memory_order_acquire)) { ok = false; break; }
            const llama_token token = llama_sampler_sample(sampler, ctx, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            llama_sampler_accept(sampler, token);
            char piece[512];
            int count = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
            if (count < 0) { ok = false; break; }
            answer.append(piece, count);
            ++generated;
            // First token feedback, then updates every 8 tokens to avoid UI flood.
            if (listener && onPartial && (i == 0 || (i % 8) == 7)) {
                jstring partial = from_utf8(env, answer);
                if (partial) {
                    env->CallVoidMethod(listener, onPartial, partial);
                    env->DeleteLocalRef(partial);
                    // An Activity teardown must not interrupt the model engine.
                    if (env->ExceptionCheck()) env->ExceptionClear();
                }
            }
            if (i == predict - 1) break;
            if (!prepare(batch, &token, 1, pos) ||
                llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) { ok = false; break; }
            ++pos;
        }
    }
    const auto generation_finished = std::chrono::steady_clock::now();
    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    const auto prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        prefill_finished - prefill_started).count();
    const auto generation_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        generation_finished - prefill_finished).count();
    if (ok && !stop_requested.load(std::memory_order_acquire) && !answer.empty()) {
        // Keep only decoded input prompt tokens as the safe next-turn baseline.
        // Old reply tokens physically in KV are discarded by seq_rm next turn.
        cached_prompt_tokens = std::move(tokens);
        last_reply_metrics = "Prefill: " + std::to_string(reused) + " cached, " +
            std::to_string(nPrompt - static_cast<int>(reused)) + " computed (" +
            std::to_string(prefill_ms) + " ms); generated " +
            std::to_string(generated) + " tokens (" +
            std::to_string(generation_ms) + " ms)";
    } else {
        cached_prompt_tokens.clear();
        last_reply_metrics.clear();
    }
    if (!ok) answer = stop_requested.load(std::memory_order_acquire)
        ? "ECLIPSE_ERROR: Generation cancelled"
        : "ECLIPSE_ERROR: Inference failed or device ran out of memory";
    if (answer.empty()) answer = "ECLIPSE_ERROR: Model produced no text";
    return from_utf8(env, answer);
}

// Only timings and token counts; never conversation contents.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_privatecompanion_eclipse_LocalModel_nativeReplyDiagnostics(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(gate);
    return from_utf8(env, last_reply_metrics);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_privatecompanion_eclipse_LocalModel_nativeUnload(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(gate);
    unload();
}

// Called directly from the UI thread. Must not acquire `gate` while inference holds it.
extern "C" JNIEXPORT void JNICALL
Java_dev_privatecompanion_eclipse_LocalModel_nativeCancel(JNIEnv*, jobject) {
    stop_requested.store(true, std::memory_order_release);
}

// Reset before the UI enqueues a new generation, not inside nativeReply; that would
// lose a Stop tap arriving while the job waits in the single-thread executor.
extern "C" JNIEXPORT void JNICALL
Java_dev_privatecompanion_eclipse_LocalModel_nativeResetCancel(JNIEnv*, jobject) {
    stop_requested.store(false, std::memory_order_release);
}
