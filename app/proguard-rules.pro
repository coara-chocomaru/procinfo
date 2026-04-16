
-keep class com.coara.proc.ProcInfoNative { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.coara.proc.IProcInfoService { *; }
-keep class com.coara.proc.IProcInfoService$Stub { *; }
-keep class com.coara.proc.IProcInfoService$Stub$Proxy { *; }
-keep class com.coara.proc.MainActivity { *; }
-keep class com.coara.proc.ProcInfoService { *; }
-keep class com.coara.proc.databinding.** { *; }
-keep class com.coara.proc.MainActivity$ProcInfoMethod { *; }
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
