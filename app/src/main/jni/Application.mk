APP_PLATFORM := android-24
APP_ABI := arm64-v8a armeabi-v7a x86 x86_64
APP_STL := none
APP_OPTIM := release
APP_CPPFLAGS := -fvisibility=hidden -O2
APP_CFLAGS := -O2 -fvisibility=hidden -ffunction-sections -fdata-sections
APP_LDFLAGS := -Wl,--gc-sections
