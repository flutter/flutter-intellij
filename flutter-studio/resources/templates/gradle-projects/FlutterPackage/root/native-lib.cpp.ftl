#include <jni.h>
#include <string>

<#if activityClass?? && packageName??>
extern "C"
JNIEXPORT jstring JNICALL
Java_${packageName?replace('_','_1','i')?replace('.','_','i')}_${activityClass}_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
</#if>
