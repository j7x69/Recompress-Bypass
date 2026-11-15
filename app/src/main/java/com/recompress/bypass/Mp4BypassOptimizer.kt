package com.recompress.bypass

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Mp4BypassOptimizer {

    private const val CHUNK_SIZE = 4 * 1024 * 1024
    private const val MAX_MOOV_SIZE = 256L * 1024L * 1024L
    private const val POISON_SAMPLE_COUNT = 4
    private const val LFE2_FRAME_LEN = 15

    private const val MASK_KEY = 0x5A
    private val MASKED_PCE_TAIL = intArrayOf(
        0x00 xor MASK_KEY,
        0x40 xor MASK_KEY,
        0x01 xor MASK_KEY,
        0x28 xor MASK_KEY,
        0x00 xor MASK_KEY,
        0x64 xor MASK_KEY,
        0x00 xor MASK_KEY,
        0x00 xor MASK_KEY,
        0x03 xor MASK_KEY,
        0x50 xor MASK_KEY,
        0x00 xor MASK_KEY,
        0x00 xor MASK_KEY,
        0x38 xor MASK_KEY
    )

    const val PROJECT_UDTA_TEXT = "https://github.com/j7x69/Recompress-Bypass"

    private val FREE_BOX = byteArrayOf(
        0x00, 0x00, 0x00, 0x08,
        'f'.code.toByte(), 'r'.code.toByte(), 'e'.code.toByte(), 'e'.code.toByte()
    )

    private val WATERMARK_SIGNATURES = listOf(
        "haze",
        "haze quality method",
        "hazemethod",
        "haze max",
        "vague-infinity",
        "vague transcode",
        "nova normal"
    )

    class MP4Box(
        val type: String,
        val pos: Int,
        val size: Long,
        val headerLen: Int,
        val data: ByteArray,
        val payload: ByteArray
    )

    class FileBoxHeader(
        val type: String,
        val pos: Long,
        val size: Long,
        val headerLen: Int
    )

    private data class AudioSampleLoc(
        val sampleIdx: Int,
        val chunkIdx: Int,
        val absFilePos: Long,
        val sampleSize: Long
    )

    fun isHazeOrWatermark(data: ByteArray): Boolean {
        val str = String(data, Charsets.ISO_8859_1).lowercase()
        return WATERMARK_SIGNATURES.any { str.contains(it) }
    }

    fun makeLfe2InbandPceFrame(sfIdx: Int = 3): ByteArray {
        val out = ByteArray(LFE2_FRAME_LEN)
        out[0] = (0xFA xor MASK_KEY).toByte() // 0xA0
        out[1] = (0x80 or ((sfIdx and 0x0F) shl 3)).toByte()
        for (i in MASKED_PCE_TAIL.indices) {
            out[2 + i] = (MASKED_PCE_TAIL[i] xor MASK_KEY).toByte()
        }
        return out
    }

    fun buildCleanProjectUdta(): ByteArray {
        if (RustCoreBridge.isNativeAvailable) {
            return RustCoreBridge.nativeBuildUdtaV46()
        }
        val tagBytes = PROJECT_UDTA_TEXT.toByteArray(Charsets.ISO_8859_1)
        val hdlrPayload = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .put(byteArrayOf(0, 0, 0, 0))
            .put("mdir".toByteArray(Charsets.ISO_8859_1))
            .put(ByteArray(13))
            .array()
        val hdlrBox = packBox("hdlr", hdlrPayload)

        val dataPayload = ByteBuffer.allocate(8 + tagBytes.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(1)
            .putInt(0)
            .put(tagBytes)
            .array()
        val dataBox = packBox("data", dataPayload)
        val tooBox = packBox("\u00a9too", dataBox)
        val ilstBox = packBox("ilst", tooBox)

        val metaPayload =
            ByteBuffer.allocate(4 + hdlrBox.size + ilstBox.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(0)
                .put(hdlrBox)
                .put(ilstBox)
                .array()
        val metaBox = packBox("meta", metaPayload)
        return packBox("udta", metaBox)
    }

    fun buildCleanBytedanceUdta(): ByteArray = buildCleanProjectUdta()

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

        if (outputFile.exists()) {
            outputFile.delete()
        }

        if (RustCoreBridge.isNativeAvailable) {
            RustCoreBridge.optimizeMp4WithRust(inputFile, outputFile, onLog)
            return
        }

        optimizeJvmFallbackV46(inputFile, outputFile, onLog)
    }

    private fun optimizeJvmFallbackV46(
        inputFile: File,
        outputFile: File,
        onLog: (String) -> Unit
    ) {
        val fileSize = inputFile.length()
        if (fileSize < 32L) {
            throw IllegalStateException("Video file is too small or corrupted.")
        }

        onLog("[Core] Initializing v4.6 stream...")

        var hasFtyp = false
        var moovData: ByteArray? = null
        var mdatInfo: FileBoxHeader? = null

        RandomAccessFile(inputFile, "r").use { raf ->
            var pos = 0L
            val hdr = ByteArray(8)
            val extHdr = ByteArray(8)
            while (pos + 8 <= fileSize) {
                raf.seek(pos)
                if (raf.read(hdr) != 8) break
                val size32 = readUInt32(hdr, 0)
                val type = String(hdr, 4, 4, Charsets.ISO_8859_1)
                var hdrLen = 8
                val size = when (size32) {
                    1L -> {
                        if (raf.read(extHdr) != 8) break
                        hdrLen = 16
                        readUInt64(extHdr, 0)
                    }

                    0L -> fileSize - pos
                    else -> size32
                }
                if (size < hdrLen || pos + size > fileSize) break

                when {
                    type == "ftyp" && !hasFtyp -> hasFtyp = true
                    type == "moov" && moovData == null -> {
                        if (size > MAX_MOOV_SIZE) {
                            throw IllegalStateException("moov atom exceeds 256MB limit.")
                        }
                        val buf = ByteArray(size.toInt())
                        raf.seek(pos)
                        raf.readFully(buf)
                        moovData = buf
                    }

                    type == "mdat" && mdatInfo == null -> {
                        mdatInfo = FileBoxHeader(type, pos, size, hdrLen)
                    }
                }
                pos += size
            }
        }

        val moovBytes = moovData ?: throw IllegalStateException("Missing moov atom in MP4.")
        val mdat = mdatInfo ?: throw IllegalStateException("Missing mdat atom in MP4.")
        if (!hasFtyp) throw IllegalStateException("Missing ftyp atom in MP4.")

        onLog("[Core] Processing container layout...")

        val cleanFtyp = packBox(
            "ftyp",
            "mp42\u0000\u0000\u0002\u0000mp42iso2mp41".toByteArray(Charsets.ISO_8859_1)
        )
        val moovBoxes = parseBoxes(moovBytes, 8)
        val mvhdBox = moovBoxes.firstOrNull { it.type == "mvhd" }
            ?: throw IllegalStateException("Missing mvhd atom.")
        val mvhdP = mvhdBox.payload
        if (mvhdP.size < 20) throw IllegalStateException("Invalid mvhd payload.")

        val mvhdVer = mvhdP[0].toInt() and 0xFF
        var origMovieTs = if (mvhdVer == 0) readUInt32(mvhdP, 12) else readUInt32(mvhdP, 20)
        val origMovieDur = if (mvhdVer == 0) readUInt32(mvhdP, 16) else readUInt64(mvhdP, 24)
        if (origMovieTs <= 0L) origMovieTs = 1000L

        var videoTrakBox: MP4Box? = null
        var audioTrakBox: MP4Box? = null
        for (b in moovBoxes) {
            if (b.type == "trak") {
                val mdia = findBox(b.data, "mdia")
                val hdlr = mdia?.let { findBox(it.data, "hdlr") }
                val subtype = if (hdlr != null && hdlr.data.size >= 20) {
                    String(hdlr.data, 16, 4, Charsets.ISO_8859_1)
                } else {
                    ""
                }
                if (subtype == "vide" && videoTrakBox == null) videoTrakBox = b
                else if (subtype == "soun" && audioTrakBox == null) audioTrakBox = b
            }
        }

        val videoTrak = videoTrakBox ?: throw IllegalStateException("Video track not found.")
        val audioTrak = audioTrakBox ?: throw IllegalStateException("Audio track not found.")

        val aMdia =
            findBox(audioTrak.data, "mdia") ?: throw IllegalStateException("Missing audio mdia")
        val aMdhd = findBox(aMdia.data, "mdhd") ?: throw IllegalStateException("Missing audio mdhd")
        val audioTs = if ((aMdhd.payload[0].toInt() and 0xFF) == 0) {
            readUInt32(aMdhd.payload, 12).toInt()
        } else {
            readUInt32(aMdhd.payload, 20).toInt()
        }
        val aMinf = findBox(aMdia.data, "minf") ?: throw IllegalStateException("Missing audio minf")
        val aStbl = findBox(aMinf.data, "stbl") ?: throw IllegalStateException("Missing audio stbl")
        val aStsd = findBox(aStbl.data, "stsd") ?: throw IllegalStateException("Missing audio stsd")
        val aStsz = findBox(aStbl.data, "stsz") ?: throw IllegalStateException("Missing audio stsz")
        val aStsc = findBox(aStbl.data, "stsc") ?: throw IllegalStateException("Missing audio stsc")
        val aStco = findBox(aStbl.data, "stco") ?: findBox(aStbl.data, "co64")
        ?: throw IllegalStateException("Missing audio stco/co64")

        val sfIdx = extractSfIdxFromStsd(aStsd.payload, audioTs)
        val lfe2Frame = makeLfe2InbandPceFrame(sfIdx)

        val ss = readUInt32(aStsz.payload, 4)
        val nSamples = readUInt32(aStsz.payload, 8).toInt()
        val origSampleSizes = LongArray(nSamples) { i ->
            if (ss == 0L) readUInt32(aStsz.payload, 12 + 4 * i) else ss
        }

        val nChunks = readUInt32(aStco.payload, 4).toInt()
        val origChunkOffsets = LongArray(nChunks) { i ->
            if (aStco.type == "stco") readUInt32(aStco.payload, 8 + 4 * i)
            else readUInt64(aStco.payload, 8 + 8 * i)
        }

        val nStsc = readUInt32(aStsc.payload, 4).toInt()
        val origStscEntries = Array(nStsc) { i ->
            val base = 8 + 12 * i
            Triple(
                readUInt32(aStsc.payload, base).toInt(),
                readUInt32(aStsc.payload, base + 4).toInt(),
                readUInt32(aStsc.payload, base + 8).toInt()
            )
        }

        val sampleLayout = expandAudioLayout(origChunkOffsets, origSampleSizes, origStscEntries)
        if (sampleLayout.isEmpty()) throw IllegalStateException("Audio track contains no samples.")

        val poisonTargets =
            sampleLayout.takeLast(POISON_SAMPLE_COUNT.coerceAtMost(sampleLayout.size))
        val targetIndices = poisonTargets.map { it.sampleIdx }.toSet()
        val lastSampleIdx = sampleLayout.last().sampleIdx
        val newSampleSizes = origSampleSizes.copyOf()

        val origMdatPayloadStart = mdat.pos + mdat.headerLen
        val origMdatPayloadLen = mdat.size - mdat.headerLen
        val origMdatEnd = origMdatPayloadStart + origMdatPayloadLen

        val affectedChunks = poisonTargets.map { it.chunkIdx }.toSortedSet().toList()
        val inplacePatches = mutableListOf<Pair<Long, ByteArray>>()
        val relocatedChunkRelOffsets = mutableMapOf<Int, Long>()
        var tailAppendBuf = ByteArray(0)
        var mdatTrimEnd = origMdatPayloadLen

        RandomAccessFile(inputFile, "r").use { raf ->
            for (cI in affectedChunks) {
                val cSamples = sampleLayout.filter { it.chunkIdx == cI }
                val cStartAbs = origChunkOffsets[cI]
                val cOrigTotalLen = cSamples.sumOf { it.sampleSize }
                val cEndAbs = cStartAbs + cOrigTotalLen
                val isAtMdatEof = (cEndAbs == origMdatEnd)

                val canPatchInplace = cSamples.none {
                    it.sampleIdx in targetIndices && it.sampleSize < LFE2_FRAME_LEN && !isAtMdatEof
                }

                if (canPatchInplace) {
                    if (isAtMdatEof) {
                        val rebuilt = ByteArrayOutputStream()
                        for (item in cSamples) {
                            when {
                                item.sampleIdx == lastSampleIdx -> {
                                    newSampleSizes[item.sampleIdx] = LFE2_FRAME_LEN.toLong()
                                    rebuilt.write(lfe2Frame)
                                }

                                item.sampleIdx in targetIndices -> {
                                    val newSz =
                                        item.sampleSize.toInt().coerceAtLeast(LFE2_FRAME_LEN)
                                    newSampleSizes[item.sampleIdx] = newSz.toLong()
                                    rebuilt.write(lfe2Frame)
                                    if (newSz > LFE2_FRAME_LEN) {
                                        rebuilt.write(ByteArray(newSz - LFE2_FRAME_LEN))
                                    }
                                }

                                else -> {
                                    val origBuf = ByteArray(item.sampleSize.toInt())
                                    raf.seek(item.absFilePos)
                                    raf.readFully(origBuf)
                                    rebuilt.write(origBuf)
                                }
                            }
                        }
                        mdatTrimEnd = cStartAbs - origMdatPayloadStart
                        tailAppendBuf = rebuilt.toByteArray() + tailAppendBuf
                    } else {
                        for (item in cSamples) {
                            if (item.sampleIdx in targetIndices) {
                                val patch = ByteArray(item.sampleSize.toInt())
                                System.arraycopy(lfe2Frame, 0, patch, 0, LFE2_FRAME_LEN)
                                inplacePatches.add((item.absFilePos - origMdatPayloadStart) to patch)
                            }
                        }
                    }
                } else {
                    val relOff = origMdatPayloadLen + tailAppendBuf.size.toLong()
                    relocatedChunkRelOffsets[cI] = relOff
                    val appended = ByteArrayOutputStream()
                    appended.write(tailAppendBuf)
                    for (item in cSamples) {
                        when {
                            item.sampleIdx == lastSampleIdx -> {
                                newSampleSizes[item.sampleIdx] = LFE2_FRAME_LEN.toLong()
                                appended.write(lfe2Frame)
                            }

                            item.sampleIdx in targetIndices -> {
                                val newSz = item.sampleSize.toInt().coerceAtLeast(LFE2_FRAME_LEN)
                                newSampleSizes[item.sampleIdx] = newSz.toLong()
                                appended.write(lfe2Frame)
                                if (newSz > LFE2_FRAME_LEN) {
                                    appended.write(ByteArray(newSz - LFE2_FRAME_LEN))
                                }
                            }

                            else -> {
                                val origBuf = ByteArray(item.sampleSize.toInt())
                                raf.seek(item.absFilePos)
                                raf.readFully(origBuf)
                                appended.write(origBuf)
                            }
                        }
                    }
                    tailAppendBuf = appended.toByteArray()
                }
            }
        }

        if (mdatTrimEnd != origMdatPayloadLen && relocatedChunkRelOffsets.isNotEmpty()) {
            val delta = origMdatPayloadLen - mdatTrimEnd
            val lastAffected = affectedChunks.last()
            val eofChunkLen = sampleLayout
                .filter { it.chunkIdx == lastAffected }
                .sumOf { newSampleSizes[it.sampleIdx] }
            for (k in relocatedChunkRelOffsets.keys.toList()) {
                relocatedChunkRelOffsets[k] = relocatedChunkRelOffsets[k]!! - delta + eofChunkLen
            }
        }

        val newMdatPayloadLen = mdatTrimEnd + tailAppendBuf.size.toLong()
        val newMdatHdr = if (newMdatPayloadLen + 8L > 0xFFFFFFFFL) {
            ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .putInt(1)
                .put("mdat".toByteArray(Charsets.ISO_8859_1))
                .putLong(newMdatPayloadLen + 16L)
                .array()
        } else {
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt((newMdatPayloadLen + 8L).toInt())
                .put("mdat".toByteArray(Charsets.ISO_8859_1))
                .array()
        }

        val newMovieDurMs = scaleCeil1000(origMovieDur, origMovieTs)
        val mvhdV0 = ByteArray(100)
        if (mvhdP.size == 100) {
            System.arraycopy(mvhdP, 0, mvhdV0, 0, 100)
        } else {
            putInt32(mvhdV0, 20, 0x00010000)
            mvhdV0[24] = 0x01
            mvhdV0[25] = 0x00
            val identityMatrix = byteArrayOf(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x40, 0x00, 0x00, 0x00
            )
            System.arraycopy(identityMatrix, 0, mvhdV0, 36, identityMatrix.size)
        }
        mvhdV0[0] = 0; mvhdV0[1] = 0; mvhdV0[2] = 0; mvhdV0[3] = 0
        putInt32(mvhdV0, 12, 1000)
        putInt32(mvhdV0, 16, newMovieDurMs.toInt())
        putInt32(mvhdV0, 96, 3)
        val newMvhd = packBox("mvhd", mvhdV0)
        val newUdta = buildCleanProjectUdta()

        fun buildVideoTrak(shiftAmount: Long): ByteArray {
            val out = ByteArrayOutputStream()
            for (b in parseBoxes(videoTrak.data, 8)) {
                when (b.type) {
                    "tref" -> continue
                    "tkhd" -> {
                        val p = b.payload.copyOf()
                        if ((p[0].toInt() and 0xFF) == 0) {
                            putInt32(p, 12, 1)
                            val oldD = readUInt32(p, 20)
                            putInt32(p, 20, scaleCeil1000(oldD, origMovieTs).toInt())
                        } else {
                            putInt32(p, 20, 1)
                            val oldD = readUInt64(p, 28)
                            putInt64(p, 28, scaleCeil1000(oldD, origMovieTs))
                        }
                        out.write(packBox("tkhd", p))
                    }

                    "edts" -> {
                        val elst = findBox(b.data, "elst")
                        if (elst != null) {
                            val ep = elst.payload.copyOf()
                            val ver = ep[0].toInt() and 0xFF
                            val cnt = readUInt32(ep, 4).toInt()
                            for (i in 0 until cnt) {
                                if (ver == 0) {
                                    val off = 8 + 12 * i
                                    putInt32(
                                        ep,
                                        off,
                                        scaleCeil1000(readUInt32(ep, off), origMovieTs).toInt()
                                    )
                                } else {
                                    val off = 8 + 20 * i
                                    putInt64(
                                        ep,
                                        off,
                                        scaleCeil1000(readUInt64(ep, off), origMovieTs)
                                    )
                                }
                            }
                            out.write(packBox("edts", packBox("elst", ep)))
                        } else {
                            out.write(b.data)
                        }
                    }

                    "mdia" -> {
                        val mdiaOut = ByteArrayOutputStream()
                        for (mb in parseBoxes(b.data, 8)) {
                            if (mb.type == "minf") {
                                val minfOut = ByteArrayOutputStream()
                                for (mib in parseBoxes(mb.data, 8)) {
                                    if (mib.type == "stbl") {
                                        val stblOut = ByteArrayOutputStream()
                                        for (sb in parseBoxes(mib.data, 8)) {
                                            when (sb.type) {
                                                "stco", "co64" -> {
                                                    val sp = sb.payload
                                                    val cnt = readUInt32(sp, 4).toInt()
                                                    val offs = LongArray(cnt) { i ->
                                                        if (sb.type == "stco") readUInt32(
                                                            sp,
                                                            8 + 4 * i
                                                        ) + shiftAmount
                                                        else readUInt64(sp, 8 + 8 * i) + shiftAmount
                                                    }
                                                    val useCo64 =
                                                        sb.type == "co64" || offs.any { it > 0xFFFFFFFFL }
                                                    val np = ByteArrayOutputStream()
                                                    np.write(sp, 0, 4)
                                                    np.write(intToBytes(cnt))
                                                    if (useCo64) {
                                                        for (o in offs) np.write(longToBytes(o))
                                                        stblOut.write(
                                                            packBox(
                                                                "co64",
                                                                np.toByteArray()
                                                            )
                                                        )
                                                    } else {
                                                        for (o in offs) np.write(intToBytes(o.toInt()))
                                                        stblOut.write(
                                                            packBox(
                                                                "stco",
                                                                np.toByteArray()
                                                            )
                                                        )
                                                    }
                                                }

                                                "stsd" -> {
                                                    val stsdP = sb.payload.copyOf()
                                                    if (stsdP.size >= 90) {
                                                        stsdP.fill(0, 58, 90)
                                                    }
                                                    stblOut.write(packBox("stsd", stsdP))
                                                }

                                                else -> stblOut.write(sb.data)
                                            }
                                        }
                                        minfOut.write(packBox("stbl", stblOut.toByteArray()))
                                    } else {
                                        minfOut.write(mib.data)
                                    }
                                }
                                mdiaOut.write(packBox("minf", minfOut.toByteArray()))
                            } else {
                                mdiaOut.write(mb.data)
                            }
                        }
                        out.write(packBox("mdia", mdiaOut.toByteArray()))
                    }

                    else -> out.write(b.data)
                }
            }
            return packBox("trak", out.toByteArray())
        }

        fun buildAudioTrak(shiftAmount: Long, newMdatPayloadPos: Long): ByteArray {
            val out = ByteArrayOutputStream()
            for (b in parseBoxes(audioTrak.data, 8)) {
                when (b.type) {
                    "tkhd" -> {
                        val p = b.payload.copyOf()
                        if ((p[0].toInt() and 0xFF) == 0) {
                            putInt32(p, 12, 2)
                            val oldD = readUInt32(p, 20)
                            putInt32(p, 20, scaleCeil1000(oldD, origMovieTs).toInt())
                        } else {
                            putInt32(p, 20, 2)
                            val oldD = readUInt64(p, 28)
                            putInt64(p, 28, scaleCeil1000(oldD, origMovieTs))
                        }
                        out.write(packBox("tkhd", p))
                    }

                    "edts" -> {
                        val elst = findBox(b.data, "elst")
                        if (elst != null) {
                            val ep = elst.payload.copyOf()
                            val ver = ep[0].toInt() and 0xFF
                            val cnt = readUInt32(ep, 4).toInt()
                            for (i in 0 until cnt) {
                                if (ver == 0) {
                                    val off = 8 + 12 * i
                                    putInt32(
                                        ep,
                                        off,
                                        scaleFloor1000(readUInt32(ep, off), origMovieTs).toInt()
                                    )
                                } else {
                                    val off = 8 + 20 * i
                                    putInt64(
                                        ep,
                                        off,
                                        scaleFloor1000(readUInt64(ep, off), origMovieTs)
                                    )
                                }
                            }
                            out.write(packBox("edts", packBox("elst", ep)))
                        } else {
                            out.write(b.data)
                        }
                    }

                    "mdia" -> {
                        val mdiaOut = ByteArrayOutputStream()
                        for (mb in parseBoxes(b.data, 8)) {
                            if (mb.type == "minf") {
                                val minfOut = ByteArrayOutputStream()
                                for (mib in parseBoxes(mb.data, 8)) {
                                    if (mib.type == "stbl") {
                                        val stblOut = ByteArrayOutputStream()
                                        for (sb in parseBoxes(mib.data, 8)) {
                                            when (sb.type) {
                                                "stsd" -> stblOut.write(cleanAudioStsdClassic(sb.payload))
                                                "stsz" -> {
                                                    val np = ByteArrayOutputStream()
                                                    np.write(sb.payload, 0, 4)
                                                    np.write(intToBytes(0))
                                                    np.write(intToBytes(newSampleSizes.size))
                                                    for (sz in newSampleSizes) np.write(
                                                        intToBytes(
                                                            sz.toInt()
                                                        )
                                                    )
                                                    stblOut.write(packBox("stsz", np.toByteArray()))
                                                }

                                                "stco", "co64" -> {
                                                    val newOffs =
                                                        LongArray(origChunkOffsets.size) { cI ->
                                                            relocatedChunkRelOffsets[cI]?.let { newMdatPayloadPos + it }
                                                                ?: (origChunkOffsets[cI] + shiftAmount)
                                                        }
                                                    val useCo64 =
                                                        sb.type == "co64" || newOffs.any { it > 0xFFFFFFFFL }
                                                    val np = ByteArrayOutputStream()
                                                    np.write(sb.payload, 0, 4)
                                                    np.write(intToBytes(newOffs.size))
                                                    if (useCo64) {
                                                        for (o in newOffs) np.write(longToBytes(o))
                                                        stblOut.write(
                                                            packBox(
                                                                "co64",
                                                                np.toByteArray()
                                                            )
                                                        )
                                                    } else {
                                                        for (o in newOffs) np.write(intToBytes(o.toInt()))
                                                        stblOut.write(
                                                            packBox(
                                                                "stco",
                                                                np.toByteArray()
                                                            )
                                                        )
                                                    }
                                                }

                                                else -> stblOut.write(sb.data)
                                            }
                                        }
                                        minfOut.write(packBox("stbl", stblOut.toByteArray()))
                                    } else {
                                        minfOut.write(mib.data)
                                    }
                                }
                                mdiaOut.write(packBox("minf", minfOut.toByteArray()))
                            } else {
                                mdiaOut.write(mb.data)
                            }
                        }
                        out.write(packBox("mdia", mdiaOut.toByteArray()))
                    }

                    else -> out.write(b.data)
                }
            }
            return packBox("trak", out.toByteArray())
        }

        fun buildMoov(shiftAmount: Long, newMdatPayloadPos: Long): ByteArray {
            val vt = buildVideoTrak(shiftAmount)
            val at = buildAudioTrak(shiftAmount, newMdatPayloadPos)
            val payload = ByteArrayOutputStream(newMvhd.size + vt.size + at.size + newUdta.size)
            payload.write(newMvhd)
            payload.write(vt)
            payload.write(at)
            payload.write(newUdta)
            return packBox("moov", payload.toByteArray())
        }

        val dummyMoov = buildMoov(0L, 0L)
        val moovSize = dummyMoov.size.toLong()
        val newMdatPos = cleanFtyp.size.toLong() + moovSize + FREE_BOX.size.toLong()
        val newMdatPayloadPos = newMdatPos + newMdatHdr.size.toLong()
        val shiftAmount = newMdatPayloadPos - origMdatPayloadStart

        val finalMoov = buildMoov(shiftAmount, newMdatPayloadPos)
        if (finalMoov.size.toLong() != moovSize) {
            throw IllegalStateException("Unexpected moov size change during layout.")
        }

        onLog("[Core] Writing optimized media stream...")
        inplacePatches.sortBy { it.first }

        RandomAccessFile(inputFile, "r").use { raf ->
            BufferedOutputStream(FileOutputStream(outputFile), CHUNK_SIZE).use { out ->
                out.write(cleanFtyp)
                out.write(finalMoov)
                out.write(FREE_BOX)
                out.write(newMdatHdr)

                raf.seek(origMdatPayloadStart)
                var curRel = 0L
                var patchIdx = 0
                val buf = ByteArray(CHUNK_SIZE)

                while (curRel < mdatTrimEnd) {
                    if (patchIdx < inplacePatches.size && inplacePatches[patchIdx].first == curRel) {
                        val pBytes = inplacePatches[patchIdx].second
                        out.write(pBytes)
                        curRel += pBytes.size.toLong()
                        raf.seek(origMdatPayloadStart + curRel)
                        patchIdx++
                    } else {
                        var nextStop = mdatTrimEnd
                        if (patchIdx < inplacePatches.size) {
                            nextStop = minOf(nextStop, inplacePatches[patchIdx].first)
                        }
                        val toRead = minOf(CHUNK_SIZE.toLong(), nextStop - curRel).toInt()
                        val n = raf.read(buf, 0, toRead)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        curRel += n.toLong()
                    }
                }

                if (tailAppendBuf.isNotEmpty()) {
                    out.write(tailAppendBuf)
                }
                out.flush()
            }
        }

        onLog("[Core] Finalizing output...")
    }

    private fun extractSfIdxFromStsd(stsdPayload: ByteArray, audioTs: Int): Int {
        var sfIdx = when (audioTs) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 3
        }
        val entries = parseBoxes(stsdPayload, 8)
        val entry1 = entries.firstOrNull() ?: return sfIdx
        for (sb in parseBoxes(entry1.data, 36)) {
            if (sb.type == "esds") {
                val p = sb.payload
                for (i in 0..p.size - 7) {
                    if (p[i] == 0x05.toByte() &&
                        p[i + 1] == 0x80.toByte() &&
                        p[i + 2] == 0x80.toByte() &&
                        p[i + 3] == 0x80.toByte()
                    ) {
                        val a0 = p[i + 5].toInt() and 0xFF
                        val a1 = p[i + 6].toInt() and 0xFF
                        sfIdx = ((a0 and 0x07) shl 1) or (a1 ushr 7)
                        break
                    }
                }
            }
        }
        return sfIdx
    }

    private fun cleanAudioStsdClassic(stsdPayload: ByteArray): ByteArray {
        val entries = parseBoxes(stsdPayload, 8)
        val entry1 = entries.firstOrNull() ?: return packBox("stsd", stsdPayload)
        if (entry1.data.size < 36) return packBox("stsd", stsdPayload)

        val mp4aPayload = ByteArrayOutputStream()
        mp4aPayload.write(entry1.data, 8, 28)
        for (sb in parseBoxes(entry1.data, 36)) {
            if (sb.type != "btrt") {
                mp4aPayload.write(sb.data)
            }
        }
        val cleanMp4a = packBox("mp4a", mp4aPayload.toByteArray())
        val stsdOut = ByteArrayOutputStream(8 + cleanMp4a.size)
        if (stsdPayload.size >= 4) stsdOut.write(stsdPayload, 0, 4)
        else stsdOut.write(ByteArray(4))
        stsdOut.write(intToBytes(1))
        stsdOut.write(cleanMp4a)
        return packBox("stsd", stsdOut.toByteArray())
    }

    private fun expandAudioLayout(
        chunkOffsets: LongArray,
        sampleSizes: LongArray,
        stscEntries: Array<Triple<Int, Int, Int>>
    ): List<AudioSampleLoc> {
        val result = ArrayList<AudioSampleLoc>(sampleSizes.size)
        var sIdx = 0
        for (cI in chunkOffsets.indices) {
            val cNum = cI + 1
            var spc = stscEntries.firstOrNull()?.second ?: 1
            for ((fc, sCnt, _) in stscEntries) {
                if (cNum >= fc) spc = sCnt else break
            }
            val cPos = chunkOffsets[cI]
            var offInC = 0L
            for (k in 0 until spc) {
                if (sIdx >= sampleSizes.size) break
                val sz = sampleSizes[sIdx]
                result.add(AudioSampleLoc(sIdx, cI, cPos + offInC, sz))
                offInC += sz
                sIdx++
            }
        }
        return result
    }

    private fun scaleCeil1000(valNum: Long, origTs: Long): Long {
        if (origTs == 1000L || origTs <= 0L) return valNum
        return (valNum * 1000L + origTs - 1L) / origTs
    }

    private fun scaleFloor1000(valNum: Long, origTs: Long): Long {
        if (origTs == 1000L || origTs <= 0L) return valNum
        return (valNum * 1000L) / origTs
    }

    private fun parseBoxes(data: ByteArray, offset: Int = 0, end: Int = data.size): List<MP4Box> {
        val limit = end.coerceAtMost(data.size)
        val boxes = mutableListOf<MP4Box>()
        var pos = offset
        while (pos + 8 <= limit) {
            val size32 = readUInt32(data, pos)
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            var hdrLen = 8
            val size = when (size32) {
                1L -> {
                    if (pos + 16 > limit) break
                    hdrLen = 16
                    readUInt64(data, pos + 8)
                }

                0L -> (limit - pos).toLong()
                else -> size32
            }
            if (size < hdrLen || pos.toLong() + size > limit.toLong()) break
            val sizeInt = size.toInt()
            boxes.add(
                MP4Box(
                    type = type,
                    pos = pos,
                    size = size,
                    headerLen = hdrLen,
                    data = data.copyOfRange(pos, pos + sizeInt),
                    payload = data.copyOfRange(pos + hdrLen, pos + sizeInt)
                )
            )
            pos += sizeInt
        }
        return boxes
    }

    private fun findBox(boxData: ByteArray, target: String): MP4Box? =
        parseBoxes(boxData, 8).firstOrNull { it.type == target }

    private fun packBox(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        val total = payload.size.toLong() + 8L
        return if (total > 0xFFFFFFFFL) {
            ByteBuffer.allocate(16 + payload.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(1)
                .put(typeBytes)
                .putLong(payload.size.toLong() + 16L)
                .put(payload)
                .array()
        } else {
            ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(total.toInt())
                .put(typeBytes)
                .put(payload)
                .array()
        }
    }

    private fun readUInt32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
                ((b[off + 1].toLong() and 0xFF) shl 16) or
                ((b[off + 2].toLong() and 0xFF) shl 8) or
                (b[off + 3].toLong() and 0xFF)

    private fun readUInt64(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 56) or
                ((b[off + 1].toLong() and 0xFF) shl 48) or
                ((b[off + 2].toLong() and 0xFF) shl 40) or
                ((b[off + 3].toLong() and 0xFF) shl 32) or
                ((b[off + 4].toLong() and 0xFF) shl 24) or
                ((b[off + 5].toLong() and 0xFF) shl 16) or
                ((b[off + 6].toLong() and 0xFF) shl 8) or
                (b[off + 7].toLong() and 0xFF)

    private fun putInt32(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 24) and 0xFF).toByte()
        b[off + 1] = ((v ushr 16) and 0xFF).toByte()
        b[off + 2] = ((v ushr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    private fun putInt64(b: ByteArray, off: Int, v: Long) {
        for (i in 0 until 8) {
            b[off + i] = ((v ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
    }

    private fun intToBytes(v: Int): ByteArray =
        byteArrayOf(
            ((v ushr 24) and 0xFF).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            (v and 0xFF).toByte()
        )

    private fun longToBytes(v: Long): ByteArray =
        ByteArray(8) { i -> ((v ushr ((7 - i) * 8)) and 0xFF).toByte() }
}
