package com.example.snapgps.data.media

import android.content.Context
import java.io.File

/** Scratch space for the capture pipeline. Nothing here outlives a single capture. */
class TempFiles(context: Context) {

    private val dir = File(context.cacheDir, "capture")

    fun newJpeg(prefix: String): File {
        dir.mkdirs()
        return File.createTempFile(prefix, ".jpg", dir)
    }

    /** Clears leftovers from a capture that was interrupted (e.g. process death mid-save). */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
