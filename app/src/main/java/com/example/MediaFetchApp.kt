package com.example

import android.app.Application
import android.system.Os
import android.util.Log
import androidx.work.Configuration

class MediaFetchApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        try {
            Os.setenv("MESA_LOG_FILE", "/dev/null", true)
            Os.setenv("MESA_LOG_LEVEL", "none", true)
            Os.setenv("MESA_NO_ERROR", "1", true)
            Os.setenv("MESA_DEBUG", "silent", true)
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("LIBGL_DRI3_DISABLE", "1", true)
            Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
            Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe", true)
        } catch (_: Throwable) {}

        try {
            android.webkit.WebView.enableSlowWholeDocumentDraw()
        } catch (_: Throwable) {}
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        init {
            try {
                Os.setenv("MESA_LOG_FILE", "/dev/null", true)
                Os.setenv("MESA_LOG_LEVEL", "none", true)
                Os.setenv("MESA_NO_ERROR", "1", true)
                Os.setenv("MESA_DEBUG", "silent", true)
                Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
                Os.setenv("LIBGL_DRI3_DISABLE", "1", true)
                Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
                Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe", true)
            } catch (_: Throwable) {}
        }
    }
}
