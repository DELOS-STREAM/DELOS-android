// Delos: frame generation on the SGSR GL thread (docs/VIDEO_FRAME_GENERATION_RESEARCH.md, phase 1). The Adreno driver's
// GL_QCOM_frame_extrapolation predicts a frame from the two newest ones; FrameGenerator.java calls it once per refresh
// between two decoded frames. Everything runs on the SGSR thread with its context current.
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <cstring>

namespace {
// void glExtrapolateTex2DQCOM(GLuint src1, GLuint src2, GLuint output, GLfloat scaleFactor)
typedef void (*ExtrapolateTex2D)(GLuint, GLuint, GLuint, GLfloat);
ExtrapolateTex2D extrapolate = nullptr;
}

// Whether the current context offers the driver's frame extrapolation; loads it when it does.
extern "C" JNIEXPORT jboolean JNICALL Java_com_limelight_binding_video_FrameGenerator_nativeInit(JNIEnv *, jclass) {
    const char *extensions = reinterpret_cast<const char *>(glGetString(GL_EXTENSIONS));
    if (!extensions || !strstr(extensions, "GL_QCOM_frame_extrapolation")) {
        __android_log_print(ANDROID_LOG_INFO, "FrameGen", "GL_QCOM_frame_extrapolation: not offered");
        return JNI_FALSE;
    }
    extrapolate = reinterpret_cast<ExtrapolateTex2D>(eglGetProcAddress("glExtrapolateTex2DQCOM"));
    __android_log_print(ANDROID_LOG_INFO, "FrameGen", "GL_QCOM_frame_extrapolation: %s", extrapolate ? "loaded" : "entry point missing");
    return extrapolate ? JNI_TRUE : JNI_FALSE;
}

// output = the frame [scale] source intervals after src2, predicted from src1 (older) and src2 (newer); all three textures
// have the same size and internal format. Returns the GL error after the call (0 when it was accepted).
extern "C" JNIEXPORT jint JNICALL Java_com_limelight_binding_video_FrameGenerator_nativeExtrapolate(
        JNIEnv *, jclass, jint older, jint newer, jint output, jfloat scale) {
    if (!extrapolate) return -1;
    extrapolate(GLuint(older), GLuint(newer), GLuint(output), scale);
    return jint(glGetError());
}
