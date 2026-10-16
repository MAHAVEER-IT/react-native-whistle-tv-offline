import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  PermissionsAndroid,
  EmitterSubscription,
} from 'react-native';
import { WhistleConfig, WhistleSpeechResult, WhistleVoiceCallbacks } from '../types';

const NativeWhistle = NativeModules.WhistleModule || NativeModules.SpeechRecognition;

const whistleEmitter = NativeWhistle ? new NativeEventEmitter(NativeWhistle) : null;

/**
 * WhistleVoiceProvider: Core static interface bridging React Native to the
 * embedded C++ JNI Whistle offline speech recognition engine.
 */
export class WhistleVoiceProvider {
  private static subscriptions: EmitterSubscription[] = [];

  /**
   * Checks whether speech recognition capabilities are supported and initialized
   * on the current hardware.
   */
  public static async isAvailable(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;
    if (!NativeWhistle) return false;
    try {
      if (typeof NativeWhistle.isRecognitionAvailable === 'function') {
        return await NativeWhistle.isRecognitionAvailable();
      }
      if (typeof NativeWhistle.isAvailable === 'function') {
        return await NativeWhistle.isAvailable();
      }
      return false;
    } catch {
      return false;
    }
  }

  /**
   * Ensures runtime RECORD_AUDIO permission is granted on Android / Fire OS.
   */
  public static async requestPermission(): Promise<boolean> {
    if (Platform.OS !== 'android') return true;
    try {
      const has = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
      if (has) return true;
      const res = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO, {
        title: 'Microphone Permission',
        message: 'This application requires microphone access for offline voice commands.',
        buttonPositive: 'Allow',
      });
      return res === PermissionsAndroid.RESULTS.GRANTED;
    } catch (err) {
      console.warn('[WhistleVoiceProvider] Permission request error:', err);
      return false;
    }
  }

  /**
   * Starts on-device voice recording and hooks event callbacks.
   */
  public static async startListening(
    callbacks: WhistleVoiceCallbacks = {},
    config: WhistleConfig = {}
  ): Promise<boolean> {
    if (Platform.OS !== 'android') {
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
        this.subscriptions.push(
          whistleEmitter.addListener('onSpeechResults', (event: any) => {
            const result: WhistleSpeechResult = {
              text: event?.text || '',
              language: event?.language || 'en',
              isFinal: event?.isFinal !== false,
              engine: event?.engine || 'whistle_native',
              ttftMs: event?.ttftMs,
              decodeTps: event?.decodeTps,
            };
            callbacks.onSpeechResult?.(result);
          })
        );
      }
      if (callbacks.onError) {
        this.subscriptions.push(
          whistleEmitter.addListener('onSpeechError', (event: any) => {
            const errMessage = event?.error || event?.message || 'Speech recognition failed';
            callbacks.onError?.(errMessage);
          })
        );
      }
      if (callbacks.onSpeechEnd) {
        this.subscriptions.push(whistleEmitter.addListener('onSpeechEnd', callbacks.onSpeechEnd));
      }
    }

    try {
      const lang = config.language || 'en';
      if (config.keywords && config.keywords.length > 0 && typeof NativeWhistle?.startListeningWithConfig === 'function') {
        await NativeWhistle.startListeningWithConfig({
          language: lang,
          keywords: config.keywords,
          maxDurationMs: config.maxDurationMs || 5000,
        });
      } else {
        await NativeWhistle?.startListening?.(lang);
      }
      return true;
    } catch (err: any) {
      this.clearSubscriptions();
      callbacks.onError?.(err?.message || 'Failed to start microphone recording');
      return false;
    }
  }

  /**
   * Stops recording and finalizes transcription.
   */
  public static async stopListening(): Promise<void> {
    try {
      await NativeWhistle?.stopListening?.();
    } catch (err) {
      console.warn('[WhistleVoiceProvider] stopListening notice:', err);
    }
  }

  /**
   * Cancels recording and discards all transient audio buffers.
   */
  public static async cancelListening(): Promise<void> {
    this.clearSubscriptions();
    try {
      await NativeWhistle?.cancelListening?.();
    } catch (err) {
      console.warn('[WhistleVoiceProvider] cancelListening notice:', err);
    }
  }

  private static clearSubscriptions(): void {
    this.subscriptions.forEach((sub) => sub.remove());
    this.subscriptions = [];
  }
}
