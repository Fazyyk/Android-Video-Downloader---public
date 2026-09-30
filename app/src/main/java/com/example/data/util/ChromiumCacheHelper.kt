package com.example.data.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Pre-initializes and sanitizes Chromium WebView cache, index, and Crashpad directory structures.
 * This prevents ENOENT errors on startup when Chromium SimpleFileEnumerator and Crashpad
 * attempt to open or write cache directories and crash attachments.
 */
object ChromiumCacheHelper {

    private const val TAG = "ChromiumCacheHelper"
    private const val KNOWN_CRASHPAD_UUID = "7fac6a52-9500-47b9-a5e3-aef5ab395ad6"

    fun prepareDirectories(context: Context) {
        try {
            val cacheDir = context.cacheDir ?: return
            grantFullPermissions(cacheDir)

            // WebView cache root
            val webViewDir = File(cacheDir, "WebView")
            val defaultDir = File(webViewDir, "Default")
            val httpCacheDir = File(defaultDir, "HTTP Cache")
            val indexDir = File(httpCacheDir, "index-dir")
            val codeCacheDir = File(httpCacheDir, "Code Cache")
            val jsDir = File(codeCacheDir, "js")
            val wasmDir = File(codeCacheDir, "wasm")

            val cacheDirectories = listOf(
                webViewDir,
                defaultDir,
                httpCacheDir,
                indexDir,
                codeCacheDir,
                jsDir,
                wasmDir
            )

            for (dir in cacheDirectories) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                grantFullPermissions(dir)
            }

            // Ensure "index" under HTTP Cache is not mistakenly a directory
            // (Chromium simple_version_upgrade writes a file named "index" as fake index)
            val fakeIndex = File(httpCacheDir, "index")
            if (fakeIndex.exists() && fakeIndex.isDirectory) {
                fakeIndex.deleteRecursively()
            }

            // Crashpad directories (for crash reporter and attachments)
            val crashpadDir = File(webViewDir, "Crashpad")
            val attachmentsDir = File(crashpadDir, "attachments")
            val completedDir = File(crashpadDir, "completed")
            val newDir = File(crashpadDir, "new")
            val pendingDir = File(crashpadDir, "pending")

            val crashpadDirs = listOf(
                crashpadDir,
                attachmentsDir,
                completedDir,
                newDir,
                pendingDir
            )

            for (dir in crashpadDirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                grantFullPermissions(dir)
            }

            // Ensure the reported Crashpad attachment directory exists
            val knownAttachmentDir = File(attachmentsDir, KNOWN_CRASHPAD_UUID)
            if (!knownAttachmentDir.exists()) {
                knownAttachmentDir.mkdirs()
            }
            grantFullPermissions(knownAttachmentDir)

            // Scan any existing crash reports in Crashpad subdirectories
            // and ensure their attachment directories exist so directory_reader_posix won't fail
            listOf(completedDir, pendingDir, newDir).forEach { subDir ->
                if (subDir.exists() && subDir.isDirectory) {
                    subDir.listFiles()?.forEach { reportFile ->
                        val reportId = reportFile.nameWithoutExtension
                        if (reportId.isNotBlank()) {
                            val reportAttachment = File(attachmentsDir, reportId)
                            if (!reportAttachment.exists()) {
                                reportAttachment.mkdirs()
                            }
                            grantFullPermissions(reportAttachment)
                        }
                    }
                }
            }

            // Also check app_webview data directory
            try {
                val appWebViewDir = File(context.applicationInfo.dataDir, "app_webview")
                if (!appWebViewDir.exists()) {
                    appWebViewDir.mkdirs()
                }
                grantFullPermissions(appWebViewDir)
            } catch (_: Throwable) {}

        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fully initialize Chromium cache directories", e)
        }
    }

    private fun grantFullPermissions(file: File) {
        try {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        } catch (_: Throwable) {}
    }
}
