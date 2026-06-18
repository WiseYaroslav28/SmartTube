package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

public class VoiceOverTranslationData implements ProfileChangeListener {
    private static final String TRANSLATION_ENABLED = "vot_enabled";
    private static final String PAUSE_ON_LOADING = "vot_pause_on_loading";
    private static final String TRANSLATION_VOLUME = "vot_translation_volume";
    private static final String VIDEO_VOLUME = "vot_video_volume";
    private static final String VOICE_NAME = "vot_voice_name";
    private static final String TRANSLATION_LANG = "vot_translation_lang";
    private static final String SOURCE_LANG = "vot_source_lang";
    private static final String USE_LIVELY = "vot_use_lively";
    private static final String USE_PROXY = "vot_use_proxy";

    @SuppressLint("StaticFieldLeak")
    private static VoiceOverTranslationData sInstance;
    private final AppPrefs mPrefs;

    private VoiceOverTranslationData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
    }

    public static VoiceOverTranslationData instance(Context context) {
        if (sInstance == null) {
            sInstance = new VoiceOverTranslationData(context.getApplicationContext());
        }
        return sInstance;
    }

    public boolean isTranslationEnabled() {
        return mPrefs.getBoolean(TRANSLATION_ENABLED, false);
    }

    public void setTranslationEnabled(boolean enable) {
        mPrefs.putBoolean(TRANSLATION_ENABLED, enable);
    }

    public boolean isPauseOnTranslationLoadingEnabled() {
        return mPrefs.getBoolean(PAUSE_ON_LOADING, true); // По умолчанию true
    }

    public void setPauseOnTranslationLoadingEnabled(boolean enable) {
        mPrefs.putBoolean(PAUSE_ON_LOADING, enable);
    }

    public int getTranslationVolume() {
        return mPrefs.getInt(TRANSLATION_VOLUME, 100);
    }

    public void setTranslationVolume(int volume) {
        mPrefs.putInt(TRANSLATION_VOLUME, volume);
    }

    public int getVideoVolume() {
        return mPrefs.getInt(VIDEO_VOLUME, 20); // По умолчанию приглушаем оригинальное видео до 20%
    }

    public void setVideoVolume(int volume) {
        mPrefs.putInt(VIDEO_VOLUME, volume);
    }

    public String getVoiceName() {
        return mPrefs.getString(VOICE_NAME, "zahar"); // По умолчанию Захар
    }

    public void setVoiceName(String voiceName) {
        mPrefs.putString(VOICE_NAME, voiceName);
    }

    public String getTranslationLanguage() {
        return mPrefs.getString(TRANSLATION_LANG, "ru");
    }

    public void setTranslationLanguage(String lang) {
        mPrefs.putString(TRANSLATION_LANG, lang);
    }

    public String getSourceLanguage() {
        return mPrefs.getString(SOURCE_LANG, "auto");
    }

    public void setSourceLanguage(String lang) {
        mPrefs.putString(SOURCE_LANG, lang);
    }

    public boolean isLivelyVoiceEnabled() {
        return mPrefs.getBoolean(USE_LIVELY, true);
    }

    public void setLivelyVoiceEnabled(boolean enable) {
        mPrefs.putBoolean(USE_LIVELY, enable);
    }

    public boolean isProxyEnabled() {
        return mPrefs.getBoolean(USE_PROXY, false);
    }

    public void setProxyEnabled(boolean enable) {
        mPrefs.putBoolean(USE_PROXY, enable);
    }

    @Override
    public void onProfileChanged() {
        // Настройки автоматически переключаются в AppPrefs
    }
}
