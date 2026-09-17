// OpenCode Android - MNN ASR dependency loader JNI.
//
// libsherpa-mnn-jni.so is built against "monolithic MNN" (its DT_NEEDED points
// only at libMNN.so), but this app ships the split MNN build where the
// MNN::Express::* symbols live in libMNN_Express.so. The Android linker keeps
// libraries loaded via DT_NEEDED effectively local, so the Express symbols are
// never visible to sherpa's runtime symbol resolution.
//
// This tiny standalone library fixes that by dlopening libMNN.so and
// libMNN_Express.so with RTLD_NOW | RTLD_GLOBAL inside the app's own linker
// namespace, then dlopening libsherpa-mnn-jni.so in the same namespace.
//
// The key requirement is that libMNN_Express.so is shipped with the
// DF_1_GLOBAL flag set in its DT_FLAGS_1 entry. On Android, the relocation
// symbol lookup (linker_namespaces.cpp get_global_group()) only includes the
// main executable, LD_PRELOAD libraries and libraries carrying DF_1_GLOBAL —
// a plain RTLD_GLOBAL dlopen does NOT put a library into that group, which is
// why the Express symbols were resolvable via dlsym() but not at sherpa's
// load time.
//
// The libs are resolved by bare soname (not absolute path): this app ships
// extractNativeLibs=false, so the .so files are NOT extracted to disk under
// nativeLibraryDir — only the app's classloader namespace (which knows the APK
// as a library source) can find them, exactly like System.loadLibrary does.
//
// It is deliberately an independent CMake target NOT linked against any MNN
// library, so the starburst_mnn target stays untouched.

#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <android/log.h>
#include <android/dlext.h>

// android_get_exported_namespace() is a libdl export on device but is not part
// of the public NDK headers or link stub; resolve it at runtime with dlsym.
using GetExportedNamespaceFn = android_namespace_t *(*)(const char *);

static GetExportedNamespaceFn resolveGetExportedNamespace() {
    static GetExportedNamespaceFn fn = []() -> GetExportedNamespaceFn {
        void *sym = dlsym(RTLD_DEFAULT, "android_get_exported_namespace");
        return reinterpret_cast<GetExportedNamespaceFn>(sym);
    }();
    return fn;
}

#define LOG_TAG "SherpaHelper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Loads a bare soname from the app's classloader linker namespace, falling back
// to plain dlopen() if the namespace handle is unavailable.
static void *loadFromAppNamespace(const char *lib) {
    GetExportedNamespaceFn getNs = resolveGetExportedNamespace();
    android_namespace_t *ns = getNs != nullptr ? getNs("classloader-namespace") : nullptr;
    if (ns == nullptr) {
        LOGD("classloader-namespace unavailable, plain dlopen for %s", lib);
        return dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
    }
    android_dlextinfo info = {};
    info.flags = ANDROID_DLEXT_USE_NAMESPACE;
    info.library_namespace = ns;
    void *handle = android_dlopen_ext(lib, RTLD_NOW | RTLD_GLOBAL, &info);
    if (handle == nullptr) {
        LOGD("android_dlopen_ext in classloader-namespace failed for %s: %s; "
             "falling back to plain dlopen", lib, dlerror());
        return dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
    }
    return handle;
}

// dlopens libMNN.so, libMNN_Express.so and libsherpa-mnn-jni.so in the app's
// classloader linker namespace with RTLD_NOW | RTLD_GLOBAL, in dependency
// order. libMNN_Express.so is shipped with DF_1_GLOBAL set (see file comment),
// which is what makes its MNN::Express symbols visible to sherpa's relocation
// resolution.
//
// Returns JNI_TRUE only if all three loads succeeded; false otherwise, logging
// dlerror() per failing library.
JNIEXPORT jboolean JNICALL
Java_org_hiylo_starburst_ml_MnnAsr_loadAsrDeps(JNIEnv *env, jobject /*thiz*/,
                                               jstring nativeLibDir) {
    // nativeLibDir is unused for resolution (see note above); consume the ref
    // so the caller's argument stays valid, then move on.
    const char *dir = nativeLibDir != nullptr ? env->GetStringUTFChars(nativeLibDir, nullptr) : nullptr;
    if (dir != nullptr) env->ReleaseStringUTFChars(nativeLibDir, dir);

    const char *libs[] = {"libMNN.so", "libMNN_Express.so", "libsherpa-mnn-jni.so"};
    bool ok = true;
    for (const char *lib : libs) {
        void *handle = loadFromAppNamespace(lib);
        if (handle == nullptr) {
            LOGE("dlopen(%s) failed: %s", lib, dlerror());
            ok = false;
        } else {
            LOGD("dlopen(%s) ok", lib);
        }
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
