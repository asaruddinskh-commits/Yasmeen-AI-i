package com.example

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
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.live.LiveSessionManager
import com.example.live.ZoyaState
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ZoyaForegroundService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    lateinit var liveSessionManager: LiveSessionManager
    private lateinit var toolEngine: ToolExecutionEngine

    private var isRecording = false
    private val audioOutputQueue = LinkedBlockingQueue<ByteArray>()
    private var isAudioPlaybackActive = false
    private var isServerTurnComplete = true

    // Audio configuration for Gemini Live API (16kHz, Mono, PCM 16-bit)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Audio configuration for output (24kHz, Mono, PCM 16-bit)
    private val outputSampleRate = 24000
    private val outChannelConfig = AudioFormat.CHANNEL_OUT_MONO

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ZOYA_CHANNEL"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentState = MutableStateFlow(ZoyaState.IDLE)
        val currentState: StateFlow<ZoyaState> = _currentState.asStateFlow()

        private val _statusText = MutableStateFlow("Offline")
        val statusText: StateFlow<String> = _statusText.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _messages = MutableStateFlow<List<String>>(emptyList())
        val messages: StateFlow<List<String>> = _messages.asStateFlow()

        var onStateChange: ((ZoyaState) -> Unit)? = null
        var activeService: ZoyaForegroundService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("ZoyaService", "ZoyaForegroundService onCreate started")
        activeService = this
        _isRunning.value = true
        _errorMessage.value = null
        _statusText.value = "Initializing..."

        // 1. Validate Microphone Permission before foreground service setup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ZoyaService", "Missing RECORD_AUDIO permission on start")
            _errorMessage.value = "Microphone permission is required."
            _statusText.value = "Permission Denied"
            stopSelf()
            return
        }

        // 2. Setup Notification Channel & start foreground
        createNotificationChannel()
        try {
            val initialNotification = buildNotification(ZoyaState.IDLE, "Initializing voice uplink...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Throwable) {
            Log.e("ZoyaService", "Failed to startForeground", e)
            _errorMessage.value = "Foreground service error: ${e.message}"
            stopSelf()
            return
        }

        // 3. Initialize components
        try {
            toolEngine = ToolExecutionEngine(this)

            val onAudioOut: (ByteArray) -> Unit = { audioData ->
                playAudio(audioData)
            }

            val onInterruptOut: () -> Unit = {
                try {
                    audioOutputQueue.clear()
                    if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.pause()
                        audioTrack?.flush()
                        audioTrack?.play()
                    }
                    updateServiceState(ZoyaState.LISTENING, "Listening to your voice...")
                } catch (e: Throwable) {
                    Log.e("ZoyaService", "Error flushing track on interrupt", e)
                }
            }

            val onTurnCompleteOut: () -> Unit = {
                isServerTurnComplete = true
                if (audioOutputQueue.isEmpty()) {
                    updateServiceState(ZoyaState.LISTENING, "Listening to your voice...")
                }
            }

            val onErrorOut: (String) -> Unit = { err ->
                _errorMessage.value = err
                updateNotification(_currentState.value, err)
            }

            liveSessionManager = LiveSessionManager(
                context = this,
                toolEngine = toolEngine,
                onAudioOut = onAudioOut,
                onInterrupt = onInterruptOut,
                onTurnComplete = onTurnCompleteOut,
                onError = onErrorOut
            )

            scope.launch {
                liveSessionManager.zoyaState.collect { state ->
                    if (state == ZoyaState.SPEAKING || (state != ZoyaState.SPEAKING && audioOutputQueue.isEmpty())) {
                        val detail = when (state) {
                            ZoyaState.LISTENING -> "Listening to your voice..."
                            ZoyaState.THINKING -> "Processing neural directives..."
                            ZoyaState.SPEAKING -> "Transmitting response..."
                            ZoyaState.IDLE -> "Standby"
                        }
                        updateServiceState(state, detail)
                    }
                }
            }

            scope.launch {
                liveSessionManager.messages.collect { msgList ->
                    _messages.value = msgList
                }
            }

            initAudioTrack()
            startMicrophoneLoop()
            liveSessionManager.startSession()

        } catch (e: Throwable) {
            Log.e("ZoyaService", "Fatal error during service setup", e)
            _errorMessage.value = "Initialization error: ${e.message}"
            stopSelf()
        }
    }

    private fun updateServiceState(state: ZoyaState, detail: String) {
        _currentState.value = state
        _statusText.value = detail
        onStateChange?.invoke(state)
        updateNotification(state, detail)
    }

    private fun initAudioTrack() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(outputSampleRate, outChannelConfig, audioFormat)
            val finalBuf = if (minBuf > 0) minBuf * 4 else 8192

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(outputSampleRate)
                        .setChannelMask(outChannelConfig)
                        .build()
                )
                .setBufferSizeInBytes(finalBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
                startAudioPlaybackLoop()
            } else {
                Log.w("ZoyaService", "AudioTrack was not initialized properly")
            }
        } catch (e: Throwable) {
            Log.e("ZoyaService", "Error initializing AudioTrack", e)
        }
    }

    private fun startAudioPlaybackLoop() {
        isAudioPlaybackActive = true
        scope.launch(Dispatchers.IO) {
            while (isActive && isAudioPlaybackActive) {
                try {
                    val data = audioOutputQueue.poll(50, TimeUnit.MILLISECONDS)
                    if (data != null) {
                        try {
                            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                audioTrack?.play()
                            }
                            audioTrack?.write(data, 0, data.size)
                        } catch (e: Throwable) {
                            Log.e("ZoyaService", "Error writing audio to track", e)
                        }
                    } else {
                        if (isServerTurnComplete && _currentState.value == ZoyaState.SPEAKING) {
                            delay(120)
                            if (audioOutputQueue.isEmpty()) {
                                updateServiceState(ZoyaState.LISTENING, "Listening to your voice...")
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("ZoyaService", "Playback loop error", e)
                }
            }
        }
    }

    private fun playAudio(data: ByteArray) {
        try {
            isServerTurnComplete = false
            updateServiceState(ZoyaState.SPEAKING, "Transmitting response...")
            audioOutputQueue.offer(data)
        } catch (e: Throwable) {
            Log.e("ZoyaService", "Error queuing audio", e)
        }
    }

    private fun startMicrophoneLoop() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ZoyaService", "Missing RECORD_AUDIO permission")
            return
        }

        try {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val finalBuf = if (minBuf > 0) minBuf * 4 else 8192

            audioRecord = createAudioRecord(finalBuf)

            if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("ZoyaService", "AudioRecord initialization failed!")
                _errorMessage.value = "Microphone could not be initialized."
                return
            }

            try {
                audioRecord?.startRecording()
                isRecording = true
            } catch (e: Throwable) {
                Log.e("ZoyaService", "Failed to start recording", e)
                _errorMessage.value = "Failed to start microphone: ${e.message}"
                return
            }

            scope.launch(Dispatchers.IO) {
                val chunkSize = 1600 // 100ms of audio at 16kHz
                val audioBuffer = ShortArray(chunkSize)
                while (isActive && isRecording) {
                    try {
                        val readResult = audioRecord?.read(audioBuffer, 0, chunkSize) ?: 0
                        if (readResult > 0) {
                            processAudio(audioBuffer, readResult)
                        }
                    } catch (e: Throwable) {
                        Log.e("ZoyaService", "Error reading microphone data", e)
                    }
                }
                Log.i("ZoyaService", "Microphone loop finished")
            }
        } catch (e: Throwable) {
            Log.e("ZoyaService", "Error in startMicrophoneLoop", e)
        }
    }

    /**
     * Resilient AudioRecord factory with fallback mechanism.
     * Avoids using hidden or device-incompatible APIs (e.g. setContext).
     */
    private fun createAudioRecord(bufSize: Int): AudioRecord? {
        // Attempt 1: VOICE_COMMUNICATION for noise/echo cancellation
        try {
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .build()

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record.release()
        } catch (t: Throwable) {
            Log.w("ZoyaService", "VOICE_COMMUNICATION AudioRecord failed, trying standard MIC", t)
        }

        // Attempt 2: Standard MIC
        try {
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .build()

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record.release()
        } catch (t: Throwable) {
            Log.w("ZoyaService", "Standard MIC AudioRecord.Builder failed, trying direct constructor", t)
        }

        // Attempt 3: Direct constructor fallback (works across all Android versions)
        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufSize
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record.release()
        } catch (t: Throwable) {
            Log.e("ZoyaService", "Direct AudioRecord constructor failed", t)
        }

        return null
    }

    private fun processAudio(buffer: ShortArray, length: Int) {
        val state = _currentState.value

        // Only stream audio if session is active
        if (state != ZoyaState.IDLE) {
            val isPlayingSpeaker = (state == ZoyaState.SPEAKING) || audioOutputQueue.isNotEmpty()
            if (isPlayingSpeaker) {
                // When speaker is playing, suppress echo and only pass high energy user barge-in
                var sum = 0L
                for (i in 0 until length) {
                    sum += abs(buffer[i].toLong())
                }
                val avg = if (length > 0) sum / length else 0
                if (avg > 2800) {
                    liveSessionManager.sendAudioData(buffer, length)
                }
            } else {
                liveSessionManager.sendAudioData(buffer, length)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            Log.i("ZoyaService", "Received STOP action intent")
            stopSelf()
            return START_NOT_STICKY
        }
        // Always return START_NOT_STICKY to prevent Android OS from auto-respawning the service after crash/kill
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun sendTextMessage(text: String) {
        liveSessionManager.sendTextMessage(text)
    }

    fun reconnectSession() {
        _errorMessage.value = null
        updateServiceState(ZoyaState.LISTENING, "Reconnecting uplink...")
        liveSessionManager.startSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("ZoyaService", "ZoyaForegroundService onDestroy started")
        activeService = null
        _isRunning.value = false
        _currentState.value = ZoyaState.IDLE
        _statusText.value = "Offline"
        onStateChange?.invoke(ZoyaState.IDLE)

        isRecording = false
        isAudioPlaybackActive = false
        audioOutputQueue.clear()

        try { audioRecord?.stop() } catch (e: Throwable) {}
        try { audioRecord?.release() } catch (e: Throwable) {}
        audioRecord = null

        try { audioTrack?.stop() } catch (e: Throwable) {}
        try { audioTrack?.release() } catch (e: Throwable) {}
        audioTrack = null

        try { liveSessionManager.stopSession() } catch (e: Throwable) {}

        job.cancel()

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(NOTIFICATION_ID)
        } catch (e: Throwable) {
            Log.e("ZoyaService", "Error clearing notification on destroy", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Zoya Assistant Uplink",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground status of Zoya AI Assistant"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateNotification(state: ZoyaState, detailText: String) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(state, detailText))
        } catch (e: Throwable) {
            Log.e("ZoyaService", "Error updating notification", e)
        }
    }

    private fun buildNotification(state: ZoyaState, detailText: String): Notification {
        val stopIntent = Intent(this, ZoyaForegroundService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (state) {
            ZoyaState.LISTENING -> "Zoya • Listening"
            ZoyaState.THINKING -> "Zoya • Processing"
            ZoyaState.SPEAKING -> "Zoya • Speaking"
            ZoyaState.IDLE -> "Zoya • Standby"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(detailText)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
