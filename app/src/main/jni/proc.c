#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdarg.h>
#include <ctype.h>
#include <limits.h>
#include <time.h>
#include <unistd.h>

#define READ_CHUNK_SIZE 8192
#define INITIAL_TEXT_CAPACITY 4096
#define INLINE_TEXT_LIMIT (512 * 1024)
#define CACHE_MARKER_PREFIX "__PROC_CACHE__:"
#define UNUSED(x) (void)(x)

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

static char g_cache_dir[PATH_MAX];
static int g_cache_dir_ready = 0;
static unsigned int g_cache_sequence = 0;

#ifndef AT_NULL
#define AT_NULL 0
#endif
#ifndef AT_IGNORE
#define AT_IGNORE 1
#endif
#ifndef AT_EXECFD
#define AT_EXECFD 2
#endif
#ifndef AT_PHDR
#define AT_PHDR 3
#endif
#ifndef AT_PHENT
#define AT_PHENT 4
#endif
#ifndef AT_PHNUM
#define AT_PHNUM 5
#endif
#ifndef AT_PAGESZ
#define AT_PAGESZ 6
#endif
#ifndef AT_BASE
#define AT_BASE 7
#endif
#ifndef AT_FLAGS
#define AT_FLAGS 8
#endif
#ifndef AT_ENTRY
#define AT_ENTRY 9
#endif
#ifndef AT_NOTELF
#define AT_NOTELF 10
#endif
#ifndef AT_UID
#define AT_UID 11
#endif
#ifndef AT_EUID
#define AT_EUID 12
#endif
#ifndef AT_GID
#define AT_GID 13
#endif
#ifndef AT_EGID
#define AT_EGID 14
#endif
#ifndef AT_PLATFORM
#define AT_PLATFORM 15
#endif
#ifndef AT_HWCAP
#define AT_HWCAP 16
#endif
#ifndef AT_CLKTCK
#define AT_CLKTCK 17
#endif
#ifndef AT_SECURE
#define AT_SECURE 23
#endif
#ifndef AT_BASE_PLATFORM
#define AT_BASE_PLATFORM 24
#endif
#ifndef AT_RANDOM
#define AT_RANDOM 25
#endif
#ifndef AT_HWCAP2
#define AT_HWCAP2 26
#endif
#ifndef AT_EXECFN
#define AT_EXECFN 31
#endif
#ifndef AT_SYSINFO_EHDR
#define AT_SYSINFO_EHDR 33
#endif

static jstring newUtf(JNIEnv *env, const char *text) {
    return (*env)->NewStringUTF(env, text ? text : "");
}

static int appendFormatted(char **buffer, size_t *capacity, size_t *length, const char *format, ...) {
    va_list args;
    va_start(args, format);
    va_list copy;
    va_copy(copy, args);
    int needed = vsnprintf(NULL, 0, format, copy);
    va_end(copy);
    if (needed < 0) {
        va_end(args);
        return 0;
    }

    size_t required = *length + (size_t)needed + 1;
    if (required > *capacity) {
        size_t newCapacity = (*capacity == 0) ? INITIAL_TEXT_CAPACITY : *capacity;
        while (newCapacity < required) {
            newCapacity *= 2;
        }
        char *newBuffer = (char *)realloc(*buffer, newCapacity);
        if (newBuffer == NULL) {
            va_end(args);
            return 0;
        }
        *buffer = newBuffer;
        *capacity = newCapacity;
    }

    vsnprintf(*buffer + *length, *capacity - *length, format, args);
    *length += (size_t)needed;
    va_end(args);
    return 1;
}

static const char *getAuxTypeName(unsigned long type) {
    switch (type) {
        case AT_NULL: return "AT_NULL";
        case AT_IGNORE: return "AT_IGNORE";
        case AT_EXECFD: return "AT_EXECFD";
        case AT_PHDR: return "AT_PHDR";
        case AT_PHENT: return "AT_PHENT";
        case AT_PHNUM: return "AT_PHNUM";
        case AT_PAGESZ: return "AT_PAGESZ";
        case AT_BASE: return "AT_BASE";
        case AT_FLAGS: return "AT_FLAGS";
        case AT_ENTRY: return "AT_ENTRY";
        case AT_NOTELF: return "AT_NOTELF";
        case AT_UID: return "AT_UID";
        case AT_EUID: return "AT_EUID";
        case AT_GID: return "AT_GID";
        case AT_EGID: return "AT_EGID";
        case AT_PLATFORM: return "AT_PLATFORM";
        case AT_HWCAP: return "AT_HWCAP";
        case AT_CLKTCK: return "AT_CLKTCK";
        case AT_SECURE: return "AT_SECURE";
        case AT_BASE_PLATFORM: return "AT_BASE_PLATFORM";
        case AT_RANDOM: return "AT_RANDOM";
        case AT_HWCAP2: return "AT_HWCAP2";
        case AT_EXECFN: return "AT_EXECFN";
        case AT_SYSINFO_EHDR: return "AT_SYSINFO_EHDR";
        default: return NULL;
    }
}

static char *readFileContent(const char *filePath) {
    FILE *fp = fopen(filePath, "rb");
    if (!fp) return NULL;

    size_t capacity = READ_CHUNK_SIZE + 1;
    char *buffer = (char *)malloc(capacity);
    if (buffer == NULL) {
        fclose(fp);
        return NULL;
    }

    size_t totalRead = 0;
    for (;;) {
        size_t room = capacity - totalRead - 1;
        if (room < READ_CHUNK_SIZE) {
            capacity *= 2;
            char *newBuffer = (char *)realloc(buffer, capacity);
            if (newBuffer == NULL) {
                free(buffer);
                fclose(fp);
                return NULL;
            }
            buffer = newBuffer;
            room = capacity - totalRead - 1;
        }

        size_t bytesRead = fread(buffer + totalRead, 1, room, fp);
        totalRead += bytesRead;
        if (bytesRead == 0) {
            break;
        }
    }

    buffer[totalRead] = '\0';
    fclose(fp);
    return buffer;
}

static unsigned char *readBinaryFileContent(const char *filePath, size_t *outSize) {
    FILE *fp = fopen(filePath, "rb");
    if (!fp) return NULL;

    size_t capacity = READ_CHUNK_SIZE;
    unsigned char *buffer = (unsigned char *)malloc(capacity);
    if (buffer == NULL) {
        fclose(fp);
        return NULL;
    }

    size_t totalRead = 0;
    for (;;) {
        if (totalRead == capacity) {
            capacity *= 2;
            unsigned char *newBuffer = (unsigned char *)realloc(buffer, capacity);
            if (newBuffer == NULL) {
                free(buffer);
                fclose(fp);
                return NULL;
            }
            buffer = newBuffer;
        }

        size_t bytesRead = fread(buffer + totalRead, 1, capacity - totalRead, fp);
        totalRead += bytesRead;
        if (bytesRead == 0) {
            break;
        }
    }

    fclose(fp);
    *outSize = totalRead;
    return buffer;
}

static int appendString(char **buffer, size_t *capacity, size_t *length, const char *text) {
    return appendFormatted(buffer, capacity, length, "%s", text);
}

static int appendHexBytes(char **buffer, size_t *capacity, size_t *length, const unsigned char *data, size_t count) {
    for (size_t i = 0; i < count; ++i) {
        if (!appendFormatted(buffer, capacity, length, "%02x", data[i])) return 0;
        if (i + 1 < count && !appendString(buffer, capacity, length, " ")) return 0;
    }
    return 1;
}

static size_t boundedStringLength(const char *text, size_t maxLen) {
    size_t n = 0;
    while (n < maxLen && text[n] != '\0') {
        ++n;
    }
    return n;
}

static void sanitizeAscii(char *text, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        if (!isprint((unsigned char)text[i])) {
            text[i] = '?';
        }
    }
}

static void setCacheDirFromJava(JNIEnv *env, jstring cacheDir) {
    if (cacheDir == NULL) {
        g_cache_dir[0] = '\0';
        g_cache_dir_ready = 0;
        return;
    }

    const char *utf = (*env)->GetStringUTFChars(env, cacheDir, NULL);
    if (utf == NULL) {
        g_cache_dir[0] = '\0';
        g_cache_dir_ready = 0;
        return;
    }

    snprintf(g_cache_dir, sizeof(g_cache_dir), "%s", utf);
    g_cache_dir_ready = (g_cache_dir[0] != '\0');
    (*env)->ReleaseStringUTFChars(env, cacheDir, utf);
}

static int openCacheFileForPath(char *outPath, size_t outPathSize, FILE **outFile) {
    if (!g_cache_dir_ready || g_cache_dir[0] == '\0') {
        return 0;
    }

    unsigned long pid = (unsigned long)getpid();
    unsigned long now = (unsigned long)time(NULL);
    unsigned int seq = ++g_cache_sequence;
    int written = snprintf(outPath, outPathSize, "%s/proc_cache_%lu_%lu_%u.txt", g_cache_dir, pid, now, seq);
    if (written < 0 || (size_t)written >= outPathSize) {
        return 0;
    }

    FILE *fp = fopen(outPath, "wb");
    if (!fp) {
        return 0;
    }
    *outFile = fp;
    return 1;
}

static jstring makeCacheMarker(JNIEnv *env, const char *cachePath) {
    char marker[PATH_MAX + 32];
    snprintf(marker, sizeof(marker), "%s%s", CACHE_MARKER_PREFIX, cachePath);
    return newUtf(env, marker);
}

static jstring readTextProcFile(JNIEnv *env, const char *path) {
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        char err[256];
        snprintf(err, sizeof(err), "Error reading %s", path);
        return newUtf(env, err);
    }

    size_t capacity = READ_CHUNK_SIZE + 1;
    char *buffer = (char *)malloc(capacity);
    if (buffer == NULL) {
        fclose(fp);
        return newUtf(env, "Error allocating read buffer");
    }

    size_t totalRead = 0;
    int spillToFile = 0;
    FILE *cacheFp = NULL;
    char cachePath[PATH_MAX];
    cachePath[0] = '\0';

    for (;;) {
        if (!spillToFile) {
            size_t room = capacity - totalRead - 1;
            if (room == 0) {
                size_t newCapacity = capacity * 2;
                char *newBuffer = (char *)realloc(buffer, newCapacity);
                if (newBuffer == NULL) {
                    free(buffer);
                    fclose(fp);
                    return newUtf(env, "Error expanding read buffer");
                }
                buffer = newBuffer;
                capacity = newCapacity;
                room = capacity - totalRead - 1;
            }

            size_t bytesRead = fread(buffer + totalRead, 1, room, fp);
            if (bytesRead == 0) {
                break;
            }

            totalRead += bytesRead;
            if (totalRead > INLINE_TEXT_LIMIT) {
                if (!openCacheFileForPath(cachePath, sizeof(cachePath), &cacheFp)) {
                    free(buffer);
                    fclose(fp);
                    return newUtf(env, "Error creating cache file");
                }
                if (fwrite(buffer, 1, totalRead, cacheFp) != totalRead) {
                    fclose(cacheFp);
                    remove(cachePath);
                    free(buffer);
                    fclose(fp);
                    return newUtf(env, "Error writing cache file");
                }
                spillToFile = 1;
                free(buffer);
                buffer = NULL;
            }
        } else {
            char ioBuffer[READ_CHUNK_SIZE];
            size_t bytesRead = fread(ioBuffer, 1, sizeof(ioBuffer), fp);
            if (bytesRead == 0) {
                break;
            }
            if (fwrite(ioBuffer, 1, bytesRead, cacheFp) != bytesRead) {
                fclose(cacheFp);
                remove(cachePath);
                fclose(fp);
                return newUtf(env, "Error writing cache file");
            }
        }
    }

    fclose(fp);

    if (spillToFile) {
        if (cacheFp != NULL) {
            fclose(cacheFp);
        }
        return makeCacheMarker(env, cachePath);
    }

    buffer[totalRead] = '\0';
    jstring result = newUtf(env, buffer);
    free(buffer);
    return result;
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcVersion(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/version");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcCPUInfo(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/cpuinfo");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcMemInfo(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/meminfo");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfStatus(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/status");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfMaps(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/maps");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfMountinfo(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/mountinfo");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfMounts(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/mounts");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfMountstats(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/mountstats");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfIO(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/io");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfLimits(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/limits");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfOomScore(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/oom_score");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfOomAdj(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/oom_adj");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfOomScoreAdj(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/oom_score_adj");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSched(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/sched");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSchedBoost(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/sched_boost");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSchedBoostPeriodMs(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/sched_boost_period_ms");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSchedGroupId(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/sched_group_id");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSchedInitTaskLoad(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/sched_init_task_load");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSchedWakeUpIdle(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/sched_wake_up_idle");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSchedstat(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/schedstat");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfSmap(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);
    return readTextProcFile(env, "/proc/self/smaps");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_getProcSelfAuxvSummary(JNIEnv *env, jclass clazz) {
    UNUSED(clazz);

    size_t binarySize = 0;
    unsigned char *binary = readBinaryFileContent("/proc/self/auxv", &binarySize);
    if (!binary) {
        return newUtf(env, "Error reading /proc/self/auxv");
    }

    const size_t wordSize = sizeof(unsigned long);
    const size_t entrySize = wordSize * 2;
    const size_t entryCount = binarySize / entrySize;
    const size_t remainder = binarySize % entrySize;

    size_t capacity = INITIAL_TEXT_CAPACITY;
    char *out = (char *)malloc(capacity);
    if (out == NULL) {
        free(binary);
        return newUtf(env, "Error allocating auxv summary buffer");
    }
    out[0] = '\0';
    size_t length = 0;

    if (!appendString(&out, &capacity, &length, "/proc/self/auxv parsed summary\n")) goto auxv_fail;
    if (!appendFormatted(&out, &capacity, &length, "word_size=%zu bytes, entry_size=%zu bytes, file_size=%zu bytes\n", wordSize, entrySize, binarySize)) goto auxv_fail;
    if (remainder != 0) {
        if (!appendFormatted(&out, &capacity, &length, "warning: trailing %zu byte(s) ignored because auxv is truncated or non-standard\n", remainder)) goto auxv_fail;
    }
    if (!appendString(&out, &capacity, &length, "index | type              | value\n")) goto auxv_fail;
    if (!appendString(&out, &capacity, &length, "------+-------------------+------------------------------------------------------------\n")) goto auxv_fail;

    for (size_t i = 0; i < entryCount; ++i) {
        const unsigned char *entry = binary + (i * entrySize);
        unsigned long type = 0;
        unsigned long value = 0;
        memcpy(&type, entry, wordSize);
        memcpy(&value, entry + wordSize, wordSize);

        const char *typeName = getAuxTypeName(type);
        char typeLabel[64];
        if (typeName != NULL) {
            snprintf(typeLabel, sizeof(typeLabel), "%s", typeName);
        } else {
            snprintf(typeLabel, sizeof(typeLabel), "AT_%lu", type);
        }

        if (type == AT_NULL) {
            if (!appendFormatted(&out, &capacity, &length, "%5zu | %-17s | 0x%0*lx (terminator)\n", i, typeLabel, (int)(wordSize * 2), value)) goto auxv_fail;
            break;
        }

        if (type == AT_PLATFORM || type == AT_EXECFN || type == AT_BASE_PLATFORM) {
            const char *str = (const char *)(uintptr_t)value;
            if (str != NULL) {
                char preview[512];
                size_t n = boundedStringLength(str, sizeof(preview) - 1);
                memcpy(preview, str, n);
                preview[n] = '\0';
                sanitizeAscii(preview, n);
                if (!appendFormatted(&out, &capacity, &length, "%5zu | %-17s | 0x%0*lx  \"%s\"\n", i, typeLabel, (int)(wordSize * 2), value, preview)) goto auxv_fail;
            } else {
                if (!appendFormatted(&out, &capacity, &length, "%5zu | %-17s | 0x%0*lx\n", i, typeLabel, (int)(wordSize * 2), value)) goto auxv_fail;
            }
            continue;
        }

        if (type == AT_RANDOM) {
            const unsigned char *randomBytes = (const unsigned char *)(uintptr_t)value;
            if (!appendFormatted(&out, &capacity, &length, "%5zu | %-17s | 0x%0*lx  [16 bytes] ", i, typeLabel, (int)(wordSize * 2), value)) goto auxv_fail;
            if (!appendHexBytes(&out, &capacity, &length, randomBytes, 16)) goto auxv_fail;
            if (!appendString(&out, &capacity, &length, "\n")) goto auxv_fail;
            continue;
        }

        if (type == AT_HWCAP || type == AT_HWCAP2 || type == AT_PAGESZ || type == AT_CLKTCK || type == AT_UID || type == AT_EUID || type == AT_GID || type == AT_EGID || type == AT_PHDR || type == AT_PHENT || type == AT_PHNUM || type == AT_BASE || type == AT_FLAGS || type == AT_ENTRY || type == AT_SECURE || type == AT_SYSINFO_EHDR) {
            if (!appendFormatted(&out, &capacity, &length, "%5zu | %-17s | 0x%0*lx (%lu)\n", i, typeLabel, (int)(wordSize * 2), value, value)) goto auxv_fail;
            continue;
        }

        if (!appendFormatted(&out, &capacity, &length, "%5zu | %-17s | 0x%0*lx\n", i, typeLabel, (int)(wordSize * 2), value)) goto auxv_fail;
    }

    jstring result = newUtf(env, out);
    free(out);
    free(binary);
    return result;

auxv_fail:
    free(out);
    free(binary);
    return newUtf(env, "Error building /proc/self/auxv summary");
}

JNIEXPORT jstring JNICALL Java_com_coara_proc_ProcInfoNative_readProcFile(JNIEnv *env, jclass clazz, jstring path) {
    UNUSED(clazz);
    if (path == NULL) {
        return newUtf(env, "Error: invalid path");
    }
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (cpath == NULL) return newUtf(env, "Error: invalid path");
    jstring result = readTextProcFile(env, cpath);
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    return result;
}


JNIEXPORT void JNICALL Java_com_coara_proc_ProcInfoNative_setCacheDirectory(JNIEnv *env, jclass clazz, jstring cacheDir) {
    UNUSED(clazz);
    setCacheDirFromJava(env, cacheDir);
}
