package com.github.grishberg.profiler.chart.flame;

import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class FlameChartZoomAndPanDelegate implements MouseListener, MouseMotionListener, MouseWheelListener {
    private static final int MOUSE_GAP = 2;
    private static final double KEYBOARD_ZOOM_MULTIPLICATION_FACTOR = 1.5;
    private static final int MOVE_BY_KEYBOARD_OFFSET = 50;
    private static final int VERTICAL_MOVE_BY_KEYBOARD_OFFSET = 20;
    private static final double ZOOM_FACTOR = 0.1;
    private static final double MINIMUM_SCREEN_WIDTH = 0.001;

    public enum VerticalAlign {
        NONE,
        ENABLED
    }

    private final Component targetComponent;

    private Point dragStartScreen;
    private Point dragEndScreen;
    private Point clickStartScreen = new Point();
    private AffineTransform coordTransform = new AffineTransform();
    private MouseEventsListener mouseEventsListener = MouseEventsListener.STUB;
    private final Rectangle visibleScreenBounds = new Rectangle();

    private ScrollBoundsStrategy boundsStrategy;
    private Point2D dataRightBottomCorner = new Point2D.Double();

    public FlameChartZoomAndPanDelegate(
            Component targetComponent,
            int topOffset,
            ScrollBoundsStrategy boundsStrategy) {
        this.targetComponent = targetComponent;
        this.boundsStrategy = boundsStrategy;
        visibleScreenBounds.setRect(0, topOffset, targetComponent.getWidth(), targetComponent.getHeight());
        targetComponent.addMouseListener(this);
        targetComponent.addMouseMotionListener(this);
        targetComponent.addMouseWheelListener(this);
    }

    public void updateRightBottomCorner(double maxX, double maxY) {
        dataRightBottomCorner.setLocation(maxX, maxY);
    }

    public void setMouseEventsListener(MouseEventsListener l) {
        mouseEventsListener = l;
    }

    public void setTransform(AffineTransform transform) {
        this.coordTransform = transform;
        try {
            Point2D.Float leftTop = transformPoint(visibleScreenBounds.getLocation());
            transform.translate(leftTop.x, leftTop.y);
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        dragStartScreen = e.getPoint();
        clickStartScreen = dragStartScreen;
        dragEndScreen = null;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int dx = Math.abs(e.getX() - clickStartScreen.x);
        int dy = Math.abs(e.getY() - clickStartScreen.y);
        clickStartScreen = e.getPoint();
        if (MOUSE_GAP > Math.max(dx, dy)) {
            Point point = e.getPoint();
            try {
                Point2D.Float transformedPoint = transformPoint(point);

                if (isCtrlShiftPressed(e)) {
                    mouseEventsListener.onControlShiftMouseClicked(point, transformedPoint.x, transformedPoint.y);
                } else if (isCtrlPressed(e)) {
                    mouseEventsListener.onControlMouseClicked(point, transformedPoint.x, transformedPoint.y);
                } else {
                    mouseEventsListener.onMouseClicked(point, transformedPoint.x, transformedPoint.y);
                }
            } catch (NoninvertibleTransformException ex) {
                ex.printStackTrace();
            }
        }
        targetComponent.repaint();
    }

    private boolean isCtrlShiftPressed(MouseEvent e) {
        return isShiftPressed(e) && isCtrlPressed(e);
    }

    private boolean isCtrlPressed(MouseEvent e) {
        return (e.getModifiersEx() & 256) > 0;
    }

    private boolean isShiftPressed(MouseEvent e) {
        return (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) > 0;
    }

    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        mouseEventsListener.onMouseExited();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Point point = e.getPoint();
        try {
            Point2D.Float transformedPoint = transformPoint(point);
            mouseEventsListener.onMouseMove(point, transformedPoint.x, transformedPoint.y);
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        moveCamera(e);
    }

    private void moveCamera(MouseEvent e) {
        try {

            dragEndScreen = e.getPoint();
            Point2D.Float dragStart = transformPoint(dragStartScreen);
            Point2D.Float dragEnd = transformPoint(dragEndScreen);

            double dx = dragEnd.getX() - dragStart.getX();
            double dy = dragEnd.getY() - dragStart.getY();

            translateCamera(dx, dy);
            dragStartScreen = dragEndScreen;
            dragEndScreen = null;
            targetComponent.repaint();
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        zoomCamera(e);
    }

    private void zoomCamera(MouseWheelEvent e) {
        try {
            double wheelRotation = e.getPreciseWheelRotation();
            Point p = e.getPoint();
            if (wheelRotation > 0.0f) {
                zoomIn(p, 1 + Math.abs(wheelRotation) * ZOOM_FACTOR);
            } else {
                zoomOut(p, 1 + Math.abs(wheelRotation) * ZOOM_FACTOR);
            }
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    private void zoomOut(Point p, double zoomMultiplicationFactor) throws NoninvertibleTransformException {
        Point2D p1 = transformPoint(p);
        double scaledScreenSize = targetComponent.getWidth() / coordTransform.getScaleX() * zoomMultiplicationFactor;
        if (scaledScreenSize <= MINIMUM_SCREEN_WIDTH) {
            return;
        }
        coordTransform.scale(zoomMultiplicationFactor, 1);
        Point2D p2 = transformPoint(p);
        coordTransform.translate(p2.getX() - p1.getX(), p2.getY() - p1.getY());
        targetComponent.repaint();
    }

    private void zoomIn(Point p, double zoomMultiplicationFactor) throws NoninvertibleTransformException {
        Point2D p1 = transformPoint(p);
        double factor = 1 / zoomMultiplicationFactor;
        coordTransform.scale(factor, 1 /*/ zoomMultiplicationFactor*/);
        Point2D p2 = transformPoint(p);

        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        translateCamera(dx, dy);
        targetComponent.repaint();

    }

    public Point2D.Float transformPoint(Point p1) throws NoninvertibleTransformException {
//        System.out.println("Model -> Screen Transformation:");
//        showMatrix(coordTransform);
        AffineTransform inverse = coordTransform.createInverse();
//        System.out.println("Screen -> Model Transformation:");
//        showMatrix(inverse);

        Point2D.Float p2 = new Point2D.Float();
        inverse.transform(p1, p2);
        return p2;
    }

    public Rectangle2D.Double transformRectangle(Rectangle2D rectSrc) throws NoninvertibleTransformException {
        AffineTransform inverse = coordTransform.createInverse();

        Point2D.Double leftTop = new Point2D.Double();
        Point2D.Double rightBottom = new Point2D.Double();
        inverse.transform(new Point2D.Double(rectSrc.getX(), rectSrc.getY()), leftTop);
        inverse.transform(new Point2D.Double(rectSrc.getMaxX(), rectSrc.getMaxY()), rightBottom);
        return new Rectangle2D.Double(leftTop.x, leftTop.y, rightBottom.x - leftTop.x, rightBottom.y - leftTop.y);
    }

    public Point2D.Float transformPoint(Point2D.Double p1) throws NoninvertibleTransformException {
        AffineTransform inverse = coordTransform.createInverse();

        Point2D.Float p2 = new Point2D.Float();
        inverse.transform(p1, p2);
        return p2;
    }

    public double getScaleX(double x) {
        return x * coordTransform.getScaleX();
    }

    @NotNull
    public AffineTransform getTransform() {
        return coordTransform;
    }

    public void scrollUp() {
        moveCameraVertical(VERTICAL_MOVE_BY_KEYBOARD_OFFSET);
    }

    public void scrollDown() {
        moveCameraVertical(-MOVE_BY_KEYBOARD_OFFSET);
    }

    public void scrollLeft() {
        moveCameraHorizontal(-MOVE_BY_KEYBOARD_OFFSET);
    }

    public void scrollRight() {
        moveCameraHorizontal(MOVE_BY_KEYBOARD_OFFSET);
    }

    public void scrollTo(double offsetX) {
        Rectangle2D.Double rect = new Rectangle2D.Double(offsetX, 0, 0, 0);
        navigateToRectangle(rect, VerticalAlign.NONE);
    }

    public void zoomOut() {
        Point center = new Point(targetComponent.getWidth() / 2, targetComponent.getHeight() / 2);

        try {
            zoomOut(center, KEYBOARD_ZOOM_MULTIPLICATION_FACTOR);
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    public void zoomIn() {
        Point center = new Point(targetComponent.getWidth() / 2, targetComponent.getHeight() / 2);

        try {
            zoomIn(center, KEYBOARD_ZOOM_MULTIPLICATION_FACTOR);
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    private void moveCameraHorizontal(int dx) {
        try {
            Point dragEndScreen = new Point(dx, 0);
            visibleScreenBounds.setRect(visibleScreenBounds.x, visibleScreenBounds.y,
                    targetComponent.getWidth() - visibleScreenBounds.x,
                    targetComponent.getHeight() - visibleScreenBounds.y);
            Rectangle2D.Double transformedScreenBounds = transformRectangle(visibleScreenBounds);

            Point2D.Double dragStart = new Point2D.Double(transformedScreenBounds.x, transformedScreenBounds.y);
            Point2D.Float dragEnd = transformPoint(dragEndScreen);
            double tdx = dragEnd.getX() - dragStart.getX();

            Point2D delta = boundsStrategy.correctOffset(tdx, 0, transformedScreenBounds, dataRightBottomCorner);
            coordTransform.translate(delta.getX(), 0);
            targetComponent.repaint();
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    private void moveCameraVertical(int dy) {
        try {
            Point targetPoint = new Point(0, dy);
            Point2D.Float currentPoint = transformPoint(new Point(0, 0));
            Point2D.Float transformedTargetPoint = transformPoint(targetPoint);
            double tdy = transformedTargetPoint.getY() - currentPoint.getY();

            translateCamera(0, tdy);

            targetComponent.repaint();
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    public void fitZoom(Rectangle.Double bounds, int sidePadding, VerticalAlign verticalAlign) {
        double screenTransformedWidth = ((double) (targetComponent.getWidth() - 2 * sidePadding)) / coordTransform.getScaleX();
        double scaleFactor = (screenTransformedWidth) / (bounds.width == 0 ? 0.001 : bounds.width);

        double scaledScreenSize = targetComponent.getWidth() / coordTransform.getScaleX() / scaleFactor;
        if (scaledScreenSize <= MINIMUM_SCREEN_WIDTH) {
            scaleFactor = screenTransformedWidth / MINIMUM_SCREEN_WIDTH;
        }
        coordTransform.scale(scaleFactor, 1);

        navigateToRectangle(bounds, verticalAlign);
    }

    public void navigateToRectangle(Rectangle2D bounds, VerticalAlign align) {
        try {
            Point.Double targetElementCenter = new Point.Double(bounds.getCenterX(), bounds.getCenterY());
            Point.Double screenCenter = new Point.Double(targetComponent.getWidth() / 2.0, targetComponent.getHeight() / 2.0);
            Point2D.Float transformedToScreenCenter = transformPoint(screenCenter);
            double tdx = transformedToScreenCenter.getX() - targetElementCenter.getX();
            double tdy = boundsStrategy.calculateNavigationDy(getTransformedScreen(), bounds);

            if (align == VerticalAlign.NONE) {
                tdy = 0;
            }
            translateCamera(tdx, tdy);
            targetComponent.repaint();
        } catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
    }

    private void translateCamera(double tdx, double tdy) throws NoninvertibleTransformException {
        Rectangle2D transformedScreenBounds = getTransformedScreen();
        Point2D delta = boundsStrategy.correctOffset(tdx, tdy, transformedScreenBounds, dataRightBottomCorner);
        coordTransform.translate(delta.getX(), delta.getY());
    }

    private Rectangle2D getTransformedScreen() throws NoninvertibleTransformException {
        visibleScreenBounds.setRect(visibleScreenBounds.x, visibleScreenBounds.y,
                targetComponent.getWidth() - visibleScreenBounds.x,
                targetComponent.getHeight() - visibleScreenBounds.y);
        return transformRectangle(visibleScreenBounds);
    }

    public void resetZoom() {
        coordTransform = new AffineTransform();
        setTransform(coordTransform);
    }

    public static class LeftBottomBounds implements ScrollBoundsStrategy {
        private final Point2D point2D = new Point2D.Double();

        @Override
        public Point2D correctOffset(double dx, double dy, Rectangle2D transformedScreenBounds, Point2D dataRightBottom) {
            double dataBottomDy = transformedScreenBounds.getHeight() - dataRightBottom.getY();
            if (dx > 0) { // pane right
                dx = Math.min(dx, transformedScreenBounds.getMinX());
            }
            if (dy < 0) { // pane up
                dy = Math.max(dy, dataBottomDy);
            }
            point2D.setLocation(dx, dy);
            return point2D;
        }

        @Override
        public double calculateNavigationDy(Rectangle2D transformedScreen, Rectangle2D targetElement) {
            return transformedScreen.getHeight() - targetElement.getMaxY();
        }
    }

    public interface ScrollBoundsStrategy {
        Point2D correctOffset(double dx, double dy, Rectangle2D transformedScreenBounds, Point2D dataRightBottom);

        double calculateNavigationDy(Rectangle2D transformedScreenBounds, Rectangle2D targetElement);
    }

    public interface MouseEventsListener {
        void onMouseClicked(Point point, float x, float y);

        void onMouseMove(Point point, float x, float y);

        void onMouseExited();

        void onControlMouseClicked(Point point, float x, float y);

        void onControlShiftMouseClicked(Point point, float x, float y);

        MouseEventsListener STUB = new MouseEventsListener() {
            @Override
            public void onMouseClicked(Point point, float x, float y) { /* stub */ }

            @Override
            public void onMouseMove(Point point, float x, float y) { /* stub */ }

            @Override
            public void onMouseExited() { /* stub */ }

            @Override
            public void onControlMouseClicked(Point point, float x, float y) {
                /* stub */
            }

            @Override
            public void onControlShiftMouseClicked(Point point, float x, float y) {
                /* stub */
            }
        };
    }
}
