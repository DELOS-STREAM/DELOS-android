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
