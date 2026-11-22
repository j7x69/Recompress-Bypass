package com.recompress.bypass

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokAuditDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current
    val currentLang = LocalAppLanguage.current
    val isEn = currentLang == AppLanguage.EN
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var linkInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentStepText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var auditResult by remember { mutableStateOf<TikTokAuditResult?>(null) }

    fun runAudit() {
        if (linkInput.isBlank()) {
            errorMessage =
                if (isEn) "Please paste a TikTok video link." else "Vui lòng dán đường link video TikTok."
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isLoading = true
        errorMessage = null
        currentStepText = if (isEn) "Connecting to TikTok..." else "Đang kết nối tới máy chủ TikTok..."
        scope.launch {
            val result = TikTokLinkAuditor.auditTikTokUrl(linkInput) { en, vi ->
                currentStepText = if (isEn) en else vi
            }
            isLoading = false
            result.onSuccess { res ->
                auditResult = res
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: if (isEn) {
                    "Cannot inspect this video. Please check the link or network."
                } else {
                    "Không thể kiểm tra video này. Vui lòng kiểm tra lại đường link hoặc kết nối mạng."
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.dialogSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = appColors.textMuted.copy(alpha = 0.35f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (auditResult != null) Modifier.fillMaxHeight(0.88f) else Modifier.wrapContentHeight())
                .animateContentSize()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Text(
                text = if (isEn) "Inspect TikTok Link" else "Soi Link TikTok",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = appColors.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isEn) "Resolution · FPS · Bitrate · FYP Status" else "Độ phân giải · FPS · Bitrate · Trạng thái FYP",
                fontSize = 12.sp,
                color = appColors.textMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input URL + Paste button
            OutlinedTextField(
                value = linkInput,
                onValueChange = {
                    linkInput = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (isEn) "Paste TikTok link (vt.tiktok.com/...)" else "Dán link video TikTok (vt.tiktok.com/...)",
                        fontSize = 13.sp,
                        color = appColors.textMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = appColors.dialogSurfaceElevated,
                    unfocusedContainerColor = appColors.dialogSurfaceElevated,
                    focusedBorderColor = appColors.accent.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = appColors.textPrimary,
                    unfocusedTextColor = appColors.textPrimary,
                    cursorColor = appColors.accent
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (linkInput.isNotEmpty()) {
                            IconButton(onClick = { linkInput = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isEn) "Clear" else "Xóa",
                                    tint = appColors.textMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    linkInput = text
                                    errorMessage = null
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    if (isEn) "Clipboard is empty" else "Bộ nhớ tạm rỗng",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = if (isEn) "Paste" else "Dán",
                                tint = appColors.accent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runAudit() })
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Primary CTA Button
            Button(
                onClick = { runAudit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.primaryCtaBg,
                    contentColor = appColors.primaryCtaContent,
                    disabledContainerColor = appColors.dialogSurfaceElevated,
                    disabledContentColor = appColors.textMuted
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = appColors.accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Inspecting..." else "Đang kiểm tra...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Inspect Video" else "Kiểm tra video",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Live Server Progress Step Card
            AnimatedVisibility(visible = isLoading) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(appColors.dialogSurfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = appColors.accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = currentStepText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = appColors.textPrimary,
                            lineHeight = 17.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors.dangerSoft)
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = appColors.danger,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results Container
            val res = auditResult
            if (res != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Top Status Row (clean, minimal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = appColors.success,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) {
                                "Analyzed in ${String.format(Locale.US, "%.1f", res.elapsedSec)}s"
                            } else {
                                "Phân tích xong trong ${
                                    String.format(
                                        Locale.US,
                                        "%.1f",
                                        res.elapsedSec
                                    )
                                } giây"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = appColors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Video Profile & Author Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(appColors.dialogSurfaceElevated)
                            .padding(12.dp)
                    ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Thumbnail (9:16 aspect)
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(appColors.borderSubtle),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    if (res.coverBitmap != null) {
                                        Image(
                                            bitmap = res.coverBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = appColors.textMuted,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    // Duration tag
                                    Box(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = res.durationFormatted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Author & Video info
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (res.authorAvatarBitmap != null) {
                                            Image(
                                                bitmap = res.authorAvatarBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(appColors.borderSubtle),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = res.authorName.take(1).uppercase(),
                                                    color = appColors.textPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = "@${res.authorUsername.ifBlank { res.authorName }}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appColors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${res.locationCreated} • ${if (isEn) res.createTimeFormattedEn else res.createTimeFormattedVi}",
                                                fontSize = 10.sp,
                                                color = appColors.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (res.desc.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = res.desc,
                                            fontSize = 11.sp,
                                            color = appColors.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Music track
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = appColors.accentText,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = res.soundTitle,
                                            fontSize = 11.sp,
                                            color = appColors.accentText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Hashtag pills
                                    if (res.hashtags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            res.hashtags.take(5).forEach { tag ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(appColors.borderSubtle)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        fontSize = 9.5.sp,
                                                        color = appColors.textMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. TIKTOK PHÁT GÌ (WHAT TIKTOK PLAYS)
                        Column {
                            Text(
                                text = if (isEn) "WHAT TIKTOK PLAYS" else "TIKTOK PHÁT GÌ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 4 Big Stat Numbers Row (Clean, no truncated ellipsis)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                // 1. Resolution
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.resolutionTierName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = "${res.width}x${res.height}",
                                        fontSize = 10.sp,
                                        color = appColors.textMuted,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // 2. FPS
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.0f", res.fps)} fps",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.accent
                                    )
                                    Text(
                                        text = if (isEn) "Frame rate" else "Khung hình",
                                        fontSize = 10.sp,
                                        color = appColors.textMuted,
                                        softWrap = false
                                    )
                                }

                                // 3. File Size
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", res.fileSizeMb)} MB",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = if (isEn) "File size" else "Dung lượng",
                                        fontSize = 10.sp,
                                        color = appColors.textMuted,
                                        softWrap = false
                                    )
                                }

                                // 4. Duration
                                Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = res.durationFormatted,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = if (isEn) "Duration" else "Thời lượng",
                                        fontSize = 10.sp,
                                        color = appColors.textMuted,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Stepped Ladder Resolution Scale (Trình duyệt & Điện thoại) - Fixed 4K wrap
                            ResolutionLadderBlock(
                                activeTierIndex = res.activeResolutionTier,
                                activeTierName = res.resolutionTierName,
                                codec = res.codecType,
                                bitrate = res.bitrateFormatted,
                                isEn = isEn,
                                appColors = appColors
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // SDR / HDR Dynamic Range Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.dialogSurfaceElevated)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(appColors.borderSubtle)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = res.dynamicRange,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = appColors.textPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (isEn) "Played in ${res.dynamicRange}" else "Phát ở ${res.dynamicRange}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appColors.textPrimary
                                            )
                                            Text(
                                                text = if (isEn) {
                                                    "Standard dynamic range, consistent across all mobile screens."
                                                } else {
                                                    "Dải tương phản động tiêu chuẩn, như nhau trên mọi màn hình."
                                                },
                                                fontSize = 10.5.sp,
                                                color = appColors.textMuted
                                            )
                                        }
                                    }

                                    // Dynamic Range bar
                                    Box(
                                        modifier = Modifier
                                            .width(55.dp)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(appColors.borderSubtle, appColors.accent)
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. DÀNH CHO BẠN (FOR YOU / FYP STATUS)
                        Column {
                            Text(
                                text = if (isEn) "FOR YOU (FYP STATUS)" else "DÀNH CHO BẠN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val statusColor = when (res.shadowbanStatus) {
                                ShadowbanStatus.NOT_SHADOW_BAN -> appColors.success
                                ShadowbanStatus.SHADOW_BAN_WARNING -> appColors.warning
                                ShadowbanStatus.SHADOW_BANNED -> appColors.danger
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.dialogSurfaceElevated)
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(statusColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (res.shadowbanStatus) {
                                                ShadowbanStatus.NOT_SHADOW_BAN -> Icons.Default.CheckCircle
                                                ShadowbanStatus.SHADOW_BAN_WARNING -> Icons.Default.Warning
                                                ShadowbanStatus.SHADOW_BANNED -> Icons.Default.Error
                                            },
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = if (isEn) res.shadowbanStatus.labelEn else res.shadowbanStatus.labelVi,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                        Text(
                                            text = when (res.shadowbanStatus) {
                                                ShadowbanStatus.NOT_SHADOW_BAN -> {
                                                    if (isEn) "TikTok is actively recommending this video on For You."
                                                    else "TikTok đang đề xuất video này trên Dành cho bạn."
                                                }
                                                ShadowbanStatus.SHADOW_BAN_WARNING -> {
                                                    if (isEn) "Video has potential FYP throttling or restricted interactions."
                                                    else "Video có dấu hiệu bị bóp tương tác hoặc hạn chế tính năng."
                                                }
                                                ShadowbanStatus.SHADOW_BANNED -> {
                                                    if (isEn) "Video flagged with policy violations or muted audio."
                                                    else "Video bị tắt tiếng bản quyền, ẩn riêng tư hoặc 0 view For You."
                                                }
                                            },
                                            fontSize = 11.sp,
                                            color = appColors.textMuted
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 5. TƯƠNG TÁC (INTERACTIONS) - Exactly 5 items, NO Downloads, no truncated labels!
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEn) "INTERACTIONS" else "TƯƠNG TÁC",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.textMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = if (isEn) {
                                        "Engagement rate: ${String.format(Locale.US, "%.1f", res.engagementRatePercent)}%"
                                    } else {
                                        "Tỷ lệ tương tác: ${String.format(Locale.US, "%.1f", res.engagementRatePercent)}%"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.engagementRatePercent >= 8.0) appColors.accent else appColors.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 5 Metric Items spaced evenly (clean vector icons, no emoji)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.dialogSurfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    InteractionMetricItem(
                                        Icons.Default.Visibility,
                                        if (isEn) "Views" else "Xem",
                                        formatHazeNumber(res.playCount, isEn),
                                        appColors
                                    )
                                    InteractionMetricItem(
                                        Icons.Default.FavoriteBorder,
                                        if (isEn) "Likes" else "Thích",
                                        formatHazeNumber(res.diggCount, isEn),
                                        appColors
                                    )
                                    InteractionMetricItem(
                                        Icons.Default.ChatBubbleOutline,
                                        if (isEn) "Comments" else "Bình luận",
                                        formatHazeNumber(res.commentCount, isEn),
                                        appColors
                                    )
                                    InteractionMetricItem(
                                        Icons.Default.BookmarkBorder,
                                        if (isEn) "Saves" else "Lưu",
                                        formatHazeNumber(res.collectCount, isEn),
                                        appColors
                                    )
                                    InteractionMetricItem(
                                        Icons.Default.Share,
                                        if (isEn) "Shares" else "Chia sẻ",
                                        formatHazeNumber(res.shareCount, isEn),
                                        appColors
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 6. CHẨN ĐOÁN CHI TIẾT TỪ MÁY CHỦ
                        Column {
                            Text(
                                text = if (isEn) "SERVER DIAGNOSTICS" else "CHẨN ĐOÁN TỪ MÁY CHỦ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.dialogSurfaceElevated)
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    res.shadowbanDiagnosis.forEach { diag ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = diag.icon,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isEn) diag.textEn else diag.textVi,
                                                fontSize = 12.sp,
                                                color = appColors.textSecondary,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

/**
 * Stepped Ladder Resolution Scale (Trình duyệt & Điện thoại)
 * Fixed: Zero text wrapping for 4K and matches active theme colors
 */
@Composable
private fun ResolutionLadderBlock(
    activeTierIndex: Int,
    activeTierName: String,
    codec: String,
    bitrate: String,
    isEn: Boolean,
    appColors: AppColors
) {
    val tiers = listOf("360p", "540p", "720p", "1080p", "2K", "4K")

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animatedFraction by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ladder_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.dialogSurfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Row 1: Trình duyệt (Browser)
        ResolutionLadderRow(
            label = if (isEn) "Browser" else "Trình duyệt",
            tiers = tiers,
            activeTierIndex = activeTierIndex,
            activeTierName = activeTierName,
            codec = codec,
            bitrate = bitrate,
            progressFraction = animatedFraction,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Điện thoại (Mobile)
        ResolutionLadderRow(
            label = if (isEn) "Mobile" else "Điện thoại",
            tiers = tiers,
            activeTierIndex = activeTierIndex,
            activeTierName = activeTierName,
            codec = codec,
            bitrate = bitrate,
            progressFraction = animatedFraction,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Row 3: Bottom Ticks (360p  540p  720p  1080p  2K  4K) - Perfectly aligned, no wrapping!
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(72.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tiers.forEach { tier ->
                    Text(
                        text = tier,
                        fontSize = 9.sp,
                        color = appColors.textMuted,
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(82.dp))
        }
    }
}

@Composable
private fun ResolutionLadderRow(
    label: String,
    tiers: List<String>,
    activeTierIndex: Int,
    activeTierName: String,
    codec: String,
    bitrate: String,
    progressFraction: Float,
    appColors: AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label on left
        Text(
            text = label,
            fontSize = 11.sp,
            color = appColors.textMuted,
            modifier = Modifier.width(72.dp)
        )

        // 6 Segments
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tiers.indices.forEach { index ->
                val isTarget = index == activeTierIndex
                val isBelow = index < activeTierIndex

                val segmentColor = when {
                    isTarget -> appColors.accent.copy(alpha = progressFraction)
                    isBelow -> appColors.accentSoft.copy(alpha = progressFraction)
                    else -> appColors.borderSubtle
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segmentColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Info on right
        Column(
            modifier = Modifier.width(72.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = activeTierName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )
            Text(
                text = "${codec.lowercase()} • $bitrate",
                fontSize = 8.5.sp,
                color = appColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InteractionMetricItem(
    icon: ImageVector,
    label: String,
    count: String,
    appColors: AppColors
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = appColors.textMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = appColors.textPrimary,
            softWrap = false,
            maxLines = 1
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            color = appColors.textMuted,
            softWrap = false,
            maxLines = 1
        )
    }
}

private fun formatHazeNumber(count: Long, isEn: Boolean): String {
    return if (isEn) {
        when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 10_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> String.format(Locale.US, "%,d", count)
        }
    } else {
        val viLocale = Locale.forLanguageTag("vi-VN")
        when {
            count >= 1_000_000 -> String.format(viLocale, "%.1f Tr", count / 1_000_000.0)
            count >= 10_000 -> String.format(viLocale, "%.1f N", count / 1_000.0)
            else -> String.format(viLocale, "%,d", count).replace(',', '.')
        }
    }
}
