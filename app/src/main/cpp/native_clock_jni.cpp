#include <jni.h>

#include <iterator>
#include <optional>
#include <string>

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

jstring utf8_string(JNIEnv* env, const std::optional<std::string>& value) {
    if (!value.has_value()) return nullptr;

    auto bytes = env->NewByteArray(static_cast<jsize>(value->size()));
    if (bytes == nullptr) return nullptr;
    env->SetByteArrayRegion(
        bytes, 0, static_cast<jsize>(value->size()),
        reinterpret_cast<const jbyte*>(value->data()));

    const auto standard_charsets = env->FindClass("java/nio/charset/StandardCharsets");
    const auto string_class = env->FindClass("java/lang/String");
    if (standard_charsets == nullptr || string_class == nullptr) {
        env->DeleteLocalRef(bytes);
        return nullptr;
    }
    const auto utf8_field = env->GetStaticFieldID(
        standard_charsets, "UTF_8", "Ljava/nio/charset/Charset;");
    const auto constructor = env->GetMethodID(
        string_class, "<init>", "([BLjava/nio/charset/Charset;)V");
    if (utf8_field == nullptr || constructor == nullptr) {
        env->DeleteLocalRef(bytes);
        env->DeleteLocalRef(standard_charsets);
        env->DeleteLocalRef(string_class);
        return nullptr;
    }
    const auto utf8 = env->GetStaticObjectField(standard_charsets, utf8_field);
    const auto result = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, utf8));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(utf8);
    env->DeleteLocalRef(standard_charsets);
    env->DeleteLocalRef(string_class);
    return result;
}

jobject now_playing_snapshot(
    JNIEnv* env, const sendspin::NowPlayingState::Snapshot& snapshot) {
    jobject progress = nullptr;
    if (snapshot.progress.has_value()) {
        const auto progress_class = env->FindClass(
            "com/nanopixel/sendspinsatellite/playback/NowPlayingSnapshot$Progress");
        if (progress_class == nullptr) return nullptr;
        const auto constructor = env->GetMethodID(progress_class, "<init>", "(JJIJ)V");
        if (constructor == nullptr) {
            env->DeleteLocalRef(progress_class);
            return nullptr;
        }
        progress = env->NewObject(
            progress_class,
            constructor,
            static_cast<jlong>(snapshot.progress->reported_position_ms),
            static_cast<jlong>(snapshot.progress->duration_ms),
            static_cast<jint>(snapshot.progress->playback_speed_milli),
            static_cast<jlong>(snapshot.progress->interpolated_position_ms));
        env->DeleteLocalRef(progress_class);
        if (progress == nullptr) return nullptr;
    }

    jobject group = nullptr;
    if (snapshot.group.has_value()) {
        const auto group_class = env->FindClass(
            "com/nanopixel/sendspinsatellite/playback/NowPlayingSnapshot$Group");
        const auto playback_state_class = env->FindClass(
            "com/nanopixel/sendspinsatellite/playback/NowPlayingSnapshot$PlaybackState");
        if (group_class == nullptr || playback_state_class == nullptr) {
            if (progress != nullptr) env->DeleteLocalRef(progress);
            return nullptr;
        }
        const auto constructor = env->GetMethodID(
            group_class,
            "<init>",
            "(Ljava/lang/String;Lcom/nanopixel/sendspinsatellite/playback/"
            "NowPlayingSnapshot$PlaybackState;)V");
        jobject playback_state = nullptr;
        if (snapshot.group->playback_state.has_value()) {
            const char* field_name =
                *snapshot.group->playback_state == sendspin::NowPlayingState::GroupPlaybackState::Playing
                    ? "PLAYING"
                    : "STOPPED";
            const auto field = env->GetStaticFieldID(
                playback_state_class,
                field_name,
                "Lcom/nanopixel/sendspinsatellite/playback/"
                "NowPlayingSnapshot$PlaybackState;");
            if (field == nullptr) {
                env->DeleteLocalRef(group_class);
                env->DeleteLocalRef(playback_state_class);
                if (progress != nullptr) env->DeleteLocalRef(progress);
                return nullptr;
            }
            playback_state = env->GetStaticObjectField(playback_state_class, field);
        }
        const auto group_name = utf8_string(env, snapshot.group->name);
        group = constructor == nullptr
            ? nullptr
            : env->NewObject(group_class, constructor, group_name, playback_state);
        if (group_name != nullptr) env->DeleteLocalRef(group_name);
        if (playback_state != nullptr) env->DeleteLocalRef(playback_state);
        env->DeleteLocalRef(group_class);
        env->DeleteLocalRef(playback_state_class);
        if (group == nullptr) {
            if (progress != nullptr) env->DeleteLocalRef(progress);
            return nullptr;
        }
    }

    const auto snapshot_class = env->FindClass(
        "com/nanopixel/sendspinsatellite/playback/NowPlayingSnapshot");
    if (snapshot_class == nullptr) {
        if (progress != nullptr) env->DeleteLocalRef(progress);
        if (group != nullptr) env->DeleteLocalRef(group);
        return nullptr;
    }
    const auto constructor = env->GetMethodID(
        snapshot_class,
        "<init>",
        "(JJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "Lcom/nanopixel/sendspinsatellite/playback/NowPlayingSnapshot$Progress;"
        "Lcom/nanopixel/sendspinsatellite/playback/NowPlayingSnapshot$Group;)V");
    const auto title = utf8_string(env, snapshot.title);
    const auto artist = utf8_string(env, snapshot.artist);
    const auto album_artist = utf8_string(env, snapshot.album_artist);
    const auto album = utf8_string(env, snapshot.album);
    const auto result = constructor == nullptr
        ? nullptr
        : env->NewObject(
              snapshot_class,
              constructor,
              static_cast<jlong>(snapshot.revision),
              static_cast<jlong>(snapshot.generation),
              title,
              artist,
              album_artist,
              album,
              progress,
              group);
    if (title != nullptr) env->DeleteLocalRef(title);
    if (artist != nullptr) env->DeleteLocalRef(artist);
    if (album_artist != nullptr) env->DeleteLocalRef(album_artist);
    if (album != nullptr) env->DeleteLocalRef(album);
    if (progress != nullptr) env->DeleteLocalRef(progress);
    if (group != nullptr) env->DeleteLocalRef(group);
    env->DeleteLocalRef(snapshot_class);
    return result;
}

jobject artwork_snapshot(JNIEnv* env, const sendspin::ArtworkState::Snapshot& snapshot) {
    const auto snapshot_class = env->FindClass(
        "com/nanopixel/sendspinsatellite/playback/ArtworkSnapshot");
    if (snapshot_class == nullptr) return nullptr;
    const auto constructor = env->GetMethodID(snapshot_class, "<init>", "(JJ[B)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(snapshot_class);
        return nullptr;
    }

    jbyteArray encoded_jpeg = nullptr;
    if (!snapshot.encoded_jpeg.empty()) {
        encoded_jpeg = env->NewByteArray(static_cast<jsize>(snapshot.encoded_jpeg.size()));
        if (encoded_jpeg == nullptr) {
            env->DeleteLocalRef(snapshot_class);
            return nullptr;
        }
        env->SetByteArrayRegion(
            encoded_jpeg,
            0,
            static_cast<jsize>(snapshot.encoded_jpeg.size()),
            reinterpret_cast<const jbyte*>(snapshot.encoded_jpeg.data()));
    }

    const auto result = env->NewObject(
        snapshot_class,
        constructor,
        static_cast<jlong>(snapshot.revision),
        static_cast<jlong>(snapshot.generation),
        encoded_jpeg);
    if (encoded_jpeg != nullptr) env->DeleteLocalRef(encoded_jpeg);
    env->DeleteLocalRef(snapshot_class);
    return result;
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
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeSetNetworkAvailable(
    JNIEnv*, jclass, jlong handle, jboolean available) {
    reinterpret_cast<NativePlaybackEngine*>(handle)->set_network_available(available == JNI_TRUE);
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

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeDiagnostics(
    JNIEnv* env, jclass, jlong handle) {
    const auto snapshot = reinterpret_cast<NativePlaybackEngine*>(handle)->diagnostics();
    const jlong values[] = {
        static_cast<jlong>(snapshot.state),
        static_cast<jlong>(snapshot.generation),
        static_cast<jlong>(snapshot.queued_frames),
        static_cast<jlong>(snapshot.fifo_capacity_frames),
        static_cast<jlong>(snapshot.output_latency_us),
        static_cast<jlong>(snapshot.underruns),
        static_cast<jlong>(snapshot.output_restarts),
        static_cast<jlong>(snapshot.hard_resyncs),
        static_cast<jlong>(snapshot.reconnect_attempts),
        static_cast<jlong>(snapshot.reconnect_completions),
        static_cast<jlong>(snapshot.round_trip_us),
        static_cast<jlong>(snapshot.clock_offset_us),
        static_cast<jlong>(snapshot.clock_drift_ppm),
        static_cast<jlong>(snapshot.clock_error_us),
        static_cast<jlong>(snapshot.clock_samples),
        snapshot.clock_converged ? 1 : 0,
        static_cast<jlong>(snapshot.last_failure),
        snapshot.output_stream_open ? 1 : 0,
        static_cast<jlong>(snapshot.output_stream_state),
        static_cast<jlong>(snapshot.output_sample_rate),
        static_cast<jlong>(snapshot.output_channel_count),
        static_cast<jlong>(snapshot.output_format),
        static_cast<jlong>(snapshot.output_performance_mode),
        static_cast<jlong>(snapshot.output_sharing_mode),
        static_cast<jlong>(snapshot.output_device_id),
        static_cast<jlong>(snapshot.output_session_id),
        static_cast<jlong>(snapshot.output_frames_per_burst),
        static_cast<jlong>(snapshot.output_buffer_size_frames),
        static_cast<jlong>(snapshot.output_buffer_capacity_frames),
        static_cast<jlong>(snapshot.output_xrun_count),
    };
    auto result = env->NewLongArray(static_cast<jsize>(std::size(values)));
    if (result == nullptr) return nullptr;
    env->SetLongArrayRegion(result, 0, static_cast<jsize>(std::size(values)), values);
    return result;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeNowPlayingIfChanged(
    JNIEnv* env, jclass, jlong handle, jlong known_revision) {
    const auto snapshot = reinterpret_cast<NativePlaybackEngine*>(handle)->now_playing_after(
        static_cast<uint64_t>(known_revision));
    if (!snapshot.has_value()) return nullptr;
    return now_playing_snapshot(env, *snapshot);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_nanopixel_sendspinsatellite_protocol_NativePlaybackEngine_nativeArtworkIfChanged(
    JNIEnv* env, jclass, jlong handle, jlong known_revision) {
    const auto snapshot = reinterpret_cast<NativePlaybackEngine*>(handle)->artwork_after(
        static_cast<uint64_t>(known_revision));
    if (!snapshot.has_value()) return nullptr;
    return artwork_snapshot(env, *snapshot);
}
