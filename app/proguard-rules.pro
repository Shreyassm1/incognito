# Incognito Vault Proguard / R8 Optimization Rules

-dontpreverify
-allowaccessmodification
-repackageclasses 'com.incognito.vault.opt'
-optimizationpasses 5

# Strip Android debug and verbose logs from release binary
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep line numbers for stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX Core & Lifecycle ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# CameraX & Media3: Allow aggressive R8 tree-shaking
-dontwarn androidx.camera.**
-dontwarn androidx.media3.**
-dontwarn androidx.biometric.**

# Incognito Cryptographic Engine (Keep essential crypto primitives)
-keep class com.incognito.crypto.VaultCrypto { *; }
-keep class com.incognito.vault.crypto.VaultCrypto { *; }
