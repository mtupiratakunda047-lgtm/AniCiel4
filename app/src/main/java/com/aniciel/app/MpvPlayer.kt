package com.aniciel.app

import android.content.Context
import android.widget.FrameLayout

/**
 * Stub mpv backend — native mpv-android-lib is optional and currently disabled
 * to avoid Kotlin metadata / ABI mismatches. ExoPlayer is the default player.
 */
class MpvPlayerView(context: Context) : FrameLayout(context) {
    fun initialize(configDir: String, cacheDir: String) {}
    fun load(url: String, headers: Map<String, String> = emptyMap()) {}
    fun play() {}
    fun pause() {}
    fun seekTo(seconds: Double) {}
    fun seekBy(seconds: Double) {}
    fun isPaused(): Boolean = true
    fun positionSeconds(): Double = 0.0
    fun durationSeconds(): Double = 0.0
    fun setSpeed(speed: Float) {}
    fun destroy() {}
}

object PlayerBackend {
    const val EXO = "exo"
    const val MPV = "mpv"
}
