# Contributing to Whistle AI 🎙️

Thank you for your interest in contributing to **Whistle AI** (`react-native-whistle-tv-offline`)! We welcome contributions from developers building for Amazon Fire TV, Android TV, Google TV, and living room voice interfaces.

---

## 🛠️ Development Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/MAHAVEER-IT/pandivo-tv.git
   cd react-native-whistle-tv-offline
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Build TypeScript declarations**:
   ```bash
   npm run build
   ```

4. **Android Native NDK**:
   - Android NDK `25.1.8937393` or higher.
   - CMake `3.22.1` or higher.
   - Android SDK API 24–34.

---

## 📋 Pull Request Guidelines

1. **Branch Naming**:
   - `feat/your-feature-name` for new voice capabilities or platform support.
   - `fix/your-bug-fix` for bug fixes or stability improvements.
   - `docs/your-doc-update` for documentation enhancements.

2. **Code Standards**:
   - Strict TypeScript (`npm run typecheck`).
   - Clean Kotlin formatting following standard Android conventions.
   - Maintain documentation integrity: all public exports must include JSDoc comments.

3. **Privacy Integrity**:
   - Under no circumstances should audio data be written to permanent disk storage or uploaded to unapproved endpoints. Whistle is strictly a **zero-passive-listening, on-device acoustic pipeline**.

---

## 🐛 Reporting Bugs & Vulnerabilities

Please open an issue on the [GitHub Issues](https://github.com/MAHAVEER-IT/pandivo-tv/issues) tab with:
- Target TV Hardware (e.g. Fire TV Stick 4K Max, Fire TV Cube, Android TV Emulator).
- Fire OS / Android API version.
- Logcat snippet showing tag `WhistleEngine` or `AudioRecordEngine`.
