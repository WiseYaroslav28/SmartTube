package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.VoiceOverTranslationSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.prefs.VoiceOverTranslationData;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.VolumeBooster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VoiceOverTranslationController extends BasePlayerController {
    public static final int STATE_OFF = 0;
    public static final int STATE_WAITING = 1;
    public static final int STATE_FINISHED = 2;

    private static final String TAG = VoiceOverTranslationController.class.getSimpleName();
    private static final String HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 YaBrowser/26.4.1.1026 Yowser/2.5 Safari/537.36";
    private static final String COMPONENT_VERSION = "26.4.1.1026";
    private static final MediaType PROTO_MEDIA_TYPE = MediaType.parse("application/x-protobuf");

    private SimpleExoPlayer mAudioPlayer;
    private VolumeBooster mVolumeBooster;
    private VoiceOverTranslationData mTranslationData;
    private OkHttpClient mHttpClient;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private String mCurrentVideoId;
    private double mCurrentDuration;
    private boolean mIsLoading;
    private boolean mIsReady;
    private String mAudioUrl;

    private String mSessionSecret;
    private long mSessionExpires;
    private String mUUID;
    private int mCurrentAppliedTranslationVolume = -1;
    private int mCurrentButtonState = STATE_OFF;

    private final Runnable mSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsReady && mAudioPlayer != null && getPlayer() != null) {
                syncPlayback();
                mMainHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onInit() {
        Log.d(TAG, "onInit: controller initialized");
        mTranslationData = VoiceOverTranslationData.instance(getContext());
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        mUUID = generateUUID();
    }

    @Override
    public void onNewVideo(Video item) {
        Log.d(TAG, "onNewVideo: item=" + (item != null ? item.videoId : "null"));
        resetTranslation();
        if (item != null) {
            mCurrentVideoId = item.videoId;
            mCurrentDuration = (double) item.getDurationMs() / 1000.0;
        }
        updateButtonState(STATE_OFF);
    }

    @Override
    public void onVideoLoaded(Video item) {
        Log.d(TAG, "onVideoLoaded: item=" + (item != null ? item.videoId : "null") + ", isTranslationEnabled=" + mTranslationData.isTranslationEnabled());
        if (item != null) {
            mCurrentVideoId = item.videoId;
            mCurrentDuration = (double) item.getDurationMs() / 1000.0;
        }

        if (mTranslationData.isTranslationEnabled() && mCurrentVideoId != null) {
            // Авто-старт перевода
            updateButtonState(STATE_WAITING);
            startTranslationProcess();
        } else {
            updateButtonState(STATE_OFF);
        }
    }

    @Override
    public void onPlay() {
        if (mIsReady && mAudioPlayer != null) {
            mAudioPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    public void onPause() {
        if (mIsReady && mAudioPlayer != null) {
            mAudioPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    public void onSeekEnd() {
        if (mIsReady && mAudioPlayer != null && getPlayer() != null) {
            mAudioPlayer.seekTo(getPlayer().getPositionMs());
        }
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        if (mIsReady && mAudioPlayer != null) {
            mAudioPlayer.seekTo(positionMs);
        }
    }

    @Override
    public void onSpeedChanged(float speed) {
        if (mIsReady && mAudioPlayer != null) {
            mAudioPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }

    @Override
    public void onEngineReleased() {
        resetTranslation();
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        Log.d(TAG, "onButtonClicked: buttonId=" + buttonId + ", expectedId=" + R.id.action_voice_over_translation);
        if (buttonId == R.id.action_voice_over_translation) {
            if (mIsReady || mIsLoading) {
                // Выключаем перевод
                resetTranslation();
                updateButtonState(STATE_OFF);
                restoreVideoVolume();
                showToast("Голосовой перевод выключен");
            } else {
                // Включаем перевод
                updateButtonState(STATE_WAITING);
                startTranslationProcess();
            }
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        Log.d(TAG, "onButtonLongClicked: buttonId=" + buttonId + ", expectedId=" + R.id.action_voice_over_translation);
        if (buttonId == R.id.action_voice_over_translation) {
            VoiceOverTranslationSettingsPresenter.instance(getContext()).show(() -> {
                // После изменения настроек перезапускаем видео/перевод если необходимо
                if (getPlayer() != null) {
                    onVideoLoaded(getPlayer().getVideo());
                }
            });
        }
    }

    private void updateButtonState(int state) {
        mCurrentButtonState = state;
        if (getPlayer() != null) {
            getPlayer().setButtonState(R.id.action_voice_over_translation, state);
        }
    }

    @Override
    public void onControlsShown(boolean shown) {
        if (shown) {
            updateButtonState(mCurrentButtonState);
        }
    }

    private void resetTranslation() {
        Log.d(TAG, "resetTranslation: state reset (was loading=" + mIsLoading + ", ready=" + mIsReady + ")");
        mIsLoading = false;
        mIsReady = false;
        mAudioUrl = null;
        mCurrentAppliedTranslationVolume = -1;
        mMainHandler.removeCallbacks(mSyncRunnable);
        releaseAudioPlayer();
    }

    private void releaseAudioPlayer() {
        if (mVolumeBooster != null) {
            try {
                mVolumeBooster.release();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка освобождения VolumeBooster", e);
            }
            mVolumeBooster = null;
        }
        if (mAudioPlayer != null) {
            try {
                mAudioPlayer.stop();
                mAudioPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка освобождения ExoPlayer перевода", e);
            }
            mAudioPlayer = null;
        }
    }

    private void restoreVideoVolume() {
        if (getPlayer() != null) {
            getPlayer().setVolume(1.0f);
        }
    }

    private void syncPlayback() {
        if (mAudioPlayer == null || getPlayer() == null) {
            return;
        }

        // Синхронизация паузы/воспроизведения
        boolean isVideoPlaying = getPlayer().isPlaying();
        if (mAudioPlayer.getPlayWhenReady() != isVideoPlaying) {
            mAudioPlayer.setPlayWhenReady(isVideoPlaying);
        }

        // Синхронизация перемотки (только если аудио-плеер готов к воспроизведению)
        if (mAudioPlayer.getPlaybackState() == com.google.android.exoplayer2.Player.STATE_READY) {
            long videoPos = getPlayer().getPositionMs();
            long audioPos = mAudioPlayer.getCurrentPosition();
            long diff = Math.abs(videoPos - audioPos);
            if (diff > 500) {
                Log.d(TAG, "syncPlayback: drift detected (" + diff + "ms), seeking audio to " + videoPos);
                mAudioPlayer.seekTo(videoPos);
            }
        }

        // Синхронизация громкости видео
        float targetVideoVol = (float) mTranslationData.getVideoVolume() / 100f;
        if (getPlayer().getVolume() != targetVideoVol) {
            getPlayer().setVolume(targetVideoVol);
        }

        // Синхронизация громкости перевода
        int transVolume = mTranslationData.getTranslationVolume();
        if (transVolume != mCurrentAppliedTranslationVolume) {
            mCurrentAppliedTranslationVolume = transVolume;
            if (transVolume > 100) {
                float boostFactor = (float) transVolume / 100f;
                if (mVolumeBooster != null) {
                    mAudioPlayer.removeAudioListener(mVolumeBooster);
                    mVolumeBooster.release();
                }
                mAudioPlayer.setVolume(1.0f);
                mVolumeBooster = new VolumeBooster(true, boostFactor, mAudioPlayer);
                mAudioPlayer.addAudioListener(mVolumeBooster);
            } else {
                if (mVolumeBooster != null) {
                    mAudioPlayer.removeAudioListener(mVolumeBooster);
                    mVolumeBooster.release();
                    mVolumeBooster = null;
                }
                float targetTransVol = (float) transVolume / 100f;
                mAudioPlayer.setVolume(targetTransVol);
            }
        }
    }

    private String getBackendHost() {
        return mTranslationData.isProxyEnabled() ? "https://vot-worker.toil.cc" : "https://api.browser.yandex.ru";
    }

    private void startTranslationProcess() {
        if (mCurrentVideoId == null) {
            updateButtonState(STATE_OFF);
            return;
        }

        Video video = getVideo();
        if (video != null && video.isLive) {
            handleError("Перевод прямых трансляций не поддерживается", null);
            return;
        }

        if (getPlayer() != null) {
            long durationMs = getPlayer().getDurationMs();
            Log.d(TAG, "startTranslationProcess: durationMs from player = " + durationMs);
            if (durationMs > 0) {
                mCurrentDuration = (double) durationMs / 1000.0;
            } else if (video != null && video.getDurationMs() > 0) {
                mCurrentDuration = (double) video.getDurationMs() / 1000.0;
                Log.d(TAG, "startTranslationProcess: durationMs from getVideo() = " + video.getDurationMs());
            }
        }

        if (mCurrentDuration <= 0 || mCurrentDuration == -0.001) {
            Log.d(TAG, "startTranslationProcess: duration is not yet available, retrying in 1 second...");
            mMainHandler.postDelayed(() -> startTranslationProcess(), 1000);
            return;
        }

        Log.d(TAG, "startTranslationProcess: videoId=" + mCurrentVideoId + ", duration=" + mCurrentDuration + ", isPauseEnabled=" + mTranslationData.isPauseOnTranslationLoadingEnabled());

        mIsLoading = true;

        if (mTranslationData.isPauseOnTranslationLoadingEnabled() && getPlayer() != null) {
            getPlayer().setPlayWhenReady(false);
        }

        // Проверяем актуальность сессии
        if (mSessionSecret == null || System.currentTimeMillis() > mSessionExpires) {
            createSessionAndRequestTranslation();
        } else {
            requestTranslationOnly();
        }
    }

    private void createSessionAndRequestTranslation() {
        Log.d(TAG, "createSessionAndRequestTranslation: uuid=" + mUUID);
        try {
            byte[] sessionBody = encodeSessionRequest(mUUID, "video-translation");
            String signature = getSignature(sessionBody);

            Request sessionReq = new Request.Builder()
                    .url(getBackendHost() + "/session/create")
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-protobuf")
                    .header("Vtrans-Signature", signature)
                    .post(okhttp3.RequestBody.create(PROTO_MEDIA_TYPE, sessionBody))
                    .build();

            mHttpClient.newCall(sessionReq).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handleError("Ошибка создания сессии Яндекса", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        handleError("Сбой создания сессии, HTTP: " + response.code(), null);
                        return;
                    }

                    try {
                        byte[] bodyBytes = response.body().bytes();
                        SessionResponse sessionData = decodeSessionResponse(bodyBytes);
                        mSessionSecret = sessionData.secretKey;
                        mSessionExpires = System.currentTimeMillis() + (sessionData.expires * 1000L) - 60000; // зазор 1 мин

                        mMainHandler.post(() -> requestTranslationOnly());
                    } catch (Exception e) {
                        handleError("Ошибка декодирования сессии", e);
                    }
                }
            });
        } catch (Exception e) {
            handleError("Сбой подготовки запроса сессии", e);
        }
    }

    private void requestTranslationOnly() {
        Log.d(TAG, "requestTranslationOnly: sessionSecret=" + (mSessionSecret != null ? "exists" : "null"));
        if (!mIsLoading) return;

        try {
            String videoUrl = "https://youtu.be/" + mCurrentVideoId;
            byte[] transBody = encodeTranslationRequest(videoUrl, mCurrentDuration, mTranslationData.getSourceLanguage(), "ru");
            String transSignature = getSignature(transBody);

            String path = "/video-translation/translate";
            String token = mUUID + ":" + path + ":" + COMPONENT_VERSION;
            String tokenSignature = getSignature(token.getBytes(StandardCharsets.UTF_8));

            Request transReq = new Request.Builder()
                    .url(getBackendHost() + path)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-protobuf")
                    .header("Vtrans-Signature", transSignature)
                    .header("Sec-Vtrans-Sk", mSessionSecret)
                    .header("Sec-Vtrans-Token", tokenSignature + ":" + token)
                    .post(okhttp3.RequestBody.create(PROTO_MEDIA_TYPE, transBody))
                    .build();

            Log.d(TAG, "requestTranslationOnly: enqueueing request to " + transReq.url());
            mHttpClient.newCall(transReq).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "requestTranslationOnly onFailure", e);
                    handleError("Ошибка запроса перевода", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "requestTranslationOnly onResponse: code=" + response.code() + ", successful=" + response.isSuccessful());
                    if (!response.isSuccessful()) {
                        handleError("Сбой запроса перевода, HTTP: " + response.code(), null);
                        return;
                    }

                    try {
                        byte[] bodyBytes = response.body().bytes();
                        Log.d(TAG, "requestTranslationOnly onResponse: body length=" + bodyBytes.length);
                        TranslationResponse transData = decodeTranslationResponse(bodyBytes);
                        Log.d(TAG, "requestTranslationOnly decoded: status=" + transData.status + ", url=" + transData.url + ", msg=" + transData.message);

                        mMainHandler.post(() -> {
                            if (!mIsLoading) {
                                Log.d(TAG, "requestTranslationOnly handler callback ignored: mIsLoading is false");
                                return;
                            }

                            if (transData.status == 1 || (transData.url != null && !transData.url.isEmpty())) {
                                // Готово
                                mAudioUrl = transData.url;
                                startAudioPlayback();
                            } else if (transData.status == 2) {
                                // В очереди / ожидание
                                Log.d(TAG, "Перевод обрабатывается, ожидаем: " + transData.remainingTime + " сек");
                                mMainHandler.postDelayed(() -> requestTranslationOnly(), 3000);
                            } else {
                                handleError("Яндекс вернул ошибку перевода: " + transData.message + " (Статус: " + transData.status + ")", null);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "requestTranslationOnly onResponse processing error", e);
                        handleError("Ошибка декодирования ответа перевода", e);
                    }
                }
            });
        } catch (Exception e) {
            handleError("Сбой подготовки запроса перевода", e);
        }
    }

    private void startAudioPlayback() {
        if (!mIsLoading || mAudioUrl == null || getContext() == null) {
            resetTranslation();
            updateButtonState(STATE_OFF);
            restoreVideoVolume();
            return;
        }

        mIsLoading = false;
        mIsReady = true;

        try {
            releaseAudioPlayer();
            mAudioPlayer = ExoPlayerFactory.newSimpleInstance(getContext());

            mAudioPlayer.addListener(new com.google.android.exoplayer2.Player.EventListener() {
                private boolean mFirstReady = true;

                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    Log.d(TAG, "Audio player state changed: playWhenReady=" + playWhenReady + ", state=" + playbackState);
                    if (playbackState == com.google.android.exoplayer2.Player.STATE_READY && mFirstReady) {
                        mFirstReady = false;
                        Log.d(TAG, "Audio player is ready for the first time. Starting playback sync.");
                        mMainHandler.post(() -> {
                            updateButtonState(STATE_FINISHED);
                            if (getPlayer() != null) {
                                // Приглушаем основное видео
                                getPlayer().setVolume((float) mTranslationData.getVideoVolume() / 100f);
                                // Возобновляем видео
                                getPlayer().setPlayWhenReady(true);
                            }
                            if (mAudioPlayer != null) {
                                mAudioPlayer.setPlayWhenReady(getPlayer() != null && getPlayer().isPlaying());
                            }
                            mMainHandler.post(mSyncRunnable);
                            showToast("Перевод готов и запущен");
                        });
                    }
                }
            });

            Uri uri = Uri.parse(mAudioUrl);
            ExoMediaSourceFactory sourceFactory = new ExoMediaSourceFactory(getContext());
            MediaSource mediaSource;
            if (mAudioUrl.contains("m3u8")) {
                mediaSource = sourceFactory.fromHlsPlaylist(mAudioUrl);
            } else {
                mediaSource = sourceFactory.fromUrlList(Collections.singletonList(mAudioUrl));
            }

            mAudioPlayer.prepare(mediaSource);
            
            int transVolume = mTranslationData.getTranslationVolume();
            mCurrentAppliedTranslationVolume = transVolume;
            if (transVolume > 100) {
                float boostFactor = (float) transVolume / 100f;
                mAudioPlayer.setVolume(1.0f);
                mVolumeBooster = new VolumeBooster(true, boostFactor, mAudioPlayer);
                mAudioPlayer.addAudioListener(mVolumeBooster);
            } else {
                mAudioPlayer.setVolume((float) transVolume / 100f);
            }
            
            mAudioPlayer.seekTo(getPlayer().getPositionMs());
            mAudioPlayer.setPlaybackParameters(new PlaybackParameters(getPlayer().getSpeed()));

        } catch (Exception e) {
            handleError("Ошибка запуска аудио плеера перевода", e);
        }
    }

    private void handleError(String message, Throwable error) {
        Log.e(TAG, message, error);
        mMainHandler.post(() -> {
            resetTranslation();
            updateButtonState(STATE_OFF);
            restoreVideoVolume();
            if (getPlayer() != null) {
                getPlayer().setPlayWhenReady(true); // возобновляем видео на ошибках
            }
            showToast(message);
        });
    }

    private void showToast(String text) {
        if (getContext() != null) {
            Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private static String generateUUID() {
        String hexDigits = "0123456789abcdef";
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        for (int i = 0; i < 32; i++) {
            sb.append(hexDigits.charAt(r.nextInt(16)));
        }
        return sb.toString();
    }

    private static String getSignature(byte[] body) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256.init(secretKey);
        byte[] bytes = sha256.doFinal(body);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // --- Proto Encoding Helper ---
    private byte[] encodeSessionRequest(String uuid, String module) throws IOException {
        ProtobufWriter pw = new ProtobufWriter();
        pw.writeString(1, uuid);
        pw.writeString(2, module);
        return pw.toByteArray();
    }

    private byte[] encodeTranslationRequest(String url, double duration, String fromLang, String toLang) throws IOException {
        ProtobufWriter pw = new ProtobufWriter();
        if (getPlayer() != null && getPlayer().getVideo() != null && getPlayer().getVideo().title != null) {
            pw.writeString(1, getPlayer().getVideo().title);
        }
        pw.writeString(3, url);              // tag 3
        if (mUUID != null) {
            pw.writeString(4, mUUID);
        }
        pw.writeBool(5, true);               // firstRequest
        pw.writeDouble(6, duration);         // duration
        pw.writeInt32(7, 1);                 // unknown0
        if ("auto".equals(fromLang)) {
            pw.writeString(8, "");
        } else {
            pw.writeString(8, fromLang);
        }
        pw.writeString(14, toLang);          // responseLanguage
        pw.writeInt32(15, 1);                // unknown2
        pw.writeInt32(16, 2);                // unknown3
        if (mTranslationData.isLivelyVoiceEnabled()) {
            pw.writeBool(18, true);
        }
        return pw.toByteArray();
    }

    // --- Proto Decoding Helper ---
    private static SessionResponse decodeSessionResponse(byte[] data) throws IOException {
        ProtobufReader pr = new ProtobufReader(data);
        SessionResponse resp = new SessionResponse();
        int tag;
        while ((tag = pr.readTag()) != 0) {
            int fieldNum = tag >>> 3;
            if (fieldNum == 1) {
                resp.secretKey = pr.readString();
            } else if (fieldNum == 2) {
                resp.expires = pr.readInt32();
            } else {
                pr.skipField(tag & 7);
            }
        }
        return resp;
    }

    private static TranslationResponse decodeTranslationResponse(byte[] data) throws IOException {
        ProtobufReader pr = new ProtobufReader(data);
        TranslationResponse resp = new TranslationResponse();
        int tag;
        while ((tag = pr.readTag()) != 0) {
            int fieldNum = tag >>> 3;
            if (fieldNum == 1) {
                resp.url = pr.readString();
            } else if (fieldNum == 2) {
                resp.duration = pr.readDouble();
            } else if (fieldNum == 4) {
                resp.status = pr.readInt32();
            } else if (fieldNum == 5) {
                resp.remainingTime = pr.readInt32();
            } else if (fieldNum == 7) {
                resp.translationId = pr.readString();
            } else if (fieldNum == 9) {
                resp.message = pr.readString();
            } else {
                pr.skipField(tag & 7);
            }
        }
        return resp;
    }

    static class SessionResponse {
        String secretKey = "";
        int expires = 0;
    }

    static class TranslationResponse {
        String url = null;
        double duration = 0;
        int status = 0;
        int remainingTime = 0;
        String translationId = "";
        String message = "";
    }

    // --- Mini Protobuf Library ---
    static class ProtobufWriter {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public void writeTag(int fieldNumber, int wireType) throws IOException {
            writeVarint((fieldNumber << 3) | wireType);
        }

        public void writeVarint(long value) throws IOException {
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    baos.write((int) value);
                    return;
                } else {
                    baos.write((int) ((value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        }

        public void writeString(int fieldNumber, String value) throws IOException {
            if (value == null || value.isEmpty()) return;
            writeTag(fieldNumber, 2);
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarint(bytes.length);
            baos.write(bytes);
        }

        public void writeBool(int fieldNumber, boolean value) throws IOException {
            writeTag(fieldNumber, 0);
            writeVarint(value ? 1 : 0);
        }

        public void writeInt32(int fieldNumber, int value) throws IOException {
            writeTag(fieldNumber, 0);
            writeVarint(value);
        }

        public void writeDouble(int fieldNumber, double value) throws IOException {
            writeTag(fieldNumber, 1);
            long bits = Double.doubleToRawLongBits(value);
            for (int i = 0; i < 8; i++) {
                baos.write((int) (bits & 0xFF));
                bits >>>= 8;
            }
        }

        public byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    static class ProtobufReader {
        private final byte[] buf;
        private int pos = 0;

        public ProtobufReader(byte[] buf) {
            this.buf = buf;
        }

        public int readTag() throws IOException {
            if (pos >= buf.length) return 0;
            return (int) readVarint();
        }

        public long readVarint() throws IOException {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (pos >= buf.length) throw new IOException("Truncated varint");
                byte b = buf[pos++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            throw new IOException("Malformed varint");
        }

        public String readString() throws IOException {
            int len = (int) readVarint();
            if (pos + len > buf.length) throw new IOException("Truncated string");
            String s = new String(buf, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        public int readInt32() throws IOException {
            return (int) readVarint();
        }

        public double readDouble() throws IOException {
            if (pos + 8 > buf.length) throw new IOException("Truncated double");
            long bits = 0;
            for (int i = 0; i < 8; i++) {
                bits |= (long) (buf[pos++] & 0xFF) << (i * 8);
            }
            return Double.longBitsToDouble(bits);
        }

        public void skipField(int wireType) throws IOException {
            if (wireType == 0) {
                readVarint();
            } else if (wireType == 1) {
                pos += 8;
            } else if (wireType == 2) {
                int len = (int) readVarint();
                pos += len;
            } else if (wireType == 5) {
                pos += 4;
            } else {
                throw new IOException("Unsupported wire type: " + wireType);
            }
        }
    }
}
