package com.android.launcher3.qsb;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiTranslateDelegate;

public class QsbLayout extends FrameLayout implements Reorderable, SharedPreferences.OnSharedPreferenceChangeListener {

    ImageView mAssistantIcon;
    ImageView mGoogleIcon;
    ImageView mLensIcon;
    ImageView mAiModeButton;
    Context mContext;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;

    public QsbLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public QsbLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAssistantIcon = findViewById(R.id.mic_icon);
        mGoogleIcon = findViewById(R.id.g_icon);
        mLensIcon = findViewById(R.id.lens_icon);
        mAiModeButton = findViewById(R.id.ai_mode_button);
        setIcons();
        LauncherPrefs.getPrefs(mContext).registerOnSharedPreferenceChangeListener(this);

        String searchPackage = Utilities.getSearchWidgetPackageName(mContext);
        if (searchPackage != null) {
            setOnClickListener(view -> {
                launchSearchActivity(searchPackage);
            });
        }

        if (Utilities.isGSAEnabled(mContext)) {
            enableLensIcon();
        }
        
        // Set the custom background drawable
        post(() -> {
            View parent = (View) getParent();
            if (parent != null) {
                QsbOuterDrawable customDrawable = new QsbOuterDrawable(mContext);
                parent.setBackground(customDrawable);
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child != null) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LauncherPrefs.getPrefs(mContext).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(LauncherPrefs.DOCK_MUSIC_SEARCH.getSharedPrefKey())
                || key.equals(LauncherPrefs.DOCK_THEME.getSharedPrefKey())) {
            setIcons();
        } else if (key.equals(LauncherPrefs.QSB_OUTER_OPACITY.getSharedPrefKey())) {
            // Update the drawable if it's already set
            View parent = (View) getParent();
            if (parent != null && parent.getBackground() instanceof QsbOuterDrawable) {
                ((QsbOuterDrawable) parent.getBackground()).updateOpacity();
            }
        }
    }

    private void setIcons() {
        boolean isThemed = LauncherPrefs.DOCK_THEME.get(mContext);
        boolean isMusicSearch = Utilities.isAiMusicSearchEnabled(mContext);

        if (isThemed) {
            mAssistantIcon.setImageResource(R.drawable.ic_mic_themed_revamped);
            mGoogleIcon.setImageResource(R.drawable.ic_super_g_themed_revamped);
            mLensIcon.setImageResource(R.drawable.ic_lens_themed_revamped);
            mAiModeButton.setImageResource(isMusicSearch ? R.drawable.ic_music_themed_revamped : R.drawable.ic_ai_mode_themed_revamped);
        } else {
            mAssistantIcon.setImageResource(R.drawable.ic_mic_color_revamped);
            mGoogleIcon.setImageResource(R.drawable.ic_super_g_color_revamped);
            mLensIcon.setImageResource(R.drawable.ic_lens_color_revamped);
            mAiModeButton.setImageResource(isMusicSearch ? R.drawable.ic_music_color_revamped : R.drawable.ic_ai_mode_color_revamped);
        }
    }

    private void launchSearchActivity(String searchPackage) {
        // Try GLOBAL_SEARCH first (standard Android search intent)
        try {
            Intent intent = new Intent("android.search.action.GLOBAL_SEARCH")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setPackage(searchPackage);
            mContext.startActivity(intent);
            return;
        } catch (ActivityNotFoundException e) {
            // GLOBAL_SEARCH not supported, try fallback approaches
            Log.d("QsbLayout", "GLOBAL_SEARCH not supported by " + searchPackage);
        }

        // Fallback 1: Try launching the app's main launcher activity
        try {
            Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(searchPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                mContext.startActivity(intent);
                return;
            }
        } catch (ActivityNotFoundException e) {
            Log.d("QsbLayout", "Launch intent not found for " + searchPackage);
        }

        // Fallback 2: Try ACTION_VIEW with the package
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setPackage(searchPackage);
            mContext.startActivity(intent);
            return;
        } catch (ActivityNotFoundException e) {
            Log.d("QsbLayout", "ACTION_VIEW not found for " + searchPackage);
        }

        // If all fallbacks fail, show a toast message
        Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        Log.e("QsbLayout", "No search activity found for package: " + searchPackage);
    }

    private void enableLensIcon() {
        mLensIcon.setVisibility(View.VISIBLE);
        mLensIcon.setOnClickListener(view -> {
            Intent lensIntent = new Intent();
            lensIntent.setAction(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setData(Uri.parse(Utilities.LENS_URI))
                    .putExtra("LensHomescreenShortcut", true);
            mContext.startActivity(lensIntent);
        });
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

}