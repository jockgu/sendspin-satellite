#include "jni_persistence_provider.h"

#include <limits>

namespace sendspin {

JniPersistenceProvider::JniPersistenceProvider(JNIEnv* env, jobject store) {
    if (env == nullptr || store == nullptr || env->GetJavaVM(&vm_) != JNI_OK) {
        failed_.store(true, std::memory_order_release);
        return;
    }
    store_ = env->NewGlobalRef(store);
    const jclass store_class = env->GetObjectClass(store);
    if (store_ != nullptr && store_class != nullptr) {
        load_blob_ = env->GetMethodID(store_class, "loadBlob", "(Ljava/lang/String;)[B");
        save_blob_ = env->GetMethodID(store_class, "saveBlob", "(Ljava/lang/String;[B)Z");
    }
    if (store_class != nullptr) env->DeleteLocalRef(store_class);
    if (store_ == nullptr || load_blob_ == nullptr || save_blob_ == nullptr ||
        env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (store_ != nullptr) env->DeleteGlobalRef(store_);
        store_ = nullptr;
        failed_.store(true, std::memory_order_release);
    }
}

JniPersistenceProvider::~JniPersistenceProvider() {
    if (store_ == nullptr || vm_ == nullptr) return;
    bool attached = false;
    JNIEnv* env = env_for_call(&attached);
    if (env != nullptr) env->DeleteGlobalRef(store_);
    release_env(env, attached);
}

JNIEnv* JniPersistenceProvider::env_for_call(bool* attached) const {
    *attached = false;
    if (vm_ == nullptr) return nullptr;
    JNIEnv* env = nullptr;
    const jint status = vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_OK) return env;
    if (status != JNI_EDETACHED || vm_->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    *attached = true;
    return env;
}

void JniPersistenceProvider::release_env(JNIEnv* env, const bool attached) const {
    if (attached && env != nullptr) vm_->DetachCurrentThread();
}

void JniPersistenceProvider::record_failure(JNIEnv* env) {
    if (env != nullptr && env->ExceptionCheck()) env->ExceptionClear();
    failed_.store(true, std::memory_order_release);
}

std::optional<std::vector<uint8_t>> JniPersistenceProvider::load_blob(const std::string& key) {
    bool attached = false;
    JNIEnv* env = env_for_call(&attached);
    if (env == nullptr || store_ == nullptr || load_blob_ == nullptr) {
        failed_.store(true, std::memory_order_release);
        release_env(env, attached);
        return std::nullopt;
    }

    jstring java_key = env->NewStringUTF(key.c_str());
    auto bytes = java_key == nullptr
        ? nullptr
        : static_cast<jbyteArray>(env->CallObjectMethod(store_, load_blob_, java_key));
    if (java_key != nullptr) env->DeleteLocalRef(java_key);
    if (env->ExceptionCheck()) {
        record_failure(env);
        release_env(env, attached);
        return std::nullopt;
    }
    if (bytes == nullptr) {
        release_env(env, attached);
        return std::nullopt;
    }

    const jsize length = env->GetArrayLength(bytes);
    if (length <= 0) {
        env->DeleteLocalRef(bytes);
        failed_.store(true, std::memory_order_release);
        release_env(env, attached);
        return std::nullopt;
    }
    std::vector<uint8_t> result(static_cast<size_t>(length));
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(result.data()));
    env->DeleteLocalRef(bytes);
    if (env->ExceptionCheck()) {
        record_failure(env);
        release_env(env, attached);
        return std::nullopt;
    }
    release_env(env, attached);
    return result;
}

bool JniPersistenceProvider::save_blob(const std::string& key, const uint8_t* data, size_t len) {
    bool attached = false;
    JNIEnv* env = env_for_call(&attached);
    if (env == nullptr || store_ == nullptr || save_blob_ == nullptr || data == nullptr || len == 0 ||
        len > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        failed_.store(true, std::memory_order_release);
        release_env(env, attached);
        return false;
    }

    jstring java_key = env->NewStringUTF(key.c_str());
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(len));
    if (java_key == nullptr || bytes == nullptr) {
        if (java_key != nullptr) env->DeleteLocalRef(java_key);
        if (bytes != nullptr) env->DeleteLocalRef(bytes);
        record_failure(env);
        release_env(env, attached);
        return false;
    }
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));
    const jboolean saved = env->ExceptionCheck()
        ? JNI_FALSE
        : env->CallBooleanMethod(store_, save_blob_, java_key, bytes);
    env->DeleteLocalRef(java_key);
    env->DeleteLocalRef(bytes);
    if (env->ExceptionCheck() || saved != JNI_TRUE) {
        record_failure(env);
        release_env(env, attached);
        return false;
    }
    release_env(env, attached);
    return true;
}

}  // namespace sendspin
