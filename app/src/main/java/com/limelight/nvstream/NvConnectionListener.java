package com.limelight.nvstream;

public interface NvConnectionListener {
    void stageStarting(String stage);
    void stageComplete(String stage);
    boolean stageFailed(String stage, int portFlags, int errorCode);
    
    void connectionStarted();
    void connectionTerminated(int errorCode);
    void connectionStatusUpdate(int connectionStatus);
    
    void displayMessage(String message);
    void displayTransientMessage(String message);

    void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor);
    void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger);

    void setHdrMode(boolean enabled, byte[] hdrMetadata);

    void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz);

    void setControllerLED(short controllerNumber, byte r, byte g, byte b);

    /**
     * DualSense trigger effects: [eventFlags] 0x04 right and 0x08 left trigger change, each a mode and 10 parameter bytes in the
     * DualSense output report format (a PC host's 0x5503, a PS5's data type 11). Only a DualSense DELOS drives itself takes them.
     */
    default void setAdaptiveTriggers(short controllerNumber, byte eventFlags, byte typeLeft, byte typeRight, byte[] left, byte[] right) {}

    /**
     * Vibepollo waveform haptics (0x5601): [frames] 48 kHz signed 16-bit stereo actuator frames in [pcm] for a controller that
     * advertised LI_CCAP_HAPTICS_PCM. Called on the control receive thread every 5 ms; must not block, and [pcm] is reused
     * after returning.
     */
    default void controllerHaptics(short controllerNumber, byte[] pcm, int frames) {}
}
