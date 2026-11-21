package com.limelight.nvstream;

import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * One stream to a host, whatever its protocol: a PC through moonlight-common-c ({@link NvConnection}) or a PlayStation through
 * DELOS's Remote Play client ({@code com.limelight.ps.PsConnection}). The stream activity, the renderers and the input handlers use
 * only this interface, so a PlayStation stream gets the same decoder, upscalers, pacing, overlay, stream menu and controller handling.
 * Input a host does not take returns {@link com.limelight.nvstream.jni.MoonBridge#LI_ERR_UNSUPPORTED} (or does nothing).
 */
public interface StreamConnection {
    /** Starts the stream on a background thread; the renderers are set up and started by the connection, the listener hears every stage. */
    void start(AudioRenderer audioRenderer, VideoDecoderRenderer videoDecoderRenderer, NvConnectionListener connectionListener);

    /** Ends the stream and stops and cleans up both renderers. Blocking; never on the UI thread. */
    void stop();

    /** Round-trip time and its variance packed as {@code (rttMs << 32) | varianceMs}, or -1 when unknown. */
    long getEstimatedRttInfo();

    /** Audio queued ahead of the audio renderer, in ms; the renderer drops samples above 40 ms. */
    int getPendingAudioDurationMs();

    /** Keeps the network path awake on Wi-Fi (the "prevent packet loss" option). */
    void sendKeepAlive();

    /** The host's app version for diagnostics, or null when it has none. */
    String getServerAppVersion();

    /** True for moonlight-common-c streams, whose failures carry port flags worth a connectivity test. */
    boolean reportsMoonlightPortFailures();

    /** Whether {@link #setRuntimeBitrate} can change the bitrate during the stream. */
    boolean supportsRuntimeBitrate();

    int setRuntimeBitrate(int bitrateKbps) throws IOException, XmlPullParserException;

    /** Whether the host takes keyboard, mouse, text and server commands (a PlayStation does not). */
    boolean supportsKeyboardAndMouse();

    void sendExecServerCmd(int cmdId);

    void sendMouseMove(short deltaX, short deltaY);

    void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight);

    void sendMouseMoveAsMousePosition(short deltaX, short deltaY, short referenceWidth, short referenceHeight);

    void sendMouseButtonDown(byte mouseButton);

    void sendMouseButtonUp(byte mouseButton);

    void sendControllerInput(short controllerNumber, short activeGamepadMask, int buttonFlags,
                             byte leftTrigger, byte rightTrigger,
                             short leftStickX, short leftStickY, short rightStickX, short rightStickY);

    void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags);

    void sendMouseScroll(byte scrollClicks);

    void sendMouseHScroll(byte scrollClicks);

    void sendMouseHighResScroll(short scrollAmount);

    void sendMouseHighResHScroll(short scrollAmount);

    int sendTouchEvent(byte eventType, int pointerId, float x, float y, float pressureOrDistance,
                       float contactAreaMajor, float contactAreaMinor, short rotation);

    int sendPenEvent(byte eventType, byte toolType, byte penButtons, float x, float y,
                     float pressureOrDistance, float contactAreaMajor, float contactAreaMinor, short rotation, byte tilt);

    int sendControllerArrivalEvent(byte controllerNumber, short activeGamepadMask, byte type, int supportedButtonFlags, short capabilities);

    int sendControllerTouchEvent(byte controllerNumber, byte eventType, int pointerId, float x, float y, float pressure);

    int sendControllerMotionEvent(byte controllerNumber, byte motionType, float x, float y, float z);

    void sendControllerBatteryEvent(byte controllerNumber, byte batteryState, byte batteryPercentage);

    void sendUtf8Text(String text);
}
