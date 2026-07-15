package com.nanzhufeng.videodownloader.probe

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaStoreProbe {
    fun writeVideo(context: Context, source: File, displayName: String): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "公共 Movies 目录探测要求 Android 10 或更高版本"
        }
        require(source.isFile && source.length() > 0L) { "源文件不存在或为空" }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/南烛枫视频下载器/Probe",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values),
        ) { "MediaStore 创建失败" }

        try {
            requireNotNull(resolver.openOutputStream(uri, "w")) {
                "MediaStore 输出流创建失败"
            }.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
