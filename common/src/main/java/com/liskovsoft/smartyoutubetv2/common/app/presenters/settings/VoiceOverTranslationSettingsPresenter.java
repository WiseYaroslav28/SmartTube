package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.VoiceOverTranslationData;
import java.util.ArrayList;
import java.util.List;

public class VoiceOverTranslationSettingsPresenter extends BasePresenter<Void> {
    private final VoiceOverTranslationData mTranslationData;

    public VoiceOverTranslationSettingsPresenter(Context context) {
        super(context);
        mTranslationData = VoiceOverTranslationData.instance(context);
    }

    public static VoiceOverTranslationSettingsPresenter instance(Context context) {
        return new VoiceOverTranslationSettingsPresenter(context);
    }

    public void show(Runnable onFinish) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendTranslationSwitch(settingsPresenter);
        appendPauseOnLoadingSwitch(settingsPresenter);
        appendLivelyVoiceSwitch(settingsPresenter);
        appendProxySwitch(settingsPresenter);
        appendSourceLanguageCategory(settingsPresenter);
        appendVolumeCategory(settingsPresenter);
        appendTranslationVolumeCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.vot_title), onFinish);
    }

    public void show() {
        show(null);
    }

    private void appendTranslationSwitch(AppDialogPresenter settingsPresenter) {
        OptionItem translationOption = UiOptionItem.from(
                getContext().getString(R.string.vot_enable),
                option -> mTranslationData.setTranslationEnabled(option.isSelected()),
                mTranslationData.isTranslationEnabled()
        );
        settingsPresenter.appendSingleSwitch(translationOption);
    }

    private void appendPauseOnLoadingSwitch(AppDialogPresenter settingsPresenter) {
        OptionItem pauseOption = UiOptionItem.from(
                getContext().getString(R.string.vot_pause_on_loading),
                option -> mTranslationData.setPauseOnTranslationLoadingEnabled(option.isSelected()),
                mTranslationData.isPauseOnTranslationLoadingEnabled()
        );
        settingsPresenter.appendSingleSwitch(pauseOption);
    }

    private void appendLivelyVoiceSwitch(AppDialogPresenter settingsPresenter) {
        OptionItem livelyOption = UiOptionItem.from(
                getContext().getString(R.string.vot_use_lively),
                option -> mTranslationData.setLivelyVoiceEnabled(option.isSelected()),
                mTranslationData.isLivelyVoiceEnabled()
        );
        settingsPresenter.appendSingleSwitch(livelyOption);
    }

    private void appendProxySwitch(AppDialogPresenter settingsPresenter) {
        OptionItem proxyOption = UiOptionItem.from(
                getContext().getString(R.string.vot_use_proxy),
                option -> mTranslationData.setProxyEnabled(option.isSelected()),
                mTranslationData.isProxyEnabled()
        );
        settingsPresenter.appendSingleSwitch(proxyOption);
    }

    private void appendSourceLanguageCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        String currentLang = mTranslationData.getSourceLanguage();

        options.add(UiOptionItem.from(getContext().getString(R.string.vot_source_lang_auto),
                option -> mTranslationData.setSourceLanguage("auto"), "auto".equals(currentLang)));
        options.add(UiOptionItem.from("English",
                option -> mTranslationData.setSourceLanguage("en"), "en".equals(currentLang)));
        options.add(UiOptionItem.from("中文 (Chinese)",
                option -> mTranslationData.setSourceLanguage("zh"), "zh".equals(currentLang)));
        options.add(UiOptionItem.from("Deutsch (German)",
                option -> mTranslationData.setSourceLanguage("de"), "de".equals(currentLang)));
        options.add(UiOptionItem.from("Français (French)",
                option -> mTranslationData.setSourceLanguage("fr"), "fr".equals(currentLang)));
        options.add(UiOptionItem.from("Español (Spanish)",
                option -> mTranslationData.setSourceLanguage("es"), "es".equals(currentLang)));
        options.add(UiOptionItem.from("Italiano (Italian)",
                option -> mTranslationData.setSourceLanguage("it"), "it".equals(currentLang)));
        options.add(UiOptionItem.from("日本語 (Japanese)",
                option -> mTranslationData.setSourceLanguage("ja"), "ja".equals(currentLang)));
        options.add(UiOptionItem.from("한국어 (Korean)",
                option -> mTranslationData.setSourceLanguage("ko"), "ko".equals(currentLang)));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.vot_source_lang), options);
    }



    private void appendVolumeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        int currentVolume = mTranslationData.getVideoVolume();

        List<Integer> volumes = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            volumes.add(i);
        }
        for (int i = 25; i <= 40; i += 5) {
            volumes.add(i);
        }
        int[] restVolumes = {50, 60, 70, 80, 90, 100};
        for (int v : restVolumes) {
            volumes.add(v);
        }

        for (int vol : volumes) {
            options.add(UiOptionItem.from(vol + "%",
                    option -> mTranslationData.setVideoVolume(vol), vol == currentVolume));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.vot_volume), options);
    }

    private void appendTranslationVolumeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        int currentVolume = mTranslationData.getTranslationVolume();

        int[] volumes = {30, 50, 70, 80, 90, 100, 110, 120, 130, 150, 180, 200};
        for (int vol : volumes) {
            options.add(UiOptionItem.from(vol + "%",
                    option -> mTranslationData.setTranslationVolume(vol), vol == currentVolume));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.vot_translation_volume), options);
    }
}
