"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.WhistleVoiceProvider = void 0;
const react_native_1 = require("react-native");
const NativeWhistle = react_native_1.NativeModules.WhistleModule || react_native_1.NativeModules.SpeechRecognition;
const whistleEmitter = NativeWhistle ? new react_native_1.NativeEventEmitter(NativeWhistle) : null;
/**
 * WhistleVoiceProvider: Core static interface bridging React Native to the
 * embedded C++ JNI Whistle offline speech recognition engine.
 */
class WhistleVoiceProvider {
    /**
     * Checks whether speech recognition capabilities are supported and initialized
     * on the current hardware.
     */
    static async isAvailable() {
        if (react_native_1.Platform.OS !== 'android')
            return false;
        if (!NativeWhistle)
            return false;
        try {
            if (typeof NativeWhistle.isRecognitionAvailable === 'function') {
                return await NativeWhistle.isRecognitionAvailable();
            }
            if (typeof NativeWhistle.isAvailable === 'function') {
                return await NativeWhistle.isAvailable();
            }
            return false;
        }
        catch {
            return false;
        }
    }
    /**
     * Ensures runtime RECORD_AUDIO permission is granted on Android / Fire OS.
     */
    static async requestPermission() {
        if (react_native_1.Platform.OS !== 'android')
            return true;
        try {
            const has = await react_native_1.PermissionsAndroid.check(react_native_1.PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
            if (has)
                return true;
            const res = await react_native_1.PermissionsAndroid.request(react_native_1.PermissionsAndroid.PERMISSIONS.RECORD_AUDIO, {
                title: 'Microphone Permission',
                message: 'This application requires microphone access for offline voice commands.',
                buttonPositive: 'Allow',
            });
            return res === react_native_1.PermissionsAndroid.RESULTS.GRANTED;
        }
        catch (err) {
            console.warn('[WhistleVoiceProvider] Permission request error:', err);
            return false;
        }
    }
    /**
     * Starts on-device voice recording and hooks event callbacks.
     */
    static async startListening(callbacks = {}, config = {}) {
        if (react_native_1.Platform.OS !== 'android') {
            callbacks.onError?.('Whistle offline voice is supported on Android TV & Fire TV.');
            return false;
        }
        const hasPermission = await this.requestPermission();
        if (!hasPermission) {
            callbacks.onError?.('Microphone permission not granted.');
            return false;
        }
        this.clearSubscriptions();
        if (whistleEmitter) {
            if (callbacks.onSpeechStart) {
                this.subscriptions.push(whistleEmitter.addListener('onSpeechStart', callbacks.onSpeechStart));
            }
            if (callbacks.onSpeechResult) {
                this.subscriptions.push(whistleEmitter.addListener('onSpeechResults', (event) => {
                    const rawText = event?.text || '';
                    const shouldClean = config.cleanPunctuation !== false;
                    const cleanedText = shouldClean
                        ? rawText.replace(/[.,/#!$%^&*;:{}=\-_`~()?]+$/g, '').trim()
                        : rawText;
                    const result = {
                        text: cleanedText,
                        rawText: rawText,
                        language: event?.language || 'en',
                        isFinal: event?.isFinal !== false,
                        engine: event?.engine || 'whistle_native',
                        ttftMs: event?.ttftMs,
                        decodeTps: event?.decodeTps,
                    };
                    callbacks.onSpeechResult?.(result);
                }));
            }
            if (callbacks.onError) {
                this.subscriptions.push(whistleEmitter.addListener('onSpeechError', (event) => {
                    const errMessage = event?.error || event?.message || 'Speech recognition failed';
                    callbacks.onError?.(errMessage);
                }));
            }
            if (callbacks.onSpeechEnd) {
                this.subscriptions.push(whistleEmitter.addListener('onSpeechEnd', callbacks.onSpeechEnd));
            }
        }
        try {
            const lang = config.language || 'en';
            const cleanPunc = config.cleanPunctuation !== false;
            const maxDur = config.maxDurationMs || 6000;
            const postSilence = config.postSpeechSilenceMs || 1400;
            if (typeof NativeWhistle?.startListeningWithConfig === 'function') {
                await NativeWhistle.startListeningWithConfig({
                    language: lang,
                    keywords: config.keywords || [],
                    maxDurationMs: maxDur,
                    postSpeechSilenceMs: postSilence,
                    cleanPunctuation: cleanPunc,
                });
            }
            else {
                await NativeWhistle?.startListening?.(lang);
            }
            return true;
        }
        catch (err) {
            this.clearSubscriptions();
            callbacks.onError?.(err?.message || 'Failed to start microphone recording');
            return false;
        }
    }
    /**
     * Stops recording and finalizes transcription.
     */
    static async stopListening() {
        try {
            await NativeWhistle?.stopListening?.();
        }
        catch (err) {
            console.warn('[WhistleVoiceProvider] stopListening notice:', err);
        }
    }
    /**
     * Cancels recording and discards all transient audio buffers.
     */
    static async cancelListening() {
        this.clearSubscriptions();
        try {
            await NativeWhistle?.cancelListening?.();
        }
        catch (err) {
            console.warn('[WhistleVoiceProvider] cancelListening notice:', err);
        }
    }
    static clearSubscriptions() {
        this.subscriptions.forEach((sub) => sub.remove());
        this.subscriptions = [];
    }
}
exports.WhistleVoiceProvider = WhistleVoiceProvider;
WhistleVoiceProvider.subscriptions = [];
