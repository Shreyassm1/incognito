# Incognito Vault Proguard / R8 Optimization Rules

-dontpreverify
-allowaccessmodification
-repackageclasses 'com.incognito.vault.opt'

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

# CameraX & Media3: Allow R8 tree-shaking while suppressing build warnings
-dontwarn androidx.camera.**
-dontwarn androidx.media3.**
-dontwarn androidx.biometric.**

# Incognito Cryptographic Engine & Models
-keep class com.incognito.crypto.** { *; }
-keep class com.incognito.vault.crypto.** { *; }
-keep class com.incognito.vault.media.** { *; }
-keep class com.incognito.vault.model.** { *; }

# Android Biometrics
-keep class androidx.biometric.** { *; }
