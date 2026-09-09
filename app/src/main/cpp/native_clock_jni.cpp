#include <jni.h>

#include "clock_filter.h"
#include "native_playback_engine.h"

namespace {

using sendspin::ClockFilter;
using sendspin::NativePlaybackEngine;

ClockFilter* filter(jlong handle) {
    return reinterpret_cast<ClockFilter*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativeClockEngine_nativeCreate(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new ClockFilter());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativeClockEngine_nativeDestroy(
    JNIEnv*, jclass, jlong handle) {
    delete filter(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativeClockEngine_nativeReset(
    JNIEnv*, jclass, jlong handle) {
    filter(handle)->reset();
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativeClockEngine_nativeUpdate(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong client_transmitted_us,
    jlong server_received_us,
    jlong server_transmitted_us,
    jlong client_received_us) {
    auto* clock = filter(handle);
    clock->update(client_transmitted_us, server_received_us, server_transmitted_us, client_received_us);
    const auto diagnostics = clock->diagnostics();
    const jlong values[] = {
        diagnostics.round_trip_us,
        diagnostics.offset_us,
        diagnostics.samples,
        diagnostics.converged ? 1 : 0,
    };
    auto result = env->NewLongArray(4);
    env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeCreate(
    JNIEnv* env, jclass, jstring client_id) {
    if (client_id == nullptr) {
        return 0;
    }
    const char* chars = env->GetStringUTFChars(client_id, nullptr);
    if (chars == nullptr) {
        return 0;
    }
    auto* engine = new NativePlaybackEngine(chars);
    env->ReleaseStringUTFChars(client_id, chars);
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeDestroy(
    JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<NativePlaybackEngine*>(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeConnect(
    JNIEnv* env, jclass, jlong handle, jstring url) {
    const char* chars = env->GetStringUTFChars(url, nullptr);
    if (chars == nullptr) return JNI_FALSE;
    const bool started = reinterpret_cast<NativePlaybackEngine*>(handle)->connect(chars);
    env->ReleaseStringUTFChars(url, chars);
    return started ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeDisconnect(
    JNIEnv*, jclass, jlong handle) {
    reinterpret_cast<NativePlaybackEngine*>(handle)->disconnect();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeState(
    JNIEnv*, jclass, jlong handle) {
    return static_cast<jint>(reinterpret_cast<NativePlaybackEngine*>(handle)->state());
}
