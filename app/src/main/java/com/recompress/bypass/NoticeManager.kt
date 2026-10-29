package com.recompress.bypass

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

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
            AppLanguage.VI -> titleVi?.ifBlank { null }
        }
    }

    fun getExplicitMessage(lang: AppLanguage): String? {
        return when (lang) {
            AppLanguage.EN -> messageEn?.ifBlank { null }
            AppLanguage.VI -> messageVi?.ifBlank { null }
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

object NoticeTranslator {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun translateText(text: String, fromLang: String, toLang: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || fromLang.equals(toLang, ignoreCase = true)) return@withContext text
        val cacheKey = "$fromLang->$toLang::$text"
        cache[cacheKey]?.let { return@withContext it }

        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$fromLang|$toLang"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "RecompressBypass-Android")
            }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val responseData = root.optJSONObject("responseData")
                val translated = responseData?.optString("translatedText", "") ?: ""
                if (translated.isNotBlank() && !translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
                    val cleaned = Html.fromHtml(translated, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    cache[cacheKey] = cleaned
                    return@withContext cleaned
                }
            }
        } catch (_: Exception) {
            // Fallback gracefully to original
        }
        return@withContext text
    }

    suspend fun resolveNoticeContent(
        notice: RemoteNotice,
        targetLang: AppLanguage
    ): Pair<String, String> {
        val explicitTitle = notice.getExplicitTitle(targetLang)
        val explicitMsg = notice.getExplicitMessage(targetLang)

        if (explicitTitle != null && explicitMsg != null) {
            return Pair(explicitTitle, explicitMsg)
        }

        val fromLang = if (targetLang == AppLanguage.EN) "vi" else "en"
        val toLang = if (targetLang == AppLanguage.EN) "en" else "vi"

        val resolvedTitle = explicitTitle ?: translateText(notice.title, fromLang, toLang)
        val resolvedMsg = explicitMsg ?: if (notice.message.isNotBlank()) {
            translateText(notice.message, fromLang, toLang)
        } else ""

        return Pair(resolvedTitle, resolvedMsg)
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

            val titleEn = root.optString("title_en", "").ifBlank { null }
            val messageEn = root.optString("message_en", "").ifBlank { null }
            val actionTextEn = root.optString("action_text_en", "").ifBlank { null }
            val titleVi = root.optString("title_vi", "").ifBlank { null }
            val messageVi = root.optString("message_vi", "").ifBlank { null }
            val actionTextVi = root.optString("action_text_vi", "").ifBlank { null }

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
