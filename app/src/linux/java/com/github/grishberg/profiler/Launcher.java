package com.github.grishberg.profiler;

import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.SimpleConsoleLogger;
import com.github.grishberg.profiler.common.settings.JsonSettings;
import com.github.grishberg.profiler.common.settings.SettingsFacade;
import com.github.grishberg.profiler.ui.FramesManager;
import com.github.grishberg.profiler.ui.Main;
import com.github.grishberg.profiler.ui.StandaloneAppDialogFactory;
import com.github.grishberg.profiler.ui.StandaloneAppFramesManagerFramesManager;
import com.github.grishberg.profiler.ui.ViewFactory;

import java.io.File;

/**
 * Launcher without mac os x specific files
 */
public class Launcher {
    private static final String APP_FILES_DIR_NAME = StandaloneAppFramesManagerFramesManager.APP_FILES_DIR;
    private static final SimpleConsoleLogger log = new SimpleConsoleLogger(APP_FILES_DIR_NAME);
    private static final JsonSettings settings = new JsonSettings(APP_FILES_DIR_NAME, log);
    private static final ViewFactory sViewFactory = new StandaloneAppDialogFactory(settings);
    private static final FramesManager sFramesManager = new StandaloneAppFramesManagerFramesManager(
            settings, log, sViewFactory);

    public static void main(String[] args) {
        initDefaultSettings(settings, log);
        Main app = sFramesManager.createMainFrame(Main.StartMode.DEFAULT);

        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.isFile()) {
                app.openTraceFile(f);
            }
        }
        String osName = System.getProperty("os.name");
        log.d("Current OS: " + osName);
    }

    private static void initDefaultSettings(SettingsFacade settings, AppLogger log) {
        String androidHome = System.getenv("ANDROID_HOME");
        log.i("ANDROID_HOME = " + androidHome);
        if (androidHome != null) {
            settings.setAndroidHome(androidHome);
        }
    }
}
