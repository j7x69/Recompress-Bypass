package com.recompress.bypass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4BypassOptimizerTest {

    @Test
    fun testOptimizeRejectsMissingInputFile() {
        val tempDir = java.nio.file.Files.createTempDirectory("opt_contract_").toFile()
        try {
            val missing = java.io.File(tempDir, "missing.mp4")
            val out = java.io.File(tempDir, "out.mp4")
            assertThrows(IllegalArgumentException::class.java) {
                Mp4BypassOptimizer.optimize(inputFile = missing, outputFile = out)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testOptimizeRejectsIdenticalInputAndOutputPaths() {
        val tempDir = java.nio.file.Files.createTempDirectory("opt_contract_").toFile()
        try {
            val same = java.io.File(tempDir, "video.mp4").apply { writeBytes(ByteArray(32)) }
            assertThrows(IllegalArgumentException::class.java) {
                Mp4BypassOptimizer.optimize(inputFile = same, outputFile = same)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testOptimizeFailsFastWhenNativeUnavailableOnHost() {
        if (RustCoreBridge.isNativeAvailable) return
        val tempDir = java.nio.file.Files.createTempDirectory("opt_contract_").toFile()
        try {
            val input = java.io.File(tempDir, "in.mp4").apply { writeBytes(ByteArray(64)) }
            val output = java.io.File(tempDir, "out.mp4")
            assertThrows(IllegalStateException::class.java) {
                Mp4BypassOptimizer.optimize(inputFile = input, outputFile = output)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAppearanceDefaults() {
        val defaultApp = AppAppearance.fromId(null)
        assertEquals(AppAppearance.AMOLED, defaultApp)

        val amoledColors = ThemePalette.forAppearance(AppAppearance.AMOLED)
        assertTrue(amoledColors.isAmoled)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledColors.background)
    }

    @Test
    fun testLanguageAndTips() {
        val enStrings = AppStrings.forLanguage(AppLanguage.EN)
        val viStrings = AppStrings.forLanguage(AppLanguage.VI)

        assertEquals("Lossless Video Quality Optimizer", enStrings.appSubtitle)
        assertEquals("Tối ưu & Giữ nguyên chất lượng video gốc", viStrings.appSubtitle)
        assertTrue(enStrings.tips.isNotEmpty())
        assertTrue(viStrings.tips.isNotEmpty())
        assertEquals(5, enStrings.tips.size)
        assertEquals(5, viStrings.tips.size)

        // Ensure no tips or strings mention "anti-haze" or "hazard"
        for (tip in enStrings.tips) {
            assertFalse(tip.first.lowercase().contains("haze"))
            assertFalse(tip.first.lowercase().contains("hazard"))
            assertFalse(tip.second.lowercase().contains("haze"))
            assertFalse(tip.second.lowercase().contains("hazard"))
        }
        for (tip in viStrings.tips) {
            assertFalse(tip.first.lowercase().contains("haze"))
            assertFalse(tip.first.lowercase().contains("hazard"))
            assertFalse(tip.second.lowercase().contains("haze"))
            assertFalse(tip.second.lowercase().contains("hazard"))
        }
    }

    @Test
    fun testRecordWithContentUri() {
        val record = CompressedVideoRecord(
            id = "123",
            displayName = "1026-01",
            originalName = "test.mp4",
            originalSizeMb = "10.0",
            optimizedSizeMb = "10.1",
            resolution = "1080x1920",
            durationSec = "15.0",
            timestamp = 1700000000L,
            outputPath = "Movies/RecompressBypass/test_v4.mp4",
            thumbnailFileName = null,
            logs = listOf("[START]", "[SUCCESS]"),
            contentUri = "content://media/external/video/media/999"
        )
        assertEquals("content://media/external/video/media/999", record.contentUri)
        assertEquals("test_v4.mp4", java.io.File(record.outputPath).name)
    }

    @Test
    fun testUpdateManagerVersionComparison() {
        // Kiểm tra xử lý trường hợp phiên bản 1.0.71 (1.0.7.1) và v1.0.81 (1.0.8.1)
        assertEquals(listOf(1, 0, 7, 1), UpdateManager.cleanVersion("1.0.71"))
        assertEquals(listOf(1, 0, 8, 1), UpdateManager.cleanVersion("v1.0.81"))
        assertEquals(listOf(1, 0, 8), UpdateManager.cleanVersion("v1.0.8"))

        // v1.0.8 PHẢI mới hơn 1.0.71
        assertTrue(UpdateManager.isNewer("1.0.71", "v1.0.8"))

        // v1.0.8 mới hơn 1.0.7
        assertTrue(UpdateManager.isNewer("1.0.7", "v1.0.8"))

        // v1.0.8 không mới hơn 1.0.8
        assertFalse(UpdateManager.isNewer("1.0.8", "v1.0.8"))

        // v1.0.8.1 mới hơn 1.0.8
        assertTrue(UpdateManager.isNewer("1.0.8", "v1.0.8.1"))
        assertFalse(UpdateManager.isNewer("1.0.8.1", "v1.0.8"))
        assertFalse(UpdateManager.isNewer("1.0.8.1", "1.0.8.1"))

        // v1.1.0 mới hơn 1.0.8.1, v1.0.81 và 1.0.9
        assertTrue(UpdateManager.isNewer("1.0.8.1", "v1.1.0"))
        assertTrue(UpdateManager.isNewer("1.0.9", "v1.1.0"))
        assertFalse(UpdateManager.isNewer("1.1.0", "v1.0.8.1"))
        assertFalse(UpdateManager.isNewer("1.1.0", "v1.0.81"))
        assertFalse(UpdateManager.isNewer("1.1.0", "1.1.0"))

        // v1.1.1 mới hơn 1.1.0
        assertTrue(UpdateManager.isNewer("1.1.0", "v1.1.1"))
        assertFalse(UpdateManager.isNewer("1.1.1", "v1.1.0"))
        assertFalse(UpdateManager.isNewer("1.1.1", "1.1.1"))

        // v1.2.1 mới hơn 1.2.0
        assertTrue(UpdateManager.isNewer("1.2.0", "v1.2.1"))
        assertFalse(UpdateManager.isNewer("1.2.1", "v1.2.0"))
        assertFalse(UpdateManager.isNewer("1.2.1", "1.2.1"))
    }

    @Test
    fun testUpdateManagerDownloadUrlSecurity() {
        val githubReleaseUrl =
            "https://github.com/j7x69/Recompress-Bypass/releases/download/v1.2.1/RecompressBypass-debug.apk"
        val cdnRedirectUrl = "https://release-assets.githubusercontent.com/github-production-release-asset/123/file.apk"
        val objectsCdnUrl = "https://objects.githubusercontent.com/github-production-release-asset/123/file.apk"
        val fakeRepoUrl = "https://github.com/hacker/Recompress-Bypass/releases/download/v1.0.8.1/RecompressBypass-debug.apk"
        val maliciousUrl = "https://malicious-site.com/apk/virus.apk"

        assertTrue(UpdateManager.isValidDownloadUrl(githubReleaseUrl))
        assertTrue(UpdateManager.isValidDownloadUrl(cdnRedirectUrl))
        assertTrue(UpdateManager.isValidDownloadUrl(objectsCdnUrl))
        assertFalse(UpdateManager.isValidDownloadUrl(fakeRepoUrl))
        assertFalse(UpdateManager.isValidDownloadUrl(maliciousUrl))
    }

    @Test
    fun testRemoteNoticeLanguageFallback() {
        // Trường hợp 1: Chỉ có bản gốc trong notice.json -> hiển thị nguyên gốc trực tiếp không qua auto-translate
        val noticeViOnly = RemoteNotice(
            active = true,
            level = "warning",
            title = "Cảnh báo bảo trì",
            message = "TikTok cập nhật thuật toán"
        )
        val viContent = NoticeTranslator.resolveNoticeContent(noticeViOnly, AppLanguage.VI)
        assertEquals("Cảnh báo bảo trì", viContent.first)
        assertEquals("TikTok cập nhật thuật toán", viContent.second)

        val enFallback = NoticeTranslator.resolveNoticeContent(noticeViOnly, AppLanguage.EN)
        assertEquals("Cảnh báo bảo trì", enFallback.first)
        assertEquals("TikTok cập nhật thuật toán", enFallback.second)

        // Trường hợp 2: Có khai báo title_en / message_en trong notice.json
        val noticeWithEn = RemoteNotice(
            active = true,
            level = "info",
            title = "Cập nhật quan trọng",
            message = "Đã có bản vá lỗi",
            titleEn = "Important Update",
            messageEn = "Bug fix available"
        )
        val enResolved = NoticeTranslator.resolveNoticeContent(noticeWithEn, AppLanguage.EN)
        assertEquals("Important Update", enResolved.first)
        assertEquals("Bug fix available", enResolved.second)
    }
}
