# ProGuard / R8 Rules for FlowPilot

# Keep AIDL interfaces
-keep interface * extends android.os.IInterface { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Keep models and data classes
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
