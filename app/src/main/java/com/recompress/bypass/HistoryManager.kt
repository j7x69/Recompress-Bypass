package com.recompress.bypass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CompressedVideoRecord(
    val id: String,
    val displayName: String,
    val originalName: String,
    val originalSizeMb: String,
    val optimizedSizeMb: String,
    val resolution: String,
    val durationSec: String,
    val timestamp: Long,
    val outputPath: String,
    val thumbnailFileName: String?,
    val logs: List<String>,
    val contentUri: String? = null
)

object HistoryRepository {

    private const val FILE_NAME = "compressed_history.json"

    fun loadHistory(context: Context): List<CompressedVideoRecord> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CompressedVideoRecord>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val logsArr = obj.optJSONArray("logs")
                val logs = mutableListOf<String>()
                if (logsArr != null) {
                    for (j in 0 until logsArr.length()) {
                        logs.add(logsArr.getString(j))
                    }
                }

                list.add(
                    CompressedVideoRecord(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        displayName = obj.optString("displayName", "Video"),
                        originalName = obj.optString("originalName", "video.mp4"),
                        originalSizeMb = obj.optString("originalSizeMb", "0.00"),
                        optimizedSizeMb = obj.optString("optimizedSizeMb", "0.00"),
                        resolution = obj.optString("resolution", "1080x1920"),
                        durationSec = obj.optString("durationSec", "0.0"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        outputPath = obj.optString("outputPath", ""),
                        thumbnailFileName = obj.optString("thumbnailFileName", null.toString()).takeIf { it != "null" && it.isNotBlank() },
                        logs = logs,
                        contentUri = obj.optString("contentUri", "").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRecord(context: Context, record: CompressedVideoRecord) {
        val current = loadHistory(context).toMutableList()
        // Thêm vào đầu danh sách
        current.removeAll { it.id == record.id }
        current.add(0, record)

        // Giới hạn lưu 50 item gần nhất
        val limited = current.take(50)

        val array = JSONArray()
        for (item in limited) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("displayName", item.displayName)
                put("originalName", item.originalName)
                put("originalSizeMb", item.originalSizeMb)
                put("optimizedSizeMb", item.optimizedSizeMb)
                put("resolution", item.resolution)
                put("durationSec", item.durationSec)
                put("timestamp", item.timestamp)
                put("outputPath", item.outputPath)
                put("thumbnailFileName", item.thumbnailFileName ?: JSONObject.NULL)
                val logsArr = JSONArray()
                item.logs.forEach { logsArr.put(it) }
                put("logs", logsArr)
                put("contentUri", item.contentUri ?: JSONObject.NULL)
            }
            array.put(obj)
        }

        val file = File(context.filesDir, FILE_NAME)
        file.writeText(array.toString())

        // Tự động dọn dẹp các file thumbnail mồ côi không còn nằm trong 50 item lịch sử
        val activeThumbs = limited.mapNotNull { it.thumbnailFileName }.toSet()
        cleanOrphanThumbnails(context, activeThumbs)
    }

    fun cleanOrphanThumbnails(context: Context, activeThumbnails: Set<String>) {
        try {
            val filesDir = context.filesDir
            filesDir.listFiles { _, name -> name.startsWith("thumb_") && name.endsWith(".jpg") }?.forEach { f ->
                if (!activeThumbnails.contains(f.name)) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun saveThumbnail(context: Context, id: String, bitmap: Bitmap): String {
        val fileName = "thumb_${id}.jpg"
        val file = File(context.filesDir, fileName)

        // Tối ưu kích thước: scale frame video về tối đa 320px
        // Thumbnail chỉ nặng ~15KB - 25KB thay vì 1-2MB nguyên frame
        val maxDim = 320
        val (scaledW, scaledH) = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim.toFloat() / kotlin.math.max(bitmap.width, bitmap.height)
            (bitmap.width * ratio).toInt().coerceAtLeast(1) to (bitmap.height * ratio).toInt().coerceAtLeast(1)
        } else {
            bitmap.width to bitmap.height
        }

        val scaledBmp = if (scaledW != bitmap.width || scaledH != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        } else {
            bitmap
        }

        FileOutputStream(file).use { out ->
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        if (scaledBmp != bitmap) {
            scaledBmp.recycle()
        }

        return fileName
    }

    fun loadThumbnail(context: Context, fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun generateDisplayName(index: Int): String {
        val dateStr = SimpleDateFormat("MMdd", Locale.getDefault()).format(Date())
        val seqStr = String.format(Locale.getDefault(), "%02d", index + 1)
        return "$dateStr-$seqStr"
    }
}
