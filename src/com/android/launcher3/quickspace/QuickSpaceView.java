/*
 * Copyright (C) 2018-2025 crDroid Android Project
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
package com.android.launcher3.quickspace;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.TypedValue;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Themes;

import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

public class QuickSpaceView extends FrameLayout implements OnDataListener {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    public ColorStateList mColorStateList;
    public BubbleTextView mBubbleTextView;
    public int mQuickspaceBackgroundRes;

    public ViewGroup mQuickspaceContent;
    public ImageView mEventSubIcon;
    public ImageView mNowPlayingIcon;
    public TextView mEventTitleSub;
    public TextView mEventTitleSubColored;
    public TextView mGreetingsExt;
    public TextView mGreetingsExtClock;
    public ViewGroup mWeatherContentSub;
    public ImageView mWeatherIconSub;
    public TextView mWeatherTempSub;
    public TextView mEventTitle;
    public TextView mClock1;
    public TextView mClock2;
    public FrameLayout mClockContainer;
    public TextView mDateText;

    public boolean mIsQuickEvent;
    public boolean mFinishedInflate;
    public boolean mWeatherAvailable;
    public boolean mAttached;

    private QuickSpaceActionReceiver mActionReceiver;
    public QuickspaceController mController;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        // Don't load controller at all if QuickSpace is disabled - saves memory
        if (!LauncherPrefs.SHOW_QUICKSPACE.get(context)) return;
        mActionReceiver = new QuickSpaceActionReceiver(context);
        mController = new QuickspaceController(context);
        mColorStateList = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
        setClipChildren(false);
    }

    @Override
    public void onDataUpdated() {
        if (mController == null) return;
        boolean altUI = LauncherPrefs.SHOW_QUICKSPACE_ALT.get(getContext());
        mController.getEventController().initQuickEvents();
        mIsQuickEvent = mController.isQuickEvent();
        if (mEventTitle == null || (altUI && mGreetingsExt == null)) {
            prepareLayout(altUI);
        }
        mWeatherAvailable = mController.isWeatherAvailable();
        loadDoubleLine(altUI);
    }

    private final void loadDoubleLine(boolean useAlternativeQuickspaceUI) {
        mEventTitle.setText(mController.getEventController().getTitle());
        if (useAlternativeQuickspaceUI) {
            String greetingsExt = mController.getEventController().getGreetings();
            if (greetingsExt != null && !greetingsExt.isEmpty()) {
                mGreetingsExt.setVisibility(View.VISIBLE);
                mGreetingsExt.setText(greetingsExt);
                mGreetingsExt.setEllipsize(TruncateAt.END);
                mGreetingsExt.setOnClickListener(mController.getEventController().getAction());
            } else {
                mGreetingsExt.setVisibility(View.GONE);
            }
            String greetingsExtClock = mController.getEventController().getClockExt();
            if (greetingsExtClock != null && !greetingsExtClock.isEmpty()) {
                mGreetingsExtClock.setVisibility(View.VISIBLE);
                mGreetingsExtClock.setText(greetingsExtClock);
                mGreetingsExtClock.setOnClickListener(mController.getEventController().getAction());
            } else {
                mGreetingsExtClock.setVisibility(View.GONE);
            }
        }
        if (mIsQuickEvent && (LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext()) ||
                        mController.getEventController().isNowPlaying())) {
            maybeSetMarquee(mEventTitle);
            mEventTitle.setOnClickListener(mController.getEventController().getAction());
            mEventTitleSub.setVisibility(View.VISIBLE);
            mEventTitleSub.setText(mController.getEventController().getActionTitle());
            maybeSetMarquee(mEventTitleSub);
            mEventTitleSub.setOnClickListener(mController.getEventController().getAction());
            if (useAlternativeQuickspaceUI) {
                if (mController.getEventController().isNowPlaying()) {
                    mEventSubIcon.setVisibility(View.GONE);
                    mEventTitleSubColored.setVisibility(View.VISIBLE);
                    mNowPlayingIcon.setVisibility(View.VISIBLE);
                    mNowPlayingIcon.setImageResource(R.drawable.baseline_audiotrack_24);
                    mNowPlayingIcon.setImageTintList(ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceAccentColor)));
                    mNowPlayingIcon.setOnClickListener(mController.getEventController().getAction());
                    mEventTitleSubColored.setText(getContext().getString(R.string.qe_now_playing_by));
                    mEventTitleSubColored.setOnClickListener(mController.getEventController().getAction());
                } else {
                    setEventSubIcon();
                    mEventTitleSubColored.setText("");
                    mEventTitleSubColored.setVisibility(View.GONE);
                    mNowPlayingIcon.setVisibility(View.GONE);
                }
            } else {
                setEventSubIcon();
            }
        } else {
            mEventTitleSub.setVisibility(View.GONE);
            mEventSubIcon.setVisibility(View.GONE);
            if (useAlternativeQuickspaceUI) {
                mEventTitleSubColored.setVisibility(View.GONE);
                mNowPlayingIcon.setVisibility(View.GONE);
            }
        }
        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);

        // Ensure clock/date visibility reflects current prefs after data update
        updateClockVisibilityAndColor();
    }

    private void maybeSetMarquee(TextView tv) {
        tv.setSelected(false);
        tv.setEllipsize(TruncateAt.END);
        final float textWidth = tv.getPaint().measureText(tv.getText().toString());
        tv.post(() -> {
            android.text.Layout layout = tv.getLayout();
            if (layout != null && layout.getEllipsizedWidth() < textWidth) {
                tv.setEllipsize(TruncateAt.MARQUEE);
                tv.setMarqueeRepeatLimit(1);
                tv.setSelected(true);
            }
        });
    }

    private Drawable mLastEventIcon = null;

    private void setEventSubIcon() {
        Drawable icon = mController.getEventController().getActionIcon();
        if (icon != null) {
            mEventSubIcon.setVisibility(View.VISIBLE);
            mEventSubIcon.setImageTintList(mController.getEventController().isNowPlaying() ? null : mColorStateList);
            
            // Only update drawable if it changed to prevent memory churn
            if (icon != mLastEventIcon) {
                if (mLastEventIcon != null) {
                    mLastEventIcon.setCallback(null);
                }
                mEventSubIcon.setImageDrawable(icon);
                mLastEventIcon = icon;
            }
            
            mEventSubIcon.setOnClickListener(mController.getEventController().getAction());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mEventSubIcon.getLayoutParams();
            params.setMargins(params.leftMargin, params.topMargin, dpToPx(8), params.bottomMargin);
            mEventSubIcon.setLayoutParams(params);
        } else {
            mEventSubIcon.setVisibility(View.GONE);
            // Clear cached icon when hidden
            if (mLastEventIcon != null) {
                mLastEventIcon.setCallback(null);
                mLastEventIcon = null;
            }
        }
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String mLastWeatherTemp = null;
    private Drawable mLastWeatherIcon = null;

    private final void bindWeather(View container, TextView title, ImageView icon) {
        if (!mWeatherAvailable || mController.getEventController().isNowPlaying()) {
            container.setVisibility(View.GONE);
            // Clear cached values when hidden to free memory
            mLastWeatherTemp = null;
            if (mLastWeatherIcon != null) {
                mLastWeatherIcon.setCallback(null);
                mLastWeatherIcon = null;
            }
            return;
        }
        String weatherTemp = mController.getWeatherTemp();
        if (weatherTemp == null || weatherTemp.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        
        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        container.setVisibility(View.VISIBLE);
        container.setOnClickListener(hasGoogleApp ? mActionReceiver.getWeatherAction() : null);
        
        // Only update if content changed to prevent unnecessary redraws
        if (!weatherTemp.equals(mLastWeatherTemp)) {
            title.setText(weatherTemp);
            mLastWeatherTemp = weatherTemp;
        }
        
        Drawable weatherIcon = mController.getWeatherIcon();
        if (weatherIcon != mLastWeatherIcon) {
            // Clear old drawable callback
            if (mLastWeatherIcon != null) {
                mLastWeatherIcon.setCallback(null);
            }
            icon.setImageDrawable(weatherIcon);
            mLastWeatherIcon = weatherIcon;
        }
    }

    private final void loadViews() {
        mEventTitle = (TextView) findViewById(R.id.quick_event_title);
        mEventTitleSub = (TextView) findViewById(R.id.quick_event_title_sub);
        mEventTitleSubColored = (TextView) findViewById(R.id.quick_event_title_sub_colored);
        mNowPlayingIcon = (ImageView) findViewById(R.id.now_playing_icon_sub);
        mEventSubIcon = (ImageView) findViewById(R.id.quick_event_icon_sub);
        mWeatherIconSub = (ImageView) findViewById(R.id.quick_event_weather_icon);
        mQuickspaceContent = (ViewGroup) findViewById(R.id.quickspace_content);
        mWeatherContentSub = (ViewGroup) findViewById(R.id.quick_event_weather_content);
        mWeatherTempSub = (TextView) findViewById(R.id.quick_event_weather_temp);
        
        boolean altUI = LauncherPrefs.SHOW_QUICKSPACE_ALT.get(getContext());
        if (altUI) {
            mGreetingsExtClock = (TextView) findViewById(R.id.extended_greetings_clock);
            mGreetingsExt = (TextView) findViewById(R.id.extended_greetings);
            mClock1 = (TextView) findViewById(R.id.quickspace_clock1);
            mClock2 = (TextView) findViewById(R.id.quickspace_clock2);
        } else {
            mClock1 = (TextView) findViewById(R.id.textClock1);
            mClock2 = (TextView) findViewById(R.id.textClock2);
            mClockContainer = (FrameLayout) findViewById(R.id.clock_date_container);
            mDateText = (TextView) findViewById(R.id.date_text);
        }
        
        // Apply clock visibility and color settings
        updateClockVisibilityAndColor();
    }
    
    private void updateClockVisibilityAndColor() {
    boolean altUI = LauncherPrefs.SHOW_QUICKSPACE_ALT.get(getContext());
    boolean showClock = LauncherPrefs.SHOW_QUICKSPACE_CLOCK.get(getContext());
    int clockColor = LauncherPrefs.QUICKSPACE_CLOCK_COLOR.get(getContext());
        boolean nowPlaying = false;
        if (mController != null && mController.getEventController() != null) {
            nowPlaying = mController.getEventController().isNowPlaying();
        }

        // Alternate UI: only two TextClocks exist; toggle their visibility
        if (altUI) {
            if (mClock1 != null) mClock1.setVisibility(showClock ? View.VISIBLE : View.GONE);
            if (mClock2 != null) {
                mClock2.setVisibility(showClock ? View.VISIBLE : View.GONE);
                if (showClock) applyQuickspaceClockColor(clockColor);
            }
            return;
        }

        // Standard UI: keep the container visible; toggle inner children.
        if (mClockContainer != null) {
            mClockContainer.setVisibility(View.VISIBLE);
            // Adjust bottom margin: keep negative margin with clock, reset when clock hidden
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mClockContainer.getLayoutParams();
            if (lp != null) {
                lp.bottomMargin = showClock ? -dpToPx(10) : 0;
                mClockContainer.setLayoutParams(lp);
            }
        }
        // When clock is shown, hide date text inside container
        if (mDateText != null) {
            // Hide date if Now Playing is active
            if (nowPlaying) {
                mDateText.setVisibility(View.GONE);
            } else {
                mDateText.setVisibility(showClock ? View.GONE : View.VISIBLE);
            }
            if (!showClock && !nowPlaying) {
                mDateText.setText(getFormattedDate(false));
                // Match big date size to the smaller subtitle size (same as second line)
                mDateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                // Ensure no visual clipping by adding a tiny top padding and keeping font padding
                mDateText.setIncludeFontPadding(true);
                int padTop = Math.max(0, dpToPx(11));
                mDateText.setPadding(
                    mDateText.getPaddingLeft(),
                    padTop,
                    mDateText.getPaddingRight(),
                    mDateText.getPaddingBottom()
                );
            }
        }
        // Toggle clock views
        if (mClock1 != null) mClock1.setVisibility(showClock ? View.VISIBLE : View.GONE);
        if (mClock2 != null) {
            mClock2.setVisibility(showClock ? View.VISIBLE : View.GONE);
            if (showClock) applyQuickspaceClockColor(clockColor);
        }

        // If clock is hidden and the main title is just the date, hide the duplicate second line
        if (!altUI && !showClock && mEventTitle != null) {
            CharSequence title = mEventTitle.getText();
            String formatted = getFormattedDate(false);
            if (title != null && title.toString().contentEquals(formatted)) {
                mEventTitle.setVisibility(View.GONE);
            } else {
                mEventTitle.setVisibility(View.VISIBLE);
            }
        } else if (mEventTitle != null && mEventTitle.getVisibility() != View.VISIBLE) {
            // Restore visibility in other scenarios
            mEventTitle.setVisibility(View.VISIBLE);
        }
    }

    public void applyQuickspaceClockColor(int clockColor) {
        if (clockColor == 0) {
            clockColor = 0xffff0000;
        }
        if (mClock2 != null) {
            mClock2.setTextColor(clockColor);
        }
    }

    private String getFormattedDate(boolean useAlternativeQuickspaceUI) {
        String styleText;
        if (useAlternativeQuickspaceUI) {
            styleText = getContext().getString(R.string.quickspace_date_format_minimalistic);
        } else {
            styleText = getContext().getString(R.string.quickspace_date_format);
        }
        DateFormat dateFormat = DateFormat.getInstanceForSkeleton(styleText, java.util.Locale.getDefault());
        dateFormat.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        return dateFormat.format(System.currentTimeMillis());
    }

    private void prepareLayout(boolean useAlternativeQuickspaceUI) {
        // Clear existing views and references before inflating new ones
        clearDrawables();
        clearTextViews();
        clearClickListeners();
        
        int indexOfChild = indexOfChild(mQuickspaceContent);
        if (mQuickspaceContent != null) {
            removeView(mQuickspaceContent);
        }
        
        // Inflate new layout
        if (useAlternativeQuickspaceUI) {
            addView(LayoutInflater.from(getContext()).inflate(R.layout.quickspace_alternate_double, this, false), indexOfChild);
        } else {
            addView(LayoutInflater.from(getContext()).inflate(R.layout.quickspace_doubleline, this, false), indexOfChild);
        }

        loadViews();
        getQuickSpaceView();
        setBackgroundResource(mQuickspaceBackgroundRes);
    }

    private void getQuickSpaceView() {
        if (mQuickspaceContent.getVisibility() != View.VISIBLE) {
        	 mQuickspaceContent.setVisibility(View.VISIBLE);
            mQuickspaceContent.setAlpha(0.0f);
            // Set listener to null to prevent animator from retaining view reference
            mQuickspaceContent.animate()
                .setDuration(200)
                .alpha(1.0f)
                .setListener(null)
                .setUpdateListener(null);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        mAttached = true;
        // Resume controller only when attached to window (visible)
        if (mController != null && mFinishedInflate) {
            mController.addListener(this);
            mController.onResume();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mController == null) return;
        // Clear all resources first
        clearDrawables();
        clearTextViews();
        setBackground(null);
        super.onDetachedFromWindow();
        if (!mAttached) return;
        
        mAttached = false;
        // Aggressively cleanup when detached from window to free memory
        mController.onPause();
        mController.removeListener(this);
    }

    public boolean isPackageEnabled(String pkgName, Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(pkgName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (mController == null) return;
        loadViews();
        mFinishedInflate = true;
        mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
        mBubbleTextView.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        mBubbleTextView.setContentDescription("");
        if (isAttachedToWindow() && mController != null) {
            mController.addListener(this);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                if (child.getId() == R.id.dummyBubbleTextView) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                } else {
                    int childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    measureChildWithMargins(child, widthMeasureSpec, 0, childHeightMeasureSpec, 0);
                }
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                childState = combineMeasuredStates(childState, child.getMeasuredState());
            }
        }

        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();

        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(
                resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT)
        );
    }

    @Override
    public void onLayout(boolean b, int n, int n2, int n3, int n4) {
        super.onLayout(b, n, n2, n3, n4);
    }

    public void onPause() {
        if (mController != null) mController.onPause();
    }

    public void onResume() {
        // Only resume if attached to window and visible
        if (mController != null && mFinishedInflate && mAttached) {
            mController.addListener(this);
            mController.onResume();
        }
    }

    public void onDestroy() {
        if (mController == null) return;
        mController.onDestroy();
        mController = null;
        mBubbleTextView = null;
        mQuickspaceContent = null;
        mEventSubIcon = null;
        mNowPlayingIcon = null;
        mEventTitleSub = null;
        mEventTitleSubColored = null;
        mGreetingsExt = null;
        mGreetingsExtClock = null;
        mWeatherContentSub = null;
        mWeatherIconSub = null;
        mWeatherTempSub = null;
        mEventTitle = null;
    }

    /**
     * Aggressively clear all drawable references to free bitmaps and prevent memory leaks
     */
    private void clearDrawables() {
        // Clear cached drawable references
        if (mLastWeatherIcon != null) {
            mLastWeatherIcon.setCallback(null);
            mLastWeatherIcon = null;
        }
        if (mLastEventIcon != null) {
            mLastEventIcon.setCallback(null);
            mLastEventIcon = null;
        }
        
        // Clear ImageView drawables
        if (mEventSubIcon != null) {
            mEventSubIcon.setImageDrawable(null);
            mEventSubIcon.setImageBitmap(null);
        }
        if (mNowPlayingIcon != null) {
            mNowPlayingIcon.setImageDrawable(null);
            mNowPlayingIcon.setImageBitmap(null);
        }
        if (mWeatherIconSub != null) {
            mWeatherIconSub.setImageDrawable(null);
            mWeatherIconSub.setImageBitmap(null);
        }
        // Clear background to release drawable cache
        setBackground(null);
    }

    /**
     * Clear all text content to release cached text spans and reduce memory
     */
    private void clearTextViews() {
        // Clear cached text
        mLastWeatherTemp = null;
        
        if (mEventTitle != null) {
            mEventTitle.setText(null);
        }
        if (mEventTitleSub != null) {
            mEventTitleSub.setText(null);
        }
        if (mEventTitleSubColored != null) {
            mEventTitleSubColored.setText(null);
        }
        if (mWeatherTempSub != null) {
            mWeatherTempSub.setText(null);
        }
        if (mGreetingsExt != null) {
            mGreetingsExt.setText(null);
        }
        if (mGreetingsExtClock != null) {
            mGreetingsExtClock.setText(null);
        }
    }

    /**
     * Clear all click listeners to prevent memory leaks from retained callbacks
     */
    private void clearClickListeners() {
        if (mEventTitle != null) {
            mEventTitle.setOnClickListener(null);
        }
        if (mEventTitleSub != null) {
            mEventTitleSub.setOnClickListener(null);
        }
        if (mEventTitleSubColored != null) {
            mEventTitleSubColored.setOnClickListener(null);
        }
        if (mEventSubIcon != null) {
            mEventSubIcon.setOnClickListener(null);
        }
        if (mNowPlayingIcon != null) {
            mNowPlayingIcon.setOnClickListener(null);
        }
        if (mWeatherContentSub != null) {
            mWeatherContentSub.setOnClickListener(null);
        }
        if (mGreetingsExt != null) {
            mGreetingsExt.setOnClickListener(null);
        }
        if (mGreetingsExtClock != null) {
            mGreetingsExtClock.setOnClickListener(null);
        }
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }
}
