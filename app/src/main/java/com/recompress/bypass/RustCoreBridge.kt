package com.recompress.bypass

import androidx.annotation.Keep
import java.io.File

/**
 * Cầu nối JNI trực tiếp tới lõi Rust Native Engine (`librecompress_core.so`).
 * Toàn bộ các thao tác xử lý nhị phân MP4 v4.6, giải mã cấu hình nhạy cảm (Discord Webhook)
 * và xác thực bảo mật URL/Domain được thực thi trong không gian bộ nhớ an toàn của Rust.
 */
@Keep
object RustCoreBridge {

    val isNativeAvailable: Boolean = try {
        System.loadLibrary("recompress_core")
        true
    } catch (_: Throwable) {
        false
    }

    @Keep
    @JvmStatic
    external fun nativeOptimizeMp4V46(inputPath: String, outputPath: String): String

    @Keep
    @JvmStatic
    external fun nativeBuildUdtaV46(): ByteArray

    @Keep
    @JvmStatic
    external fun nativeResolveWebhook(callerPackage: String): String

    @Keep
    @JvmStatic
    external fun nativeIsValidDownloadUrl(url: String): Boolean

    @Keep
    @JvmStatic
    external fun nativeIsValidTikTokHost(host: String): Boolean

    /**
     * Thực thi tối ưu hóa MP4 v4.6 bằng Rust Native Engine.
     * Ném ngoại lệ với thông báo rõ ràng nếu quá trình xử lý gặp lỗi.
     */
    fun optimizeMp4WithRust(
        inputFile: File,
        outputFile: File,
        onLog: (String) -> Unit
    ) {
        val rawResponse = nativeOptimizeMp4V46(inputFile.absolutePath, outputFile.absolutePath)
        val lines = rawResponse.split("\n")
        val statusLine = lines.firstOrNull() ?: "ERR:Empty response from Rust Native Engine"
        val logLines = lines.drop(1).filter { it.isNotBlank() }

        for (log in logLines) {
            onLog(log)
        }

        if (!statusLine.startsWith("OK")) {
            val errorMsg =
                statusLine.removePrefix("ERR:").ifBlank { "Unknown error from Rust Engine" }
            throw IllegalStateException(errorMsg)
        }
    }
}
