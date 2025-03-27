#include <jni.h>
#include <string>
#include <android/log.h>
#include "txe.h"
#include <map>
#include <sstream>
#define LOG_TAG "TXE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Example text expansion map
static std::map<std::string, std::string> textExpansions = {
        {"brb", "be right back"},
        {"lol", "laughing out loud"},
        {"omw", "on my way"},
        {"ttyl", "talk to you later"}
};

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_txe_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_txe_MyAccessibilityService_processText(JNIEnv *env, jobject thiz, jstring text) {
    const char *nativeText = env->GetStringUTFChars(text, nullptr);
    if (nativeText == nullptr) {
        LOGE("Failed to get native text");
        return nullptr;
    }

    std::string inputText(nativeText);
    env->ReleaseStringUTFChars(text, nativeText);

    // Log the received text
    LOGI("Processing text: %s", inputText.c_str());

    // Check if the text ends with a space
    bool hasSpace = !inputText.empty() && inputText.back() == ' ';
    if (!hasSpace) {
        return env->NewStringUTF(inputText.c_str());
    }

    // Remove the trailing space for processing
    inputText = inputText.substr(0, inputText.length() - 1);

    // Find the last word
    size_t lastSpace = inputText.find_last_of(' ');
    std::string lastWord = (lastSpace == std::string::npos) ? inputText : inputText.substr(lastSpace + 1);
    std::string prefix = (lastSpace == std::string::npos) ? "" : inputText.substr(0, lastSpace + 1);

    // Check if the last word is in our expansion map
    auto it = textExpansions.find(lastWord);
    if (it != textExpansions.end()) {
        std::string processedText = prefix + it->second + " ";
        LOGI("Text expansion applied: %s -> %s", inputText.c_str(), processedText.c_str());
        return env->NewStringUTF(processedText.c_str());
    }

    // If no expansion found, return original text with space
    return env->NewStringUTF((inputText + " ").c_str());
}