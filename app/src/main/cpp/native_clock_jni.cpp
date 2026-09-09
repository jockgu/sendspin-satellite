#include <jni.h>

#include "clock_filter.h"

namespace {

using sendspin::ClockFilter;

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
