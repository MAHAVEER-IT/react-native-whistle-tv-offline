import { WhistleConfig, WhistleVoiceCallbacks } from '../types';
/**
 * WhistleVoiceProvider: Core static interface bridging React Native to the
 * embedded C++ JNI Whistle offline speech recognition engine.
 */
export declare class WhistleVoiceProvider {
    private static subscriptions;
    /**
     * Checks whether speech recognition capabilities are supported and initialized
     * on the current hardware.
     */
    static isAvailable(): Promise<boolean>;
    /**
     * Ensures runtime RECORD_AUDIO permission is granted on Android / Fire OS.
     */
    static requestPermission(): Promise<boolean>;
    /**
     * Starts on-device voice recording and hooks event callbacks.
     */
    static startListening(callbacks?: WhistleVoiceCallbacks, config?: WhistleConfig): Promise<boolean>;
    /**
     * Stops recording and finalizes transcription.
     */
    static stopListening(): Promise<void>;
    /**
     * Cancels recording and discards all transient audio buffers.
     */
    static cancelListening(): Promise<void>;
    private static clearSubscriptions;
}
