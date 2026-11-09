package com.recompress.bypass

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Mp4BypassOptimizer {

    private const val CHUNK_SIZE = 16 * 1024 * 1024
    private const val MAX_MOOV_SIZE = 256L * 1024L * 1024L

    private const val MASK_KEY = 0x5A
    private val MASKED_FRAME = intArrayOf(0x7B, 0x4A, 0x5E, 0x3A, 0xD6, 0x46)
    private val MASKED_CFG_TAIL = intArrayOf(0x1A, 0x5A, 0x7A, 0x5A)

    val AAC_SILENT_FRAME: ByteArray
        get() = ByteArray(MASKED_FRAME.size) { i -> (MASKED_FRAME[i] xor MASK_KEY).toByte() }

    const val CHUNK1_SAMPLES = 9
    const val CHUNK2_SAMPLES = 8
    const val ADDED_SAMPLES = CHUNK1_SAMPLES + CHUNK2_SAMPLES
    const val AAC_FRAME_DURATION = 1024L

    const val PROJECT_UDTA_TEXT = "Recompress Bypass https://github.com/j7x69/Recompress-Bypass"

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

    @Suppress("unused")
    class MP4Box(
        val type: String,
        val pos: Int,
        val size: Long,
        val headerLen: Int,
        val data: ByteArray,
        val payload: ByteArray
    )

    @Suppress("unused")
    class FileBoxHeader(
        val type: String,
        val pos: Long,
        val size: Long,
        val headerLen: Int
    )

    fun isHazeOrWatermark(data: ByteArray): Boolean {
        val str = String(data, Charsets.ISO_8859_1).lowercase()
        return WATERMARK_SIGNATURES.any { str.contains(it) }
    }

    fun makeSlSrPceAsc(sfIdx: Int = 3): ByteArray {
        val sf = sfIdx and 0x0F
        val b0 = ((2 shl 3) or (sf shr 1)).toByte()
        val b1 = ((sf and 1) shl 7).toByte()
        val b2 = (0x04 or ((sf shr 2) and 0x03)).toByte()
        val b3 = ((sf and 0x03) shl 6).toByte()
        return byteArrayOf(
            b0,
            b1,
            b2,
            b3,
            (MASKED_CFG_TAIL[0] xor MASK_KEY).toByte(),
            (MASKED_CFG_TAIL[1] xor MASK_KEY).toByte(),
            (MASKED_CFG_TAIL[2] xor MASK_KEY).toByte(),
            (MASKED_CFG_TAIL[3] xor MASK_KEY).toByte()
        )
    }

    fun buildCleanProjectUdta(): ByteArray {
        if (RustCoreBridge.isNativeAvailable) {
            return RustCoreBridge.nativeBuildUdtaV45()
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

        // Xóa sạch file đầu ra cũ (nếu tồn tại) để không bao giờ bị ghi đè lỗi
        if (outputFile.exists()) {
            outputFile.delete()
        }

        if (RustCoreBridge.isNativeAvailable) {
            RustCoreBridge.optimizeMp4WithRust(inputFile, outputFile, onLog)
            return
        }

        optimizeJvmFallbackV45(inputFile, outputFile, onLog)
    }

    private fun optimizeJvmFallbackV45(
        inputFile: File,
        outputFile: File,
        onLog: (String) -> Unit
    ) {
        val fileSize = inputFile.length()
        if (fileSize < 32L) {
            throw IllegalStateException("Video file is too small or corrupted.")
        }

        onLog("[PROCESS]")

        var ftypData: ByteArray? = null
        var moovData: ByteArray? = null
        var moovHdrLen = 8
        var mdatInfo: FileBoxHeader? = null

        RandomAccessFile(inputFile, "r").use { raf ->
            var pos = 0L
            val hdr = ByteArray(8)
            val extHdr = ByteArray(8)

            while (pos + 8 <= fileSize) {
                raf.seek(pos)
                if (raf.read(hdr) < 8) break

                val btype = String(hdr, 4, 4, Charsets.ISO_8859_1)
                var size = ByteBuffer.wrap(hdr, 0, 4)
                    .order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                var hdrLen = 8

                if (size == 1L) {
                    if (pos + 16 > fileSize) break
                    if (raf.read(extHdr) < 8) break
                    size = ByteBuffer.wrap(extHdr).order(ByteOrder.BIG_ENDIAN).long
                    hdrLen = 16
                } else if (size == 0L) {
                    size = fileSize - pos
                }

                if (size < hdrLen || pos + size > fileSize) {
                    throw IllegalStateException("Corrupted MP4 container structure.")
                }

                if (btype == "ftyp" && ftypData == null) {
                    raf.seek(pos)
                    val arr = ByteArray(size.toInt())
                    raf.readFully(arr)
                    ftypData = arr
                } else if (btype == "moov" && moovData == null) {
                    if (size > MAX_MOOV_SIZE) {
                        throw IllegalStateException("Video metadata exceeds memory safety limit.")
                    }
                    raf.seek(pos)
                    val arr = ByteArray(size.toInt())
                    raf.readFully(arr)
                    moovData = arr
                    moovHdrLen = hdrLen
                } else if (btype == "mdat" && mdatInfo == null) {
                    mdatInfo = FileBoxHeader(btype, pos, size, hdrLen)
                }

                pos += size
            }
        }

        val ftyp = ftypData ?: throw IllegalStateException("Invalid MP4 file format.")
        val moov = moovData ?: throw IllegalStateException("Missing video metadata in MP4 file.")
        val mdat = mdatInfo ?: throw IllegalStateException("Missing media stream in MP4 file.")

        val moovBoxes = parseBoxes(moov, moovHdrLen, moov.size)
        val mvhdBox = moovBoxes.find { it.type == "mvhd" }
            ?: throw IllegalStateException("Invalid video header metadata.")

        val mvhdP = mvhdBox.payload.clone()
        val mvhdVer = mvhdP[0].toInt() and 0xFF
        val mvhdBuf = ByteBuffer.wrap(mvhdP).order(ByteOrder.BIG_ENDIAN)

        val origMovieTs: Long
        val origNextTid: Int
        if (mvhdVer == 0) {
            origMovieTs = (mvhdBuf.getInt(12).toLong() and 0xFFFFFFFFL).coerceAtLeast(1L)
            origNextTid = mvhdBuf.getInt(96)
        } else {
            origMovieTs = (mvhdBuf.getInt(20).toLong() and 0xFFFFFFFFL).coerceAtLeast(1L)
            origNextTid = mvhdBuf.getInt(108)
        }

        var audioTrakBox: MP4Box? = null
        for (b in moovBoxes) {
            if (b.type == "trak") {
                val mdia = findInBox(b.data, 8, "mdia")
                val hdlr = if (mdia != null) findInBox(mdia.data, 8, "hdlr") else null
                if (hdlr != null && hdlr.data.size >= 20) {
                    val subtype = String(hdlr.data, 16, 4, Charsets.ISO_8859_1)
                    if (subtype == "soun") {
                        audioTrakBox = b
                        break
                    }
                }
            }
        }

        val audioTrak = audioTrakBox
            ?: throw IllegalStateException("Video must contain an audio track.")

        val aTkhd = findInBox(audioTrak.data, 8, "tkhd")
            ?: throw IllegalStateException("Invalid audio track header.")
        val origAudioTkhdDur: Long = if ((aTkhd.payload[0].toInt() and 0xFF) == 0) {
            ByteBuffer.wrap(aTkhd.payload).order(ByteOrder.BIG_ENDIAN).getInt(20)
                .toLong() and 0xFFFFFFFFL
        } else {
            ByteBuffer.wrap(aTkhd.payload).order(ByteOrder.BIG_ENDIAN).getLong(28)
        }

        val aMdia = findInBox(audioTrak.data, 8, "mdia")
            ?: throw IllegalStateException("Invalid audio media data.")
        val aMdhd = findInBox(aMdia.data, 8, "mdhd")
            ?: throw IllegalStateException("Invalid audio media header.")
        val aMdhdBuf = ByteBuffer.wrap(aMdhd.payload).order(ByteOrder.BIG_ENDIAN)
        val audioTs: Long
        val origMdhdDur: Long
        if ((aMdhd.payload[0].toInt() and 0xFF) == 0) {
            audioTs = (aMdhdBuf.getInt(12).toLong() and 0xFFFFFFFFL).coerceAtLeast(1L)
            origMdhdDur = aMdhdBuf.getInt(16).toLong() and 0xFFFFFFFFL
        } else {
            audioTs = (aMdhdBuf.getInt(20).toLong() and 0xFFFFFFFFL).coerceAtLeast(1L)
            origMdhdDur = aMdhdBuf.getLong(24)
        }

        val aMinf = findInBox(aMdia.data, 8, "minf")
            ?: throw IllegalStateException("Invalid audio stream info.")
        val aStbl = findInBox(aMinf.data, 8, "stbl")
            ?: throw IllegalStateException("Invalid audio sample table.")
        findInBox(aStbl.data, 8, "stsz")
            ?: throw IllegalStateException("Invalid audio sample sizes.")
        val aStco = findInBox(aStbl.data, 8, "stco") ?: findInBox(aStbl.data, 8, "co64")
        ?: throw IllegalStateException("Invalid audio chunk offsets.")

        val origChunks = ByteBuffer.wrap(aStco.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int

        val addedMdhdUnits = ADDED_SAMPLES.toLong() * AAC_FRAME_DURATION // 17 * 1024 = 17408
        val newMdhdDur = origMdhdDur + addedMdhdUnits
        val newAudioDurMs =
            (origAudioTkhdDur * 1000L + (addedMdhdUnits * 1000L * origMovieTs) / audioTs) / origMovieTs

        if (mvhdVer == 0) {
            mvhdBuf.putInt(12, 1000)
            mvhdBuf.putInt(16, newAudioDurMs.toInt())
            mvhdBuf.putInt(96, origNextTid + 1)
        } else {
            mvhdBuf.putInt(20, 1000)
            mvhdBuf.putLong(24, newAudioDurMs)
            mvhdBuf.putInt(108, origNextTid + 1)
        }
        val newMvhd = packBox("mvhd", mvhdP)

        val origMdatPayloadPos = mdat.pos + mdat.headerLen
        val origMdatPayloadLen = mdat.size - mdat.headerLen

        val mdat1Hdr: ByteArray = if (origMdatPayloadLen + 8L > 0xFFFFFFFFL) {
            ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .putInt(1)
                .put("mdat".toByteArray(Charsets.ISO_8859_1))
                .putLong(origMdatPayloadLen + 16L)
                .array()
        } else {
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt((origMdatPayloadLen + 8L).toInt())
                .put("mdat".toByteArray(Charsets.ISO_8859_1))
                .array()
        }

        val newMdat1Pos = ftyp.size.toLong() + FREE_BOX.size
        val newMdat1PayloadPos = newMdat1Pos + mdat1Hdr.size
        val shiftAmount = newMdat1PayloadPos - origMdatPayloadPos

        val mdat2Pos = newMdat1PayloadPos + origMdatPayloadLen
        val mdat2PayloadPos = mdat2Pos + 8L
        val mdat2Payload = ByteArray(ADDED_SAMPLES * AAC_SILENT_FRAME.size)
        for (i in 0 until ADDED_SAMPLES) {
            System.arraycopy(
                AAC_SILENT_FRAME,
                0,
                mdat2Payload,
                i * AAC_SILENT_FRAME.size,
                AAC_SILENT_FRAME.size
            )
        }
        val mdat2Box = packBox("mdat", mdat2Payload)

        val newMoovChildren = mutableListOf<ByteArray>()
        newMoovChildren.add(newMvhd)
        var clonedAudioTrak: ByteArray? = null

        for (b in moovBoxes) {
            when (b.type) {
                "mvhd" -> continue
                "trak" -> {
                    val mdia = findInBox(b.data, 8, "mdia")
                    val hdlr = if (mdia != null) findInBox(mdia.data, 8, "hdlr") else null
                    val subtype = if (hdlr != null && hdlr.data.size >= 20) {
                        String(hdlr.data, 16, 4, Charsets.ISO_8859_1)
                    } else ""

                    if (subtype == "vide") {
                        newMoovChildren.add(
                            processVideoOrOtherTrak(
                                b.data,
                                origMovieTs,
                                shiftAmount,
                                isVideo = true
                            )
                        )
                    } else if (subtype == "soun" && clonedAudioTrak == null) {
                        val primaryAudio = processAudioTrak(
                            trakData = b.data,
                            origMovieTs = origMovieTs,
                            shiftAmount = shiftAmount,
                            mdat2PayloadPos = mdat2PayloadPos,
                            newAudioDurMs = newAudioDurMs,
                            newMdhdDur = newMdhdDur,
                            audioTs = audioTs.toInt(),
                            origChunks = origChunks,
                            isPrimaryPoisoned = true,
                            overrideTrackId = null
                        )
                        newMoovChildren.add(primaryAudio)

                        clonedAudioTrak = processAudioTrak(
                            trakData = b.data,
                            origMovieTs = origMovieTs,
                            shiftAmount = shiftAmount,
                            mdat2PayloadPos = mdat2PayloadPos,
                            newAudioDurMs = newAudioDurMs,
                            newMdhdDur = newMdhdDur,
                            audioTs = audioTs.toInt(),
                            origChunks = origChunks,
                            isPrimaryPoisoned = false,
                            overrideTrackId = origNextTid
                        )
                    } else {
                        newMoovChildren.add(
                            processVideoOrOtherTrak(
                                b.data,
                                origMovieTs,
                                shiftAmount,
                                isVideo = false
                            )
                        )
                    }
                }

                "udta" -> {
                    clonedAudioTrak?.let {
                        newMoovChildren.add(it)
                        clonedAudioTrak = null
                    }
                    newMoovChildren.add(buildCleanProjectUdta())
                }

                else -> newMoovChildren.add(b.data)
            }
        }

        clonedAudioTrak?.let { newMoovChildren.add(it) }
        val finalMoov = packBox("moov", concatByteArrays(newMoovChildren))

        BufferedOutputStream(FileOutputStream(outputFile, false), 1024 * 1024).use { out ->
            out.write(ftyp)
            out.write(FREE_BOX)
            out.write(mdat1Hdr)

            RandomAccessFile(inputFile, "r").use { inRaf ->
                inRaf.seek(origMdatPayloadPos)
                val buffer = ByteArray(CHUNK_SIZE)
                var rem = origMdatPayloadLen
                while (rem > 0) {
                    val toRead = minOf(buffer.size.toLong(), rem).toInt()
                    val read = inRaf.read(buffer, 0, toRead)
                    if (read <= 0) {
                        throw IllegalStateException("Unexpected end of input video file.")
                    }
                    out.write(buffer, 0, read)
                    rem -= read
                }
            }

            out.write(mdat2Box)
            out.write(finalMoov)
            out.flush()
        }
    }

    private fun buildV45AudioStsd(
        origStsdPayload: ByteArray,
        audioTs: Int
    ): Pair<ByteArray, ByteArray> {
        val entries = parseBoxes(origStsdPayload, 8, origStsdPayload.size)
        if (entries.isEmpty() || entries[0].data.size < 36 || origStsdPayload.size < 8) {
            val box = packBox("stsd", origStsdPayload)
            return Pair(box, box)
        }

        val entry1Raw = entries[0].data
        val audioHeader28b = entry1Raw.copyOfRange(8, 36)
        val subBoxes = parseBoxes(entry1Raw, 36, entry1Raw.size)

        var maxBr = 294000
        var avgBr = 2275
        val sfMap = mapOf(
            96000 to 0, 88200 to 1, 64000 to 2, 48000 to 3, 44100 to 4, 32000 to 5,
            24000 to 6, 22050 to 7, 16000 to 8, 12000 to 9, 11025 to 10, 8000 to 11, 7350 to 12
        )
        var sfIdx = sfMap[audioTs] ?: 3

        for (sb in subBoxes) {
            if (sb.type == "btrt" && sb.payload.size >= 12) {
                val buf = ByteBuffer.wrap(sb.payload).order(ByteOrder.BIG_ENDIAN)
                maxBr = buf.getInt(4)
                avgBr = buf.getInt(8)
            } else if (sb.type == "esds") {
                val p = sb.payload
                val pos05 = indexOfSubArray(
                    p,
                    byteArrayOf(0x05, 0x80.toByte(), 0x80.toByte(), 0x80.toByte())
                )
                if (pos05 != -1 && pos05 + 7 <= p.size) {
                    val a0 = p[pos05 + 5].toInt() and 0xFF
                    val a1 = p[pos05 + 6].toInt() and 0xFF
                    sfIdx = ((a0 and 0x07) shl 1) or (a1 shr 7)
                }
            }
        }

        val pceAsc = makeSlSrPceAsc(sfIdx)
        val esds2Payload = ByteBuffer.allocate(50).order(ByteOrder.BIG_ENDIAN)
            .put(byteArrayOf(0x00, 0x00, 0x00, 0x00))
            .put(byteArrayOf(0x03, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x28))
            .put(byteArrayOf(0x00, 0x02, 0x00))
            .put(byteArrayOf(0x04, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x1a))
            .put(byteArrayOf(0x40, 0x15, 0x00, 0x00, 0x00))
            .putInt(maxBr)
            .putInt(avgBr)
            .put(byteArrayOf(0x05, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x08))
            .put(pceAsc)
            .put(byteArrayOf(0x06, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01, 0x02))
            .array()

        val esds2Box = packBox("esds", esds2Payload)
        val entry2Raw = packBox("mp4a", audioHeader28b + esds2Box)

        val header4b = origStsdPayload.copyOfRange(0, 4)
        val count2 = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(2).array()
        val count1 = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array()

        val dualPayload = concatByteArrays(listOf(header4b, count2, entry1Raw, entry2Raw))
        val singlePayload = concatByteArrays(listOf(header4b, count1, entry1Raw))

        return Pair(packBox("stsd", dualPayload), packBox("stsd", singlePayload))
    }

    private fun shiftChunkOffsets(
        sb: MP4Box,
        shiftAmount: Long,
        extraOffsets: LongArray = LongArray(0)
    ): ByteArray {
        val payload = sb.payload
        val cnt = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val offsets = LongArray(cnt + extraOffsets.size)

        if (sb.type == "stco") {
            val inBuf = ByteBuffer.wrap(payload, 8, cnt * 4).order(ByteOrder.BIG_ENDIAN)
            for (i in 0 until cnt) {
                offsets[i] = (inBuf.int.toLong() and 0xFFFFFFFFL) + shiftAmount
            }
        } else {
            val inBuf = ByteBuffer.wrap(payload, 8, cnt * 8).order(ByteOrder.BIG_ENDIAN)
            for (i in 0 until cnt) {
                offsets[i] = inBuf.long + shiftAmount
            }
        }

        for (i in extraOffsets.indices) {
            offsets[cnt + i] = extraOffsets[i]
        }

        return if (sb.type == "co64" || offsets.any { it > 0xFFFFFFFFL }) {
            val outBuf = ByteBuffer.allocate(8 + offsets.size * 8).order(ByteOrder.BIG_ENDIAN)
            outBuf.put(payload, 0, 4)
            outBuf.putInt(offsets.size)
            for (o in offsets) outBuf.putLong(o)
            packBox("co64", outBuf.array())
        } else {
            val outBuf = ByteBuffer.allocate(8 + offsets.size * 4).order(ByteOrder.BIG_ENDIAN)
            outBuf.put(payload, 0, 4)
            outBuf.putInt(offsets.size)
            for (o in offsets) outBuf.putInt(o.toInt())
            packBox("stco", outBuf.array())
        }
    }

    private fun rescaleTkhd(
        tkhdPayload: ByteArray,
        origMovieTs: Long,
        newId: Int? = null,
        overrideDur: Long? = null
    ): ByteArray {
        val p = tkhdPayload.clone()
        val ver = p[0].toInt() and 0xFF
        val buf = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
        if (ver == 0) {
            if (newId != null) buf.putInt(12, newId)
            val oldDur = buf.getInt(20).toLong() and 0xFFFFFFFFL
            val newDur = overrideDur ?: ((oldDur * 1000L) / origMovieTs)
            buf.putInt(20, newDur.toInt())
        } else {
            if (newId != null) buf.putInt(20, newId)
            val oldDur = buf.getLong(28)
            val newDur = overrideDur ?: ((oldDur * 1000L) / origMovieTs)
            buf.putLong(28, newDur)
        }
        return packBox("tkhd", p)
    }

    private fun rescaleEdts(
        edtsData: ByteArray,
        origMovieTs: Long,
        overrideDur: Long? = null
    ): ByteArray {
        val elst = findInBox(edtsData, 8, "elst") ?: return edtsData
        val p = elst.payload.clone()
        val ver = p[0].toInt() and 0xFF
        val buf = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
        val cnt = buf.getInt(4)
        for (i in 0 until cnt) {
            if (ver == 0) {
                val off = 8 + 12 * i
                val sd = buf.getInt(off).toLong() and 0xFFFFFFFFL
                val newSd = overrideDur ?: ((sd * 1000L) / origMovieTs)
                buf.putInt(off, newSd.toInt())
            } else {
                val off = 8 + 20 * i
                val sd = buf.getLong(off)
                val newSd = overrideDur ?: ((sd * 1000L) / origMovieTs)
                buf.putLong(off, newSd)
            }
        }
        return packBox("edts", packBox("elst", p))
    }

    private fun processVideoOrOtherTrak(
        trakData: ByteArray,
        origMovieTs: Long,
        shiftAmount: Long,
        isVideo: Boolean
    ): ByteArray {
        val subBoxes = parseBoxes(trakData, 8, trakData.size)
        val out = mutableListOf<ByteArray>()
        for (b in subBoxes) {
            when (b.type) {
                "tkhd" -> out.add(rescaleTkhd(b.payload, origMovieTs))
                "edts" -> out.add(rescaleEdts(b.data, origMovieTs))
                "mdia" -> {
                    val mdiaOut = mutableListOf<ByteArray>()
                    for (mb in parseBoxes(b.data, 8, b.data.size)) {
                        if (mb.type == "minf") {
                            val minfOut = mutableListOf<ByteArray>()
                            for (mib in parseBoxes(mb.data, 8, mb.data.size)) {
                                if (mib.type == "stbl") {
                                    val stblOut = mutableListOf<ByteArray>()
                                    for (sb in parseBoxes(mib.data, 8, mib.data.size)) {
                                        if (sb.type == "stco" || sb.type == "co64") {
                                            stblOut.add(shiftChunkOffsets(sb, shiftAmount))
                                        } else if (sb.type == "stsd" && isVideo) {
                                            val stsdP = if (sb.payload.size in 86..89) {
                                                sb.payload.copyOf(90)
                                            } else {
                                                sb.payload.clone()
                                            }
                                            if (stsdP.size >= 90) {
                                                for (ci in 58 until 90) stsdP[ci] = 0
                                            }
                                            stblOut.add(packBox("stsd", stsdP))
                                        } else {
                                            stblOut.add(sb.data)
                                        }
                                    }
                                    minfOut.add(packBox("stbl", concatByteArrays(stblOut)))
                                } else {
                                    minfOut.add(mib.data)
                                }
                            }
                            mdiaOut.add(packBox("minf", concatByteArrays(minfOut)))
                        } else {
                            mdiaOut.add(mb.data)
                        }
                    }
                    out.add(packBox("mdia", concatByteArrays(mdiaOut)))
                }

                else -> out.add(b.data)
            }
        }
        return packBox("trak", concatByteArrays(out))
    }

    private fun processAudioTrak(
        trakData: ByteArray,
        origMovieTs: Long,
        shiftAmount: Long,
        mdat2PayloadPos: Long,
        newAudioDurMs: Long,
        newMdhdDur: Long,
        audioTs: Int,
        origChunks: Int,
        isPrimaryPoisoned: Boolean,
        overrideTrackId: Int?
    ): ByteArray {
        val subBoxes = parseBoxes(trakData, 8, trakData.size)
        val out = mutableListOf<ByteArray>()
        val extraChunkOffsets = longArrayOf(
            mdat2PayloadPos,
            mdat2PayloadPos + CHUNK1_SAMPLES.toLong() * AAC_SILENT_FRAME.size
        )

        for (b in subBoxes) {
            when (b.type) {
                "tkhd" -> out.add(
                    rescaleTkhd(
                        b.payload,
                        origMovieTs,
                        newId = overrideTrackId,
                        overrideDur = newAudioDurMs
                    )
                )

                "edts" -> out.add(rescaleEdts(b.data, origMovieTs, overrideDur = newAudioDurMs))
                "mdia" -> {
                    val mdiaOut = mutableListOf<ByteArray>()
                    for (mb in parseBoxes(b.data, 8, b.data.size)) {
                        if (mb.type == "mdhd") {
                            val mdhdP = mb.payload.clone()
                            val buf = ByteBuffer.wrap(mdhdP).order(ByteOrder.BIG_ENDIAN)
                            if ((mdhdP[0].toInt() and 0xFF) == 0) {
                                buf.putInt(16, newMdhdDur.toInt())
                            } else {
                                buf.putLong(24, newMdhdDur)
                            }
                            mdiaOut.add(packBox("mdhd", mdhdP))
                        } else if (mb.type == "minf") {
                            val minfOut = mutableListOf<ByteArray>()
                            for (mib in parseBoxes(mb.data, 8, mb.data.size)) {
                                if (mib.type == "stbl") {
                                    val stblOut = mutableListOf<ByteArray>()
                                    for (sb in parseBoxes(mib.data, 8, mib.data.size)) {
                                        when (sb.type) {
                                            "stsd" -> {
                                                val (dualBox, singleBox) = buildV45AudioStsd(
                                                    sb.payload,
                                                    audioTs
                                                )
                                                stblOut.add(if (isPrimaryPoisoned) dualBox else singleBox)
                                            }

                                            "stts" -> {
                                                val p = sb.payload
                                                val cnt = ByteBuffer.wrap(p, 4, 4)
                                                    .order(ByteOrder.BIG_ENDIAN).int
                                                val entries = mutableListOf<Pair<Int, Int>>()
                                                val inBuf = ByteBuffer.wrap(p, 8, cnt * 8)
                                                    .order(ByteOrder.BIG_ENDIAN)
                                                repeat(cnt) {
                                                    entries.add(Pair(inBuf.int, inBuf.int))
                                                }
                                                if (entries.isNotEmpty()) {
                                                    val last = entries.last()
                                                    entries[entries.lastIndex] = Pair(
                                                        last.first + ADDED_SAMPLES,
                                                        last.second
                                                    )
                                                } else {
                                                    entries.add(
                                                        Pair(
                                                            ADDED_SAMPLES,
                                                            AAC_FRAME_DURATION.toInt()
                                                        )
                                                    )
                                                }
                                                val outBuf =
                                                    ByteBuffer.allocate(8 + entries.size * 8)
                                                        .order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(p, 0, 4)
                                                outBuf.putInt(entries.size)
                                                for ((sc, sd) in entries) {
                                                    outBuf.putInt(sc)
                                                    outBuf.putInt(sd)
                                                }
                                                stblOut.add(packBox("stts", outBuf.array()))
                                            }

                                            "stsc" -> {
                                                val p = sb.payload
                                                val cnt = ByteBuffer.wrap(p, 4, 4)
                                                    .order(ByteOrder.BIG_ENDIAN).int
                                                val outBuf = ByteBuffer.allocate(8 + (cnt + 2) * 12)
                                                    .order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(p, 0, 4)
                                                outBuf.putInt(cnt + 2)
                                                outBuf.put(p, 8, cnt * 12)
                                                outBuf.putInt(origChunks + 1)
                                                outBuf.putInt(CHUNK1_SAMPLES)
                                                outBuf.putInt(1)
                                                outBuf.putInt(origChunks + 2)
                                                outBuf.putInt(CHUNK2_SAMPLES)
                                                outBuf.putInt(if (isPrimaryPoisoned) 2 else 1)
                                                stblOut.add(packBox("stsc", outBuf.array()))
                                            }

                                            "stsz" -> {
                                                val p = sb.payload
                                                val ss = ByteBuffer.wrap(p, 4, 4)
                                                    .order(ByteOrder.BIG_ENDIAN).int
                                                val cnt = ByteBuffer.wrap(p, 8, 4)
                                                    .order(ByteOrder.BIG_ENDIAN).int
                                                if (ss == 0) {
                                                    val outBuf =
                                                        ByteBuffer.allocate(12 + (cnt + ADDED_SAMPLES) * 4)
                                                            .order(ByteOrder.BIG_ENDIAN)
                                                    outBuf.put(p, 0, 8)
                                                    outBuf.putInt(cnt + ADDED_SAMPLES)
                                                    outBuf.put(p, 12, cnt * 4)
                                                    repeat(ADDED_SAMPLES) {
                                                        outBuf.putInt(AAC_SILENT_FRAME.size)
                                                    }
                                                    stblOut.add(packBox("stsz", outBuf.array()))
                                                } else {
                                                    val outBuf = ByteBuffer.allocate(12)
                                                        .order(ByteOrder.BIG_ENDIAN)
                                                    outBuf.put(p, 0, 8)
                                                    outBuf.putInt(cnt + ADDED_SAMPLES)
                                                    stblOut.add(packBox("stsz", outBuf.array()))
                                                }
                                            }

                                            "stco", "co64" -> {
                                                stblOut.add(
                                                    shiftChunkOffsets(
                                                        sb,
                                                        shiftAmount,
                                                        extraChunkOffsets
                                                    )
                                                )
                                            }

                                            "sbgp" -> {
                                                val p = sb.payload
                                                val cnt = ByteBuffer.wrap(p, 8, 4)
                                                    .order(ByteOrder.BIG_ENDIAN).int
                                                val outBuf = ByteBuffer.allocate(12 + (cnt + 1) * 8)
                                                    .order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(p, 0, 8)
                                                outBuf.putInt(cnt + 1)
                                                outBuf.put(p, 12, cnt * 8)
                                                outBuf.putInt(ADDED_SAMPLES)
                                                outBuf.putInt(0)
                                                stblOut.add(packBox("sbgp", outBuf.array()))
                                            }

                                            else -> stblOut.add(sb.data)
                                        }
                                    }
                                    minfOut.add(packBox("stbl", concatByteArrays(stblOut)))
                                } else {
                                    minfOut.add(mib.data)
                                }
                            }
                            mdiaOut.add(packBox("minf", concatByteArrays(minfOut)))
                        } else {
                            mdiaOut.add(mb.data)
                        }
                    }
                    out.add(packBox("mdia", concatByteArrays(mdiaOut)))
                }

                else -> out.add(b.data)
            }
        }
        return packBox("trak", concatByteArrays(out))
    }

    private fun indexOfSubArray(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun parseBoxes(data: ByteArray, offset: Int = 0, end: Int = data.size): List<MP4Box> {
        val safeEnd = end.coerceAtMost(data.size)
        val boxes = mutableListOf<MP4Box>()
        var pos = offset
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        while (pos + 8 <= safeEnd) {
            var size = buf.getInt(pos).toLong() and 0xFFFFFFFFL
            val btype = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            var hdrLen = 8

            if (size == 1L) {
                if (pos + 16 > safeEnd) break
                size = buf.getLong(pos + 8)
                hdrLen = 16
            } else if (size == 0L) {
                size = (safeEnd - pos).toLong()
            }

            if (size < hdrLen || pos + size > safeEnd) break

            val boxData = data.copyOfRange(pos, (pos + size).toInt())
            val payload = data.copyOfRange(pos + hdrLen, (pos + size).toInt())
            boxes.add(MP4Box(btype, pos, size, hdrLen, boxData, payload))
            pos += size.toInt()
        }
        return boxes
    }

    private fun packBox(type: String, payload: ByteArray): ByteArray {
        val totalSize = payload.size.toLong() + 8L
        return if (totalSize > 0xFFFFFFFFL) {
            val buf = ByteBuffer.allocate(16 + payload.size).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(1)
            buf.put(type.toByteArray(Charsets.ISO_8859_1))
            buf.putLong(payload.size.toLong() + 16L)
            buf.put(payload)
            buf.array()
        } else {
            val buf = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(totalSize.toInt())
            buf.put(type.toByteArray(Charsets.ISO_8859_1))
            buf.put(payload)
            buf.array()
        }
    }

    private fun findInBox(boxData: ByteArray, headerLen: Int, targetType: String): MP4Box? {
        return parseBoxes(boxData, headerLen, boxData.size).find { it.type == targetType }
    }

    private fun concatByteArrays(list: List<ByteArray>): ByteArray {
        val total = list.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (arr in list) {
            System.arraycopy(arr, 0, result, offset, arr.size)
            offset += arr.size
        }
        return result
    }
}
