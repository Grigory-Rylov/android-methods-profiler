package com.github.grishberg.profiler;

import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.SimpleConsoleLogger;
import com.github.grishberg.profiler.common.settings.JsonSettings;
import com.github.grishberg.profiler.common.settings.SettingsRepository;
import com.github.grishberg.profiler.ui.Main;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

import static com.github.grishberg.profiler.ui.Main.APP_FILES_DIR_NAME;
import static com.github.grishberg.profiler.ui.Main.SETTINGS_ANDROID_HOME;
import static com.github.grishberg.profiler.ui.Main.SETTINGS_SHOW_BOOKMARKS;

/**
 * Launcher without mac os x specific files
 */
public class Launcher {
    public static void main(String[] args) {
        SimpleConsoleLogger log = new SimpleConsoleLogger(APP_FILES_DIR_NAME);
        JsonSettings settings = new JsonSettings(APP_FILES_DIR_NAME, log);
        initDefaultSettings(settings, log);

        Main app = new Main(Main.StartMode.DEFAULT, settings, log);

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
