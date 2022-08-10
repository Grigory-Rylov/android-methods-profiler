package com.github.grishberg.profiler.ui;

import com.github.grishberg.profiler.analyzer.FlatMethodsReportGenerator;
import com.github.grishberg.profiler.chart.*;
import com.github.grishberg.profiler.chart.flame.FlameChartController;
import com.github.grishberg.profiler.chart.flame.FlameChartDialog;
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl;
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository;
import com.github.grishberg.profiler.chart.preview.PreviewImageFactory;
import com.github.grishberg.profiler.chart.preview.PreviewImageFactoryImpl;
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository;
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade;
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade;
import com.github.grishberg.profiler.chart.threads.ThreadsSelectionController;
import com.github.grishberg.profiler.chart.threads.ThreadsViewDialog;
import com.github.grishberg.profiler.common.*;
import com.github.grishberg.profiler.common.settings.SettingsFacade;
import com.github.grishberg.profiler.common.updates.ReleaseVersion;
import com.github.grishberg.profiler.common.updates.UpdatesChecker;
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel;
import com.github.grishberg.profiler.comparator.ComparatorUIListener;
import com.github.grishberg.profiler.comparator.OpenTraceToCompareCallback;
import com.github.grishberg.profiler.comparator.model.ComparableProfileData;
import com.github.grishberg.profiler.core.AnalyzerResult;
import com.github.grishberg.profiler.core.ProfileData;
import com.github.grishberg.profiler.core.ThreadItem;
import com.github.grishberg.profiler.plugins.PluginsFacade;
import com.github.grishberg.profiler.ui.dialogs.*;
import com.github.grishberg.profiler.ui.dialogs.highlighting.HighlightDialog;
import com.github.grishberg.profiler.ui.dialogs.info.DependenciesDialogLogic;
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate;
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView;
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderLogicKt;
import com.github.grishberg.profiler.ui.theme.ThemeController;
import com.github.grishberg.tracerecorder.SystraceRecordResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.List;

public class Main implements ZoomAndPanDelegate.MouseEventsListener,
        FoundNavigationListener<ProfileData>, ActionListener, ShowDialogDelegate, CallTracePanel.OnRightClickListener, UpdatesChecker.UpdatesFoundAction {
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
    private final JLabel coordinatesLabel = new JLabel("pointer: --/--");
    private final JTextField selectedClassNameLabel = new JTextField(10);
    private final JTextField findClassText;
    private final JLabel selectedDurationLabel = new JLabel("Selected duration");
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
    private final JCheckBox caseInsensitiveToggle;
    private UrlOpener urlOpener;
    private String appFilesDir;
    private final MethodsColorImpl methodsColor;
    private JToolBar toolBar = new JToolBar();
    private boolean allowModalDialogs;
    private final Finder methodsFinder;

    @Nullable
    private TraceContainer resultContainer;

    @Nullable
    private final ComparatorUIListener comparatorUIListener;

    public Main(StartMode startMode,
                SettingsFacade settings,
                AppLogger log,
                FramesManager framesManager,
                ThemeController themeController,
                UpdatesChecker updatesChecker,
                ViewFactory viewFactory,
                UrlOpener urlOpener,
                AppIconDelegate appIconDelegate,
                MethodsColorRepository methodsColorRepository,
                String appFilesDir,
                boolean allowModalDialogs,
                @Nullable ComparatorUIListener comparatorUIListener) {
        this.settings = settings;
        this.log = log;
        this.framesManager = framesManager;
        this.dialogFactory = viewFactory;
        this.urlOpener = urlOpener;
        this.appFilesDir = appFilesDir;
        this.allowModalDialogs = allowModalDialogs;
        this.comparatorUIListener = comparatorUIListener;
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

        toolBar.setFloatable(false);

        // open file button
        JPanel topControls = new JPanel(new BorderLayout(2, 2));
        JPanel topInnerControls = new JPanel(new BorderLayout());

        topControls.setBorder(new EmptyBorder(0, 4, 0, 4));

        switchThreadsButton = new SwitchThreadButton();
        switchThreadsButton.addActionListener(e -> showThreadsDialog());
        topControls.add(switchThreadsButton, BorderLayout.LINE_START);

        findClassText = new JTextField("");
        findClassText.setToolTipText("Use this field to find elements in trace");
        findClassText.getDocument().addDocumentListener(new FindTextChangedEvent());

        findClassText.addActionListener(new FindInMethodsAction());
        topControls.add(topInnerControls, BorderLayout.CENTER);
        topInnerControls.add(findClassText, BorderLayout.CENTER);

        caseInsensitiveToggle = new JCheckBox("Cc");
        caseInsensitiveToggle.setToolTipText("Select to make it case sensitive");
        caseInsensitiveToggle.addChangeListener(new CaseChangeFlagListener());
        caseInsensitiveToggle.setSelected(settings.getCaseSensitive());
        topInnerControls.add(caseInsensitiveToggle, BorderLayout.LINE_END);

        foundInfo = new JLabel(DEFAULT_FOUND_INFO_MESSAGE);
        topControls.add(foundInfo, BorderLayout.LINE_END);


        previewPanel = new CallTracePreviewPanel(log);
        int gridY = 0;
        if (viewFactory.shouldAddToolBar()) {
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = gridY++;
            gbc.weightx = 1;
            gbc.anchor = GridBagConstraints.PAGE_START;
            gbc.gridwidth = 0;
            controls.add(toolBar, gbc);
            addToolbarButtons(toolBar, appIconDelegate);
            toolBar.setVisible(settings.getShouldShowToolbar());
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

        timeModeLabel = new JLabel(timeModeAsString());
        controls.add(createSelectionsPanel(timeModeLabel), gbc);

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
        methodsColor = new MethodsColorImpl(methodsColorRepository);

        PreviewImageFactory imageFactory = new PreviewImageFactoryImpl(themeController.getPalette(),
                methodsColor, bookmarks);
        previewImageRepository = new PreviewImageRepository(imageFactory, settings, log,
                coroutineScope, coroutinesDispatchers);

        methodsFinder = new Finder(coroutineScope, coroutinesDispatchers);
        methodsFinder.setListener(new MethodsFinderListener());

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

        loadingDialog = new LoadingDialog(frame, appIconDelegate, allowModalDialogs);
        loadingDialog.pack();

        methodTraceRecordDialog = viewFactory.createJavaMethodsRecorderDialog(
                coroutineScope, coroutinesDispatchers, frame, settings, log);

        scaleRangeDialog = new ScaleRangeDialog(frame);

        flameChartController = new FlameChartController(methodsColor, settings, log,
                coroutineScope, coroutinesDispatchers);
        FlameChartDialog flameChartDialog = new FlameChartDialog(
                flameChartController,
                themeController.getPalette(),
                Main.DEFAULT_FOUND_INFO_MESSAGE,
                null);
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
                if (comparatorUIListener != null) {
                    comparatorUIListener.onWindowClosed();
                }
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
        frame.setLocationRelativeTo(null);
    }

    private JPanel createSelectionsPanel(JLabel timeModeLabel) {
        selectedClassNameLabel.setEnabled(false);
        selectedClassNameLabel.setBorder(new EmptyBorder(0, 4, 0, 4));
        selectedClassNameLabel.setForeground(UIManager.getColor("Label.foreground"));

        JPanel selectionControls = new JPanel(new BorderLayout(10, 4));
        selectionControls.setBorder(new EmptyBorder(0, 4, 2, 4));

        timeModeLabel.setEnabled(false);
        JPanel timeInfoPanel = new JPanel(new BorderLayout(2, 2));
        timeInfoPanel.add(timeModeLabel, BorderLayout.LINE_START);
        timeInfoPanel.add(coordinatesLabel, BorderLayout.LINE_END);

        selectionControls.add(timeInfoPanel, BorderLayout.LINE_START);
        selectionControls.add(selectedClassNameLabel, BorderLayout.CENTER);

        selectionControls.add(selectedDurationLabel, BorderLayout.LINE_END);

        return selectionControls;
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

    public void switchThread(ThreadItem thread) {
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

        button = makeToolbarButton("Open", "openFile",
                Actions.OPEN_TRACE_FILE,
                "Open .trace file",
                appIconDelegate);
        toolBar.add(button);

        button = makeToolbarButton("New", "newFile",
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

        String imageLocation = "images/" + iconName + ".svg";

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
        if (dialogFactory.getShouldShowSetAdbMenu()) {
            menuBar.add(createSettingsMenu(updatesChecker));
        }
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
        globalTimeMenuItem.setAccelerator(MenuAcceleratorHelperKt.createAccelerator('G'));
        group.add(globalTimeMenuItem);
        viewMenu.add(globalTimeMenuItem);
        globalTimeMenuItem.addActionListener(e -> switchTimeMode(false));

        threadTimeMenuItem.setSelected(settings.getThreadTimeMode());
        threadTimeMenuItem.setMnemonic(KeyEvent.VK_T);
        threadTimeMenuItem.setAccelerator(MenuAcceleratorHelperKt.createAccelerator('T'));
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

        JCheckBoxMenuItem showToolbarMenuItem = new JCheckBoxMenuItem("Show toolbar");
        viewMenu.add(showToolbarMenuItem);
        showToolbarMenuItem.setState(settings.getShouldShowToolbar());
        showToolbarMenuItem.addActionListener(a -> showToolbar(showToolbarMenuItem.getState()));

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

        JMenuItem showHighlightingList = new JMenuItem("Show highlighting settings");
        viewMenu.add(showHighlightingList);
        showHighlightingList.addActionListener(a -> showHighlightingDialog());

        return viewMenu;
    }

    private void showHighlightingDialog() {
        HighlightDialog dialog = dialogFactory.createHighlightDialog(frame);
        dialog.show(frame);
        methodsColor.updateColors();
        chart.invalidateHighlighting();
    }

    private void showToolbar(boolean show) {
        settings.setShouldShowToolbar(show);
        toolBar.setVisible(show);
    }

    public void showThreadsDialog() {
        if (resultContainer == null) {
            return;
        }

        ThreadItem selected = showThreadsDialog(resultContainer.getResult().getThreads(), "Select thread");
        if (selected == null) {
            return;
        }
        switchThread(selected);
        if (methodsFinder.isSearchingModeEnabled()) {
            Finder.ThreadFindResult resultForThread = methodsFinder.getResultForThread(selected);
            if (resultForThread != null) {
                methodsFinder.setCurrentThreadResultForThread(selected);
                chart.renderFoundItems(resultForThread);
            }
        }
    }

    private ThreadItem showThreadsDialog(List<ThreadItem> threads, String title) {
        ThreadsSelectionController controller = new ThreadsSelectionController();
        ThreadsViewDialog dialog = new ThreadsViewDialog(title, frame, controller, previewImageRepository, log);
        dialog.showThreads(threads);
        dialog.setLocationRelativeTo(chart);
        dialog.setVisible(true);
        return dialog.getSelectedThreadItem();
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
        if (dialogFactory.getShouldShowSetAdbMenu()) {
            JMenuItem openAndroidHomeDir = new JMenuItem("Set Android SDK path");
            help.add(openAndroidHomeDir);
            openAndroidHomeDir.addActionListener(arg0 -> {
                setupAndroidHome();
            });
        }

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
    public void onMouseClicked(Point point, double x, double y) {
        selectMethodUnderCursor(x, y);
    }

    private ProfileData selectMethodUnderCursor(double x, double y) {
        chart.requestFocus();
        @Nullable
        ProfileData selectedData = chart.findDataByPositionAndSelect(x, y);
        if (comparatorUIListener != null) {
            comparatorUIListener.onFrameSelected(selectedData);
        }
        if (selectedData != null) {
            showMethodInfoInTopPanel(selectedData);
            return selectedData;
        }
        return null;
    }

    private void showMethodInfoInTopPanel(@NotNull ProfileData selectedData) {
        boolean isThreadTime = settings.getThreadTimeMode();

        double start = isThreadTime ? selectedData.getThreadStartTimeInMillisecond() : selectedData.getGlobalStartTimeInMillisecond();
        double end = isThreadTime ? selectedData.getThreadEndTimeInMillisecond() : selectedData.getGlobalEndTimeInMillisecond();

        selectedClassNameLabel.setText(selectedData.getName());
        selectedDurationLabel.setText(String.format("start: %s, end: %s, duration: %.3f ms",
                formatMicroseconds(start),
                formatMicroseconds(end),
                end - start));
    }

    @Override
    public void onMouseMove(Point point, double x, double y) {
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
    public void onControlMouseClicked(Point point, double x, double y) {
        // do nothing
    }

    @Override
    public void onControlShiftMouseClicked(Point point, double x, double y) {
        // do nothing
    }

    private void findMethods() {
        final String textToFind = findClassText.getText();
        if (textToFind == null || textToFind.length() == 0) {
            return;
        }

        methodsFinder.findMethods(resultContainer.getResult(), textToFind, !caseInsensitiveToggle.isSelected());
    }

    private class MethodsFinderListener implements Finder.FindResultListener {
        @Override
        public void onFindDone(@NotNull Finder.FindResult findResult) {
            if (findResult.getThreadResults().isEmpty()) {
                JOptionPane.showMessageDialog(chart, "Not found");
                return;
            }

            ThreadItem currentThread = switchThreadsButton.getCurrentThread();
            if (resultContainer == null || currentThread == null) {
                return;
            }

            Finder.ThreadFindResult threadFindResult = findResult.getResultForThread(currentThread.getThreadId());
            if (threadFindResult == null) {
                onResultsFoundInOtherThreads(findResult);
                return;
            }

            onResultsFoundInCurrentAndOtherThreads(threadFindResult, findResult);
        }
    }

    private void onResultsFoundInOtherThreads(Finder.FindResult findResult) {
        ThreadItem selectedThread = showThreadsDialog(findResult.getThreadList(), "Found results in another threads");

        if (selectedThread != null) {
            switchThread(selectedThread);
            chart.renderFoundItems(findResult.getThreadResult(selectedThread));
        }
    }

    private void onResultsFoundInCurrentAndOtherThreads(Finder.ThreadFindResult threadFindResult, Finder.FindResult findResult) {
        JOptionPane.showMessageDialog(frame, "Found results in multiple threads: \n" +
                findResult.generateFoundThreadNames() +
                "\"\n");

        chart.renderFoundItems(threadFindResult);
    }

    @Override
    public void onSelected(int count, int selectedIndex, ProfileData selectedElement) {
        foundInfo.setText(String.format("found %d, current %d", count, selectedIndex));
        showMethodInfoInTopPanel(selectedElement);
    }

    @Override
    public void onNavigatedOverLastItem() {
        if (methodsFinder.getSearchResultThreadsCount() == 1) {
            chart.resetFoundItemToStart();
            return;
        }

        ThreadItem nextThread = methodsFinder.getNextThread();

        //if has any result in previous threads - ask and switch
        boolean shouldSwitchThread = JOptionPane.showConfirmDialog(frame, "Switch to results in thread: \"" +
                        nextThread.getName() +
                        "\"",
                "Switch to another thread", JOptionPane.YES_NO_OPTION) == 0;

        if (shouldSwitchThread) {
            methodsFinder.switchNextThread();
            switchThread(nextThread);
            chart.renderFoundItems(methodsFinder.getCurrentThreadResult());
        } else {
            chart.resetFoundItemToStart();
        }

        chart.requestFocus();
    }

    @Override
    public void onNavigatedOverFirstItem() {
        if (methodsFinder.getSearchResultThreadsCount() == 1) {
            chart.resetFoundItemToEnd();
            return;
        }

        ThreadItem previousThread = methodsFinder.getPreviousThread();

        //if has any result in previous threads - ask and switch
        boolean shouldSwitchThread = JOptionPane.showConfirmDialog(frame, "Switch to results in thread: \"" +
                        previousThread.getName() +
                        "\"",
                "Switch to another thread", JOptionPane.YES_NO_OPTION) == 0;

        if (shouldSwitchThread) {
            methodsFinder.switchPreviousThread();
            switchThread(previousThread);
            chart.renderFoundItems(methodsFinder.getCurrentThreadResult());
        }
        chart.resetFoundItemToEnd();
        chart.requestFocus();
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
                                "\nOr set 'androidHome' in " + appFilesDir + "/.android-methods-profiler-settings.json"
                );
                return;
            }
        }

        methodTraceRecordDialog.setResultListener(result -> {
            menuHistoryItems.addToFileHistory(result.getRecorderTraceFile());
            openTraceFile(result.getRecorderTraceFile(), result.getSystraces());
        });
        methodTraceRecordDialog.setLocationRelativeTo(frame);
        methodTraceRecordDialog.showDialog();
    }

    public void openTraceFile(File file) {
        openTraceFile(file, null);
    }

    public void openTraceFile(File file, SystraceRecordResult systraceRecords) {
        currentOpenedFile = file;
        new ParseWorker(file, systraceRecords).execute();
        showProgressDialog(file);
    }

    public void openCompareTraceFile(File file, OpenTraceToCompareCallback callback) {
        currentOpenedFile = file;
        new ParseToCompareWorker(file, callback).execute();
        showProgressDialog(file);
    }

    public void highlightCompareResult(ComparableProfileData rootCompareData) {
        assert resultContainer != null;
        chart.highlightCompare(rootCompareData, resultContainer.getResult().getMainThreadId());
    }

    public void updateCompareResult(ComparableProfileData rootCompareData) {
        assert resultContainer != null;
        ThreadItem currentThread = pluginsFacade.getCurrentThread();
        if (currentThread == null) {
            return;
        }
        chart.updateCompare(rootCompareData, currentThread.getThreadId());
    }

    public boolean isCompareMenuItemEnabled() {
        return comparatorUIListener != null;
    }

    public void onCompareMenuItemClicked() {
        if (comparatorUIListener != null) {
            ProfileData selected = chart.getSelected();
            assert selected != null;
            comparatorUIListener.onCompareMenuItemClick(selected);
        }
    }

    public void onCompareFlameChartMenuItemClicked() {
        if (comparatorUIListener != null) {
            ProfileData selected = chart.getSelected();
            assert selected != null;
            comparatorUIListener.onCompareFlameChartMenuItemClick(selected);
        }
    }

    public void selectProfileData(ProfileData profileData) {
        chart.selectProfileData(profileData);
    }

    public void fitSelectedElement() {
        chart.fitSelectedElement();
    }

    private void showProgressDialog(File file) {
        loadingDialog.setLocationRelativeTo(frame);
        loadingDialog.setVisible(true);
    }

    private void hideProgressDialog() {
        loadingDialog.setVisible(false);
    }

    public void showErrorDialog(String title, String errorMessage) {
        JOptionPane.showMessageDialog(
                frame,
                errorMessage,
                title,
                JOptionPane.ERROR_MESSAGE
        );
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
        methodsFinder.disableSearching();
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
    public void onMethodRightClicked(Point clickedPoint, Point2D.Double transformed) {
        ProfileData selected = selectMethodUnderCursor(transformed.x, transformed.y);
        if (selected == null) {
            return;
        }
        MethodsPopupMenu menu = new MethodsPopupMenu(this, frame, chart, selected, stagesFacade);
        menu.show(chart, clickedPoint.x, clickedPoint.y);
    }

    @Nullable
    public TraceContainer getResultContainer() {
        return resultContainer;
    }

    private class FindInMethodsAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            findMethods();
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
                frame.setTitle(dialogFactory.getShortTitle() + " : " + traceFile.getName());
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

    private class ParseToCompareWorker extends ParseWorker {

        private final OpenTraceToCompareCallback callback;

        private ParseToCompareWorker(File traceFile, OpenTraceToCompareCallback callback) {
            super(traceFile, null);
            this.callback = callback;
        }

        @Override
        protected void done() {
            super.done();
            try {
                TraceContainer traceContainer = get().traceContainer;
                if (traceContainer != null) {
                    callback.onTraceOpened(traceContainer);
                }
            } catch (Exception e) {
                log.e("In callback exception occured:", e);
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

    private class CaseChangeFlagListener implements ChangeListener {
        private boolean oldSelectedState = false;

        @Override
        public void stateChanged(ChangeEvent e) {
            if (oldSelectedState != caseInsensitiveToggle.isSelected()) {
                oldSelectedState = caseInsensitiveToggle.isSelected();
                findMethods();
            }
        }
    }
}
