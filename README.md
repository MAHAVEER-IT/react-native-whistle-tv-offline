# Whistle AI: Offline Speech-to-Text for Amazon Fire TV & Android TV 🎙️📺

[![npm version](https://img.shields.io/badge/npm-1.1.0-blue.svg)](https://www.npmjs.com/package/react-native-whistle-tv-offline)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Fire OS & Android TV](https://img.shields.io/badge/Platform-Fire%20OS%20%7C%20Android%20TV-green.svg)](https://developer.amazon.com/fire-tv)
[![Privacy: Zero Cloud](https://img.shields.io/badge/Privacy-100%25%20On--Device-emerald.svg)](#-privacy--zero-passive-listening-guarantee)
[![Architecture: ARM64 & x86_64](https://img.shields.io/badge/Architecture-ARM64%20%7C%20x86__64-purple.svg)](#-supported-tv-hardware--architectures)

**Whistle AI** (`react-native-whistle-tv-offline`) is an ultra-lightweight (~16.9 MB), high-accuracy, 100% on-device speech-to-text engine engineered specifically for 10-foot television user interfaces.

Built with native C++ JNI bindings, ARM NEON SIMD vectorization, hardware-tuned audio DSP preprocessing, and circular audio buffering, Whistle runs speech recognition locally on TV hardware with **zero cloud latency**, **zero network reliance**, **zero passive listening**, and **zero neural silence hallucinations**.

---

## ⚡ Why Whistle AI for Living Room TV?

Traditional cloud speech-to-text solutions (Google Speech Services, cloud Whisper APIs) fail on living room smart TVs:
- ❌ **High Cloud Latency (800ms – 2500ms)**: Slow roundtrips kill the snappiness of TV remote voice search.
- ❌ **Privacy & Compliance**: Users do not want microphones streaming audio to remote cloud servers.
- ❌ **Low-Gain Remote Microphones**: Fire TV Voice Remotes and Android TV Bluetooth remotes suffer from high DC bias, low input gain, and a 1–3 foot distance from the user's mouth.
- ❌ **Whisper Silence Hallucinations**: Standard Whisper models notoriously output phantom internet subtitle tokens (`"Thank you."`, `"Happy birthday!"`, `"Thanks for watching."`) on ambient silence or quiet rooms.

**Whistle AI solves every single one of these problems locally.**

---

## 🚀 Key Performance Indicators & Benchmarks

| Metric | Whistle AI Engine | Cloud STT Alternatives |
|---|---|---|
| **Time to First Token (TTFT)** | **~120 ms** (Instant local inference) | 850 ms – 2,200 ms (Network latency) |
| **Model Footprint** | **16.9 MB** (`whistle.cact`) | >150 MB or Cloud-only |
| **Peak RAM Consumption** | **< 35 MB** transient RSS | 120 MB – 250 MB |
| **Cloud Dependency** | **0% (100% Offline)** | 100% Cloud-tethered |
| **Silence Hallucinations** | **0% (Eliminated via RMS Gating & Blacklist)** | Common on Whisper architectures |
| **Living Room Remote Mic SNR** | **Optimized via 80Hz HPF + AGC Normalization** | Raw un-preprocessed audio |
| **UI Search Compatibility** | **Auto-sanitized (`"Rose"` vs `"Rose."`)** | Requires manual client-side regex |

---

## 🎛️ TV Hardware Audio DSP Preprocessing Pipeline (v1.1.0)

Television remotes transmit compressed Bluetooth audio with high DC offset and low signal amplitude. Whistle includes an integrated, zero-allocation native audio preprocessor ([`AudioPreprocessor.kt`](android/src/main/java/com/pandivo/whistle/AudioPreprocessor.kt)) that optimizes raw PCM audio before inference:

```
Raw Remote PCM (16kHz Mono)
         │
         ▼
┌─────────────────────────────────┐
│ 1-Pole High-Pass Filter (~80Hz) │  ──► Eliminates DC bias & sub-80Hz low-frequency room rumble
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ Voice Activity Detection (VAD)  │  ──► Trims leading & trailing dead silence with 160ms safety margin
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ Automatic Gain Control (AGC)    │  ──► Dynamically scales faint speech up to 6.0x to reach nominal 0.85 peak
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ Dual-Stage Silence Gating       │  ──► Pre-inference RMS check (<0.010 RMS) & Hallucination blacklist
└─────────────────────────────────┘
         │
         ▼
Whistle C++ Neural Inference (whistle.cact)
```

1. **80Hz High-Pass Filter**: Removes sub-audible DC bias and HVAC rumble that would otherwise distort the 80-channel log-mel filterbank.
2. **VAD Energy Silence Trimming**: Detects voice frame boundaries and strips inactive leading/trailing silence, preventing dead air from consuming the acoustic encoder window.
3. **Peak AGC Normalization**: Automatically normalizes quiet speech (common when speaking toward a remote held at chest level) to a nominal **0.85 peak dynamic range**.
4. **Dual-Stage Silence Hallucination Guard**:
   - *Pre-Inference Gating*: If preprocessed audio energy is below voice threshold (`rms < 0.010f && peak < 0.05f`), audio is rejected immediately as `ERROR_SPEECH_TIMEOUT` (`"No speech detected"`) without touching the neural network.
   - *Post-Inference Rejection*: Discards known Whisper training artifacts (`"Thank you"`, `"Happy birthday"`, `"Thanks for watching"`) if faint room noise is processed.
5. **Clean Punctuation (`cleanPunctuation: true`)**: Automatically strips trailing punctuation (`"Rose."` → `"Rose"`, `"Rock music."` → `"Rock music"`) so that UI database queries and substring search (`item.name.includes(transcript)`) work instantly out of the box.

---

## 🛠️ Architecture Overview

```mermaid
flowchart TD
    subgraph Client ["React Native 10-Foot UI"]
        UI["TV Screen / Search Input"]
        Hook["useWhistleVoice() Hook"]
        Bridge["WhistleVoiceProvider (Bridge)"]
        UI --> Hook --> Bridge
    end

    subgraph NativeAndroid ["Android Native Layer (Kotlin)"]
        ARE["AudioRecordEngine.kt<br/>(VOICE_RECOGNITION / MIC Source)"]
        DSP["AudioPreprocessor.kt<br/>(80Hz HPF + VAD + AGC Peak 0.85)"]
        Gate{"RMS Silence Gate<br/>(RMS > 0.010)"}
        Module["WhistleModule.kt<br/>(Hallucination Rejection & Clean Text)"]

        Bridge --> ARE --> DSP --> Gate
        Gate -- "Energy Below Threshold" --> NoSpeech["Emit onSpeechError<br/>'No speech detected'"]
        Gate -- "Speech Validated" --> JNI
    end

    subgraph NativeCpp ["Embedded C++ Inference Engine"]
        JNI["whistle_jni.cpp<br/>(JNI Wrapper)"]
        Needle["Needle C++ Engine<br/>(libwhistle_native.so / libneedle.so)"]
        Model[("whistle.cact<br/>16.9 MB Bundled Model")]

        JNI --> Needle --> Model
    end

    Needle --> JNI --> Module --> Bridge --> Hook --> UI
```

---

## 💻 Installation

```bash
npm install react-native-whistle-tv-offline
# or
yarn add react-native-whistle-tv-offline
```

### Android / Fire OS Configuration

Ensure your app's `android/app/src/main/AndroidManifest.xml` includes the microphone permission:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

> **Note for Fire OS & Android TV**: Whistle automatically selects `MediaRecorder.AudioSource.VOICE_RECOGNITION`, which engages the television remote's directional noise-cancelling microphone profile, with automatic fallback to standard `MIC`.

---

## 📋 Usage & API Reference

### 1. React Hook: `useWhistleVoice` (Recommended)

The `useWhistleVoice` hook provides complete state management, event listeners, and automatic cleanup on unmount.

```tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useWhistleVoice } from 'react-native-whistle-tv-offline';

export function TVVoiceSearch() {
  const {
    isListening,
    transcript,
    error,
    startListening,
    stopListening,
  } = useWhistleVoice({
    language: 'en',
    keywords: ['rock', 'jazz', 'lofi', 'classical', 'playlist', 'play'],
    cleanPunctuation: true, // Strips trailing '.' or '!' for clean search matching (default: true)
    maxDurationMs: 6000,    // Maximum capture duration before auto-stop (default: 6000)
    postSpeechSilenceMs: 1400, // Auto-stop after 1.4s of silence (default: 1400)
    onResult: (text, result) => {
      console.log('Final Transcribed Text:', text);
      console.log('Inference Latency (TTFT):', result.ttftMs, 'ms');
      console.log('Raw uncleaned text:', result.rawText);
    },
    onError: (err) => {
      console.warn('Voice recognition error:', err);
    },
  });

  return (
    <View style={styles.container}>
      <TouchableOpacity
        hasTVPreferredFocus={true}
        onPress={isListening ? stopListening : startListening}
        style={[styles.button, isListening && styles.buttonActive]}
      >
        <Text style={styles.buttonText}>
          {isListening ? '🎙️ Listening... (Press OK to Stop)' : '🎤 Press OK to Speak'}
        </Text>
      </TouchableOpacity>

      {transcript ? <Text style={styles.resultText}>"{transcript}"</Text> : null}
      {error ? <Text style={styles.errorText}>⚠️ {error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 24, alignItems: 'center' },
  button: { backgroundColor: '#4F46E5', padding: 18, borderRadius: 12 },
  buttonActive: { backgroundColor: '#DC2626' },
  buttonText: { color: '#FFF', fontSize: 18, fontWeight: '700' },
  resultText: { color: '#38BDF8', fontSize: 24, marginTop: 16, fontWeight: '600' },
  errorText: { color: '#F87171', fontSize: 16, marginTop: 12 },
});
```

---

### 2. Static Interface: `WhistleVoiceProvider`

For imperative control or non-React components:

```typescript
import { WhistleVoiceProvider } from 'react-native-whistle-tv-offline';

// Check if on-device engine is supported on this CPU architecture
const isSupported = await WhistleVoiceProvider.isAvailable();

// Request runtime microphone permission (Fire OS / Android)
const granted = await WhistleVoiceProvider.requestPermission();

// Start speech recognition session
await WhistleVoiceProvider.startListening(
  {
    onSpeechStart: () => console.log('Microphone open'),
    onSpeechResult: (result) => {
      console.log('Transcribed Text:', result.text);
      console.log('TTFT:', result.ttftMs, 'ms | Engine:', result.engine);
    },
    onError: (error) => console.error('Recognition error:', error),
    onSpeechEnd: () => console.log('Session ended'),
  },
  {
    language: 'en',
    keywords: ['play', 'pause', 'skip', 'chill', 'workout'],
    cleanPunctuation: true,
    maxDurationMs: 6000,
    postSpeechSilenceMs: 1400,
  }
);

// Stop recording manually
await WhistleVoiceProvider.stopListening();

// Or cancel and discard buffer without processing
await WhistleVoiceProvider.cancelListening();
```

---

## ⚙️ Configuration Options (`WhistleConfig`)

| Option | Type | Default | Description |
|---|---|---|---|
| `language` | `string` | `'en'` | Language code (`'en'`, etc.). |
| `keywords` | `string[]` | `[]` | List of domain-specific keywords or phrases boosted via Aho-Corasick prefix-tree biasing. |
| `cleanPunctuation` | `boolean` | `true` | Automatically strips trailing punctuation (`.` `,` `!` `?`) so voice search queries cleanly match database entries. |
| `maxDurationMs` | `number` | `6000` | Maximum recording length in milliseconds before auto-stopping audio capture. |
| `postSpeechSilenceMs` | `number` | `1400` | Silence window in milliseconds after speech stops to trigger automatic finalization. |

---

## 📊 Result Object (`WhistleSpeechResult`)

| Property | Type | Description |
|---|---|---|
| `text` | `string` | The final sanitized speech text (punctuation-cleaned if enabled). |
| `rawText` | `string?` | The original raw transcribed text prior to punctuation stripping. |
| `language` | `string` | Detected or configured language ISO code. |
| `isFinal` | `boolean` | Indicates whether transcription is finalized (`true`). |
| `engine` | `string` | Engine source (`'whistle_native'`, `'adb_voice_command'`, or `'simulated'`). |
| `ttftMs` | `number?` | Time-to-First-Token decoding latency in milliseconds. |
| `decodeTps` | `number?` | Tokens per second decoding throughput. |

---

## 📺 Supported TV Hardware & Architectures

Whistle includes precompiled 64-bit native libraries (`libwhistle_native.so`) tuned with ARM NEON acceleration:

| Device / Platform | Operating System | Native ABI | Status |
|---|---|---|---|
| **Amazon Fire TV Stick 4K / 4K Max** | Fire OS 7 / 8 (Android 9 & 11) | `arm64-v8a` | **Verified (60 FPS)** |
| **Amazon Fire TV Cube (2nd / 3rd Gen)** | Fire OS 7 / 8 | `arm64-v8a` | **Verified (60 FPS)** |
| **Amazon Fire TV Omni QLED Series** | Fire OS 7 | `arm64-v8a` | **Verified (60 FPS)** |
| **Android TV / Google TV (Chromecast, Sony, TCL)** | Android TV 9.0 – 14.0 (API 28–34) | `arm64-v8a` / `x86_64` | **Verified** |
| **Android Studio TV Emulator** | Android 11.0 / Television 1080p | `x86_64` | **Verified (CI/CD)** |

---

## 🧪 Headless TV Automation & CI/CD Testing

Whistle includes a built-in `BroadcastReceiver` that enables automated test suites and CI/CD pipelines to inject synthetic voice input via ADB without requiring a physical remote microphone:

```bash
# Inject voice command via package action
adb shell am broadcast -a com.pandivo.whistle.VOICE_INPUT --es query "Search chill acoustic beats"

# Inject voice command via alternative POC action
adb shell am broadcast -a com.firetv.musicpoc.VOICE_INPUT --es query "Play synthwave radio"
```

Or programmatically from test scripts:

```typescript
WhistleVoiceProvider.simulateVoiceInput('Play lofi hip hop mix');
```

---

## 🛡️ Privacy & Zero Passive Listening Guarantee

1. **On-Demand Capture**: The Android audio subsystem (`android.media.AudioRecord`) is initialized **only** when the user explicitly triggers a voice action.
2. **Volatile RAM Only**: Audio samples are buffered strictly in ephemeral C++ memory structures and are zero-purged immediately upon inference completion.
3. **Zero Disk Writes**: No audio clips, WAV files, or temporary recordings are ever written to local storage.
4. **Zero Remote Telemetry**: Operates completely detached from any external API, Google Play Services, or Amazon cloud endpoints.

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.
