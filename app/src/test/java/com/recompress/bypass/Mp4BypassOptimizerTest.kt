package com.recompress.bypass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Mp4BypassOptimizerTest {

    @Test
    fun testWatermarkDetection() {
        val hazeData = "Created with Haze Quality Method".toByteArray(Charsets.ISO_8859_1)
        val vagueData = "vague-infinity engine".toByteArray(Charsets.ISO_8859_1)
        val cleanData = "Clean Apple QuickTime Movie".toByteArray(Charsets.ISO_8859_1)

        assertTrue(Mp4BypassOptimizer.isHazeOrWatermark(hazeData))
        assertTrue(Mp4BypassOptimizer.isHazeOrWatermark(vagueData))
        assertFalse(Mp4BypassOptimizer.isHazeOrWatermark(cleanData))
    }

    @Test
    fun testBuildCleanBytedanceUdta() {
        val udtaBox = Mp4BypassOptimizer.buildCleanBytedanceUdta()
        assertTrue(udtaBox.size > 8)

        val buf = ByteBuffer.wrap(udtaBox).order(ByteOrder.BIG_ENDIAN)
        val size = buf.int
        val type = String(udtaBox, 4, 4, Charsets.ISO_8859_1)

        assertEquals(udtaBox.size, size)
        assertEquals("udta", type)

        // Verify te_is_reencode and Lavf63.7.100 are present in the box payload
        val content = String(udtaBox, Charsets.ISO_8859_1)
        assertTrue(content.contains("te_is_reencode"))
        assertTrue(content.contains("com.apple.quicktime.video"))
        assertTrue(content.contains("Lavf63.7.100"))
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

        // v1.1.4 mới hơn 1.1.1
        assertTrue(UpdateManager.isNewer("1.1.1", "v1.1.4"))
        assertFalse(UpdateManager.isNewer("1.1.4", "v1.1.1"))
        assertFalse(UpdateManager.isNewer("1.1.4", "1.1.4"))
    }

    @Test
    fun testUpdateManagerDownloadUrlSecurity() {
        val githubReleaseUrl = "https://github.com/j7x69/Recompress-Bypass/releases/download/v1.1.0/RecompressBypass-debug.apk"
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
    fun testOptimizeRealSampleVideo() {
        val sampleFile = java.io.File("G:/videos/davinci export/AI.mp4")
        if (!sampleFile.exists()) return

        val outFile = java.io.File("G:/videos/davinci export/AI_test_v4_out.mp4")
        if (outFile.exists()) outFile.delete()

        try {
            Mp4BypassOptimizer.optimize(
                inputFile = sampleFile,
                outputFile = outFile,
                ghostMultiplier = 9,
                onLog = { println("LOG: $it") }
            )
            println("OPTIMIZE SUCCESS! Out size: ${outFile.length()}")
            assertTrue(outFile.exists() && outFile.length() > 0)
        } catch (e: Throwable) {
            println("OPTIMIZE THREW EXCEPTION: ${e.javaClass.name}: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            if (outFile.exists()) outFile.delete()
        }
    }
}
