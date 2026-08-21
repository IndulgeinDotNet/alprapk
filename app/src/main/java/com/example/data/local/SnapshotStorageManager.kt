package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class SnapshotStorageManager(private val context: Context) {

    private val snapshotsDir = File(context.filesDir, "snapshots").apply {
        if (!exists()) mkdirs()
    }

    suspend fun saveBitmapSnapshot(bitmap: Bitmap, plateNumber: String): String = withContext(Dispatchers.IO) {
        val fileName = "snap_${plateNumber}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val targetFile = File(snapshotsDir, fileName)
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return@withContext targetFile.absolutePath
    }

    fun getFileFromUri(pathOrUri: String?): File? {
        if (pathOrUri.isNullOrBlank()) return null
        val file = File(pathOrUri)
        return if (file.exists()) file else null
    }
}
