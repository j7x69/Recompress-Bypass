package com.recompress.bypass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

enum class ShadowbanStatus(val labelEn: String, val labelVi: String) {
    NOT_SHADOW_BAN("Not Shadow Ban", "Không bị shadowban"),
    SHADOW_BAN_WARNING("Shadow Ban Warning", "Cảnh báo shadowban"),
    SHADOW_BANNED("Shadow Banned", "Bị shadowban")
}

data class DiagnosisEntry(
    val icon: String, // "🟢", "🟡", "🔴", "🟠"
    val textEn: String,
    val textVi: String
)

data class TikTokAuditResult(
    val videoId: String,
    val elapsedSec: Double,
    val authorName: String,
    val authorUsername: String,
    val authorAvatarBitmap: Bitmap?,
    val authorAvatarUrl: String?,
    val desc: String,
    val coverBitmap: Bitmap?,
    val coverUrl: String?,
    val createTimeFormattedEn: String,
    val createTimeFormattedVi: String,
    val locationCreated: String,
    val hashtags: List<String>,

    // Video Specifications
    val width: Int,
    val height: Int,
    val definition: String, // 2160p, 1440p, 1080p, 720p, 540p, 360p
    val ratio: String, // 9:16, 16:9, etc.
    val durationSec: Int,
    val durationFormatted: String, // "0:54"
    val fps: Double,
    val isDownscaled: Boolean,
    val bitrateBps: Long,
    val bitrateFormatted: String,
    val fileSizeMb: Double,
    val codecType: String,
    val format: String,
    val dynamicRange: String, // "SDR" or "HDR"

    // Stepped Ladder Resolution Index (0 = 360p, 1 = 540p, 2 = 720p, 3 = 1080p, 4 = 2K, 5 = 4K)
    val activeResolutionTier: Int,
    val resolutionTierName: String, // "540p", "1080p", "4K", etc.

    // Score percentages (0f .. 1f)
    val resolutionScore: Float,
    val resolutionLabelEn: String,
    val resolutionLabelVi: String,
    val fpsScore: Float,
    val fpsLabelEn: String,
    val fpsLabelVi: String,
    val bitrateScore: Float,
    val bitrateLabelEn: String,
    val bitrateLabelVi: String,
    val codecScore: Float,
    val codecLabelEn: String,
    val codecLabelVi: String,

    // RecompressBypass signature check
    val isBypassOptimized: Boolean,

    // Engagement stats
    val playCount: Long,
    val diggCount: Long,
    val commentCount: Long,
    val shareCount: Long,
    val collectCount: Long,
    val downloadCount: Long,
    val engagementRatePercent: Double,

    // Shadowban & Distribution restrictions
    val isPrivate: Boolean,
    val duetEnabled: Boolean,
    val stitchEnabled: Boolean,
    val warnings: List<String>,
    val soundStatusEn: String,
    val soundStatusVi: String,
    val isSoundMuted: Boolean,
    val soundTitle: String,
    val shadowbanStatus: ShadowbanStatus,
    val shadowbanDiagnosis: List<DiagnosisEntry>
)

object TikTokLinkAuditor {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Trích xuất URL hợp lệ từ chuỗi văn bản bất kỳ
     */
    fun extractUrl(text: String): String? {
        val pattern = Pattern.compile("https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(0) else null
    }

    /**
     * Phân tích kiểm tra toàn diện video TikTok từ link kèm cập nhật tiến trình
     */
    suspend fun auditTikTokUrl(
        rawUrlOrText: String,
        onProgress: (statusEn: String, statusVi: String) -> Unit = { _, _ -> }
    ): Result<TikTokAuditResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val urlStr = extractUrl(rawUrlOrText)
                ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy đường link TikTok hợp lệ / Valid TikTok link not found."))

            // 1. Theo dõi chuyển hướng (Redirect resolution) để lấy URL chính xác
            onProgress("Resolving TikTok link & redirects...", "Đang phân giải link & chuyển hướng video...")
            val resolvedUrl = resolveFinalUrl(urlStr)

            // Security M-2: Validate final domain must be TikTok after redirect resolution
            val resolvedHost = try { URL(resolvedUrl).host.lowercase() } catch (_: Exception) { "" }
            if (!isTikTokDomain(resolvedHost)) {
                return@withContext Result.failure(
                    SecurityException("Resolved URL is not a TikTok domain: $resolvedHost")
                )
            }

            // 2. Tải mã nguồn HTML của trang video
            onProgress("Fetching HTML & stream manifest from TikTok CDN...", "Đang kết nối CDN & tải manifest từ TikTok...")
            val html = fetchHtml(resolvedUrl)

            // 3. Trích xuất JSON __UNIVERSAL_DATA_FOR_REHYDRATION__
            onProgress("Extracting TikTok video metadata...", "Đang trích xuất dữ liệu video TikTok...")
            val jsonStr = extractRehydrationJson(html)
                ?: return@withContext Result.failure(IllegalStateException("Không đọc được dữ liệu TikTok. Video có thể ở chế độ riêng tư, đã bị xóa hoặc IP bị giới hạn tạm thời."))

            val rootJson = JSONObject(jsonStr)
            val defaultScope = rootJson.optJSONObject("__DEFAULT_SCOPE__")
                ?: return@withContext Result.failure(IllegalStateException("Cấu trúc phản hồi của TikTok đã thay đổi / TikTok response structure changed."))

            val videoDetail = defaultScope.optJSONObject("webapp.video-detail")
            val statusCode = videoDetail?.optInt("statusCode", 0) ?: 0
            if (statusCode != 0) {
                val statusMsg = videoDetail?.optString("statusMsg", "Video không khả dụng") ?: "Video unavailable"
                return@withContext Result.failure(IllegalStateException("TikTok error ($statusCode): $statusMsg"))
            }

            val itemStruct = videoDetail?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                ?: return@withContext Result.failure(IllegalStateException("Không tìm thấy thông tin video trong dữ liệu TikTok."))

            onProgress("Parsing resolution, codec & server bitrate...", "Đang giải mã độ phân giải, codec & bitrate...")
            val authorObj = itemStruct.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "Anonymous") ?: "Anonymous"
            val authorUsername = authorObj?.optString("uniqueId", "") ?: ""
            val authorAvatarUrl = authorObj?.optString("avatarThumb", "")?.takeIf { it.isNotBlank() }
            val desc = itemStruct.optString("desc", "")

            val videoObj = itemStruct.optJSONObject("video") ?: JSONObject()
            val videoId = itemStruct.optString("id", "")
            val width = videoObj.optInt("width", 1080)
            val height = videoObj.optInt("height", 1920)
            val definition = videoObj.optString("definition", "1080p")
            val durationSec = videoObj.optInt("duration", 0)
            val format = videoObj.optString("format", "mp4")
            val codecType = videoObj.optString("codecType", "h264")
            val coverUrl = videoObj.optString("cover", "").takeIf { it.isNotBlank() }

            val playAddrStruct = videoObj.optJSONObject("PlayAddrStruct")

            // Tính toán dung lượng file chính xác (Bytes)
            val sizeBytes = videoObj.optLong("size", 0L).takeIf { it > 0 }
                ?: playAddrStruct?.optLong("DataSize", 0L).takeIf { (it ?: 0L) > 0 }
                ?: playAddrStruct?.optString("DataSize")?.toLongOrNull() ?: 0L
            val fileSizeMb = if (sizeBytes > 0) sizeBytes / (1024.0 * 1024.0) else 0.0

            // Tính toán chính xác Bitrate (nếu video.bitrate = 0 thì tính từ DataSize / duration)
            var bitrateBps = videoObj.optLong("bitrate", 0L)
            if (bitrateBps <= 0L && sizeBytes > 0 && durationSec > 0) {
                bitrateBps = (sizeBytes * 8L) / durationSec
            }

            val bitrateFormatted = when {
                bitrateBps >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", bitrateBps / 1_000_000.0)
                bitrateBps > 0 -> String.format(Locale.US, "%d kbps", bitrateBps / 1_000)
                else -> "2.0 Mbps"
            }

            // Thời lượng MM:SS
            val mins = durationSec / 60
            val secs = durationSec % 60
            val durationFormatted = String.format(Locale.US, "%d:%02d", mins, secs)

            // Ngày đăng & Vị trí
            val createTimeUnix = itemStruct.optLong("createTime", 0L)
            val (createTimeFormattedEn, createTimeFormattedVi) = if (createTimeUnix > 0) {
                val date = Date(createTimeUnix * 1000L)
                val sdfEn = SimpleDateFormat("HH:mm MMM d, yyyy", Locale.US)
                val sdfVi = SimpleDateFormat("HH:mm 'ngày' d 'thg' M, yyyy", Locale.forLanguageTag("vi-VN"))
                Pair(sdfEn.format(date), sdfVi.format(date))
            } else {
                Pair("Recent", "Gần đây")
            }

            val rawLocation = itemStruct.optString("locationCreated", "").uppercase()
            val locationCreated = when (rawLocation) {
                "VN" -> "Việt Nam"
                "US" -> "Hoa Kỳ"
                "ID" -> "Indonesia"
                "TH" -> "Thái Lan"
                "PH" -> "Philippines"
                "MY" -> "Malaysia"
                "JP" -> "Nhật Bản"
                "KR" -> "Hàn Quốc"
                "BR" -> "Brazil"
                else -> if (rawLocation.isNotBlank()) rawLocation else "Toàn cầu"
            }

            // Hashtags
            val hashtags = mutableListOf<String>()
            val textExtraArr = itemStruct.optJSONArray("textExtra")
            if (textExtraArr != null) {
                for (i in 0 until textExtraArr.length()) {
                    val hName = textExtraArr.optJSONObject(i)?.optString("hashtagName", "")
                    if (!hName.isNullOrBlank()) hashtags.add("#$hName")
                }
            }

            // Phân loại thang đo phân giải bậc thang (360p, 540p, 720p, 1080p, 2K, 4K)
            val maxDim = maxOf(width, height)
            val minDim = minOf(width, height)
            val defLower = definition.lowercase()

            val activeResolutionTier = when {
                maxDim >= 3840 || minDim >= 2160 || defLower.contains("2160") || defLower.contains("4k") -> 5 // 4K
                maxDim >= 2560 || minDim >= 1440 || defLower.contains("1440") || defLower.contains("2k") -> 4 // 2K
                maxDim >= 1920 || minDim >= 1080 || defLower.contains("1080") -> 3 // 1080p
                maxDim >= 1280 || minDim >= 720 || defLower.contains("720") -> 2 // 720p
                maxDim >= 960 || minDim >= 540 || defLower.contains("540") -> 1 // 540p
                else -> 0 // 360p
            }

            val tierNames = listOf("360p", "540p", "720p", "1080p", "2K", "4K")
            val resolutionTierName = tierNames[activeResolutionTier]
            val isDownscaled = activeResolutionTier < 3 // Dưới 1080p là bị TikTok nén hạ chất lượng

            val resolutionScore = when (activeResolutionTier) {
                5 -> 1.00f
                4 -> 0.90f
                3 -> 0.80f
                2 -> 0.55f
                1 -> 0.35f
                else -> 0.20f
            }

            val resolutionLabelEn = when (activeResolutionTier) {
                5 -> "4K Ultra HD (2160p)"
                4 -> "2K Quad HD (1440p)"
                3 -> "1080p Full HD"
                2 -> "720p HD (Compressed)"
                1 -> "540p SD (Sub-optimal)"
                else -> "360p Low Definition"
            }

            val resolutionLabelVi = when (activeResolutionTier) {
                5 -> "4K Ultra HD (2160p)"
                4 -> "2K Quad HD (1440p)"
                3 -> "1080p Full HD Chuẩn"
                2 -> "720p HD (Bị nén nhẹ)"
                1 -> "540p SD (Hạ chất lượng)"
                else -> "360p Chất lượng thấp"
            }

            // Tỉ lệ khung hình
            val gcdVal = gcd(width, height)
            val rw = width / gcdVal
            val rh = height / gcdVal
            val ratio = if ((rw in 8..10 && rh in 15..17) || (width == 1080 && height == 1920) || (width == 2160 && height == 3840)) {
                "9:16 (Dọc)"
            } else if ((rw in 15..17 && rh in 8..10) || (width == 1920 && height == 1080) || (width == 3840 && height == 2160)) {
                "16:9 (Ngang)"
            } else if (rw == rh || width == height) {
                "1:1 (Vuông)"
            } else {
                "$rw:$rh"
            }

            // Đo FPS thực tế
            onProgress("Probing hardware frame rate (FPS) via Play URL...", "Đang đo đạc tốc độ khung hình (FPS) từ Play URL...")
            var detectedFps = 30.0
            val urlList = playAddrStruct?.optJSONArray("UrlList")
            val directPlayUrl = if (urlList != null && urlList.length() > 0) {
                urlList.optString(0)
            } else {
                videoObj.optString("playAddr")
            }

            if (directPlayUrl.isNotBlank()) {
                detectedFps = probeFpsFromPlayUrl(directPlayUrl, durationSec)
            }

            val fpsScore = ((detectedFps / 60.0).coerceIn(0.1, 1.0)).toFloat()
            val fpsLabelEn = when {
                detectedFps >= 58.0 -> "Ultra Smooth (60 FPS)"
                detectedFps >= 48.0 -> "High Smoothness (50 FPS)"
                detectedFps >= 28.0 -> "Standard Playback (30 FPS)"
                detectedFps >= 23.0 -> "Cinematic Motion (24 FPS)"
                else -> "Low Frame Rate (Lag Warning)"
            }
            val fpsLabelVi = when {
                detectedFps >= 58.0 -> "Cực mượt mà (60 FPS)"
                detectedFps >= 48.0 -> "Khá mượt mà (50 FPS)"
                detectedFps >= 28.0 -> "Chuẩn phát sóng (30 FPS)"
                detectedFps >= 23.0 -> "Chuẩn điện ảnh (24 FPS)"
                else -> "FPS thấp (Cảnh báo giật lag)"
            }

            // Bitrate Score
            val bitrateScore = when {
                bitrateBps >= 15_000_000 -> 1.00f
                bitrateBps >= 8_000_000 -> 0.90f
                bitrateBps >= 4_000_000 -> 0.75f
                bitrateBps >= 2_000_000 -> 0.55f
                bitrateBps >= 1_000_000 -> 0.40f
                bitrateBps > 0 -> 0.25f
                else -> 0.50f
            }

            val bitrateLabelEn = when {
                bitrateBps >= 15_000_000 -> "Master Quality Bitrate"
                bitrateBps >= 8_000_000 -> "Ultra High Bitrate"
                bitrateBps >= 4_000_000 -> "Optimal TikTok Bitrate"
                bitrateBps >= 2_000_000 -> "Standard Streaming Bitrate"
                bitrateBps >= 1_000_000 -> "Compressed Bitrate"
                bitrateBps > 0 -> "Low Bitrate (Pixelation Risk)"
                else -> "Dynamic Stream Bitrate"
            }

            val bitrateLabelVi = when {
                bitrateBps >= 15_000_000 -> "Băng thông Master cực cao"
                bitrateBps >= 8_000_000 -> "Băng thông siêu nét"
                bitrateBps >= 4_000_000 -> "Băng thông tối ưu TikTok"
                bitrateBps >= 2_000_000 -> "Băng thông streaming tiêu chuẩn"
                bitrateBps >= 1_000_000 -> "Băng thông bị nén nhẹ"
                bitrateBps > 0 -> "Băng thông thấp (Dễ vỡ hạt)"
                else -> "Băng thông máy chủ linh hoạt"
            }

            // Bộ mã hóa Codec
            val codecClean = codecType.lowercase()
            val (codecScore, codecLabelEn, codecLabelVi) = when {
                codecClean.contains("hevc") || codecClean.contains("h265") || codecClean.contains("bytevc1") || codecClean.contains("av1") -> {
                    Triple(1.00f, "Next-Gen Compression (HEVC/ByteVC1)", "Nén thế hệ mới (HEVC/ByteVC1)")
                }
                codecClean.contains("h264") || codecClean.contains("avc") -> {
                    Triple(0.65f, "Standard Compatibility (AVC/H.264)", "Chuẩn tương thích phổ thông (H.264)")
                }
                else -> {
                    Triple(0.50f, "Standard Codec", "Bộ mã hóa tiêu chuẩn")
                }
            }

            // Stats
            val statsObj = itemStruct.optJSONObject("stats") ?: JSONObject()
            val playCount = statsObj.optLong("playCount", 0L)
            val diggCount = statsObj.optLong("diggCount", 0L)
            val commentCount = statsObj.optLong("commentCount", 0L)
            val shareCount = statsObj.optLong("shareCount", 0L)
            val collectCount = statsObj.optLong("collectCount", 0L)
            val downloadCount = statsObj.optLong("downloadCount", 0L)

            val totalInteractions = diggCount + commentCount + shareCount + collectCount
            val engagementRatePercent = if (playCount > 0) {
                (totalInteractions.toDouble() / playCount.toDouble()) * 100.0
            } else 0.0

            // Cờ hạn chế & Shadowban
            val isPrivate = itemStruct.optBoolean("privateItem", false)
            val duetEnabled = itemStruct.optBoolean("duetEnabled", true)
            val stitchEnabled = itemStruct.optBoolean("stitchEnabled", true)

            val warnings = mutableListOf<String>()
            val warnInfoArr = itemStruct.optJSONArray("warnInfo")
            if (warnInfoArr != null) {
                for (i in 0 until warnInfoArr.length()) {
                    val w = warnInfoArr.optString(i)
                    if (w.isNotBlank()) warnings.add(w)
                }
            }

            val musicObj = itemStruct.optJSONObject("music")
            val soundStatusRaw = musicObj?.optInt("status", 1) ?: 1
            val soundTitle = musicObj?.optString("title", "Original Sound") ?: "Original Sound"
            val isSoundMuted = soundStatusRaw == 0
            val soundStatusEn = if (isSoundMuted) "Muted / Copyright Infringement" else "Active / Normal"
            val soundStatusVi = if (isSoundMuted) "Bị tắt tiếng / Dính bản quyền" else "Hoạt động bình thường"

            // Đánh giá điểm rủi ro Shadowban
            onProgress("Analyzing engagement & Shadowban diagnostics...", "Đang phân tích tương tác & rủi ro Shadowban...")
            val diagnoses = mutableListOf<DiagnosisEntry>()
            var riskScore = 0

            if (isPrivate) {
                riskScore += 4
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🔴",
                        textEn = "Video is Private or hidden from public recommendations.",
                        textVi = "Video đang ở chế độ Riêng tư (Private) hoặc bị ẩn khỏi đề xuất công khai."
                    )
                )
            }

            if (isSoundMuted) {
                riskScore += 3
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🔴",
                        textEn = "Audio is MUTED or flagged for Copyright infringement (Major cause of 0-view FYP lock).",
                        textVi = "Âm thanh dính bản quyền hoặc bị MUTE tiếng (Nguyên nhân hàng đầu khiến video 0 view For You)."
                    )
                )
            }

            if (warnings.isNotEmpty()) {
                riskScore += 3
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🔴",
                        textEn = "Video flagged with policy warnings: ${warnings.joinToString(", ")}.",
                        textVi = "Video dính cờ cảnh báo vi phạm chính sách: ${warnings.joinToString(", ")}."
                    )
                )
            }

            if (!duetEnabled && !stitchEnabled) {
                riskScore += 1
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟡",
                        textEn = "Duet & Stitch are disabled (Could be restricted by account settings or platform throttle).",
                        textVi = "Tính năng Duet & Stitch bị tắt (Có thể do cài đặt hoặc bị hạn chế tương tác)."
                    )
                )
            }

            if (isDownscaled) {
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟡",
                        textEn = "TikTok downscaled video stream to $definition (${width}x${height}). Below 1080p full clarity.",
                        textVi = "TikTok đã nén hạ độ phân giải xuống $definition (${width}x${height}). Chưa đạt chuẩn 1080p nét căng."
                    )
                )
            } else {
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟢",
                        textEn = "High Definition stream preserved (${width}x${height}) with no downscaling detected.",
                        textVi = "Độ phân giải Full HD/4K (${width}x${height}) được giữ nguyên, không bị hạ chất lượng."
                    )
                )
            }

            if (detectedFps >= 55.0) {
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟢",
                        textEn = "Ultra-smooth 60 FPS playback detected (${String.format(Locale.US, "%.1f", detectedFps)} fps).",
                        textVi = "Tốc độ khung hình 60 FPS (${String.format(Locale.US, "%.1f", detectedFps)} fps) cực kỳ mượt mà."
                    )
                )
            } else if (detectedFps >= 28.0) {
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟢",
                        textEn = "Standard 30 FPS playback detected (${String.format(Locale.US, "%.1f", detectedFps)} fps).",
                        textVi = "Tốc độ khung hình 30 FPS (${String.format(Locale.US, "%.1f", detectedFps)} fps) chuẩn phát sóng."
                    )
                )
            } else {
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟡",
                        textEn = "Low frame rate (${String.format(Locale.US, "%.1f", detectedFps)} fps); video might stutter on mobile playback.",
                        textVi = "FPS thấp (${String.format(Locale.US, "%.1f", detectedFps)} fps), video có thể bị giật hình khi phát."
                    )
                )
            }

            if (playCount in 1..200 && engagementRatePercent > 20.0) {
                riskScore += 2
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟠",
                        textEn = "Possible 200-view FYP Jail: High interaction (${String.format(Locale.US, "%.1f", engagementRatePercent)}%) but views stalled under 200.",
                        textVi = "Dấu hiệu kẹt phân phối: Tỉ lệ tương tác cao (${String.format(Locale.US, "%.1f", engagementRatePercent)}%) nhưng view bị kẹt dưới 200 view."
                    )
                )
            } else if (playCount > 1000) {
                diagnoses.add(
                    DiagnosisEntry(
                        icon = "🟢",
                        textEn = "Healthy For You feed distribution active ($playCount views).",
                        textVi = "Video đã được phân phối tự nhiên lên For You ($playCount lượt xem)."
                    )
                )
            }

            val shadowbanStatus = when {
                riskScore >= 3 -> ShadowbanStatus.SHADOW_BANNED
                riskScore in 1..2 -> ShadowbanStatus.SHADOW_BAN_WARNING
                else -> ShadowbanStatus.NOT_SHADOW_BAN
            }

            // Tải Thumbnail và Avatar chạy ngầm
            onProgress("Fetching video cover & author profile...", "Đang tải ảnh bìa & kênh tác giả...")
            val coverBitmap = fetchBitmapSafely(coverUrl)
            val authorAvatarBitmap = fetchBitmapSafely(authorAvatarUrl)

            onProgress("Finalizing report...", "Đang hoàn tất kết xuất báo cáo...")
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0

            Result.success(
                TikTokAuditResult(
                    videoId = videoId,
                    elapsedSec = elapsedSec,
                    authorName = authorName,
                    authorUsername = authorUsername,
                    authorAvatarBitmap = authorAvatarBitmap,
                    authorAvatarUrl = authorAvatarUrl,
                    desc = desc,
                    coverBitmap = coverBitmap,
                    coverUrl = coverUrl,
                    createTimeFormattedEn = createTimeFormattedEn,
                    createTimeFormattedVi = createTimeFormattedVi,
                    locationCreated = locationCreated,
                    hashtags = hashtags,
                    width = width,
                    height = height,
                    definition = definition,
                    ratio = ratio,
                    durationSec = durationSec,
                    durationFormatted = durationFormatted,
                    fps = detectedFps,
                    isDownscaled = isDownscaled,
                    bitrateBps = bitrateBps,
                    bitrateFormatted = bitrateFormatted,
                    fileSizeMb = fileSizeMb,
                    codecType = codecType,
                    format = format,
                    dynamicRange = "SDR",
                    activeResolutionTier = activeResolutionTier,
                    resolutionTierName = resolutionTierName,
                    resolutionScore = resolutionScore,
                    resolutionLabelEn = resolutionLabelEn,
                    resolutionLabelVi = resolutionLabelVi,
                    fpsScore = fpsScore,
                    fpsLabelEn = fpsLabelEn,
                    fpsLabelVi = fpsLabelVi,
                    bitrateScore = bitrateScore,
                    bitrateLabelEn = bitrateLabelEn,
                    bitrateLabelVi = bitrateLabelVi,
                    codecScore = codecScore,
                    codecLabelEn = codecLabelEn,
                    codecLabelVi = codecLabelVi,
                    isBypassOptimized = false, // File tiêu chuẩn
                    playCount = playCount,
                    diggCount = diggCount,
                    commentCount = commentCount,
                    shareCount = shareCount,
                    collectCount = collectCount,
                    downloadCount = downloadCount,
                    engagementRatePercent = engagementRatePercent,
                    isPrivate = isPrivate,
                    duetEnabled = duetEnabled,
                    stitchEnabled = stitchEnabled,
                    warnings = warnings,
                    soundStatusEn = soundStatusEn,
                    soundStatusVi = soundStatusVi,
                    isSoundMuted = isSoundMuted,
                    soundTitle = soundTitle,
                    shadowbanStatus = shadowbanStatus,
                    shadowbanDiagnosis = diagnoses
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchBitmapSafely(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        // Security M-1: Only allow HTTPS image URLs to prevent SSRF via file:// or http:// injection
        val parsed = try { URL(imageUrl) } catch (_: Exception) { return null }
        if (parsed.protocol != "https") return null
        return try {
            val conn = parsed.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    // Allowed TikTok CDN domains for redirect resolution (Security M-2)
    private val ALLOWED_TIKTOK_DOMAINS = setOf(
        "tiktok.com", "tiktokv.com", "byteoversea.com",
        "ibytedtos.com", "tiktokcdn.com", "muscdn.com",
        "vm.tiktok.com", "vt.tiktok.com"
    )

    private fun isTikTokDomain(host: String): Boolean {
        val lower = host.lowercase()
        return ALLOWED_TIKTOK_DOMAINS.any { lower == it || lower.endsWith(".$it") }
    }

    private fun resolveFinalUrl(startUrl: String): String {
        var current = startUrl
        var redirects = 0
        while (redirects < 5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.connect()

            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) {
                    current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                    redirects++
                    conn.disconnect()
                    continue
                }
            }
            conn.disconnect()
            break
        }
        return current
    }

    private fun fetchHtml(targetUrl: String): String {
        val conn = URL(targetUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
        conn.connectTimeout = 12000
        conn.readTimeout = 12000

        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun extractRehydrationJson(html: String): String? {
        val startNeedle = "id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\""
        val idx = html.findFirstOrNull(startNeedle) ?: return null
        val tagEnd = html.indexOf('>', idx)
        if (tagEnd == -1) return null
        val endIdx = html.indexOf("</script>", tagEnd)
        if (endIdx == -1) return null
        return html.substring(tagEnd + 1, endIdx).trim()
    }

    private fun String.findFirstOrNull(needle: String): Int? {
        val idx = this.indexOf(needle)
        return if (idx != -1) idx else null
    }

    private fun probeFpsFromPlayUrl(videoUrl: String, durationSec: Int): Double {
        return try {
            val retriever = MediaMetadataRetriever()
            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://www.tiktok.com/"
            )
            retriever.setDataSource(videoUrl, headers)

            var fps = 30.0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                val frameCount = frameCountStr?.toIntOrNull()
                if (frameCount != null && frameCount > 0 && durationSec > 0) {
                    fps = frameCount.toDouble() / durationSec.toDouble()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (fps <= 0 || fps == 30.0)) {
                val captureRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val captureRate = captureRateStr?.toDoubleOrNull()
                if (captureRate != null && captureRate > 0) {
                    fps = captureRate
                }
            }

            retriever.release()
            if (fps in 10.0..120.0) fps else 30.0
        } catch (_: Exception) {
            30.0
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return if (x != 0) x else 1
    }
}
