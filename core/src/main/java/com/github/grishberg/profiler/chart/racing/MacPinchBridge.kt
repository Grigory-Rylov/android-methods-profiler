package com.github.grishberg.profiler.chart.racing

import java.awt.GraphicsEnvironment
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Pinch-зум на macOS — два канала, оба дают `scale *= (1 + magnification)`
 * (кэнон Apple), дедуплицированы «владельцем жеста» ([claim]) в окне [ECHO_WINDOW_MS]:
 *
 * 1. **JNI-монитор** (`resources/macos/pinch_bridge.m`): локальный NSEvent-монитор на
 *    NSEventTypeMagnify. Cocoa AWT не реализует magnifyWithEvent: и macOS 15 не
 *    эмулирует pinch колесом (JDK-8226225), поэтому монитор ловит жест сам. Дайлиб
 *    компилируется clang'ом на первом запуске (кэш в tmp по sha источника), монитор
 *    ставится hop-ом на main-тред AppKit. Работает без VM-флага; нет clang — канал гаснет.
 * 2. **eawt-жесты** (`com.apple.eawt.event.GestureUtilities.addGestureListenerTo`
 *    рефлексивно): канонический путь JDK — `AWTWindow_Normal.magnifyWithEvent:` →
 *    `GestureHandler.handleGestureFromNative` → слушатель глубочайшего компонента
 *    под точкой жеста. Пакет не экспортируется: нужен
 *    `--add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED` и JDK >= 17.0.2+2
 *    (JDK-8275131); без флага канал просто недоступен.
 *
 * Третий артефакт: Oracle JDK / macOS <15 дублируют pinch контрольным колесом —
 * ctrl+wheel в окне [ECHO_WINDOW_MS] после magnify глотается ([isPinchEcho]).
 */
object MacPinchBridge {

    fun interface PinchListener {
        /** [factor] — множитель масштаба (1 + magnification). Колбэк на EDT. */
        fun onPinch(factor: Double)
    }

    private const val SRC_JNI = 0
    private const val SRC_EAWT = 1

    @Volatile
    private var jniInstalled = false

    @Volatile
    private var eawtProbed = false

    @Volatile
    private var eawtAvailable = false

    @Volatile
    private var lastMagnifyMs = 0L

    @Volatile
    private var ownerSource = -1

    @Volatile
    private var lastDeliveryMs = 0L

    private val listeners = CopyOnWriteArrayList<PinchListener>()

    private var addGestureListenerTo: Method? = null
    private var gestureListenerIface: Class<*>? = null
    private var magnificationListenerIface: Class<*>? = null
    private var getMagnification: Method? = null
    private var consumeEvent: Method? = null

    val isSupported: Boolean
        get() = IS_MAC && !GraphicsEnvironment.isHeadless()

    /** true, если хоть один канал жив. */
    val isAvailable: Boolean get() = jniInstalled || eawtAvailable

    private external fun nInstall(cls: Class<*>): Boolean

    /**
     * Ставит JNI-монитор (компиляция с кэшем, идемпотентно). Вызывать вне EDT:
     * первая сборка дайлиба clang'ом занимает секунды.
     * false — канала нет (не mac, нет clang, загрузка упала); eawt не затронут.
     */
    @Synchronized
    fun install(): Boolean {
        if (jniInstalled) return true
        if (!isSupported) return false
        return try {
            System.load(buildDylib())
            jniInstalled = nInstall(MacPinchBridge.javaClass)
            if (jniInstalled) System.err.println("MacPinchBridge: JNI magnify monitor installed")
            else System.err.println("MacPinchBridge: nInstall returned false")
            jniInstalled
        } catch (t: Throwable) {
            System.err.println("MacPinchBridge: JNI канал отключен (${t.message})")
            false
        }
    }

    fun addListener(listener: PinchListener) {
        listeners += listener
    }

    /** Слушатель больше не нужен (компонент снят с дерева) — убираем, синглтон не течёт. */
    fun removeListener(listener: PinchListener) {
        listeners -= listener
    }

    /**
     * secondary-канал: вешает eawt magnify-слушателя на [component] (жест приходит
     * только когда заякорен на компонент). false — eawt недоступен (нет флага/не mac).
     */
    @Synchronized
    fun attach(component: JComponent): Boolean {
        if (!probeEawt()) return false
        return try {
            val handler = InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "magnify" -> { handleEawtMagnify(args!![0]); null }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> args!!.size == 1 && args[0] === proxy
                    "toString" -> "com.github.grishberg.profiler.chart.racing.MacPinchBridge"
                    else -> null // phase/rotate/swipe — не нужны
                }
            }
            val proxy = Proxy.newProxyInstance(
                MacPinchBridge::class.java.classLoader,
                arrayOf(gestureListenerIface, magnificationListenerIface), handler)
            addGestureListenerTo!!.invoke(null, component, proxy)
            true
        } catch (t: Throwable) {
            System.err.println("MacPinchBridge: eawt attach failed ($t)")
            false
        }
    }

    /**
     * Эхо жеста колесом: Oracle JDK и macOS <15 дублируют pinch как ctrl+wheel.
     * magnify был меньше [ECHO_WINDOW_MS] назад — ctrl+wheel игнорируем, зум уже применён.
     */
    fun isPinchEcho(nowMs: Long = System.currentTimeMillis()): Boolean =
        isAvailable && nowMs - lastMagnifyMs < ECHO_WINDOW_MS

    // --- JNI-канал ----------------------------------------------------------------

    /** Колбэк нативного монитора, тред AppKit. */
    @JvmStatic
    fun onMagnify(magnification: Double) {
        lastMagnifyMs = System.currentTimeMillis()
        val factor = (1.0 + magnification).coerceIn(FACTOR_MIN, FACTOR_MAX)
        if (factor == 1.0 || listeners.isEmpty()) return
        if (!claim(SRC_JNI, lastMagnifyMs)) return
        SwingUtilities.invokeLater { fire(factor) }
    }

    // --- eawt-канал ----------------------------------------------------------------

    private fun handleEawtMagnify(event: Any) {
        lastMagnifyMs = System.currentTimeMillis()
        val mag = getMagnification!!.invoke(event) as Double
        runCatching { consumeEvent!!.invoke(event) }
        val factor = (1.0 + mag).coerceIn(FACTOR_MIN, FACTOR_MAX)
        if (factor == 1.0) return
        if (!claim(SRC_EAWT, lastMagnifyMs)) return
        fire(factor) // handleGestureFromNative уже диспатчит на EDT
    }

    @Synchronized
    private fun probeEawt(): Boolean {
        if (eawtProbed) return eawtAvailable
        eawtProbed = true
        if (!IS_MAC) return false
        try {
            val gl = Class.forName("com.apple.eawt.event.GestureListener")
            gestureListenerIface = gl
            magnificationListenerIface = Class.forName("com.apple.eawt.event.MagnificationListener")
            addGestureListenerTo = Class.forName("com.apple.eawt.event.GestureUtilities")
                .getMethod("addGestureListenerTo", JComponent::class.java, gl)
            val me = Class.forName("com.apple.eawt.event.MagnificationEvent")
            getMagnification = me.getMethod("getMagnification")
            consumeEvent = me.superclass.getMethod("consume")
            // классы без флага тоже грузятся, но invoke упал бы IllegalAccessException —
            // проверяем право реального вызова прямо здесь
            if (!addGestureListenerTo!!.canAccess(null)) throw IllegalAccessException("com.apple.eawt.event not exported")
            eawtAvailable = true
        } catch (t: Throwable) {
            System.err.println("MacPinchBridge: eawt-канал недоступен ($t) — VM-флаг " +
                "--add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED включает его; " +
                "без него и без clang остаётся ctrl+wheel")
        }
        return eawtAvailable
    }

    // --- дедупликация каналов --------------------------------------------------------

    /**
     * Оба канала видят один жест тачпада (JNI + eawt). Первый доставивший источник
     * становится владельцем серии до паузы [ECHO_WINDOW_MS]; второй канал в этой серии
     * молчит — иначе зум удваивается.
     */
    @Synchronized
    private fun claim(source: Int, nowMs: Long): Boolean {
        if (ownerSource != source && nowMs - lastDeliveryMs < ECHO_WINDOW_MS) return false
        ownerSource = source
        lastDeliveryMs = nowMs
        return true
    }

    private fun fire(factor: Double) {
        for (l in listeners) runCatching { l.onPinch(factor) }
    }

    // --- сборка дайлиба JNI-канала -----------------------------------------------------

    private fun buildDylib(): String {
        val srcBytes = MacPinchBridge::class.java.getResourceAsStream(RESOURCE)?.readAllBytes()
            ?: error("resource $RESOURCE missing")
        val hash = MessageDigest.getInstance("SHA-256").digest(srcBytes)
            .joinToString("") { "%02x".format(it) }.substring(0, 16)
        val dir = File(System.getProperty("java.io.tmpdir"), "yamp-racing-pinch").apply { mkdirs() }
        val dylib = File(dir, "pinch-$hash.dylib")
        if (!dylib.isFile) {
            val srcFile = File(dir, "pinch-$hash.m").apply { writeBytes(srcBytes) }
            compile(srcFile, dylib)
        }
        return dylib.absolutePath
    }

    private fun compile(srcFile: File, dylib: File) {
        val jniInc = File(System.getProperty("java.home"), "include")
        val flags = buildList {
            add("-dynamiclib"); add("-x"); add("objective-c"); add("-fblocks"); add("-Os")
            add("-mmacosx-version-min=10.13")
            if (File(jniInc, "jni.h").isFile) {
                add("-I${jniInc.absolutePath}")
                add("-I${File(jniInc, "darwin").absolutePath}")
            }
            add("-framework"); add("AppKit")
        }
        val tmp = File(dylib.parentFile, "${dylib.name}.${ProcessHandle.current().pid()}.tmp")
        var lastErr = "no compiler launcher found"
        for (launcher in listOf(listOf("xcrun", "clang"), listOf("clang"), listOf("cc"))) {
            val pb = ProcessBuilder(launcher + flags + listOf(srcFile.absolutePath, "-o", tmp.absolutePath))
                .redirectErrorStream(true)
            val proc = runCatching { pb.start() }.getOrNull() ?: continue
            val output = proc.inputStream.bufferedReader().readText()
            if (proc.waitFor() == 0 && tmp.isFile) {
                Files.move(tmp.toPath(), dylib.toPath(), StandardCopyOption.ATOMIC_MOVE)
                return
            }
            tmp.delete()
            lastErr = output.ifBlank { launcher.joinToString(" ") }
        }
        error("clang compile failed: $lastErr")
    }

    private val IS_MAC = System.getProperty("os.name", "").contains("Mac")

    private const val RESOURCE = "/macos/pinch_bridge.m"
    private const val ECHO_WINDOW_MS = 250L
    private const val FACTOR_MIN = 0.5
    private const val FACTOR_MAX = 2.0
}
