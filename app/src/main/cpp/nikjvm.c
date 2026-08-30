/*
 * The JVM bridge.
 *
 * Android forbids exec()ing a binary from an app's data directory, so a
 * downloaded Java runtime cannot be started as a `java` subprocess. The only
 * route is to dlopen() the pack's libjvm.so in this process and start the VM
 * through the JNI Invocation API. The on-device probe confirmed both halves of
 * that are permitted: loading a shared object from the data directory, and
 * obtaining the executable memory the JIT needs.
 *
 * This runs in the isolated `:runtime` process. A JVM created this way cannot
 * be cleanly destroyed and recreated, and a crash inside Minecraft takes the
 * whole process with it - which is exactly why it must not be the launcher's
 * own process.
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "NikLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Android's jni.h stops at JNI_VERSION_1_6, because that is what ART
 * implements. We are not talking to ART: JNI_CreateJavaVM here belongs to the
 * desktop OpenJDK inside the runtime pack, which does understand 1.8. So the
 * constant has to be defined locally rather than taken from the NDK header.
 */
#define NIK_JNI_VERSION_1_8 0x00010008

typedef jint (*CreateJavaVM)(JavaVM **, void **, void *);

static void *g_jvm_handle = NULL;
static JavaVM *g_jvm = NULL;
static JNIEnv *g_jvm_env = NULL;

/* Copies a Java string into a freshly allocated C string. */
static char *dup_java_string(JNIEnv *env, jstring value) {
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (chars == NULL) {
        return NULL;
    }
    char *copy = strdup(chars);
    (*env)->ReleaseStringUTFChars(env, value, chars);
    return copy;
}

/*
 * Loads libjvm.so and starts a VM with the given options.
 *
 * Returns NULL on success, or a message describing what went wrong. Returning
 * the reason rather than a bare boolean matters here: every failure at this
 * point is one the user has to be told about, and "the game did not start" is
 * not a diagnosis.
 */
JNIEXPORT jstring JNICALL
Java_com_niklauncher_app_runtime_JvmBridge_nativeCreateJvm(
        JNIEnv *env, jobject thiz, jstring j_libjvm_path, jobjectArray j_options) {
    (void) thiz;
    char message[512];

    if (g_jvm != NULL) {
        return (*env)->NewStringUTF(env, "A JVM already exists in this process");
    }

    char *libjvm_path = dup_java_string(env, j_libjvm_path);
    if (libjvm_path == NULL) {
        return (*env)->NewStringUTF(env, "Could not read the libjvm path");
    }

    dlerror();
    g_jvm_handle = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
    if (g_jvm_handle == NULL) {
        const char *error = dlerror();
        snprintf(message, sizeof(message), "dlopen(%s) failed: %s",
                 libjvm_path, error ? error : "unknown");
        free(libjvm_path);
        return (*env)->NewStringUTF(env, message);
    }
    free(libjvm_path);

    CreateJavaVM create = (CreateJavaVM) dlsym(g_jvm_handle, "JNI_CreateJavaVM");
    if (create == NULL) {
        const char *error = dlerror();
        snprintf(message, sizeof(message), "JNI_CreateJavaVM not found: %s",
                 error ? error : "unknown");
        dlclose(g_jvm_handle);
        g_jvm_handle = NULL;
        return (*env)->NewStringUTF(env, message);
    }

    jsize option_count = (*env)->GetArrayLength(env, j_options);
    JavaVMOption *options = calloc((size_t) option_count, sizeof(JavaVMOption));
    if (options == NULL) {
        return (*env)->NewStringUTF(env, "Out of memory building the JVM options");
    }

    for (jsize i = 0; i < option_count; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, j_options, i);
        options[i].optionString = dup_java_string(env, element);
        (*env)->DeleteLocalRef(env, element);
    }

    JavaVMInitArgs args;
    args.version = NIK_JNI_VERSION_1_8;
    args.nOptions = option_count;
    args.options = options;
    args.ignoreUnrecognized = JNI_FALSE;

    LOGI("Creating the JVM with %d options", (int) option_count);
    jint result = create(&g_jvm, (void **) &g_jvm_env, &args);

    for (jsize i = 0; i < option_count; i++) {
        free((void *) options[i].optionString);
    }
    free(options);

    if (result != JNI_OK) {
        snprintf(message, sizeof(message), "JNI_CreateJavaVM returned %d", (int) result);
        g_jvm = NULL;
        g_jvm_env = NULL;
        LOGE("%s", message);
        return (*env)->NewStringUTF(env, message);
    }

    LOGI("JVM created");
    return NULL;
}

/*
 * Calls `public static void main(String[])` on the game's entry point.
 *
 * Blocks until Minecraft exits, so the caller runs it on its own thread.
 */
JNIEXPORT jstring JNICALL
Java_com_niklauncher_app_runtime_JvmBridge_nativeInvokeMain(
        JNIEnv *env, jobject thiz, jstring j_main_class, jobjectArray j_args) {
    (void) thiz;
    char message[512];

    if (g_jvm == NULL || g_jvm_env == NULL) {
        return (*env)->NewStringUTF(env, "No JVM has been created");
    }

    char *main_class = dup_java_string(env, j_main_class);
    if (main_class == NULL) {
        return (*env)->NewStringUTF(env, "Could not read the main class name");
    }

    /* JNI wants slashes, while manifests state the class with dots. */
    for (char *c = main_class; *c != '\0'; c++) {
        if (*c == '.') {
            *c = '/';
        }
    }

    JNIEnv *vm_env = g_jvm_env;
    jclass entry = (*vm_env)->FindClass(vm_env, main_class);
    if (entry == NULL) {
        snprintf(message, sizeof(message), "Main class not found: %s", main_class);
        free(main_class);
        (*vm_env)->ExceptionClear(vm_env);
        return (*env)->NewStringUTF(env, message);
    }
    free(main_class);

    jmethodID main_method = (*vm_env)->GetStaticMethodID(
            vm_env, entry, "main", "([Ljava/lang/String;)V");
    if (main_method == NULL) {
        (*vm_env)->ExceptionClear(vm_env);
        return (*env)->NewStringUTF(env, "The main class has no main(String[]) method");
    }

    /* Rebuild the argument array inside the guest VM: the strings handed to us
     * belong to Android's runtime and are not valid objects over there. */
    jsize arg_count = (*env)->GetArrayLength(env, j_args);
    jclass string_class = (*vm_env)->FindClass(vm_env, "java/lang/String");
    jobjectArray guest_args = (*vm_env)->NewObjectArray(vm_env, arg_count, string_class, NULL);

    for (jsize i = 0; i < arg_count; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, j_args, i);
        const char *chars = (*env)->GetStringUTFChars(env, element, NULL);
        jstring guest_string = (*vm_env)->NewStringUTF(vm_env, chars ? chars : "");
        (*vm_env)->SetObjectArrayElement(vm_env, guest_args, i, guest_string);
        (*vm_env)->DeleteLocalRef(vm_env, guest_string);
        (*env)->ReleaseStringUTFChars(env, element, chars);
        (*env)->DeleteLocalRef(env, element);
    }

    LOGI("Entering Minecraft's main method");
    (*vm_env)->CallStaticVoidMethod(vm_env, entry, main_method, guest_args);

    if ((*vm_env)->ExceptionCheck(vm_env)) {
        /* Print inside the guest VM: its stack trace is the only useful record
         * of why the game died, and it must reach logcat. */
        (*vm_env)->ExceptionDescribe(vm_env);
        (*vm_env)->ExceptionClear(vm_env);
        return (*env)->NewStringUTF(env, "Minecraft exited with an uncaught exception");
    }

    LOGI("Minecraft's main method returned");
    return NULL;
}

/*
 * Sets a process environment variable.
 *
 * The JVM and the graphics translation layer read JAVA_HOME, LD_LIBRARY_PATH
 * and friends while they initialise, so they have to be in the real process
 * environment before the VM is created. Android's Java API cannot write there,
 * which is why this goes through setenv().
 */
JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_JvmBridge_nativeSetEnv(
        JNIEnv *env, jobject thiz, jstring j_name, jstring j_value) {
    (void) thiz;
    const char *name = (*env)->GetStringUTFChars(env, j_name, NULL);
    const char *value = (*env)->GetStringUTFChars(env, j_value, NULL);
    if (name != NULL && value != NULL) {
        setenv(name, value, 1);
    }
    if (name != NULL) (*env)->ReleaseStringUTFChars(env, j_name, name);
    if (value != NULL) (*env)->ReleaseStringUTFChars(env, j_value, value);
}

JNIEXPORT jboolean JNICALL
Java_com_niklauncher_app_runtime_JvmBridge_nativeIsRunning(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return g_jvm != NULL ? JNI_TRUE : JNI_FALSE;
}
