package com.recompress.bypass

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(
    val code: String,
    val title: String,
    val nativeName: String
) {
    EN(
        code = "en",
        title = "English",
        nativeName = "English"
    ),
    VI(
        code = "vi",
        title = "Vietnamese",
        nativeName = "Tiếng Việt"
    );

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code.equalsIgnoreCase(code) } ?: EN
        }
    }
}

private fun String.equalsIgnoreCase(other: String?): Boolean =
    this.equals(other, ignoreCase = true)

object LanguagePreferences {
    private const val PREFS_NAME = "app_lang_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    fun getLanguage(context: Context): AppLanguage {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = sp.getString(KEY_LANGUAGE, AppLanguage.EN.code)
        return AppLanguage.fromCode(saved)
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_LANGUAGE, language.code).apply()
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

data class AppStringsData(
    val appTitle: String,
    val appSubtitle: String,
    val optCardTitle: String,
    val optCardSub: String,
    val uploadCardTitle: String,
    val uploadCardSub: String,
    val recentProjects: String,
    val videosOptimized: String,
    val noVideosYet: String,
    val tips: List<Pair<String, String>>,
    val settingsTitle: String,
    val authorGithub: String,
    val openGithub: String,
    val copy: String,
    val copiedToast: String,
    val appVersion: String,
    val currentVersion: String,
    val newUpdateBadge: String,
    val checkUpdate: String,
    val checking: String,
    val languageTitle: String,
    val languageDesc: String,
    val appearanceTitle: String,
    val appearanceDesc: String,
    val engineV4Title: String,
    val playProtectTitle: String,
    val playProtectDesc: String,
    val viewGuide: String,
    val close: String,
    val newUpdateBannerTitle: String,
    val newUpdateBannerSub: String,
    val openBrowserError: String,
    val defaultActionText: String,
    val translateToggle: String,
    val originalToggle: String,
    val amoledTitle: String,
    val amoledDesc: String,
    val midnightTitle: String,
    val midnightDesc: String,
    val charcoalTitle: String,
    val charcoalDesc: String,
    val cyberpunkTitle: String,
    val cyberpunkDesc: String,
    val emeraldTitle: String,
    val emeraldDesc: String,
    val customWallpaperTitle: String,
    val customWallpaperDesc: String,
    val advancedThemeBtn: String,
    val advancedThemeTitle: String,
    val advancedThemeDesc: String,
    val pickWallpaperBtn: String,
    val changeWallpaperBtn: String,
    val removeWallpaperBtn: String,
    val wallpaperDimLabel: String,
    val cardOpacityLabel: String,
    val accentColorLabel: String,
    val resetThemeBtn: String,
    val applyThemeBtn: String,
    val noWallpaperSelected: String,
    val playProtectDialogTitle: String,
    val playProtectWhyTitle: String,
    val playProtectWhyContent: String,
    val playProtectHowTitle: String,
    val playProtectHowContent: String,
    val playProtectGotIt: String,
    val logCompressingTitle: String,
    val logFailedTitle: String,
    val logSuccessTitle: String,
    val logSendDiscord: String,
    val logSendingDiscord: String,
    val logSentDiscord: String,
    val logPostTikTok: String,
    val logToastDiscordSuccess: String,
    val logToastDiscordFail: String,
    val historyProjectPrefix: String,
    val historyStoragePath: String,
    val historyViewAllLogs: String,
    val historyPlayVideo: String,
    val historyCopyLogs: String,
    val historyCopiedLogs: String,
    val updateDialogTitle: String,
    val updateReleaseNotes: String,
    val updateDownloading: String,
    val updateLater: String,
    val updateNow: String,
    val updateError: String,
    val engineFeatures: String
)

object AppStrings {
    val EN = AppStringsData(
        appTitle = "VIDEO OPTIMIZER",
        appSubtitle = "Lossless Video Quality Optimizer",
        optCardTitle = "Optimize video",
        optCardSub = "Fix compression blur",
        uploadCardTitle = "Upload video",
        uploadCardSub = "TikTok Studio",
        recentProjects = "Recent projects",
        videosOptimized = "%d videos optimized",
        noVideosYet = "No videos processed yet",
        tips = listOf(
            Pair("💡 Optimal Export Specs", "Render at 1080x1920 (9:16) 60 FPS for maximum social media clarity."),
            Pair("⚡ Target Bitrate", "Keep bitrate under 15 Mbps to prevent platforms from heavy compression."),
            Pair("🚀 TikTok Studio Upload", "Use the 'Upload video' button to post via Studio without mobile app re-encoding."),
            Pair("🎬 Lossless Passthrough", "Preserves 100% video stream data without lossy pixel transcoding."),
            Pair("⚡ FastStart Container", "Positions metadata at the front for instant streaming without buffering.")
        ),
        settingsTitle = "Settings & About",
        authorGithub = "Author GitHub",
        openGithub = "Open GitHub ↗",
        copy = "Copy",
        copiedToast = "Copied GitHub link!",
        appVersion = "App Version",
        currentVersion = "Current version: v%s",
        newUpdateBadge = "New update!",
        checkUpdate = "Check",
        checking = "Checking...",
        languageTitle = "Language / Ngôn ngữ",
        languageDesc = "Select application display language",
        appearanceTitle = "Appearance Theme",
        appearanceDesc = "Choose your preferred visual theme",
        engineV4Title = "Video Optimization Engine",
        playProtectTitle = "Play Protect & APK Security Guide",
        playProtectDesc = "Learn how to bypass false Google Play Protect warnings when sideloading APK.",
        viewGuide = "View Guide ↗",
        close = "Close",
        newUpdateBannerTitle = "New update: %s",
        newUpdateBannerSub = "Tap to view changelog and update (%s)",
        openBrowserError = "Cannot open browser",
        defaultActionText = "View Details",
        translateToggle = "Translate",
        originalToggle = "Original",
        amoledTitle = "Dark AMOLED",
        amoledDesc = "0-nit pitch black, maximum battery saving, absolute contrast",
        midnightTitle = "Midnight Obsidian",
        midnightDesc = "Deep obsidian night with oceanic navy tint, eye-friendly",
        charcoalTitle = "Minimalist Charcoal",
        charcoalDesc = "Neutral ergonomic dark charcoal minimalist aesthetic",
        cyberpunkTitle = "Cyberpunk Neon",
        cyberpunkDesc = "Electric violet & cyber neon pink high-contrast aesthetic",
        emeraldTitle = "Deep Emerald",
        emeraldDesc = "Mystic emerald jade and cooling mint elegance",
        customWallpaperTitle = "Custom Wallpaper & Glass",
        customWallpaperDesc = "Personal background photo with translucent glass cards",
        advancedThemeBtn = "Advance: Custom UI & Glass ⚙️",
        advancedThemeTitle = "Advanced Theme Customizer",
        advancedThemeDesc = "Upload custom wallpaper, tune card transparency & pick accent colors",
        pickWallpaperBtn = "Select Wallpaper Photo",
        changeWallpaperBtn = "Change Photo",
        removeWallpaperBtn = "Remove Photo",
        wallpaperDimLabel = "Wallpaper Darken / Dim",
        cardOpacityLabel = "Card Glass Opacity",
        accentColorLabel = "Accent Color",
        resetThemeBtn = "Reset Defaults",
        applyThemeBtn = "Save & Apply",
        noWallpaperSelected = "No custom wallpaper selected yet",
        playProtectDialogTitle = "Disable APK Scan Warnings",
        playProtectWhyTitle = "Why does the scan prompt appear?",
        playProtectWhyContent = "Because this APK is sideloaded outside Google Play, Google Play Protect or OEM security scanners (Xiaomi/Samsung/Oppo) automatically prompt before allowing installation.",
        playProtectHowTitle = "How to bypass permanently:",
        playProtectHowContent = "1. In Google Play Store (Play Protect):\n• Open Google Play ➔ Tap Profile avatar on top right.\n• Tap Play Protect ➔ Tap Gear icon ⚙️ on top right.\n• Turn OFF: 'Improve harmful app detection'.\n\n2. For Xiaomi / HyperOS / MIUI:\n• Open Security app ➔ Settings ⚙️ ➔ Security scan.\n• Turn off: 'Scan before installing'.\n\n3. In-App Updates:\n• Updates downloaded directly inside this app are safe and will overwrite seamlessly.",
        playProtectGotIt = "Got it",
        logCompressingTitle = "Optimizing video...",
        logFailedTitle = "Optimization Failed",
        logSuccessTitle = "Optimization Complete!",
        logSendDiscord = "Report to Discord",
        logSendingDiscord = "Sending...",
        logSentDiscord = "Sent to Discord",
        logPostTikTok = "Post to TikTok",
        logToastDiscordSuccess = "Sent error log to Discord successfully!",
        logToastDiscordFail = "Cannot connect to Discord (please retry)",
        historyProjectPrefix = "Project: %s",
        historyStoragePath = "Saved path:",
        historyViewAllLogs = "View full processing logs",
        historyPlayVideo = "Play Video",
        historyCopyLogs = "Copy",
        historyCopiedLogs = "Copied all logs to clipboard!",
        updateDialogTitle = "New update available!",
        updateReleaseNotes = "Release notes:",
        updateDownloading = "Downloading APK...",
        updateLater = "Later",
        updateNow = "Update Now",
        updateError = "Download failed. Please try again later.",
        engineFeatures = "• FastStart Streaming: Repositions container metadata (moov) to the front for instant playback\n" +
                "• Lossless Passthrough: Preserves 100% original video stream data without quality degradation\n" +
                "• Stream Cleanup: Completely purges corrupted and incompatible metadata tracks\n" +
                "• Audio Stream Enhancement: Boosts audio fidelity and sample synchronization\n" +
                "• Ingestion Compatibility: Optimizes native container tags for social platform algorithms"
    )

    val VI = AppStringsData(
        appTitle = "TỐI ƯU VIDEO",
        appSubtitle = "Tối ưu & Giữ nguyên chất lượng video gốc",
        optCardTitle = "Tối ưu video",
        optCardSub = "Khắc phục nén mờ",
        uploadCardTitle = "Đăng video",
        uploadCardSub = "TikTok Studio",
        recentProjects = "Dự án gần đây",
        videosOptimized = "%d video đã tối ưu",
        noVideosYet = "Chưa có video nào được tối ưu",
        tips = listOf(
            Pair("💡 Thông số xuất chuẩn", "Nên xuất video ở 1080x1920 (9:16) 60 FPS để đạt độ nét cao nhất trên mạng xã hội."),
            Pair("⚡ Giữ Bitrate vừa phải", "Nên giữ bitrate dưới 15 Mbps để thuật toán nén của nền tảng không can thiệp làm mờ."),
            Pair("🚀 Đăng qua TikTok Studio", "Dùng nút 'Đăng video' trong app để đăng qua giao diện Studio, giữ trọn độ nét gốc."),
            Pair("🎬 Bảo toàn 100% nguyên bản", "Giữ nguyên luồng hình ảnh & âm thanh gốc, không giải mã nén lại gây suy hao."),
            Pair("⚡ Cấu trúc FastStart", "Tối ưu vị trí dữ liệu lên đầu file giúp video phát ngay tức thì không cần đợi đệm.")
        ),
        settingsTitle = "Cài đặt & Giới thiệu",
        authorGithub = "GitHub Tác Giả",
        openGithub = "Mở GitHub ↗",
        copy = "Sao chép",
        copiedToast = "Đã sao chép link GitHub!",
        appVersion = "Phiên bản ứng dụng",
        currentVersion = "Phiên bản hiện tại: v%s",
        newUpdateBadge = "Có bản mới!",
        checkUpdate = "Kiểm tra",
        checking = "Đang kiểm tra...",
        languageTitle = "Ngôn ngữ / Language",
        languageDesc = "Chọn ngôn ngữ hiển thị ứng dụng",
        appearanceTitle = "Chế độ Giao diện",
        appearanceDesc = "Chọn tông màu giao diện bạn thích",
        engineV4Title = "Cơ chế Tối ưu Video (Engine V4)",
        playProtectTitle = "Trợ Giúp Quét APK & Play Protect",
        playProtectDesc = "Hướng dẫn vượt qua cảnh báo sai của Google Play Protect khi cài file APK ngoài.",
        viewGuide = "Xem hướng dẫn ↗",
        close = "Đóng",
        newUpdateBannerTitle = "Bản cập nhật mới: %s",
        newUpdateBannerSub = "Chạm để xem ghi chú và cập nhật (%s)",
        openBrowserError = "Không thể mở trình duyệt",
        defaultActionText = "Xem chi tiết",
        translateToggle = "Bản dịch",
        originalToggle = "Bản gốc",
        amoledTitle = "Dark AMOLED",
        amoledDesc = "Đen tuyền 0-nit, tiết kiệm pin tối đa, độ tương phản tuyệt đối",
        midnightTitle = "Midnight Obsidian",
        midnightDesc = "Sắc đêm huyền bí pha xanh than sâu thẳm, dịu mắt",
        charcoalTitle = "Minimalist Charcoal",
        charcoalDesc = "Xám than tối giản trung tính chuẩn phong cách công thái học",
        cyberpunkTitle = "Cyberpunk Neon",
        cyberpunkDesc = "Sắc tím neon kết hợp hồng điện tử cá tính & tương phản cao",
        emeraldTitle = "Deep Emerald",
        emeraldDesc = "Tông ngọc lục bảo huyền bí, thanh lịch và dịu mát",
        customWallpaperTitle = "Ảnh nền tự chọn & Glass",
        customWallpaperDesc = "Tự chọn ảnh nền từ máy, tùy chỉnh độ trong suốt kính mờ",
        advancedThemeBtn = "Nâng cao: Tùy biến UI & Glass ⚙️",
        advancedThemeTitle = "Tùy Biến Giao Diện Nâng Cao",
        advancedThemeDesc = "Tải ảnh nền cá nhân, chỉnh độ mờ thẻ kính và màu sắc",
        pickWallpaperBtn = "Chọn ảnh từ máy làm hình nền",
        changeWallpaperBtn = "Đổi ảnh khác",
        removeWallpaperBtn = "Gỡ ảnh nền",
        wallpaperDimLabel = "Độ tối ảnh nền",
        cardOpacityLabel = "Độ trong suốt thẻ (Glass)",
        accentColorLabel = "Màu sắc điểm nhấn",
        resetThemeBtn = "Đặt lại mặc định",
        applyThemeBtn = "Lưu & Áp dụng",
        noWallpaperSelected = "Chưa chọn ảnh nền cá nhân nào",
        playProtectDialogTitle = "Mẹo tắt cảnh báo quét APK",
        playProtectWhyTitle = "Tại sao lại có thông báo quét?",
        playProtectWhyContent = "Do APK cài ngoài Google Play (sideload), Google Play Protect hoặc trình quét bảo mật của máy (Xiaomi/Samsung/Oppo) sẽ tự động hiện thông báo hỏi quét trước khi cho cài.",
        playProtectHowTitle = "Cách tắt vĩnh viễn (Không bao giờ hỏi lại):",
        playProtectHowContent = "1. Tắt trong CH Play (Google Play Protect):\n• Mở app CH Play ➔ Bấm vào Ảnh đại diện góc trên phải.\n• Chọn Play Protect ➔ Bấm icon Bánh răng ⚙️ ở góc phải.\n• TẮT mục: 'Cải thiện khả năng phát hiện ứng dụng độc hại'.\n\n2. Đối với máy Xiaomi / HyperOS / MIUI:\n• Mở app Bảo mật ➔ Bánh răng ⚙️ ➔ Quét bảo mật.\n• Tắt: 'Quét trước khi cài đặt'.\n\n3. Tính năng Tự động Cập nhật trong app:\n• Cập nhật ngay trong app sẽ tải từ GitHub Release và cài đặt đè trực tiếp.",
        playProtectGotIt = "Đã hiểu",
        logCompressingTitle = "Đang tối ưu video...",
        logFailedTitle = "Tối ưu thất bại",
        logSuccessTitle = "Tối ưu hoàn tất!",
        logSendDiscord = "Gửi lỗi Discord",
        logSendingDiscord = "Đang gửi...",
        logSentDiscord = "Đã gửi Discord",
        logPostTikTok = "Đăng TikTok",
        logToastDiscordSuccess = "Đã gửi toàn bộ log lỗi lên Discord thành công!",
        logToastDiscordFail = "Không thể kết nối Discord (vui lòng thử lại)",
        historyProjectPrefix = "Dự án: %s",
        historyStoragePath = "Đường dẫn lưu:",
        historyViewAllLogs = "Xem toàn bộ nhật ký xử lý",
        historyPlayVideo = "Phát video",
        historyCopyLogs = "Sao chép",
        historyCopiedLogs = "Đã sao chép toàn bộ log!",
        updateDialogTitle = "Bản cập nhật mới!",
        updateReleaseNotes = "Ghi chú phát hành:",
        updateDownloading = "Đang tải APK về máy...",
        updateLater = "Để sau",
        updateNow = "Cập nhật ngay",
        updateError = "Lỗi khi tải file. Vui lòng thử lại sau.",
        engineFeatures = "• Cấu trúc FastStart: Đưa metadata container lên đầu giúp video phát ngay tức thì\n" +
                "• Bảo toàn nguyên gốc: Giữ nguyên 100% dữ liệu luồng video, không suy hao điểm ảnh\n" +
                "• Dọn dẹp dữ liệu rác: Loại bỏ triệt để các track lỗi gây cấm đăng hoặc giảm nét\n" +
                "• Tối ưu âm thanh: Chuẩn hóa mẫu âm thanh và luồng phát cho độ chi tiết cao nhất\n" +
                "• Tương thích nền tảng: Tối ưu thẻ nạp nội bộ tương thích tối đa với mạng xã hội"
    )

    fun forLanguage(language: AppLanguage): AppStringsData {
        return when (language) {
            AppLanguage.EN -> EN
            AppLanguage.VI -> VI
        }
    }
}
