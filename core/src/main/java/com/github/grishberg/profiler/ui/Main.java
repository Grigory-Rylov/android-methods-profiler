package com.github.grishberg.profiler.ui;

import com.github.grishberg.android.profiler.core.AnalyzerResult;
import com.github.grishberg.android.profiler.core.ProfileData;
import com.github.grishberg.android.profiler.core.ThreadItem;
import com.github.grishberg.profiler.analyzer.FlatMethodsReportGenerator;
import com.github.grishberg.profiler.chart.BookmarkPopupMenu;
import com.github.grishberg.profiler.chart.Bookmarks;
import com.github.grishberg.profiler.chart.BookmarksRectangle;
import com.github.grishberg.profiler.chart.CallTracePanel;
import com.github.grishberg.profiler.chart.CallTracePreviewPanel;
import com.github.grishberg.profiler.chart.Finder;
import com.github.grishberg.profiler.chart.FoundInfoListener;
import com.github.grishberg.profiler.chart.MethodsPopupMenu;
import com.github.grishberg.profiler.chart.flame.FlameChartController;
import com.github.grishberg.profiler.chart.flame.FlameChartDialog;
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl;
import com.github.grishberg.profiler.chart.preview.PreviewImageFactory;
import com.github.grishberg.profiler.chart.preview.PreviewImageFactoryImpl;
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository;
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade;
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade;
import com.github.grishberg.profiler.chart.threads.ThreadsSelectionController;
import com.github.grishberg.profiler.chart.threads.ThreadsViewDialog;
import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl;
import com.github.grishberg.profiler.common.FileSystem;
import com.github.grishberg.profiler.common.MainScope;
import com.github.grishberg.profiler.common.MenuAcceleratorHelperKt;
import com.github.grishberg.profiler.common.TraceContainer;
import com.github.grishberg.profiler.common.UrlOpener;
import com.github.grishberg.profiler.common.settings.SettingsFacade;
import com.github.grishberg.profiler.common.updates.ReleaseVersion;
import com.github.grishberg.profiler.common.updates.UpdatesChecker;
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel;
import com.github.grishberg.profiler.plugins.PluginsFacade;
import com.github.grishberg.profiler.ui.dialogs.KeymapDialog;
import com.github.grishberg.profiler.ui.dialogs.LoadingDialog;
import com.github.grishberg.profiler.ui.dialogs.NewBookmarkDialog;
import com.github.grishberg.profiler.ui.dialogs.ReportsGeneratorDialog;
import com.github.grishberg.profiler.ui.dialogs.ScaleRangeDialog;
import com.github.grishberg.profiler.ui.dialogs.SetAndroidHomeDialog;
import com.github.grishberg.profiler.ui.dialogs.info.DependenciesDialogLogic;
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate;
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView;
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderLogicKt;
import com.github.grishberg.profiler.ui.dialogs.recorder.RecordedResult;
import com.github.grishberg.profiler.ui.theme.ThemeController;
import com.github.grishberg.tracerecorder.SystraceRecordResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;

public class Main implements ZoomAndPanDelegate.MouseEventsListener,
        FoundInfoListener, ActionListener, ShowDialogDelegate, CallTracePanel.OnRightClickListener, UpdatesChecker.UpdatesFoundAction {
    private static final String DEFAULT_DIR = "android-methods-profiler";
    public static final String APP_FILES_DIR_NAME = System.getProperty("user.home") + File.separator + DEFAULT_DIR;
    private final MethodsColorImpl methodsColor;
    @Nullable
    private File currentOpenedFile;

    private enum Actions {
        OPEN_TRACE_FILE,
        RECORD_NEW_TRACE
    }

    public enum StartMode {
        DEFAULT,
        OPEN_TRACE_FILE,
        RECORD_NEW_TRACE
    }

    public static final String DEFAULT_FOUND_INFO_MESSAGE = "";
    private final TimeFormatter timeFormatter = new TimeFormatter();
    private final JFrame frame;
    private final JLabel timeModeLabel;
    private final JLabel coordinatesLabel;
    private final JTextField selectedClassNameLabel;
    private final JTextField findClassText;
    private final JLabel selectedDurationLabel;
    private final CallTracePanel chart;
    private final InfoPanel hoverInfoPanel;
    private final JLabel foundInfo;
    private final NewBookmarkDialog newBookmarkDialog;
    private final LoadingDialog loadingDialog;
    private final JavaMethodsRecorderDialogView methodTraceRecordDialog;
    private final ScaleRangeDialog scaleRangeDialog;
    private final SwitchThreadButton switchThreadsButton;
    private final JCheckBoxMenuItem showBookmarks;
    private final AppLogger log;
    private FramesManager framesManager;
    private final SettingsFacade settings;
    private final JMenu fileMenu;
    private final MenuHistoryItems menuHistoryItems;
    private final FileSystem fileSystem;
    private final DependenciesDialogLogic dependenciesDialog;
    private final JRadioButtonMenuItem globalTimeMenuItem = new JRadioButtonMenuItem("Global time");
    private final JRadioButtonMenuItem threadTimeMenuItem = new JRadioButtonMenuItem("Thread time");
    private final FlameChartController flameChartController;
    private final PluginsFacade pluginsFacade;
    private final StagesFacade stagesFacade;
    private final SystraceStagesFacade systraceStagesFacade;
    private final Bookmarks bookmarks;
    private final CallTracePreviewPanel previewPanel;
    private final PreviewImageRepository previewImageRepository;
    private final ViewFactory dialogFactory;
    private UrlOpener urlOpener;

    @Nullable
    private TraceContainer resultContainer;

    public Main(StartMode startMode,
                SettingsFacade settings,
                AppLogger log,
                FramesManager framesManager,
                ThemeController themeController,
                UpdatesChecker updatesChecker,
                ViewFactory viewFactory,
                UrlOpener urlOpener,
                AppIconDelegate appIconDelegate) {
        this.settings = settings;
        this.log = log;
        this.framesManager = framesManager;
        this.dialogFactory = viewFactory;
        this.urlOpener = urlOpener;
        themeController.applyTheme();

        String title = viewFactory.getTitle();
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fileSystem = new FileSystem(frame, settings, log);

        appIconDelegate.updateFrameIcon(frame);

        JPanel ui = new JPanel(new BorderLayout(2, 2));
        ui.setBorder(new EmptyBorder(0, 4, 0, 4));

        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // open file button
        JPanel topControls = new JPanel(new BorderLayout(2, 2));
        topControls.setBorder(new EmptyBorder(0, 4, 0, 4));

        switchThreadsButton = new SwitchThreadButton();
        switchThreadsButton.addActionListener(e -> showThreadsDialog());
        topControls.add(switchThreadsButton, BorderLayout.LINE_START);

        findClassText = new JTextField("");
        findClassText.setToolTipText("Use this field to find elements in trace");
        findClassText.getDocument().addDocumentListener(new FindTextChangedEvent());

        findClassText.addActionListener(new FindInMethodsAction());
        topControls.add(findClassText, BorderLayout.CENTER);

        foundInfo = new JLabel(DEFAULT_FOUND_INFO_MESSAGE);
        topControls.add(foundInfo, BorderLayout.LINE_END);

        previewPanel = new CallTracePreviewPanel(log);
        int gridY = 0;
        if (viewFactory.shouldAddToolBar()) {
            JToolBar toolBar = new JToolBar();
            toolBar.setFloatable(false);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = gridY++;
            gbc.weightx = 1;
            gbc.anchor = GridBagConstraints.PAGE_START;
            gbc.gridwidth = 0;
            controls.add(toolBar, gbc);
            addToolbarButtons(toolBar, appIconDelegate);
        }

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = gridY++;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.PAGE_START;
        gbc.gridwidth = 0;
        controls.add(previewPanel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = gridY++;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.PAGE_START;
        gbc.gridwidth = 0;
        controls.add(topControls, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = gridY++;
        gbc.weightx = 1;
        gbc.gridwidth = 2;

        gbc.anchor = GridBagConstraints.PAGE_START;
        selectedClassNameLabel = new JTextField("Selected class");
        selectedClassNameLabel.setEnabled(false);
        selectedClassNameLabel.setBorder(new EmptyBorder(0, 4, 0, 4));
        selectedClassNameLabel.setForeground(UIManager.getColor("Label.foreground"));

        JPanel selectionControls = new JPanel(new BorderLayout(10, 4));
        selectionControls.setBorder(new EmptyBorder(0, 4, 2, 4));

        timeModeLabel = new JLabel(timeModeAsString());
        timeModeLabel.setEnabled(false);
        coordinatesLabel = new JLabel("pointer: --/--");
        JPanel timeInfoPanel = new JPanel(new BorderLayout(2, 2));
        timeInfoPanel.add(timeModeLabel, BorderLayout.LINE_START);
        timeInfoPanel.add(coordinatesLabel, BorderLayout.LINE_END);

        selectionControls.add(timeInfoPanel, BorderLayout.LINE_START);
        selectionControls.add(selectedClassNameLabel, BorderLayout.CENTER);

        selectedDurationLabel = new JLabel("Selected duration");
        selectionControls.add(selectedDurationLabel, BorderLayout.LINE_END);

        controls.add(selectionControls, gbc);

        ui.add(controls, BorderLayout.PAGE_START);

        FocusElementDelegate focusElementDelegate = new FocusElementDelegate() {
            @Override
            public void selectProfileElement(@NotNull ProfileData selectedElement) {
                chart.selectProfileData(selectedElement);
            }

            @Override
            public void focusProfileElement(@NotNull ProfileData selectedElement) {
                chart.selectProfileData(selectedElement);
                chart.fitSelectedElement();
            }
        };
        dependenciesDialog = new DependenciesDialogLogic(frame, settings, focusElementDelegate, log);

        bookmarks = new Bookmarks(settings, log);
        MainScope coroutineScope = new MainScope();
        CoroutinesDispatchersImpl coroutinesDispatchers = new CoroutinesDispatchersImpl();
        stagesFacade = new StagesFacade(coroutineScope, coroutinesDispatchers, log);
        systraceStagesFacade = new SystraceStagesFacade(log);
        methodsColor = new MethodsColorImpl(APP_FILES_DIR_NAME, log);

        PreviewImageFactory imageFactory = new PreviewImageFactoryImpl(themeController.getPalette(),
                methodsColor, bookmarks);
        previewImageRepository = new PreviewImageRepository(imageFactory, settings, log,
                coroutineScope, coroutinesDispatchers);

        chart = new CallTracePanel(
                timeFormatter,
                methodsColor,
                this,
                settings,
                log,
                dependenciesDialog,
                stagesFacade,
                systraceStagesFacade,
                bookmarks,
                previewImageRepository,
                previewPanel,
                themeController.getPalette());
        chart.setLayout(new BorderLayout());
        chart.setDoubleBuffered(true);
        chart.setPreferredSize(new Dimension(1024, 800));
        chart.setMinimumSize(new Dimension(1024, 800));
        ui.add(chart, BorderLayout.CENTER);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(ui);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        hoverInfoPanel = new InfoPanel(chart);
        boolean isThreadTime = settings.getThreadTimeMode();
        hoverInfoPanel.changeTimeMode(isThreadTime);
        chart.getRootPane().setGlassPane(hoverInfoPanel);

        chart.setMouseEventListener(this);
        chart.setRightClickListener(this);
        chart.setGridEnabled(true);

        newBookmarkDialog = new NewBookmarkDialog(frame);
        newBookmarkDialog.pack();

        loadingDialog = new LoadingDialog(frame, appIconDelegate);
        loadingDialog.pack();

        methodTraceRecordDialog = viewFactory.createJavaMethodsRecorderDialog(
                coroutineScope, coroutinesDispatchers, frame, settings, log);

        scaleRangeDialog = new ScaleRangeDialog(frame);

        flameChartController = new FlameChartController(methodsColor, settings, log,
                coroutineScope, coroutinesDispatchers);
        FlameChartDialog flameChartDialog = new FlameChartDialog(
                flameChartController,
                themeController.getPalette(),
                Main.DEFAULT_FOUND_INFO_MESSAGE);
        flameChartController.setFoundInfoListener(flameChartDialog);
        flameChartController.setDialogView(flameChartDialog);

        pluginsFacade = new PluginsFacade(frame,
                stagesFacade,
                systraceStagesFacade,
                focusElementDelegate, settings, log,
                coroutineScope, coroutinesDispatchers);
        KeyBinder keyBinder = new KeyBinder(chart,
                selectedClassNameLabel,
                findClassText,
                this,
                newBookmarkDialog,
                hoverInfoPanel, this);
        keyBinder.setUpKeyBindings();

        showBookmarks = new JCheckBoxMenuItem("Show bookmarks");
        showBookmarks.setAccelerator(MenuAcceleratorHelperKt.createAccelerator('B'));
        fileMenu = createFileMenu();
        createMenu(fileMenu, themeController, updatesChecker);
        menuHistoryItems = new MenuHistoryItems(fileMenu, settings, this::openTraceFile);

        if (startMode == StartMode.DEFAULT) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::onClose));
        }
        if (startMode == StartMode.OPEN_TRACE_FILE) {
            showOpenFileChooser(false);
        }
        if (startMode == StartMode.RECORD_NEW_TRACE) {
            showNewTraceDialog(false);
        }

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                coroutineScope.destroy();
                framesManager.onFrameClosed();
            }
        });

        themeController.addThemeSwitchedCallback(new Runnable() {
            @Override
            public void run() {
                previewImageRepository.clear();
                chart.updatePreviewImage();
                chart.repaint();
                flameChartDialog.refreshFlameChart();
            }
        });
        updatesChecker.checkForUpdates(this);
    }

    @Override
    public void onUpdatesFound(@NotNull ReleaseVersion version) {
        log.i("New version '" + version.getVersionName() + "' found, get on " + version.getRepositoryUrl());
        UpdatesInfoPanel updatesInfoPanel = dialogFactory.createUpdatesInfoPanel(
                chart,
                version,
                () -> chart.getRootPane().setGlassPane(hoverInfoPanel),
                () -> urlOpener.openUrl("https://github.com/Grigory-Rylov/android-methods-profiler/releases")
        );
        chart.getRootPane().setGlassPane(updatesInfoPanel);
        updatesInfoPanel.showUpdate();
    }

    private void switchThread(ThreadItem thread) {
        switchThreadsButton.switchThread(thread);
        chart.switchThread(thread.getThreadId());
        pluginsFacade.setCurrentThread(thread);
    }

    private String timeModeAsString() {
        return settings.getThreadTimeMode() ? "thread time" : "global time";
    }

    private void onClose() {
        settings.save();
        log.d("App closed");
    }

    public void addBookmark() {
        newBookmarkDialog.clearAndHide();
        newBookmarkDialog.showNewBookmarkDialog(chart);
        BookMarkInfo result = newBookmarkDialog.getBookMarkInfo();
        if (result != null) {
            chart.addBookmarkAtSelectedElement(result);
        }
    }

    private void addToolbarButtons(JToolBar toolBar,
                                   AppIconDelegate appIconDelegate) {
        JButton button;

        button = makeToolbarButton("Open", "openfile",
                Actions.OPEN_TRACE_FILE,
                "Open .trace file",
                appIconDelegate);
        toolBar.add(button);

        button = makeToolbarButton("New", "newfile",
                Actions.RECORD_NEW_TRACE,
                "Record new method trace from device",
                appIconDelegate);
        toolBar.add(button);
    }

    private JButton makeToolbarButton(
            String altText,
            String iconName,
            Actions actionCommand,
            String toolTipText,
            AppIconDelegate appIconDelegate) {

        String imageLocation = "images/" + iconName + ".png";

        JButton button = new JButton();
        button.setActionCommand(actionCommand.name());
        button.setToolTipText(toolTipText);
        button.addActionListener(this);
        button.setIcon(appIconDelegate.loadIcon(imageLocation, altText));
        return button;
    }

    private void createMenu(JMenu fileMenu, ThemeController themeController, UpdatesChecker updatesChecker) {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(createViewMenu());
        menuBar.add(createSettingsMenu(updatesChecker));
        pluginsFacade.createPluginsMenu(menuBar);
        themeController.addToMenu(menuBar);
        menuBar.add(createHelpMenu());
        frame.setJMenuBar(menuBar);
    }

    private JMenu createFileMenu() {
        JMenu file = new JMenu("File");
        JMenuItem openFile = new JMenuItem("Open .trace file");
        openFile.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('O'));

        JMenuItem openMappingFile = new JMenuItem("Open mapping text file");

        JMenuItem openFileInNewWindow = new JMenuItem("Open .trace file in new window");
        openFileInNewWindow.setAccelerator(MenuAcceleratorHelperKt.createControlShiftAccelerator('O'));

        JMenuItem newFile = new JMenuItem("Record new .trace");
        newFile.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('N'));

        JMenuItem newFileInNewWindow = new JMenuItem("Record new .trace in new Window");
        newFileInNewWindow.setAccelerator(MenuAcceleratorHelperKt.createControlShiftAccelerator('N'));

        JMenuItem exportTraceWithBookmarks = new JMenuItem("Export trace with bookmarks");
        exportTraceWithBookmarks.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('E'));
        JMenuItem openTracesDirInExternalFileManager = new JMenuItem("Open traces dir in external FIle manager");

        JMenuItem deleteCurrentFile = new JMenuItem("Delete current file");

        file.add(openFile);
        file.add(openFileInNewWindow);
        file.add(openMappingFile);
        file.add(newFile);
        file.add(newFileInNewWindow);
        file.addSeparator();
        file.add(exportTraceWithBookmarks);
        file.addSeparator();
        file.add(openTracesDirInExternalFileManager);
        file.add(deleteCurrentFile);

        openFile.addActionListener(arg0 -> showOpenFileChooser(false));
        openFileInNewWindow.addActionListener(arg0 -> showOpenFileChooser(true));
        openMappingFile.addActionListener(arg0 -> openMappingFileChooser());
        newFile.addActionListener(arg0 -> showNewTraceDialog(false));
        newFileInNewWindow.addActionListener(arg0 -> showNewTraceDialog(true));
        exportTraceWithBookmarks.addActionListener(arg0 -> exportTraceWithBookmarks());
        openTracesDirInExternalFileManager.addActionListener(arg -> openTracesDirInExternalFileManager());
        deleteCurrentFile.addActionListener(arg -> deleteCurrentFile());
        file.addSeparator();
        return file;
    }

    private void openTracesDirInExternalFileManager() {
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.open(new File(settings.filesDir(), JavaMethodsRecorderLogicKt.TRACE_FOLDER));
        } catch (Exception e) {
            log.e("File Not Found", e);
        }
    }

    private void deleteCurrentFile() {
        if (currentOpenedFile == null) {
            return;
        }
        boolean shouldDelete = JOptionPane.showConfirmDialog(frame, "Are you wanted to delete: \n\"" + currentOpenedFile.getName() +
                        "\" ?",
                "Delete current file", JOptionPane.YES_NO_OPTION) == 0;

        if (!shouldDelete) {
            return;
        }
        bookmarks.clear();
        chart.closeTrace();
        switchThreadsButton.clear();
        currentOpenedFile.delete();
        previewPanel.clear();
        menuHistoryItems.remove(currentOpenedFile);
        resultContainer = null;
        currentOpenedFile = null;
    }

    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu("View");

        viewMenu.addSeparator();
        ButtonGroup group = new ButtonGroup();
        globalTimeMenuItem.setSelected(!settings.getThreadTimeMode());
        globalTimeMenuItem.setMnemonic(KeyEvent.VK_G);
        globalTimeMenuItem.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('G'));
        group.add(globalTimeMenuItem);
        viewMenu.add(globalTimeMenuItem);
        globalTimeMenuItem.addActionListener(e -> switchTimeMode(false));

        threadTimeMenuItem.setSelected(settings.getThreadTimeMode());
        threadTimeMenuItem.setMnemonic(KeyEvent.VK_T);
        threadTimeMenuItem.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('T'));
        threadTimeMenuItem.addActionListener(e -> switchTimeMode(true));
        group.add(threadTimeMenuItem);
        viewMenu.add(threadTimeMenuItem);

        viewMenu.addSeparator();
        showBookmarks.setSelected(settings.getShowBookmarks());
        showBookmarks.addActionListener(e -> {
            toggleBookmarkMode(false);
        });
        viewMenu.add(showBookmarks);

        viewMenu.addSeparator();

        JMenuItem openRangeDialog = new JMenuItem("Set screen range");
        openRangeDialog.setAccelerator(MenuAcceleratorHelperKt.createShiftAccelerator('R'));
        viewMenu.add(openRangeDialog);
        openRangeDialog.addActionListener(arg0 -> showScaleRangeDialog());

        JMenuItem showFlameChart = new JMenuItem("Show Flame Chart");
        viewMenu.add(showFlameChart);
        showFlameChart.addActionListener(a -> showFlameChartDialog());

        JMenuItem showThreadsDialog = new JMenuItem("Show threads dialog");
        viewMenu.add(showThreadsDialog);
        showThreadsDialog.addActionListener(a -> showThreadsDialog());
        showThreadsDialog.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator(KeyEvent.VK_T));

        viewMenu.addSeparator();

        JMenuItem clearBookmarks = new JMenuItem("Clear bookmarks");
        clearBookmarks.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator(KeyEvent.VK_BACK_SPACE));
        viewMenu.add(clearBookmarks);
        clearBookmarks.addActionListener(a -> clearBookmarks());

        viewMenu.addSeparator();
        JMenuItem fitScreen = new JMenuItem("Scale selected method on screen width");
        fitScreen.setAccelerator(MenuAcceleratorHelperKt.createAccelerator('F'));
        viewMenu.add(fitScreen);
        fitScreen.addActionListener(a -> chart.fitSelectedElement());

        JMenuItem centerSelectedElement = new JMenuItem("Centering the selected method");
        centerSelectedElement.setAccelerator(MenuAcceleratorHelperKt.createAccelerator('C'));
        viewMenu.add(centerSelectedElement);
        centerSelectedElement.addActionListener(a -> chart.centerSelectedElement());

        JMenuItem resetZoom = new JMenuItem("Reset zoom");
        resetZoom.setAccelerator(MenuAcceleratorHelperKt.createAccelerator('Z'));
        viewMenu.add(resetZoom);
        resetZoom.addActionListener(a -> chart.resetZoom());

        viewMenu.addSeparator();

        JMenuItem focusNextBookmark = new JMenuItem("Focus next bookmark");
        focusNextBookmark.setAccelerator(MenuAcceleratorHelperKt.createShiftAccelerator('E'));
        viewMenu.add(focusNextBookmark);
        focusNextBookmark.addActionListener(a -> {
            chart.focusNextMarker();
            hoverInfoPanel.hidePanel();
        });

        JMenuItem focusPreviousBookmark = new JMenuItem("Focus previous bookmark");
        focusPreviousBookmark.setAccelerator(MenuAcceleratorHelperKt.createShiftAccelerator('Q'));
        viewMenu.add(focusPreviousBookmark);
        focusPreviousBookmark.addActionListener(a -> {
            chart.focusPrevMarker();
            hoverInfoPanel.hidePanel();
        });

        JMenuItem switchMainThread = new JMenuItem("Switch to main thread");
        switchMainThread.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('0'));
        viewMenu.add(switchMainThread);
        switchMainThread.addActionListener(a -> {
            switchMainThread();
        });

        viewMenu.addSeparator();
        JMenuItem increaseFontSize = new JMenuItem("Increase font size");
        increaseFontSize.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('+'));
        viewMenu.add(increaseFontSize);
        increaseFontSize.addActionListener(a -> chart.increaseFontSize());

        JMenuItem decreaseFontSize = new JMenuItem("Decrease font size");
        decreaseFontSize.setAccelerator(MenuAcceleratorHelperKt.createControlAccelerator('-'));
        viewMenu.add(decreaseFontSize);
        decreaseFontSize.addActionListener(a -> chart.decreaseFontSize());

        return viewMenu;
    }

    public void showThreadsDialog() {
        if (resultContainer == null) {
            return;
        }

        ThreadsSelectionController controller = new ThreadsSelectionController();
        ThreadsViewDialog dialog = new ThreadsViewDialog(frame, controller, previewImageRepository, log);
        dialog.showThreads(resultContainer.getResult().getThreads());
        dialog.setLocationRelativeTo(chart);
        dialog.setVisible(true);
        ThreadItem selected = dialog.getSelectedThreadItem();
        if (selected == null) {
            return;
        }
        switchThread(selected);
    }

    public void switchMainThread() {
        if (resultContainer != null) {
            switchThread(resultContainer.getResult().getThreads().get(0));
            hoverInfoPanel.hidePanel();
        }
    }

    private void clearBookmarks() {
        chart.clearBookmarks();
    }

    public void showFlameChartDialog() {
        ProfileData selected = chart.getSelected();

        flameChartController.showDialog();

        boolean isThreadTime = settings.getThreadTimeMode();
        if (selected == null) {
            flameChartController.showFlameChart(chart.getCurrentThreadMethods(), isThreadTime);
            return;
        }
        flameChartController.showFlameChart(selected, isThreadTime);
    }

    public void toggleBookmarkMode(boolean triggeredFromKeyMap) {
        boolean selected = showBookmarks.getState();
        if (triggeredFromKeyMap) {
            showBookmarks.setState(!selected);
        } else {
            selected = !selected;
        }
        settings.setShowBookmarks(!selected);
        chart.onBookmarksStateChanged();
    }

    private JMenu createSettingsMenu(UpdatesChecker updatesChecker) {
        JMenu help = new JMenu("Settings");
        JMenuItem openAndroidHomeDir = new JMenuItem("Set Android SDK path");
        help.add(openAndroidHomeDir);

        openAndroidHomeDir.addActionListener(arg0 -> {
            setupAndroidHome();
        });

        if (updatesChecker.shouldAddToMenu()) {
            JCheckBoxMenuItem checkForUpdates = new JCheckBoxMenuItem("Check for updates on start");
            help.add(checkForUpdates);
            checkForUpdates.setState(updatesChecker.getCheckForUpdatesState());
            checkForUpdates.addActionListener(arg0 -> {
                boolean newState = checkForUpdates.getState();
                updatesChecker.setCheckForUpdatesState(newState);
            });
        }
        return help;
    }

    private void setupAndroidHome() {
        SetAndroidHomeDialog dialog = new SetAndroidHomeDialog(frame, settings);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private JMenu createHelpMenu() {
        JMenu help = new JMenu("Help");
        JMenuItem openKeymap = new JMenuItem("Keymap");
        JMenuItem homePage = new JMenuItem("Visit YAMP home page");
        help.add(openKeymap);
        help.add(homePage);

        openKeymap.addActionListener(arg0 -> {
            KeymapDialog dialog = new KeymapDialog(frame);
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);
        });
        homePage.addActionListener(args -> {
            urlOpener.openUrl("https://github.com/Grigory-Rylov/android-methods-profiler");
        });
        return help;
    }

    public void switchTimeMode(boolean isThreadTime) {
        threadTimeMenuItem.setSelected(isThreadTime);
        globalTimeMenuItem.setSelected(!isThreadTime);
        settings.setThreadTimeMode(isThreadTime);
        chart.switchTimeMode(isThreadTime);
        hoverInfoPanel.changeTimeMode(isThreadTime);
        timeModeLabel.setText(timeModeAsString());
        updateSelectedElementValues();
    }

    private void updateSelectedElementValues() {
        // TODO: get selected data and update selectedClassNameLabel
    }

    @Override
    public void onMouseClicked(Point point, float x, float y) {
        selectMethodUnderCursor(x, y);
    }

    private ProfileData selectMethodUnderCursor(float x, float y) {
        chart.requestFocus();
        @Nullable
        ProfileData selectedData = chart.findDataByPositionAndSelect(x, y);
        if (selectedData != null) {
            boolean isThreadTime = settings.getThreadTimeMode();

            double start = isThreadTime ? selectedData.getThreadStartTimeInMillisecond() : selectedData.getGlobalStartTimeInMillisecond();
            double end = isThreadTime ? selectedData.getThreadEndTimeInMillisecond() : selectedData.getGlobalEndTimeInMillisecond();

            selectedClassNameLabel.setText(selectedData.getName());
            selectedDurationLabel.setText(String.format("start: %s, end: %s, duration: %.3f ms",
                    formatMicroseconds(start),
                    formatMicroseconds(end),
                    end - start));

            return selectedData;
        }
        return null;
    }

    @Override
    public void onMouseMove(Point point, float x, float y) {
        coordinatesLabel.setText(String.format("pointer: %s", formatMicroseconds(x)));
        @Nullable
        ProfileData selectedData = chart.findDataByPosition(x, y);
        if (selectedData != null) {
            hoverInfoPanel.setText(point, selectedData);
        } else {
            hoverInfoPanel.hidePanel();
        }
    }

    @Override
    public void onMouseExited() {
        hoverInfoPanel.hidePanel();
    }

    @Override
    public void onControlMouseClicked(Point point, float x, float y) {
        // do nothing
    }

    @Override
    public void onControlShiftMouseClicked(Point point, float x, float y) {
        // do nothing
    }

    @Override
    public void onFound(int count, int selectedIndex) {
        foundInfo.setText(String.format("found %d, current %d", count, selectedIndex));
    }

    @Override
    public void onNotFound(String text, boolean ignoreCase) {
        foundInfo.setText("not found");

        ThreadItem thread = switchThreadsButton.getCurrentThread();
        if (resultContainer == null || thread == null) {
            return;
        }
        Finder finder = new Finder(resultContainer.getResult());
        Finder.FindResult result = finder.findInThread(text, ignoreCase, thread.getThreadId());
        if (result.getFoundResult().isEmpty()) {
            JOptionPane.showMessageDialog(chart, "Not found");
            return;
        }

        int threadIndex = 0;
        for (ThreadItem item : resultContainer.getResult().getThreads()) {
            if (item.getThreadId() == result.getThreadId()) {
                break;
            }
            threadIndex++;
        }
        ThreadItem foundThreadItem = resultContainer.getResult().getThreads().get(threadIndex);
        if (foundThreadItem == null) {
            JOptionPane.showMessageDialog(chart, "Not found");
            return;
        }

        boolean shouldSwitchThread = JOptionPane.showConfirmDialog(frame, "Found results in another thread: \"" + foundThreadItem.getName() +
                        "\"\nShould switch to this thread?",
                "Found in another thread", JOptionPane.YES_NO_OPTION) == 0;

        if (shouldSwitchThread) { //The ISSUE is here
            switchThread(foundThreadItem);
            chart.requestFocus();
            chart.findItems(text, ignoreCase);
        }
    }

    public void findAllChildren() {
        chart.highlightSelectedProfileChildren();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals(Actions.OPEN_TRACE_FILE.name())) {
            showOpenFileChooser(false);
            return;
        }

        if (e.getActionCommand().equals(Actions.RECORD_NEW_TRACE.name())) {
            showNewTraceDialog(false);
        }
    }

    @Override
    public void showOpenFileChooser(boolean inNewWindow) {
        if (inNewWindow) {
            framesManager.createMainFrame(StartMode.OPEN_TRACE_FILE);
            return;
        }

        hoverInfoPanel.hidePanel();
        JFileChooser fileChooser = new JFileChooser(settings.getTraceFileDialogDir());
        for (FileNameExtensionFilter filter : fileSystem.getFileFilters()) {
            fileChooser.addChoosableFileFilter(filter);
        }
        fileChooser.setFileFilter(fileSystem.getFileFilters().get(0));

        int returnVal = fileChooser.showOpenDialog(frame);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            settings.setTraceFileDialogDir(file.getParent());
            menuHistoryItems.addToFileHistory(file);
            openTraceFile(file);
        }
    }

    private void openMappingFileChooser() {
        hoverInfoPanel.hidePanel();
        JFileChooser fileChooser = new JFileChooser(settings.getMappingFileDir());
        fileChooser.setFileFilter(fileSystem.getMappingFilters().get(0));

        int returnVal = fileChooser.showOpenDialog(frame);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            settings.setMappingFileDir(file.getParent());
            fileSystem.openMappingFile(file);

            if (currentOpenedFile != null) {
                openTraceFile(currentOpenedFile);
            }
        }
    }

    @Override
    public void showNewTraceDialog(boolean inNewWindow) {
        if (inNewWindow) {
            framesManager.createMainFrame(StartMode.RECORD_NEW_TRACE);
            return;
        }

        hoverInfoPanel.hidePanel();
        if (settings.getAndroidHome().length() == 0) {
            setupAndroidHome();
            if (settings.getAndroidHome().length() == 0) {
                JOptionPane.showMessageDialog(
                        frame,
                        "For recording need to set ANDROID_HOME env variable." +
                                "\nIf value is already defined, start app from terminal 'java -jar android-methods-profiler.jar'" +
                                "\nOr set 'androidHome' in " + APP_FILES_DIR_NAME + "/.android-methods-profiler-settings.json"
                );
                return;
            }
        }

        methodTraceRecordDialog.setLocationRelativeTo(frame);
        methodTraceRecordDialog.showDialog();
        RecordedResult result = methodTraceRecordDialog.getResult();
        if (result != null) {
            menuHistoryItems.addToFileHistory(result.getRecorderTraceFile());
            openTraceFile(result.getRecorderTraceFile(), result.getSystraces());
        }
    }

    public void openTraceFile(File file) {
        openTraceFile(file, null);
    }

    public void openTraceFile(File file, SystraceRecordResult systraceRecords) {
        currentOpenedFile = file;
        new ParseWorker(file, systraceRecords).execute();
        showProgressDialog(file);
    }

    private void showProgressDialog(File file) {
        loadingDialog.setLocationRelativeTo(frame);
        loadingDialog.setVisible(true);
    }

    private void hideProgressDialog() {
        loadingDialog.setVisible(false);
    }

    @Override
    public void showReportsDialog() {
        hoverInfoPanel.hidePanel();

        FlatMethodsReportGenerator generator = new FlatMethodsReportGenerator(chart.getData());
        ReportsGeneratorDialog reportsGeneratorDialog = new ReportsGeneratorDialog(frame, settings, generator);
        reportsGeneratorDialog.pack();

        reportsGeneratorDialog.setLocationRelativeTo(frame);
        reportsGeneratorDialog.setVisible(true);
    }

    /**
     * Exports current opened trace file with bookmarks.
     */
    public void exportTraceWithBookmarks() {
        if (resultContainer == null) {
            log.d("Try to export trace with bookmarks while nothing is opened");
            return;
        }
        hoverInfoPanel.hidePanel();
        fileSystem.exportTraceWithBookmarks(resultContainer);
    }

    @Override
    public void showScaleRangeDialog() {
        hoverInfoPanel.hidePanel();

        scaleRangeDialog.setLocationRelativeTo(frame);
        scaleRangeDialog.setVisible(true);
        ScaleRangeDialog.Range result = scaleRangeDialog.getResult();
        if (result != null) {
            chart.scaleScreenToRange(result.getStart(), result.getEnd());
        }
    }

    public void exitFromSearching(boolean removeSelection) {
        if (removeSelection) {
            chart.removeSelection();
            chart.requestFocus();
        }
        chart.disableSearching();
        foundInfo.setText(DEFAULT_FOUND_INFO_MESSAGE);
    }

    private String formatMicroseconds(double ms) {
        return timeFormatter.timeToString(ms);
    }

    @Override
    public void onBookmarkRightClicked(int x, int y, BookmarksRectangle bookmark) {
        BookmarkPopupMenu menu = new BookmarkPopupMenu(chart, newBookmarkDialog, bookmark);
        menu.show(chart, x, y);
    }

    @Override
    public void onMethodRightClicked(Point clickedPoint, Point2D.Float transformed) {
        ProfileData selected = selectMethodUnderCursor(transformed.x, transformed.y);
        if (selected == null) {
            return;
        }
        MethodsPopupMenu menu = new MethodsPopupMenu(this, frame, chart, selected, stagesFacade);
        menu.show(chart, clickedPoint.x, clickedPoint.y);
    }

    private class FindInMethodsAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String textToFind = findClassText.getText();
            if (textToFind != null && textToFind.length() > 0) {
                chart.findItems(textToFind, false /* TODO: add checkbox */);
                chart.requestFocus();
                return;
            }
        }
    }

    private class ParseWorker extends SwingWorker<WorkerResult, WorkerResult> {
        private final File traceFile;
        private SystraceRecordResult systraceRecords;

        private ParseWorker(
                File traceFile,
                SystraceRecordResult systraceRecords) {
            this.traceFile = traceFile;
            this.systraceRecords = systraceRecords;
        }

        @Override
        protected WorkerResult doInBackground() {
            try {
                return new WorkerResult(fileSystem.readFile(traceFile));
            } catch (Throwable t) {
                t.printStackTrace();
                return new WorkerResult(t);
            }
        }

        @Override
        protected void done() {
            hideProgressDialog();
            try {
                WorkerResult result = get();
                if (result.traceContainer == null) {
                    JOptionPane.showMessageDialog(
                            frame,
                            result.throwable.getMessage(),
                            "Open .trace file error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                resultContainer = result.traceContainer;
                frame.setTitle(TITLE + getClass().getPackage().getImplementationVersion() + " " + ": " + traceFile.getName());
                chart.openTraceResult(result.traceContainer);
                AnalyzerResult traceContainerResult = result.traceContainer.getResult();
                if (systraceRecords != null) {
                    systraceStagesFacade.setSystraceStages(
                            traceContainerResult,
                            systraceRecords);
                }
                pluginsFacade.setCurrentTraceProfiler(traceContainerResult);
                ThreadItem firstThread = resultContainer.getResult().getThreads().get(0);
                switchThreadsButton.switchThread(firstThread);
                pluginsFacade.setCurrentThread(firstThread);
            } catch (Exception e) {
                e.printStackTrace();
                log.e("Parse trace file exception: ", e);
            }
        }
    }

    private class FindTextChangedEvent implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            /* */
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            if (findClassText.getText().length() == 0) {
                exitFromSearching(false);
            }
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            /* */
        }
    }

    private static class WorkerResult {
        @Nullable
        final TraceContainer traceContainer;
        @Nullable
        final Throwable throwable;

        public WorkerResult(Throwable throwable) {
            this.traceContainer = null;
            this.throwable = throwable;
        }

        public WorkerResult(TraceContainer traceContainer) {
            this.traceContainer = traceContainer;
            this.throwable = null;
        }
    }
}
