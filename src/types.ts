/**
 * Type definitions for react-native-whistle-tv-offline
 * Engineered for Amazon Fire TV & Android TV
 */

export interface WhistleConfig {
  /** Language ISO code (default: 'en') */
  language?: string;
  /** Custom keyword biasing list to boost recognition of audio/media terms */
  keywords?: string[];
  /** Maximum recording duration in milliseconds before auto-stop (default: 6000) */
  maxDurationMs?: number;
  /** Silence duration in milliseconds after speech before automatically stopping capture (default: 1400) */
  postSpeechSilenceMs?: number;
  /** Automatically strip trailing punctuation (e.g. '.', '!') for clean search and voice command matching (default: true) */
  cleanPunctuation?: boolean;
}

export interface WhistleSpeechResult {
  /** The final transcribed speech text */
  text: string;
  /** The raw unstripped transcribed speech text if cleanPunctuation was applied */
  rawText?: string;
  /** Language detected or used */
  language: string;
  /** Whether this is a finalized transcription */
  isFinal: boolean;
  /** Engine used ('whistle_native', 'adb_voice_command', or 'simulated') */
  engine: string;
  /** Time to first token in milliseconds */
  ttftMs?: number;
  /** Tokens per second decoding speed */
  decodeTps?: number;
}

export interface WhistleVoiceCallbacks {
  /** Called when the microphone activates and starts recording */
  onSpeechStart?: () => void;
  /** Called when a speech result is successfully transcribed */
  onSpeechResult?: (result: WhistleSpeechResult) => void;
  /** Called when recognition fails or mic encounters an error */
  onError?: (error: string) => void;
  /** Called when the microphone session completes and closes */
  onSpeechEnd?: () => void;
}

export interface UseWhistleVoiceOptions extends WhistleConfig {
  /** Callback fired upon transcription */
  onResult?: (text: string, result: WhistleSpeechResult) => void;
  /** Callback fired upon error */
  onError?: (error: string) => void;
  /** Callback fired before recording begins */
  onStart?: () => void;
}

export interface UseWhistleVoiceReturn {
  /** Whether the microphone is currently active */
  isListening: boolean;
  /** The most recent transcribed voice text */
  transcript: string;
  /** Current error message if any */
  error: string | null;
  /** Start listening for voice commands */
  startListening: () => Promise<boolean>;
  /** Stop listening and process audio */
  stopListening: () => Promise<void>;
  /** Cancel listening and discard audio without processing */
  cancelListening: () => Promise<void>;
}
