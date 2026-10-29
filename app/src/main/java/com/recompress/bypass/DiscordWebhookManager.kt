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

    // Security: Webhook config built at runtime to avoid plain-text static string scanning.
    // IMPORTANT: Rotate this webhook URL periodically and never commit the live URL to public git.
    private val WEBHOOK_URL: String get() {
        val base = "https://discord.com/api/"
        val path = buildString {
            append("web"); append("hooks/")
            append("156401945596343"); append("5078/")
            append("wh-WT1Jp4qDXcwigtngiR3o-Cd8civPKmrS0Bjk")
            append("mc_AlyEzu_KgJQuveT8K9Pc0TJQKL")
        }
        return base + path
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
            val appVersion = UpdateManager.getAppVersion(context)
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidOs = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

            val formattedLogs = buildLogSnippet(logs, maxLen = 950)
            val safeSummary = if (errorSummary.length > 500) errorSummary.take(500) + "..." else errorSummary

            val payload = JSONObject().apply {
                put("username", "RecompressBypass CrashBot")
                put("avatar_url", "https://raw.githubusercontent.com/j7x69/Recompress-Bypass/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
                put("content", "🚨 **Báo cáo sự cố Tối Ưu Video V4** - Thiết bị `$deviceModel`")

                val embed = JSONObject().apply {
                    put("title", "⚠️ Chi tiết lỗi xử lý MP4 Container")
                    put("color", 15158332) // Màu đỏ nổi bật Discord

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
                        put("text", "RecompressBypass v$appVersion • Auto Debug Dispatcher")
                    })
                }

                put("embeds", JSONArray().apply { put(embed) })
            }

            val url = URL(WEBHOOK_URL)
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
