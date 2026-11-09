package com.recompress.bypass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

data class VideoMetadata(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val sizeMb: String,
    val resolution: String,
    val durationSec: String,
    val thumbnail: Bitmap?
)

object SponsorPromptManager {
    const val GITHUB_SPONSOR_URL = "https://github.com/sponsors/j7x69"
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_OPT_COUNT = "sponsor_opt_success_count"
    private const val KEY_NEXT_PROMPT_AT = "sponsor_next_prompt_at"
    private const val KEY_LATER_COUNT = "sponsor_later_count"
    private const val KEY_SPONSOR_CLICKED = "sponsor_already_clicked"

    /**
     * Điều phối hiển thị popup Sponsor thông minh (không spam người dùng):
     * - Lần đầu tiên chạy fix video thành công (count == 1): hiện popup.
     * - Nếu ấn "Later" lần 1: hẹn hiển thị lại ở lần fix thứ 5 (cách 4 video).
     * - Nếu ấn "Later" lần 2: hẹn hiển thị lại ở lần fix thứ 11 (cách 6 video).
     * - Nếu ấn "Later" các lần tiếp theo: cứ mỗi 10 lần fix thành công mới hỏi lại 1 lần (21, 31, 41...).
     * - Nếu người dùng đã bấm nút Sponsor ủng hộ: không tự động hiện popup nữa.
     */
    fun onVideoOptimizeSuccess(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_SPONSOR_CLICKED, false)) {
            return false
        }
        val count = sp.getInt(KEY_OPT_COUNT, 0) + 1
        val nextPromptAt = sp.getInt(KEY_NEXT_PROMPT_AT, 1)
        val shouldPrompt = count >= nextPromptAt

        sp.edit {
            putInt(KEY_OPT_COUNT, count)
            if (shouldPrompt) {
                val laterCount = sp.getInt(KEY_LATER_COUNT, 0) + 1
                val step = when (laterCount) {
                    1 -> 4   // Hiển thị lại ở lần thứ 5
                    2 -> 6   // Hiển thị lại ở lần thứ 11
                    else -> 10 // Các lần sau cách mỗi 10 video
                }
                putInt(KEY_LATER_COUNT, laterCount)
                putInt(KEY_NEXT_PROMPT_AT, count + step)
            }
        }
        return shouldPrompt
    }

    fun markSponsorClicked(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit { putBoolean(KEY_SPONSOR_CLICKED, true) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bảo vệ chống crash do bug nội bộ Compose AndroidComposeView với chuột / Device Mirroring hover event
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is IllegalStateException && throwable.message?.contains("ACTION_HOVER_EXIT") == true) {
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var currentAppearance by remember {
                mutableStateOf(AppearancePreferences.getAppearance(context))
            }
            var currentLanguage by remember {
                mutableStateOf(LanguagePreferences.getLanguage(context))
            }
            var customThemeConfig by remember {
                mutableStateOf(AppearancePreferences.getCustomConfig(context))
            }

            val appColors = remember(currentAppearance, customThemeConfig) {
                ThemePalette.forAppearance(currentAppearance, customThemeConfig)
            }

            CompositionLocalProvider(
                LocalAppColors provides appColors,
                LocalAppLanguage provides currentLanguage
            ) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = appColors.background,
                        surface = appColors.surface
                    )
                ) {
                    MinimalistOptimizerApp(
                        currentAppearance = currentAppearance,
                        onAppearanceChange = { newApp ->
                            currentAppearance = newApp
                            AppearancePreferences.saveAppearance(context, newApp)
                        },
                        currentLanguage = currentLanguage,
                        onLanguageChange = { newLang ->
                            currentLanguage = newLang
                            LanguagePreferences.saveLanguage(context, newLang)
                        },
                        customThemeConfig = customThemeConfig,
                        onCustomThemeConfigChange = { newCfg ->
                            customThemeConfig = newCfg
                            AppearancePreferences.saveCustomConfig(context, newCfg)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalistOptimizerApp(
    currentAppearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    customThemeConfig: CustomThemeConfig,
    onCustomThemeConfigChange: (CustomThemeConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current
    val strings = AppStrings.forLanguage(currentLanguage)

    // Quản lý lịch sử nén
    var historyList by remember { mutableStateOf(emptyList<CompressedVideoRecord>()) }

    // Quản lý cập nhật GitHub Release
    val currentAppVersion = remember { UpdateManager.getAppVersion(context) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    // SharedPreferences lưu trữ cài đặt & trạng thái thông báo
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    // Quản lý thông báo từ xa (Remote Notice / Kill-Switch từ GitHub)
    var remoteNotice by remember { mutableStateOf<RemoteNotice?>(null) }
    var isNoticeVisible by remember { mutableStateOf(true) }
    var emergencyUnlock by remember { mutableStateOf(false) }
    val isActionBlocked = (remoteNotice?.blockActions == true) && !emergencyUnlock

    // Quản lý các Modal Dialogs
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showAdvancedThemeDialog by remember { mutableStateOf(false) }
    var showTikTokAuditDialog by remember { mutableStateOf(false) }
    var showSponsorDialog by remember { mutableStateOf(false) }

    var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(customThemeConfig.wallpaperPath) {
        val path = customThemeConfig.wallpaperPath
        wallpaperBitmap = if (path != null && File(path).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }

    LaunchedEffect(Unit) {
        // Tự động dọn dẹp các tệp tạm cũ và thumbnail mồ côi
        launch(Dispatchers.IO) {
            cleanLegacyTempFiles(context)
            val currentRecords = HistoryRepository.loadHistory(context)
            val activeThumbs = currentRecords.mapNotNull { it.thumbnailFileName }.toSet()
            HistoryRepository.cleanOrphanThumbnails(context, activeThumbs)
        }

        historyList = HistoryRepository.loadHistory(context)
        // Tự động kiểm tra bản cập nhật mới trên GitHub Release & hiển thị dialog ngay khi có
        launch {
            val update = UpdateManager.checkUpdate(context)
            if (update != null && update.hasUpdate) {
                availableUpdate = update
                showUpdateDialog = true
            }
        }
        // Kiểm tra thông báo từ xa từ file notice.json trên GitHub
        launch {
            val notice = NoticeManager.fetchNotice()
            remoteNotice = notice
            if (notice != null && !notice.blockActions) {
                val dismissedNoticeKey = prefs.getString("dismissed_notice_key", null)
                val currentNoticeKey = "${notice.title}__${notice.message}"
                if (dismissedNoticeKey == currentNoticeKey) {
                    isNoticeVisible = false
                }
            }
        }
    }

    // Trạng thái Live Log Dialog khi đang tối ưu
    var isCompressing by remember { mutableStateOf(false) }
    var currentCompressLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentCompressStatus by remember {
        mutableStateOf(
            if (currentLanguage == AppLanguage.EN) "Preparing..." else "Chuẩn bị xử lý..."
        )
    }
    var currentSavedRecord by remember { mutableStateOf<CompressedVideoRecord?>(null) }
    var currentProcessingVideoMeta by remember { mutableStateOf<VideoMetadata?>(null) }
    var showLiveLogDialog by remember { mutableStateOf(false) }

    // Trạng thái Dialog xem chi tiết Video & Log từ Lịch sử
    var selectedHistoryRecord by remember { mutableStateOf<CompressedVideoRecord?>(null) }

    // Animated background color for smooth theme transitions
    val animatedBg by animateColorAsState(
        targetValue = appColors.background,
        animationSpec = tween(250),
        label = "bgColor"
    )

    // Bộ chọn Video MP4 cho tác vụ "Tối ưu video"
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                showLiveLogDialog = true
                isCompressing = true
                currentCompressLogs = listOf(
                    if (currentLanguage == AppLanguage.EN) "[START] Reading video..."
                    else "[START] Đang đọc video..."
                )
                currentCompressStatus =
                    if (currentLanguage == AppLanguage.EN) "Reading video..." else "Đang đọc video..."
                currentSavedRecord = null

                val meta = extractVideoMetadata(context, uri)
                currentProcessingVideoMeta = meta
                val newLogs = currentCompressLogs.toMutableList()

                val savedRecord = processAndOptimizeVideo(
                    context = context,
                    media = meta,
                    historyIndex = historyList.size,
                    language = currentLanguage,
                    onLog = { logLine ->
                        newLogs.add(logLine)
                        currentCompressLogs = newLogs.toList()
                        when {
                            logLine.startsWith("[PROCESS]") -> currentCompressStatus =
                                if (currentLanguage == AppLanguage.EN) "Optimizing video..." else "Đang tối ưu video..."

                            logLine.startsWith("[SUCCESS]") -> currentCompressStatus =
                                if (currentLanguage == AppLanguage.EN) "Optimization complete!" else "Hoàn tất tối ưu video!"
                        }
                    }
                )

                isCompressing = false
                currentSavedRecord = savedRecord
                if (savedRecord != null) {
                    historyList = HistoryRepository.loadHistory(context)
                    if (SponsorPromptManager.onVideoOptimizeSuccess(context)) {
                        kotlinx.coroutines.delay(450L)
                        showSponsorDialog = true
                    }
                }
            }
        }
    }

    run {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (currentAppearance == AppAppearance.CUSTOM && wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = customThemeConfig.wallpaperDim))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedBg)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // ================= TOP BAR MINIMALIST CAO CẤP =================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(appColors.accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.appTitle,
                                color = appColors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.appSubtitle,
                            color = appColors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nút Bánh răng (Settings) cố định kích thước, luôn hiển thị đầy đủ trên mọi thiết bị
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appColors.surface)
                            .border(1.dp, appColors.border, RoundedCornerShape(10.dp))
                            .clickable { showSettingsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = appColors.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                        if (availableUpdate?.hasUpdate == true) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(appColors.warning)
                            )
                        }
                    }
                }

                // ================= BANNER BẢN CẬP NHẬT MỚI GITHUB RELEASE =================
                val update = availableUpdate
                if (update != null && update.hasUpdate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(appColors.warningSoft)
                            .border(
                                1.dp,
                                appColors.warning.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { showUpdateDialog = true }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(appColors.warning.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = appColors.warning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = String.format(strings.newUpdateBannerTitle, update.tagName),
                                        color = appColors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(strings.newUpdateBannerSub, update.apkSizeMb),
                                        color = appColors.accentText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = appColors.warning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ================= BẢNG THÔNG BÁO TỪ XA NẾU CÓ =================
                val notice = remoteNotice
                // Tránh dính / trùng lặp: nếu đã có banner update ở trên và notice cũng là thông báo cập nhật (info) thì ẩn notice để tránh hiển thị 2 thông báo update cùng lúc
                val isDuplicateUpdateNotice = (update != null && update.hasUpdate) &&
                        (notice?.level == "info") &&
                        (notice.title.contains("bản mới", ignoreCase = true) ||
                         notice.title.contains("update", ignoreCase = true) ||
                         notice.message.contains("update", ignoreCase = true))

                if (notice != null && notice.active && isNoticeVisible && !isDuplicateUpdateNotice) {
                    val noticeColor = when (notice.level) {
                        "error", "danger" -> appColors.danger
                        "warning" -> appColors.warning
                        else -> appColors.accent
                    }
                    val noticeBg = when (notice.level) {
                        "error", "danger" -> appColors.dangerSoft
                        "warning" -> appColors.warningSoft
                        else -> appColors.accentSoft
                    }

                    val displayTitle = notice.getTitleForLang(currentLanguage)
                    val displayMessage = notice.getMessageForLang(currentLanguage)
                    val displayActionText = notice.getActionTextForLang(currentLanguage)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(noticeBg)
                            .border(1.dp, noticeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (notice.level) {
                                        "error", "danger" -> Icons.Default.Error
                                        "warning" -> Icons.Default.Warning
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = noticeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = displayTitle,
                                    color = appColors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                if (!notice.blockActions || emergencyUnlock) {
                                    IconButton(
                                        onClick = {
                                            isNoticeVisible = false
                                            prefs.edit {
                                                putString(
                                                    "dismissed_notice_key",
                                                    "${notice.title}__${notice.message}"
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.close,
                                            tint = appColors.textMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            if (displayMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = displayMessage,
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            // Khôi phục nút action_url khi notice có link đính kèm
                            if (!notice.actionUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent =
                                                Intent(Intent.ACTION_VIEW, notice.actionUrl.toUri())
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, strings.openBrowserError, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = noticeColor,
                                        contentColor = if (noticeColor == appColors.warning) Color.Black else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = displayActionText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ================= 2 THẺ HÀNH ĐỘNG CHÍNH (MINIMALIST BENTO) =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // NÚT TRÁI: "Tối ưu video"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(168.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(appColors.surface)
                            .border(
                                width = 1.dp,
                                color = appColors.border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                if (availableUpdate?.hasUpdate == true && !emergencyUnlock) {
                                    showUpdateDialog = true
                                } else if (isActionBlocked) {
                                    val blockedText = remoteNotice?.let { n ->
                                        val t = n.getTitleForLang(currentLanguage)
                                        val m = n.getMessageForLang(currentLanguage)
                                        if (m.isNotBlank()) "${strings.featureBlockedPrefix}: $t\n$m"
                                        else "${strings.featureBlockedPrefix}: $t"
                                    } ?: strings.featureBlockedPrefix
                                    Toast.makeText(
                                        context,
                                        blockedText,
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    videoPicker.launch("video/mp4")
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Badge phía trên
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .clip(CircleShape)
                                        .background(appColors.accentSoft)
                                        .border(
                                            0.7.dp,
                                            appColors.accent.copy(alpha = 0.5f),
                                            CircleShape
                                        )
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = "HD",
                                        color = appColors.accentText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        style = LocalTextStyle.current.copy(
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false
                                            ),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.Both
                                            )
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Icon container
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.surfaceElevated)
                                    .border(1.dp, appColors.borderFocus, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = strings.optCardTitle,
                                    tint = appColors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tiêu đề & phụ đề
                            Text(
                                text = strings.optCardTitle,
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.optCardSub,
                                color = appColors.accentText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // NÚT PHẢI: "Đăng video" / "TikTok Studio"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(168.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(appColors.surface)
                            .border(
                                width = 1.dp,
                                color = appColors.border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                openTikTokStudioInBrowser(context, strings)
                            }
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Badge phía trên
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .clip(CircleShape)
                                        .background(appColors.successSoft)
                                        .border(
                                            0.7.dp,
                                            appColors.success.copy(alpha = 0.5f),
                                            CircleShape
                                        )
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = "STUDIO",
                                        color = appColors.success,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        style = LocalTextStyle.current.copy(
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false
                                            ),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.Both
                                            )
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Icon container
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.surfaceElevated)
                                    .border(1.dp, appColors.borderFocus, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = strings.uploadCardTitle,
                                    tint = appColors.success,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tiêu đề & phụ đề
                            Text(
                                text = strings.uploadCardTitle,
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.uploadCardSub,
                                color = appColors.success,
                                fontSize = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ================= NÚT SOI LINK TIKTOK (BẢN TEST) =================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(appColors.surface)
                        .border(1.dp, appColors.border, RoundedCornerShape(20.dp))
                        .clickable { showTikTokAuditDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(appColors.accentSoft)
                                .border(
                                    1.dp,
                                    appColors.accent.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (currentLanguage == AppLanguage.EN) "Inspect TikTok Link" else "Soi Link TikTok",
                                    color = appColors.textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(appColors.accent.copy(alpha = 0.2f))
                                        .border(0.7.dp, appColors.accent, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "TEST",
                                        color = appColors.accentText,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (currentLanguage == AppLanguage.EN) "Check FPS, Resolution, Bitrate & Shadowban Risk" else "Check FPS, Resolution, Bitrate & Rủi ro Shadowban",
                                color = appColors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ================= DỰ ÁN GẦN ĐÂY =================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.recentProjects,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                    if (historyList.isNotEmpty()) {
                        Text(
                            text = String.format(strings.videosOptimized, historyList.size),
                            fontSize = 11.sp,
                            color = appColors.textMuted
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp)
                ) {
                    items(historyList) { item ->
                        AmoledHistoryVideoCard(
                            record = item,
                            onClick = { selectedHistoryRecord = item }
                        )
                    }

                    val placeholderCount = max(0, 4 - historyList.size)
                    items(placeholderCount) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(appColors.surface)
                                .border(
                                    width = 1.dp,
                                    color = appColors.borderSubtle,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = appColors.textMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ================= CARD MẸO SỬ DỤNG HỮU ÍCH (ROTATING TIPS - 5S) =================
                val tips = strings.tips
                var currentTipIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(tips) {
                    while (true) {
                        kotlinx.coroutines.delay(5000L)
                        if (tips.isNotEmpty()) {
                            currentTipIndex = (currentTipIndex + 1) % tips.size
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(appColors.surface)
                        .border(1.dp, appColors.border, RoundedCornerShape(18.dp))
                        .clickable {
                            if (tips.isNotEmpty()) {
                                currentTipIndex = (currentTipIndex + 1) % tips.size
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(appColors.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            AnimatedContent(
                                targetState = currentTipIndex,
                                transitionSpec = {
                                    (slideInVertically { height -> height / 2 } + fadeIn(tween(350))) togetherWith
                                        (slideOutVertically { height -> -height / 2 } + fadeOut(tween(250)))
                                },
                                label = "tip_transition"
                            ) { index ->
                                val tip = tips.getOrElse(index) { Pair("", "") }
                                Column {
                                    Text(
                                        text = tip.first,
                                        color = appColors.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tip.second,
                                        color = appColors.textMuted,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        if (tips.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(currentTipIndex % tips.size) + 1}/${tips.size}",
                                color = appColors.textMuted.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // ================= MODAL CÀI ĐẶT & GIỚI THIỆU (BÁNH RĂNG) =================
    if (showSettingsDialog) {
        AppSettingsDialog(
            currentAppVersion = currentAppVersion,
            availableUpdate = availableUpdate,
            isCheckingUpdate = isCheckingUpdate,
            currentAppearance = currentAppearance,
            currentLanguage = currentLanguage,
            emergencyUnlock = emergencyUnlock,
            onEmergencyUnlockChange = { emergencyUnlock = it },
            onAppearanceSelect = onAppearanceChange,
            onLanguageChange = onLanguageChange,
            onCheckUpdate = {
                if (!isCheckingUpdate) {
                    isCheckingUpdate = true
                    scope.launch {
                        val update = UpdateManager.checkUpdate(context)
                        isCheckingUpdate = false
                        if (update != null && update.hasUpdate) {
                            availableUpdate = update
                            showUpdateDialog = true
                        } else {
                            val msg = if (currentLanguage == AppLanguage.EN) {
                                "Using the latest version (v$currentAppVersion)"
                            } else {
                                "Đang dùng phiên bản mới nhất (v$currentAppVersion)"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onOpenUpdateDialog = {
                showUpdateDialog = true
            },
            onOpenAdvancedTheme = {
                showAdvancedThemeDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // ================= MODAL TÙY BIẾN GIAO DIỆN NÂNG CAO =================
    if (showAdvancedThemeDialog) {
        AdvancedThemeCustomizerDialog(
            currentConfig = customThemeConfig,
            onSaveConfig = { newCfg ->
                onCustomThemeConfigChange(newCfg)
            },
            onSelectAppearance = onAppearanceChange,
            strings = strings,
            onDismiss = { showAdvancedThemeDialog = false }
        )
    }

    // ================= MODAL SOI LINK TIKTOK (BẢN TEST) =================
    if (showTikTokAuditDialog) {
        TikTokAuditDialog(
            onDismiss = { showTikTokAuditDialog = false }
        )
    }

    // ================= MODAL CHỌN GIAO DIỆN NHANH (APPEARANCE) =================
    if (showAppearanceDialog) {
        AppearanceSelectionDialog(
            currentAppearance = currentAppearance,
            strings = strings,
            onSelect = {
                onAppearanceChange(it)
                showAppearanceDialog = false
            },
            onDismiss = { showAppearanceDialog = false }
        )
    }

    // ================= MODAL LIVE LOG KHI ĐANG NÉN =================
    if (showLiveLogDialog) {
        LiveLogDialog(
            isCompressing = isCompressing,
            statusText = currentCompressStatus,
            logs = currentCompressLogs,
            savedRecord = currentSavedRecord,
            videoMeta = currentProcessingVideoMeta,
            strings = strings,
            onDismiss = { showLiveLogDialog = false },
            onOpenTikTokStudio = {
                showLiveLogDialog = false
                openTikTokStudioInBrowser(context, strings)
            }
        )
    }

    // ================= MODAL CHI TIẾT VIDEO TỪ LỊCH SỬ =================
    selectedHistoryRecord?.let { record ->
        HistoryDetailDialog(
            record = record,
            strings = strings,
            onDismiss = { selectedHistoryRecord = null },
            onOpenTikTokStudio = {
                selectedHistoryRecord = null
                openTikTokStudioInBrowser(context, strings)
            }
        )
    }

    // ================= MODAL CẬP NHẬT BẮT BUỘC GITHUB RELEASE =================
    val isForceUpdate = (availableUpdate?.hasUpdate == true) && !emergencyUnlock
    if ((showUpdateDialog || isForceUpdate) && availableUpdate != null) {
        AppUpdateDialog(
            updateInfo = availableUpdate!!,
            strings = strings,
            isForceUpdate = isForceUpdate,
            onDismiss = {
                if (!isForceUpdate) {
                    showUpdateDialog = false
                }
            }
        )
    }

    // ================= MODAL ỦNG HỘ TÁC GIẢ (GITHUB SPONSORS) =================
    if (showSponsorDialog) {
        SponsorPromptDialog(
            strings = strings,
            onDismiss = { showSponsorDialog = false },
            onSponsorClick = {
                SponsorPromptManager.markSponsorClicked(context)
                showSponsorDialog = false
                try {
                    val intent =
                        Intent(Intent.ACTION_VIEW, SponsorPromptManager.GITHUB_SPONSOR_URL.toUri())
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, strings.openBrowserError, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ================= THẺ VIDEO LỊCH SỬ DARK AMOLED =================
@Composable
fun AmoledHistoryVideoCard(
    record: CompressedVideoRecord,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val thumbnailBitmap = remember(record.id) {
        HistoryRepository.loadThumbnail(context, record.thumbnailFileName)
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.surface)
            .border(
                1.dp,
                appColors.border,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                            startY = 40f
                        )
                    )
            )
        } else {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = appColors.textMuted.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(appColors.accent)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = record.displayName,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ================= MODAL CÀI ĐẶT & THÔNG TIN (BÁNH RĂNG) =================
@Composable
fun AppSettingsDialog(
    currentAppVersion: String,
    availableUpdate: AppUpdateInfo?,
    isCheckingUpdate: Boolean,
    currentAppearance: AppAppearance,
    currentLanguage: AppLanguage,
    emergencyUnlock: Boolean,
    onEmergencyUnlockChange: (Boolean) -> Unit,
    onAppearanceSelect: (AppAppearance) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenAdvancedTheme: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val strings = AppStrings.forLanguage(currentLanguage)
    var versionTapCount by remember { mutableIntStateOf(0) }
    val githubRepoUrl = "https://github.com/j7x69/Recompress-Bypass"

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .border(1.dp, appColors.border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(appColors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.settingsTitle,
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = appColors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // CARD 1: GitHub Của Tác Giả
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(appColors.surfaceElevated)
                                .border(1.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = appColors.accentText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.authorGithub,
                                            color = appColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "@j7x69",
                                        color = appColors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "github.com/j7x69/Recompress-Bypass",
                                    color = appColors.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    githubRepoUrl.toUri()
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, strings.openBrowserError, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                                        border = BorderStroke(1.dp, appColors.borderFocus),
                                        contentPadding = PaddingValues(horizontal = 9.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.openGithub, fontSize = 10.5.sp, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Repo", githubRepoUrl))
                                            Toast.makeText(context, strings.copiedToast, Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textSecondary),
                                        border = BorderStroke(1.dp, appColors.borderSubtle),
                                        contentPadding = PaddingValues(horizontal = 9.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.copy, fontSize = 10.5.sp, maxLines = 1)
                                    }

                                    val sponsorPink = Color(0xFFEA4AAA)
                                    Button(
                                        onClick = {
                                            SponsorPromptManager.markSponsorClicked(context)
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    SponsorPromptManager.GITHUB_SPONSOR_URL.toUri()
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, strings.openBrowserError, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = sponsorPink.copy(alpha = 0.16f),
                                            contentColor = sponsorPink
                                        ),
                                        border = BorderStroke(1.dp, sponsorPink.copy(alpha = 0.65f)),
                                        contentPadding = PaddingValues(horizontal = 9.dp)
                                    ) {
                                        Icon(Icons.Default.Favorite, contentDescription = null, tint = sponsorPink, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.sponsorMeBtn, color = sponsorPink, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    // CARD 2: Phiên Bản Ứng Dụng & Cập Nhật
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(appColors.surfaceElevated)
                                .border(1.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.clickable {
                                            versionTapCount++
                                            if (versionTapCount >= 5 && !emergencyUnlock) {
                                                onEmergencyUnlockChange(true)
                                                val unlockMsg = if (currentLanguage == AppLanguage.EN) {
                                                    "🔓 Emergency maintenance mode unlocked!"
                                                } else {
                                                    "🔓 Đã mở khóa bảo trì khẩn cấp!"
                                                }
                                                Toast.makeText(context, unlockMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = strings.appVersion,
                                            color = appColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(strings.currentVersion, currentAppVersion),
                                            color = appColors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (availableUpdate != null && availableUpdate.hasUpdate) {
                                        Button(
                                            onClick = onOpenUpdateDialog,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = appColors.warning),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(strings.newUpdateBadge, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = onCheckUpdate,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                                            border = BorderStroke(1.dp, appColors.borderFocus),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            if (isCheckingUpdate) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(11.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = appColors.accent
                                                )
                                            } else {
                                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.checkUpdate, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CARD 3: Cài đặt Ngôn Ngữ (Language Selection)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(appColors.surfaceElevated)
                                .border(1.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = strings.languageTitle,
                                    color = appColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.languageDesc,
                                    color = appColors.textMuted,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                AppLanguage.entries.forEach { langItem ->
                                    val isSelected = (currentLanguage == langItem)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) appColors.accentSoft else Color.Transparent)
                                            .clickable { onLanguageChange(langItem) }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = langItem.nativeName,
                                                color = if (isSelected) appColors.accentText else appColors.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = langItem.title,
                                                color = appColors.textMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = appColors.accent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    // CARD 4: Chế độ Giao diện (Appearance)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(appColors.surfaceElevated)
                                .border(1.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = strings.appearanceTitle,
                                    color = appColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.appearanceDesc,
                                    color = appColors.textMuted,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                AppAppearance.entries.forEach { appItem ->
                                    val isSelected = (currentAppearance == appItem)
                                    val title = when (appItem) {
                                        AppAppearance.AMOLED -> strings.amoledTitle
                                        AppAppearance.MIDNIGHT -> strings.midnightTitle
                                        AppAppearance.CHARCOAL -> strings.charcoalTitle
                                        AppAppearance.CYBERPUNK -> strings.cyberpunkTitle
                                        AppAppearance.EMERALD -> strings.emeraldTitle
                                        AppAppearance.CUSTOM -> strings.customWallpaperTitle
                                    }
                                    val desc = when (appItem) {
                                        AppAppearance.AMOLED -> strings.amoledDesc
                                        AppAppearance.MIDNIGHT -> strings.midnightDesc
                                        AppAppearance.CHARCOAL -> strings.charcoalDesc
                                        AppAppearance.CYBERPUNK -> strings.cyberpunkDesc
                                        AppAppearance.EMERALD -> strings.emeraldDesc
                                        AppAppearance.CUSTOM -> strings.customWallpaperDesc
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) appColors.accentSoft else Color.Transparent)
                                            .clickable {
                                                onAppearanceSelect(appItem)
                                                if (appItem == AppAppearance.CUSTOM) {
                                                    onOpenAdvancedTheme()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                color = if (isSelected) appColors.accentText else appColors.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = desc,
                                                color = appColors.textMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = appColors.accent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = onOpenAdvancedTheme,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = appColors.accentSoft),
                                    border = BorderStroke(1.dp, appColors.accent.copy(alpha = 0.5f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.advancedThemeBtn,
                                            color = appColors.accentText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.surfaceElevated),
                    border = BorderStroke(1.dp, appColors.border)
                ) {
                    Text(strings.close, color = appColors.textPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

// ================= MODAL CHỌN NHANH APPEARANCE =================
@Composable
fun AppearanceSelectionDialog(
    currentAppearance: AppAppearance,
    strings: AppStringsData,
    onSelect: (AppAppearance) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(appColors.surface)
                .border(1.dp, appColors.border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.appearanceTitle,
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.close, tint = appColors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AppAppearance.entries.forEach { item ->
                    val isSelected = (currentAppearance == item)
                    val title = when (item) {
                        AppAppearance.AMOLED -> strings.amoledTitle
                        AppAppearance.MIDNIGHT -> strings.midnightTitle
                        AppAppearance.CHARCOAL -> strings.charcoalTitle
                        AppAppearance.CYBERPUNK -> strings.cyberpunkTitle
                        AppAppearance.EMERALD -> strings.emeraldTitle
                        AppAppearance.CUSTOM -> strings.customWallpaperTitle
                    }
                    val desc = when (item) {
                        AppAppearance.AMOLED -> strings.amoledDesc
                        AppAppearance.MIDNIGHT -> strings.midnightDesc
                        AppAppearance.CHARCOAL -> strings.charcoalDesc
                        AppAppearance.CYBERPUNK -> strings.cyberpunkDesc
                        AppAppearance.EMERALD -> strings.emeraldDesc
                        AppAppearance.CUSTOM -> strings.customWallpaperDesc
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) appColors.accentSoft else appColors.surfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) appColors.accent else appColors.borderSubtle,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(item) }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = if (isSelected) appColors.accentText else appColors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = appColors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = appColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

// ================= MODAL TÙY BIẾN GIAO DIỆN NÂNG CAO (WALLPAPER & GLASS) =================
@Composable
fun AdvancedThemeCustomizerDialog(
    currentConfig: CustomThemeConfig,
    onSaveConfig: (CustomThemeConfig) -> Unit,
    onSelectAppearance: (AppAppearance) -> Unit,
    strings: AppStringsData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    var wallpaperPath by remember { mutableStateOf(currentConfig.wallpaperPath) }
    var wallpaperDim by remember { mutableFloatStateOf(currentConfig.wallpaperDim) }
    var cardOpacity by remember { mutableFloatStateOf(currentConfig.cardOpacity) }
    var selectedAccentHex by remember { mutableStateOf(currentConfig.accentHex) }

    var wallpaperBitmap by remember(wallpaperPath) {
        mutableStateOf<Bitmap?>(
            if (wallpaperPath != null && File(wallpaperPath!!).exists()) {
                try {
                    BitmapFactory.decodeFile(wallpaperPath)
                } catch (_: Exception) {
                    null
                }
            } else null
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = AppearancePreferences.saveWallpaperFromUri(context, uri)
            if (savedPath != null) {
                wallpaperPath = savedPath
                try {
                    wallpaperBitmap = BitmapFactory.decodeFile(savedPath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val presetColors = listOf(
        "#00E5FF", // Cyan
        "#00E676", // Mint Emerald
        "#FF007F", // Neon Pink
        "#B388FF", // Electric Violet
        "#FFD600", // Cyber Gold
        "#FF5252", // Sunset Crimson
        "#F5F5F7"  // Pure White
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .border(1.dp, appColors.border, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(appColors.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.advancedThemeTitle,
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.advancedThemeDesc,
                                color = appColors.textMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.close, tint = appColors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Wallpaper Image
                Text(
                    text = "1. ${strings.pickWallpaperBtn}",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (wallpaperBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                appColors.accent.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Image(
                            bitmap = wallpaperBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = wallpaperDim))
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(strings.changeWallpaperBtn, color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    AppearancePreferences.removeWallpaper(context)
                                    wallpaperPath = null
                                    wallpaperBitmap = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.danger.copy(alpha = 0.85f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(strings.removeWallpaperBtn, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = appColors.surfaceElevated),
                        border = BorderStroke(1.dp, appColors.borderSubtle)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(strings.pickWallpaperBtn, color = appColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(strings.noWallpaperSelected, color = appColors.textMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Wallpaper Dim Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. ${strings.wallpaperDimLabel}",
                        color = appColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(wallpaperDim * 100).toInt()}%",
                        color = appColors.accentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = wallpaperDim,
                    onValueChange = { wallpaperDim = it },
                    valueRange = 0.0f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = appColors.accent,
                        activeTrackColor = appColors.accent,
                        inactiveTrackColor = appColors.surfaceInput
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Section 3: Card Glass Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. ${strings.cardOpacityLabel}",
                        color = appColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(cardOpacity * 100).toInt()}% (Glass)",
                        color = appColors.accentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = cardOpacity,
                    onValueChange = { cardOpacity = it },
                    valueRange = 0.15f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = appColors.accent,
                        activeTrackColor = appColors.accent,
                        inactiveTrackColor = appColors.surfaceInput
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Section 4: Accent Color Presets
                Text(
                    text = "4. ${strings.accentColorLabel}",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    presetColors.forEach { hex ->
                        val col = Color(hex.toColorInt())
                        val isChosen = selectedAccentHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (isChosen) 2.5.dp else 1.dp,
                                    color = if (isChosen) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedAccentHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChosen) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = if (hex == "#F5F5F7") Color.Black else Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AppearancePreferences.removeWallpaper(context)
                            val reset = CustomThemeConfig()
                            wallpaperPath = null
                            wallpaperBitmap = null
                            wallpaperDim = reset.wallpaperDim
                            cardOpacity = reset.cardOpacity
                            selectedAccentHex = reset.accentHex
                            onSaveConfig(reset)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Text(strings.resetThemeBtn, fontSize = 11.5.sp)
                    }

                    Button(
                        onClick = {
                            val newConfig = CustomThemeConfig(
                                wallpaperPath = wallpaperPath,
                                wallpaperDim = wallpaperDim,
                                cardOpacity = cardOpacity,
                                accentHex = selectedAccentHex
                            )
                            onSaveConfig(newConfig)
                            onSelectAppearance(AppAppearance.CUSTOM)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.accentSoft),
                        border = BorderStroke(1.dp, appColors.accent)
                    ) {
                        Text(strings.applyThemeBtn, color = appColors.accentText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ================= MODAL LIVE LOG KHI TỐI ƯU =================
@Composable
fun LiveLogDialog(
    isCompressing: Boolean,
    statusText: String,
    logs: List<String>,
    savedRecord: CompressedVideoRecord?,
    videoMeta: VideoMetadata?,
    strings: AppStringsData,
    onDismiss: () -> Unit,
    onOpenTikTokStudio: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val appColors = LocalAppColors.current

    var isSendingDiscord by remember { mutableStateOf(false) }
    var discordSentSuccess by remember { mutableStateOf(false) }

    val hasError = logs.any { it.startsWith("[ERROR]") }
    val isFailed = !isCompressing && (savedRecord == null || hasError)

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = { if (!isCompressing) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isCompressing, dismissOnClickOutside = !isCompressing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .border(
                    1.dp,
                    if (isFailed) appColors.danger.copy(alpha = 0.5f) else appColors.border,
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isFailed -> appColors.dangerSoft
                                        isCompressing -> appColors.accentSoft
                                        else -> appColors.success.copy(alpha = 0.15f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isFailed -> Icons.Default.Error
                                    isCompressing -> Icons.Default.Terminal
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = when {
                                    isFailed -> appColors.danger
                                    isCompressing -> appColors.accent
                                    else -> appColors.success
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when {
                                isCompressing -> strings.logCompressingTitle
                                isFailed -> strings.logFailedTitle
                                else -> strings.logSuccessTitle
                            },
                            color = appColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isCompressing) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.close,
                                tint = appColors.textMuted
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCompressing) {
                        CircularProgressIndicator(
                            color = appColors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = if (isFailed) (logs.findLast { it.startsWith("[ERROR]") } ?: strings.logFailedTitle) else statusText,
                        color = when {
                            isCompressing -> appColors.accentText
                            isFailed -> appColors.danger
                            else -> appColors.success
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surfaceInput)
                        .border(
                            1.dp,
                            if (isFailed) appColors.danger.copy(alpha = 0.35f) else appColors.borderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(10.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { line ->
                            val color = when {
                                line.startsWith("[START]") -> appColors.accentText
                                line.startsWith("[PROCESS]") -> Color(0xFF4FC3F7)
                                line.startsWith("[ERROR]") -> appColors.danger
                                line.startsWith("[SUCCESS]") -> appColors.success
                                else -> appColors.textSecondary
                            }
                            Text(
                                text = line,
                                color = color,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isCompressing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                            border = BorderStroke(1.dp, appColors.border)
                        ) {
                            Text(strings.close, fontSize = 12.5.sp)
                        }

                        if (isFailed) {
                            // Nút gửi log lỗi trực tiếp lên Discord Webhook của tác giả
                            Button(
                                onClick = {
                                    if (!isSendingDiscord && !discordSentSuccess) {
                                        isSendingDiscord = true
                                        scope.launch {
                                            val lastErr = logs.findLast { it.startsWith("[ERROR]") } ?: "Unknown Error"
                                            val sent = DiscordWebhookManager.sendErrorLog(
                                                context = context,
                                                videoName = videoMeta?.name ?: "Video_Unknown",
                                                videoSizeMb = videoMeta?.sizeMb ?: "N/A",
                                                resolution = videoMeta?.resolution ?: "N/A",
                                                durationSec = videoMeta?.durationSec ?: "N/A",
                                                errorSummary = lastErr,
                                                logs = logs
                                            )
                                            isSendingDiscord = false
                                            discordSentSuccess = sent
                                            if (sent) {
                                                Toast.makeText(context, strings.logToastDiscordSuccess, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, strings.logToastDiscordFail, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (discordSentSuccess) appColors.success.copy(alpha = 0.2f) else appColors.danger.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, if (discordSentSuccess) appColors.success else appColors.danger)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSendingDiscord) {
                                        CircularProgressIndicator(
                                            color = appColors.danger,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.logSendingDiscord, color = appColors.danger, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    } else if (discordSentSuccess) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = appColors.success, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.logSentDiscord, color = appColors.success, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = appColors.danger, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.logSendDiscord, color = appColors.danger, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = onOpenTikTokStudio,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.surfaceElevated),
                                border = BorderStroke(1.dp, appColors.borderFocus)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Upload, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.logPostTikTok, color = appColors.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= MODAL CHI TIẾT VIDEO ĐÃ TỐI ƯU =================
@Composable
fun HistoryDetailDialog(
    record: CompressedVideoRecord,
    strings: AppStringsData,
    onDismiss: () -> Unit,
    onOpenTikTokStudio: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current
    var showFullLogs by remember { mutableStateOf(false) }
    var isSendingDiscord by remember { mutableStateOf(false) }
    var discordSentSuccess by remember { mutableStateOf(false) }

    val thumbnailBitmap = remember(record.id) {
        HistoryRepository.loadThumbnail(context, record.thumbnailFileName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .border(1.dp, appColors.border, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.historyProjectPrefix.format(record.displayName),
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = strings.close, tint = appColors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surfaceElevated)
                        .border(1.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .clickable { playSpecificVideo(context, record) }
                    ) {
                        if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = strings.historyPlayVideo,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier
                                    .size(28.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = record.originalName,
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${record.resolution}  •  ${record.durationSec}s",
                            color = appColors.accentText,
                            fontSize = 11.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${record.optimizedSizeMb} MB",
                            color = appColors.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.historyStoragePath,
                    color = appColors.textMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = record.outputPath,
                    color = appColors.textSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFullLogs = !showFullLogs }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showFullLogs) strings.close else "${strings.historyViewAllLogs} (${record.logs.size})",
                        color = appColors.accentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (showFullLogs) "▲" else "▼",
                        color = appColors.accentText,
                        fontSize = 11.sp
                    )
                }

                AnimatedVisibility(visible = showFullLogs) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(appColors.surfaceInput)
                                .border(1.dp, appColors.borderSubtle, RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(record.logs) { line ->
                                    Text(
                                        text = line,
                                        color = appColors.textMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                if (!isSendingDiscord && !discordSentSuccess) {
                                    isSendingDiscord = true
                                    scope.launch {
                                        val sent = DiscordWebhookManager.sendErrorLog(
                                            context = context,
                                            videoName = record.originalName,
                                            videoSizeMb = record.originalSizeMb,
                                            resolution = record.resolution,
                                            durationSec = record.durationSec,
                                            errorSummary = record.logs.findLast { it.startsWith("[ERROR]") } ?: "Project history report",
                                            logs = record.logs
                                        )
                                        isSendingDiscord = false
                                        discordSentSuccess = sent
                                        if (sent) {
                                            Toast.makeText(context, strings.logToastDiscordSuccess, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, strings.logToastDiscordFail, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (discordSentSuccess) appColors.success else appColors.accentText
                            ),
                            border = BorderStroke(1.dp, if (discordSentSuccess) appColors.success else appColors.borderSubtle),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            if (isSendingDiscord) {
                                CircularProgressIndicator(color = appColors.accent, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(strings.logSendingDiscord, fontSize = 11.sp)
                            } else if (discordSentSuccess) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = appColors.success, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(strings.logSentDiscord, fontSize = 11.sp, color = appColors.success)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = appColors.accentText, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(strings.logSendDiscord, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            playSpecificVideo(context, record)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.historyPlayVideo, fontSize = 12.5.sp)
                    }

                    Button(
                        onClick = onOpenTikTokStudio,
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.surfaceElevated),
                        border = BorderStroke(1.dp, appColors.borderFocus)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.logPostTikTok, color = appColors.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Mở TikTok Studio trực tiếp bằng trình duyệt ngoài trên điện thoại
fun openTikTokStudioInBrowser(context: Context, strings: AppStringsData) {
    val studioUrl = "https://www.tiktok.com/tiktokstudio/upload"
    try {
        val intent = Intent(Intent.ACTION_VIEW, studioUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, strings.openBrowserError, Toast.LENGTH_SHORT).show()
    }
}

// Dọn dẹp các tệp tạm cũ từ các phiên trước
fun cleanLegacyTempFiles(context: Context) {
    try {
        context.cacheDir?.listFiles()?.forEach { file ->
            if (file.name.contains("webview", ignoreCase = true) ||
                file.name.contains("chromium", ignoreCase = true) ||
                file.name.endsWith(".tmp") ||
                file.name.startsWith("temp_")
            ) {
                file.deleteRecursively()
            }
        }
        val appWebviewDir = File(context.dataDir, "app_webview")
        if (appWebviewDir.exists() && appWebviewDir.isDirectory) {
            appWebviewDir.deleteRecursively()
        }
    } catch (_: Exception) {
    }
}

// Phát chính xác video của record (tránh phát nhầm video khác)
fun playSpecificVideo(context: Context, record: CompressedVideoRecord) {
    val lang = LanguagePreferences.getLanguage(context)
    val strings = AppStrings.forLanguage(lang)
    val targetUri = resolveVideoUri(context, record)
    if (targetUri != null) {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            val chooser = Intent.createChooser(
                playIntent,
                String.format(strings.playVideoChooser, record.displayName)
            )
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            try {
                context.startActivity(playIntent)
            } catch (_: Exception) {
                Toast.makeText(context, strings.noVideoPlayerFound, Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Toast.makeText(
            context,
            String.format(strings.videoFileNotFound, record.displayName),
            Toast.LENGTH_LONG
        ).show()
    }
}

// Truy tìm chính xác Uri của video từ contentUri, MediaStore hoặc FileProvider
fun resolveVideoUri(context: Context, record: CompressedVideoRecord): Uri? {
    val resolver = context.contentResolver

    // 1. Kiểm tra trực tiếp contentUri đã lưu sẵn trong record
    if (!record.contentUri.isNullOrBlank()) {
        try {
            val directUri = record.contentUri.toUri()
            resolver.openInputStream(directUri)?.use {
                return directUri
            }
        } catch (_: Exception) {}
    }

    val fileName = File(record.outputPath).name

    // 2. Tra cứu MediaStore bằng DISPLAY_NAME chính xác
    try {
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                if (idIdx != -1) {
                    val mediaId = cursor.getLong(idIdx)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                    try {
                        resolver.openInputStream(uri)?.use {
                            return uri
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    } catch (_: Exception) {}

    // 3. Tra cứu theo từ khóa tên gốc nếu tên file đã đổi
    try {
        val baseName = record.originalName.substringBeforeLast(".")
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$baseName%")
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                if (idIdx != -1) {
                    val mediaId = cursor.getLong(idIdx)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                    try {
                        resolver.openInputStream(uri)?.use {
                            return uri
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    } catch (_: Exception) {}

    // 4. Tra cứu File vật lý trong thư mục Movies/RecompressBypass
    try {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val targetFile = File(moviesDir, "RecompressBypass/$fileName")
        if (targetFile.exists() && targetFile.length() > 0) {
            return try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            } catch (_: Exception) {
                Uri.fromFile(targetFile)
            }
        }
    } catch (_: Exception) {}

    return null
}

// Trích xuất metadata & thumbnail
suspend fun extractVideoMetadata(context: Context, uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
    var name = "video.mp4"
    var sizeBytes = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx != -1) name = cursor.getString(nameIdx)
            if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
        }
    }

    val retriever = MediaMetadataRetriever()
    var width = "1080"
    var height = "1920"
    var durationMs = 0L
    var thumbnail: Bitmap? = null

    try {
        retriever.setDataSource(context, uri)
        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "1080"
        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "1920"
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        thumbnail = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
    } finally {
        retriever.release()
    }

    val sizeMb = String.format(Locale.US, "%.2f", sizeBytes / (1024.0 * 1024.0))
    val durationSec = String.format(Locale.US, "%.1f", durationMs / 1000.0)

    VideoMetadata(
        uri = uri,
        name = name,
        sizeBytes = sizeBytes,
        sizeMb = sizeMb,
        resolution = "${width}x${height}",
        durationSec = durationSec,
        thumbnail = thumbnail
    )
}

// Xử lý tối ưu video và lưu lịch sử (log ngắn gọn, không lộ chi tiết kỹ thuật)
suspend fun processAndOptimizeVideo(
    context: Context,
    media: VideoMetadata,
    historyIndex: Int,
    language: AppLanguage = LanguagePreferences.getLanguage(context),
    onLog: (String) -> Unit
): CompressedVideoRecord? = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()
    val isEn = (language == AppLanguage.EN)
    val tempIn = File(context.cacheDir, "temp_input.mp4")
    val tempOut = File(context.cacheDir, "temp_optimized.mp4")
    if (tempIn.exists()) tempIn.delete()
    if (tempOut.exists()) tempOut.delete()

    val initialLog = if (isEn) "[START] Reading video..." else "[START] Đang đọc video..."
    val logsCollector = mutableListOf(initialLog)

    val mainHandler = Handler(Looper.getMainLooper())
    fun appendLog(msg: String) {
        logsCollector.add(msg)
        mainHandler.post {
            onLog(msg)
        }
    }

    try {
        context.contentResolver.openInputStream(media.uri)?.use { input ->
            FileOutputStream(tempIn, false).use { output ->
                input.copyTo(output)
            }
        }

        appendLog(if (isEn) "[PROCESS] Optimizing video..." else "[PROCESS] Đang tối ưu video...")

        Mp4BypassOptimizer.optimize(
            inputFile = tempIn,
            outputFile = tempOut,
            onLog = { /* Không ghi chi tiết kỹ thuật vào log hiển thị */ }
        )

        val cleanBaseName = media.name
            .removeSuffix(".mp4")
            .removeSuffix(".mov")
            .removeSuffix(".MP4")
            .removeSuffix(".MOV")
            .replace(
                Regex(
                    "_(haze|v3|v4|v4\\.5|v45|nova|opt|optimized).*$",
                    RegexOption.IGNORE_CASE
                ), ""
            )
        val outFileName = "${cleanBaseName}_opt.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outFileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RecompressBypass")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException(if (isEn) "Cannot save video to gallery." else "Không thể lưu video vào thư viện.")

        resolver.openOutputStream(itemUri)?.use { outStream ->
            tempOut.inputStream().use { inStream ->
                inStream.copyTo(outStream)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        appendLog(
            if (isEn) "[SUCCESS] Saved to gallery (${elapsedMs}ms)"
            else "[SUCCESS] Đã lưu vào thư viện (${elapsedMs}ms)"
        )

        val id = System.currentTimeMillis().toString()
        val displayName = HistoryRepository.generateDisplayName(historyIndex)

        // Lưu ảnh thumbnail cho lịch sử
        val thumbFileName = media.thumbnail?.let { bmp ->
            HistoryRepository.saveThumbnail(context, id, bmp)
        }

        val record = CompressedVideoRecord(
            id = id,
            displayName = displayName,
            originalName = media.name,
            originalSizeMb = media.sizeMb,
            optimizedSizeMb = String.format(
                Locale.US,
                "%.2f",
                tempOut.length() / (1024.0 * 1024.0)
            ),
            resolution = media.resolution,
            durationSec = media.durationSec,
            timestamp = System.currentTimeMillis(),
            outputPath = "Movies/RecompressBypass/$outFileName",
            thumbnailFileName = thumbFileName,
            logs = logsCollector,
            contentUri = itemUri.toString()
        )

        HistoryRepository.saveRecord(context, record)
        record
    } catch (_: Throwable) {
        val cleanErr = if (isEn) {
            "Unable to optimize this video file."
        } else {
            "Không thể tối ưu file video này."
        }
        appendLog("[ERROR] $cleanErr")
        null
    } finally {
        tempIn.delete()
        tempOut.delete()
    }
}

// ================= MODAL CẬP NHẬT BẮT BUỘC GITHUB RELEASE =================
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    strings: AppStringsData,
    isForceUpdate: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current
    val currentLanguage = LocalAppLanguage.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val canDismiss = !isDownloading && !isForceUpdate

    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .border(1.dp, appColors.border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(appColors.warningSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = appColors.warning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.updateDialogTitle,
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${updateInfo.tagName} (${updateInfo.apkSizeMb})",
                                color = appColors.accentText,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    if (canDismiss) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.close,
                                tint = appColors.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (currentLanguage == AppLanguage.EN) {
                        "Please update to the latest version to continue with the best optimization experience."
                    } else {
                        "Vui lòng cập nhật lên phiên bản mới nhất để tiếp tục sử dụng với trải nghiệm tối ưu tốt nhất."
                    },
                    color = appColors.warning,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.updateReleaseNotes,
                    color = appColors.textMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(appColors.surfaceInput)
                        .border(1.dp, appColors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(
                                text = updateInfo.getLocalizedChangelog(currentLanguage),
                                color = appColors.textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.updateDownloading,
                                color = appColors.textPrimary,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "$downloadProgress%",
                                color = appColors.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = appColors.accent,
                            trackColor = appColors.borderSubtle
                        )
                    }
                }

                if (downloadError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadError!!,
                        color = appColors.danger,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (!isDownloading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (canDismiss) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                                border = BorderStroke(1.dp, appColors.border)
                            ) {
                                Text(strings.updateLater, fontSize = 12.5.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            UpdateManager.GITHUB_RELEASES_URL.toUri()
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            strings.openBrowserError,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary),
                                border = BorderStroke(1.dp, appColors.border)
                            ) {
                                Text(strings.openGithub, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                isDownloading = true
                                downloadError = null
                                scope.launch {
                                    val file = UpdateManager.downloadApk(context, updateInfo.apkDownloadUrl) { p ->
                                        downloadProgress = p
                                    }
                                    isDownloading = false
                                    if (file != null && file.exists()) {
                                        UpdateManager.installApk(context, file)
                                        if (canDismiss) onDismiss()
                                    } else {
                                        downloadError = strings.updateError
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.surfaceElevated),
                            border = BorderStroke(1.dp, appColors.borderFocus)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.updateNow, color = appColors.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= MODAL ỦNG HỘ TÁC GIẢ (GITHUB SPONSORS) =================
@Composable
fun SponsorPromptDialog(
    strings: AppStringsData,
    onDismiss: () -> Unit,
    onSponsorClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val sponsorPink = Color(0xFFEA4AAA)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .border(1.dp, sponsorPink.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(sponsorPink.copy(alpha = 0.16f))
                                .border(1.dp, sponsorPink.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = sponsorPink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.sponsorDialogTitle,
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.sponsorDialogSubtitle,
                                color = sponsorPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = appColors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors.surfaceInput)
                        .border(1.dp, appColors.borderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = strings.sponsorDialogBody,
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "github.com/sponsors/j7x69",
                            color = sponsorPink,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textSecondary),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Text(strings.sponsorLaterBtn, fontSize = 12.5.sp)
                    }

                    Button(
                        onClick = onSponsorClick,
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = sponsorPink.copy(alpha = 0.2f),
                            contentColor = sponsorPink
                        ),
                        border = BorderStroke(1.dp, sponsorPink)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = sponsorPink,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.sponsorNowBtn,
                            color = sponsorPink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
