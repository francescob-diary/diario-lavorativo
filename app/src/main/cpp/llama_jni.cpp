// Ponte fra l'app (Kotlin) e llama.cpp.
//
// Tre operazioni: carica il modello, genera una risposta a partire da un
// testo, libera la memoria. La generazione avvisa l'app di come procede,
// cosi' sullo schermo si vede un indicatore che avanza e non sembra bloccata.

#include <jni.h>
#include <algorithm>
#include <atomic>
#include <string>
#include <vector>
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "DiarioLLM", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#endif

namespace {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    std::atomic<bool> cancel{false};
};

std::atomic<bool> g_backend_ready{false};

std::string to_string(JNIEnv * env, jstring s) {
    if (s == nullptr) return std::string();
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray out = env->NewByteArray((jsize) s.size());
    if (out != nullptr && !s.empty()) {
        env->SetByteArrayRegion(out, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    }
    return out;
}

// Il modello porta con se' il formato della conversazione. Se non si
// riconosce, si usa quello di Gemma, che e' il modello previsto.
std::string format_prompt(const llama_model * model, const std::string & user) {
    llama_chat_message msg{"user", user.c_str()};
    const char * tmpl = llama_model_chat_template(model, nullptr);
    std::vector<char> buf(user.size() * 2 + 512);
    int32_t n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
    if (n > (int32_t) buf.size()) {
        buf.resize(n + 1);
        n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
    }
    if (n <= 0) {
        return "<start_of_turn>user\n" + user + "<end_of_turn>\n<start_of_turn>model\n";
    }
    return std::string(buf.data(), n);
}

void report(JNIEnv * env, jobject listener, jmethodID method, jint phase, jint done, jint total) {
    if (listener == nullptr || method == nullptr) return;
    env->CallVoidMethod(listener, method, phase, done, total);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_it_diario_lavorativo_core_llm_LlamaBridge_nativeLoad(
        JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    if (!g_backend_ready.exchange(true)) {
        llama_backend_init();
    }
    std::string path = to_string(env, jpath);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.use_mmap = true;
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (model == nullptr) {
        LOGI("modello non caricato: %s", path.c_str());
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) n_ctx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }

    auto * engine = new Engine();
    engine->model = model;
    engine->ctx = ctx;
    engine->vocab = llama_model_get_vocab(model);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_it_diario_lavorativo_core_llm_LlamaBridge_nativeSetThreads(
        JNIEnv *, jobject, jlong handle, jint gen_threads, jint batch_threads) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) llama_set_n_threads(engine->ctx, gen_threads, batch_threads);
}

JNIEXPORT jbyteArray JNICALL
Java_it_diario_lavorativo_core_llm_LlamaBridge_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jstring jprompt, jint max_tokens,
        jobject listener) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return to_bytes(env, "");
    engine->cancel = false;

    jmethodID on_progress = nullptr;
    if (listener != nullptr) {
        jclass cls = env->GetObjectClass(listener);
        on_progress = env->GetMethodID(cls, "onProgress", "(III)V");
        if (env->ExceptionCheck()) { env->ExceptionClear(); on_progress = nullptr; }
    }

    // Ogni richiesta parte da zero: niente memoria delle precedenti.
    llama_memory_clear(llama_get_memory(engine->ctx), true);

    const std::string text = format_prompt(engine->model, to_string(env, jprompt));

    int32_t n_tokens = -llama_tokenize(engine->vocab, text.c_str(), (int32_t) text.size(),
                                       nullptr, 0, true, true);
    if (n_tokens <= 0) return to_bytes(env, "");
    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(engine->vocab, text.c_str(), (int32_t) text.size(),
                   tokens.data(), n_tokens, true, true);

    const int32_t ctx_size = (int32_t) llama_n_ctx(engine->ctx);
    if (n_tokens + max_tokens > ctx_size) {
        LOGI("testo troppo lungo: %d token", n_tokens);
        return to_bytes(env, "\x01TROPPO_LUNGO");
    }

    // Prima fase: il modello legge il testo, a blocchi.
    const int32_t step = (int32_t) llama_n_batch(engine->ctx);
    for (int32_t i = 0; i < n_tokens; i += step) {
        if (engine->cancel) return to_bytes(env, "");
        int32_t len = std::min(step, n_tokens - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, len);
        if (llama_decode(engine->ctx, batch) != 0) {
            return to_bytes(env, "\x01ERRORE_LETTURA");
        }
        report(env, listener, on_progress, 0, i + len, n_tokens);
    }

    // Seconda fase: scrive la risposta, un pezzo alla volta. Scelta sempre
    // la parola piu' probabile: per compilare dei campi serve precisione,
    // non fantasia.
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string out;
    int depth = 0;
    bool started = false;
    char piece[256];
    for (int32_t k = 0; k < max_tokens; ++k) {
        if (engine->cancel) break;
        llama_token tok = llama_sampler_sample(sampler, engine->ctx, -1);
        if (llama_vocab_is_eog(engine->vocab, tok)) break;
        int32_t n = llama_token_to_piece(engine->vocab, tok, piece, sizeof(piece), 0, false);
        if (n > 0) {
            out.append(piece, n);
            // Appena l'oggetto JSON si chiude, il lavoro e' finito:
            // inutile aspettare che il modello aggiunga commenti.
            for (int32_t c = 0; c < n; ++c) {
                if (piece[c] == '{') { depth++; started = true; }
                else if (piece[c] == '}') { depth--; }
            }
        }
        report(env, listener, on_progress, 1, k + 1, max_tokens);
        if (started && depth <= 0) break;
        llama_batch batch = llama_batch_get_one(&tok, 1);
        if (llama_decode(engine->ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    return to_bytes(env, out);
}

JNIEXPORT void JNICALL
Java_it_diario_lavorativo_core_llm_LlamaBridge_nativeCancel(JNIEnv *, jobject, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) engine->cancel = true;
}

JNIEXPORT void JNICALL
Java_it_diario_lavorativo_core_llm_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    if (engine->ctx) llama_free(engine->ctx);
    if (engine->model) llama_model_free(engine->model);
    delete engine;
}

} // extern "C"
