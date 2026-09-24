/*
 * Copyright (C) 2021-2026 crDroid Android Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.util.Log;
import android.view.View.OnClickListener;

import com.android.internal.util.infinity.OmniJawsClient;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.MediaSessionTrackHelper;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickspaceController implements OmniJawsClient.OmniJawsObserver,
        MediaSessionTrackHelper.MediaMetadataListener {

    private static final String TAG = "Launcher3:QuickspaceController";

    private final List<OnDataListener> mListeners =
        Collections.synchronizedList(new ArrayList<>());
    private final Context mContext;
    private final MediaSessionTrackHelper mMediaSessionHelper;
    private final Map<String, Integer> mConditionMap;
    private QuickEventsController mEventsController;
    private OmniJawsClient mWeatherClient;
    private volatile OmniJawsClient.WeatherInfo mWeatherInfo;
    private volatile Drawable mConditionImage;
    private volatile boolean mOmniJawsEnabled;

    private final Handler mBgHandler = UI_HELPER_EXECUTOR.getHandler();

    private Runnable mOnDataUpdatedRunnable = new Runnable() {
            @Override
            public void run() {
                for (OnDataListener list : mListeners) {
                    list.onDataUpdated();
                }
            }
        };

    private final Runnable mWeatherRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final OmniJawsClient client = mWeatherClient;
                    if (client == null) return;
                    final boolean enabled = client.isOmniJawsEnabled(mContext);
                    mOmniJawsEnabled = enabled;
                    if (!enabled) {
                        mWeatherInfo = null;
                        mConditionImage = null;
                        notifyListeners();
                        return;
                    }
                    client.queryWeather(mContext);
                    final OmniJawsClient.WeatherInfo info = client.getWeatherInfo();
                    mWeatherInfo = info;
                    if (info != null) {
                        mConditionImage = client.getWeatherConditionImage(mContext, info.conditionCode);
                    } else {
                        mConditionImage = null;
                    }
                    notifyListeners();
                } catch (Exception e) {
                    Log.w(TAG, "weather update failed", e);
                }
            }
        };

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mConditionMap = initializeConditionMap();
        mEventsController = new QuickEventsController(context);
        mWeatherClient = OmniJawsClient.get();
        mMediaSessionHelper = MediaSessionTrackHelper.getInstance(context);
    }

    private void addWeatherProvider() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return;
        mWeatherClient.addObserver(mContext, this);
        queryAndUpdateWeather();
    }

    public void addListener(OnDataListener listener) {
        mListeners.add(listener);
        addWeatherProvider();
        mMediaSessionHelper.addMediaMetadataListener(this);
        listener.onDataUpdated();
    }

    public void removeListener(OnDataListener listener) {
        mListeners.remove(listener);
        // Only cleanup weather observer if no more listeners
        if (mListeners.isEmpty()) {
            mBgHandler.removeCallbacks(mWeatherRunnable);
            if (mWeatherClient != null) {
                mWeatherClient.removeObserver(mContext, this);
            }
            mMediaSessionHelper.removeMediaMetadataListener(this);
            mOmniJawsEnabled = false;
        }
    }

    public boolean isQuickEvent() {
        return mEventsController.isQuickEvent();
    }

    public QuickEventsController getEventController() {
        return mEventsController;
    }

    public boolean isWeatherAvailable() {
        return mWeatherClient != null && mOmniJawsEnabled;
    }

    public Drawable getWeatherIcon() {
        return mConditionImage;
    }

    public String getWeatherTemp() {
        final OmniJawsClient.WeatherInfo info = mWeatherInfo;
        if (info == null) return null;

        boolean shouldShowCity = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY.get(mContext);
        boolean showWeatherText = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(mContext);

        StringBuilder weatherTemp = new StringBuilder();
        if (shouldShowCity) {
            weatherTemp.append(info.city).append(" ");
        }
        weatherTemp.append(info.temp)
                   .append(info.tempUnits);

        if (showWeatherText) {
            weatherTemp.append(" • ").append(getConditionText(info.condition));
        }

        return weatherTemp.toString();
    }

    private String getConditionText(String input) {
        if (input == null || input.isEmpty()) return "";

        Locale locale = mContext.getResources().getConfiguration().getLocales().get(0);
        boolean isEnglish = locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("en");
        String lowerCaseInput = input.toLowerCase();

        if (!isEnglish) {
            for (Map.Entry<String, Integer> entry : mConditionMap.entrySet()) {
                if (lowerCaseInput.contains(entry.getKey())) {
                    return mContext.getResources().getString(entry.getValue());
                }
            }
        }
        return capitalizeWords(lowerCaseInput);
    }

    private Map<String, Integer> initializeConditionMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("clouds", R.string.quick_event_weather_clouds);
        map.put("rain", R.string.quick_event_weather_rain);
        map.put("clear", R.string.quick_event_weather_clear);
        map.put("storm", R.string.quick_event_weather_storm);
        map.put("snow", R.string.quick_event_weather_snow);
        map.put("wind", R.string.quick_event_weather_wind);
        map.put("mist", R.string.quick_event_weather_mist);
        return map;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] words = input.split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                           .append(word.substring(1).toLowerCase())
                           .append(" ");
            }
        }
        return capitalized.toString().trim();
    }

    public void onPause() {
        cancelListeners();
    }

    public void onResume() {
        mEventsController.onResume();
        updateMediaController();
        notifyListeners();
    }

    private void cancelListeners() {
        mBgHandler.removeCallbacks(mWeatherRunnable);
        if (mEventsController != null) {
            mEventsController.onPause();
        }
        // Clear all listeners safely
        synchronized (mListeners) {
            for (OnDataListener listener : new ArrayList<>(mListeners)) {
                if (mWeatherClient != null) {
                    mWeatherClient.removeObserver(mContext, this);
                }
                mListeners.remove(listener);
            }
        }
        unregisterMediaController();
    }

    public void onDestroy() {
        cancelListeners();
        mBgHandler.removeCallbacks(mWeatherRunnable);
        // Aggressively clean up all drawable and bitmap references
        if (mConditionImage != null) {
            mConditionImage.setCallback(null);
            mConditionImage = null;
        }
        mWeatherClient = null;
        mWeatherInfo = null;
        mOmniJawsEnabled = false;
        if (mEventsController != null) {
            mEventsController.cleanup();
            mEventsController = null;
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        Log.d(TAG, "weatherError " + errorReason);
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            notifyListeners();
        }
    }

    @Override
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        mBgHandler.removeCallbacks(mWeatherRunnable);
        mBgHandler.post(mWeatherRunnable);
    }

    public void notifyListeners() {
        MAIN_EXECUTOR
            .getHandler()
            .post(mOnDataUpdatedRunnable);
    }

    private void unregisterMediaController() {
        mMediaSessionHelper.removeMediaMetadataListener(this);
    }

    private void updateMediaController() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) {
            unregisterMediaController();
            return;
        }
        MediaMetadata mediaMetadata = mMediaSessionHelper.getCurrentMediaMetadata();
        boolean isPlaying = mMediaSessionHelper.isMediaPlaying();
        String trackArtist = isPlaying && mediaMetadata != null ?
                mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : "";
        String trackTitle = isPlaying && mediaMetadata != null ?
                mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "";
        Drawable mediaIcon = mMediaSessionHelper.getMediaAppIcon();
        OnClickListener launchMediaApp =
                view -> mMediaSessionHelper.launchMediaApp();
        mEventsController.setMediaInfo(trackTitle, trackArtist, isPlaying,
                mediaIcon, launchMediaApp);
        mEventsController.updateQuickEvents();
        notifyListeners();
    }

    @Override
    public void onMediaMetadataChanged() {
        updateMediaController();
    }

    @Override
    public void onPlaybackStateChanged() {
        updateMediaController();
    }
}
