package com.github.grishberg.profiler;

import com.github.grishberg.profiler.chart.highlighting.ColorInfoAdapter;
import com.github.grishberg.profiler.chart.highlighting.StandaloneAppMethodsColorRepository;
import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.SimpleConsoleLogger;
import com.github.grishberg.profiler.common.TraceContainer;
import com.github.grishberg.profiler.common.settings.JsonSettings;
import com.github.grishberg.profiler.common.settings.SettingsFacade;
import com.github.grishberg.profiler.comparator.AggregatorMain;
import com.github.grishberg.profiler.comparator.TraceComparator;
import com.github.grishberg.profiler.comparator.model.ComparableProfileData;
import com.github.grishberg.profiler.ui.FramesManager;
import com.github.grishberg.profiler.ui.Main;
import com.github.grishberg.profiler.ui.StandaloneAppDialogFactory;
import com.github.grishberg.profiler.ui.StandaloneAppFramesManagerFramesManager;
import com.github.grishberg.profiler.ui.ViewFactory;
import kotlin.Pair;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Launcher {
    @Nullable
    private static File sPendingFile = null;
    private static final String APP_FILES_DIR_NAME = StandaloneAppFramesManagerFramesManager.APP_FILES_DIR;
    private static final SimpleConsoleLogger log = new SimpleConsoleLogger(APP_FILES_DIR_NAME);
    private static final JsonSettings settings = new JsonSettings(APP_FILES_DIR_NAME, log);
    private static boolean sMainWidowStarted = false;
    private static final StandaloneAppMethodsColorRepository methodsColorRepository =
            new StandaloneAppMethodsColorRepository(APP_FILES_DIR_NAME,
                    new ColorInfoAdapter(log),
                    log
            );
    private static final ViewFactory sViewFactory = new StandaloneAppDialogFactory(settings, methodsColorRepository);
    private static final FramesManager sFramesManager =
            new StandaloneAppFramesManagerFramesManager(
                    settings, log, sViewFactory, methodsColorRepository
            );

    private static final TraceComparator sTraceComparator = new TraceComparator(log);

    static {
        initDefaultSettings(settings, log);

        String osName = System.getProperty("os.name");
        log.i("Current OS: " + osName);
        if (osName.contains("OS X")) {
            log.i("Setup mac open file handler");
            setupMacOpenFileHandler(log, settings);
        }
    }

    public static void main(String[] args) {
        if (args.length >= 5 && args[0].equals("--agg")) {
            assert args[1].equals("-ref");
            ArrayList<File> reference = new ArrayList<>();
            ArrayList<File> tested = new ArrayList<>();
            int index = 2;
            while (!args[index].equals("-tested")) {
                reference.add(new File(args[index]));
                index++;
                if (index >= args.length) {
                    System.out.println("Aggregate should contain -ref and -tested params");
                    return;
                }
            }
            index++;
            while (index < args.length) {
                tested.add(new File(args[index]));
                index++;
            }
            AggregatorMain app = sFramesManager.createAggregatorFrame();
            app.aggregateAndCompareTraces(reference, tested, true);
        } else if (args.length == 3 && args[0].equals("--cmp")) {
            File reference = new File(args[1]);
            File tested = new File(args[2]);
            Main windowRef = sFramesManager.createMainFrame(Main.StartMode.DEFAULT);
            Main windowTest = sFramesManager.createMainFrame(Main.StartMode.DEFAULT);
            List<TraceContainer> analyzerResults = new ArrayList<>();
            sMainWidowStarted = true;
            windowRef.openCompareTraceFile(reference, (traceContainer) -> {
                analyzerResults.add(traceContainer);
                if (analyzerResults.size() == 2) {
                    Pair<ComparableProfileData, ComparableProfileData> compareResult =
                            sTraceComparator.compare(traceContainer, analyzerResults.get(0));
                    windowRef.highlightCompareResult(compareResult.getFirst());
                    windowTest.highlightCompareResult(compareResult.getSecond());
                }
            });
            windowTest.openCompareTraceFile(tested, (traceContainer) -> {
                analyzerResults.add(traceContainer);
                if (analyzerResults.size() == 2) {
                    Pair<ComparableProfileData, ComparableProfileData> compareResult =
                            sTraceComparator.compare(analyzerResults.get(0), traceContainer);
                    windowRef.highlightCompareResult(compareResult.getFirst());
                    windowTest.highlightCompareResult(compareResult.getSecond());
                }
            });
        } else {
            Main app = sFramesManager.createMainFrame(Main.StartMode.DEFAULT);
            sMainWidowStarted = true;
            if (sPendingFile != null) {
                app.openTraceFile(sPendingFile);
                sPendingFile = null;
            }

            if (args.length > 0) {
                File f = new File(args[0]);
                if (f.isFile()) {
                    app.openTraceFile(f);
                }
            }
        }
    }

    private static void setupMacOpenFileHandler(SimpleConsoleLogger log, JsonSettings settings) {
        log.d("setupMacOpenFileHandler");

        Desktop.getDesktop().setOpenFileHandler(ofe -> {
            log.d("setupMacOpenFileHandler: openFiles: ofe = " + ofe);
            List<File> files = ofe.getFiles();
            if (files != null && files.size() > 0) {
                File file = files.get(0);
                log.d("setupMacOpenFileHandler: openFiles: " + file.getPath());
                if (!sMainWidowStarted) {
                    sPendingFile = file;
                    log.d("setupMacOpenFileHandler: pending file: " + file.getPath());
                    return;
                }
                Main newWindow = sFramesManager.createMainFrame(Main.StartMode.DEFAULT);
                newWindow.openTraceFile(file);
            }
        });
    }

    private static void initDefaultSettings(SettingsFacade settings, AppLogger log) {
        String androidHome = System.getenv("ANDROID_HOME");
        log.i("ANDROID_HOME = " + androidHome);
        if (androidHome != null) {
            settings.setAndroidHome(androidHome);
        }
    }
}
