#ifndef TXE_H
#define TXE_H

#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "TXE_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL
Java_com_example_txe_MyAccessibilityService_processText(JNIEnv *env, jobject thiz, jstring text);

#ifdef __cplusplus
}
#endif

#endif // TXE_H 