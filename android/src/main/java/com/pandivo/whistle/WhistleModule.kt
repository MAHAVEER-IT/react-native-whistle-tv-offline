package com.pandivo.whistle

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.Executors

/**
 * WhistleModule: React Native Bridge for 100% On-Device Offline Speech-to-Text
 * on Fire TV, Android TV, and living room devices.
 *
 * PRIVACY GUARANTEE:
 * - Completely free of Google SpeechRecognizer, Google Play Services, or cloud STT.
 * - Zero remote telemetry of voice data.
 * - Streams raw PCM audio strictly into transient heap memory via AudioRecordEngine.
 * - Transcribes on-device using the embedded C++ Whistle inference engine (libwhistle_native.so).
 * - Zero disk writes; zero hardcoded / mock fallbacks.
 */
class WhistleModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "WhistleModule"
        private const val TAG = "WhistleModule"
        private const val ACTION_VOICE_INPUT = "com.pandivo.whistle.VOICE_INPUT"
        private const val ACTION_VOICE_INPUT_ALT = "com.firetv.musicpoc.VOICE_INPUT"

        // Error code constants
        const val ERROR_AUDIO = 3
        const val ERROR_CLIENT = 5
        const val ERROR_SPEECH_TIMEOUT = 6
        const val ERROR_NO_MATCH = 7
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
        const val ERROR_WHISTLE_UNSUPPORTED = 10

        // Known Whisper neural network silence hallucinations
        private val KNOWN_SILENCE_HALLUCINATIONS = setOf(
            "thank you", "thank you.", "thank you!", "thank you very much", "thank you very much.",
            "happy birthday", "happy birthday!", "happy birthday.",
            "thanks for watching", "thanks for watching.", "thanks for watching!",
            "please subscribe", "subscribe", "subtitles by", "you", ".", "..."
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val audioRecordEngine = AudioRecordEngine()
    private var isListening = false
    private var isReceiverRegistered = false

    private val voiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_VOICE_INPUT || intent?.action == ACTION_VOICE_INPUT_ALT) {
                val query = intent.getStringExtra("query")
                if (!query.isNullOrBlank()) {
                    Log.d(TAG, "Simulated voice input broadcast received: $query")
                    val map = Arguments.createMap().apply {
                        putString("text", query.trim())
                        putString("language", "en")
                        putBoolean("isFinal", true)
                        putString("engine", "adb_voice_command")
                    }
                    sendEvent("onSpeechResults", map)
                } else {
                    Log.w(TAG, "Voice broadcast received without query extra")
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_VOICE_INPUT)
                addAction(ACTION_VOICE_INPUT_ALT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(voiceBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                reactContext.registerReceiver(voiceBroadcastReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Registered broadcast receiver for $ACTION_VOICE_INPUT and $ACTION_VOICE_INPUT_ALT")
        } catch (e: Exception) {
            Log.w(TAG, "Notice registering broadcast receiver: ${e.message}")
        }

        // Initialize Whistle C++ JNI inference engine with bundled 16.9 MB model
        WhistleEngine.initialize(reactContext) { success ->
            Log.i(TAG, "Whistle Engine initialized: success=$success, supported=${WhistleEngine.isEngineSupported()}")
        }
    }

    override fun getName(): String = NAME

    override fun invalidate() {
        super.invalidate()
        if (isReceiverRegistered) {
            try {
                reactContext.unregisterReceiver(voiceBroadcastReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
        mainHandler.post {
            audioRecordEngine.stopRecording()
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        if (reactContext.hasActiveReactInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        }
    }

    @ReactMethod
    fun isRecognitionAvailable(promise: Promise) {
        // 100% On-Device Whistle Engine availability (Zero Google Speech or remote fallback)
        val whistleSupported = WhistleEngine.isEngineSupported()
        promise.resolve(whistleSupported)
    }

    @ReactMethod
    fun isAvailable(promise: Promise) {
        isRecognitionAvailable(promise)
    }

    @ReactMethod
    fun startListening(language: String?, promise: Promise) {
        startListeningInternal(
            language = language,
            keywords = null,
            maxDurationMs = AudioRecordEngine.DEFAULT_MAX_DURATION_MS,
            postSpeechSilenceMs = AudioRecordEngine.DEFAULT_POST_SPEECH_SILENCE_MS,
            cleanPunctuation = false,
            promise = promise
        )
    }

    @ReactMethod
    fun startListeningWithConfig(config: ReadableMap, promise: Promise) {
        val language = if (config.hasKey("language")) config.getString("language") else "en"
        val keywords = if (config.hasKey("keywords")) {
            val kwArray = config.getArray("keywords")
            val list = mutableListOf<String>()
            if (kwArray != null) {
                for (i in 0 until kwArray.size()) {
                    kwArray.getString(i)?.let { list.add(it) }
                }
            }
            list.joinToString("\n")
        } else {
            null
        }
        val maxDurationMs = if (config.hasKey("maxDurationMs")) {
            config.getDouble("maxDurationMs").toLong()
        } else {
            AudioRecordEngine.DEFAULT_MAX_DURATION_MS
        }
        val postSpeechSilenceMs = if (config.hasKey("postSpeechSilenceMs")) {
            config.getDouble("postSpeechSilenceMs").toLong()
        } else {
            AudioRecordEngine.DEFAULT_POST_SPEECH_SILENCE_MS
        }
        val cleanPunctuation = if (config.hasKey("cleanPunctuation")) {
            config.getBoolean("cleanPunctuation")
        } else {
            false
        }

        startListeningInternal(
            language = language,
            keywords = keywords,
            maxDurationMs = maxDurationMs,
            postSpeechSilenceMs = postSpeechSilenceMs,
            cleanPunctuation = cleanPunctuation,
            promise = promise
        )
    }

    private fun startListeningInternal(
        language: String?,
        keywords: String?,
        maxDurationMs: Long,
        postSpeechSilenceMs: Long,
        cleanPunctuation: Boolean,
        promise: Promise
    ) {
        val targetLanguage = if (language.isNullOrBlank()) "en" else language

        mainHandler.post {
            try {
                val hasPermission = ContextCompat.checkSelfPermission(
                    reactContext,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    val map = Arguments.createMap().apply {
                        putString("error", "Microphone permission (RECORD_AUDIO) not granted.")
                        putInt("errorCode", ERROR_INSUFFICIENT_PERMISSIONS)
                    }
                    sendEvent("onSpeechError", map)
                    promise.reject("PERMISSION_DENIED", "RECORD_AUDIO permission not granted.")
                    return@post
                }

                if (isListening) {
                    promise.resolve(true)
                    return@post
                }

                if (!WhistleEngine.isEngineSupported()) {
                    Log.e(TAG, "Whistle engine not supported on this device architecture")
                    val errMap = Arguments.createMap().apply {
                        putString("error", "Whistle on-device voice engine is not supported on this architecture")
                        putInt("errorCode", ERROR_WHISTLE_UNSUPPORTED)
                    }
                    sendEvent("onSpeechError", errMap)
                    promise.reject("UNAVAILABLE", "Whistle on-device speech engine is not supported on this architecture")
                    return@post
                }

                isListening = true

                Log.d(TAG, "Starting on-device native Whistle voice recognition ($targetLanguage, maxDurationMs=${maxDurationMs}ms, cleanPunctuation=$cleanPunctuation)...")

                val startMap = Arguments.createMap().apply {
                    putString("status", "listening")
                    putString("engine", "whistle_native")
                }
                sendEvent("onSpeechStart", startMap)

                audioRecordEngine.startRecording(
                    maxDurationMs = maxDurationMs,
                    postSpeechSilenceMs = postSpeechSilenceMs,
                    onAudioReady = { pcmFloatSamples ->
                        handleWhistleAudioRecorded(pcmFloatSamples, targetLanguage, keywords, cleanPunctuation)
                    },
                    onError = { errorMsg ->
                        mainHandler.post {
                            isListening = false
                            val errMap = Arguments.createMap().apply {
                                putString("error", errorMsg)
                                putInt("errorCode", ERROR_AUDIO)
                            }
                            sendEvent("onSpeechError", errMap)
                        }
                    }
                )
                promise.resolve(true)
            } catch (e: Exception) {
                isListening = false
                Log.e(TAG, "Error starting voice recognition: ${e.message}", e)
                promise.reject("START_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun stopListening(promise: Promise) {
        mainHandler.post {
            try {
                if (audioRecordEngine.isCurrentlyRecording()) {
                    val endMap = Arguments.createMap().apply { putString("status", "processing") }
                    sendEvent("onSpeechEnd", endMap)
                    audioRecordEngine.stopRecording()
                }

                isListening = false
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("STOP_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun cancelListening(promise: Promise) {
        mainHandler.post {
            try {
                if (audioRecordEngine.isCurrentlyRecording()) {
                    audioRecordEngine.stopRecording()
                }
                isListening = false
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("CANCEL_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun simulateVoiceInput(text: String, promise: Promise) {
        mainHandler.post {
            Log.d(TAG, "Simulating speech recognition input: $text")
            val map = Arguments.createMap().apply {
                putString("text", text)
                putString("language", "en")
                putBoolean("isFinal", true)
                putString("engine", "simulated")
            }
            sendEvent("onSpeechResults", map)
            promise.resolve(text)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for RN NativeEventEmitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for RN NativeEventEmitter
    }

    private fun handleWhistleAudioRecorded(
        rawSamples: FloatArray,
        language: String,
        keywords: String?,
        cleanPunctuation: Boolean
    ) {
        if (rawSamples.isEmpty()) {
            mainHandler.post {
                isListening = false
                val map = Arguments.createMap().apply {
                    putString("error", "No speech recorded (empty buffer)")
                    putInt("errorCode", ERROR_NO_MATCH)
                }
                sendEvent("onSpeechError", map)
            }
            return
        }

        // Step 1: Preprocess audio (High-pass ~80Hz, VAD silence trimming, AGC 0.85 peak normalization)
        val samples = AudioPreprocessor.process(rawSamples)

        // Step 2: Calculate audio RMS and peak to verify speech presence
        var sumSquares = 0.0
        var peak = 0.0f
        for (sample in samples) {
            sumSquares += (sample * sample).toDouble()
            val absVal = kotlin.math.abs(sample)
            if (absVal > peak) peak = absVal
        }
        val rms = kotlin.math.sqrt(sumSquares / samples.size).toFloat()
        val hasSpeechEnergy = rms > 0.010f || peak > 0.05f
        Log.d(TAG, "Audio captured & preprocessed: ${samples.size} samples, RMS: $rms, Peak: $peak, speechEnergy=$hasSpeechEnergy")

        // Step 3: PRE-INFERENCE SILENCE GATING
        // Guard against Whisper neural network silence hallucinations on quiet mic recordings
        if (!hasSpeechEnergy) {
            Log.w(TAG, "Audio energy below speech threshold (RMS: $rms, Peak: $peak). Emitting no-speech timeout error.")
            mainHandler.post {
                isListening = false
                val map = Arguments.createMap().apply {
                    putString("error", "No speech detected")
                    putInt("errorCode", ERROR_SPEECH_TIMEOUT)
                }
                sendEvent("onSpeechError", map)
            }
            return
        }

        backgroundExecutor.execute {
            Log.d(TAG, "Transcribing ${samples.size} PCM float samples with Whistle C++ engine...")
            val result = WhistleEngine.transcribe(
                pcmSamples = samples,
                language = language,
                keywords = keywords
            )

            mainHandler.post {
                isListening = false
                var recognizedText = ""
                var isHallucination = false

                if (result.isSuccess && result.text.isNotBlank()) {
                    val rawText = result.text.trim()
                    val normalized = rawText.lowercase().trim()

                    // Step 4: POST-INFERENCE HALLUCINATION BLACKLIST
                    if (KNOWN_SILENCE_HALLUCINATIONS.contains(normalized) && rms < 0.020f) {
                        Log.w(TAG, "Discarded Whisper silence hallucination: \"$rawText\" (RMS: $rms, Peak: $peak)")
                        isHallucination = true
                        recognizedText = ""
                    } else {
                        recognizedText = if (cleanPunctuation) {
                            rawText.replace(Regex("[.,/#!$%^&*;:{}=\\-_`~()?]+$"), "").trim()
                        } else {
                            rawText
                        }
                    }
                }

                if (recognizedText.isNotBlank()) {
                    Log.i(TAG, "Whistle transcription result: \"$recognizedText\" (TTFT: ${result.ttftMs}ms)")
                    val map = Arguments.createMap().apply {
                        putString("text", recognizedText)
                        putString("language", result.language)
                        putBoolean("isFinal", true)
                        putDouble("ttftMs", result.ttftMs.toDouble())
                        putDouble("decodeTps", result.decodeTps.toDouble())
                        putString("engine", "whistle_native")
                    }
                    sendEvent("onSpeechResults", map)
                } else {
                    Log.w(TAG, "No speech recognized or speech was unintelligible (hasEnergy=$hasSpeechEnergy, isHallucination=$isHallucination, RMS=$rms, Peak=$peak, err=${result.error})")
                    val map = Arguments.createMap().apply {
                        putString(
                            "error",
                            if (isHallucination || !hasSpeechEnergy) "No speech detected"
                            else (result.error ?: "Could not recognize speech, please try speaking again")
                        )
                        putInt("errorCode", if (hasSpeechEnergy && !isHallucination) ERROR_NO_MATCH else ERROR_SPEECH_TIMEOUT)
                    }
                    sendEvent("onSpeechError", map)
                }
            }
        }
    }
}
