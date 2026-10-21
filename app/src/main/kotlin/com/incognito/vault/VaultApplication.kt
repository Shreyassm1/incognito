package com.incognito.vault

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.incognito.vault.media.ThumbnailCache

class VaultApplication : Application() {

    companion object {
        lateinit var instance: VaultApplication
            private set

        var onBackgroundLockRequested: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Register application-level lifecycle observer for immediate auto-lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Immediate auto-lock when app moves to background or screen turns off
                onBackgroundLockRequested?.invoke()
                ThumbnailCache.clear()
            }
        })
    }
}
