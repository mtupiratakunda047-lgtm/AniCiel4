package com.aniciel.app

import android.content.Context
import android.os.Environment
import java.io.File

/** Public Downloads/AniCiel only (MediaHub / safety preference). */
object DownloadSettings {
    fun publicDownloadDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniCiel")

    fun publicAniCielRoot(): File = publicDownloadDir()

    /** No-arg form. */
    fun resolvedDir(): File = publicDownloadDir().also { it.mkdirs() }

    /** Context overload kept so older call sites compile. */
    fun resolvedDir(context: Context): File = resolvedDir()
}
