package com.example

import android.app.Application
import android.system.Os
import android.util.Log
import androidx.work.Configuration
import com.example.data.util.ChromiumCacheHelper
import java.io.File

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

        // Pre-initialize and sanitize WebView HTTP cache, index, and Crashpad directories
        // to prevent Chromium simple_file_enumerator ENOENT errors and fake index write failures
        ChromiumCacheHelper.prepareDirectories(this)
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
