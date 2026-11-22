// Delos: frame generation on the SGSR GL thread (docs/VIDEO_FRAME_GENERATION_RESEARCH.md, phase 1). Two Adreno driver
// extensions: GL_QCOM_frame_extrapolation predicts a frame from the two newest ones; GL_QCOM_motion_estimation gives block
// motion vectors for FrameGenerator's own warp. Everything runs on the SGSR thread with its context current.
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <cstring>

namespace {
// void glExtrapolateTex2DQCOM(GLuint src1, GLuint src2, GLuint output, GLfloat scaleFactor)
typedef void (*ExtrapolateTex2D)(GLuint, GLuint, GLuint, GLfloat);
// void glTexEstimateMotionQCOM(GLuint ref, GLuint target, GLuint output)
typedef void (*TexEstimateMotion)(GLuint, GLuint, GLuint);
ExtrapolateTex2D extrapolate = nullptr;
TexEstimateMotion estimateMotion = nullptr;

constexpr jint kExtrapolation = 1;
constexpr jint kMotionEstimation = 2;
}

// What the current context offers: kExtrapolation and/or kMotionEstimation (their entry points loaded), 0 for neither.
extern "C" JNIEXPORT jint JNICALL Java_com_limelight_binding_video_FrameGenerator_nativeInit(JNIEnv *, jclass) {
    const char *extensions = reinterpret_cast<const char *>(glGetString(GL_EXTENSIONS));
    jint offered = 0;
    if (extensions && strstr(extensions, "GL_QCOM_frame_extrapolation")) {
        extrapolate = reinterpret_cast<ExtrapolateTex2D>(eglGetProcAddress("glExtrapolateTex2DQCOM"));
        if (extrapolate) offered |= kExtrapolation;
    }
    if (extensions && strstr(extensions, "GL_QCOM_motion_estimation")) {
        estimateMotion = reinterpret_cast<TexEstimateMotion>(eglGetProcAddress("glTexEstimateMotionQCOM"));
        if (estimateMotion) offered |= kMotionEstimation;
    }
    __android_log_print(ANDROID_LOG_INFO, "FrameGen", "frame extrapolation %s, motion estimation %s",
            (offered & kExtrapolation) ? "loaded" : "not offered", (offered & kMotionEstimation) ? "loaded" : "not offered");
    return offered;
}

// output = the frame [scale] source intervals after src2, predicted from src1 (older) and src2 (newer); all three textures
// have the same size and internal format. Returns the GL error after the call (0 when it was accepted).
extern "C" JNIEXPORT jint JNICALL Java_com_limelight_binding_video_FrameGenerator_nativeExtrapolate(
        JNIEnv *, jclass, jint older, jint newer, jint output, jfloat scale) {
    if (!extrapolate) return -1;
    extrapolate(GLuint(older), GLuint(newer), GLuint(output), scale);
    return jint(glGetError());
}

// output (GL_RGBA16F, one texel per search block) = the motion from ref to target (both GL_R8, the same size, a multiple of
// the search block). Returns the GL error after the call (0 when it was accepted).
extern "C" JNIEXPORT jint JNICALL Java_com_limelight_binding_video_FrameGenerator_nativeEstimateMotion(
        JNIEnv *, jclass, jint ref, jint target, jint output) {
    if (!estimateMotion) return -1;
    estimateMotion(GLuint(ref), GLuint(target), GLuint(output));
    return jint(glGetError());
}
