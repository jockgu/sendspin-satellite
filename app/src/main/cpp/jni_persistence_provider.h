#pragma once

#include <jni.h>

#include <atomic>
#include <optional>
#include <string>
#include <vector>

#include <sendspin/client.h>

namespace sendspin {

/// Sendspin's byte-blob persistence API backed by the Android Keystore store in Kotlin.
class JniPersistenceProvider final : public SendspinPersistenceProvider {
public:
    JniPersistenceProvider(JNIEnv* env, jobject store);
    ~JniPersistenceProvider() override;

    JniPersistenceProvider(const JniPersistenceProvider&) = delete;
    JniPersistenceProvider& operator=(const JniPersistenceProvider&) = delete;

    std::optional<std::vector<uint8_t>> load_blob(const std::string& key) override;
    bool save_blob(const std::string& key, const uint8_t* data, size_t len) override;
    [[nodiscard]] bool failed() const { return failed_.load(std::memory_order_acquire); }

private:
    JNIEnv* env_for_call(bool* attached) const;
    void release_env(JNIEnv* env, bool attached) const;
    void record_failure(JNIEnv* env);

    JavaVM* vm_{nullptr};
    jobject store_{nullptr};
    jmethodID load_blob_{nullptr};
    jmethodID save_blob_{nullptr};
    std::atomic<bool> failed_{false};
};

}  // namespace sendspin
