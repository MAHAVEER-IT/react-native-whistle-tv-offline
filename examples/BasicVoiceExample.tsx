import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  NativeModules,
} from 'react-native';
import { useWhistleVoice, WhistleSpeechResult } from '../src';

/**
 * BasicVoiceExample: A production-ready 10-foot living room voice search component.
 * Demonstrates offline STT with zero cloud roundtrips and complete D-pad focus states.
 */
export const BasicVoiceExample: React.FC = () => {
  const [metrics, setMetrics] = useState<{ ttftMs?: number; engine?: string }>({});
  const [micFocused, setMicFocused] = useState<boolean>(false);
  const [simulateFocused, setSimulateFocused] = useState<boolean>(false);

  const { isListening, transcript, error, startListening, stopListening } = useWhistleVoice({
    language: 'en',
    keywords: ['lofi', 'chill', 'rock', 'synthwave', 'play', 'pause'],
    onResult: (text: string, result: WhistleSpeechResult) => {
      setMetrics({
        ttftMs: result.ttftMs,
        engine: result.engine,
      });
    },
    onError: (err) => {
      console.warn('Whistle STT error:', err);
    },
  });

  const handleSimulate = useCallback(() => {
    // For automated testing or emulators without hardware microphones
    const module = NativeModules.WhistleModule || NativeModules.SpeechRecognition;
    module?.simulateVoiceInput?.('Play high energy workout beats');
  }, []);

  return (
    <View style={styles.container}>
      {/* TV Header Banner */}
      <View style={styles.header}>
        <Text style={styles.title}>🎙️ Whistle Offline Voice Engine</Text>
        <Text style={styles.subtitle}>
          100% On-Device Speech Recognition for Fire TV & Android TV
        </Text>
      </View>

      {/* Main Focusable Voice Action Button */}
      <View style={styles.card}>
        <TouchableOpacity
          hasTVPreferredFocus={true}
          activeOpacity={0.8}
          onFocus={() => setMicFocused(true)}
          onBlur={() => setMicFocused(false)}
          onPress={() => {
            if (isListening) {
              stopListening();
            } else {
              startListening();
            }
          }}
          style={[
            styles.micButton,
            micFocused && styles.micButtonFocused,
            isListening && styles.micButtonListening,
          ]}
        >
          {isListening ? (
            <ActivityIndicator size="large" color="#FFFFFF" />
          ) : (
            <Text style={styles.micIcon}>🎤</Text>
          )}
          <Text style={styles.micLabel}>
            {isListening ? 'Listening (Press OK to Stop)...' : 'Press Select / OK to Speak'}
          </Text>
        </TouchableOpacity>

        {/* Emulation / Testing Button for Emulators */}
        <TouchableOpacity
          activeOpacity={0.8}
          onFocus={() => setSimulateFocused(true)}
          onBlur={() => setSimulateFocused(false)}
          onPress={handleSimulate}
          style={[
            styles.simulateButton,
            simulateFocused && styles.simulateButtonFocused,
          ]}
        >
          <Text style={styles.simulateLabel}>⚡ Simulate Voice Query (Emulator Test)</Text>
        </TouchableOpacity>

        {/* Live Status & Transcript Display */}
        <View style={styles.resultBox}>
          <Text style={styles.resultHeading}>Detected Voice Intent:</Text>
          <Text style={styles.resultText}>
            {transcript ? `"${transcript}"` : '(Awaiting voice input...)'}
          </Text>

          {metrics.ttftMs !== undefined && (
            <View style={styles.metricsRow}>
              <Text style={styles.metricBadge}>
                ⚡ Latency: {metrics.ttftMs.toFixed(1)} ms
              </Text>
              <Text style={styles.metricBadge}>
                🔒 Engine: {metrics.engine || 'whistle_native'}
              </Text>
            </View>
          )}

          {error && <Text style={styles.errorText}>⚠️ {error}</Text>}
        </View>

        {/* Privacy & Compliance Assurance */}
        <View style={styles.privacyBanner}>
          <Text style={styles.privacyText}>
            🛡️ Privacy Guaranteed: Zero audio leaves this device. 100% offline inference.
          </Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0C14',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  header: {
    marginBottom: 32,
    alignItems: 'center',
  },
  title: {
    fontSize: 32,
    fontWeight: '800',
    color: '#F8FAFC',
    letterSpacing: 0.5,
  },
  subtitle: {
    fontSize: 16,
    color: '#94A3B8',
    marginTop: 8,
  },
  card: {
    width: '100%',
    maxWidth: 680,
    backgroundColor: '#141824',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#262D42',
    padding: 32,
    alignItems: 'center',
  },
  micButton: {
    width: '100%',
    paddingVertical: 24,
    paddingHorizontal: 32,
    backgroundColor: '#1E2538',
    borderRadius: 16,
    borderWidth: 3,
    borderColor: 'transparent',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  micButtonFocused: {
    borderColor: '#8B5CF6',
    backgroundColor: '#2A1B4E',
    transform: [{ scale: 1.02 }],
  },
  micButtonListening: {
    borderColor: '#EF4444',
    backgroundColor: '#3E1818',
  },
  micIcon: {
    fontSize: 40,
    marginBottom: 8,
  },
  micLabel: {
    fontSize: 18,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  simulateButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    backgroundColor: '#182032',
    borderRadius: 10,
    borderWidth: 2,
    borderColor: 'transparent',
    marginBottom: 24,
  },
  simulateButtonFocused: {
    borderColor: '#38BDF8',
    backgroundColor: '#0F2E45',
  },
  simulateLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#94A3B8',
  },
  resultBox: {
    width: '100%',
    backgroundColor: '#0A0C14',
    borderRadius: 12,
    padding: 20,
    borderWidth: 1,
    borderColor: '#262D42',
    marginBottom: 16,
  },
  resultHeading: {
    fontSize: 12,
    fontWeight: '700',
    color: '#64748B',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 8,
  },
  resultText: {
    fontSize: 22,
    fontWeight: '600',
    color: '#38BDF8',
    minHeight: 32,
  },
  metricsRow: {
    flexDirection: 'row',
    marginTop: 12,
    gap: 8,
  },
  metricBadge: {
    fontSize: 12,
    color: '#10B981',
    backgroundColor: '#064E3B20',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    overflow: 'hidden',
  },
  errorText: {
    marginTop: 12,
    fontSize: 14,
    color: '#F87171',
  },
  privacyBanner: {
    marginTop: 8,
  },
  privacyText: {
    fontSize: 12,
    color: '#64748B',
    textAlign: 'center',
  },
});

export default BasicVoiceExample;
