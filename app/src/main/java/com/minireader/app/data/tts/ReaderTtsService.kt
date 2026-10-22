package com.minireader.app.data.tts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.minireader.app.MainActivity
import com.minireader.app.MiniReaderApp
import com.minireader.app.R
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.HtmlDecoder
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderTtsService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private var chunks: List<EpubTextExtractor.Chunk> = emptyList()
    private var totalChars = 0
    private var currentIndex = 0
    private var generation = 0
    private var paused = false
    private var speed = 1f
    private var session = 0

    private var bookId: String? = null
    private var chapterHref: String? = null
    private var chapterTitle: String = ""
    private var chapterIndex = 0
    private var chapterCount = 1
    private var startRatio = 0f
    private var language: String? = null

    private var preparingJob: Job? = null
    private var lastProgressSaveAt = 0L

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> startPlayback(intent)
            ACTION_TOGGLE -> togglePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_SET_SPEED -> {
                val value = intent.getFloatExtra(EXTRA_SPEED, speed)
                setSpeed(value)
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        preparingJob?.cancel()
        destroyTts()
        releaseWakeLock()
        abandonAudioFocus()
        if (ReaderTtsPlayback.state.value.isActive) {
            ReaderTtsPlayback.update { it.copy(status = TtsStatus.IDLE) }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPlayback(intent: Intent) {
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val href = intent.getStringExtra(EXTRA_CHAPTER_HREF)
        if (filePath.isNullOrBlank() || href.isNullOrBlank()) {
            fail("缺少朗讀資訊")
            return
        }

        resetPlayback()
        val newSession = session
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        chapterHref = href
        chapterTitle = intent.getStringExtra(EXTRA_CHAPTER_TITLE).orEmpty()
        chapterIndex = intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0)
        chapterCount = intent.getIntExtra(EXTRA_CHAPTER_COUNT, 1).coerceAtLeast(1)
        startRatio = intent.getFloatExtra(EXTRA_START_RATIO, 0f).coerceIn(0f, 1f)
        language = intent.getStringExtra(EXTRA_LANGUAGE)
        speed = intent.getFloatExtra(EXTRA_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)

        ReaderTtsPlayback.set(
            ReaderTtsState(
                status = TtsStatus.PREPARING,
                bookId = bookId,
                chapterHref = href,
                chapterTitle = chapterTitle,
                speed = speed,
            ),
        )

        runCatching {
            startForegroundWithNotification("準備朗讀…")
        }.onFailure {
            fail("無法啟動背景朗讀")
            return
        }

        acquireWakeLock()
        requestAudioFocus()

        preparingJob = serviceScope.launch {
            val chapterText = try {
                withContext(Dispatchers.IO) { loadChapterText(filePath, href) }
            } catch (exception: Exception) {
                if (session == newSession) {
                    fail(exception.message ?: "無法讀取章節內容")
                }
                return@launch
            }
            if (session != newSession) return@launch
            if (chapterText.chunks.isEmpty()) {
                fail("此章節沒有可朗讀的文字")
                return@launch
            }
            chunks = chapterText.chunks
            totalChars = chapterText.totalChars
            currentIndex = chapterText.chunkIndexForRatio(startRatio)
            initTts()
        }
    }

    private fun loadChapterText(filePath: String, href: String): EpubTextExtractor.ChapterText {
        val file = File(filePath)
        if (!file.exists()) throw IllegalStateException("書籍檔案不存在")
        val archive = EpubArchive(file)
        return try {
            val bytes = archive.readBytes(href) ?: throw IllegalStateException("找不到章節內容")
            EpubTextExtractor.extract(HtmlDecoder.decode(bytes))
        } finally {
            runCatching { archive.close() }
        }
    }

    private fun initTts() {
        destroyTts()
        paused = false
        var engine: TextToSpeech? = null
        engine = TextToSpeech(this) { status ->
            serviceScope.launch {
                handleTtsInit(engine, status)
            }
        }
        tts = engine
    }

    private fun handleTtsInit(engine: TextToSpeech?, status: Int) {
        if (engine == null || engine !== tts) return
        if (status != TextToSpeech.SUCCESS) {
            fail("無法初始化語音引擎")
            return
        }

        val locale = resolveLocale(language)
        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallback = engine.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                fail("裝置沒有支援的語音語言，請安裝語音資料")
                return
            }
        }

        engine.setSpeechRate(speed)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                serviceScope.launch { handleUtteranceStart(utteranceId) }
            }

            override fun onDone(utteranceId: String?) {
                serviceScope.launch { handleUtteranceDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                serviceScope.launch { handleUtteranceError(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                serviceScope.launch { handleUtteranceError(utteranceId, errorCode) }
            }
        })

        speakCurrent()
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        if (currentIndex !in chunks.indices) {
            finishChapter()
            return
        }
        paused = false
        generation += 1
        val chunk = chunks[currentIndex]
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val result = engine.speak(
            chunk.text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId(generation, currentIndex),
        )
        if (result != TextToSpeech.SUCCESS) {
            fail("無法開始朗讀")
            return
        }
        ReaderTtsPlayback.update {
            it.copy(
                status = TtsStatus.PLAYING,
                bookId = bookId,
                chapterHref = chapterHref,
                chapterTitle = chapterTitle,
                speed = speed,
                currentChunkIndex = currentIndex,
                ratio = ratioForIndex(currentIndex),
                error = null,
            )
        }
        updateNotification("朗讀中")
    }

    private fun handleUtteranceStart(utteranceId: String?) {
        val (utteranceGeneration, index) = parseUtteranceId(utteranceId) ?: return
        if (utteranceGeneration != generation || index != currentIndex || paused) return
        val ratio = ratioForIndex(index)
        ReaderTtsPlayback.update {
            it.copy(
                status = TtsStatus.PLAYING,
                bookId = bookId,
                chapterHref = chapterHref,
                chapterTitle = chapterTitle,
                speed = speed,
                currentChunkIndex = index,
                ratio = ratio,
                error = null,
            )
        }
        updateNotification("朗讀中")
        saveProgress()
    }

    private fun handleUtteranceDone(utteranceId: String?) {
        val (utteranceGeneration, index) = parseUtteranceId(utteranceId) ?: return
        if (utteranceGeneration != generation || index != currentIndex || paused) return
        currentIndex = index + 1
        if (currentIndex >= chunks.size) {
            finishChapter()
        } else {
            speakCurrent()
        }
    }

    private fun handleUtteranceError(utteranceId: String?, errorCode: Int? = null) {
        val (utteranceGeneration, index) = parseUtteranceId(utteranceId) ?: return
        if (utteranceGeneration != generation || index != currentIndex || paused) return
        fail(if (errorCode == null) "語音播放失敗" else "語音播放失敗 ($errorCode)")
    }

    private fun togglePlayback() {
        when (ReaderTtsPlayback.state.value.status) {
            TtsStatus.PLAYING -> pausePlayback()
            TtsStatus.PAUSED -> resumePlayback()
            TtsStatus.PREPARING -> Unit
            else -> Unit
        }
    }

    private fun pausePlayback() {
        if (ReaderTtsPlayback.state.value.status != TtsStatus.PLAYING) return
        paused = true
        generation += 1
        runCatching { tts?.stop() }
        ReaderTtsPlayback.update { it.copy(status = TtsStatus.PAUSED) }
        updateNotification("已暫停")
    }

    private fun resumePlayback() {
        if (chunks.isEmpty()) return
        if (currentIndex >= chunks.size) {
            finishChapter()
            return
        }
        ReaderTtsPlayback.update { it.copy(status = TtsStatus.PLAYING) }
        speakCurrent()
    }

    private fun setSpeed(value: Float) {
        speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
        tts?.setSpeechRate(speed)
        ReaderTtsPlayback.update { it.copy(speed = speed) }
        if (ReaderTtsPlayback.state.value.status == TtsStatus.PLAYING) {
            speakCurrent()
        }
        updateNotification(
            when (ReaderTtsPlayback.state.value.status) {
                TtsStatus.PAUSED -> "已暫停"
                else -> "朗讀中"
            },
        )
    }

    private fun finishChapter() {
        saveProgress(force = true, ratioOverride = 1f)
        ReaderTtsPlayback.update {
            it.copy(
                status = TtsStatus.COMPLETED,
                ratio = 1f,
                currentChunkIndex = chunks.size,
                error = null,
            )
        }
        cleanupAndStop()
    }

    private fun stopPlayback() {
        val current = ReaderTtsPlayback.state.value
        ReaderTtsPlayback.set(
            ReaderTtsState(
                status = TtsStatus.IDLE,
                speed = current.speed.coerceIn(MIN_SPEED, MAX_SPEED),
            ),
        )
        cleanupAndStop()
    }

    private fun fail(message: String) {
        ReaderTtsPlayback.update {
            it.copy(status = TtsStatus.ERROR, error = message)
        }
        updateNotification(message)
        cleanupAndStop()
    }

    private fun cleanupAndStop() {
        session += 1
        preparingJob?.cancel()
        paused = true
        generation += 1
        destroyTts()
        chunks = emptyList()
        totalChars = 0
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
        releaseWakeLock()
        abandonAudioFocus()
        stopSelf()
    }

    private fun resetPlayback() {
        session += 1
        preparingJob?.cancel()
        paused = true
        generation += 1
        destroyTts()
        chunks = emptyList()
        totalChars = 0
        currentIndex = 0
        lastProgressSaveAt = 0L
    }

    private fun destroyTts() {
        val engine = tts
        tts = null
        if (engine != null) {
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
    }

    private fun ratioForIndex(index: Int): Float {
        if (totalChars <= 0 || index !in chunks.indices) return 0f
        return (chunks[index].startChar.toFloat() / totalChars).coerceIn(0f, 1f)
    }

    private fun utteranceId(generationValue: Int, index: Int): String {
        return "$UTTERANCE_PREFIX$generationValue-$index"
    }

    private fun parseUtteranceId(utteranceId: String?): Pair<Int, Int> {
        if (utteranceId == null || !utteranceId.startsWith(UTTERANCE_PREFIX)) {
            return 0 to -1
        }
        val parts = utteranceId.removePrefix(UTTERANCE_PREFIX).split('-')
        if (parts.size != 2) return 0 to -1
        val generationValue = parts[0].toIntOrNull() ?: return 0 to -1
        val index = parts[1].toIntOrNull() ?: return 0 to -1
        return generationValue to index
    }

    private fun saveProgress(force: Boolean = false, ratioOverride: Float? = null) {
        val id = bookId ?: return
        val href = chapterHref ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastProgressSaveAt < PROGRESS_SAVE_INTERVAL_MS) return
        lastProgressSaveAt = now

        val ratio = ratioOverride ?: ratioForIndex(currentIndex)
        val fraction = ((chapterIndex + ratio) / chapterCount).coerceIn(0f, 1f)
        val app = application as? MiniReaderApp ?: return
        app.container.applicationScope.launch {
            runCatching {
                app.container.repository.saveProgress(id, href, ratio, fraction)
            }
        }
    }

    private fun resolveLocale(raw: String?): Locale {
        if (raw.isNullOrBlank()) return Locale.getDefault()
        val tag = raw.replace('_', '-')
        return when {
            tag.equals("zh", ignoreCase = true) ||
                tag.startsWith("zh-Hant", ignoreCase = true) ||
                tag.startsWith("zh-TW", ignoreCase = true) ||
                tag.startsWith("zh-HK", ignoreCase = true) -> Locale.TRADITIONAL_CHINESE
            tag.startsWith("zh-Hans", ignoreCase = true) ||
                tag.startsWith("zh-CN", ignoreCase = true) ||
                tag.startsWith("zh-SG", ignoreCase = true) -> Locale.SIMPLIFIED_CHINESE
            else -> runCatching { Locale.forLanguageTag(tag) }.getOrDefault(Locale.getDefault())
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "朗讀",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "朗讀播放控制"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(text),
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val status = ReaderTtsPlayback.state.value.status
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ReaderTtsService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ReaderTtsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pausedState = status == TtsStatus.PAUSED
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts)
            .setContentTitle(chapterTitle.ifBlank { "朗讀" })
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(status == TtsStatus.PLAYING || status == TtsStatus.PAUSED)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (pausedState) R.drawable.ic_play else R.drawable.ic_pause,
                if (pausedState) "繼續" else "暫停",
                toggleIntent,
            )
            .addAction(R.drawable.ic_stop, "停止", stopIntent)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MiniReader:Tts",
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock?.isHeld == true) {
            runCatching { lock.release() }
        }
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    pausePlayback()
                }
            }
            .build()
        audioFocusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest
        audioFocusRequest = null
        if (request != null) {
            runCatching { audioManager.abandonAudioFocusRequest(request) }
        }
    }

    companion object {
        const val ACTION_START = "com.minireader.app.tts.action.START"
        const val ACTION_TOGGLE = "com.minireader.app.tts.action.TOGGLE"
        const val ACTION_PAUSE = "com.minireader.app.tts.action.PAUSE"
        const val ACTION_RESUME = "com.minireader.app.tts.action.RESUME"
        const val ACTION_STOP = "com.minireader.app.tts.action.STOP"
        const val ACTION_SET_SPEED = "com.minireader.app.tts.action.SET_SPEED"

        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_CHAPTER_HREF = "chapter_href"
        private const val EXTRA_CHAPTER_TITLE = "chapter_title"
        private const val EXTRA_CHAPTER_INDEX = "chapter_index"
        private const val EXTRA_CHAPTER_COUNT = "chapter_count"
        private const val EXTRA_START_RATIO = "start_ratio"
        private const val EXTRA_LANGUAGE = "language"
        private const val EXTRA_SPEED = "speed"

        private const val CHANNEL_ID = "reader_tts"
        private const val NOTIFICATION_ID = 4101
        private const val UTTERANCE_PREFIX = "mr-tts-"
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 2f
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        fun start(
            context: Context,
            bookId: String,
            filePath: String,
            chapterHref: String,
            chapterTitle: String,
            chapterIndex: Int,
            chapterCount: Int,
            startRatio: Float,
            language: String?,
            speed: Float,
        ) {
            val intent = Intent(context, ReaderTtsService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_CHAPTER_HREF, chapterHref)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                putExtra(EXTRA_CHAPTER_INDEX, chapterIndex)
                putExtra(EXTRA_CHAPTER_COUNT, chapterCount)
                putExtra(EXTRA_START_RATIO, startRatio)
                putExtra(EXTRA_LANGUAGE, language)
                putExtra(EXTRA_SPEED, speed)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, ReaderTtsService::class.java).setAction(action),
                )
            }
        }

        fun setSpeed(context: Context, speed: Float) {
            runCatching {
                context.startService(
                    Intent(context, ReaderTtsService::class.java)
                        .setAction(ACTION_SET_SPEED)
                        .putExtra(EXTRA_SPEED, speed),
                )
            }
        }
    }
}
