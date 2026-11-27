#include <jni.h>
#include <stdbool.h>

#include <pthread.h>
#include <string.h>

#include <Limelight.h>
#include <ControllerHaptics.h>

#include <opus_multistream.h>
#include <android/log.h>

#include <cpu-features.h>

#include "latency_threads.h"
#include "diag_log.h"

#include <stdarg.h>
#include <stdatomic.h>
#include <stdio.h>
#include <time.h>

static OpusMSDecoder* Decoder;
static OPUS_MULTISTREAM_CONFIGURATION OpusConfig;

static JavaVM *JVM;
static pthread_key_t JniEnvKey;
static pthread_once_t JniEnvKeyInitOnce = PTHREAD_ONCE_INIT;
static jclass GlobalBridgeClass;
static jmethodID BridgeDrSetupMethod;
static jmethodID BridgeDrStartMethod;
static jmethodID BridgeDrStopMethod;
static jmethodID BridgeDrCleanupMethod;
static jmethodID BridgeDrSubmitDecodeUnitMethod;
static jmethodID BridgeArInitMethod;
static jmethodID BridgeArStartMethod;
static jmethodID BridgeArStopMethod;
static jmethodID BridgeArCleanupMethod;
static jmethodID BridgeArPlaySampleMethod;
static jmethodID BridgeClStageStartingMethod;
static jmethodID BridgeClStageCompleteMethod;
static jmethodID BridgeClStageFailedMethod;
static jmethodID BridgeClConnectionStartedMethod;
static jmethodID BridgeClConnectionTerminatedMethod;
static jmethodID BridgeClRumbleMethod;
static jmethodID BridgeClConnectionStatusUpdateMethod;
static jmethodID BridgeClSetHdrModeMethod;
static jmethodID BridgeClRumbleTriggersMethod;
static jmethodID BridgeClSetMotionEventStateMethod;
static jmethodID BridgeClSetControllerLEDMethod;
static jmethodID BridgeClSetAdaptiveTriggersMethod;
static jmethodID BridgeClControllerHapticsMethod;
static jmethodID BridgeNativeLogMethod;
static jbyteArray DecodedFrameBuffer;
static jshortArray DecodedAudioBuffer;

void DetachThread(void* context) {
    (*JVM)->DetachCurrentThread(JVM);
}

void JniEnvKeyInit(void) {
    // Create a TLS slot for the JNIEnv. We aren't in
    // a pthread during init, so we must wait until we
    // are to initialize this.
    pthread_key_create(&JniEnvKey, DetachThread);
}

// The JNIEnv of this thread, attaching it on its first call, or NULL when it cannot be attached.
JNIEnv* GetThreadEnv(void) {
    JNIEnv* env;

    if (JVM == NULL) {
        return NULL;
    }

    // The VM knows whether this thread is attached. A JNIEnv remembered in TLS would be stale after
    // the thread was detached (see UpcallThrew), so it is never read back from there.
    if ((*JVM)->GetEnv(JVM, (void**)&env, JNI_VERSION_1_4) == JNI_OK) {
        return env;
    }

    // Create the TLS slot now that we're safely in a pthread
    pthread_once(&JniEnvKeyInitOnce, JniEnvKeyInit);

    // This is the thread's first JNI call, so attach now
    env = NULL;
    if ((*JVM)->AttachCurrentThread(JVM, &env, NULL) != JNI_OK || env == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, "moonlight-core", "Cannot attach a native thread to the Java VM");
        return NULL;
    }

    // Write our JNIEnv to TLS, so we detach before dying
    pthread_setspecific(JniEnvKey, env);

    return env;
}

// The JNIEnv for an upcall into MoonBridge, or NULL when this thread cannot make one: it could not be
// attached, or an earlier upcall on this Java thread threw and that exception is still on its way to the
// Java caller (no JNI call that can run Java code is allowed until then).
static JNIEnv* BeginUpcall(void) {
    JNIEnv* env = GetThreadEnv();
    if (env != NULL && (*env)->ExceptionCheck(env)) {
        return NULL;
    }
    return env;
}

// Must follow every upcall. True when the upcall threw; the caller then returns without another JNI call,
// because [env] may be gone.
//
// On a thread that this file attached (moonlight-common-c's own threads) no Java frame can receive the
// exception, so the thread is detached: the VM then hands the exception to the uncaught exception handler
// (CrashReporter), which ends the process with the Java stack trace. MediaCodecDecoderRenderer throws from
// bridgeDrSubmitDecodeUnit to report a decoder failure exactly this way. The TLS slot is cleared first, so
// the thread is not detached a second time when it exits.
//
// On a Java thread (start and stop of the connection) the exception stays pending and is thrown when the
// native method returns; BeginUpcall refuses further upcalls until then.
static bool UpcallThrew(JNIEnv* env) {
    if (!(*env)->ExceptionCheck(env)) {
        return false;
    }

    pthread_once(&JniEnvKeyInitOnce, JniEnvKeyInit);
    if (pthread_getspecific(JniEnvKey) == env) {
        pthread_setspecific(JniEnvKey, NULL);
        (*JVM)->DetachCurrentThread(JVM);
    }
    return true;
}

// An upcall to a MoonBridge method that returns nothing
static void VoidUpcall(jmethodID method, ...) {
    JNIEnv* env = BeginUpcall();
    if (env == NULL) {
        return;
    }

    va_list args;
    va_start(args, method);
    (*env)->CallStaticVoidMethodV(env, GlobalBridgeClass, method, args);
    va_end(args);
    UpcallThrew(env);
}

// Turns the local reference of a new array into a global one and drops the local one, which a thread
// attached from native code would otherwise keep until it exits. NULL when the array or the global
// reference could not be created; no exception is left pending.
static jobject ToGlobalArray(JNIEnv* env, jobject local) {
    jobject global = NULL;
    if (local != NULL) {
        global = (*env)->NewGlobalRef(env, local);
        (*env)->DeleteLocalRef(env, local);
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    return global;
}

// The characters of [string]; NULL for no string, when they cannot be read (OutOfMemoryError is then
// pending) or when an earlier call already failed that way
static const char* GetUtfChars(JNIEnv* env, jstring string) {
    if (string == NULL || (*env)->ExceptionCheck(env)) {
        return NULL;
    }
    return (*env)->GetStringUTFChars(env, string, NULL);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_init(JNIEnv *env, jclass clazz) {
    static const struct {
        jmethodID* id;
        const char* name;
        const char* signature;
    } methods[] = {
            {&BridgeDrSetupMethod, "bridgeDrSetup", "(IIII)I"},
            {&BridgeDrStartMethod, "bridgeDrStart", "()V"},
            {&BridgeDrStopMethod, "bridgeDrStop", "()V"},
            {&BridgeDrCleanupMethod, "bridgeDrCleanup", "()V"},
            {&BridgeDrSubmitDecodeUnitMethod, "bridgeDrSubmitDecodeUnit", "([BIIIICJJ)I"},
            {&BridgeArInitMethod, "bridgeArInit", "(III)I"},
            {&BridgeArStartMethod, "bridgeArStart", "()V"},
            {&BridgeArStopMethod, "bridgeArStop", "()V"},
            {&BridgeArCleanupMethod, "bridgeArCleanup", "()V"},
            {&BridgeArPlaySampleMethod, "bridgeArPlaySample", "([S)V"},
            {&BridgeClStageStartingMethod, "bridgeClStageStarting", "(I)V"},
            {&BridgeClStageCompleteMethod, "bridgeClStageComplete", "(I)V"},
            {&BridgeClStageFailedMethod, "bridgeClStageFailed", "(II)V"},
            {&BridgeClConnectionStartedMethod, "bridgeClConnectionStarted", "()V"},
            {&BridgeClConnectionTerminatedMethod, "bridgeClConnectionTerminated", "(I)V"},
            {&BridgeClRumbleMethod, "bridgeClRumble", "(SSS)V"},
            {&BridgeClConnectionStatusUpdateMethod, "bridgeClConnectionStatusUpdate", "(I)V"},
            {&BridgeClSetHdrModeMethod, "bridgeClSetHdrMode", "(Z[B)V"},
            {&BridgeClRumbleTriggersMethod, "bridgeClRumbleTriggers", "(SSS)V"},
            {&BridgeClSetMotionEventStateMethod, "bridgeClSetMotionEventState", "(SBS)V"},
            {&BridgeClSetControllerLEDMethod, "bridgeClSetControllerLED", "(SBBB)V"},
            {&BridgeClSetAdaptiveTriggersMethod, "bridgeClSetAdaptiveTriggers", "(SBBB[B[B)V"},
            {&BridgeClControllerHapticsMethod, "bridgeClControllerHaptics", "(S[BI)V"},
            {&BridgeNativeLogMethod, "bridgeNativeLog", "(ILjava/lang/String;[B)V"},
    };

    // A failure below leaves its exception (NoSuchMethodError, OutOfMemoryError) pending: MoonBridge's
    // static initialiser then fails with it, which names the cause, instead of a crash at the first callback
    if ((*env)->GetJavaVM(env, &JVM) != JNI_OK) {
        JVM = NULL;
        return;
    }
    GlobalBridgeClass = (*env)->NewGlobalRef(env, clazz);
    if (GlobalBridgeClass == NULL) {
        return;
    }
    for (size_t i = 0; i < sizeof(methods) / sizeof(methods[0]); i++) {
        *methods[i].id = (*env)->GetStaticMethodID(env, clazz, methods[i].name, methods[i].signature);
        if (*methods[i].id == NULL) {
            return;
        }
    }
    LatencyThreadsInit(env);
}

// Delos: PyroWave frames go straight to the native PyroWave renderer (pyrowave-renderer), never through
// Java; the Java side only creates and destroys it (PyroWaveRenderer)
static int ActiveVideoFormat;
#ifdef DELOS_PYROWAVE
int PyroWave_SubmitDecodeUnit(PDECODE_UNIT decodeUnit);
#endif

int BridgeDrSetup(int videoFormat, int width, int height, int redrawRate, void* context, int drFlags) {
    JNIEnv* env = BeginUpcall();
    int err;

    if (env == NULL) {
        return -1;
    }

    ActiveVideoFormat = videoFormat;

    err = (*env)->CallStaticIntMethod(env, GlobalBridgeClass, BridgeDrSetupMethod, videoFormat, width, height, redrawRate);
    if (UpcallThrew(env)) {
        // This is called on a Java thread, so it's safe to return
        return -1;
    }
    else if (err != 0) {
        return err;
    }

    // Use a 32K frame buffer that will increase if needed
    DecodedFrameBuffer = ToGlobalArray(env, (*env)->NewByteArray(env, 32768));
    if (DecodedFrameBuffer == NULL) {
        // moonlight-common-c does not clean up a renderer whose setup failed, and the Java side is set up
        (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeDrCleanupMethod);
        UpcallThrew(env);
        return -1;
    }

    return 0;
}

void BridgeDrStart(void) {
    VoidUpcall(BridgeDrStartMethod);
}

void BridgeDrStop(void) {
    VoidUpcall(BridgeDrStopMethod);
}

void BridgeDrCleanup(void) {
    JNIEnv* env = GetThreadEnv();

    if (env != NULL && DecodedFrameBuffer != NULL) {
        (*env)->DeleteGlobalRef(env, DecodedFrameBuffer);
        DecodedFrameBuffer = NULL;
    }

    VoidUpcall(BridgeDrCleanupMethod);
}

// [envLost] is set when an upcall threw: [env] must not be used any more
static int SubmitDecodeUnit(JNIEnv* env, PDECODE_UNIT decodeUnit, bool* envLost) {
    int ret;

#ifdef DELOS_PYROWAVE
    if (ActiveVideoFormat & VIDEO_FORMAT_MASK_PYROWAVE) {
        return PyroWave_SubmitDecodeUnit(decodeUnit);
    }
#endif

    // Increase the size of our frame data buffer if our frame won't fit. It at least doubles, so a stream
    // whose frames keep growing does not allocate on every one of them.
    jsize capacity = (*env)->GetArrayLength(env, DecodedFrameBuffer);
    if (capacity < decodeUnit->fullLength) {
        jsize wanted = decodeUnit->fullLength;
        if (capacity <= INT32_MAX / 2 && capacity * 2 > wanted) {
            wanted = capacity * 2;
        }
        jbyteArray grown = ToGlobalArray(env, (*env)->NewByteArray(env, wanted));
        if (grown == NULL) {
            // Out of memory: this frame is lost, so the next one has to be a keyframe
            return DR_NEED_IDR;
        }
        (*env)->DeleteGlobalRef(env, DecodedFrameBuffer);
        DecodedFrameBuffer = grown;
    }

    PLENTRY currentEntry;
    int offset;

    currentEntry = decodeUnit->bufferList;
    offset = 0;
    while (currentEntry != NULL) {
        // Submit parameter set NALUs separately from picture data
        if (currentEntry->bufferType != BUFFER_TYPE_PICDATA) {
            // Use the beginning of the buffer each time since this is a separate
            // invocation of the decoder each time.
            (*env)->SetByteArrayRegion(env, DecodedFrameBuffer, 0, currentEntry->length, (jbyte*)currentEntry->data);

            ret = (*env)->CallStaticIntMethod(env, GlobalBridgeClass, BridgeDrSubmitDecodeUnitMethod,
                                              DecodedFrameBuffer, currentEntry->length, currentEntry->bufferType,
                                              decodeUnit->frameNumber, decodeUnit->frameType, (jchar)decodeUnit->frameHostProcessingLatency,
                                              (jlong)decodeUnit->receiveTimeUs, (jlong)decodeUnit->enqueueTimeUs);
            if (UpcallThrew(env)) {
                // We will crash here
                *envLost = true;
                return DR_OK;
            }
            else if (ret != DR_OK) {
                return ret;
            }
        }
        else {
            (*env)->SetByteArrayRegion(env, DecodedFrameBuffer, offset, currentEntry->length, (jbyte*)currentEntry->data);
            offset += currentEntry->length;
        }

        currentEntry = currentEntry->next;
    }

    ret = (*env)->CallStaticIntMethod(env, GlobalBridgeClass, BridgeDrSubmitDecodeUnitMethod,
                                       DecodedFrameBuffer, offset, BUFFER_TYPE_PICDATA,
                                       decodeUnit->frameNumber, decodeUnit->frameType, (jchar)decodeUnit->frameHostProcessingLatency,
                                       (jlong)decodeUnit->receiveTimeUs, (jlong)decodeUnit->enqueueTimeUs);
    if (UpcallThrew(env)) {
        // We will crash here
        *envLost = true;
        return DR_OK;
    }
    else {
        return ret;
    }
}

int BridgeDrSubmitDecodeUnit(PDECODE_UNIT decodeUnit) {
    JNIEnv* env = BeginUpcall();
    bool envLost = false;

    if (env == NULL) {
        return DR_NEED_IDR;
    }

    // Delos: the first call raises this thread's priority and registers it for core placement and the
    // performance hint session; the time the frame takes goes to the session
    uint64_t beginNs = LatencyFrameBegin(env);
    int ret = SubmitDecodeUnit(env, decodeUnit, &envLost);
    if (!envLost) {
        LatencyFrameEnd(env, beginNs);
    }
    return ret;
}

int BridgeArInit(int audioConfiguration, POPUS_MULTISTREAM_CONFIGURATION opusConfig, void* context, int flags) {
    JNIEnv* env = BeginUpcall();
    int err;

    if (env == NULL) {
        return -1;
    }

    err = (*env)->CallStaticIntMethod(env, GlobalBridgeClass, BridgeArInitMethod, audioConfiguration, opusConfig->sampleRate, opusConfig->samplesPerFrame);
    if (UpcallThrew(env)) {
        // This is called on a Java thread, so it's safe to return
        return -1;
    }
    if (err == 0) {
        memcpy(&OpusConfig, opusConfig, sizeof(*opusConfig));
        Decoder = opus_multistream_decoder_create(opusConfig->sampleRate,
                                                  opusConfig->channelCount,
                                                  opusConfig->streams,
                                                  opusConfig->coupledStreams,
                                                  opusConfig->mapping,
                                                  &err);

        // We know ahead of time what the buffer size will be for decoded audio, so pre-allocate it
        if (Decoder != NULL) {
            DecodedAudioBuffer = ToGlobalArray(env, (*env)->NewShortArray(env, opusConfig->channelCount * opusConfig->samplesPerFrame));
        }
        if (Decoder == NULL || DecodedAudioBuffer == NULL) {
            opus_multistream_decoder_destroy(Decoder);
            Decoder = NULL;
            (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArCleanupMethod);
            UpcallThrew(env);
            return -1;
        }
    }

    return err;
}

void BridgeArStart(void) {
    VoidUpcall(BridgeArStartMethod);
}

void BridgeArStop(void) {
    VoidUpcall(BridgeArStopMethod);
}

void BridgeArCleanup() {
    JNIEnv* env = GetThreadEnv();

    opus_multistream_decoder_destroy(Decoder);
    Decoder = NULL;

    if (env != NULL && DecodedAudioBuffer != NULL) {
        (*env)->DeleteGlobalRef(env, DecodedAudioBuffer);
        DecodedAudioBuffer = NULL;
    }

    VoidUpcall(BridgeArCleanupMethod);
}

void BridgeArDecodeAndPlaySample(char* sampleData, int sampleLength) {
    JNIEnv* env = BeginUpcall();

    if (env == NULL) {
        return;
    }

    jshort* decodedData = (*env)->GetPrimitiveArrayCritical(env, DecodedAudioBuffer, NULL);
    if (decodedData == NULL) {
        // Out of memory: this packet is not played
        (*env)->ExceptionClear(env);
        return;
    }

    int decodeLen = opus_multistream_decode(Decoder,
                                            (const unsigned char*)sampleData,
                                            sampleLength,
                                            decodedData,
                                            OpusConfig.samplesPerFrame,
                                            0);
    if (decodeLen > 0) {
        // We must release the array elements before making further JNI calls
        (*env)->ReleasePrimitiveArrayCritical(env, DecodedAudioBuffer, decodedData, 0);

        (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArPlaySampleMethod, DecodedAudioBuffer);
        UpcallThrew(env);
    }
    else {
        // We can abort here to avoid the copy back since no data was modified
        (*env)->ReleasePrimitiveArrayCritical(env, DecodedAudioBuffer, decodedData, JNI_ABORT);
    }
}

void BridgeClStageStarting(int stage) {
    VoidUpcall(BridgeClStageStartingMethod, stage);
}

void BridgeClStageComplete(int stage) {
    VoidUpcall(BridgeClStageCompleteMethod, stage);
}

void BridgeClStageFailed(int stage, int errorCode) {
    VoidUpcall(BridgeClStageFailedMethod, stage, errorCode);
}

void BridgeClConnectionStarted(void) {
    JNIEnv* env = BeginUpcall();

    if (env == NULL) {
        return;
    }

    // Delos: the connection's other threads exist now
    LatencyTuneConnectionThreads(env);

    VoidUpcall(BridgeClConnectionStartedMethod);
}

void BridgeClConnectionTerminated(int errorCode) {
    VoidUpcall(BridgeClConnectionTerminatedMethod, errorCode);
}

void BridgeClRumble(unsigned short controllerNumber, unsigned short lowFreqMotor, unsigned short highFreqMotor) {
    // The seemingly redundant short casts are required in order to convert the unsigned short to a signed short.
    // If we leave it as an unsigned short, CheckJNI will fail when the value exceeds 32767. The cast itself is
    // fine because the Java code treats the value as unsigned even though it's stored in a signed type.
    VoidUpcall(BridgeClRumbleMethod, controllerNumber, (short)lowFreqMotor, (short)highFreqMotor);
}

void BridgeClConnectionStatusUpdate(int connectionStatus) {
    VoidUpcall(BridgeClConnectionStatusUpdateMethod, connectionStatus);
}

void BridgeClSetHdrMode(bool enabled) {
    JNIEnv* env = BeginUpcall();

    if (env == NULL) {
        return;
    }

    jbyteArray hdrMetadataByteArray = NULL;
    SS_HDR_METADATA hdrMetadata;

    // Check if HDR metadata was provided
    if (enabled && LiGetHdrMetadata(&hdrMetadata)) {
        hdrMetadataByteArray = (*env)->NewByteArray(env, sizeof(SS_HDR_METADATA));
        if (hdrMetadataByteArray != NULL) {
            (*env)->SetByteArrayRegion(env, hdrMetadataByteArray, 0, sizeof(SS_HDR_METADATA), (jbyte*)&hdrMetadata);
        }
        else {
            // Out of memory: the mode still changes, without the metadata
            (*env)->ExceptionClear(env);
        }
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClSetHdrModeMethod, enabled, hdrMetadataByteArray);
    if (UpcallThrew(env)) {
        return;
    }

    // This runs on a thread attached from native code, where a local reference lives until the thread exits
    if (hdrMetadataByteArray != NULL) {
        (*env)->DeleteLocalRef(env, hdrMetadataByteArray);
    }
}

void BridgeClRumbleTriggers(unsigned short controllerNumber, unsigned short leftTrigger, unsigned short rightTrigger) {
    // The seemingly redundant short casts are required in order to convert the unsigned short to a signed short.
    // If we leave it as an unsigned short, CheckJNI will fail when the value exceeds 32767. The cast itself is
    // fine because the Java code treats the value as unsigned even though it's stored in a signed type.
    VoidUpcall(BridgeClRumbleTriggersMethod, controllerNumber, (short)leftTrigger, (short)rightTrigger);
}

void BridgeClSetMotionEventState(uint16_t controllerNumber, uint8_t motionType, uint16_t reportRateHz) {
    VoidUpcall(BridgeClSetMotionEventStateMethod, controllerNumber, motionType, reportRateHz);
}

void BridgeClSetControllerLED(uint16_t controllerNumber, uint8_t r, uint8_t g, uint8_t b) {
    // These jbyte casts are necessary to satisfy CheckJNI
    VoidUpcall(BridgeClSetControllerLEDMethod, controllerNumber, (jbyte)r, (jbyte)g, (jbyte)b);
}

// A DualSense's trigger effects from the host (Sunshine 0x5503): which triggers change, each mode and its 10 parameter bytes
void BridgeClSetAdaptiveTriggers(uint16_t controllerNumber, uint8_t eventFlags, uint8_t typeLeft, uint8_t typeRight, uint8_t *left, uint8_t *right) {
    JNIEnv* env = BeginUpcall();
    if (env == NULL) {
        return;
    }

    jbyteArray leftArray = (*env)->NewByteArray(env, DS_EFFECT_PAYLOAD_SIZE);
    jbyteArray rightArray = leftArray != NULL ? (*env)->NewByteArray(env, DS_EFFECT_PAYLOAD_SIZE) : NULL;
    if (leftArray == NULL || rightArray == NULL) {
        // Out of memory: the effect is dropped
        (*env)->ExceptionClear(env);
        if (leftArray != NULL) {
            (*env)->DeleteLocalRef(env, leftArray);
        }
        return;
    }
    (*env)->SetByteArrayRegion(env, leftArray, 0, DS_EFFECT_PAYLOAD_SIZE, (jbyte*)left);
    (*env)->SetByteArrayRegion(env, rightArray, 0, DS_EFFECT_PAYLOAD_SIZE, (jbyte*)right);

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClSetAdaptiveTriggersMethod, (jshort)controllerNumber,
                                 (jbyte)eventFlags, (jbyte)typeLeft, (jbyte)typeRight, leftArray, rightArray);
    if (UpcallThrew(env)) {
        return;
    }

    // This runs on a thread attached from native code, where a local reference lives until the thread exits
    (*env)->DeleteLocalRef(env, leftArray);
    (*env)->DeleteLocalRef(env, rightArray);
}

// Vibepollo waveform haptics (0x5601): a DualSense's actuator PCM, 48 kHz S16LE stereo, every 5 ms. Called on the control
// receive thread only, so one buffer serves every packet without an allocation; Java plays it before returning.
static jbyteArray HapticsPcmArray;

void BridgeClControllerHaptics(uint16_t controllerNumber, uint32_t sequence, const uint8_t* pcm, uint16_t frames) {
    (void)sequence;
    JNIEnv* env = BeginUpcall();
    if (env == NULL) {
        return;
    }

    if (HapticsPcmArray == NULL) {
        jbyteArray local = (*env)->NewByteArray(env, ML_HAPTICS_MAX_FRAMES * ML_HAPTICS_FRAME_BYTES);
        if (local == NULL) {
            (*env)->ExceptionClear(env);
            return;
        }
        HapticsPcmArray = (*env)->NewGlobalRef(env, local);
        (*env)->DeleteLocalRef(env, local);
        if (HapticsPcmArray == NULL) {
            (*env)->ExceptionClear(env);
            return;
        }
    }
    (*env)->SetByteArrayRegion(env, HapticsPcmArray, 0, frames * ML_HAPTICS_FRAME_BYTES, (const jbyte*)pcm);

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClControllerHapticsMethod, (jshort)controllerNumber,
                                 HapticsPcmArray, (jint)frames);
    UpcallThrew(env);
}

// At most this many lines a second reach the diagnostic log; a flood stays in logcat only
#define DIAG_LOG_LINES_PER_SECOND 60

static bool DiagLogAllowed(void) {
    static _Atomic long window;
    static _Atomic int count;
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    if (atomic_exchange(&window, (long)now.tv_sec) != (long)now.tv_sec) {
        atomic_store(&count, 0);
    }
    return atomic_fetch_add(&count, 1) < DIAG_LOG_LINES_PER_SECOND;
}

void DelosDiagLog(int priority, const char* tag, const char* message) {
    if (JVM == NULL || BridgeNativeLogMethod == NULL || message == NULL || !DiagLogAllowed()) {
        return;
    }

    // Not while an exception of the caller is pending: the calls below are not allowed then, and the
    // ExceptionClear at the end must only ever clear an exception of this function
    JNIEnv* env = BeginUpcall();
    if (env == NULL) {
        return;
    }

    // The text may hold bytes that are not valid (modified) UTF-8, which NewStringUTF must never see: pass the bytes
    // and let Java decode them. The tag is one of our own constants.
    jsize length = (jsize)strlen(message);
    jbyteArray bytes = (*env)->NewByteArray(env, length);
    jstring jtag = (*env)->NewStringUTF(env, tag != NULL ? tag : "native");
    if (bytes != NULL && jtag != NULL) {
        (*env)->SetByteArrayRegion(env, bytes, 0, length, (const jbyte*)message);
        (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeNativeLogMethod, priority, jtag, bytes);
    }
    if ((*env)->ExceptionCheck(env)) {
        // Diagnostics must never become a problem of their own
        (*env)->ExceptionClear(env);
    }
    if (bytes != NULL) (*env)->DeleteLocalRef(env, bytes);
    if (jtag != NULL) (*env)->DeleteLocalRef(env, jtag);
}

void BridgeClLogMessage(const char* format, ...) {
    va_list va;
    va_start(va, format);
    char message[1024];
    vsnprintf(message, sizeof(message), format, va);
    va_end(va);
    __android_log_write(ANDROID_LOG_INFO, "moonlight-common-c", message);
    DelosDiagLog(ANDROID_LOG_INFO, "moonlight-common-c", message);
}

static DECODER_RENDERER_CALLBACKS BridgeVideoRendererCallbacks = {
        .setup = BridgeDrSetup,
        .start = BridgeDrStart,
        .stop = BridgeDrStop,
        .cleanup = BridgeDrCleanup,
        .submitDecodeUnit = BridgeDrSubmitDecodeUnit,
};

static AUDIO_RENDERER_CALLBACKS BridgeAudioRendererCallbacks = {
        .init = BridgeArInit,
        .start = BridgeArStart,
        .stop = BridgeArStop,
        .cleanup = BridgeArCleanup,
        .decodeAndPlaySample = BridgeArDecodeAndPlaySample,
        .capabilities = CAPABILITY_SUPPORTS_ARBITRARY_AUDIO_DURATION
};

static CONNECTION_LISTENER_CALLBACKS BridgeConnListenerCallbacks = {
        .stageStarting = BridgeClStageStarting,
        .stageComplete = BridgeClStageComplete,
        .stageFailed = BridgeClStageFailed,
        .connectionStarted = BridgeClConnectionStarted,
        .connectionTerminated = BridgeClConnectionTerminated,
        .logMessage = BridgeClLogMessage,
        .rumble = BridgeClRumble,
        .connectionStatusUpdate = BridgeClConnectionStatusUpdate,
        .setHdrMode = BridgeClSetHdrMode,
        .rumbleTriggers = BridgeClRumbleTriggers,
        .setMotionEventState = BridgeClSetMotionEventState,
        .setControllerLED = BridgeClSetControllerLED,
        .setAdaptiveTriggers = BridgeClSetAdaptiveTriggers,
        .controllerHaptics = BridgeClControllerHaptics,
};

static bool
hasFastAes() {
    if (android_getCpuCount() <= 2) {
        return false;
    }

    switch (android_getCpuFamily()) {
        case ANDROID_CPU_FAMILY_ARM:
            return !!(android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_AES);
        case ANDROID_CPU_FAMILY_ARM64:
            return !!(android_getCpuFeatures() & ANDROID_CPU_ARM64_FEATURE_AES);
        case ANDROID_CPU_FAMILY_X86:
        case ANDROID_CPU_FAMILY_X86_64:
            return !!(android_getCpuFeatures() & ANDROID_CPU_X86_FEATURE_AES_NI);
        case ANDROID_CPU_FAMILY_MIPS:
        case ANDROID_CPU_FAMILY_MIPS64:
            return false;
        default:
            // Assume new architectures will all have crypto acceleration (RISC-V will)
            return true;
    }
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_startConnection(JNIEnv *env, jclass clazz,
                                                           jstring address, jstring appVersion, jstring gfeVersion,
                                                           jstring rtspSessionUrl, jint serverCodecModeSupport,
                                                           jint width, jint height, jint fps,
                                                           jint bitrate, jint packetSize, jint streamingRemotely,
                                                           jint audioConfiguration, jint supportedVideoFormats,
                                                           jint clientRefreshRateX100,
                                                           jbyteArray riAesKey, jbyteArray riAesIv,
                                                           jint videoCapabilities,
                                                           jint colorSpace, jint colorRange) {
    // The copies below read exactly this many bytes of the two arrays
    _Static_assert(sizeof(((STREAM_CONFIGURATION*)0)->remoteInputAesKey) == 16, "remote input AES key size");
    _Static_assert(sizeof(((STREAM_CONFIGURATION*)0)->remoteInputAesIv) == 16, "remote input AES IV size");

    if (address == NULL || appVersion == NULL || riAesKey == NULL || riAesIv == NULL ||
            (*env)->GetArrayLength(env, riAesKey) < (jsize)sizeof(((STREAM_CONFIGURATION*)0)->remoteInputAesKey) ||
            (*env)->GetArrayLength(env, riAesIv) < (jsize)sizeof(((STREAM_CONFIGURATION*)0)->remoteInputAesIv)) {
        return -1;
    }

    SERVER_INFORMATION serverInfo = {
            .address = GetUtfChars(env, address),
            .serverInfoAppVersion = GetUtfChars(env, appVersion),
            .serverInfoGfeVersion = GetUtfChars(env, gfeVersion),
            .rtspSessionUrl = GetUtfChars(env, rtspSessionUrl),
            .serverCodecModeSupport = serverCodecModeSupport,
    };
    STREAM_CONFIGURATION streamConfig = {
            .width = width,
            .height = height,
            .fps = fps,
            .bitrate = bitrate,
            .packetSize = packetSize,
            .streamingRemotely = streamingRemotely,
            .audioConfiguration = audioConfiguration,
            .supportedVideoFormats = supportedVideoFormats,
            .clientRefreshRateX100 = clientRefreshRateX100,
            .encryptionFlags = ENCFLG_AUDIO,
            .colorSpace = colorSpace,
            .colorRange = colorRange
    };
    int ret = -1;

    // A string that could not be read leaves OutOfMemoryError pending, which Java gets on return
    if (serverInfo.address != NULL && serverInfo.serverInfoAppVersion != NULL &&
            (gfeVersion == NULL || serverInfo.serverInfoGfeVersion != NULL) &&
            (rtspSessionUrl == NULL || serverInfo.rtspSessionUrl != NULL)) {
        (*env)->GetByteArrayRegion(env, riAesKey, 0, sizeof(streamConfig.remoteInputAesKey), (jbyte*)streamConfig.remoteInputAesKey);
        (*env)->GetByteArrayRegion(env, riAesIv, 0, sizeof(streamConfig.remoteInputAesIv), (jbyte*)streamConfig.remoteInputAesIv);

        BridgeVideoRendererCallbacks.capabilities = videoCapabilities;

        // Enable all encryption features if the platform has fast AES support
        if (hasFastAes()) {
            streamConfig.encryptionFlags = ENCFLG_ALL;
        }

        ret = LiStartConnection(&serverInfo,
                                &streamConfig,
                                &BridgeConnListenerCallbacks,
                                &BridgeVideoRendererCallbacks,
                                &BridgeAudioRendererCallbacks,
                                NULL, 0,
                                NULL, 0);
    }

    if (serverInfo.address != NULL) {
        (*env)->ReleaseStringUTFChars(env, address, serverInfo.address);
    }
    if (serverInfo.serverInfoAppVersion != NULL) {
        (*env)->ReleaseStringUTFChars(env, appVersion, serverInfo.serverInfoAppVersion);
    }
    if (serverInfo.serverInfoGfeVersion != NULL) {
        (*env)->ReleaseStringUTFChars(env, gfeVersion, serverInfo.serverInfoGfeVersion);
    }
    if (serverInfo.rtspSessionUrl != NULL) {
        (*env)->ReleaseStringUTFChars(env, rtspSessionUrl, serverInfo.rtspSessionUrl);
    }

    return ret;
}