package com.github.grishberg.profiler.chart;

import com.github.grishberg.profiler.analyzer.AnalyzerResult;
import com.github.grishberg.profiler.analyzer.ProfileData;
import com.github.grishberg.profiler.analyzer.ThreadTimeBounds;
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl;
import com.github.grishberg.profiler.common.AppLogger;
import com.github.grishberg.profiler.common.SimpleMouseListener;
import com.github.grishberg.profiler.common.TraceContainer;
import com.github.grishberg.profiler.common.settings.SettingsRepository;
import com.github.grishberg.profiler.ui.BookMarkInfo;
import com.github.grishberg.profiler.ui.Main;
import com.github.grishberg.profiler.ui.SimpleComponentListener;
import com.github.grishberg.profiler.ui.TimeFormatter;
import com.github.grishberg.profiler.ui.ZoomAndPanDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ProfilerPanel extends JPanel implements ProfileDataDimensionDelegate, ChartPaintDelegate {
    public static final int TOP_OFFSET = 20;
    public static final String SETTINGS_FONT_NAME = "Chart.fontName";
    public static final String SETTINGS_CELL_FONT_SIZE = "Chart.cellsFontSize";
    public static final int DEFAULT_CELL_FONT_SIZE = 12;
    private static final int MARKER_LABEL_TEXT_MIN_WIDTH = 20;
    private static final int FIT_PADDING = 80;
    private static final int SCALE_FONT_SIZE = 13;
    private static final double NOT_FOUND_ITEM_DARKEN_FACTOR = 0.5;
    private static final double MINIMUM_WIDTH_IN_PX = 3;

    private final FoundInfoListener foundInfoListener;
    private boolean init = true;

    private Bookmarks bookmarks;
    private int currentThreadId = -1;
    private AnalyzerResult result = new AnalyzerResult(Collections.emptyMap(), Collections.emptyMap(), 0, Collections.emptyMap(), Collections.emptyList(), 0);
    private final Map<Integer, List<ProfileRectangle>> objects = new HashMap<>();

    private int levelHeight = 20;
    private int leftSymbolOffset = 4;
    private int fontTopOffset = 4;
    private double maxRightOffset;
    private double maxBottomOffset;
    private double minTime;
    private int minLevel;
    private Dimension screenSize;

    private final TimeFormatter timeFormatter;
    private final MethodsColorImpl methodsColor;
    private final Color edgesColor = new Color(0, 0, 0, 131);
    private final Color selectedBookmarkBorderColor = new Color(246, 255, 241);

    private final Color bgColor = new Color(65, 65, 65);
    private final Color toolbarColor = bgColor.darker();

    private final ZoomAndPanDelegate zoomAndPanDelegate;
    private boolean isSearchingInProgress;
    private Color selectionColor = new Color(115, 238, 46);
    private Color foundColor = new Color(70, 238, 220);
    private Color focusedFoundColor = new Color(171, 238, 221);
    private Color selectedFoundColor = new Color(110, 238, 161);

    private int currentFocusedFoundElement = -1;
    private int currentSelectedElement = -1;
    private final ArrayList<ProfileRectangle> foundItems = new ArrayList<>();
    private final CalledStacktrace calledStacktrace;
    private Grid scale;
    private final SettingsRepository settings;
    private final AppLogger logger;
    private boolean isThreadTime;
    private String fontName;
    private final Font labelFont;
    private final FontMetrics labelFontMetrics;
    @Nullable
    private OnBookmarkClickListener bookmarkClickListener;

    public ProfilerPanel(TimeFormatter timeFormatter,
                         MethodsColorImpl methodsColor,
                         FoundInfoListener foundInfoListener,
                         SettingsRepository settings,
                         AppLogger logger,
                         DependenciesFoundAction dependenciesFoundAction) {
        this.timeFormatter = timeFormatter;
        this.methodsColor = methodsColor;
        this.foundInfoListener = foundInfoListener;
        this.settings = settings;
        this.logger = logger;
        this.zoomAndPanDelegate = new ZoomAndPanDelegate(this, TOP_OFFSET, new ZoomAndPanDelegate.LeftTopBounds());
        bookmarks = new Bookmarks(settings, logger);

        addMouseListener(new SimpleMouseListener() {
            @Override
            public void mouseRightClicked(@NotNull MouseEvent e) {
                checkBookmarkHeaderClicked(e.getPoint());
            }
        });
        screenSize = getSize();
        setBackground(bgColor);
        addComponentListener(new SimpleComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                screenSize = ProfilerPanel.this.getSize();
            }
        });
        labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, SCALE_FONT_SIZE);
        labelFontMetrics = getFontMetrics(labelFont);
        scale = new Grid(settings, TOP_OFFSET, timeFormatter, labelFont, labelFontMetrics);

        isThreadTime = settings.getBoolValueOrDefault(Main.SETTINGS_THREAD_TIME_MODE, false);

        int cellFontSize = settings.getIntValueOrDefault(SETTINGS_CELL_FONT_SIZE, DEFAULT_CELL_FONT_SIZE);
        settings.setIntValue(SETTINGS_CELL_FONT_SIZE, cellFontSize);

        fontName = settings.getStringValueOrDefault(SETTINGS_FONT_NAME, "Arial");
        settings.setStringValue(SETTINGS_FONT_NAME, fontName);
        levelHeight = cellFontSize + 3;
        ElementsSelectionRenderer renderer = new ElementsSelectionRenderer(this, this);
        calledStacktrace = new CalledStacktrace(renderer, logger);
        calledStacktrace.setDependenciesFoundAction(dependenciesFoundAction);
    }

    private void checkBookmarkHeaderClicked(Point point) {
        AffineTransform transform = zoomAndPanDelegate.getTransform();
        for (int i = bookmarks.size() - 1; i >= 0; i--) {
            BookmarksRectangle bookmark = bookmarks.bookmarkAt(i);
            Shape transformedShape = transform.createTransformedShape(bookmark);
            Rectangle rect = transformedShape.getBounds();

            int cx = (rect.x + rect.width / 2);
            int labelTextWidth = Math.max(labelFontMetrics.stringWidth(bookmark.getName()), MARKER_LABEL_TEXT_MIN_WIDTH);

            // header background
            Rectangle labelRect = new Rectangle(cx - labelTextWidth / 2 - leftSymbolOffset, 0, labelTextWidth + 2 * leftSymbolOffset, TOP_OFFSET);
            if (labelRect.contains(point)) {
                if (bookmarkClickListener != null) {
                    bookmarkClickListener.onBookmarkClicked(point.x, point.y, bookmark);
                }
                return;
            }
        }
    }

    public void setMouseEventListener(ZoomAndPanDelegate.MouseEventsListener l) {
        ZoomAndPanDelegate.MouseEventsListener delegate = new ZoomAndPanDelegate.MouseEventsListener() {
            @Override
            public void onMouseClicked(Point point, float x, float y) {
                l.onMouseClicked(point, x, y);
            }

            @Override
            public void onMouseMove(Point point, float x, float y) {
                l.onMouseMove(point, x, y);
            }

            @Override
            public void onMouseExited() {
                l.onMouseExited();
            }

            @Override
            public void onControlMouseClicked(Point point, float x, float y) {
                l.onControlMouseClicked(point, x, y);
                findCreatedByDagger(x, y);
            }

            @Override
            public void onControlShiftMouseClicked(Point point, float x, float y) {
                l.onControlShiftMouseClicked(point, x, y);
                findDaggerCaller(x, y);
            }
        };
        zoomAndPanDelegate.setMouseEventsListener(delegate);
    }

    public void setBookmarkClickListener(OnBookmarkClickListener listener) {
        bookmarkClickListener = listener;
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
    }

    public void openTraceResult(TraceContainer trace) {
        scale = new Grid(settings, TOP_OFFSET, timeFormatter, labelFont, labelFontMetrics);

        this.result = trace.getResult();
        currentThreadId = -1;

        scale.setScreenWidth(getWidth());
        objects.clear();

        switchThread(result.getMainThreadId());

        this.minLevel = 0;
        this.minTime = 0;

        maxBottomOffset = calculateTopForLevel(result.getMaxLevel()) + levelHeight;
        bookmarks = trace.getBookmarks();
        bookmarks.setup(maxBottomOffset, isThreadTime);

        zoomAndPanDelegate.setTransform(new AffineTransform());
        zoomAndPanDelegate.fitZoom(new Rectangle.Double(0, 0, maxRightOffset, maxBottomOffset), 0, ZoomAndPanDelegate.VerticalAlign.NONE);
        removeSelection();
    }

    public void switchThread(int threadId) {
        updateBounds(threadId);

        if (threadId == currentThreadId) {
            return;
        }

        currentThreadId = threadId;

        updateMarkersState();

        List<ProfileRectangle> objectsForThread = objects.get(currentThreadId);
        if (objectsForThread != null) {
            // there is data.
            repaint();
            return;
        }

        currentSelectedElement = -1;
        objectsForThread = new ArrayList<>();
        objects.put(threadId, objectsForThread);

        rebuildData(objectsForThread);
        repaint();
    }

    private void updateBounds(int threadId) {
        if (isThreadTime) {
            maxRightOffset = result.getThreadTimeBounds().getOrDefault(threadId, new ThreadTimeBounds()).getMaxTime();
        } else {
            maxRightOffset = result.getGlobalTimeBounds().getOrDefault(threadId, new ThreadTimeBounds()).getMaxTime();
        }
        zoomAndPanDelegate.updateRightBottomCorner(maxBottomOffset, maxBottomOffset);
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
                record);
    }

    private void updateData() {
        List<ProfileRectangle> objectsForThread = objects.get(currentThreadId);
        if (objectsForThread == null) {
            // there is data.
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
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

        int fontSize = settings.getIntValueOrDefault(SETTINGS_CELL_FONT_SIZE, DEFAULT_CELL_FONT_SIZE);
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

        try {
            Point2D.Float leftTop = zoomAndPanDelegate.transformPoint(new Point(0, 0));
            Point2D.Float rightBottom = zoomAndPanDelegate.transformPoint(new Point(screenSize.width, screenSize.height));

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

        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());

        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle element = objectsForThread.get(i);

            if (!element.isInScreen(screenLeft, screenTop, screenRight, screenBottom)) {
                continue;
            }
            if (element.width < minimumSizeInMs) {
                continue;
            }

            Shape transformedShape = at.createTransformedShape(element);
            ElementColor colorProfileData = getColorProfileData(element, objectsForThread);
            drawElement(g, transformedShape, colorProfileData);
            g.setColor(Color.BLACK);
        }

        @Nullable
        ProfileRectangle selected = currentSelectedElement >= 0 ? objectsForThread.get(currentSelectedElement) : null;

        calledStacktrace.draw(g, at, fm, currentThreadId, selected, minimumSizeInMs, screenLeft, screenTop, screenRight, screenBottom);

        if (settings.getBoolValueOrDefault(Main.SETTINGS_SHOW_BOOKMARKS, true)) {
            drawBookmarks(g, at);
        }

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
            drawLabel(g, fm, element.profileData.getName(), bounds, bounds.y + bounds.height - fontTopOffset);
        }
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

        Iterator<BookmarksRectangle> iterator = bookmarks.iterator();
        while (iterator.hasNext()) {
            BookmarksRectangle bookmark = iterator.next();
            if (!bookmark.getShouldShow()) {
                continue;
            }

            Shape transformedShape = at.createTransformedShape(bookmark);
            Rectangle rect = transformedShape.getBounds();

            int cx = (rect.x + rect.width / 2);
            int labelTextWidth = Math.max(fm.stringWidth(bookmark.getName()), MARKER_LABEL_TEXT_MIN_WIDTH);

            // header background
            g.setColor(bookmark.getHeaderColor());
            g.fillRect(cx - labelTextWidth / 2 - leftSymbolOffset, 0, labelTextWidth + 2 * leftSymbolOffset, TOP_OFFSET);

            g.setColor(bookmark.getHeaderTitleColor());
            if (bookmark.getName().length() > 0) {
                g.drawString(bookmark.getName(), cx - labelTextWidth / 2, labelFontMetrics.getHeight());
            }
        }
    }

    @Override
    public void drawLabel(Graphics2D g,
                          FontMetrics fm,
                          String name,
                          Rectangle horizontalBounds,
                          int topPosition) {
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

    private ElementColor getColorProfileData(ProfileRectangle element, List<ProfileRectangle> objects) {
        boolean isSelectedElement = currentSelectedElement >= 0 && element == objects.get(currentSelectedElement);
        ProfileData profile = element.profileData;

        if (isSearchingInProgress) {
            if (element.isFoundElement) {
                boolean isFocusedElement = currentFocusedFoundElement >= 0 && element == foundItems.get(currentFocusedFoundElement);
                Color color = isSelectedElement ? selectedFoundColor : foundColor;
                if (isFocusedElement && !isSelectedElement) {
                    color = focusedFoundColor;
                }
                Color borderColor = isFocusedElement ? selectedBookmarkBorderColor : edgesColor;
                return new ElementColor(color, borderColor);
            } else {
                if (isSelectedElement) {
                    return new ElementColor(selectionColor, edgesColor);
                }
                Color color = getColorForMethod(profile);
                return new ElementColor(darker(color), edgesColor);
            }
        }

        if (isSelectedElement) {
            return new ElementColor(selectionColor, edgesColor);
        }

        Color color = getColorForMethod(profile);
        return new ElementColor(color, edgesColor);
    }

    private static Color darker(Color color) {
        return new Color(Math.max((int) (color.getRed() * NOT_FOUND_ITEM_DARKEN_FACTOR), 0),
                Math.max((int) (color.getGreen() * NOT_FOUND_ITEM_DARKEN_FACTOR), 0),
                Math.max((int) (color.getBlue() * NOT_FOUND_ITEM_DARKEN_FACTOR), 0),
                color.getAlpha());
    }

    @NotNull
    private Color getColorForMethod(ProfileData profile) {
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

    public ProfileData findDataByPositionAndSelect(float x, float y) {
        calledStacktrace.removeElements();
        currentSelectedElement = findElementIndexByXY(x, y);
        if (currentSelectedElement < 0) {
            return null;
        }

        repaint();
        return objects.get(currentThreadId).get(currentSelectedElement).profileData;
    }


    private void findCreatedByDagger(float x, float y) {
        int foundElement = findElementIndexByXY(x, y);
        if (foundElement < 0) {
            return;
        }

        ProfileData found = objects.get(currentThreadId).get(foundElement).profileData;
        calledStacktrace.findDaggerCreationTrace(found, currentThreadId, true);
        repaint();
    }

    private void findDaggerCaller(float x, float y) {
        int foundElement = findElementIndexByXY(x, y);
        if (foundElement < 0) {
            return;
        }

        ProfileData found = objects.get(currentThreadId).get(foundElement).profileData;
        calledStacktrace.findDaggerCallerChain(found, currentThreadId);
        repaint();
    }

    public ProfileData findDataByPosition(float x, float y) {
        int pos = findElementIndexByXY(x, y);
        if (pos < 0) {
            return null;
        }

        return objects.get(currentThreadId).get(pos).profileData;
    }

    private int findElementIndexByXY(float x, float y) {
        if (x < 0 || x > maxRightOffset || y < 0) {
            return -1;
        }
        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle currentElement = objectsForThread.get(i);

            if (currentElement.isInside(x, y)) {
                return i;
            }
        }
        return -1;
    }

    public void findItems(String textToFind, boolean ignoreCase) {
        boolean shouldEndsWithText = textToFind.endsWith("()");
        if (shouldEndsWithText) {
            textToFind = textToFind.substring(0, textToFind.length() - 2);
        }
        isSearchingInProgress = true;
        String targetString = ignoreCase ? textToFind.toLowerCase() : textToFind;

        foundItems.clear();

        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
        for (int i = 0; i < objectsForThread.size(); i++) {
            ProfileRectangle element = objectsForThread.get(i);

            String lowerCasedName = ignoreCase ? element.profileData.getName().toLowerCase() : element.profileData.getName();
            boolean isEquals = shouldEndsWithText ? lowerCasedName.endsWith(targetString) : lowerCasedName.contains(targetString);
            if (isEquals) {
                foundItems.add(element);
                element.isFoundElement = true;
            } else {
                element.isFoundElement = false;
            }
        }
        if (foundItems.isEmpty()) {
            foundInfoListener.onNotFound(textToFind, ignoreCase);
            repaint();
            return;
        }
        currentFocusedFoundElement = 0;
        foundInfoListener.onFound(foundItems.size(), currentFocusedFoundElement);
        ProfileRectangle element = foundItems.get(currentFocusedFoundElement);
        zoomAndPanDelegate.fitZoom(element, FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED);
    }

    private void navigateToElement(Shape element) {
        zoomAndPanDelegate.navigateToRectangle(element.getBounds2D(), ZoomAndPanDelegate.VerticalAlign.ENABLED);
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
    }

    public void addBookmarkAtSelectedElement(BookMarkInfo bookMarkInfo) {
        if (currentSelectedElement < 0) {
            return;
        }

        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
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

        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
        return objectsForThread.get(currentSelectedElement).profileData;
    }

    public void focusPrevFoundItem() {
        if (foundItems.size() > 0) {
            currentFocusedFoundElement--;
            if (currentFocusedFoundElement < 0) {
                currentFocusedFoundElement = foundItems.size() - 1;
            }
            zoomAndPanDelegate.fitZoom(foundItems.get(currentFocusedFoundElement), FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED);
            foundInfoListener.onFound(foundItems.size(), currentFocusedFoundElement);
            return;
        }
    }

    public void focusNextFoundItem() {
        if (foundItems.size() > 0) {
            currentFocusedFoundElement++;
            if (currentFocusedFoundElement >= foundItems.size()) {
                currentFocusedFoundElement = 0;
            }
            zoomAndPanDelegate.fitZoom(foundItems.get(currentFocusedFoundElement), FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED);
            foundInfoListener.onFound(foundItems.size(), currentFocusedFoundElement);
            return;
        }
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
    }

    public void removeBookmark(@NotNull BookmarksRectangle selectedBookmark) {
        bookmarks.remove(selectedBookmark);
        repaint();
    }

    public void centerSelectedElement() {
        if (currentSelectedElement < 0) {
            return;
        }
        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
        navigateToElement(objectsForThread.get(currentSelectedElement));
    }

    public String copySelectedStacktrace() {
        if (currentSelectedElement < 0) {
            return null;
        }
        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
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
        zoomAndPanDelegate.fitZoom(new Rectangle.Double(0, 0, maxRightOffset, maxBottomOffset), 0, ZoomAndPanDelegate.VerticalAlign.NONE);
        repaint();
    }

    public void fitSelectedElement() {
        ProfileRectangle rectangle = null;
        if (foundItems.size() > 0) {
            rectangle = foundItems.get(currentFocusedFoundElement);
        } else if (currentSelectedElement >= 0) {
            List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
            rectangle = objectsForThread.get(currentSelectedElement);
        }

        if (rectangle == null) {
            return;
        }

        zoomAndPanDelegate.fitZoom(rectangle, FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED);
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
        int newFontSize = changeFontSize(1);
        levelHeight = newFontSize + 3;
        updateData();
        repaint();
    }

    public void decreaseFontSize() {
        int newFontSize = changeFontSize(-1);
        levelHeight = newFontSize + 3;
        updateData();
        repaint();
    }

    private int changeFontSize(int value) {
        int newFontSize = settings.getIntValueOrDefault(SETTINGS_CELL_FONT_SIZE, DEFAULT_CELL_FONT_SIZE) + value;
        if (newFontSize < 4) {
            return 4;
        }
        settings.setIntValue(SETTINGS_CELL_FONT_SIZE, newFontSize);
        return newFontSize;
    }

    /**
     * Select element and focus.
     */
    public void selectProfileData(ProfileData selectedElement) {
        ProfileRectangle selectedRectangle = createProfileRectangle(selectedElement);
        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
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
        List<ProfileRectangle> objectsForThread = objects.getOrDefault(currentThreadId, Collections.emptyList());
        return objectsForThread.get(currentSelectedElement);
    }

    /**
     * Update {@param selectedBookmark} bookmark
     */
    public void updateBookmark(BookmarksRectangle selectedBookmark, BookMarkInfo result) {
        bookmarks.updateBookmark(selectedBookmark, result);
        repaint();
    }

    public interface FoundInfoListener {
        void onFound(int count, int selectedIndex);

        void onNotFound(String text, boolean ignoreCase);
    }

    public class ProfilerPanelData {
        public final List<ProfileRectangle> profileData;
        public final List<BookmarksRectangle> markersData;

        public ProfilerPanelData(List<ProfileRectangle> profileData, List<BookmarksRectangle> markersData) {
            this.profileData = profileData;
            this.markersData = markersData;
        }
    }

    public interface OnBookmarkClickListener {
        /**
         * Is called when right-clicked on bookmark.
         */
        void onBookmarkClicked(int x, int y, BookmarksRectangle bookmark);
    }
}
