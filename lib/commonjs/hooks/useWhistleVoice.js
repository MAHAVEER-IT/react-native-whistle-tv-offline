"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.useWhistleVoice = useWhistleVoice;
const react_1 = require("react");
const WhistleNativeBridge_1 = require("../native/WhistleNativeBridge");
/**
 * useWhistleVoice: Convenient React hook for on-demand 10-foot TV voice control.
 */
function useWhistleVoice(options = {}) {
    const [isListening, setIsListening] = (0, react_1.useState)(false);
    const [transcript, setTranscript] = (0, react_1.useState)('');
    const [error, setError] = (0, react_1.useState)(null);
    const isListeningRef = (0, react_1.useRef)(false);
    isListeningRef.current = isListening;
    const optionsRef = (0, react_1.useRef)(options);
    optionsRef.current = options;
    const startListening = (0, react_1.useCallback)(async () => {
        if (isListeningRef.current)
            return true;
        setError(null);
        optionsRef.current.onStart?.();
        const started = await WhistleNativeBridge_1.WhistleVoiceProvider.startListening({
            onSpeechStart: () => {
                setIsListening(true);
            },
            onSpeechResult: (result) => {
                setTranscript(result.text);
                setIsListening(false);
                optionsRef.current.onResult?.(result.text, result);
            },
            onError: (errMessage) => {
                setError(errMessage);
                setIsListening(false);
                optionsRef.current.onError?.(errMessage);
            },
            onSpeechEnd: () => {
                setIsListening(false);
            },
        }, {
            language: optionsRef.current.language || 'en',
            keywords: optionsRef.current.keywords,
            maxDurationMs: optionsRef.current.maxDurationMs || 6000,
        });
        if (!started) {
            setIsListening(false);
        }
        return started;
    }, []);
    const stopListening = (0, react_1.useCallback)(async () => {
        setIsListening(false);
        await WhistleNativeBridge_1.WhistleVoiceProvider.stopListening();
    }, []);
    const cancelListening = (0, react_1.useCallback)(async () => {
        setIsListening(false);
        await WhistleNativeBridge_1.WhistleVoiceProvider.cancelListening();
    }, []);
    // Cleanup on unmount
    (0, react_1.useEffect)(() => {
        return () => {
            if (isListeningRef.current) {
                WhistleNativeBridge_1.WhistleVoiceProvider.cancelListening();
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
