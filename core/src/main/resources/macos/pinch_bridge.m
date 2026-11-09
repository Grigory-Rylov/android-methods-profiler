// JNI-мост: NSEventTypeMagnify (pinch на тачпаде) → колбэк magnification в JVM.
//
// Зачем: Cocoa AWT в JDK (проверено на исходниках jdk21u и trunk) НЕ реализует
// magnifyWithEvent:, а ОС-эмуляция «pinch → ctrl+scrollWheel» на macOS 15+
// отсутствует. Значит настоящий pinch в MouseWheelEvent не приезжает никогда —
// единственный путь забрать жест локально — NSEvent-монитор.
//
// Собирается clang'ом на первом запуске MacPinchBridge'ом; компилируется как
// обычный C-файл с ObjC-блоками (ARC не нужен — монолог-владение на NSApp).
#import <AppKit/AppKit.h>
#include <dispatch/dispatch.h>
#include <jni.h>

static JavaVM *g_vm = NULL;
static jclass g_callbackClass = NULL;   // глобальный ref на com.github.grishberg.profiler.chart.racing.MacPinchBridge
static jmethodID g_onMagnify = NULL;

static void reportToJvm(double magnification) {
    if (!g_vm || !g_callbackClass || !g_onMagnify || magnification == 0.0) return;
    JNIEnv *env = NULL;
    jint st = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
    if (st == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != JNI_OK) return;
    } else if (st != JNI_OK) {
        return;
    }
    (*env)->CallStaticVoidMethod(env, g_callbackClass, g_onMagnify, magnification);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionDescribe(env);
}

static id g_monitor = nil;
static BOOL g_registered = NO;

static void doRegister(void) {
    if (g_registered) return;
    NSEventMask mask = (NSEventMask)(1ULL << NSEventTypeMagnify);
    g_monitor = [NSEvent addLocalMonitorForEventsMatchingMask:mask handler:^NSEvent *(NSEvent *event) {
        reportToJvm([event magnification]);
        return nil; // поглощаем: жест не должен утекать в дефолтное поведение
    }];
    g_registered = (g_monitor != nil);
}

// second arg — jobject (instance of the Kotlin object); third — Class<MacPinchBridge>.
JNIEXPORT jboolean JNICALL Java_com_github_grishberg_profiler_chart_racing_MacPinchBridge_nInstall(JNIEnv *env, jobject self, jclass cls) {
    if (g_registered) return JNI_TRUE;
    (*env)->GetJavaVM(env, &g_vm);
    g_callbackClass = (jclass)(*env)->NewGlobalRef(env, cls);
    g_onMagnify = (*env)->GetStaticMethodID(env, cls, "onMagnify", "(D)V");
    if (!g_vm || !g_callbackClass || !g_onMagnify) return JNI_FALSE;

    // addLocalMonitorForEventsMatchingMask — API главного треда AppKit: с фонового
    // треда регистрируем синхронным hop-ом на main queue (NSApp-тред JVM main).
    // Дедлока нет: main queue обслуживается AppKit-рунлоупом, а вызывающий тред
    // main не держит.
    if ([NSThread isMainThread]) doRegister();
    else dispatch_sync(dispatch_get_main_queue(), ^{ doRegister(); });
    return g_registered ? JNI_TRUE : JNI_FALSE;
}
