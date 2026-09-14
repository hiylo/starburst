// OpenCode Android - MNN on-device LLM inference JNI.
// Minimal wrapper around MNN's Llm API for the on-device next-step suggestion feature.

#include <jni.h>
#include <string>
#include <sstream>
#include <fstream>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <cstring>
#include <cerrno>

#include "llm/llm.hpp"

#define LOG_TAG "StarBurstMnn"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Llm;

// Redirects the process stderr into the log buffer so MNN's MNN_ERROR/MNN_PRINT
// output shows up in logcat for diagnostics.
static void redirectStderrToLogcat() {
    int pipefd[2];
    if (pipe(pipefd) != 0) return;
    struct LogThread {
        int fd;
        static void *run(void *arg) {
            auto *ctx = static_cast<LogThread *>(arg);
            char buf[512];
            ssize_t n;
            while ((n = read(ctx->fd, buf, sizeof(buf) - 1)) > 0) {
                buf[n] = '\0';
                __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "MNN> %s", buf);
            }
            return nullptr;
        }
    };
    auto *t = new LogThread{pipefd[0]};
    pthread_t tid;
    pthread_create(&tid, nullptr, &LogThread::run, t);
    dup2(pipefd[1], STDERR_FILENO);
}

extern "C" {

// Loads the model from the given directory (must contain config.json / llm_config.json).
JNIEXPORT jlong JNICALL
Java_org_hiylo_starburst_ml_MnnLlm_initNative(JNIEnv *env, jobject /*thiz*/, jstring configDir) {
    const char *dir = env->GetStringUTFChars(configDir, nullptr);
    std::string model_dir(dir ? dir : "");
    env->ReleaseStringUTFChars(configDir, dir);
    LOGD("initNative model_dir=%s", model_dir.c_str());
    redirectStderrToLogcat();

    // Diagnostic: verify every required file exists before asking MNN to load.
    const char *required_files[] = {
        "config.json", "llm_config.json", "llm.mnn", "llm.mnn.weight",
        "tokenizer.txt", "visual.mnn", "visual.mnn.weight",
    };
    for (const char *rf : required_files) {
        std::string p = model_dir + "/" + rf;
        struct stat st {};
        if (stat(p.c_str(), &st) == 0) {
            LOGD("file OK: %s (%lld bytes)", rf, (long long)st.st_size);
        } else {
            LOGE("file MISSING: %s (%s)", rf, strerror(errno));
        }
    }

    // Pass the config.json *file path* (not the directory) so MNN's LlmConfig
    // can derive a trailing-slash base_dir and resolve relative file names correctly.
    std::string config_path = model_dir + "/config.json";
    Llm *llm = Llm::createLLM(config_path);
    if (llm == nullptr) {
        LOGE("createLLM failed for %s", model_dir.c_str());
        return 0;
    }
    // Official LlmSession.Load() calls set_config() before load().
    // Multi-modal blocks (e.g. "mllm"/vision) come from config.json, which
    // Llm::load() does NOT read itself — it must be injected via set_config().
    {
        std::ifstream config_file(model_dir + "/config.json");
        std::ostringstream buffer;
        buffer << config_file.rdbuf();
        if (!buffer.str().empty()) {
            LOGD("set_config: %s", buffer.str().substr(0, 200).c_str());
            llm->set_config(buffer.str());
        } else {
            LOGE("config.json empty or unreadable");
        }
    }
    bool ok = llm->load();
    if (!ok) {
        LOGE("LLM load() failed for %s", model_dir.c_str());
        Llm::destroy(llm);
        return 0;
    }
    LOGD("LLM loaded");
    return reinterpret_cast<jlong>(llm);
}

// ---------------------------------------------------------------------------
// Streaming: a custom std::streambuf that forwards incremental decoded text to
// a Java callback (void onDelta(String)). Handles UTF-8 boundary splitting by
// buffering until a complete character is available.
// ---------------------------------------------------------------------------
static JavaVM *gJavaVM = nullptr;

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

class JniStreamBuf : public std::streambuf {
public:
    JniStreamBuf(JNIEnv *env, jobject callback) {
        m_env = env;
        m_callback = env->NewGlobalRef(callback);
    }
    ~JniStreamBuf() override {
        if (m_callback && m_env) m_env->DeleteGlobalRef(m_callback);
    }

protected:
    std::streambuf::int_type overflow(std::streambuf::int_type c) override {
        if (c != traits_type::eof()) {
            m_buf.push_back(static_cast<char>(c));
        }
        flushComplete();
        return c;
    }

    std::streamsize xsputn(const char *s, std::streamsize n) override {
        m_buf.append(s, static_cast<size_t>(n));
        flushComplete();
        return n;
    }

private:
    void flushComplete() {
        // Only forward complete UTF-8 sequences (at least we cut at a char start).
        size_t idx = 0;
        size_t len = m_buf.size();
        while (idx < len) {
            unsigned char c = static_cast<unsigned char>(m_buf[idx]);
            size_t charLen = (c < 0x80) ? 1 :
                             ((c & 0xE0) == 0xC0) ? 2 :
                             ((c & 0xF0) == 0xE0) ? 3 :
                             ((c & 0xF8) == 0xF0) ? 4 : 1;
            if (idx + charLen > len) return; // incomplete tail, wait for more
            idx += charLen;
        }
        if (idx == len && !m_buf.empty()) {
            send(m_buf.c_str(), len);
            m_buf.clear();
        }
    }

    void send(const char *data, size_t len) {
        if (!m_env || !m_callback) return;
        // Look up the method on the *interface* (kept un-obfuscated by ProGuard), not on
        // the anonymous implementation class (whose name gets obfuscated -> NoSuchMethodError).
        jclass cls = m_env->FindClass("org/hiylo/starburst/ml/MnnLlm$StreamingCallback");
        if (!cls) return;
        jmethodID mid = m_env->GetMethodID(cls, "onDelta", "(Ljava/lang/String;)V");
        m_env->DeleteLocalRef(cls);
        if (!mid) return;
        jstring js = m_env->NewStringUTF(std::string(data, len).c_str());
        m_env->CallVoidMethod(m_callback, mid, js);
        m_env->DeleteLocalRef(js);
    }

    JNIEnv *m_env = nullptr;
    jobject m_callback = nullptr;
    std::string m_buf;
};

// Generates a text response to `prompt`, streaming incremental output to the
// Java callback object (its onDelta(String) is invoked as tokens are decoded).
JNIEXPORT jlong JNICALL
Java_org_hiylo_starburst_ml_MnnLlm_generateStreamingNative(JNIEnv *env, jobject /*thiz*/, jlong llmPtr,
                                                         jstring prompt, jint maxTokens,
                                                         jobject callback) {
    Llm *llm = reinterpret_cast<Llm *>(llmPtr);
    if (llm == nullptr) {
        LOGE("generateStreamingNative: null llm");
        return -1;
    }
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_text(prompt_str ? prompt_str : "");
    env->ReleaseStringUTFChars(prompt, prompt_str);

    JniStreamBuf streamBuf(env, callback);
    std::ostream oss(&streamBuf);
    try {
        llm->response(prompt_text, &oss, /*end_with*/ nullptr, /*max_new_tokens*/ maxTokens);
    } catch (const std::exception &e) {
        LOGE("response threw: %s", e.what());
        return -1;
    } catch (...) {
        LOGE("response threw unknown exception");
        return -1;
    }
    return 0;
}

// Synchronously generates a text response to `prompt` and returns the full string.
JNIEXPORT jstring JNICALL
Java_org_hiylo_starburst_ml_MnnLlm_generateNative(JNIEnv *env, jobject /*thiz*/, jlong llmPtr,
                                                 jstring prompt, jint maxTokens) {
    Llm *llm = reinterpret_cast<Llm *>(llmPtr);
    if (llm == nullptr) {
        LOGE("generateNative: null llm");
        return env->NewStringUTF("");
    }
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_text(prompt_str ? prompt_str : "");
    env->ReleaseStringUTFChars(prompt, prompt_str);

    std::ostringstream oss;
    try {
        llm->response(prompt_text, &oss, /*end_with*/ nullptr, /*max_new_tokens*/ maxTokens);
    } catch (const std::exception &e) {
        LOGE("response threw: %s", e.what());
    } catch (...) {
        LOGE("response threw unknown exception");
    }
    return env->NewStringUTF(oss.str().c_str());
}

// Clears the conversation context / KV cache for the next prompt.
JNIEXPORT void JNICALL
Java_org_hiylo_starburst_ml_MnnLlm_resetNative(JNIEnv * /*env*/, jobject /*thiz*/, jlong llmPtr) {
    Llm *llm = reinterpret_cast<Llm *>(llmPtr);
    if (llm != nullptr) {
        llm->reset();
    }
}

// Destroys the LLM instance and releases native resources.
JNIEXPORT void JNICALL
Java_org_hiylo_starburst_ml_MnnLlm_releaseNative(JNIEnv * /*env*/, jobject /*thiz*/, jlong llmPtr) {
    Llm *llm = reinterpret_cast<Llm *>(llmPtr);
    if (llm != nullptr) {
        Llm::destroy(llm);
    }
}

} // extern "C"
