package com.recompress.bypass

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileOutputStream

enum class AppAppearance(
    val id: String,
    val title: String,
    val description: String
) {
    AMOLED(
        id = "amoled",
        title = "Dark AMOLED",
        description = "Đen tuyền 0-nit, tiết kiệm pin tối đa, độ tương phản tuyệt đối"
    ),
    MIDNIGHT(
        id = "midnight",
        title = "Midnight Obsidian",
        description = "Sắc đêm huyền bí pha xanh than sâu thẳm, dịu mắt"
    ),
    CHARCOAL(
        id = "charcoal",
        title = "Minimalist Charcoal",
        description = "Xám than tối giản trung tính chuẩn phong cách công thái học"
    ),
    CYBERPUNK(
        id = "cyberpunk",
        title = "Cyberpunk Neon",
        description = "Sắc tím neon kết hợp hồng điện tử cá tính & tương phản cao"
    ),
    EMERALD(
        id = "emerald",
        title = "Deep Emerald",
        description = "Tông ngọc lục bảo huyền bí, thanh lịch và dịu mát"
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Wallpaper & Glass",
        description = "Tự chọn ảnh nền cá nhân, tùy chỉnh độ trong suốt kính mờ (Glassmorphism)"
    );

    companion object {
        fun fromId(id: String?): AppAppearance {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: AMOLED
        }
    }
}

data class CustomThemeConfig(
    val wallpaperPath: String? = null,
    val wallpaperDim: Float = 0.45f,        // 0.0 (no dim) to 0.85 (dark dim)
    val cardOpacity: Float = 0.65f,         // 0.15 (glass) to 1.0 (solid)
    val accentHex: String = "#00E5FF"       // Custom accent color hex
)

data class AppColors(
    val isAmoled: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInput: Color,
    val border: Color,
    val borderSubtle: Color,
    val borderFocus: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color
)

object ThemePalette {
    val Amoled = AppColors(
        isAmoled = true,
        background = Color(0xFF000000),       // Pure AMOLED Pitch Black
        surface = Color(0xFF0B0B0C),          // Deep black surface
        surfaceElevated = Color(0xFF141416),  // Slightly elevated card
        surfaceInput = Color(0xFF070708),     // Terminal/input black
        border = Color(0xFF262628),           // Crisp subtle border
        borderSubtle = Color(0xFF1A1A1C),     // Micro-divider
        borderFocus = Color(0xFF404044),      // Focus ring
        textPrimary = Color(0xFFF6F6F6),      // Crisp white
        textSecondary = Color(0xFFA1A1AA),    // Neutral gray
        textMuted = Color(0xFF71717A),        // Muted gray
        accent = Color(0xFF00E5FF),           // Vivid minimalist Cyan
        accentSoft = Color(0x1F00E5FF),       // 12% Cyan
        accentText = Color(0xFF80D8FF),       // Light cyan text
        success = Color(0xFF10B981),          // Emerald
        successSoft = Color(0x2210B981),
        warning = Color(0xFFF59E0B),          // Amber
        warningSoft = Color(0x22F59E0B),
        danger = Color(0xFFEF4444),           // Crimson
        dangerSoft = Color(0x22EF4444)
    )

    val Midnight = AppColors(
        isAmoled = false,
        background = Color(0xFF080B12),
        surface = Color(0xFF0F1523),
        surfaceElevated = Color(0xFF172033),
        surfaceInput = Color(0xFF090E17),
        border = Color(0xFF23304B),
        borderSubtle = Color(0xFF182236),
        borderFocus = Color(0xFF38BDF8),
        textPrimary = Color(0xFFF0F4F8),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        accent = Color(0xFF38BDF8),
        accentSoft = Color(0x2238BDF8),
        accentText = Color(0xFF7DD3FC),
        success = Color(0xFF34D399),
        successSoft = Color(0x2234D399),
        warning = Color(0xFFFBBF24),
        warningSoft = Color(0x22FBBF24),
        danger = Color(0xFFF87171),
        dangerSoft = Color(0x22F87171)
    )

    val Charcoal = AppColors(
        isAmoled = false,
        background = Color(0xFF111113),
        surface = Color(0xFF18181B),
        surfaceElevated = Color(0xFF27272A),
        surfaceInput = Color(0xFF0E0E10),
        border = Color(0xFF333338),
        borderSubtle = Color(0xFF242428),
        borderFocus = Color(0xFF52525B),
        textPrimary = Color(0xFFEEEEF0),
        textSecondary = Color(0xFFA1A1AA),
        textMuted = Color(0xFF71717A),
        accent = Color(0xFFE4E4E7),
        accentSoft = Color(0x22E4E4E7),
        accentText = Color(0xFFFFFFFF),
        success = Color(0xFF10B981),
        successSoft = Color(0x2210B981),
        warning = Color(0xFFF59E0B),
        warningSoft = Color(0x22F59E0B),
        danger = Color(0xFFEF4444),
        dangerSoft = Color(0x22EF4444)
    )

    val Cyberpunk = AppColors(
        isAmoled = false,
        background = Color(0xFF08040E),
        surface = Color(0xFF120822),
        surfaceElevated = Color(0xFF1C0E34),
        surfaceInput = Color(0xFF0A0514),
        border = Color(0xFF3E1D64),
        borderSubtle = Color(0xFF2A1344),
        borderFocus = Color(0xFFFF007F),
        textPrimary = Color(0xFFFCE7F3),
        textSecondary = Color(0xFFD8B4E2),
        textMuted = Color(0xFF9D74AC),
        accent = Color(0xFFFF007F),
        accentSoft = Color(0x26FF007F),
        accentText = Color(0xFFFF52A4),
        success = Color(0xFF00F5D4),
        successSoft = Color(0x2600F5D4),
        warning = Color(0xFFFFBE0B),
        warningSoft = Color(0x26FFBE0B),
        danger = Color(0xFFFF0055),
        dangerSoft = Color(0x26FF0055)
    )

    val Emerald = AppColors(
        isAmoled = false,
        background = Color(0xFF030D08),
        surface = Color(0xFF081810),
        surfaceElevated = Color(0xFF0E2519),
        surfaceInput = Color(0xFF05110B),
        border = Color(0xFF173E2B),
        borderSubtle = Color(0xFF102B1E),
        borderFocus = Color(0xFF00E676),
        textPrimary = Color(0xFFE8F5E9),
        textSecondary = Color(0xFFA5D6A7),
        textMuted = Color(0xFF66BB6A),
        accent = Color(0xFF00E676),
        accentSoft = Color(0x2200E676),
        accentText = Color(0xFF69F0AE),
        success = Color(0xFF00E676),
        successSoft = Color(0x2200E676),
        warning = Color(0xFFFFD54F),
        warningSoft = Color(0x22FFD54F),
        danger = Color(0xFFFF5252),
        dangerSoft = Color(0x22FF5252)
    )

    fun forCustom(config: CustomThemeConfig): AppColors {
        val parsedAccent = try {
            Color(android.graphics.Color.parseColor(config.accentHex))
        } catch (e: Exception) {
            Color(0xFF00E5FF)
        }
        val cardAlpha = config.cardOpacity.coerceIn(0.12f, 1.0f)
        val elevatedAlpha = (cardAlpha * 1.15f).coerceIn(0.18f, 1.0f)
        val inputAlpha = (cardAlpha * 0.85f).coerceIn(0.12f, 0.95f)
        val isGlass = cardAlpha < 0.90f

        return AppColors(
            isAmoled = false,
            background = Color.Black,
            surface = Color(0xFF0C0D12).copy(alpha = cardAlpha),
            surfaceElevated = Color(0xFF161822).copy(alpha = elevatedAlpha),
            surfaceInput = Color(0xFF05060A).copy(alpha = inputAlpha),
            border = if (isGlass) Color.White.copy(alpha = 0.18f) else Color(0xFF26262A),
            borderSubtle = if (isGlass) Color.White.copy(alpha = 0.10f) else Color(0xFF1C1C20),
            borderFocus = parsedAccent.copy(alpha = 0.75f),
            textPrimary = Color(0xFFF9F9FB),
            textSecondary = Color(0xFFC4C4CC),
            textMuted = Color(0xFF8E8E98),
            accent = parsedAccent,
            accentSoft = parsedAccent.copy(alpha = 0.22f),
            accentText = parsedAccent,
            success = Color(0xFF10B981),
            successSoft = Color(0x2210B981),
            warning = Color(0xFFF59E0B),
            warningSoft = Color(0x22F59E0B),
            danger = Color(0xFFEF4444),
            dangerSoft = Color(0x22EF4444)
        )
    }

    fun forAppearance(appearance: AppAppearance, customConfig: CustomThemeConfig? = null): AppColors {
        return when (appearance) {
            AppAppearance.AMOLED -> Amoled
            AppAppearance.MIDNIGHT -> Midnight
            AppAppearance.CHARCOAL -> Charcoal
            AppAppearance.CYBERPUNK -> Cyberpunk
            AppAppearance.EMERALD -> Emerald
            AppAppearance.CUSTOM -> forCustom(customConfig ?: CustomThemeConfig())
        }
    }
}

val LocalAppColors = staticCompositionLocalOf { ThemePalette.Amoled }

object AppearancePreferences {
    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_APPEARANCE = "selected_appearance"
    private const val KEY_WALLPAPER_PATH = "custom_wallpaper_path"
    private const val KEY_WALLPAPER_DIM = "custom_wallpaper_dim"
    private const val KEY_CARD_OPACITY = "custom_card_opacity"
    private const val KEY_ACCENT_HEX = "custom_accent_hex"

    fun getAppearance(context: Context): AppAppearance {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = sp.getString(KEY_APPEARANCE, AppAppearance.AMOLED.id)
        return AppAppearance.fromId(saved)
    }

    fun saveAppearance(context: Context, appearance: AppAppearance) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_APPEARANCE, appearance.id).apply()
    }

    fun getCustomConfig(context: Context): CustomThemeConfig {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = sp.getString(KEY_WALLPAPER_PATH, null)
        val dim = sp.getFloat(KEY_WALLPAPER_DIM, 0.45f)
        val opacity = sp.getFloat(KEY_CARD_OPACITY, 0.65f)
        val accent = sp.getString(KEY_ACCENT_HEX, "#00E5FF") ?: "#00E5FF"
        return CustomThemeConfig(
            wallpaperPath = path,
            wallpaperDim = dim,
            cardOpacity = opacity,
            accentHex = accent
        )
    }

    fun saveCustomConfig(context: Context, config: CustomThemeConfig) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_WALLPAPER_PATH, config.wallpaperPath)
            .putFloat(KEY_WALLPAPER_DIM, config.wallpaperDim)
            .putFloat(KEY_CARD_OPACITY, config.cardOpacity)
            .putString(KEY_ACCENT_HEX, config.accentHex)
            .apply()
    }

    fun saveWallpaperFromUri(context: Context, uri: Uri): String? {
        return try {
            val destination = File(context.filesDir, "custom_wallpaper.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            destination.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun removeWallpaper(context: Context) {
        try {
            val file = File(context.filesDir, "custom_wallpaper.png")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
