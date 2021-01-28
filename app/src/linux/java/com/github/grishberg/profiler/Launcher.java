package com.github.grishberg.profiler;

import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.SimpleConsoleLogger;
import com.github.grishberg.profiler.common.settings.JsonSettings;
import com.github.grishberg.profiler.common.settings.SettingsRepository;
import com.github.grishberg.profiler.ui.FramesManager;
import com.github.grishberg.profiler.ui.Main;

import java.io.File;

import static com.github.grishberg.profiler.ui.Main.*;

/**
 * Launcher without mac os x specific files
 */
public class Launcher {
    private static final SimpleConsoleLogger log = new SimpleConsoleLogger(APP_FILES_DIR_NAME);
    private static final JsonSettings settings = new JsonSettings(APP_FILES_DIR_NAME, log);
    private static final FramesManager sFramesManager = new FramesManager(settings, log);

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

    private static void initDefaultSettings(SettingsRepository settings, AppLogger log) {
        String androidHome = System.getenv("ANDROID_HOME");
        log.i("ANDROID_HOME = " + androidHome);
        if (androidHome != null) {
            settings.setStringValue(SETTINGS_ANDROID_HOME, androidHome);
        }

        settings.getBoolValueOrDefault(SETTINGS_SHOW_BOOKMARKS,
                settings.getBoolValueOrDefault(SETTINGS_SHOW_BOOKMARKS, true));
    }
}
