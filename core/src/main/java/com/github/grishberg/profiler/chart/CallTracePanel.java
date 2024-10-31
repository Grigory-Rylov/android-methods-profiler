package com.github.grishberg.profiler.chart;

import com.github.grishberg.profiler.analyzer.AnalyzerResultImpl;
import com.github.grishberg.profiler.analyzer.ThreadTimeBoundsImpl;
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl;
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository;
import com.github.grishberg.profiler.chart.preview.PreviewType;
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade;
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade;
import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.SimpleMouseListener;
import com.github.grishberg.profiler.common.TraceContainer;
import com.github.grishberg.profiler.common.settings.SettingsFacade;
import com.github.grishberg.profiler.comparator.model.ComparableProfileData;
import com.github.grishberg.profiler.core.AnalyzerResult;
import com.github.grishberg.profiler.core.ProfileData;
import com.github.grishberg.profiler.core.ThreadTimeBounds;
import com.github.grishberg.profiler.ui.BookMarkInfo;
import com.github.grishberg.profiler.ui.SimpleComponentListener;
import com.github.grishberg.profiler.ui.TimeFormatter;
import com.github.grishberg.profiler.ui.ZoomAndPanDelegate;
import com.github.grishberg.profiler.ui.theme.Palette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;

public class CallTracePanel extends JPanel
    implements ProfileDataDimensionDelegate, ChartPaintDelegate, RepaintDelegate {

    public static final int TOP_OFFSET = 20;
    public static final int MARKER_LABEL_TEXT_MIN_WIDTH = 20;
    private static final int FIT_PADDING = 80;
    private static final int SCALE_FONT_SIZE = 13;
    private static final double NOT_FOUND_ITEM_DARKEN_FACTOR = 0.5;
    private static final double MINIMUM_WIDTH_IN_PX = 1;
    private static final AnalyzerResultImpl RESULT_STUB =
        new AnalyzerResultImpl(Collections.emptyMap(),
            Collections.emptyMap(),
            0,
            Collections.emptyMap(),
            Collections.emptyList(),
            0,
            -1,
            0.0,
            0.0
        );

    private final FoundNavigationListener<ProfileData> foundNavigationListener;
    private boolean init = true;

    private final Bookmarks bookmarks;
    private int currentThreadId = -1;
    private AnalyzerResult result = RESULT_STUB;
    private final Map<Integer, List<ProfileRectangle>> objects = new HashMap<>();

    private int levelHeight = 20;
    private int leftSymbolOffset = 4;
    private int fontTopOffset = 4;
    private double maxRightOffset;
    private double minLeftOffset;
    private double maxBottomOffset;
    private int minLevel;
    private Dimension screenSize;

    private final TimeFormatter timeFormatter;
    private final MethodsColorImpl methodsColor;
    private final Color edgesColor = new Color(0, 0, 0, 131);
    private final Color selectedBookmarkBorderColor = new Color(246, 255, 241);

    private final Color toolbarColor = new Color(65, 65, 65).darker();

    private final ZoomAndPanDelegate zoomAndPanDelegate;
    private boolean isSearchingInProgress;
    private Color selectionColor = new Color(115, 238, 46);
    private Color foundColor = new Color(70, 238, 220);
    private Color focusedFoundColor = new Color(171, 238, 221);
    private Color selectedFoundColor = new Color(110, 238, 161);

    private int currentFocusedFoundElement = -1;
    private int currentSelectedElement = -1;
    private final ArrayList<ProfileRectangle> foundItems = new ArrayList<>();
    private double foundTotalGlobalDuration;
    private double foundTotalThreadDuration;
    private final CalledStacktrace calledStacktrace;
    private Grid scale;
    private final SettingsFacade settings;
    private final AppLogger logger;
    private StagesFacade stagesFacade;
    private SystraceStagesFacade systraceStagesFacade;
    private PreviewImageRepository previewImageRepository;
    private CallTracePreviewPanel previewPanel;
    private Palette palette;
    private boolean isThreadTime;
    private String fontName;
    private final Font labelFont;
    private final FontMetrics labelFontMetrics;
    @Nullable
    private OnRightClickListener rightClickListener;
    private final MethodsNameDrawer cellPaintDelegate = new MethodsNameDrawer(leftSymbolOffset);
    private final ElementColor colorBuffer = new ElementColor();

    public CallTracePanel(
        TimeFormatter timeFormatter,
        MethodsColorImpl methodsColor,
        FoundNavigationListener foundInfoListener,
        SettingsFacade settings,
        AppLogger logger,
        DependenciesFoundAction dependenciesFoundAction,
        StagesFacade stagesFacade,
        SystraceStagesFacade systraceStagesFacade,
        Bookmarks bookmarks,
        PreviewImageRepository previewImageRepository,
        CallTracePreviewPanel previewPanel,
        Palette palette
    ) {
        this.timeFormatter = timeFormatter;
        this.methodsColor = methodsColor;
        this.foundNavigationListener = foundInfoListener;
        this.settings = settings;
        this.logger = logger;
        this.stagesFacade = stagesFacade;
        this.systraceStagesFacade = systraceStagesFacade;
        this.previewImageRepository = previewImageRepository;
        this.previewPanel = previewPanel;
        this.palette = palette;
        this.zoomAndPanDelegate =
            new ZoomAndPanDelegate(this, TOP_OFFSET, new ZoomAndPanDelegate.LeftTopBounds());
        this.bookmarks = bookmarks;
        stagesFacade.setRepaintDelegate(this);
        stagesFacade.setLabelPaintDelegate(this);
        systraceStagesFacade.setRepaintDelegate(this);
        systraceStagesFacade.setLabelPaintDelegate(this);

        addMouseListener(new SimpleMouseListener() {
            @Override
            public void mouseRightClicked(@NotNull MouseEvent e) {
                Point point = e.getPoint();
                if (checkBookmarkHeaderClicked(point)) {
                    return;
                }

                if (rightClickListener != null) {
                    try {
                        Point2D.Double transformedPoint = zoomAndPanDelegate.transformPoint(point);
                        rightClickListener.onMethodRightClicked(point, transformedPoint);
                    } catch (NoninvertibleTransformException noninvertibleTransformException) {
                        noninvertibleTransformException.printStackTrace();
                    }
                }
            }
        });
        screenSize = getSize();
        setBackground(palette.getTraceBackgroundColor());
        addComponentListener(new SimpleComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                screenSize = CallTracePanel.this.getSize();
            }
        });
        labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, SCALE_FONT_SIZE);
        labelFontMetrics = getFontMetrics(labelFont);
        scale = new Grid(TOP_OFFSET, timeFormatter, labelFont, labelFontMetrics);

        isThreadTime = settings.getThreadTimeMode();

        int cellFontSize = settings.getFontSize();

        fontName = settings.getFontName();
        levelHeight = cellFontSize + 3;
        ElementsSelectionRenderer renderer = new ElementsSelectionRenderer(this, this);
        calledStacktrace = new CalledStacktrace(renderer, logger);
        calledStacktrace.setDependenciesFoundAction(dependenciesFoundAction);
        previewPanel.setPreviewClickedAction(new OnPreviewClickedAction() {
            @Override
            public void onPreviewClicked(double offsetInPercent) {
                if (result == RESULT_STUB) {
                    return;
                }

                double offset = 0;
                if (isThreadTime) {
                    offset = result.getThreadTimeBounds().get(currentThreadId).getMaxTime() *
                        offsetInPercent;
                } else {
                    offset = result.getGlobalTimeBounds().get(currentThreadId).getMaxTime() *
                        offsetInPercent;
                }
                zoomAndPanDelegate.scrollTo(offset);
            }
        });
    }

    public void updatePreviewImage() {
        if (result == RESULT_STUB) {
            return;
        }
        PreviewType previewType =
            isThreadTime ? PreviewType.PREVIEW_THREAD : PreviewType.PREVIEW_GLOBAL;
        BufferedImage cachedImage = previewImageRepository.preparePreview(currentThreadId,
            previewType,
            (image, threadId) -> {
                previewPanel.setImage(image);
            }
        );
        if (cachedImage != null) {
            previewPanel.setImage(cachedImage);
        }
    }

    private boolean checkBookmarkHeaderClicked(Point point) {
        AffineTransform transform = zoomAndPanDelegate.getTransform();
        for (int i = bookmarks.size() - 1; i >= 0; i--) {
            BookmarksRectangle bookmark = bookmarks.bookmarkAt(i);
            Shape transformedShape = transform.createTransformedShape(bookmark);
            Rectangle rect = transformedShape.getBounds();

            int cx = (rect.x + rect.width / 2);
            int labelTextWidth = Math.max(
                labelFontMetrics.stringWidth(bookmark.getName()),
                MARKER_LABEL_TEXT_MIN_WIDTH
            );

            // header background
            Rectangle labelRect = new Rectangle(cx - labelTextWidth / 2 - leftSymbolOffset,
                0,
                labelTextWidth + 2 * leftSymbolOffset,
                TOP_OFFSET
            );
            if (labelRect.contains(point)) {
                if (rightClickListener != null) {
                    rightClickListener.onBookmarkRightClicked(point.x, point.y, bookmark);
                }
                return true;
            }
        }
        return false;
    }

    public void setMouseEventListener(ZoomAndPanDelegate.MouseEventsListener l) {
        ZoomAndPanDelegate.MouseEventsListener delegate =
            new ZoomAndPanDelegate.MouseEventsListener() {
                @Override
                public void onMouseClicked(Point point, double x, double y) {
                    l.onMouseClicked(point, x, y);
                }

                @Override
                public void onMouseMove(Point point, double x, double y) {
                    l.onMouseMove(point, x, y);
                }

                @Override
                public void onMouseExited() {
                    l.onMouseExited();
                }

                @Override
                public void onControlMouseClicked(Point point, double x, double y) {
                    l.onControlMouseClicked(point, x, y);
                    findCreatedByDagger(x, y);
                }

                @Override
                public void onControlShiftMouseClicked(Point point, double x, double y) {
                    l.onControlShiftMouseClicked(point, x, y);
                    findDaggerCaller(x, y);
                }
            };
        zoomAndPanDelegate.setMouseEventsListener(delegate);
    }

    public void setRightClickListener(OnRightClickListener listener) {
        rightClickListener = listener;
    }

    /**
     * Switches between thead / global time.
     */
    public void switchTimeMode(boolean isThreadTime) {
        if (isThreadTime == this.isThreadTime) {
            return;
        }
        this.isThreadTime = isThreadTime;
        if (objects.isEmpty()) {
            return;
        }

        updateMarkersState();

        updateBounds(currentThreadId);
        updateData();
        repaint();
        stagesFacade.onThreadModeSwitched(isThreadTime);
        updatePreviewImage();
    }

    public void openTraceResult(TraceContainer trace) {
        disableSearching();

        cellPaintDelegate.resetFontSize();
        stagesFacade.onOpenNewTrace(trace.getResult());

        scale = new Grid(TOP_OFFSET, timeFormatter, labelFont, labelFontMetrics);

        this.result = trace.getResult();
        currentThreadId = -1;

        scale.setScreenWidth(getWidth());
        objects.clear();

        switchThread(result.getMainThreadId());

        this.minLevel = 0;

        maxBottomOffset = calculateTopForLevel(result.getMaxLevel()) + levelHeight;
        bookmarks.set(trace.getBookmarks());
        bookmarks.setup(maxBottomOffset, isThreadTime);

        zoomAndPanDelegate.setTransform(new AffineTransform());
        zoomAndPanDelegate.fitZoom(
            new Rectangle.Double(minLeftOffset, 0, maxRightOffset - minLeftOffset, maxBottomOffset),
            0,
            ZoomAndPanDelegate.VerticalAlign.NONE
        );
        removeSelection();
        previewImageRepository.setAnalyzerResult(result);
        updatePreviewImage();
    }

    public void switchThread(int threadId) {
        requestFocus();
        updateBounds(threadId);

        if (threadId == currentThreadId) {
            return;
        }

        currentThreadId = threadId;

        updateMarkersState();
        updatePreviewImage();

        List<ProfileRectangle> objectsForThread = objects.get(currentThreadId);
        if (objectsForThread != null) {
            // there is data.
            repaint();
            updateStages(threadId, objectsForThread);
            return;
        }

        currentSelectedElement = -1;
        objectsForThread = new ArrayList<>();
        objects.put(threadId, objectsForThread);

        updateStages(threadId, objectsForThread);
        rebuildData(objectsForThread);
        repaint();
    }

    public void highlightCompare(ComparableProfileData rootCompareData, int threadId) {
        requestFocus();
        updateBounds(threadId);

        currentThreadId = threadId;

        updateMarkersState();
        updatePreviewImage();

        currentSelectedElement = -1;

        List<ProfileRectangle> objectsForThread = new ArrayList<>();
        objects.put(threadId, objectsForThread);

        updateStages(threadId, objectsForThread);

        if (rootCompareData.getProfileData().getLevel() != -1) {
            throw new IllegalStateException("Root has level -1");
        }
        rebuildDataWithCompare(rootCompareData, objectsForThread);
        repaint();
    }

    public void updateCompare(ComparableProfileData root, int threadId) {
        Map<String, ProfileRectangle> objectsForThread = new HashMap<>();
        List<ProfileRectangle> rectangles = objects.get(threadId);
        for (ProfileRectangle rectangle : rectangles) {
            rectangle.setColor(methodsColor.getColorForMethod(rectangle));
            objectsForThread.put(rectangle.toString(), rectangle);
        }
        updateCompare(root, objectsForThread);
        repaint();
    }

    private void updateCompare(
        ComparableProfileData root,
        Map<String, ProfileRectangle> objectsForThread
    ) {
        ProfileRectangle rect = createProfileRectangle(root.getProfileData());
        ProfileRectangle currentRect = objectsForThread.get(rect.toString());
        currentRect.setColor(methodsColor.getColorForCompare(root.getMark(), root.getName()));
        for (ComparableProfileData child : root.getChildren()) {
            updateCompare(child, objectsForThread);
        }
    }

    private void rebuildDataWithCompare(
        ComparableProfileData root,
        List<ProfileRectangle> objectsForThread
    ) {
        if (root.getProfileData().getLevel() != -1) {
            ProfileRectangle rect = createProfileRectangle(root.getProfileData());
            rect.setColor(methodsColor.getColorForCompare(root.getMark(), root.getName()));
            objectsForThread.add(rect);
        }
        for (ComparableProfileData child : root.getChildren()) {
            rebuildDataWithCompare(child, objectsForThread);
        }
    }

    private void updateStages(int threadId, List<ProfileRectangle> objectsForThread) {
        stagesFacade.onThreadSwitched(
            objectsForThread,
            threadId == result.getMainThreadId(),
            isThreadTime,
            TOP_OFFSET
        );
        systraceStagesFacade.onThreadSwitched(
            objectsForThread,
            threadId == result.getMainThreadId(),
            isThreadTime,
            TOP_OFFSET
        );
    }

    private void updateBounds(int threadId) {
        if (isThreadTime) {
            ThreadTimeBounds threadTimeBounds = result.getThreadTimeBounds()
                .getOrDefault(threadId, new ThreadTimeBoundsImpl());
            maxRightOffset = threadTimeBounds.getMaxTime();
            minLeftOffset = threadTimeBounds.getMinTime();
        } else {
            ThreadTimeBounds threadTimeBounds = result.getGlobalTimeBounds()
                .getOrDefault(threadId, new ThreadTimeBoundsImpl());
            maxRightOffset = threadTimeBounds.getMaxTime();
            minLeftOffset = threadTimeBounds.getMinTime();
        }
        zoomAndPanDelegate.updateBounds(minLeftOffset, maxRightOffset, maxBottomOffset);
    }

    private void rebuildData(List<ProfileRectangle> objectsForThread) {
        List<ProfileData> newData = result.getData().get(currentThreadId);
        if (newData == null) {
            newData = Collections.emptyList();
        }

        for (ProfileData record : newData) {
            ProfileRectangle rectangle = createProfileRectangle(record);
            objectsForThread.add(rectangle);
        }
    }

    @NotNull
    private ProfileRectangle createProfileRectangle(ProfileData record) {
        double top = calculateTopForLevel(record.getLevel());
        double left = calculateStartXForTime(record);
        double right = calculateEndXForTime(record);
        double width = right - left;

        return new ProfileRectangle(
            left,
            top,
            width,
            levelHeight,
            record
        );
    }

    private void updateData() {
        List<ProfileRectangle> objectsForThread = objects.get(currentThreadId);
        if (objectsForThread == null) {
            // there is no data.
            return;
        }

        for (ProfileRectangle rectangle : objectsForThread) {
            ProfileData record = rectangle.profileData;
            double top = calculateTopForLevel(record.getLevel());
            double left = calculateStartXForTime(record);
            double right = calculateEndXForTime(record);
            double width = right - left;
            rectangle.x = left;
            rectangle.y = top;
            rectangle.width = width;
            rectangle.height = levelHeight;
        }

        calledStacktrace.invalidate();
    }

    private void updateMarkersState() {
        Iterator<BookmarksRectangle> iterator = bookmarks.iterator();
        while (iterator.hasNext()) {
            BookmarksRectangle bookmark = iterator.next();
            bookmark.switchThread(currentThreadId, isThreadTime);
        }
    }

    public void scrollUp() {
        zoomAndPanDelegate.scrollUp();
    }

    public void scrollDown() {
        zoomAndPanDelegate.scrollDown();
    }

    public void scrollLeft() {
        zoomAndPanDelegate.scrollLeft();
    }

    public void scrollRight() {
        zoomAndPanDelegate.scrollRight();
    }

    public void zoomOut() {
        zoomAndPanDelegate.zoomOut();
    }

    public void zoomIn() {
        zoomAndPanDelegate.zoomIn();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        if (init) {
            // Initialize the viewport by moving the origin to the center of the window,
            // and inverting the y-axis to point upwards.
            init = false;
            // Save the viewport to be updated by the ZoomAndPanListener
            AffineTransform transform = g.getTransform();
            zoomAndPanDelegate.setTransform(transform);
        }

        draw(g);
    }

    private void draw(Graphics2D g) {
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_GASP
        );
        // Дополнительно можно включить другие улучшения рендеринга
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        int fontSize = settings.getFontSize();
        fontName = "Arial";
        Font font = new Font(fontName, Font.PLAIN, fontSize);
        //g.setFont(font);
        AffineTransform fontTransform = new AffineTransform(zoomAndPanDelegate.getTransform());
        fontTransform.scale(1, 1);
        g.setFont(font);

        paintObjects(g, zoomAndPanDelegate.getTransform());
    }

    private void paintObjects(Graphics2D g, AffineTransform at) {
        double screenLeft = 0;
        double screenTop = 0;
        double screenRight = 0;
        double screenBottom = 0;
        if (result != null) {
            Map<Integer, ThreadTimeBounds> bounds;
            if (isThreadTime) {
                bounds = result.getThreadTimeBounds();
            } else {
                bounds = result.getGlobalTimeBounds();
            }
            ThreadTimeBounds currentBound = bounds.get(currentThreadId);
            if (currentBound != null) {
                screenLeft = currentBound.getMinTime();
            }
        }
        g.setColor(palette.getTraceBackgroundColor());
        g.fillRect(0, 0, getWidth(), getHeight());

        try {
            Point2D.Double leftTop = zoomAndPanDelegate.transformPoint(new Point(0, 0));
            Point2D.Double rightBottom =
                zoomAndPanDelegate.transformPoint(new Point(screenSize.width, screenSize.height));

            screenLeft = leftTop.x;
            screenTop = leftTop.y;

            screenRight = rightBottom.x;
            screenBottom = rightBottom.y;

        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }

        double msPerPixel = (screenRight - screenLeft) / getWidth();
        double minimumSizeInMs = msPerPixel * MINIMUM_WIDTH_IN_PX;

        FontMetrics fm = getFontMetrics(g.getFont());

        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());

        // draw rectangles
        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle element = objectsForThread.get(i);

            if (!element.isInScreen(screenLeft, screenTop, screenRight, screenBottom)) {
                continue;
            }
            if (element.width < minimumSizeInMs) {
                continue;
            }

            Shape transformedShape = at.createTransformedShape(element);
            calculateColorProfileData(element, objectsForThread);
            drawElement(g, transformedShape, colorBuffer);
            g.setColor(Color.BLACK);
        }

        if (settings.getShowBookmarks()) {
            drawBookmarks(g, at);
        }

        // draw titles
        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle element = objectsForThread.get(i);

            if (!element.isInScreen(screenLeft, screenTop, screenRight, screenBottom)) {
                continue;
            }
            if (element.width < minimumSizeInMs) {
                continue;
            }

            Shape transformedShape = at.createTransformedShape(element);
            Rectangle bounds = transformedShape.getBounds();
            g.setColor(Color.BLACK);

            double left = Math.max(0, bounds.x);
            double right = Math.min(screenSize.width, bounds.x + bounds.width);
            cellPaintDelegate.drawLabel(g, fm, element.profileData.getName(),
                left, right, bounds.y + bounds.height - fontTopOffset
            );
        }

        // draw selections
        @Nullable
        ProfileRectangle selected =
            currentSelectedElement >= 0 ? objectsForThread.get(currentSelectedElement) : null;
        calledStacktrace.draw(
            g,
            at,
            fm,
            currentThreadId,
            selected,
            minimumSizeInMs,
            screenLeft,
            screenTop,
            screenRight,
            screenBottom
        );

        // toolbar background.
        g.setColor(toolbarColor);
        g.fillRect(0, 0, getWidth(), TOP_OFFSET);

        scale.draw(g, at, screenLeft, screenTop, screenRight, screenBottom);

        drawToolbar(g, at, labelFontMetrics);
    }

    private void drawBookmarks(Graphics2D g, AffineTransform at) {
        Iterator<BookmarksRectangle> iterator = bookmarks.iterator();
        while (iterator.hasNext()) {
            BookmarksRectangle bookmark = iterator.next();
            if (!bookmark.getShouldShow()) {
                continue;
            }

            Shape transformedShape = at.createTransformedShape(bookmark);
            g.setColor(bookmark.getColor());
            g.fill(transformedShape);

            if (bookmark == bookmarks.currentSelectedBookmark) {
                g.setColor(selectedBookmarkBorderColor);
                g.draw(transformedShape);
            }
        }
    }

    private void drawElement(Graphics2D g, Shape element, ElementColor colorProfileData) {
        g.setColor(colorProfileData.fillColor);
        g.fill(element);

        g.setColor(colorProfileData.borderColor);
        g.draw(element);
    }

    private void drawToolbar(Graphics2D g, AffineTransform at, FontMetrics fm) {
        systraceStagesFacade.drawStages(g, at, fm);
        stagesFacade.drawStages(g, at, fm);

        Iterator<BookmarksRectangle> iterator = bookmarks.iterator();
        while (iterator.hasNext()) {
            BookmarksRectangle bookmark = iterator.next();
            if (!bookmark.getShouldShow()) {
                continue;
            }

            Shape transformedShape = at.createTransformedShape(bookmark);
            Rectangle rect = transformedShape.getBounds();

            int cx = (rect.x + rect.width / 2);
            int labelTextWidth =
                Math.max(fm.stringWidth(bookmark.getName()), MARKER_LABEL_TEXT_MIN_WIDTH);

            // header background
            g.setColor(bookmark.getHeaderColor());
            g.fillRect(
                cx - labelTextWidth / 2 - leftSymbolOffset,
                0,
                labelTextWidth + 2 * leftSymbolOffset,
                TOP_OFFSET
            );

            g.setColor(bookmark.getHeaderTitleColor());
            if (bookmark.getName().length() > 0) {
                g.drawString(
                    bookmark.getName(),
                    cx - labelTextWidth / 2,
                    labelFontMetrics.getHeight()
                );
            }
        }
    }

    @Override
    public void drawLabel(
        Graphics2D g,
        FontMetrics fm,
        String name,
        Rectangle horizontalBounds,
        int topPosition
    ) {
        int leftPosition = horizontalBounds.x + leftSymbolOffset;
        if (leftPosition < 0) {
            leftPosition = 0;
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            int w = fm.charWidth(c);

            if (leftPosition + w > horizontalBounds.x + horizontalBounds.width) {
                break;
            }

            g.drawString(String.valueOf(c), leftPosition, topPosition);
            leftPosition += w;
        }
    }

    private void calculateColorProfileData(
        ProfileRectangle element,
        List<ProfileRectangle> objects
    ) {
        boolean isSelectedElement =
            currentSelectedElement >= 0 && element == objects.get(currentSelectedElement);

        if (isSearchingInProgress) {
            if (element.isFoundElement) {
                boolean isFocusedElement = currentFocusedFoundElement >= 0 &&
                    element == foundItems.get(currentFocusedFoundElement);
                Color color = isSelectedElement ? selectedFoundColor : foundColor;
                if (isFocusedElement && !isSelectedElement) {
                    color = focusedFoundColor;
                }
                Color borderColor = isFocusedElement ? selectedBookmarkBorderColor : edgesColor;
                colorBuffer.set(color, borderColor);
                return;
            } else {
                if (isSelectedElement) {
                    colorBuffer.set(selectionColor, edgesColor);
                    return;
                }
                Color color = getColorForMethod(element);
                colorBuffer.set(darker(color, NOT_FOUND_ITEM_DARKEN_FACTOR), edgesColor);
                return;
            }
        }

        if (isSelectedElement) {
            colorBuffer.set(selectionColor, edgesColor);
            return;
        }

        Color color = getColorForMethod(element);
        colorBuffer.set(color, edgesColor);
    }

    public static Color darker(Color color, Double darkenFactor) {
        return new Color(
            Math.max((int) (color.getRed() * darkenFactor), 0),
            Math.max((int) (color.getGreen() * darkenFactor), 0),
            Math.max((int) (color.getBlue() * darkenFactor), 0),
            color.getAlpha()
        );
    }

    @NotNull
    private Color getColorForMethod(ProfileRectangle profile) {
        Color color = profile.getColor();
        if (color == null) {
            color = methodsColor.getColorForMethod(profile);
            profile.setColor(color);
        }
        return color;
    }

    @Override
    public double calculateStartXForTime(@NotNull ProfileData record) {
        if (isThreadTime) {
            return record.getThreadStartTimeInMillisecond();
        } else {
            return record.getGlobalStartTimeInMillisecond();
        }
    }

    @Override
    public double calculateEndXForTime(@NotNull ProfileData record) {
        if (isThreadTime) {
            return record.getThreadEndTimeInMillisecond();
        } else {
            return record.getGlobalEndTimeInMillisecond();
        }
    }

    @Override
    public double calculateTopForLevel(int level) {
        return (level - minLevel) * levelHeight;
    }

    @Override
    public double levelHeight() {
        return levelHeight;
    }

    @Override
    public int fontTopOffset() {
        return fontTopOffset;
    }

    public ProfileData findDataByPositionAndSelect(double x, double y) {
        calledStacktrace.removeElements();
        currentSelectedElement = findElementIndexByXY(x, y);
        if (currentSelectedElement < 0) {
            return null;
        }

        repaint();
        return objects.get(currentThreadId).get(currentSelectedElement).profileData;
    }


    private void findCreatedByDagger(double x, double y) {
        int foundElement = findElementIndexByXY(x, y);
        if (foundElement < 0) {
            return;
        }

        ProfileData found = objects.get(currentThreadId).get(foundElement).profileData;
        calledStacktrace.findDaggerCreationTrace(found, currentThreadId, true);
        repaint();
    }

    private void findDaggerCaller(double x, double y) {
        int foundElement = findElementIndexByXY(x, y);
        if (foundElement < 0) {
            return;
        }

        ProfileData found = objects.get(currentThreadId).get(foundElement).profileData;
        calledStacktrace.findDaggerCallerChain(found, currentThreadId);
        repaint();
    }

    public ProfileData findDataByPosition(double x, double y) {
        int pos = findElementIndexByXY(x, y);
        if (pos < 0) {
            return null;
        }

        return objects.get(currentThreadId).get(pos).profileData;
    }

    private int findElementIndexByXY(double x, double y) {
        if (x < minLeftOffset || x > maxRightOffset || y < 0) {
            return -1;
        }
        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle currentElement = objectsForThread.get(i);

            if (currentElement.isInside(x, y)) {
                return i;
            }
        }
        return -1;
    }

    public void renderFoundItems(Finder.ThreadFindResult threadFindResult) {
        isSearchingInProgress = true;
        foundItems.clear();

        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle element = objectsForThread.get(i);

            if (threadFindResult.hasMethod(element.profileData)) {
                foundItems.add(element);
                element.isFoundElement = true;
            } else {
                element.isFoundElement = false;
            }
        }

        currentFocusedFoundElement = 0;
        ProfileRectangle element = foundItems.get(currentFocusedFoundElement);

        foundTotalGlobalDuration = threadFindResult.getTotalGlobalDuration();
        foundTotalThreadDuration = threadFindResult.getTotalThreadDuration();
        foundNavigationListener.onSelected(
            foundItems.size(),
            currentFocusedFoundElement,
            element.profileData,
            foundTotalGlobalDuration,
            foundTotalThreadDuration
        );
        zoomAndPanDelegate.fitZoom(element, FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED);

        requestFocus();
    }

    private void navigateToElement(Shape element) {
        zoomAndPanDelegate.navigateToRectangle(
            element.getBounds2D(),
            ZoomAndPanDelegate.VerticalAlign.ENABLED
        );
    }

    private void navigateToElement(Shape element, ZoomAndPanDelegate.VerticalAlign verticalAlign) {
        zoomAndPanDelegate.navigateToRectangle(element.getBounds2D(), verticalAlign);
    }

    public boolean isSearchingInProgress() {
        return isSearchingInProgress;
    }

    private void addBookmark(ProfileRectangle foundElement, Color color, String title) {
        bookmarks.add(
            new BookmarksRectangle(
                title,
                color,
                foundElement.profileData.getThreadStartTimeInMillisecond(),
                foundElement.profileData.getThreadEndTimeInMillisecond(),
                foundElement.profileData.getGlobalStartTimeInMillisecond(),
                foundElement.profileData.getGlobalEndTimeInMillisecond(),
                foundElement.profileData.getLevel(),
                currentThreadId,
                maxBottomOffset,
                isThreadTime
            )
        );
        previewImageRepository.clear();
        updatePreviewImage();
    }

    public void addBookmarkAtSelectedElement(BookMarkInfo bookMarkInfo) {
        if (currentSelectedElement < 0) {
            return;
        }

        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        ProfileRectangle selected = objectsForThread.get(currentSelectedElement);
        addBookmark(selected, bookMarkInfo.getColor(), bookMarkInfo.getTitle());
        repaint();
    }

    public void addBookmarkAtFocusedFound(BookMarkInfo bookMarkInfo) {
        if (!isSearchingInProgress) {
            return;
        }
        ProfileRectangle focused = foundItems.get(currentFocusedFoundElement);
        addBookmark(focused, bookMarkInfo.getColor(), bookMarkInfo.getTitle());
        repaint();
    }

    public void clearBookmarks() {
        bookmarks.clear();
        repaint();
    }

    public void disableSearching() {
        if (!isSearchingInProgress) {
            return;
        }
        isSearchingInProgress = false;
        for (ProfileRectangle foundElement : foundItems) {
            foundElement.isFoundElement = false;
        }
        foundItems.clear();
        repaint();
    }

    public void removeSelection() {
        calledStacktrace.removeElements();
        currentSelectedElement = -1;
        repaint();
    }

    /**
     * @return selected {@link ProfileData} or null, if there is no any selection.
     */
    @Nullable
    public ProfileData getSelected() {
        if (currentSelectedElement < 0) {
            return null;
        }

        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        return objectsForThread.get(currentSelectedElement).profileData;
    }

    public void focusPrevFoundItem() {
        if (foundItems.size() > 0) {
            currentFocusedFoundElement--;
            if (currentFocusedFoundElement < 0) {
                foundNavigationListener.onNavigatedOverFirstItem();
                return;
            }

            focusFoundItem(currentFocusedFoundElement);
        }
    }

    public void resetFoundItemToEnd() {
        currentFocusedFoundElement = foundItems.size() - 1;
        focusFoundItem(currentFocusedFoundElement);
    }

    private void focusFoundItem(int currentFocusedFoundElement) {
        ProfileRectangle found = foundItems.get(currentFocusedFoundElement);
        zoomAndPanDelegate.fitZoom(found, FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED);
        foundNavigationListener.onSelected(
            foundItems.size(),
            currentFocusedFoundElement,
            found.profileData,
            foundTotalGlobalDuration,
            foundTotalThreadDuration
        );
    }

    public void focusNextFoundItem() {
        if (foundItems.size() > 0) {
            currentFocusedFoundElement++;
            if (currentFocusedFoundElement >= foundItems.size()) {
                foundNavigationListener.onNavigatedOverLastItem();
                return;
            }
            focusFoundItem(currentFocusedFoundElement);
        }
    }

    public void resetFoundItemToStart() {
        currentFocusedFoundElement = 0;
        focusFoundItem(currentFocusedFoundElement);
    }

    public void focusPrevMarker() {
        BookmarksRectangle currentSelectedBookmark = bookmarks.focusPreviousBookmark();
        if (currentSelectedBookmark != null) {
            navigateToElement(currentSelectedBookmark, ZoomAndPanDelegate.VerticalAlign.NONE);
        }
    }

    public void focusNextMarker() {
        BookmarksRectangle currentSelectedBookmark = bookmarks.focusNextBookmark();
        if (currentSelectedBookmark != null) {
            navigateToElement(currentSelectedBookmark, ZoomAndPanDelegate.VerticalAlign.NONE);
        }
    }

    public void removeCurrentFoundElement() {
        if (isSearchingInProgress) {
            foundItems.remove(currentFocusedFoundElement);
            if (foundItems.isEmpty()) {
                isSearchingInProgress = false;
            }
        }
    }

    public void removeCurrentBookmark() {
        bookmarks.removeCurrentBookmark();
        repaint();
        previewImageRepository.clear();
        updatePreviewImage();
    }

    public void removeBookmark(@NotNull BookmarksRectangle selectedBookmark) {
        bookmarks.remove(selectedBookmark);
        repaint();
        previewImageRepository.clear();
        updatePreviewImage();
    }

    public void centerSelectedElement() {
        if (currentSelectedElement < 0) {
            return;
        }
        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        navigateToElement(objectsForThread.get(currentSelectedElement));
    }

    public String copySelectedStacktrace() {
        if (currentSelectedElement < 0) {
            return null;
        }
        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        ProfileRectangle selected = objectsForThread.get(currentSelectedElement);
        return createStackTrace(selected);
    }

    private String createStackTrace(ProfileRectangle selected) {
        StringBuilder sb = new StringBuilder(selected.profileData.getName());

        ProfileData parent = selected.profileData.getParent();
        while (parent != null) {
            sb.append("\n");
            sb.append(parent.getName());
            parent = parent.getParent();
        }
        return sb.toString();
    }

    public void resetZoom() {
        zoomAndPanDelegate.setTransform(new AffineTransform());
        zoomAndPanDelegate.fitZoom(
            new Rectangle.Double(minLeftOffset, 0, maxRightOffset - minLeftOffset, maxBottomOffset),
            0,
            ZoomAndPanDelegate.VerticalAlign.NONE
        );
        repaint();
    }

    public void fitSelectedElement() {
        ProfileRectangle rectangle = null;
        if (foundItems.size() > 0) {
            rectangle = foundItems.get(currentFocusedFoundElement);
        } else if (currentSelectedElement >= 0) {
            List<ProfileRectangle> objectsForThread =
                objects.getOrDefault(currentThreadId, Collections.emptyList());
            rectangle = objectsForThread.get(currentSelectedElement);
        }

        if (rectangle == null) {
            return;
        }

        zoomAndPanDelegate.fitZoom(
            rectangle,
            FIT_PADDING,
            ZoomAndPanDelegate.VerticalAlign.ENABLED
        );
    }

    public void scaleScreenToRange(double start, double end) {
        Rectangle.Double rectangle = new Rectangle2D.Double(start, 0, end - start, 0);
        zoomAndPanDelegate.fitZoom(rectangle, 0, ZoomAndPanDelegate.VerticalAlign.ENABLED);
    }

    public ProfilerPanelData getData() {
        return new ProfilerPanelData(objects.get(currentThreadId), bookmarks.bookmarks);
    }

    /**
     * Enables grid.
     */
    public void setGridEnabled(boolean selected) {
        if (selected != scale.getEnabled()) {
            scale.setEnabled(selected);
            repaint();
        }
    }

    public void onBookmarksStateChanged() {
        repaint();
    }

    public void increaseFontSize() {
        cellPaintDelegate.resetFontSize();
        int newFontSize = changeFontSize(1);
        levelHeight = newFontSize + 3;
        updateData();
        repaint();
    }

    public void decreaseFontSize() {
        cellPaintDelegate.resetFontSize();
        int newFontSize = changeFontSize(-1);
        levelHeight = newFontSize + 3;
        updateData();
        repaint();
    }

    private int changeFontSize(int value) {
        int newFontSize = settings.getFontSize() + value;
        if (newFontSize < 4) {
            return 4;
        }
        settings.setFontSize(newFontSize);
        return newFontSize;
    }

    /**
     * Select element and focus.
     */
    public void selectProfileData(ProfileData selectedElement) {
        ProfileRectangle selectedRectangle = createProfileRectangle(selectedElement);
        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        currentSelectedElement = objectsForThread.indexOf(selectedRectangle);
        if (currentSelectedElement < 0) {
            return;
        }
        centerSelectedElement();
    }

    /**
     *
     */
    public void highlightSelectedProfileChildren() {
        ProfileRectangle selected = getSelectedElement();
        if (selected == null) {
            return;
        }
        calledStacktrace.findChildren(selected.profileData, currentThreadId, true);
        repaint();
    }

    @Nullable
    private ProfileRectangle getSelectedElement() {
        if (currentSelectedElement < 0) {
            return null;
        }
        List<ProfileRectangle> objectsForThread =
            objects.getOrDefault(currentThreadId, Collections.emptyList());
        return objectsForThread.get(currentSelectedElement);
    }

    /**
     * Update {@param selectedBookmark} bookmark
     */
    public void updateBookmark(BookmarksRectangle selectedBookmark, BookMarkInfo result) {
        bookmarks.updateBookmark(selectedBookmark, result);
        repaint();
    }

    @NotNull
    public List<ProfileData> getCurrentThreadMethods() {
        if (result == null) {
            return Collections.emptyList();
        }
        return result.getData().get(currentThreadId);
    }

    public void closeTrace() {
        result = RESULT_STUB;
        objects.clear();
        repaint();
    }

    public void invalidateHighlighting() {
        for (Map.Entry<Integer, List<ProfileRectangle>> entry : objects.entrySet()) {
            List<ProfileRectangle> rectangles = entry.getValue();
            for (ProfileRectangle rectangle : rectangles) {
                rectangle.setColor(null);
            }
        }

        previewImageRepository.clear();
        updatePreviewImage();
        repaint();
    }

    public class ProfilerPanelData {

        public final List<ProfileRectangle> profileData;
        public final List<BookmarksRectangle> markersData;

        public ProfilerPanelData(
            List<ProfileRectangle> profileData,
            List<BookmarksRectangle> markersData
        ) {
            this.profileData = profileData;
            this.markersData = markersData;
        }
    }

    public interface OnRightClickListener {

        /**
         * Is called when right-clicked on bookmark.
         */
        void onBookmarkRightClicked(int x, int y, BookmarksRectangle bookmark);

        /**
         * Is celled when right-clicked on method.
         */
        void onMethodRightClicked(Point clickedPoint, Point2D.Double transformed);
    }
}
