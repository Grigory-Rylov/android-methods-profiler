package com.github.grishberg.profiler.ui;

import com.github.grishberg.profiler.analyzer.FlatMethodsReportGenerator;
import com.github.grishberg.profiler.analyzer.ProfileData;
import com.github.grishberg.profiler.analyzer.ThreadItem;
import com.github.grishberg.profiler.chart.BookmarkPopupMenu;
import com.github.grishberg.profiler.chart.BookmarksRectangle;
import com.github.grishberg.profiler.chart.Finder;
import com.github.grishberg.profiler.chart.ProfilerPanel;
import com.github.grishberg.profiler.chart.flame.FlameChartController;
import com.github.grishberg.profiler.chart.flame.FlameChartDialog;
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl;
import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl;
import com.github.grishberg.profiler.common.FileSystem;
import com.github.grishberg.profiler.common.TraceContainer;
import com.github.grishberg.profiler.common.settings.SettingsRepository;
import com.github.grishberg.profiler.ui.dialogs.KeymapDialog;
import com.github.grishberg.profiler.ui.dialogs.LoadingDialog;
import com.github.grishberg.profiler.ui.dialogs.NewBookmarkDialog;
import com.github.grishberg.profiler.ui.dialogs.ReportsGeneratorDialog;
import com.github.grishberg.profiler.ui.dialogs.ScaleRangeDialog;
import com.github.grishberg.profiler.ui.dialogs.SetAndroidHomeDialog;
import com.github.grishberg.profiler.ui.dialogs.info.DependenciesDialogLogic;
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate;
import com.github.grishberg.profiler.ui.dialogs.recorder.SampleJavaMethodsDialog;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URL;

public class Main implements ZoomAndPanDelegate.MouseEventsListener,
        ProfilerPanel.FoundInfoListener, ActionListener, ShowDialogDelegate, ProfilerPanel.OnBookmarkClickListener {

    public static final String SETTINGS_ANDROID_HOME = "androidHome";
    private static final String DEFAULT_DIR = "android-methods-profiler";
    public static final String APP_FILES_DIR_NAME = System.getProperty("user.home") + File.separator + DEFAULT_DIR;
    private final MethodsColorImpl methodsColor;

    private enum Actions {
        OPEN_TRACE_FILE,
        RECORD_NEW_TRACE
    }

    public enum StartMode {
        DEFAULT,
        OPEN_TRACE_FILE,
        RECORD_NEW_TRACE
    }

    public static final String SETTINGS_THREAD_TIME_MODE = "Main.threadTimeEnabled";
    public static final String SETTINGS_TRACES_FILE_DIALOG_DIRECTORY = "Main.tracesFileDialogDirectory";
    public static final String SETTINGS_REPORTS_FILE_DIALOG_DIRECTORY = "Main.reportsFileDialogDirectory";
    public static final String SETTINGS_SHOW_BOOKMARKS = "Char.showBookmarks";
    private static final String DEFAULT_FOUND_INFO_MESSAGE = "";
    private static final String TITLE = "YAMP v";
    public static final String SETTINGS_GRID = "Main.enableGrid";

    private final TimeFormatter timeFormatter = new TimeFormatter();
    private final JFrame frame;
    private final JLabel timeModeLabel;
    private final JLabel coordinatesLabel;
    private final JTextField selectedClassNameLabel;
    private final JTextField findClassText;
    private final JLabel selectedDurationLabel;
    private final ProfilerPanel chart;
    private final InfoPanel hoverInfoPanel;
    private final JLabel foundInfo;
    private final NewBookmarkDialog newBookmarkDialog;
    private final LoadingDialog loadingDialog;
    private final SampleJavaMethodsDialog methodTraceRecordDialog;
    private final ScaleRangeDialog scaleRangeDialog;
    private final JComboBox threadsComboBox;
    private final JCheckBoxMenuItem showBookmarks;
    private final AppLogger log;
    private final SettingsRepository settings;
    private final JMenu fileMenu;
    private final MenuHistoryItems menuHistoryItems;
    private final FileSystem fileSystem;
    private final DependenciesDialogLogic dependenciesDialog;
    private final JRadioButtonMenuItem globalTimeMenuItem = new JRadioButtonMenuItem("Global time");
    private final JRadioButtonMenuItem threadTimeMenuItem = new JRadioButtonMenuItem("Thread time");

    private final FlameChartController flameChartController;

    @Nullable
    private TraceContainer resultContainer;

    public Main(StartMode startMode,
                SettingsRepository settings,
                AppLogger log) {
        this.settings = settings;
        this.log = log;
        frame = new JFrame(TITLE + getClass().getPackage().getImplementationVersion());
        fileSystem = new FileSystem(frame, settings, log);

        URL icon = ClassLoader.getSystemResource("images/icon.png");
        frame.setIconImage(Toolkit.getDefaultToolkit().getImage(icon));

        JPanel ui = new JPanel(new BorderLayout(2, 2));
        ui.setBorder(new EmptyBorder(0, 4, 0, 4));

        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // open file button
        JPanel topControls = new JPanel(new BorderLayout(2, 2));
        topControls.setBorder(new EmptyBorder(0, 4, 0, 4));

        threadsComboBox = new JComboBox<ThreadItem>();
        threadsComboBox.setToolTipText("Threads switcher");
        threadsComboBox.setPrototypeDisplayValue(new ThreadItem("XXXXXXXXXXXXXXX", 0));
        threadsComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() != ItemEvent.SELECTED) {
                    return;
                }
                ThreadItem thread = (ThreadItem) threadsComboBox.getSelectedItem();
                if (thread == null) {
                    return;
                }
                chart.switchThread(thread.getThreadId());
                chart.requestFocus();
            }
        });
        topControls.add(threadsComboBox, BorderLayout.LINE_START);

        findClassText = new JTextField("");
        findClassText.setToolTipText("Use this field to find elements in trace");
        findClassText.getDocument().addDocumentListener(new FindTextChangedEvent());

        findClassText.addActionListener(new FindInMethodsAction());
        topControls.add(findClassText, BorderLayout.CENTER);

        foundInfo = new JLabel(DEFAULT_FOUND_INFO_MESSAGE);
        topControls.add(foundInfo, BorderLayout.LINE_END);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.PAGE_START;
        gbc.gridwidth = 0;
        controls.add(topControls, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 1;
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

        dependenciesDialog = new DependenciesDialogLogic(frame, settings, new FocusElementDelegate() {
            @Override
            public void selectProfileElement(@NotNull ProfileData selectedElement) {
                chart.selectProfileData(selectedElement);
            }
        }, log);

        methodsColor = new MethodsColorImpl(APP_FILES_DIR_NAME, log);
        chart = new ProfilerPanel(timeFormatter, methodsColor, this, settings, log, dependenciesDialog);
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
        hoverInfoPanel.changeTimeMode(settings.getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE, false));
        chart.getRootPane().setGlassPane(hoverInfoPanel);

        chart.setMouseEventListener(this);
        chart.setBookmarkClickListener(this);
        chart.setGridEnabled(settings.getBoolValueOrDefault(SETTINGS_GRID, true));

        newBookmarkDialog = new NewBookmarkDialog(frame);
        newBookmarkDialog.pack();


        loadingDialog = new LoadingDialog(frame);
        loadingDialog.pack();

        methodTraceRecordDialog = new SampleJavaMethodsDialog(frame, settings, log);
        methodTraceRecordDialog.pack();

        scaleRangeDialog = new ScaleRangeDialog(frame);

        flameChartController = new FlameChartController(methodsColor, settings, log,
                GlobalScope.INSTANCE, new CoroutinesDispatchersImpl());
        FlameChartDialog flameChartDialog = new FlameChartDialog(frame, flameChartController);

        KeyBinder keyBinder = new KeyBinder(chart,
                selectedClassNameLabel,
                findClassText,
                this,
                newBookmarkDialog,
                hoverInfoPanel, this);
        keyBinder.setUpKeyBindings();

        showBookmarks = new JCheckBoxMenuItem("Show bookmarks");
        fileMenu = createFileMenu();
        createMenu(fileMenu);
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
    }

    private String timeModeAsString() {
        return settings.getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE, false) ?
                "thread time" : "global time";
    }

    private void onClose() {
        settings.save();
        log.d("App closed");
    }

    private void addToolbarButtons(JToolBar toolBar) {
        JButton button;

        button = makeToolbarButton("Open", "openfile",
                Actions.OPEN_TRACE_FILE,
                "Open .trace file");
        toolBar.add(button);

        button = makeToolbarButton("New", "newfile",
                Actions.RECORD_NEW_TRACE,
                "Record new method trace from device");
        toolBar.add(button);
    }

    private JButton makeToolbarButton(
            String altText,
            String iconName,
            Actions actionCommand,
            String toolTipText) {
        String imageLocation = "images/" + iconName + ".png";
        URL icon = ClassLoader.getSystemResource(imageLocation);

        JButton button = new JButton();
        button.setActionCommand(actionCommand.name());
        button.setToolTipText(toolTipText);
        button.addActionListener(this);
        if (icon != null) {
            button.setIcon(new ImageIcon(icon, altText));
        } else {
            button.setText(altText);
            System.err.println("Resource not found: " + imageLocation);
        }

        return button;
    }

    private void createMenu(JMenu fileMenu) {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(createViewMenu());
        menuBar.add(createSettingsMenu());
        menuBar.add(createHelpMenu());
        frame.setJMenuBar(menuBar);
    }

    private JMenu createFileMenu() {
        JMenu file = new JMenu("File");
        JMenuItem openFile = new JMenuItem("Open .trace file");
        JMenuItem openFileInNewWindow = new JMenuItem("Open .trace file in new window");
        JMenuItem newFile = new JMenuItem("Record new .trace");
        JMenuItem newFileInNewWindow = new JMenuItem("Record new .trace in new Window");
        JMenuItem exportTraceWithBookmarks = new JMenuItem("Export trace with bookmarks");

        file.add(openFile);
        file.add(openFileInNewWindow);
        file.add(newFile);
        file.add(newFileInNewWindow);
        file.addSeparator();
        file.add(exportTraceWithBookmarks);

        openFile.addActionListener(arg0 -> showOpenFileChooser(false));
        openFileInNewWindow.addActionListener(arg0 -> showOpenFileChooser(true));
        newFile.addActionListener(arg0 -> showNewTraceDialog(false));
        newFileInNewWindow.addActionListener(arg0 -> showNewTraceDialog(true));
        exportTraceWithBookmarks.addActionListener(arg0 -> exportTraceWithBookmarks());

        file.addSeparator();
        return file;
    }

    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu("View");
        JCheckBoxMenuItem grid = new JCheckBoxMenuItem("Show grid");
        boolean enabled = settings.getBoolValueOrDefault(SETTINGS_GRID, true);
        grid.setSelected(enabled);
        chart.setGridEnabled(enabled);
        grid.addActionListener(e -> {
            AbstractButton aButton = (AbstractButton) e.getSource();
            boolean selected = aButton.getModel().isSelected();
            settings.setBoolValue(SETTINGS_GRID, selected);
            chart.setGridEnabled(selected);
        });

        viewMenu.add(grid);

        viewMenu.addSeparator();
        ButtonGroup group = new ButtonGroup();
        globalTimeMenuItem.setSelected(!settings.getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE, false));
        globalTimeMenuItem.setMnemonic(KeyEvent.VK_G);
        group.add(globalTimeMenuItem);
        viewMenu.add(globalTimeMenuItem);
        globalTimeMenuItem.addActionListener(e -> switchTimeMode(false));

        threadTimeMenuItem.setSelected(settings.getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE, false));
        threadTimeMenuItem.setMnemonic(KeyEvent.VK_T);
        threadTimeMenuItem.addActionListener(e -> switchTimeMode(true));
        group.add(threadTimeMenuItem);
        viewMenu.add(threadTimeMenuItem);

        viewMenu.addSeparator();
        showBookmarks.setSelected(settings.getBoolValueOrDefault(SETTINGS_SHOW_BOOKMARKS, true));
        showBookmarks.addActionListener(e -> {
            toggleBookmarkMode(false);
        });
        viewMenu.add(showBookmarks);

        viewMenu.addSeparator();

        JMenuItem openRangeDialog = new JMenuItem("Set screen range");
        viewMenu.add(openRangeDialog);
        openRangeDialog.addActionListener(arg0 -> showScaleRangeDialog());

        JMenuItem showFlameChart = new JMenuItem("Show Flame Chart");
        viewMenu.add(showFlameChart);
        showFlameChart.addActionListener(a -> showFlameChartDialog());
        return viewMenu;
    }

    private void showFlameChartDialog() {
        ProfileData selected = chart.getSelected();

        flameChartController.showDialog();
        if (selected == null) {
            return;
        }
        flameChartController.showFlameChart(selected);
    }

    public void toggleBookmarkMode(boolean triggeredFromKeyMap) {
        boolean selected = showBookmarks.getState();
        if (triggeredFromKeyMap) {
            showBookmarks.setState(!selected);
        } else {
            selected = !selected;
        }
        settings.setBoolValue(SETTINGS_SHOW_BOOKMARKS, !selected);
        chart.onBookmarksStateChanged();
    }

    private JMenu createSettingsMenu() {
        JMenu help = new JMenu("Settings");
        JMenuItem openAndroidHomeDir = new JMenuItem("Set Android SDK path");
        help.add(openAndroidHomeDir);

        openAndroidHomeDir.addActionListener(arg0 -> {
            setupAndroidHome();
        });
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
        help.add(openKeymap);

        openKeymap.addActionListener(arg0 -> {
            KeymapDialog dialog = new KeymapDialog(frame);
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);
        });
        return help;
    }

    public void switchTimeMode(boolean isThreadTime) {
        threadTimeMenuItem.setSelected(isThreadTime);
        globalTimeMenuItem.setSelected(!isThreadTime);
        settings.setBoolValue(SETTINGS_THREAD_TIME_MODE, isThreadTime);
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
        chart.requestFocus();
        @Nullable
        ProfileData selectedData = chart.findDataByPositionAndSelect(x, y);
        if (selectedData != null) {
            boolean isThreadTime = settings.getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE, false);

            double start = isThreadTime ? selectedData.getThreadStartTimeInMillisecond() : selectedData.getGlobalStartTimeInMillisecond();
            double end = isThreadTime ? selectedData.getThreadEndTimeInMillisecond() : selectedData.getGlobalEndTimeInMillisecond();

            selectedClassNameLabel.setText(selectedData.getName());
            selectedDurationLabel.setText(String.format("start: %s, end: %s, duration: %.3f ms",
                    formatMicroseconds(start),
                    formatMicroseconds(end),
                    end - start));

            if (flameChartController.isViewVisible()) {
                flameChartController.showFlameChart(selectedData);
            }
        }
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

        ThreadItem thread = (ThreadItem) threadsComboBox.getSelectedItem();
        if (resultContainer == null || thread == null) {
            return;
        }
        Finder finder = new Finder(resultContainer.getResult());
        Finder.FindResult result = finder.findInThread(text, ignoreCase, thread.getThreadId());
        if (result.getFoundResult().isEmpty()) {
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
            return;
        }

        boolean shouldSwitchThread = JOptionPane.showConfirmDialog(frame, "Found results in another thread: \"" + foundThreadItem.getName() +
                        "\"\nShould switch to this thread?",
                "Found in another thread", JOptionPane.YES_NO_OPTION) == 0;

        if (shouldSwitchThread) { //The ISSUE is here
            threadsComboBox.setSelectedIndex(threadIndex);
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
            new Main(StartMode.OPEN_TRACE_FILE, settings, log);
            return;
        }

        hoverInfoPanel.hidePanel();
        JFileChooser fileChooser = new JFileChooser(settings.getStringValue(SETTINGS_TRACES_FILE_DIALOG_DIRECTORY));
        for (FileNameExtensionFilter filter : fileSystem.getFileFilters()) {
            fileChooser.addChoosableFileFilter(filter);
        }
        fileChooser.setFileFilter(fileSystem.getFileFilters().get(0));


        int returnVal = fileChooser.showOpenDialog(frame);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            settings.setStringValue(SETTINGS_TRACES_FILE_DIALOG_DIRECTORY, file.getParent());
            menuHistoryItems.addToFileHistory(file);
            openTraceFile(file);
        }
    }

    @Override
    public void showNewTraceDialog(boolean inNewWindow) {
        if (inNewWindow) {
            new Main(StartMode.RECORD_NEW_TRACE, settings, log);
            return;
        }

        hoverInfoPanel.hidePanel();
        if (settings.getStringValueOrDefault(SETTINGS_ANDROID_HOME, "").length() == 0) {
            setupAndroidHome();
            if (settings.getStringValueOrDefault(SETTINGS_ANDROID_HOME, "").length() == 0) {
                JOptionPane.showMessageDialog(
                        frame,
                        "For recording need to set ANDROID_HOME env variable." +
                                "\nIf value is already defined, start app from terminal 'java -jar android-methods-profiler.jar'" +
                                "\nOr set '" + SETTINGS_ANDROID_HOME + "' in " + APP_FILES_DIR_NAME + "/.android-methods-profiler-settings.json"
                );
                return;
            }
        }


        methodTraceRecordDialog.setLocationRelativeTo(frame);
        methodTraceRecordDialog.showDialog();
        File file = methodTraceRecordDialog.getTraceFile();
        if (file != null) {
            menuHistoryItems.addToFileHistory(file);
            openTraceFile(file);
        }
    }

    public void openTraceFile(File file) {
        new ParseWorker(file).execute();
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

    public void exitFromSearching() {
        foundInfo.setText(DEFAULT_FOUND_INFO_MESSAGE);
    }

    private String formatMicroseconds(double ms) {
        return timeFormatter.timeToString(ms);
    }

    @Override
    public void onBookmarkClicked(int x, int y, BookmarksRectangle bookmark) {
        BookmarkPopupMenu menu = new BookmarkPopupMenu(chart, newBookmarkDialog, bookmark);
        menu.show(chart, x, y);
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

        private ParseWorker(File traceFile) {
            this.traceFile = traceFile;
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

                threadsComboBox.removeAllItems();
                for (ThreadItem thread : resultContainer.getResult().getThreads()) {
                    threadsComboBox.addItem(thread);
                }
                threadsComboBox.setSelectedIndex(0);

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
                exitFromSearching();
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
