package io.codeconcept.realtimecutvadlibrary;

public interface VADCallback {
    void onVoiceStart();
    void onVoiceEnd(byte[] wavData);
    void onVoiceDidContinue(byte[] pcmFloatData);
}
