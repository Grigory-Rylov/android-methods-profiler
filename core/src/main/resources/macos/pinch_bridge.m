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

static BOOL reportToJvm(double magnification) {
    if (!g_vm || !g_callbackClass || !g_onMagnify || magnification == 0.0) return NO;
    JNIEnv *env = NULL;
    jint st = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
    if (st == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != JNI_OK) return NO;
    } else if (st != JNI_OK) {
        return NO;
    }
    jboolean consumed = (*env)->CallStaticBooleanMethod(env, g_callbackClass, g_onMagnify, magnification);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); return NO; }
    return consumed ? YES : NO;
}

static id g_monitor = nil;
static BOOL g_registered = NO;

static void doRegister(void) {
    if (g_registered) return;
    NSEventMask mask = (NSEventMask)(1ULL << NSEventTypeMagnify);
    id monitor = [NSEvent addLocalMonitorForEventsMatchingMask:mask handler:^NSEvent *(NSEvent *event) {
        // Если JVM не потребила жест (мост ещё не готов, слушателей нет, доставка
        // не заявлена) — отдаём событие дальше: его заберёт JBR/OS-эмуляция
        // (ctrl+wheel). Поглощение непотреблённого жеста = мёртвый пинч в AS.
        if (reportToJvm([event magnification])) return nil;
        return event;
    }];
    // MRC: addLocalMonitor возвращает autoreleased-объект; без retain он гибнет
    // при сливе autorelease-пула (в IntelliJ EDT пул сливается на каждой пачке
    // событий — монитор отваливался, standalone это не ловил).
    g_monitor = [monitor retain];
    g_registered = (g_monitor != nil);
}

// second arg — jobject (instance of the Kotlin object); third — Class<MacPinchBridge>.
JNIEXPORT jboolean JNICALL Java_com_github_grishberg_profiler_chart_racing_MacPinchBridge_nInstall(JNIEnv *env, jobject self, jclass cls) {
    if (g_registered) return JNI_TRUE;
    (*env)->GetJavaVM(env, &g_vm);
    g_callbackClass = (jclass)(*env)->NewGlobalRef(env, cls);
    g_onMagnify = (*env)->GetStaticMethodID(env, cls, "onMagnify", "(D)Z");
    if (!g_vm || !g_callbackClass || !g_onMagnify) return JNI_FALSE;

    // addLocalMonitorForEventsMatchingMask — API главного треда AppKit: с фонового
    // треда регистрируем синхронным hop-ом на main queue (NSApp-тред JVM main).
    // Дедлока нет: main queue обслуживается AppKit-рунлоупом, а вызывающий тред
    // main не держит.
    if ([NSThread isMainThread]) doRegister();
    else dispatch_sync(dispatch_get_main_queue(), ^{ doRegister(); });
    return g_registered ? JNI_TRUE : JNI_FALSE;
}
