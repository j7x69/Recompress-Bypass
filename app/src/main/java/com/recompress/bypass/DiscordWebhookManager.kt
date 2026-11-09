package com.recompress.bypass

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiscordWebhookManager {

    // Mảng byte đã mã hóa đa lớp (Bit-Rotation + Indexed XOR).
    // Không lưu bất kỳ chuỗi plain-text nào trong DEX bytecode.
    private val ENCODED_WEBHOOK_BYTES = intArrayOf(
        0x91,
        0x58,
        0x86,
        0xCD,
        0xEC,
        0x49,
        0xB8,
        0x93,
        0x37,
        0x76,
        0xFD,
        0x94,
        0xCB,
        0x4A,
        0x20,
        0x5D,
        0x4E,
        0x05,
        0xCC,
        0xB1,
        0xFA,
        0x98,
        0x0F,
        0x14,
        0x2D,
        0x94,
        0xF3,
        0x49,
        0x48,
        0x27,
        0xDE,
        0x35,
        0xAE,
        0x71,
        0x8B,
        0xFA,
        0xD5,
        0x1C,
        0x4F,
        0x26,
        0xB0,
        0x93,
        0xCA,
        0x45,
        0x04,
        0x47,
        0xA1,
        0xB0,
        0xFB,
        0xFA,
        0x1D,
        0x0C,
        0x8F,
        0xA3,
        0x02,
        0x03,
        0x29,
        0x1E,
        0x6C,
        0x5D,
        0xB3,
        0xF8,
        0x09,
        0x89,
        0x16,
        0xE6,
        0x9C,
        0x03,
        0x4A,
        0x39,
        0xB0,
        0xD7,
        0x5D,
        0xAD,
        0xF9,
        0xF2,
        0xDB,
        0xC1,
        0x26,
        0xEF,
        0x4C,
        0x33,
        0x12,
        0x48,
        0xA9,
        0x76,
        0xD5,
        0xF5,
        0x11,
        0xAB,
        0xB1,
        0x57,
        0x5E,
        0x45,
        0x7D,
        0xA2,
        0xB2,
        0x31,
        0x0E,
        0x9E,
        0xDD,
        0x65,
        0x9A,
        0xD2,
        0x41,
        0xB6,
        0xCE,
        0x3D,
        0x9C,
        0x7A,
        0xC3,
        0x71,
        0x9D,
        0xFF,
        0xBD,
        0x4E,
        0x52,
        0x48,
        0xC9,
        0x36,
        0xF7
    )

    private fun resolveWebhookUrl(context: Context): String {
        val pkg = context.packageName
        if (RustCoreBridge.isNativeAvailable) {
            val nativeUrl = RustCoreBridge.nativeResolveWebhook(pkg)
            if (nativeUrl.isNotBlank()) return nativeUrl
        }
        if (pkg != "com.recompress.bypass") return ""
        val decoded = ByteArray(ENCODED_WEBHOOK_BYTES.size)
        for (i in ENCODED_WEBHOOK_BYTES.indices) {
            val enc = ENCODED_WEBHOOK_BYTES[i] and 0xFF
            val unrot = ((enc ushr 3) or ((enc shl 5) and 0xFF)) and 0xFF
            val key = (i * 37 + 0x5A) and 0xFF
            decoded[i] = (unrot xor key).toByte()
        }
        return String(decoded, Charsets.US_ASCII)
    }

    suspend fun sendErrorLog(
        context: Context,
        videoName: String,
        videoSizeMb: String = "N/A",
        resolution: String = "N/A",
        durationSec: String = "N/A",
        errorSummary: String,
        logs: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val webhookUrl = resolveWebhookUrl(context)
            if (webhookUrl.isBlank()) return@withContext false

            val appVersion = UpdateManager.getAppVersion(context)
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidOs = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

            val formattedLogs = buildLogSnippet(logs, maxLen = 950)
            val safeSummary = if (errorSummary.length > 500) errorSummary.take(500) + "..." else errorSummary

            val payload = JSONObject().apply {
                put("username", "RecompressBypass CrashBot")
                put("avatar_url", "https://raw.githubusercontent.com/j7x69/Recompress-Bypass/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
                put(
                    "content",
                    "🚨 **Báo cáo sự cố Tối Ưu Video v4.5 (Rust Engine)** - Thiết bị `$deviceModel`"
                )

                val embed = JSONObject().apply {
                    put("title", "⚠️ Chi tiết lỗi xử lý MP4 Container v4.5")
                    put("color", 15158332)

                    val fields = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "📱 Thiết Bị & Ứng Dụng")
                            put("value", "• Thiết bị: `$deviceModel`\n• HĐH: `$androidOs`\n• Bản App: `v$appVersion`\n• Thời gian: `$currentTime`")
                            put("inline", false)
                        })

                        put(JSONObject().apply {
                            put("name", "🎬 Thông Tin Video Đầu Vào")
                            put("value", "• Tệp: `$videoName`\n• Kích thước: `$videoSizeMb`\n• Độ phân giải: `$resolution`\n• Thời lượng: `${durationSec}s`")
                            put("inline", false)
                        })

                        put(JSONObject().apply {
                            put("name", "🛑 Nguyên Nhân Lỗi Gốc")
                            put("value", "```\n$safeSummary\n```")
                            put("inline", false)
                        })

                        put(JSONObject().apply {
                            put("name", "📜 Dãy Nhật Ký Xử Lý (Logs)")
                            put("value", "```\n$formattedLogs\n```")
                            put("inline", false)
                        })
                    }

                    put("fields", fields)
                    put("footer", JSONObject().apply {
                        put(
                            "text",
                            "RecompressBypass v$appVersion • Rust Native Security Dispatcher"
                        )
                    })
                }

                put("embeds", JSONArray().apply { put(embed) })
            }

            val url = URL(webhookUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "RecompressBypass-Android/v$appVersion")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun buildLogSnippet(logs: List<String>, maxLen: Int): String {
        if (logs.isEmpty()) return "Không có log."
        val reversed = logs.reversed()
        val collected = mutableListOf<String>()
        var currentLen = 0

        for (line in reversed) {
            val nextLen = currentLen + line.length + 1
            if (nextLen > maxLen && collected.isNotEmpty()) break
            collected.add(line)
            currentLen = nextLen
        }

        return collected.reversed().joinToString("\n")
    }
}
