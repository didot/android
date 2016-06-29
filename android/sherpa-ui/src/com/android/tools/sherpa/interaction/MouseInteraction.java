/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.sherpa.interaction;

import com.android.tools.sherpa.animation.AnimatedDestroyCircle;
import com.android.tools.sherpa.animation.AnimatedDestroyLine;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.ColorTheme;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import android.support.constraint.solver.widgets.*;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Encapsulate the mouse interactions
 */
public class MouseInteraction {

    // Used as a margin value
    private static int sMargin = 8;

    private boolean mIsControlDown;
    private boolean mIsShiftDown;
    private boolean mIsAltDown;
    private boolean mMouseDown = false;
    private boolean mMoveOnlyMode = false;
    private final ViewTransform mViewTransform;
    private final WidgetsScene mWidgetsScene;
    private final SceneDraw mSceneDraw;
    private final Selection mSelection;
    private final WidgetMotion mWidgetMotion;
    private final WidgetResize mWidgetResize;

    // Points used for the selection / dragging of widgets

    private Point mStartPoint = new Point();
    private Point mLastMousePosition = new Point();

    private Snapshot mSnapshot = null;

    private boolean mUseDefinedMargin = true;
    private boolean mAutoConnect = true;
    private int mMouseCursor = Cursor.DEFAULT_CURSOR;
    private ConstraintWidget mPreviousHoverWidget = null;

    private long mPressTime = 0;

    private final int SELECTION_TARGET_SLOPE = 4;

    // After that delay, prevent delete anchor
    private final static int LONG_PRESS_THRESHOLD = 500; // ms

    // After that delay, allow baseline selection
    private final static int BASELINE_TIME_THRESHOLD = 800; // ms

    public static void setMargin(int margin) {
        sMargin = margin;
    }

    // Represent the different mouse interaction modes
    enum MouseMode {
        INACTIVE, SELECT, RESIZE, MOVE, CONNECT
    }

    private MouseMode mMouseMode = MouseMode.SELECT;

    /**
     * Base constructor
     *
     * @param transform    the view transform
     * @param widgetsScene
     * @param selection
     * @param widgetMotion
     * @param widgetResize
     * @param sceneDraw
     * @param previous     get parameter from previous
     */
    public MouseInteraction(ViewTransform transform,
            WidgetsScene widgetsScene, Selection selection,
            WidgetMotion widgetMotion, WidgetResize widgetResize,
            SceneDraw sceneDraw, MouseInteraction previous) {
        mViewTransform = transform;
        mWidgetsScene = widgetsScene;
        mSelection = selection;
        mWidgetMotion = widgetMotion;
        mWidgetResize = widgetResize;
        mSceneDraw = sceneDraw;

        if (previous != null) { // copy setting from previous mouse interaction
            mMoveOnlyMode = previous.mMoveOnlyMode;
            mAutoConnect = previous.mAutoConnect;
            mUseDefinedMargin = previous.mUseDefinedMargin;
        }
    }

    /*-----------------------------------------------------------------------*/
    // Accessors
    /*-----------------------------------------------------------------------*/

    /**
     * Return the current snap candidates from widget motion
     * @return current list of snap candidates
     */
    public ArrayList<SnapCandidate> getSnapCandidates() {
        return mWidgetMotion.getSnapCandidates();
    }

    /**
     * Accessor for the start mouse point
     *
     * @return start point
     */
    public Point getStartPoint() {
        return mStartPoint;
    }

    /**
     * Accessor for the last mouse point
     *
     * @return last point
     */
    public Point getLastPoint() {
        return mLastMousePosition;
    }

    /**
     * Accessor for control down check
     *
     * @return true if control is currently pressed
     */
    public boolean isControlDown() {
        return mIsControlDown;
    }

    /**
     * Setter for control down
     *
     * @param value
     */
    public void setIsControlDown(boolean value) {
        mIsControlDown = value;
    }

    /**
     * Accessor for shift down check
     *
     * @return true if shift is currently pressed
     */
    public boolean isShiftDown() {
        return mIsShiftDown;
    }

    /**
     * Setter for shift down
     *
     * @param value
     */
    public void setIsShiftDown(boolean value) {
        mIsShiftDown = value;
    }

    /**
     * Accessor for alt down check
     *
     * @return true if alt is currently pressed
     */
    public boolean isAltDown() {
        return mIsAltDown;
    }

    /**
     * Setter for alt down
     *
     * @param value
     */
    public void setIsAltDown(boolean value) {
        mIsAltDown = value;
    }

    /**
     * Accessor for mouse down check
     *
     * @return true if mouse is currently down (i.e. we are interacting)
     */
    public boolean isMouseDown() {
        return mMouseDown;
    }

    /**
     * Setter for the snapshot of the selected widget
     *
     * @param snapshot
     */
    public void setSnapshot(Snapshot snapshot) {
        mSnapshot = snapshot;
    }

    /**
     * Getter for the snapshot of the selected widget
     *
     * @return
     */
    public Snapshot getSnapshot() {
        return mSnapshot;
    }

    /**
     * Setter for the start point (needed to reposition the mouse point on anchor click)
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void setStartPoint(int x, int y) {
        mStartPoint.setLocation(x, y);
    }

    /**
     * Getter returning true if we'll use the defined margin value, false if we'll use
     * the current distance between anchors as a margin.
     *
     * @return true if we'll use the defined margin value
     */
    public boolean isUseDefinedMargin() {
        return mUseDefinedMargin;
    }

    /**
     * Setter for deciding to use or not the defined margin value. Pass true to use it,
     * false to use instead the current distance between anchors as a margin when making a connection.
     *
     * @param useDefinedMargin
     */
    public void setUseDefinedMargin(boolean useDefinedMargin) {
        mUseDefinedMargin = useDefinedMargin;
    }

    /**
     * Getter returning true if we are in auto connect mode
     *
     * @return true if doing automatic connections
     */
    public boolean isAutoConnect() {
        return mAutoConnect;
    }

    /**
     * Setter for deciding to automatically connect elements when dragging them
     *
     * @param autoConnect set to true to automatically connect, false otherwise
     */
    public void setAutoConnect(boolean autoConnect) {
        mAutoConnect = autoConnect;
    }

    /**
     * Getter for the current mouse cursor
     *
     * @return mouse cursor type
     */
    public int getMouseCursor() {
        return mMouseCursor;
    }

    /**
     * Clear indicators (snap guidelines, similar margins...)
     */
    public void clearIndicators() {
        mWidgetMotion.getSnapCandidates().clear();
        mWidgetMotion.getSimilarMargins().clear();
    }

    /*-----------------------------------------------------------------------*/
    // Mouse handling
    /*-----------------------------------------------------------------------*/

    private HitListener mHoverListener = new HitListener(HitListener.HOVER_MODE);
    private HitListener mClickListener = new HitListener(HitListener.CLICK_MODE);
    private HitListener mDragListener = new HitListener(HitListener.DRAG_MODE);

    private Timer mBaselineTimer = new Timer(BASELINE_TIME_THRESHOLD, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            mClickListener.mEnableBaseline = true;
            mHoverListener.mEnableBaseline = true;
            updateFromHoverListener(mHoverListener.mLastX, mHoverListener.mLastY);
            mBaselineTimer.stop();
        }
    });

    /**
     * Utility function to retrieve a decorator
     *
     * @param widget the widget we look at
     * @return the decorator of the widget
     */
    private WidgetDecorator getDecorator(ConstraintWidget widget) {
        WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
        WidgetDecorator decorator = companion.getWidgetDecorator(mSceneDraw.getCurrentStyle());
        return decorator;
    }

    /**
     * Internal helper class handling mouse hits on the various elements
     */
    class HitListener implements DrawPicker.HitElementListener {
        private DrawPicker mPicker = new DrawPicker();

        private ConstraintWidget mHitWidget = null;
        private double mHitWidgetDistance = 0;
        private ConstraintHandle mHitConstraintHandle = null;
        private double mHitConstraintHandleDistance = 0;
        private ResizeHandle mHitResizeHandle = null;
        private double mHitResizeHandleDistance = 0;
        private WidgetDecorator.WidgetAction mHitWidgetAction = null;
        private double mHitWidgetActionDistance = 0;
        public boolean mEnableBaseline = false;

        static final int CLICK_MODE = 0;
        static final int HOVER_MODE = 1;
        static final int DRAG_MODE = 2;

        private final int mMode;

        private int mLastX;
        private int mLastY;

        public HitListener(int mode) {
            mMode = mode;
            mPicker.setSelectListener(this);
        }

        public void reset() {
            mPicker.reset();
            clearSelection();
        }

        public void clearSelection() {
            if (mHitWidget != null) {
                getDecorator(mHitWidget).over(false);
            }
            mHitWidget = null;
            mHitWidgetDistance = Double.MAX_VALUE;
            mHitConstraintHandle = null;
            mHitConstraintHandleDistance = Double.MAX_VALUE;
            mHitResizeHandle = null;
            mHitResizeHandleDistance = Double.MAX_VALUE;
            if (mHitWidgetAction != null) {
                mHitWidgetAction.over(false);
            }
            mHitWidgetAction = null;
            mHitWidgetActionDistance = Double.MAX_VALUE;
        }

        public void populate() {
            reset();
            Collection<ConstraintWidget> widgets = mWidgetsScene.getWidgets();
            for (ConstraintWidget widget : widgets) {
                if ((widget.isRoot() || widget.isRootContainer()) && mMode != DRAG_MODE && mMode != CLICK_MODE) {
                    continue;
                }
                addWidgetToPicker(widget, mPicker);
            }
        }

        public ConstraintAnchor getConstraintAnchor() {
            if (mHitConstraintHandle == null) {
                return null;
            }
            return mHitConstraintHandle.getAnchor();
        }

        public WidgetDecorator.WidgetAction getWidgetAction() {
            return mHitWidgetAction;
        }

        @Override
        public void over(Object over, double dist) {
            if (over instanceof ConstraintWidget) {
                ConstraintWidget widget = (ConstraintWidget) over;
                if ((mHitWidget == null) || (mHitWidgetDistance >= dist)) {
                    if (mHitWidget != null) {
                        getDecorator(mHitWidget).over(false);
                    }
                    if (dist == 0) {
                        // we should select children preferably
                        if (mHitWidget != null && !widget.hasAncestor(mHitWidget)) {
                            // not a children, keep the current one
                            return;
                        }
                    }
                    mHitWidget = widget;
                    mHitWidgetDistance = dist;
                    if (dist == 0) {
                        getDecorator(mHitWidget).over(true);
                    }
                }
            } else if (over instanceof ConstraintHandle) {
                ConstraintHandle handle = (ConstraintHandle) over;
                if ((mHitConstraintHandle == null)
                    || (mHitConstraintHandleDistance >= dist)
                    || dist < SELECTION_TARGET_SLOPE) {
                    if ((mHitConstraintHandle != null)
                            && (mHitConstraintHandleDistance < SELECTION_TARGET_SLOPE)
                            && mSelection.contains(mHitConstraintHandle.getOwner())
                            && !mSelection.contains(handle.getOwner())) {
                        // If we hit something that's closer to the current best hit,
                        // but the current best hit belongs to a selected widget,
                        // while the new hit doesn't, keep the current best hit.
                        return;
                    }
                    if (mMode != DRAG_MODE && handle.getOwner() instanceof Guideline) {
                        // For now, we do not want to be able to drag the left or top anchor that
                        // a Guideline has, as we do not allow connections of this anchor...
                        // TODO: revisit in the future, it might be better to unify Guidelines connections
                        return;
                    }
                    if (dist < SELECTION_TARGET_SLOPE && mHitConstraintHandle != null) {
                        // we should select children preferably
                        ConstraintWidget currentWidget = mHitConstraintHandle.getOwner();
                        ConstraintWidget candidateWidget = handle.getOwner();
                        if (!candidateWidget.hasAncestor(currentWidget)) {
                            // not a children, keep the current one
                            return;
                        } else {
                            ConstraintWidget parent = candidateWidget.getParent();
                            if (!(parent instanceof ConstraintWidgetContainer)) {
                                // candidate is inside a non-ConstraintWidgetContainer, keep
                                // the current one
                                return;
                            }
                        }
                    }
                    if (handle.getAnchor().getType() == ConstraintAnchor.Type.BASELINE) {
                        if (mEnableBaseline || mMode == DRAG_MODE) {
                            if (dist < SELECTION_TARGET_SLOPE) {
                                mHitConstraintHandle = handle;
                                mHitConstraintHandleDistance = dist;
                            }
                        }
                    } else {
                        mHitConstraintHandle = handle;
                        mHitConstraintHandleDistance = dist;
                    }
                }
            } else if (over instanceof ResizeHandle) {
                ResizeHandle handle = (ResizeHandle) over;
                if ((mHitResizeHandle == null)
                    || (mHitResizeHandleDistance > dist)
                    || dist < SELECTION_TARGET_SLOPE) {
                    if ((mHitResizeHandle != null)
                            && (mHitResizeHandleDistance < SELECTION_TARGET_SLOPE)
                            && mSelection.contains(mHitResizeHandle.getOwner())
                            && !mSelection.contains(handle.getOwner())) {
                        // If we hit something that's closer to the current best hit,
                        // but the current best hit belongs to a selected widget,
                        // while the new hit doesn't, keep the current best hit.
                        return;
                    }
                    mHitResizeHandle = handle;
                    mHitResizeHandleDistance = dist;
                }
            } else if (over instanceof WidgetDecorator.WidgetAction) {
                if ((mHitWidgetAction == null) || (mHitWidgetActionDistance > dist)) {
                    WidgetDecorator.WidgetAction action = (WidgetDecorator.WidgetAction) over;
                    if (mHitWidgetAction != null) {
                        mHitWidgetAction.over(false);
                    }
                    mHitWidgetAction = action;
                    mHitWidgetActionDistance = dist;
                    if (dist == 0) {
                        mHitWidgetAction.over(true);
                    }
                }
            }
        }

        private void addWidgetToPicker(ConstraintWidget widget, DrawPicker picker) {
            int l = mViewTransform.getSwingX(widget.getDrawX());
            int t = mViewTransform.getSwingY(widget.getDrawY());
            int r = l + mViewTransform.getSwingDimension(widget.getDrawWidth());
            int b = t + mViewTransform.getSwingDimension(widget.getDrawHeight());
            int widgetSelectionMargin = 8;
            int handleSelectionMargin = 8;
            if (widget instanceof Guideline) {
                Guideline guideline = (Guideline)widget;
                if (guideline.getOrientation() == Guideline.HORIZONTAL) {
                    picker.addLine(guideline, widgetSelectionMargin, l, t, r, t);
                } else {
                    picker.addLine(guideline, widgetSelectionMargin, l, t, l, b);
                }
                if (mSelection.contains(guideline) && mMode == DRAG_MODE) {
                    return;
                }
            }
            picker.addRect(widget, widgetSelectionMargin, l, t, r, b);
            WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetInteractionTargets targets = companion.getWidgetInteractionTargets();
            if (mMode == HOVER_MODE && !mSelection.contains(widget)) {
                return;
            }
            for (ConstraintHandle handle : targets.getConstraintHandles()) {
                int x = mViewTransform.getSwingX(handle.getDrawX());
                int y = mViewTransform.getSwingY(handle.getDrawY());
                ConstraintAnchor.Type type = handle.getAnchor().getType();
                if (type == ConstraintAnchor.Type.CENTER
                        || type == ConstraintAnchor.Type.CENTER_X
                        || type == ConstraintAnchor.Type.CENTER_Y) {
                    continue;
                }
                if (type == ConstraintAnchor.Type.BASELINE) {
                    if (widget.getBaselineDistance() > 0) {
                        picker.addLine(handle, handleSelectionMargin, l, y, r, y);
                    }
                } else {
                    if (mMode == DRAG_MODE) {
                        switch (type) {
                            case LEFT: {
                                picker.addLine(handle, handleSelectionMargin, l, t, l, b);
                            }
                            break;
                            case RIGHT: {
                                picker.addLine(handle, handleSelectionMargin, r, t, r, b);
                            }
                            break;
                            case TOP: {
                                picker.addLine(handle, handleSelectionMargin, l, t, r, t);
                            }
                            break;
                            case BOTTOM: {
                                picker.addLine(handle, handleSelectionMargin, l, b, r, b);
                            }
                            break;
                            default: {
                                picker.addPoint(handle, handleSelectionMargin, x, y);
                            }
                        }
                    } else {
                        picker.addPoint(handle, handleSelectionMargin, x, y);
                    }
                }
            }
            for (ResizeHandle handle : targets.getResizeHandles()) {
                java.awt.Rectangle bounds = handle.getBounds();
                int x = mViewTransform.getSwingFX((float) bounds.getCenterX());
                int y = mViewTransform.getSwingFY((float) bounds.getCenterY());
                picker.addPoint(handle, handleSelectionMargin, x, y);
            }
            WidgetDecorator decorator = companion.getWidgetDecorator(mSceneDraw.getCurrentStyle());
            for (WidgetDecorator.WidgetAction action : decorator.getWidgetActions()) {
                action.addToPicker(mViewTransform, picker);
            }
        }

        public void find(int x, int y) {
            mPicker.find(x, y);
            mLastX = x;
            mLastY = y;
        }
    }

    /**
     * Mouse press handling
     *
     * @param x            mouse x coordinate
     * @param y            mouse y coordinate
     * @param isRightClick
     */
    public void mousePressed(float x, float y, boolean isRightClick) {
        if (isRightClick) {
            mMouseMode = MouseMode.INACTIVE;
            return;
        }
        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
            getDecorator(w).setShowActions(false);
        }
        mPressTime = System.currentTimeMillis();
        Animator.setAnimationEnabled(true);
        mMouseDown = true;
        mStartPoint.setLocation(x, y);
        mLastMousePosition.setLocation(x, y);

        mSelection.setConnectionCandidateAnchor(null);
        mSelection.setSelectedAnchor(null);
        mSelection.setSelectedGuideline(null);
        mSelection.setLastConnectedAnchor(null);

        mMouseMode = MouseMode.SELECT;

        mWidgetsScene.updatePositions(mViewTransform);

        // First, let's populate the draw picker
        mClickListener.populate();
        mClickListener.find(mViewTransform.getSwingFX(x), mViewTransform.getSwingFY(y));

        // check for widget, anchors, resize handle hits

        ConstraintWidget widget = mClickListener.mHitWidget;
        ConstraintAnchor anchor = mClickListener.getConstraintAnchor();
        ResizeHandle resizeHandle = mClickListener.mHitResizeHandle;

        // give a chance to widgets to respond to a mouse press even if out of bounds
        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
            ConstraintWidget widgetHit =
                    getDecorator(w).mousePressed(x, y, mViewTransform, mSelection);
            if (widgetHit != null && widget == null) {
                widget = widgetHit;
            }
        }

        WidgetDecorator.WidgetAction widgetAction = mClickListener.getWidgetAction();
        if (widgetAction != null && widgetAction.isVisible()) {
            widget = widgetAction.getWidget();
            if (widgetAction.click()) {
                mSelection.addModifiedWidget(widgetAction.getWidget());
            }
        }

        // don't allow direct interactions with root
        if (widget != null && (widget.isRoot() || widget.isRootContainer())) {
            widget = null;
        }

        if (!isAltDown() ^ mMoveOnlyMode) { // alt down only accept moving
            if (anchor != null) {
                widget = anchor.getOwner();
                if (mSelection.contains(widget)) {
                    ConstraintHandle handle = WidgetInteractionTargets.constraintHandle(anchor);
                    setStartPoint(handle.getDrawX(), handle.getDrawY());
                    mSelection.clear();
                    mSelection.add(widget);
                    mSelection.setSelectedAnchor(anchor);
                    mMouseMode = MouseMode.CONNECT;
                }
            } else if (resizeHandle != null) {
                widget = resizeHandle.getOwner();
                if (mSelection.contains(widget)) {
                    mSelection.clear();
                    mSelection.add(widget);
                    mSelection.setSelectedResizeHandle(resizeHandle);
                    mMouseMode = MouseMode.RESIZE;
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////
        // let's check for guidelines...
        // TODO: switch to the WidgetDecorator model
        ///////////////////////////////////////////////////////////////////////
        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
            // Check if we hit a guideline head
            if (w instanceof Guideline) {
                Guideline guideline = (Guideline) w;
                Rectangle head = guideline.getHead();
                if (head.contains((int) x, (int) y)) {
                    mSelection.setSelectedGuideline(guideline);
                    break;
                }
            }
        }

        if (mSelection.getSelectedGuideline() != null) {
            mSelection.clear();
            mSelection.setSelectedResizeHandle(resizeHandle);
            mSelection.add(mSelection.getSelectedGuideline());
            widget = mSelection.getSelectedGuideline();
            if (mMouseMode == MouseMode.SELECT) {
                mMouseMode = MouseMode.MOVE;
            }
        }
        ///////////////////////////////////////////////////////////////////////

        if (widget == null) {
            // clear the selection as no widget were found
            mSelection.clear();
        }

        if (mSelection.getSelectedAnchor() != null) {
            mSelection.setSelectedAnchorInitialTarget(
                    mSelection.getSelectedAnchor().getTarget());
        }

        mSelection.updatePosition();

        // if the selection is multiple, compute the bounds
        mSelection.createBounds();

        if (mSelection.hasSingleElement()) {
            setSnapshot(new Snapshot(mSelection.getFirstElement().widget));
        } else {
            setSnapshot(null);
        }

        mSceneDraw.setCurrentUnderneathAnchor(mSelection.getSelectedAnchor());
        mSceneDraw.onMousePress(mSelection.getSelectedAnchor());

        mBaselineTimer.stop();
    }

    /**
     * Mouse release handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     */
    public void mouseReleased(int x, int y) {
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            getDecorator(widget).setShowActions(true);
        }
        boolean longPress = false;
        if (System.currentTimeMillis() - mPressTime > LONG_PRESS_THRESHOLD) {
            longPress = true;
        }
        if (mMouseMode == MouseMode.INACTIVE) {
            return;
        }
        if (mAutoConnect) {
            // Auto-connect to candidates
            for (SnapCandidate candidate : mWidgetMotion.getSnapCandidates()) {
                if (!candidate.source.isConnectionAllowed(candidate.target.getOwner())) {
                    continue;
                }
                int margin = candidate.margin;
                if (candidate.padding != 0) {
                    margin = candidate.padding;
                }
                margin = Math.abs(margin);
                ConstraintWidget widget = candidate.source.getOwner();
                widget.connect(candidate.source, candidate.target, margin,
                        ConstraintAnchor.AUTO_CONSTRAINT_CREATOR);
                mSelection.addModifiedWidget(candidate.source.getOwner());
            }
            // For all modified widgets, start auto locking on the relevant anchors
            // (note: as e.g. center anchors are connected via an indirect mechanism,
            // it's safer to do it this way)
            for (ConstraintWidget widget : mSelection.getModifiedWidgets()) {
                for (ConstraintAnchor anchor : widget.getAnchors()) {
                    if (!anchor.isConnected()) {
                        continue;
                    }
                    if (anchor.getConnectionCreator() != ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                        continue;
                    }
                    WidgetDecorator.getConstraintHandle(anchor).startLock();
                }
            }
        }

        mWidgetMotion.mouseReleased();
        mWidgetResize.mouseReleased();
        mSceneDraw.mouseReleased();

        // First check anchors that are not guidelines, to deal with the case
        // where we want to delete the connection
        mClickListener.clearSelection();
        mClickListener.find(mViewTransform.getSwingFX(x), mViewTransform.getSwingFY(y));
        ConstraintAnchor anchor = mClickListener.getConstraintAnchor();

        if (mSelection.getSelectedAnchor() != null
                && mSelection.getConnectionCandidateAnchor() == null
                && anchor == mSelection.getSelectedAnchor() && !longPress) {
            // delete the anchor connection
            if (mSelection.getSelectedAnchor().isConnected()
                    && mSelection.getSelectedAnchor().getTarget()
                    == mSelection.getSelectedAnchorInitialTarget()) {
                ConstraintWidget widget = mSelection.getSelectedAnchor().getOwner();
                ConstraintAnchor selectedAnchor = mSelection.getSelectedAnchor();
                if (selectedAnchor.isVerticalAnchor()) {
                    widget.setVerticalBiasPercent(0.5f);
                } else {
                    widget.setHorizontalBiasPercent(0.5f);
                }
                widget.resetAnchor(selectedAnchor);
                ConstraintHandle selectedHandle =
                        WidgetInteractionTargets.constraintHandle(selectedAnchor);
                if (mSelection.getSelectedAnchor().getType() == ConstraintAnchor.Type.BASELINE) {
                    mSceneDraw.getChoreographer()
                            .addAnimation(new AnimatedDestroyLine(selectedHandle));
                } else {
                    mSceneDraw.getChoreographer().addAnimation(
                            new AnimatedDestroyCircle(selectedHandle));
                }
                mSelection.addModifiedWidget(widget);
            }
        }

        // If we hit a widget, update the selection
        ConstraintWidget widget = mClickListener.mHitWidget;
        if (widget != null) {
            if (mMouseMode == MouseMode.SELECT) {
                if (!mSelection.contains(widget)) {
                    // replace the current selection
                    if (!(isShiftDown() || isControlDown())) {
                        mSelection.clear();
                    }
                    mSelection.add(widget);
                } else if (isControlDown()) {
                    mSelection.remove(widget);
                }
            }
        }

        if (mSelection.isEmpty() && mSelection.getSelectedAnchor() == null) {
            int x1 = Math.min(getStartPoint().x, getLastPoint().x);
            int x2 = Math.max(getStartPoint().x, getLastPoint().x);
            int y1 = Math.min(getStartPoint().y, getLastPoint().y);
            int y2 = Math.max(getStartPoint().y, getLastPoint().y);
            Rectangle selectionRect = new Rectangle();
            selectionRect.setBounds(x1, y1, x2 - x1, y2 - y1);
            if (selectionRect.width > 0 && selectionRect.height > 0) {
                ArrayList<ConstraintWidget> selection = mWidgetsScene.findWidgets(
                        mWidgetsScene.getRoot(),
                        selectionRect.x, selectionRect.y,
                        selectionRect.width, selectionRect.height);
                for (ConstraintWidget w : selection) {
                    mSelection.add(w);
                }
            }
        }

        if (mSelection.getSelectedGuideline() != null) {
            Rectangle head = mSelection.getSelectedGuideline().getHead();
            if (head.contains(getStartPoint().x, getStartPoint().y)) {
                Selection.Element element = mSelection.get(mSelection.getSelectedGuideline());
                if (element != null) {
                    if (mSelection.getSelectedGuideline().getOrientation() ==
                            Guideline.VERTICAL) {
                        if (element.origin.x == mSelection.getSelectedGuideline().getDrawX()) {
                            mSelection.getSelectedGuideline().cyclePosition();
                            mSelection.addModifiedWidget(mSelection.getSelectedGuideline());
                        }
                    } else {
                        if (element.origin.y == mSelection.getSelectedGuideline().getDrawY()) {
                            mSelection.getSelectedGuideline().cyclePosition();
                            mSelection.addModifiedWidget(mSelection.getSelectedGuideline());
                        }
                    }
                }
            }
        }

        // give a chance to widgets to respond to a mouse press
        for (Selection.Element selection : mSelection.getElements()) {
            getDecorator(selection.widget).mouseRelease(x, y, mViewTransform, mSelection);
        }

        for (Selection.Element selection : mSelection.getElements()) {
            selection.directionLocked = Selection.DIRECTION_UNLOCKED;
        }

        mSceneDraw.setCurrentUnderneathAnchor(null);
        mMouseMode = MouseMode.INACTIVE;
        mSelection.setSelectedAnchor(null);
        mSelection.setSelectedResizeHandle(null);
        mSelection.setConnectionCandidateAnchor(null);
        mSelection.clearBounds();
        mSelection.selectionHasChanged(); // in case something did change...
        mLastMousePosition.setLocation(0, 0);
        mSnapshot = null;
        mMouseDown = false;
        Animator.setAnimationEnabled(true);
    }

    /**
     * Mouse dragged handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     * @return the type of direction (locked in x/y or not)
     */
    public int mouseDragged(int x, int y) {
        if (mMouseMode == MouseMode.SELECT) {
            ConstraintWidget widget = mClickListener.mHitWidget;
            if (widget != null && widget.getParent() instanceof ConstraintWidgetContainer) {
                if (!mSelection.contains(widget)) {
                    // replace the current selection
                    if (!(isShiftDown() || isControlDown())) {
                        mSelection.clear();
                    }
                    mSelection.add(widget);
                }
                else if (isControlDown()) {
                    mSelection.remove(widget);
                }
            }
            mMouseMode = MouseMode.MOVE;
        }
        int directionLockedStatus = Selection.DIRECTION_UNLOCKED;
        mLastMousePosition.setLocation(x, y);
        switch (mMouseMode) {
            case MOVE: {
                if (!mSelection.isEmpty()) {
                    // Remove any constraints auto-created
                    for (Selection.Element selection : mSelection.getElements()) {
                        boolean didResetAutoConnections = false;
                        for (ConstraintAnchor anchor : selection.widget.getAnchors()) {
                            if (anchor.isConnected()
                                    && anchor.getConnectionCreator()
                                    == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                                anchor.getOwner().resetAnchor(anchor);
                                WidgetDecorator.getConstraintHandle(anchor).stopLock();
                                didResetAutoConnections = true;
                            }
                        }
                        if (didResetAutoConnections) {
                            mSelection.addModifiedWidget(selection.widget);
                        }
                        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
                            widget.disconnectUnlockedWidget(selection.widget);
                            mSelection.addModifiedWidget(widget);
                        }
                    }
                    // Dragging the widget is no anchors or resize handles are selected
                    boolean snapPosition = mSelection.hasSingleElement();
                    if (!mSelection.hasSingleElement() && mSelection.getSelectionBounds() != null) {
                        Selection.Element bounds = mSelection.getSelectionBounds();
                        bounds.widget.setParent(mWidgetsScene.getRoot());
                        directionLockedStatus = mWidgetMotion.dragWidget(getStartPoint(), bounds,
                                x, y, true, isShiftDown(), mViewTransform);
                        mSelection.updatePositionsFromBounds();
                    } else {
                        for (Selection.Element selection : mSelection.getElements()) {
                            directionLockedStatus =
                                    mWidgetMotion.dragWidget(getStartPoint(), selection, x, y,
                                            snapPosition, isShiftDown(), mViewTransform);
                        }
                    }
                    for (Selection.Element selection : mSelection.getElements()) {
                        mSelection.addModifiedWidget(selection.widget);
                    }
                    mSelection.fireContinuousChange();

                }
            }
            break;
            case RESIZE: {
                if (mSelection.getSelectedResizeHandle() != null) {
                    // if we have a resize handle selected, let's resize!
                    Selection.Element selection = mSelection.getFirstElement();
                    if (mSelection.getSelectedResizeHandle() != null &&
                            !selection.widget.isRoot() &&
                            !selection.widget.isRootContainer()) {
                        ArrayList<ConstraintWidget> widgetsToCheck =
                                new ArrayList<ConstraintWidget>();
                        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
                            widgetsToCheck.add(w);
                        }
                        mWidgetResize.resizeWidget(widgetsToCheck, selection.widget,
                                mSelection.getSelectedResizeHandle(),
                                mSelection.getOriginalWidgetBounds(), x, y);
                        mSelection.addModifiedWidget(selection.widget);
                    }
                }
            }
            break;
            case CONNECT: {
                if (mSelection.getSelectedAnchor() != null && mSelection.hasSingleElement()) {
                    // we have a selected anchor, let's check against other available anchors
                    ConstraintWidget selectedWidget = mSelection.getFirstElement().widget;

                    mDragListener.populate();
                    mDragListener.find(mViewTransform.getSwingFX(getLastPoint().x),
                            mViewTransform.getSwingFY(getLastPoint().y));
                    ConstraintAnchor anchor = mDragListener.getConstraintAnchor();
                    if (anchor != null
                            && anchor != mSelection.getSelectedAnchor()
                            && mSelection.getSelectedAnchor().isValidConnection(anchor)
                            &&
                            mSelection.getSelectedAnchor().isConnectionAllowed(anchor.getOwner(), anchor)) {
                        if (mSelection.getConnectionCandidateAnchor() != anchor) {
                            if (mSelection.getConnectionCandidateAnchor() != null) {
                                if (getSnapshot() != null) {
                                    getSnapshot().applyTo(selectedWidget);
                                    mSelection.addModifiedWidget(selectedWidget);
                                }
                            }
                            mSelection.setConnectionCandidateAnchor(anchor);
                        }
                        if (mSelection.getSelectedAnchor().getTarget() !=
                                mSelection.getConnectionCandidateAnchor()) {
                            int margin = 0;
                            boolean useExistingDistance = !mUseDefinedMargin;
                            if (isControlDown()) {
                                useExistingDistance = !useExistingDistance;
                            }
                            ConstraintHandle handle =
                                    WidgetInteractionTargets.constraintHandle(
                                            mSelection.getSelectedAnchor());
                            ConstraintHandle handleTarget =
                                    WidgetInteractionTargets.constraintHandle(
                                            mSelection.getConnectionCandidateAnchor());
                            int existingDistance = handle.getCreationMarginFrom(handleTarget);
                            if (useExistingDistance) {
                                margin = existingDistance;
                            } else {
                                if (existingDistance >= sMargin) {
                                    margin = sMargin;
                                }
                            }
                            if (handleTarget.getAnchor().getType() ==
                                    handle.getAnchor().getType()) {
                                if (handleTarget.getOwner().isRootContainer()) {
                                    // for root we use a default margin (16dp)
                                    margin = Math.max(SnapPlacement.DEFAULT_MARGIN, margin);
                                } else {
                                    // If we have a connection between the same type of anchors,
                                    // let's not set a margin, as it's more likely to be a direct
                                    // alignment
                                    margin = 0;
                                }
                            }
                            ConstraintAnchor.Strength strength = ConstraintAnchor.Strength.STRONG;
                            if (isShiftDown()) {
                                strength = ConstraintAnchor.Strength.WEAK;
                            }
                            ConstraintWidget widget = mSelection.getSelectedAnchor().getOwner();
                            widget.connect(
                                    mSelection.getSelectedAnchor(),
                                    mSelection.getConnectionCandidateAnchor(), margin, strength,
                                    ConstraintAnchor.USER_CREATOR);
                            mSelection.addModifiedWidget(widget);
                            mSelection.setLastConnectedAnchor(mSelection.getSelectedAnchor());
                        }
                    } else {
                        if (mSelection.getConnectionCandidateAnchor() != null) {
                            mSelection.setConnectionCandidateAnchor(null);
                            if (getSnapshot() != null) {
                                getSnapshot().applyTo(selectedWidget);
                                mSelection.addModifiedWidget(selectedWidget);
                            }
                        }
                    }
                }
            }
            break;
        }
        return directionLockedStatus;
    }

    /**
     * Mouse dragged handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     * @return the type of direction (locked in x/y or not)
     */
    public void dragAndDrop(ConstraintWidget widget, int x, int y) {
        mMouseMode = MouseMode.MOVE;
        mWidgetMotion.dragWidget(new Point(x, y), new Selection.Element(widget), x, y,
                                 true, isShiftDown(), mViewTransform);
    }

    /**
     * Mouse moved handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     */
    public void mouseMoved(float x, float y) {
        if (mMoveOnlyMode) {
            return;
        }
        mClickListener.mEnableBaseline = false;
        mHoverListener.mEnableBaseline = false;
        mBaselineTimer.restart();
        updateFromHoverListener(mViewTransform.getSwingFX(x), mViewTransform.getSwingFY(y));
    }

    /**
     * Check the hover listener for any anchor to lit
     *
     * @param x
     * @param y
     */
    private void updateFromHoverListener(int x, int y) {
        mHoverListener.populate();
        mHoverListener.find(x, y);
        ConstraintWidget widget = mHoverListener.mHitWidget;
        ConstraintAnchor anchor = mHoverListener.getConstraintAnchor();
        ResizeHandle handle = mHoverListener.mHitResizeHandle;
        mMouseCursor = updateMouseCursor(handle);
        if (mPreviousHoverWidget != null) {
            if (!mSelection.contains(mPreviousHoverWidget)) {
                getDecorator(mPreviousHoverWidget).setLook(ColorTheme.Look.NORMAL);
            }
        }

        if (widget != null) {
            if (!mSelection.contains(widget)) {
                getDecorator(widget).setLook(ColorTheme.Look.HIGHLIGHTED);
            }
            mPreviousHoverWidget = widget;
        }

        mSceneDraw.setCurrentUnderneathAnchor(anchor);
        mSceneDraw.repaint();
    }

    /**
     * Return the mouse cursor type given the current handle we hit
     *
     * @param handle the resize handle we find under the mouse (if any)
     * @return the mouse cursor type
     */
    private int updateMouseCursor(ResizeHandle handle) {
        if (handle == null) {
            return Cursor.DEFAULT_CURSOR;
        }
        switch (handle.getType()) {
            case LEFT_BOTTOM: {
                return Cursor.SW_RESIZE_CURSOR;
            }
            case LEFT_TOP: {
                return Cursor.NW_RESIZE_CURSOR;
            }
            case RIGHT_BOTTOM: {
                return Cursor.SE_RESIZE_CURSOR;
            }
            case RIGHT_TOP: {
                return Cursor.NE_RESIZE_CURSOR;
            }
            case LEFT_SIDE: {
                return Cursor.W_RESIZE_CURSOR;
            }
            case RIGHT_SIDE: {
                return Cursor.E_RESIZE_CURSOR;
            }
            case TOP_SIDE: {
                return Cursor.N_RESIZE_CURSOR;
            }
            case BOTTOM_SIDE: {
                return Cursor.S_RESIZE_CURSOR;
            }
        }
        return Cursor.DEFAULT_CURSOR;
    }

    /*-----------------------------------------------------------------------*/
    // Mouse events
    /*-----------------------------------------------------------------------*/

    /**
     * Mouse press handling
     *
     * @param e mouse event
     */
    public void mousePressed(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
        float x = mViewTransform.getAndroidFX(e.getX());
        float y = mViewTransform.getAndroidFY(e.getY());
        mousePressed(x, y, SwingUtilities.isRightMouseButton(e));
    }

    /**
     * Mouse release handling
     *
     * @param e mouse event
     */
    public void mouseReleased(MouseEvent e) {
        int x = mViewTransform.getAndroidX(e.getX());
        int y = mViewTransform.getAndroidY(e.getY());
        mouseReleased(x, y);
    }

    /**
     * Mouse dragged handling
     *
     * @param e mouse event
     * @return the type of direction (locked in x/y or not)
     */
    public int mouseDragged(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
        int x = mViewTransform.getAndroidX(e.getX());
        int y = mViewTransform.getAndroidY(e.getY());
        return mouseDragged(x, y);
    }

    /**
     * Mouse entered handling
     *
     * @param e mouse event
     */
    public void mouseEntered(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
    }

    /**
     * Mouse moved handling
     *
     * @param e mouse event
     */
    public void mouseMoved(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
        float x = mViewTransform.getAndroidFX(e.getX());
        float y = mViewTransform.getAndroidFY(e.getY());
        mouseMoved(x, y);
    }

    /*-----------------------------------------------------------------------*/
    // Key events
    /*-----------------------------------------------------------------------*/

    public void keyPressed(KeyEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
    }

    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            mIsControlDown = false;
        }
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            mIsShiftDown = false;
        }
    }

    public boolean isMoveOnlyMode() {
        return mMoveOnlyMode;
    }

    public void setMoveOnlyMode(boolean moveOnlyMode) {
        mMoveOnlyMode = moveOnlyMode;
    }
}
