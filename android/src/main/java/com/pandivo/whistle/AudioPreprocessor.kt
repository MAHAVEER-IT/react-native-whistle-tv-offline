package com.pandivo.whistle

import android.util.Log

/**
 * Audio preprocessor designed specifically for Whistle / Whisper neural speech recognition on TV remotes:
 * 1. 1-pole High-Pass Filter (cutoff ~80 Hz): Removes DC offset and sub-80Hz low rumble from remote mics.
 * 2. Voice Activity Detection (VAD) Silence Trimming: Removes leading and trailing dead silence
 *    with a 160ms safety padding window.
 * 3. Peak Normalization / AGC (Automatic Gain Control): Scales low-volume TV remote mic audio
 *    so maximum peak reaches nominal 0.85 dynamic range for optimal 80-channel log-mel extraction.
 */
object AudioPreprocessor {
    private const val TAG = "AudioPreprocessor"
    private const val FRAME_SIZE = 320 // 20ms at 16kHz
    private const val VAD_PADDING_SAMPLES = 2560 // 160ms safety margin

    fun process(rawSamples: FloatArray): FloatArray {
        if (rawSamples.isEmpty()) return rawSamples

        // Step 1: 1-pole High-Pass Filter (cutoff ~80Hz at 16kHz)
        val alpha = 0.97f
        val filtered = FloatArray(rawSamples.size)
        var prevIn = 0f
        var prevOut = 0f
        for (i in rawSamples.indices) {
            val currIn = rawSamples[i]
            val currOut = alpha * (prevOut + currIn - prevIn)
            filtered[i] = currOut
            prevIn = currIn
            prevOut = currOut
        }

        // Step 2: Voice Activity Detection (VAD) Trimming
        val numFrames = filtered.size / FRAME_SIZE
        var firstSpeechFrame = -1
        var lastSpeechFrame = -1
        val frameRmsThreshold = 0.006f

        for (f in 0 until numFrames) {
            val startIdx = f * FRAME_SIZE
            var sumSquares = 0.0
            for (i in 0 until FRAME_SIZE) {
                val s = filtered[startIdx + i]
                sumSquares += (s * s).toDouble()
            }
            val frameRms = kotlin.math.sqrt(sumSquares / FRAME_SIZE).toFloat()
            if (frameRms > frameRmsThreshold) {
                if (firstSpeechFrame == -1) {
                    firstSpeechFrame = f
                }
                lastSpeechFrame = f
            }
        }

        val trimmedSamples = if (firstSpeechFrame != -1 && lastSpeechFrame != -1) {
            val startSample = (firstSpeechFrame * FRAME_SIZE - VAD_PADDING_SAMPLES).coerceAtLeast(0)
            val endSample = ((lastSpeechFrame + 1) * FRAME_SIZE + VAD_PADDING_SAMPLES).coerceAtMost(filtered.size)
            if (endSample > startSample) {
                filtered.copyOfRange(startSample, endSample)
            } else {
                filtered
            }
        } else {
            filtered
        }

        // Step 3: Peak AGC Normalization (Target peak: 0.85)
        var peak = 0f
        for (s in trimmedSamples) {
            val absVal = kotlin.math.abs(s)
            if (absVal > peak) peak = absVal
        }

        if (peak >= 0.015f) {
            val targetPeak = 0.85f
            val gain = (targetPeak / peak).coerceIn(1.0f, 6.0f)
            Log.d(TAG, "Audio preprocessed: rawPeak=$peak, appliedGain=${gain}x, finalSamples=${trimmedSamples.size} (${trimmedSamples.size / 16000f}s)")
            for (i in trimmedSamples.indices) {
                trimmedSamples[i] = (trimmedSamples[i] * gain).coerceIn(-1.0f, 1.0f)
            }
        }

        return trimmedSamples
    }
}
