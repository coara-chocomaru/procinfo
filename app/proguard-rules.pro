# Keep JNI entry points stable. JNI symbol names depend on Java class/method names.
-keep class com.coara.proc.ProcInfoNative { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep AIDL interface and generated binder stub/proxy classes stable.
-keep class com.coara.proc.IProcInfoService { *; }
-keep class com.coara.proc.IProcInfoService$Stub { *; }
-keep class com.coara.proc.IProcInfoService$Stub$Proxy { *; }

# Keep app entry points used by the manifest.
-keep class com.coara.proc.MainActivity { *; }
-keep class com.coara.proc.ProcInfoService { *; }

# Keep generated ViewBinding classes referenced from source.
-keep class com.coara.proc.databinding.** { *; }

# Keep enum used in switch handling.
-keep class com.coara.proc.MainActivity$ProcInfoMethod { *; }

# Remove noisy logs in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
