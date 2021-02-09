//
// Created by exprosic on 06/02/21.
//


#include <jni.h>
#include <pthread.h>
#include <threads.h>
#include <malloc.h>
#include <signal.h>
#include <assert.h>
#include <stdbool.h>
#include <string.h>
#include <inttypes.h>

#include <android/log.h>

static jfieldID idThreadHandle = NULL;
//static jfieldID idThreadState = NULL;
static jmethodID idRunBody = NULL;
//static jmethodID idSetStateIfNotCancelled = NULL;

static const int MY_SIGNAL = SIGUSR1;

#ifdef TEST_HOST
#define printlog(...) \
  printlog(__VA_ARGS__)
#else
#define printlog(...) \
  __android_log_print(ANDROID_LOG_DEBUG, "Paperant", __VA_ARGS__)
#endif

#define jvm_error(env, msg) (do_jvm_error(env, msg))  // TODO: __LINE__, __FILE__, __func__

static jint do_jvm_error(JNIEnv *env, char const *msg) {
    jclass error_class = (*env)->FindClass(env, "java/lang/Error");
    return (*env)->ThrowNew(env, error_class, msg);
}

struct do_job_arg {
    JavaVM *jvm;
    JNIEnv *env;
    jobject thiz;
};

thread_local struct do_job_arg *arg = NULL;  // for signal handler

static void do_job_clean_up();
static void signal_handler(int _sig) {
    assert(arg != NULL);  // implies running
    assert(arg->env != NULL);
//    assert((*arg->env)->GetIntField(arg->env, arg->thiz, idThreadState) == THREAD_SLOW_STARTED);

    do_job_clean_up();
    printlog("signal handled: %"PRIx64"\n", (uint64_t)pthread_self());
    pthread_exit(NULL);
}

JNIEXPORT void JNICALL
Java_com_champignoom_paperant_KillableThread_00024Companion_bindFieldAccessors(JNIEnv *env, jobject thiz) {
    jclass cls = (*env)->FindClass(env, "com/champignoom/paperant/KillableThread");
    idThreadHandle = (*env)->GetFieldID(env, cls, "threadHandle", "J");
//    idThreadState = (*env)->GetFieldID(env, cls, "threadState", "I");
    idRunBody = (*env)->GetMethodID(env, cls, "runBody", "()V");
//    idSetStateIfNotCancelled = (*env)->GetMethodID(env, cls, "setStateIfNotCancelled", "(I)I");
}

static void do_register_handler() {
    struct sigaction sa;
    sa.sa_flags = 0;
    sa.sa_restorer = NULL;
    sa.sa_sigaction = NULL;
    sa.sa_handler = signal_handler;
    if (sigemptyset(&sa.sa_mask) != 0)
        jvm_error(arg->env, "sigemptyset failed");

    if (sigaction(MY_SIGNAL, &sa, NULL) != 0)
        jvm_error(arg->env, "sigaction failhandle_killed");

    sigset_t ss;
    if (sigemptyset(&ss))
        jvm_error(arg->env, "sigemptyset failed");
    if (sigaddset(&ss, MY_SIGNAL))
        jvm_error(arg->env, "sigaddset failed");
    if (pthread_sigmask(SIG_UNBLOCK, &ss, NULL))
        jvm_error(arg->env, "pthread_sigmask failed");
}

JNIEXPORT void JNICALL
Java_com_champignoom_paperant_KillableThread_00024Companion_initSignalHandler(JNIEnv *env,
                                                                              jobject thiz) {
    do_register_handler();
}

//static const int THREAD_NOT_STARTED = 0;
//static const int THREAD_SLOW_STARTED = 1;
//static const int THREAD_SLOW_FINISHED = 2;
//static const int THREAD_CANCELLED = -1;

static void do_job_init(struct do_job_arg *_arg) {
    printlog("thread %"PRIx64" init\n", (uint64_t)pthread_self());
    (*_arg->jvm)->AttachCurrentThread(_arg->jvm, &_arg->env, NULL);
    arg = _arg;
}

static void do_job_clean_up() {
    JavaVM *jvm = arg->jvm;
    JNIEnv *env = arg->env;
    jobject thiz = arg->thiz;

    sighandler_t old_handler = signal(MY_SIGNAL, SIG_DFL);
    printlog("handler when cleaning up: %p\n", (void*)old_handler);
    signal(MY_SIGNAL, old_handler);

    (*env)->DeleteGlobalRef(env, thiz);
    (*jvm)->DetachCurrentThread(jvm);
    free(arg);
    arg = NULL;
    printlog("thread %"PRIx64" cleaned up\n", (uint64_t)pthread_self());
}

static void *do_job(void *_arg) {
    do_job_init(_arg);
    (*arg->env)->CallVoidMethod(arg->env, arg->thiz, idRunBody);
    do_job_clean_up();
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_champignoom_paperant_KillableThread_start(JNIEnv *env, jobject thiz) {
    pthread_attr_t attr;
    pthread_attr_init(&attr);
//    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    struct do_job_arg *new_arg = malloc(sizeof(struct do_job_arg));
    (*env)->GetJavaVM(env, &new_arg->jvm);
    new_arg->env = NULL;
    new_arg->thiz = (*env)->NewGlobalRef(env, thiz);
    pthread_t pid;
    int result = pthread_create(&pid, &attr, do_job, new_arg);
    if (result != 0) {
        printlog("pthread_create failed: %s\n", strerror(result));
    } else {
        printlog("pthread_create succeeded\n");
    }
    (*env)->SetLongField(env, thiz, idThreadHandle, (jlong)pid);
}

JNIEXPORT void JNICALL
Java_com_champignoom_paperant_KillableThread_sendSignal(JNIEnv *env, jobject thiz) {
    pthread_t pid = (*env)->GetLongField(env, thiz, idThreadHandle);
    pthread_kill(pid, MY_SIGNAL);
    printlog("signal sent to %"PRIx64"\n", (uint64_t)pid);
}

JNIEXPORT void JNICALL
Java_com_champignoom_paperant_KillableThread_join(JNIEnv *env, jobject thiz) {
    pthread_t pid = (*env)->GetLongField(env, thiz, idThreadHandle);
    struct timespec start_time;
    struct timespec end_time;
    clock_gettime(CLOCK_REALTIME, &start_time);
    int result = pthread_join(pid, NULL);
    if (result != 0) {
        perror("JNI KillableThread join");
        printlog("error number = %d, error name = %s\n", result, strerror(result));
    } else {
        clock_gettime(CLOCK_REALTIME, &end_time);
        double duration = (end_time.tv_sec - start_time.tv_sec) +
                          (end_time.tv_nsec - start_time.tv_nsec) / 1000000000;
        printlog("KillableThread JNI: join duration = %f s\n", duration);
    }
}
