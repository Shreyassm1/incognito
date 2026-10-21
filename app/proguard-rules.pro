# Incognito Vault Proguard / R8 Rules

-dontpreverify
-allowaccessmodification

# Keep line numbers for stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX Core & Lifecycle
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Media3 / ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class androidx.camera.video.** { *; }
-dontwarn androidx.camera.**

# Incognito Cryptographic Engine
-keep class com.incognito.crypto.** { *; }
-keep class com.incognito.vault.crypto.** { *; }
-keep class com.incognito.vault.media.** { *; }
-keep class com.incognito.vault.model.** { *; }

# Android Biometrics
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**
