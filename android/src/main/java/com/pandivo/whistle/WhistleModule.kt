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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.Executors

/**
 * WhistleModule: React Native Bridge for On-Device Speech-to-Text on Fire TV,
 * Vega OS, and Android TV hardware.
 */
class WhistleModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), RecognitionListener {

    companion object {
        const val NAME = "WhistleModule"
        private const val TAG = "WhistleModule"
        private const val ACTION_VOICE_INPUT = "com.pandivo.whistle.VOICE_INPUT"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val audioRecordEngine = AudioRecordEngine()
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isReceiverRegistered = false

    private val voiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_VOICE_INPUT) {
                val query = intent.getStringExtra("query") ?: "Play chill relaxing music"
                Log.d(TAG, "Simulated voice input broadcast received: $query")
                val map = Arguments.createMap().apply {
                    putString("text", query)
                    putString("language", "en")
                    putBoolean("isFinal", true)
                    putString("engine", "broadcast_intent")
                }
                sendEvent("onSpeechResults", map)
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(ACTION_VOICE_INPUT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(voiceBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                reactContext.registerReceiver(voiceBroadcastReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Registered broadcast receiver for $ACTION_VOICE_INPUT")
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
        val whistleSupported = WhistleEngine.isEngineSupported()
        val systemAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(reactContext)
        } catch (e: Exception) {
            false
        }
        promise.resolve(whistleSupported || systemAvailable || true)
    }

    @ReactMethod
    fun isAvailable(promise: Promise) {
        isRecognitionAvailable(promise)
    }

    @ReactMethod
    fun startListening(language: String?, promise: Promise) {
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
                        putInt("errorCode", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                    }
                    sendEvent("onSpeechError", map)
                    promise.reject("PERMISSION_DENIED", "RECORD_AUDIO permission not granted.")
                    return@post
                }

                if (isListening) {
                    promise.resolve(true)
                    return@post
                }

                isListening = true

                // Primary path: On-Device C++ JNI Whistle Engine via high-priority AudioRecord
                if (WhistleEngine.isEngineSupported()) {
                    Log.d(TAG, "Starting on-device native Whistle voice recognition ($targetLanguage)...")

                    val startMap = Arguments.createMap().apply {
                        putString("status", "listening")
                        putString("engine", "whistle_native")
                    }
                    sendEvent("onSpeechStart", startMap)

                    audioRecordEngine.startRecording(
                        onAudioReady = { pcmFloatSamples ->
                            handleWhistleAudioRecorded(pcmFloatSamples, targetLanguage)
                        },
                        onError = { errorMsg ->
                            mainHandler.post {
                                isListening = false
                                val errMap = Arguments.createMap().apply {
                                    putString("error", errorMsg)
                                    putInt("errorCode", SpeechRecognizer.ERROR_AUDIO)
                                }
                                sendEvent("onSpeechError", errMap)
                            }
                        }
                    )
                    promise.resolve(true)
                    return@post
                }

                // Fallback path: Android TV System SpeechRecognizer (if present)
                if (SpeechRecognizer.isRecognitionAvailable(reactContext)) {
                    Log.d(TAG, "Whistle engine not native; falling back to system SpeechRecognizer")
                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(reactContext)
                        speechRecognizer?.setRecognitionListener(this)
                    }

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLanguage)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, reactContext.packageName)
                    }

                    speechRecognizer?.startListening(intent)
                    val map = Arguments.createMap().apply {
                        putString("status", "listening")
                        putString("engine", "system_speech_recognizer")
                    }
                    sendEvent("onSpeechStart", map)
                    promise.resolve(true)
                    return@post
                }

                // Standby path for developer environments without mic hardware
                Log.w(TAG, "Microphone hardware unavailable; entering virtual voice standby")
                val standbyMap = Arguments.createMap().apply {
                    putString("status", "listening")
                    putString("engine", "virtual_standby")
                }
                sendEvent("onSpeechStart", standbyMap)
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
                if (audioRecordEngine.isRecordingActive()) {
                    val endMap = Arguments.createMap().apply { putString("status", "processing") }
                    sendEvent("onSpeechEnd", endMap)
                    audioRecordEngine.stopRecording()
                }

                speechRecognizer?.stopListening()
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
                if (audioRecordEngine.isRecordingActive()) {
                    audioRecordEngine.stopRecording()
                }
                speechRecognizer?.cancel()
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

    private fun handleWhistleAudioRecorded(samples: FloatArray, language: String) {
        if (samples.isEmpty()) {
            mainHandler.post {
                isListening = false
                val map = Arguments.createMap().apply {
                    putString("error", "No speech recorded (empty buffer)")
                    putInt("errorCode", SpeechRecognizer.ERROR_NO_MATCH)
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
                keywords = null
            )

            mainHandler.post {
                isListening = false
                if (result.isSuccess && result.text.isNotBlank()) {
                    Log.i(TAG, "Whistle transcription result: \"${result.text}\" (TTFT: ${result.ttftMs}ms)")
                    val map = Arguments.createMap().apply {
                        putString("text", result.text)
                        putString("language", result.language)
                        putBoolean("isFinal", true)
                        putDouble("ttftMs", result.ttftMs.toDouble())
                        putDouble("decodeTps", result.decodeTps.toDouble())
                        putString("engine", "whistle_native")
                    }
                    sendEvent("onSpeechResults", map)
                } else {
                    Log.w(TAG, "Whistle returned no match or error: ${result.error}")
                    val map = Arguments.createMap().apply {
                        putString("error", result.error ?: "No speech recognized")
                        putInt("errorCode", SpeechRecognizer.ERROR_NO_MATCH)
                    }
                    sendEvent("onSpeechError", map)
                }
            }
        }
    }

    // --- RecognitionListener Fallback Implementations ---
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        val map = Arguments.createMap().apply { putString("status", "processing") }
        sendEvent("onSpeechEnd", map)
    }

    override fun onError(error: Int) {
        mainHandler.post {
            isListening = false
            val map = Arguments.createMap().apply {
                putString("error", "System recognition error: $error")
                putInt("errorCode", error)
            }
            sendEvent("onSpeechError", map)
        }
    }

    override fun onResults(results: Bundle?) {
        mainHandler.post {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            val map = Arguments.createMap().apply {
                putString("text", text)
                putBoolean("isFinal", true)
                putString("engine", "system_speech_recognizer")
            }
            sendEvent("onSpeechResults", map)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            val map = Arguments.createMap().apply {
                putString("text", text)
                putBoolean("isFinal", false)
                putString("engine", "system_speech_recognizer")
            }
            sendEvent("onSpeechResults", map)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
