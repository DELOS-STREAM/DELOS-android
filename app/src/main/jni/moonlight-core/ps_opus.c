// Delos: an Opus decoder for the PlayStation Remote Play audio (com.limelight.ps.PsOpus), on the libopus this library links anyway.
// moonlight-common-c decodes its own audio in callbacks.c; a PlayStation stream has its packets in Kotlin and needs a decoder there,
// with Opus packet-loss concealment for a missing packet.

#include <jni.h>
#include <stdlib.h>
#include <opus.h>

JNIEXPORT jlong JNICALL
Java_com_limelight_ps_PsOpus_create(JNIEnv *env, jclass clazz, jint sampleRate, jint channels) {
    int err = OPUS_OK;
    OpusDecoder *decoder = opus_decoder_create(sampleRate, channels, &err);
    return err == OPUS_OK ? (jlong) (intptr_t) decoder : 0;
}

// Decodes one packet into pcm (interleaved 16-bit, frameSize samples per channel); a null packet conceals a lost one.
// Returns the samples per channel written, or a negative Opus error.
JNIEXPORT jint JNICALL
Java_com_limelight_ps_PsOpus_decode(JNIEnv *env, jclass clazz, jlong handle, jbyteArray packet, jint length, jshortArray pcm, jint frameSize) {
    OpusDecoder *decoder = (OpusDecoder *) (intptr_t) handle;
    jbyte *data = packet != NULL ? (*env)->GetPrimitiveArrayCritical(env, packet, NULL) : NULL;
    jshort *out = (*env)->GetPrimitiveArrayCritical(env, pcm, NULL);
    int samples = opus_decode(decoder, (const unsigned char *) data, packet != NULL ? length : 0, out, frameSize, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, pcm, out, 0);
    if (data != NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, packet, data, JNI_ABORT);
    }
    return samples;
}

JNIEXPORT void JNICALL
Java_com_limelight_ps_PsOpus_destroy(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        opus_decoder_destroy((OpusDecoder *) (intptr_t) handle);
    }
}

// The microphone encoder of a PlayStation stream (spec 8.5): 48 kHz mono, restricted low delay, constant 32 kbit/s, so a 480-sample
// frame is a 40-byte packet.
JNIEXPORT jlong JNICALL
Java_com_limelight_ps_PsOpusEncoder_create(JNIEnv *env, jclass clazz, jint sampleRate, jint bitrate) {
    int err = OPUS_OK;
    OpusEncoder *encoder = opus_encoder_create(sampleRate, 1, OPUS_APPLICATION_RESTRICTED_LOWDELAY, &err);
    if (err != OPUS_OK) {
        return 0;
    }
    opus_encoder_ctl(encoder, OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(encoder, OPUS_SET_VBR(0));
    return (jlong) (intptr_t) encoder;
}

// Encodes frameSize mono samples into out; returns the packet length or a negative Opus error.
JNIEXPORT jint JNICALL
Java_com_limelight_ps_PsOpusEncoder_encode(JNIEnv *env, jclass clazz, jlong handle, jshortArray pcm, jint frameSize, jbyteArray out) {
    OpusEncoder *encoder = (OpusEncoder *) (intptr_t) handle;
    jsize capacity = (*env)->GetArrayLength(env, out);
    jshort *in = (*env)->GetPrimitiveArrayCritical(env, pcm, NULL);
    jbyte *data = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    int length = opus_encode(encoder, in, frameSize, (unsigned char *) data, capacity);
    (*env)->ReleasePrimitiveArrayCritical(env, out, data, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, pcm, in, JNI_ABORT);
    return length;
}

JNIEXPORT void JNICALL
Java_com_limelight_ps_PsOpusEncoder_destroy(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        opus_encoder_destroy((OpusEncoder *) (intptr_t) handle);
    }
}
