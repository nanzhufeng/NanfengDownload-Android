package com.nanzhufeng.videodownloader.data.playback

import android.content.SharedPreferences
import com.nanzhufeng.videodownloader.domain.playback.VideoPositionStorage

class PreferencesVideoPositionStorage(private val preferences: SharedPreferences) : VideoPositionStorage {
    override fun read(key: String): Long = preferences.getLong(key, 0L)
    @Suppress("ApplySharedPref") // Seek/close/lifecycle checkpoints must reach disk before process exit.
    override fun write(key: String, positionMillis: Long, flush: Boolean): Boolean {
        val update = preferences.edit().putLong(key, positionMillis)
        return if (flush) update.commit() else { update.apply(); true }
    }
}
