package com.aniciel.app

import android.os.Environment
import java.io.File

/** Public Downloads/AniCiel only (MediaHub / safety preference). */
object DownloadSettings {
    fun publicDownloadDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniCiel")

    fun publicAniCielRoot(): File = publicDownloadDir()

    fun resolvedDir(): File = publicDownloadDir().also { it.mkdirs() }
}
