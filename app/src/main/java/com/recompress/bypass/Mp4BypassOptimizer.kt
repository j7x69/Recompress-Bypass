package com.recompress.bypass

import java.io.File

/**
 * Giao diện gọi tối ưu hóa MP4/MOV v4.6, ủy quyền trực tiếp cho lõi Rust Native Engine.
 */
object Mp4BypassOptimizer {

    fun optimize(
        inputFile: File,
        outputFile: File,
        onLog: (String) -> Unit = {}
    ) {
        if (!inputFile.exists() || !inputFile.isFile) {
            throw IllegalArgumentException("Input video file not found.")
        }
        if (inputFile.canonicalPath == outputFile.canonicalPath) {
            throw IllegalArgumentException("Input and output paths must be different.")
        }
        if (!RustCoreBridge.isNativeAvailable) {
            throw IllegalStateException("Native engine is not available on this device.")
        }

        if (outputFile.exists()) {
            outputFile.delete()
        }

        RustCoreBridge.optimizeMp4WithRust(inputFile, outputFile, onLog)
    }
}
