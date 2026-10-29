package com.recompress.bypass

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val tagName: String,
    val releaseName: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkSizeMb: String,
    val hasUpdate: Boolean
)

object UpdateManager {

    private const val GITHUB_API_RELEASES = "https://api.github.com/repos/j7x69/Recompress-Bypass/releases"
    const val GITHUB_RELEASES_URL = "https://github.com/j7x69/Recompress-Bypass/releases"

    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.1.4"
        } catch (e: Exception) {
            "1.1.4"
        }
    }

    suspend fun checkUpdate(context: Context): AppUpdateInfo? = checkUpdate(getAppVersion(context))

    suspend fun checkUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_RELEASES)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RecompressBypass-Android")
            }

            if (conn.responseCode != 200) {
                return@withContext null
            }

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(jsonStr)

            var selectedRelease: JSONObject? = null
            var apkUrl: String? = null
            var apkSizeBytes = 0L

            for (r in 0 until releases.length()) {
                val rel = releases.getJSONObject(r)
                val assets = rel.optJSONArray("assets") ?: continue
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) || downloadUrl.endsWith(".apk", ignoreCase = true)) {
                        selectedRelease = rel
                        apkUrl = downloadUrl
                        apkSizeBytes = asset.optLong("size", 0L)
                        break
                    }
                }
                if (selectedRelease != null) break
            }

            if (selectedRelease == null || apkUrl.isNullOrBlank()) {
                return@withContext null
            }

            val tagName = selectedRelease.optString("tag_name", "").trim()
            val releaseName = selectedRelease.optString("name", tagName).ifBlank { tagName }
            val changelog = selectedRelease.optString("body", "Không có ghi chú phát hành.")
            val apkSizeMb = String.format("%.2f MB", apkSizeBytes / (1024.0 * 1024.0))
            val hasUpdate = isNewer(currentVersion, tagName)

            AppUpdateInfo(
                tagName = tagName,
                releaseName = releaseName,
                changelog = changelog,
                apkDownloadUrl = apkUrl,
                apkSizeMb = apkSizeMb,
                hasUpdate = hasUpdate
            )
        } catch (e: Exception) {
            null
        }
    }

    fun cleanVersion(v: String): List<Int> {
        var cleaned = v.trim().replace(Regex("^[vV][._\\-]?"), "").trim()
        // Bỏ hậu tố như -debug, -release, -beta nếu có
        cleaned = cleaned.replace(Regex("[-_](debug|release|beta|alpha).*$", RegexOption.IGNORE_CASE), "")
        // Chuẩn hóa phiên bản phụ dạng "1.0.71" -> "1.0.7.1", "1.0.81" -> "1.0.8.1" (tránh bị hiểu là patch 81 > 8)
        if (cleaned.matches(Regex("""\d+\.\d+\.[0-9]{2,}"""))) {
            cleaned = cleaned.replace(Regex("""(\d+\.\d+\.\d)(\d+)"""), "$1.$2")
        }
        return cleaned.split(Regex("[.\\-_]")).mapNotNull { it.toIntOrNull() }
    }

    fun isNewer(current: String, remote: String): Boolean {
        val cParts = cleanVersion(current)
        val rParts = cleanVersion(remote)
        if (cParts.isEmpty() || rParts.isEmpty()) return false

        val maxLen = maxOf(cParts.size, rParts.size)
        for (i in 0 until maxLen) {
            val cVal = if (i < cParts.size) cParts[i] else 0
            val rVal = if (i < rParts.size) rParts[i] else 0
            if (rVal > cVal) return true
            if (rVal < cVal) return false
        }
        return false
    }

    /**
     * Xác thực URL tải APK có thuộc GitHub chính chủ của tác giả hoặc CDN lưu trữ chính thức của GitHub không
     */
    fun isValidDownloadUrl(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            val protocol = url.protocol?.lowercase() ?: ""
            val host = url.host?.lowercase() ?: ""
            val path = url.path ?: ""
            if (protocol != "https") return false

            // Cho phép tải từ chính repo GitHub của tác giả hoặc CDN lưu trữ của GitHub (*.githubusercontent.com)
            (host == "github.com" && path.startsWith("/j7x69/Recompress-Bypass/releases/download/")) ||
                    host.endsWith(".githubusercontent.com") ||
                    host == "githubusercontent.com"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Xác thực tính toàn vẹn và chữ ký bảo mật của file APK trước khi cài đặt.
     * Ngăn chặn hoàn toàn việc cài APK giả mạo hoặc bị can thiệp.
     */
    fun verifyApkIntegrity(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0) return false

        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false

            // 1. Phải trùng khớp đúng applicationId của dự án
            if (archiveInfo.packageName != context.packageName) {
                apkFile.delete()
                return false
            }

            // 2. Phải có cùng chữ ký số (Certificate / Keystore) với ứng dụng đang chạy
            val currentInfo = pm.getPackageInfo(context.packageName, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val currentSigning = currentInfo.signingInfo
                val archiveSigning = archiveInfo.signingInfo

                if (currentSigning != null && archiveSigning != null) {
                    val currentSigners = currentSigning.apkContentsSigners.map { it.toByteArray().contentHashCode() }
                    val archiveSigners = archiveSigning.apkContentsSigners.map { it.toByteArray().contentHashCode() }

                    val matches = currentSigners.isNotEmpty() && currentSigners.all { archiveSigners.contains(it) }
                    if (!matches) {
                        apkFile.delete()
                        return false
                    }
                } else {
                    // Fallback tương thích trên một số bản ROM Android
                    @Suppress("DEPRECATION")
                    val currentSigs = currentInfo.signatures?.map { it.toCharsString() }
                    @Suppress("DEPRECATION")
                    val archiveSigs = archiveInfo.signatures?.map { it.toCharsString() }

                    if (currentSigs != null && archiveSigs != null) {
                        val matches = currentSigs.isNotEmpty() && currentSigs.all { archiveSigs.contains(it) }
                        if (!matches) {
                            apkFile.delete()
                            return false
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val currentSigs = currentInfo.signatures?.map { it.toCharsString() } ?: return false
                @Suppress("DEPRECATION")
                val archiveSigs = archiveInfo.signatures?.map { it.toCharsString() } ?: return false

                val matches = currentSigs.isNotEmpty() && currentSigs.all { archiveSigs.contains(it) }
                if (!matches) {
                    apkFile.delete()
                    return false
                }
            }

            true
        } catch (e: Exception) {
            apkFile.delete()
            false
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            var currentUrl = downloadUrl
            var redirectCount = 0
            val maxRedirects = 5

            // Vòng lặp giải quyết HTTP 301/302/303/307 redirects (GitHub redirect sang release-assets.githubusercontent.com)
            while (true) {
                if (!isValidDownloadUrl(currentUrl)) {
                    return@withContext null
                }

                val url = URL(currentUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false // Tự điều hướng thủ công để vượt qua cross-domain redirect của Android
                    setRequestProperty("User-Agent", "RecompressBypass-Android")
                }

                val responseCode = conn.responseCode
                if (responseCode in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = null
                    if (location.isNullOrBlank() || ++redirectCount > maxRedirects) {
                        return@withContext null
                    }
                    currentUrl = if (location.startsWith("http", ignoreCase = true)) {
                        location
                    } else {
                        URL(url, location).toString()
                    }
                    continue
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@withContext null
                }

                break
            }

            val currentConn = conn ?: return@withContext null
            val totalBytes = currentConn.contentLength.toLong()
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "RecompressBypass_Update.apk")
            if (apkFile.exists()) apkFile.delete()

            currentConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val progress = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            currentConn.disconnect()

            // Kiểm tra bảo mật & chữ ký số trước khi chấp nhận file
            if (!verifyApkIntegrity(context, apkFile)) {
                return@withContext null
            }

            apkFile
        } catch (e: Exception) {
            conn?.disconnect()
            null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        // Kiểm tra an toàn trước khi mở trình cài đặt
        if (!verifyApkIntegrity(context, apkFile)) {
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "com.recompress.bypass.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
