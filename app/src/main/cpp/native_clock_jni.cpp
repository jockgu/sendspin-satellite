#include <jni.h>

#include "clock_filter.h"
#include "native_playback_engine.h"

namespace {

using sendspin::ClockFilter;
using sendspin::NativePlaybackEngine;
using RecoveryCause = sendspin::PlaybackRecoveryState::RecoveryCause;

ClockFilter* filter(jlong handle) {
    return reinterpret_cast<ClockFilter*>(handle);
}

RecoveryCause recovery_cause(jint value) {
    switch (value) {
        case 1:
            return RecoveryCause::RouteChange;
        case 2:
            return RecoveryCause::FocusResume;
        default:
            return RecoveryCause::OutputError;
    }
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
    JNIEnv* env, jclass, jstring client_id, jstring player_name) {
    if (client_id == nullptr || player_name == nullptr) {
        return 0;
    }
    const char* client_chars = env->GetStringUTFChars(client_id, nullptr);
    if (client_chars == nullptr) {
        return 0;
    }
    const char* player_chars = env->GetStringUTFChars(player_name, nullptr);
    if (player_chars == nullptr) {
        env->ReleaseStringUTFChars(client_id, client_chars);
        return 0;
    }
    auto* engine = new NativePlaybackEngine(client_chars, player_chars);
    env->ReleaseStringUTFChars(player_name, player_chars);
    env->ReleaseStringUTFChars(client_id, client_chars);
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

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeRequestRecovery(
    JNIEnv*, jclass, jlong handle, jint cause) {
    reinterpret_cast<NativePlaybackEngine*>(handle)->request_recovery(recovery_cause(cause));
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeSuspendForFocus(
    JNIEnv*, jclass, jlong handle) {
    reinterpret_cast<NativePlaybackEngine*>(handle)->suspend_for_focus();
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeResumeFromFocus(
    JNIEnv*, jclass, jlong handle) {
    reinterpret_cast<NativePlaybackEngine*>(handle)->resume_from_focus();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeState(
    JNIEnv*, jclass, jlong handle) {
    return static_cast<jint>(reinterpret_cast<NativePlaybackEngine*>(handle)->state());
}
