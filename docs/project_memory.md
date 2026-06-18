# Техническая документация: Интеграция плагина голосового перевода (VOT) Яндекса в SmartTube

Данный документ содержит подробную техническую информацию об архитектуре плагина, списке измененных файлов, логике работы с API Яндекса, а также пошаговое руководство по обновлению SmartTube или скрипта перевода в будущем.

---

## 📂 1. Карта затронутых файлов (File Map)

При интеграции плагина были изменены или созданы следующие файлы:

### Новые файлы плагина (изолированная логика):
*   [`VoiceOverTranslationController.java`](file:///c:/Antigravity%20projects/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VoiceOverTranslationController.java) — ядро плагина. Управляет запросами к API, ExoPlayer-ом озвучки, синхронизацией дорожек и громкостью.
*   [`VoiceOverTranslationData.java`](file:///c:/Antigravity%20projects/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/prefs/VoiceOverTranslationData.java) — хранение настроек плагина в SharedPreferences.
*   [`VoiceOverTranslationSettingsPresenter.java`](file:///c:/Antigravity%20projects/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/VoiceOverTranslationSettingsPresenter.java) — меню настроек плагина (открывается по долгому нажатию).
*   [`TranslateAction.java`](file:///c:/Antigravity%20projects/SmartTube/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/actions/TranslateAction.java) — кнопка на панели плеера (3 состояния: серый, желтый, зеленый).
*   [`ic_translate.xml`](file:///c:/Antigravity%20projects/SmartTube/smarttubetv/src/main/res/drawable/ic_translate.xml) — векторная иконка кнопки перевода (буква "A" + иероглиф "文").

### Изменения в коде SmartTube (точки интеграции):
*   [`PlaybackPresenter.java`](file:///c:/Antigravity%20projects/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/PlaybackPresenter.java) — инициализация и регистрация контроллера в общем цикле плеера:
    ```java
    // В методе initControllers()
    addController(new VoiceOverTranslationController());
    ```
*   [`VideoPlayerGlue.java`](file:///c:/Antigravity%20projects/SmartTube/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/other/VideoPlayerGlue.java) — добавление кнопки во вторичные действия плеера (метод `onCreateSecondaryActions`).
*   [`SplashPresenter.java`](file:///c:/Antigravity%20projects/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/SplashPresenter.java) — показ приветственного нативного сообщения (Toast) при старте (метод `showSplashToast`).
*   [`strings.xml` (ru)](file:///c:/Antigravity%20projects/SmartTube/common/src/main/res/values-ru/strings.xml) и [`strings.xml` (en)](file:///c:/Antigravity%20projects/SmartTube/common/src/main/res/values/strings.xml) — добавлены локализованные строки меню настроек с префиксом `vot_`.
*   [`ids.xml`](file:///c:/Antigravity%20projects/SmartTube/common/src/main/res/values/ids.xml) — объявлен ID для кнопки: `<item type="id" name="action_voice_over_translation" />`.

---

## 🛠️ 2. Архитектура и Синхронизация

Интеграция построена на базе **двух параллельных плееров**:
1.  **Основной ExoPlayer приложения:** воспроизводит видео и оригинальный звук.
2.  **ExoPlayer плагина (`mAudioPlayer`):** воспроизводит полученную от Яндекса аудиодорожку перевода.

### Логика синхронизации:
*   При старте перевода видео ставится на паузу (если включена опция «Ставить на паузу при загрузке»).
*   Когда первый аудио-сегмент загрузился (`playbackState == STATE_READY`), оригинальное видео запускается, а его громкость приглушается до заданного уровня (например, 20%).
*   В цикле `syncPlayback()` раз в секунду сравниваются позиции видео и аудио. Если разница превышает **500 мс**, аудио-плеер перевода выполняет `seekTo(videoPosition)`.
*   При перемотке (`onSeekEnd`, `onSeekPositionChanged`) аудио-плеер мгновенно перемещается на ту же позицию.
*   При показе панели управления плеера (`onControlsShown`) состояние и цвет кнопки перевода восстанавливаются принудительно, предотвращая сброс индикации в серый цвет.

---

## 🔄 3. Инструкция по обновлению SmartTube

Когда автор SmartTube (`yuliskov`) выпускает новую версию, вы можете легко обновить ваш форк, сохранив все наработки перевода:

1.  **Добавьте оригинальный репозиторий как upstream:**
    ```bash
    git remote add upstream https://github.com/yuliskov/SmartTube.git
    ```
2.  **Получите последние изменения:**
    ```bash
    git fetch upstream
    ```
3.  **Перенесите ваши изменения поверх новой версии (рекомендуется Rebase):**
    ```bash
    git checkout master
    * По желанию сделайте резервную ветку: git branch backup-vot
    git rebase upstream/master
    ```
    *Поскольку вся логика плагина изолирована в отдельных новых файлах, конфликты при rebase могут возникнуть только в трех файлах:*
    *   `PlaybackPresenter.java` (строка подключения контроллера)
    *   `VideoPlayerGlue.java` (строка добавления кнопки в плеер)
    *   `SplashPresenter.java` (функция показа Toast)
    
    *Эти конфликты легко разрешаются вручную в IDE.*
4.  **Соберите проект и проверьте работоспособность:**
    ```bash
    ./gradlew assembleStstableDebug
    ```

---

## 🌐 4. Инструкция по обновлению API Яндекса (VOT)

Если Яндекс обновит свои алгоритмы, сигнатуры или адреса (что приведет к ошибкам загрузки перевода), внесите изменения в следующие части [**VoiceOverTranslationController.java**](file:///c:/Antigravity%20projects/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VoiceOverTranslationController.java):

### А. Изменение прокси-сервера:
Если общественный прокси `vot-worker.toil.cc` изменит адрес или перестанет работать, обновите метод `getBackendHost()`:
```java
private String getBackendHost() {
    return mTranslationData.isProxyEnabled() ? "https://НОВЫЙ_АДРЕС_ПРОКСИ" : "https://api.browser.yandex.ru";
}
```

### Б. Обновление HMAC ключа шифрования:
Яндекс использует HMAC-SHA256 для подписи запросов. Ключ задан в константе:
```java
private static final String HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf";
```
Если подписи перестанут приниматься, найдите актуальный ключ в исходниках расширения [VOT на GitHub](https://github.com/ilyhalight/voice-over-translation) (обычно в файле `yandexproto.js` or `yandex.js`) и обновите эту константу.

### В. Обновление Protobuf-схемы:
Если Яндекс добавит или изменит тэги в Protobuf-запросах, обновите методы кодирования:
*   `encodeSessionRequest` (Тэг 1 - UUID, Тэг 2 - модуль).
*   `encodeTranslationRequest` (Тэг 3 - URL видео, Тэг 4 - UUID, Тэг 6 - длительность видео, Тэг 8 - язык источника, Тэг 14 - язык перевода, Тэг 18 - живые голоса).
*   Для записи данных используется легковесный класс `ProtobufWriter`, который пишет теги напрямую. Например, запись флага `useLivelyVoice` (тэг 18, тип Varint/Bool) выполняется так:
    ```java
    pw.writeBool(18, true);
    ```

---

## 🔊 5. Логика VolumeBooster (Усиление звука перевода)

Для программного усиления звука перевода (от 110% до 200%) используется Android-класс `LoudnessEnhancer`, обернутый в `VolumeBooster`:

*   При громкости $\le 100\%$ громкость задается напрямую через ExoPlayer: `mAudioPlayer.setVolume(volume / 100f)`.
*   При громкости $> 100\%$ ExoPlayer выставляется на максимум (`1.0f`), инициализируется `VolumeBooster(true, boostFactor, mAudioPlayer)` и подключается к аудио-сессии плеера в качестве `AudioListener`.
*   Максимальный уровень усиления (200%) соответствует `2.0f` коэффициента усиления в `LoudnessEnhancer`.
*   *Примечание:* Избегайте использования громкости 200% на устройствах со слабыми динамиками во избежание хрипов (клиппинга). Рекомендуется выставлять 120-130% и делать тише оригинальный звук.
