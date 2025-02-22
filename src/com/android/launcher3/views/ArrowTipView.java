/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.views;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.content.ContextCompat;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.TriangleShape;

/**
 * A base class for arrow tip view in launcher
 */
public class ArrowTipView extends AbstractFloatingView {

    private static final String TAG = ArrowTipView.class.getSimpleName();
    private static final long AUTO_CLOSE_TIMEOUT_MILLIS = 10 * 1000;
    private static final long SHOW_DELAY_MS = 200;
    private static final long SHOW_DURATION_MS = 300;
    private static final long HIDE_DURATION_MS = 100;

    protected final BaseDraggingActivity mActivity;
    private final Handler mHandler = new Handler();
    private final int mArrowWidth;
    private boolean mIsPointingUp;
    private Runnable mOnClosed;
    private View mArrowView;

    public ArrowTipView(Context context) {
        this(context, false);
    }

    public ArrowTipView(Context context, boolean isPointingUp) {
        super(context, null, 0);
        mActivity = BaseDraggingActivity.fromContext(context);
        mIsPointingUp = isPointingUp;
        mArrowWidth = context.getResources().getDimensionPixelSize(R.dimen.arrow_toast_arrow_width);
        init(context);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            close(true);
            if (mActivity.getDragLayer().isEventOverView(this, ev)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            if (animate) {
                animate().alpha(0f)
                        .withLayer()
                        .setStartDelay(0)
                        .setDuration(HIDE_DURATION_MS)
                        .setInterpolator(Interpolators.ACCEL)
                        .withEndAction(() -> mActivity.getDragLayer().removeView(this))
                        .start();
            } else {
                animate().cancel();
                mActivity.getDragLayer().removeView(this);
            }
            if (mOnClosed != null) mOnClosed.run();
            mIsOpen = false;
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    private void init(Context context) {
        inflate(context, R.layout.arrow_toast, this);
        setOrientation(LinearLayout.VERTICAL);

        mArrowView = findViewById(R.id.arrow);
        updateArrowTipInView();
    }

    /**
     * Show Tip with specified string and Y location
     */
    public ArrowTipView show(String text, int top) {
        return show(text, Gravity.CENTER_HORIZONTAL, 0, top);
    }

    /**
     * Show the ArrowTipView (tooltip) center, start, or end aligned.
     *
     * @param text             The text to be shown in the tooltip.
     * @param gravity          The gravity aligns the tooltip center, start, or end.
     * @param arrowMarginStart The margin from start to place arrow (ignored if center)
     * @param top              The Y coordinate of the bottom of tooltip.
     * @return The tooltip.
     */
    public ArrowTipView show(String text, int gravity, int arrowMarginStart, int top) {
        ((TextView) findViewById(R.id.text)).setText(text);
        ViewGroup parent = mActivity.getDragLayer();
        parent.addView(this);

        DragLayer.LayoutParams params = (DragLayer.LayoutParams) getLayoutParams();
        params.gravity = gravity;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mArrowView.getLayoutParams();
        lp.gravity = gravity;

        if (parent.getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            arrowMarginStart = parent.getMeasuredWidth() - arrowMarginStart;
        }
        if (gravity == Gravity.END) {
            lp.setMarginEnd(parent.getMeasuredWidth() - arrowMarginStart - mArrowWidth);
        } else if (gravity == Gravity.START) {
            lp.setMarginStart(arrowMarginStart - mArrowWidth / 2);
        }
        requestLayout();

        params.leftMargin = mActivity.getDeviceProfile().workspacePadding.left;
        params.rightMargin = mActivity.getDeviceProfile().workspacePadding.right;
        post(() -> setY(top - (mIsPointingUp ? 0 : getHeight())));

        mIsOpen = true;
        mHandler.postDelayed(() -> handleClose(true), AUTO_CLOSE_TIMEOUT_MILLIS);
        setAlpha(0);
        animate()
                .alpha(1f)
                .withLayer()
                .setStartDelay(SHOW_DELAY_MS)
                .setDuration(SHOW_DURATION_MS)
                .setInterpolator(Interpolators.DEACCEL)
                .start();
        return this;
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text        The text to be shown in the tooltip.
     * @param arrowXCoord The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                    center of tooltip unless the tooltip goes beyond screen margin.
     * @param yCoord      The Y coordinate of the pointed tip end of the tooltip.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable
    public ArrowTipView showAtLocation(String text, @Px int arrowXCoord, @Px int yCoord) {
        return showAtLocation(
                text,
                arrowXCoord,
                /* yCoordDownPointingTip= */ yCoord,
                /* yCoordUpPointingTip= */ yCoord);
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text        The text to be shown in the tooltip.
     * @param arrowXCoord The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                    center of tooltip unless the tooltip goes beyond screen margin.
     * @param rect        The coordinates of the view which requests the tooltip to be shown.
     * @param margin      The margin between {@param rect} and the tooltip.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable
    public ArrowTipView showAroundRect(
            String text, @Px int arrowXCoord, Rect rect, @Px int margin) {
        return showAtLocation(
                text,
                arrowXCoord,
                /* yCoordDownPointingTip= */ rect.top - margin,
                /* yCoordUpPointingTip= */ rect.bottom + margin);
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text                  The text to be shown in the tooltip.
     * @param arrowXCoord           The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                              center of tooltip unless the tooltip goes beyond screen margin.
     * @param yCoordDownPointingTip The Y coordinate of the pointed tip end of the tooltip when the
     *                              tooltip is placed pointing downwards.
     * @param yCoordUpPointingTip   The Y coordinate of the pointed tip end of the tooltip when the
     *                              tooltip is placed pointing upwards.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable
    private ArrowTipView showAtLocation(String text, @Px int arrowXCoord,
                                        @Px int yCoordDownPointingTip, @Px int yCoordUpPointingTip) {
        ViewGroup parent = mActivity.getDragLayer();
        @Px int parentViewWidth = parent.getWidth();
        @Px int parentViewHeight = parent.getHeight();
        @Px int maxTextViewWidth = getContext().getResources()
                .getDimensionPixelSize(R.dimen.widget_picker_education_tip_max_width);
        @Px int minViewMargin = getContext().getResources()
                .getDimensionPixelSize(R.dimen.widget_picker_education_tip_min_margin);
        if (parentViewWidth < maxTextViewWidth + 2 * minViewMargin) {
            Log.w(TAG, "Cannot display tip on a small screen of size: " + parentViewWidth);
            return null;
        }

        TextView textView = findViewById(R.id.text);
        textView.setText(text);
        textView.setMaxWidth(maxTextViewWidth);
        parent.addView(this);
        requestLayout();

        post(() -> {
            // Adjust the tooltip horizontally.
            float halfWidth = getWidth() / 2f;
            float xCoord;
            if (arrowXCoord - halfWidth < minViewMargin) {
                // If the tooltip is estimated to go beyond the left margin, place its start just at
                // the left margin.
                xCoord = minViewMargin;
            } else if (arrowXCoord + halfWidth > parentViewWidth - minViewMargin) {
                // If the tooltip is estimated to go beyond the right margin, place it such that its
                // end is just at the right margin.
                xCoord = parentViewWidth - minViewMargin - getWidth();
            } else {
                // Place the tooltip such that its center is at arrowXCoord.
                xCoord = arrowXCoord - halfWidth;
            }
            setX(xCoord);

            // Adjust the tooltip vertically.
            @Px int viewHeight = getHeight();
            if (mIsPointingUp
                    ? (yCoordUpPointingTip + viewHeight > parentViewHeight)
                    : (yCoordDownPointingTip - viewHeight < 0)) {
                // Flip the view if it exceeds the vertical bounds of screen.
                mIsPointingUp = !mIsPointingUp;
                updateArrowTipInView();
            }
            // Place the tooltip such that its top is at yCoordUpPointingTip if arrow is displayed
            // pointing upwards, otherwise place it such that its bottom is at
            // yCoordDownPointingTip.
            setY(mIsPointingUp ? yCoordUpPointingTip : yCoordDownPointingTip - viewHeight);

            // Adjust the arrow's relative position on tooltip to make sure the actual position of
            // arrow's pointed tip is always at arrowXCoord.
            mArrowView.setX(arrowXCoord - xCoord - mArrowView.getWidth() / 2f);
            requestLayout();
        });

        mIsOpen = true;
        mHandler.postDelayed(() -> handleClose(true), AUTO_CLOSE_TIMEOUT_MILLIS);
        setAlpha(0);
        animate()
                .alpha(1f)
                .withLayer()
                .setStartDelay(SHOW_DELAY_MS)
                .setDuration(SHOW_DURATION_MS)
                .setInterpolator(Interpolators.DEACCEL)
                .start();
        return this;
    }

    private void updateArrowTipInView() {
        ViewGroup.LayoutParams arrowLp = mArrowView.getLayoutParams();
        ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                arrowLp.width, arrowLp.height, mIsPointingUp));
        Paint arrowPaint = arrowDrawable.getPaint();
        @Px int arrowTipRadius = getContext().getResources()
                .getDimensionPixelSize(R.dimen.arrow_toast_corner_radius);
        arrowPaint.setColor(ContextCompat.getColor(getContext(), R.color.arrow_tip_view_bg));
        arrowPaint.setPathEffect(new CornerPathEffect(arrowTipRadius));
        mArrowView.setBackground(arrowDrawable);
        // Add negative margin so that the rounded corners on base of arrow are not visible.
        removeView(mArrowView);
        if (mIsPointingUp) {
            addView(mArrowView, 0);
            ((ViewGroup.MarginLayoutParams) arrowLp).setMargins(0, 0, 0, -1 * arrowTipRadius);
        } else {
            addView(mArrowView, 1);
            ((ViewGroup.MarginLayoutParams) arrowLp).setMargins(0, -1 * arrowTipRadius, 0, 0);
        }
    }

    /**
     * Register a callback fired when toast is hidden
     */
    public ArrowTipView setOnClosedCallback(Runnable runnable) {
        mOnClosed = runnable;
        return this;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        close(/* animate= */ false);
    }
}
