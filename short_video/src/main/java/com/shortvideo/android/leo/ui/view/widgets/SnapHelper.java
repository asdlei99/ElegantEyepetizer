package com.shortvideo.android.leo.ui.view.widgets;
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


import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.RecyclerView.SmoothScroller;
import android.support.v7.widget.RecyclerView.SmoothScroller.ScrollVectorProvider;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Class intended to support snapping for a {@link RecyclerView}.
 * <p>
 * SnapHelper tries to handle fling as well but for this to work properly, the
 * {@link RecyclerView.LayoutManager} must implement the {@link ScrollVectorProvider} interface or
 * you should override {@link #onFling(int, int)} and handle fling manually.
 */
public abstract class SnapHelper extends RecyclerView.OnFlingListener implements RecyclerView.OnChildAttachStateChangeListener {

    static final float MILLISECONDS_PER_INCH = 100f;

    RecyclerView mRecyclerView;
    private Scroller mGravityScroller;

    protected List<OnPageStateChangedListener> mOnPageStateChangedListeners;
    protected int mSmoothScrollTargetPosition = -1;
    protected int mPositionBeforeScroll = 0;
    private float mFlingFactor = 0.15f;
    // Handles the snap on scroll case.
    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                boolean mScrolled = false;

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                        if (mScrolled) {
                            mScrolled = false;
                            snapToTargetExistingView();
                        }
                        LayoutManager layoutManager = mRecyclerView.getLayoutManager();
                        if (layoutManager != null && layoutManager instanceof LinearLayoutManager) {
                            View view = findSnapView(layoutManager);
                            if (view != null) {
                                mSmoothScrollTargetPosition = layoutManager.getPosition(view);
                            } else {
                                mSmoothScrollTargetPosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                            }
                            Log.d("@", "SCROLL_STATE_IDLE --->mSmoothScrollTargetPosition->" + mSmoothScrollTargetPosition);
                            if (mOnPageStateChangedListeners != null && layoutManager.getChildCount() == 1) {
                                Log.d("@", "onPageChanged  old position --->" + mPositionBeforeScroll + "  new position->" + mSmoothScrollTargetPosition);
                                for (OnPageStateChangedListener pageChangedListener : mOnPageStateChangedListeners) {
                                    pageChangedListener.onPageChanged(mPositionBeforeScroll, mSmoothScrollTargetPosition);
                                }
                                mPositionBeforeScroll = mSmoothScrollTargetPosition;
                            }
                        }

                    }
                    if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
//                        Log.d("@", "SCROLL_STATE_SETTLING --->");
                    }
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
//                        Log.d("@", "SCROLL_STATE_DRAGGING --->");
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (dx != 0 || dy != 0) {
                        mScrolled = true;
                    }
                }
            };

    @Override
    public boolean onFling(int velocityX, int velocityY) {
        LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager == null) {
            return false;
        }
        RecyclerView.Adapter adapter = mRecyclerView.getAdapter();
        if (adapter == null) {
            return false;
        }
        int minFlingVelocity = mRecyclerView.getMinFlingVelocity();
        return (Math.abs(velocityY * mFlingFactor) > minFlingVelocity || Math.abs(velocityX * mFlingFactor) > minFlingVelocity)
                && snapFromFling(layoutManager, velocityX, velocityY);
    }

    /**
     * Attaches the {@link SnapHelper} to the provided RecyclerView, by calling
     * {@link RecyclerView#setOnFlingListener(RecyclerView.OnFlingListener)}.
     * You can call this method with {@code null} to detach it from the current RecyclerView.
     *
     * @param recyclerView The RecyclerView instance to which you want to add this helper or
     *                     {@code null} if you want to remove SnapHelper from the current
     *                     RecyclerView.
     * @throws IllegalArgumentException if there is already a {@link RecyclerView.OnFlingListener}
     *                                  attached to the provided {@link RecyclerView}.
     */
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView)
            throws IllegalStateException {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (mRecyclerView != null) {
            setupCallbacks();
            mGravityScroller = new Scroller(mRecyclerView.getContext(),
                    new DecelerateInterpolator());
            snapToTargetExistingView();
        }
    }

    /**
     * Called when an instance of a {@link RecyclerView} is attached.
     */
    private void setupCallbacks() throws IllegalStateException {
        if (mRecyclerView.getOnFlingListener() != null) {
            throw new IllegalStateException("An instance of OnFlingListener already set.");
        }
        mRecyclerView.addOnScrollListener(mScrollListener);
        mRecyclerView.setOnFlingListener(this);
    }

    /**
     * Called when the instance of a {@link RecyclerView} is detached.
     */
    private void destroyCallbacks() {
        mRecyclerView.removeOnScrollListener(mScrollListener);
        mRecyclerView.setOnFlingListener(null);
    }

    public void addOnPageChangedListener(OnPageStateChangedListener listener) {
        if (mOnPageStateChangedListeners == null) {
            mOnPageStateChangedListeners = new ArrayList<>();
        }
        mOnPageStateChangedListeners.add(listener);
    }

    public void removeOnPageChangedListener(OnPageStateChangedListener listener) {
        if (mOnPageStateChangedListeners != null) {
            mOnPageStateChangedListeners.remove(listener);
        }
    }

    public void clearOnPageChangedListeners() {
        if (mOnPageStateChangedListeners != null) {
            mOnPageStateChangedListeners.clear();
        }
    }

    public void scrollToPosition(final int position) {
        if (mOnPageStateChangedListeners != null && mOnPageStateChangedListeners.size() > 0) {
            mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressWarnings("deprecation")
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < 16) {
                        mRecyclerView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        mRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }

                    if (position < mRecyclerView.getAdapter().getItemCount()) {
                        mSmoothScrollTargetPosition = position;
                        if (mOnPageStateChangedListeners != null) {
                            for (OnPageStateChangedListener onPageStateChangedListener : mOnPageStateChangedListeners) {
                                if (onPageStateChangedListener != null) {
                                    onPageStateChangedListener.onPageChanged(mPositionBeforeScroll, mSmoothScrollTargetPosition);
                                }
                            }
                        }
                        //TODO 直接刷新onBindViewHolder 只调用了第一个，会造成下一页封面没及时显示，暂时方案为向下偏移一像素，有好方案可以替换
                        mRecyclerView.scrollBy(0, 1);
                        mPositionBeforeScroll = position;
                    }
                }
            });
        }
    }

    /**
     * Calculated the estimated scroll distance in each direction given velocities on both axes.
     *
     * @param velocityX Fling velocity on the horizontal axis.
     * @param velocityY Fling velocity on the vertical axis.
     * @return array holding the calculated distances in x and y directions
     * respectively.
     */
    public int[] calculateScrollDistance(int velocityX, int velocityY) {
        int[] outDist = new int[2];
        mGravityScroller.fling(0, 0, velocityX, velocityY,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        outDist[0] = mGravityScroller.getFinalX();
        outDist[1] = mGravityScroller.getFinalY();
        return outDist;
    }

    /**
     * Helper method to facilitate for snapping triggered by a fling.
     *
     * @param layoutManager The {@link LayoutManager} associated with the attached
     *                      {@link RecyclerView}.
     * @param velocityX     Fling velocity on the horizontal axis.
     * @param velocityY     Fling velocity on the vertical axis.
     * @return true if it is handled, false otherwise.
     */
    private boolean snapFromFling(@NonNull LayoutManager layoutManager, int velocityX,
                                  int velocityY) {
        if (!(layoutManager instanceof ScrollVectorProvider)) {
            return false;
        }

        SmoothScroller smoothScroller = createScroller(layoutManager);
        if (smoothScroller == null) {
            return false;
        }

        int targetPosition = findTargetSnapPosition(layoutManager, velocityX, velocityY);
        if (targetPosition == RecyclerView.NO_POSITION) {
            return false;
        }
        //快速滑动回调
        if (targetPosition != mPositionBeforeScroll) {
            if (mOnPageStateChangedListeners != null) {
                for (OnPageStateChangedListener pageChangedListener : mOnPageStateChangedListeners) {
                    pageChangedListener.onFlingToOtherPosition();
                }
            }
        }
        smoothScroller.setTargetPosition(targetPosition);
        layoutManager.startSmoothScroll(smoothScroller);
        return true;
    }

    /**
     * Snaps to a target view which currently exists in the attached {@link RecyclerView}. This
     * method is used to snap the view when the {@link RecyclerView} is first attached; when
     * snapping was triggered by a scroll and when the fling is at its final stages.
     */
    void snapToTargetExistingView() {
        if (mRecyclerView == null) {
            return;
        }
        LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        View snapView = findSnapView(layoutManager);
        if (snapView == null) {
            return;
        }
        SmoothScroller smoothScroller = createScroller(layoutManager);
        if (smoothScroller == null) {
            return;
        }

        int targetPosition = layoutManager.getPosition(snapView);
        if (targetPosition == RecyclerView.NO_POSITION) {
            return;
        }

        smoothScroller.setTargetPosition(targetPosition);
        layoutManager.startSmoothScroll(smoothScroller);
       /* int[] snapDistance = calculateDistanceToFinalSnap(layoutManager, snapView);
        if (snapDistance[0] != 0 || snapDistance[1] != 0) {
            mRecyclerView.smoothScrollBy(snapDistance[0], snapDistance[1]);
        }*/
    }

    /**
     * Creates a scroller to be used in the snapping implementation.
     *
     * @param layoutManager The {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}.
     * @return a {@link SmoothScroller} which will handle the scrolling.
     */
    @Nullable
    protected SmoothScroller createScroller(LayoutManager layoutManager) {
        return createSnapScroller(layoutManager);
    }

    /**
     * Creates a scroller to be used in the snapping implementation.
     *
     * @param layoutManager The {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}.
     * @return a {@link LinearSmoothScroller} which will handle the scrolling.
     * @deprecated use {@link #createScroller(LayoutManager)} instead.
     */
    @Nullable
    @Deprecated
    protected LinearSmoothScroller createSnapScroller(LayoutManager layoutManager) {
        if (!(layoutManager instanceof ScrollVectorProvider)) {
            return null;
        }
        return new LinearSmoothScroller(mRecyclerView.getContext()) {
            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                if (mRecyclerView == null) {
                    // The associated RecyclerView has been removed so there is no action to take.
                    return;
                }
                int[] snapDistances = calculateDistanceToFinalSnap(mRecyclerView.getLayoutManager(),
                        targetView);
                final int dx = snapDistances[0];
                final int dy = snapDistances[1];
                final int time = calculateTimeForDeceleration(Math.max(Math.abs(dx), Math.abs(dy)));
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator);
                }
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
            }

            @Override
            protected void onStop() {
                super.onStop();
            }
        };
    }

    /**
     * Override this method to snap to a particular point within the target view or the container
     * view on any axis.
     * <p>
     * This method is called when the {@link SnapHelper} has intercepted a fling and it needs
     * to know the exact distance required to scroll by in order to snap to the target view.
     *
     * @param layoutManager the {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}
     * @param targetView    the target view that is chosen as the view to snap
     * @return the output coordinates the put the result into. out[0] is the distance
     * on horizontal axis and out[1] is the distance on vertical axis.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public abstract int[] calculateDistanceToFinalSnap(@NonNull LayoutManager layoutManager,
                                                       @NonNull View targetView);

    /**
     * Override this method to provide a particular target view for snapping.
     * <p>
     * This method is called when the {@link SnapHelper} is ready to start snapping and requires
     * a target view to snap to. It will be explicitly called when the scroll state becomes idle
     * after a scroll. It will also be called when the {@link SnapHelper} is preparing to snap
     * after a fling and requires a reference view from the current set of child views.
     * <p>
     * If this method returns {@code null}, SnapHelper will not snap to any view.
     *
     * @param layoutManager the {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}
     * @return the target view to which to snap on fling or end of scroll
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public abstract View findSnapView(LayoutManager layoutManager);

    /**
     * Override to provide a particular adapter target position for snapping.
     *
     * @param layoutManager the {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}
     * @param velocityX     fling velocity on the horizontal axis
     * @param velocityY     fling velocity on the vertical axis
     * @return the target adapter position to you want to snap or {@link RecyclerView#NO_POSITION}
     * if no snapping should happen
     */
    public abstract int findTargetSnapPosition(LayoutManager layoutManager, int velocityX,
                                               int velocityY);

    @Override
    public void onChildViewAttachedToWindow(View view) {

    }

    @Override
    public void onChildViewDetachedFromWindow(View view) {
        if (mOnPageStateChangedListeners != null && mRecyclerView != null) {
            for (OnPageStateChangedListener onPageStateChangedListener : mOnPageStateChangedListeners) {
                LayoutManager layoutManager = mRecyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                int position = layoutManager.getPosition(view);
                if (position < 0) {
                    return;
                }
                onPageStateChangedListener.onPageDetachedFromWindow(position);
            }
        }
    }
}