import { useState, useRef, useCallback, useEffect } from 'react';
import { WhistleVoiceProvider } from '../native/WhistleNativeBridge';
import { UseWhistleVoiceOptions, UseWhistleVoiceReturn, WhistleSpeechResult } from '../types';

/**
 * useWhistleVoice: Convenient React hook for on-demand 10-foot TV voice control.
 */
export function useWhistleVoice(options: UseWhistleVoiceOptions = {}): UseWhistleVoiceReturn {
  const [isListening, setIsListening] = useState<boolean>(false);
  const [transcript, setTranscript] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const isListeningRef = useRef<boolean>(false);
  isListeningRef.current = isListening;

  const optionsRef = useRef(options);
  optionsRef.current = options;

  const startListening = useCallback(async (): Promise<boolean> => {
    if (isListeningRef.current) return true;

    setError(null);
    optionsRef.current.onStart?.();

    const started = await WhistleVoiceProvider.startListening(
      {
        onSpeechStart: () => {
          setIsListening(true);
        },
        onSpeechResult: (result: WhistleSpeechResult) => {
          setTranscript(result.text);
          setIsListening(false);
          optionsRef.current.onResult?.(result.text, result);
        },
        onError: (errMessage: string) => {
          setError(errMessage);
          setIsListening(false);
          optionsRef.current.onError?.(errMessage);
        },
        onSpeechEnd: () => {
          setIsListening(false);
        },
      },
      {
        language: optionsRef.current.language || 'en',
        keywords: optionsRef.current.keywords,
        maxDurationMs: optionsRef.current.maxDurationMs || 6000,
        postSpeechSilenceMs: optionsRef.current.postSpeechSilenceMs || 1400,
        cleanPunctuation: optionsRef.current.cleanPunctuation !== false,
      }
    );

    if (!started) {
      setIsListening(false);
    }
    return started;
  }, []);

  const stopListening = useCallback(async (): Promise<void> => {
    setIsListening(false);
    await WhistleVoiceProvider.stopListening();
  }, []);

  const cancelListening = useCallback(async (): Promise<void> => {
    setIsListening(false);
    await WhistleVoiceProvider.cancelListening();
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (isListeningRef.current) {
        WhistleVoiceProvider.cancelListening();
      }
    };
  }, []);

  return {
    isListening,
    transcript,
    error,
    startListening,
    stopListening,
    cancelListening,
  };
}
