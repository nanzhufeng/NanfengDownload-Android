#include <jni.h>
#include <lame.h>

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <new>
#include <string>
#include <vector>

namespace {

struct EncoderSession {
    lame_t lame = nullptr;
    FILE* output = nullptr;
    int channels = 0;
    bool finished = false;
    std::vector<unsigned char> buffer;
};

void throw_io_exception(JNIEnv* env, const std::string& message) {
    jclass exception_class = env->FindClass("java/io/IOException");
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

EncoderSession* session_from(jlong handle) {
    return reinterpret_cast<EncoderSession*>(handle);
}

bool write_encoded(JNIEnv* env, EncoderSession* session, int encoded_bytes) {
    if (encoded_bytes <= 0) {
        return encoded_bytes == 0;
    }
    const size_t written = std::fwrite(
        session->buffer.data(),
        1,
        static_cast<size_t>(encoded_bytes),
        session->output
    );
    if (written != static_cast<size_t>(encoded_bytes)) {
        throw_io_exception(env, "Failed to write encoded MP3 bytes");
        return false;
    }
    return true;
}

void destroy_session(EncoderSession* session) {
    if (session == nullptr) return;
    if (session->lame != nullptr) {
        lame_close(session->lame);
        session->lame = nullptr;
    }
    if (session->output != nullptr) {
        std::fclose(session->output);
        session->output = nullptr;
    }
    delete session;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_nanzhufeng_videodownloader_domain_download_audio_NativeLameBridge_open(
    JNIEnv* env,
    jobject,
    jstring path,
    jint sample_rate,
    jint channels,
    jint bit_rate_kbps
) {
    if (path == nullptr || sample_rate <= 0 || (channels != 1 && channels != 2) || bit_rate_kbps <= 0) {
        throw_io_exception(env, "Invalid LAME encoder configuration");
        return 0;
    }

    const char* path_chars = env->GetStringUTFChars(path, nullptr);
    if (path_chars == nullptr) return 0;

    EncoderSession* session = new (std::nothrow) EncoderSession();
    if (session == nullptr) {
        env->ReleaseStringUTFChars(path, path_chars);
        throw_io_exception(env, "Failed to allocate LAME encoder session");
        return 0;
    }

    session->output = std::fopen(path_chars, "wb");
    const int open_errno = errno;
    env->ReleaseStringUTFChars(path, path_chars);
    if (session->output == nullptr) {
        const std::string message = "Failed to open MP3 output: " + std::string(std::strerror(open_errno));
        destroy_session(session);
        throw_io_exception(env, message);
        return 0;
    }

    session->lame = lame_init();
    session->channels = channels;
    if (session->lame == nullptr) {
        destroy_session(session);
        throw_io_exception(env, "lame_init failed");
        return 0;
    }

    int result = 0;
    result |= lame_set_in_samplerate(session->lame, sample_rate);
    result |= lame_set_num_channels(session->lame, channels);
    result |= lame_set_brate(session->lame, bit_rate_kbps);
    result |= lame_set_quality(session->lame, 2);
    result |= lame_set_VBR(session->lame, vbr_off);
    result |= lame_set_mode(session->lame, channels == 1 ? MONO : JOINT_STEREO);
    id3tag_init(session->lame);
    id3tag_add_v2(session->lame);
    id3tag_v2_only(session->lame);

    if (result != 0 || lame_init_params(session->lame) < 0) {
        destroy_session(session);
        throw_io_exception(env, "lame_init_params rejected the encoder configuration");
        return 0;
    }

    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nanzhufeng_videodownloader_domain_download_audio_NativeLameBridge_encode(
    JNIEnv* env,
    jobject,
    jlong handle,
    jshortArray pcm,
    jint frames
) {
    EncoderSession* session = session_from(handle);
    if (session == nullptr || session->lame == nullptr || session->output == nullptr || session->finished) {
        throw_io_exception(env, "LAME encoder session is not writable");
        return -1;
    }
    if (pcm == nullptr || frames < 0) {
        throw_io_exception(env, "Invalid PCM buffer");
        return -1;
    }

    const jsize sample_count = env->GetArrayLength(pcm);
    const long long required_samples = static_cast<long long>(frames) * session->channels;
    if (required_samples > sample_count) {
        throw_io_exception(env, "PCM buffer contains incomplete frames");
        return -1;
    }

    const size_t output_capacity = static_cast<size_t>((frames * 5LL) / 4LL + 7200LL);
    session->buffer.resize(output_capacity);
    jshort* samples = env->GetShortArrayElements(pcm, nullptr);
    if (samples == nullptr) return -1;

    const int encoded_bytes = session->channels == 1
        ? lame_encode_buffer(
            session->lame,
            samples,
            samples,
            frames,
            session->buffer.data(),
            static_cast<int>(session->buffer.size())
        )
        : lame_encode_buffer_interleaved(
            session->lame,
            samples,
            frames,
            session->buffer.data(),
            static_cast<int>(session->buffer.size())
        );
    env->ReleaseShortArrayElements(pcm, samples, JNI_ABORT);

    if (encoded_bytes < 0) return encoded_bytes;
    if (!write_encoded(env, session, encoded_bytes)) return -1;
    return encoded_bytes;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nanzhufeng_videodownloader_domain_download_audio_NativeLameBridge_finish(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    EncoderSession* session = session_from(handle);
    if (session == nullptr || session->lame == nullptr || session->output == nullptr) {
        throw_io_exception(env, "LAME encoder session is closed");
        return -1;
    }
    if (session->finished) return 0;

    session->buffer.resize(7200);
    const int encoded_bytes = lame_encode_flush(
        session->lame,
        session->buffer.data(),
        static_cast<int>(session->buffer.size())
    );
    if (encoded_bytes < 0) return encoded_bytes;
    if (!write_encoded(env, session, encoded_bytes)) return -1;
    if (std::fflush(session->output) != 0) {
        throw_io_exception(env, "Failed to flush MP3 output");
        return -1;
    }
    session->finished = true;
    return encoded_bytes;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nanzhufeng_videodownloader_domain_download_audio_NativeLameBridge_close(
    JNIEnv*,
    jobject,
    jlong handle
) {
    destroy_session(session_from(handle));
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_6;
}
