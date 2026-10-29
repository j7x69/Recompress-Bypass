package com.recompress.bypass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteNotice(
    val active: Boolean,
    val level: String, // "info", "warning", "error"
    val title: String,
    val message: String,
    val blockActions: Boolean = false,
    val actionText: String? = null,
    val actionUrl: String? = null,
    val titleEn: String? = null,
    val messageEn: String? = null,
    val actionTextEn: String? = null,
    val titleVi: String? = null,
    val messageVi: String? = null,
    val actionTextVi: String? = null
) {
    fun getExplicitTitle(lang: AppLanguage): String? {
        return when (lang) {
            AppLanguage.EN -> titleEn?.ifBlank { null }
            AppLanguage.VI -> titleVi?.ifBlank { null } ?: title.ifBlank { null }
        }
    }

    fun getExplicitMessage(lang: AppLanguage): String? {
        return when (lang) {
            AppLanguage.EN -> messageEn?.ifBlank { null }
            AppLanguage.VI -> messageVi?.ifBlank { null } ?: message.ifBlank { null }
        }
    }

    fun getActionTextForLang(lang: AppLanguage): String {
        val specific = when (lang) {
            AppLanguage.EN -> actionTextEn?.ifBlank { null }
            AppLanguage.VI -> actionTextVi?.ifBlank { null }
        }
        if (!specific.isNullOrBlank()) return specific
        if (!actionText.isNullOrBlank()) return actionText
        return when (lang) {
            AppLanguage.EN -> "View Details"
            AppLanguage.VI -> "Xem chi tiết"
        }
    }
}

/**
 * Điều phối hiển thị nội dung thông báo theo ngôn ngữ người dùng.
 * Đã loại bỏ hoàn toàn việc gọi API dịch ngoài (MyMemory) để:
 * 1. Tải thông báo tức thì (0ms latency), không làm chậm khởi động app.
 * 2. Bảo mật tuyệt đối, không rò rỉ nội dung thông báo nội bộ.
 * 3. Không bị giới hạn lượt gọi hay lỗi rác chuỗi dịch (MYMEMORY WARNING).
 */
object NoticeTranslator {
    suspend fun resolveNoticeContent(
        notice: RemoteNotice,
        targetLang: AppLanguage
    ): Pair<String, String> = withContext(Dispatchers.Default) {
        val resolvedTitle = notice.getExplicitTitle(targetLang) ?: notice.title
        val resolvedMsg = notice.getExplicitMessage(targetLang) ?: notice.message
        Pair(resolvedTitle, resolvedMsg)
    }
}

object NoticeManager {

    private const val NOTICE_URL = "https://raw.githubusercontent.com/j7x69/Recompress-Bypass/main/notice.json"

    suspend fun fetchNotice(): RemoteNotice? = withContext(Dispatchers.IO) {
        try {
            // Thêm query timestamp để vượt qua bộ nhớ đệm (cache) của GitHub Raw
            val urlWithTimestamp = "$NOTICE_URL?t=${System.currentTimeMillis()}"
            val url = URL(urlWithTimestamp)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "RecompressBypass-Android")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (conn.responseCode != 200) {
                return@withContext null
            }

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)

            val active = root.optBoolean("active", false)
            val level = root.optString("level", "warning").lowercase()
            val title = root.optString("title", "Thông báo từ tác giả")
            val message = root.optString("message", "")
            val blockActions = root.optBoolean("block_actions", false)
            val actionText = root.optString("action_text", "").ifBlank { null }
            val actionUrl = root.optString("action_url", "").ifBlank { null }

            // Các trường đa ngôn ngữ (tuỳ chọn - nếu không có sẽ tự động fallback về title & message gốc)
            val titleEn = root.optString("title_en", "").ifBlank { null }
            val messageEn = root.optString("message_en", "").ifBlank { null }
            val actionTextEn = root.optString("action_text_en", "").ifBlank { null }

            val titleVi = root.optString("title_vi", "").ifBlank { null } ?: title.ifBlank { null }
            val messageVi = root.optString("message_vi", "").ifBlank { null } ?: message.ifBlank { null }
            val actionTextVi = root.optString("action_text_vi", "").ifBlank { null } ?: actionText

            RemoteNotice(
                active = active,
                level = level,
                title = title,
                message = message,
                blockActions = blockActions,
                actionText = actionText,
                actionUrl = actionUrl,
                titleEn = titleEn,
                messageEn = messageEn,
                actionTextEn = actionTextEn,
                titleVi = titleVi,
                messageVi = messageVi,
                actionTextVi = actionTextVi
            )
        } catch (_: Exception) {
            null
        }
    }
}
