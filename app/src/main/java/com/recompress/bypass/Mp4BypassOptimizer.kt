package com.recompress.bypass

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * MP4 Quality Bypass Optimizer (Video Quality & Social Media Container Engine)
 *
 * Key Optimization Vectors:
 * 1. 64-bit Indeterminate Duration normalization.
 * 2. Secondary Audio Stream sample synchronization.
 * 3. Clean Container Normalization (Dropping toxic tmcd & tref atoms, scaling timescales to 1000 Hz).
 * 4. ByteDance Internal Ingest Tag (te_is_reencode = 1, Lavf63.7.100).
 * 5. FastStart Container Layout (ftyp -> moov -> free -> mdat).
 * 6. Metadata Sanitizer & Container Cleaner.
 */
object Mp4BypassOptimizer {

    private const val CHUNK_SIZE = 16 * 1024 * 1024 // 16 MB streaming buffer
    private const val DEFAULT_GHOST_MULTIPLIER = 9

    private val CLEAN_FREE = byteArrayOf(
        0x00, 0x00, 0x00, 0x08,
        'f'.code.toByte(), 'r'.code.toByte(), 'e'.code.toByte(), 'e'.code.toByte()
    )

    private val DUMMY_AUDIO_PACKET = byteArrayOf(
        0x00, 0x00, 0x00, 0x04,
        0x00, 0x00, 0x00, 0x00
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

    data class MP4Box(
        val type: String,
        val pos: Int,
        val size: Long,
        val headerLen: Int,
        val data: ByteArray,
        val payload: ByteArray
    )

    data class FileBoxHeader(
        val type: String,
        val pos: Long,
        val size: Long,
        val headerLen: Int
    )

    fun isHazeOrWatermark(data: ByteArray): Boolean {
        val str = String(data, Charsets.ISO_8859_1).lowercase()
        return WATERMARK_SIGNATURES.any { str.contains(it) }
    }

    /**
     * Rebuilds iTunes/QuickTime udta.meta.ilst with ByteDance internal ingest tag
     * (te_is_reencode = 1) and encoder tag (Lavf63.7.100).
     */
    fun buildCleanBytedanceUdta(): ByteArray {
        val hdlrPayload = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN)
            .putInt(0) // version & flags
            .put(byteArrayOf(0, 0, 0, 0))
            .put("mdir".toByteArray(Charsets.ISO_8859_1))
            .put(ByteArray(13))
            .array()
        val hdlrBox = rebuildBox("hdlr", hdlrPayload)

        val tooDataPayload = ByteBuffer.allocate(8 + "Lavf63.7.100".length).order(ByteOrder.BIG_ENDIAN)
            .putInt(1) // type indicator = 1 (text)
            .putInt(0) // locale = 0
            .put("Lavf63.7.100".toByteArray(Charsets.ISO_8859_1))
            .array()
        val tooBox = rebuildBox("\u00a9too", rebuildBox("data", tooDataPayload))

        // ByteDance internal ingest passthrough tag: te_is_reencode = 1
        val meanPayload = ByteBuffer.allocate(4 + "com.apple.quicktime.video".length).order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .put("com.apple.quicktime.video".toByteArray(Charsets.ISO_8859_1))
            .array()
        val meanBox = rebuildBox("mean", meanPayload)

        val namePayload = ByteBuffer.allocate(4 + "te_is_reencode".length).order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .put("te_is_reencode".toByteArray(Charsets.ISO_8859_1))
            .array()
        val nameBox = rebuildBox("name", namePayload)

        val reencodeDataPayload = ByteBuffer.allocate(8 + 1).order(ByteOrder.BIG_ENDIAN)
            .putInt(1)
            .putInt(0)
            .put("1".toByteArray(Charsets.ISO_8859_1))
            .array()
        val dataBox = rebuildBox("data", reencodeDataPayload)

        val reencodeBox = rebuildBox("----", concatByteArrays(listOf(meanBox, nameBox, dataBox)))
        val ilstBox = rebuildBox("ilst", concatByteArrays(listOf(tooBox, reencodeBox)))

        val metaPayload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .array() + concatByteArrays(listOf(hdlrBox, ilstBox))
        val metaBox = rebuildBox("meta", metaPayload)

        return rebuildBox("udta", metaBox)
    }

    fun optimize(
        inputFile: File,
        outputFile: File,
        ghostMultiplier: Int = DEFAULT_GHOST_MULTIPLIER,
        onLog: (String) -> Unit = {}
    ) {
        val startTime = System.currentTimeMillis()
        if (!inputFile.exists()) {
            throw IllegalArgumentException("Không tìm thấy file đầu vào: ${inputFile.absolutePath}")
        }
        val fileSize = inputFile.length()
        onLog("[*] Bắt đầu tối ưu V4: ${inputFile.name} (${String.format("%.2f", fileSize / (1024.0 * 1024.0))} MB)")

        var ftypData: ByteArray? = null
        var moovData: ByteArray? = null
        var moovBoxHdrLen = 8
        var mdatBoxInfo: FileBoxHeader? = null

        RandomAccessFile(inputFile, "r").use { raf ->
            var pos = 0L
            val hdr = ByteArray(8)
            val extHdr = ByteArray(8)

            while (pos < fileSize) {
                raf.seek(pos)
                val readHdr = raf.read(hdr)
                if (readHdr < 8) break

                val btype = String(hdr, 4, 4, Charsets.ISO_8859_1)
                val bufHdr = ByteBuffer.wrap(hdr).order(ByteOrder.BIG_ENDIAN)
                var size = bufHdr.int.toLong() and 0xFFFFFFFFL
                var hdrLen = 8

                if (size == 1L) {
                    val readExt = raf.read(extHdr)
                    if (readExt < 8) break
                    val bufExt = ByteBuffer.wrap(extHdr).order(ByteOrder.BIG_ENDIAN)
                    size = bufExt.long
                    hdrLen = 16
                } else if (size == 0L) {
                    size = fileSize - pos
                }

                if (size < hdrLen) {
                    throw IllegalStateException("Atom '$btype' bị hỏng tại offset $pos.")
                }

                if (btype == "ftyp" && ftypData == null) {
                    raf.seek(pos)
                    val arr = ByteArray(size.toInt())
                    raf.readFully(arr)
                    ftypData = arr
                } else if (btype == "moov" && moovData == null) {
                    raf.seek(pos)
                    val arr = ByteArray(size.toInt())
                    raf.readFully(arr)
                    moovData = arr
                    moovBoxHdrLen = hdrLen
                } else if (btype == "mdat" && mdatBoxInfo == null) {
                    mdatBoxInfo = FileBoxHeader(btype, pos, size, hdrLen)
                }

                pos += size
            }
        }

        if (ftypData == null || moovData == null || mdatBoxInfo == null) {
            throw IllegalStateException("File MP4 không hợp lệ: Thiếu atom moov, mdat hoặc ftyp.")
        }

        val moovBoxes = parseBoxes(moovData, moovBoxHdrLen, moovData.size)
        val mvhdBox = moovBoxes.find { it.type == "mvhd" }
            ?: throw IllegalStateException("Không tìm thấy mvhd atom trong moov.")

        val mvhdVer = mvhdBox.payload[0].toInt() and 0xFF
        val mvhdBuf = ByteBuffer.wrap(mvhdBox.payload).order(ByteOrder.BIG_ENDIAN)
        val origMovieTs: Long = if (mvhdVer == 0) {
            mvhdBuf.position(12)
            mvhdBuf.int.toLong() and 0xFFFFFFFFL
        } else {
            mvhdBuf.position(20)
            mvhdBuf.int.toLong() and 0xFFFFFFFFL
        }
        val safeOrigMovieTs = if (origMovieTs <= 0L) 1000L else origMovieTs

        // Phân loại track Video và Audio (loại bỏ tmcd và tref gây cấm đăng trên TikTok)
        val traks = moovBoxes.filter { it.type == "trak" }
        var videoTrak: MP4Box? = null
        var audioTrak: MP4Box? = null
        var videoCodec = "hvc1"

        for (t in traks) {
            val mdia = findInBox(t.data, 8, "mdia") ?: continue
            val hdlr = findInBox(mdia.data, 8, "hdlr")
            if (hdlr != null && hdlr.data.size >= 20) {
                val subtype = String(hdlr.data, 16, 4, Charsets.ISO_8859_1)
                if (subtype == "vide" && videoTrak == null) {
                    videoTrak = t
                    val minf = findInBox(mdia.data, 8, "minf")
                    val stbl = if (minf != null) findInBox(minf.data, 8, "stbl") else null
                    val stsd = if (stbl != null) findInBox(stbl.data, 8, "stsd") else null
                    if (stsd != null && stsd.payload.size >= 16) {
                        videoCodec = String(stsd.payload, 12, 4, Charsets.ISO_8859_1)
                    }
                } else if (subtype == "soun" && audioTrak == null) {
                    audioTrak = t
                }
            }
        }

        if (videoTrak == null) {
            throw IllegalStateException("Không tìm thấy Video Track hợp lệ trong file MP4.")
        }

        val hasAudio = audioTrak != null
        var origAudioSamples = 0
        var audioTs = 48000L
        var audioDur = 0L
        var dummyCount = 0

        if (hasAudio) {
            val aMdia = findInBox(audioTrak!!.data, 8, "mdia")
            val aMdhd = if (aMdia != null) findInBox(aMdia.data, 8, "mdhd") else null
            if (aMdhd != null) {
                val aVer = aMdhd.payload[0].toInt() and 0xFF
                val aBuf = ByteBuffer.wrap(aMdhd.payload).order(ByteOrder.BIG_ENDIAN)
                if (aVer == 0) {
                    aBuf.position(12)
                    audioTs = aBuf.int.toLong() and 0xFFFFFFFFL
                    audioDur = aBuf.int.toLong() and 0xFFFFFFFFL
                } else {
                    aBuf.position(20)
                    audioTs = aBuf.int.toLong() and 0xFFFFFFFFL
                    audioDur = aBuf.long
                }
            }
            if (audioTs <= 0L) audioTs = 48000L

            val aMinf = if (aMdia != null) findInBox(aMdia.data, 8, "minf") else null
            val aStbl = if (aMinf != null) findInBox(aMinf.data, 8, "stbl") else null
            val aStsz = if (aStbl != null) findInBox(aStbl.data, 8, "stsz") else null
            if (aStsz != null && aStsz.payload.size >= 12) {
                origAudioSamples = ByteBuffer.wrap(aStsz.payload, 8, 4).order(ByteOrder.BIG_ENDIAN).int
            }

            dummyCount = origAudioSamples * ghostMultiplier
            if (dummyCount <= 0) dummyCount = 5958
        }

        // 1. EXPLOIT 1: Tràn thời lượng 64-bit Indeterminate Duration (0xFFFFFFFFFFFFFFFF)
        val timescale = 1000
        val nextTrackId = if (hasAudio) 4 else 2
        val mvhdV1Buf = ByteBuffer.allocate(112).order(ByteOrder.BIG_ENDIAN)
        mvhdV1Buf.put(1.toByte()) // version 1
        mvhdV1Buf.put(byteArrayOf(0, 0, 0)) // flags
        mvhdV1Buf.putLong(0L) // creation_time
        mvhdV1Buf.putLong(0L) // mod_time
        mvhdV1Buf.putInt(timescale) // timescale = 1000
        mvhdV1Buf.putLong(-1L) // 0xFFFFFFFFFFFFFFFFL Indeterminate movie duration
        mvhdV1Buf.put(byteArrayOf(0x00, 0x01, 0x00, 0x00)) // rate 1.0
        mvhdV1Buf.put(byteArrayOf(0x01, 0x00)) // volume 1.0
        mvhdV1Buf.put(ByteArray(10)) // reserved
        // unity matrix 36 bytes
        mvhdV1Buf.putInt(0x00010000)
        mvhdV1Buf.putInt(0)
        mvhdV1Buf.putInt(0)
        mvhdV1Buf.putInt(0)
        mvhdV1Buf.putInt(0x00010000)
        mvhdV1Buf.putInt(0)
        mvhdV1Buf.putInt(0)
        mvhdV1Buf.putInt(0)
        mvhdV1Buf.putInt(0x40000000)
        mvhdV1Buf.put(ByteArray(24)) // pre_defined
        mvhdV1Buf.putInt(nextTrackId)
        val newMvhd = rebuildBox("mvhd", mvhdV1Buf.array())

        // 2. Prepare dummy audio payload for mdat
        val dummyPayloadLen = if (hasAudio) dummyCount.toLong() * DUMMY_AUDIO_PACKET.size else 0L
        val origMdatPayloadLen = mdatBoxInfo.size - mdatBoxInfo.headerLen
        val newMdatPayloadLen = origMdatPayloadLen + dummyPayloadLen

        val newMdatHeader: ByteArray = if (newMdatPayloadLen + 8 > 0xFFFFFFFFL) {
            ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .putInt(1)
                .put("mdat".toByteArray(Charsets.ISO_8859_1))
                .putLong(newMdatPayloadLen + 16)
                .array()
        } else {
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt((newMdatPayloadLen + 8).toInt())
                .put("mdat".toByteArray(Charsets.ISO_8859_1))
                .array()
        }

        onLog("[*] Video Codec: ${videoCodec.uppercase()}")
        onLog("[!] BẬT EXPLOIT 1: Tràn thời lượng 64-bit (0xFFFFFFFFFFFFFFFF).")
        if (hasAudio) {
            onLog("[!] BẬT EXPLOIT 2: Ghost Audio Stream (Track #3 + $dummyCount dummy packets, 10x samples).")
        }
        onLog("[!] BẬT EXPLOIT 3: Thẻ nội bộ ByteDance (te_is_reencode = 1, Lavf63.7.100).")
        onLog("[!] BẬT CHỐNG LỖI: Loại bỏ hoàn toàn track tmcd rác và tref gây cấm đăng trên TikTok.")
        onLog("[!] LÀM SẠCH CONTAINER: Tẩy sạch siêu dữ liệu rác và thông tin thừa.")

        fun buildVideoTrak(shiftAmount: Long): ByteArray {
            val subBoxes = parseBoxes(videoTrak.data, 8, videoTrak.data.size)
            val newSub = mutableListOf<ByteArray>()

            for (b in subBoxes) {
                val btype = b.type
                if (btype == "udta" || btype == "meta") {
                    if (isHazeOrWatermark(b.data)) continue
                    newSub.add(b.data)
                } else if (btype == "tref") {
                    // Loại bỏ tref hoàn toàn để ngắt liên kết tới timecode track rác
                    continue
                } else if (btype == "tkhd") {
                    val p = b.payload.clone()
                    val ver = p[0].toInt() and 0xFF
                    val buf = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
                    if (ver == 0) {
                        buf.putInt(12, 1) // Track ID = 1
                        val oldDur = buf.getInt(20).toLong() and 0xFFFFFFFFL
                        val newDur = (oldDur.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong().toInt()
                        buf.putInt(20, newDur)
                    } else {
                        buf.putInt(20, 1) // Track ID = 1
                        val oldDur = buf.getLong(28)
                        val newDur = (oldDur.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong()
                        buf.putLong(28, newDur)
                    }
                    newSub.add(rebuildBox("tkhd", p))
                } else if (btype == "edts") {
                    val elst = findInBox(b.data, 8, "elst")
                    if (elst != null) {
                        val elstP = elst.payload.clone()
                        val elVer = elstP[0].toInt() and 0xFF
                        val elBuf = ByteBuffer.wrap(elstP).order(ByteOrder.BIG_ENDIAN)
                        val cnt = elBuf.getInt(4)
                        for (i in 0 until cnt) {
                            if (elVer == 0) {
                                val offset = 8 + 12 * i
                                val sd = elBuf.getInt(offset).toLong() and 0xFFFFFFFFL
                                val newSd = (sd.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong().toInt()
                                elBuf.putInt(offset, newSd)
                            } else {
                                val offset = 8 + 20 * i
                                val sd = elBuf.getLong(offset)
                                val newSd = (sd.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong()
                                elBuf.putLong(offset, newSd)
                            }
                        }
                        val newEdts = rebuildBox("edts", rebuildBox("elst", elstP))
                        newSub.add(newEdts)
                    } else {
                        newSub.add(b.data)
                    }
                } else if (btype == "mdia") {
                    val mdiaSub = parseBoxes(b.data, 8, b.data.size)
                    val newMdia = mutableListOf<ByteArray>()
                    for (mb in mdiaSub) {
                        if (mb.type == "minf") {
                            val minfSub = parseBoxes(mb.data, 8, mb.data.size)
                            val newMinf = mutableListOf<ByteArray>()
                            for (mib in minfSub) {
                                if (mib.type == "stbl") {
                                    val stblSub = parseBoxes(mib.data, 8, mib.data.size)
                                    val newStbl = mutableListOf<ByteArray>()
                                    for (sb in stblSub) {
                                        if (sb.type == "stco") {
                                            val cnt = ByteBuffer.wrap(sb.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val offsets = LongArray(cnt)
                                            val inBuf = ByteBuffer.wrap(sb.payload, 8, cnt * 4).order(ByteOrder.BIG_ENDIAN)
                                            for (i in 0 until cnt) {
                                                offsets[i] = (inBuf.int.toLong() and 0xFFFFFFFFL) + shiftAmount
                                            }
                                            if (offsets.any { it > 0xFFFFFFFFL }) {
                                                val outBuf = ByteBuffer.allocate(8 + cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(sb.payload, 0, 4)
                                                outBuf.putInt(cnt)
                                                for (o in offsets) outBuf.putLong(o)
                                                newStbl.add(rebuildBox("co64", outBuf.array()))
                                            } else {
                                                val outBuf = ByteBuffer.allocate(8 + cnt * 4).order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(sb.payload, 0, 4)
                                                outBuf.putInt(cnt)
                                                for (o in offsets) outBuf.putInt(o.toInt())
                                                newStbl.add(rebuildBox("stco", outBuf.array()))
                                            }
                                        } else if (sb.type == "co64") {
                                            val cnt = ByteBuffer.wrap(sb.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val inBuf = ByteBuffer.wrap(sb.payload, 8, cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                            val outBuf = ByteBuffer.allocate(8 + cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                            outBuf.put(sb.payload, 0, 4)
                                            outBuf.putInt(cnt)
                                            for (i in 0 until cnt) {
                                                outBuf.putLong(inBuf.long + shiftAmount)
                                            }
                                            newStbl.add(rebuildBox("co64", outBuf.array()))
                                        } else if (sb.type == "stsd") {
                                            // Xóa 32-byte compressor name trong visual sample entry
                                            val stsdP = sb.payload.clone()
                                            val entryStart = 8
                                            if (stsdP.size >= entryStart + 78) {
                                                val compOffset = entryStart + 50
                                                for (ci in compOffset until compOffset + 32) {
                                                    stsdP[ci] = 0
                                                }
                                            }
                                            newStbl.add(rebuildBox("stsd", stsdP))
                                        } else {
                                            newStbl.add(sb.data)
                                        }
                                    }
                                    newMinf.add(rebuildBox("stbl", concatByteArrays(newStbl)))
                                } else {
                                    newMinf.add(mib.data)
                                }
                            }
                            newMdia.add(rebuildBox("minf", concatByteArrays(newMinf)))
                        } else {
                            newMdia.add(mb.data)
                        }
                    }
                    newSub.add(rebuildBox("mdia", concatByteArrays(newMdia)))
                } else {
                    newSub.add(b.data)
                }
            }
            return rebuildBox("trak", concatByteArrays(newSub))
        }

        fun buildAudioTrak(shiftAmount: Long): ByteArray {
            if (audioTrak == null) return ByteArray(0)
            val subBoxes = parseBoxes(audioTrak.data, 8, audioTrak.data.size)
            val newSub = mutableListOf<ByteArray>()

            for (b in subBoxes) {
                val btype = b.type
                if (btype == "udta" || btype == "meta") {
                    if (isHazeOrWatermark(b.data)) continue
                    newSub.add(b.data)
                } else if (btype == "tkhd") {
                    val p = b.payload.clone()
                    val ver = p[0].toInt() and 0xFF
                    val buf = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
                    if (ver == 0) {
                        buf.putInt(12, 2) // Track ID = 2
                        val oldDur = buf.getInt(20).toLong() and 0xFFFFFFFFL
                        val newDur = (oldDur.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong().toInt()
                        buf.putInt(20, newDur)
                    } else {
                        buf.putInt(20, 2) // Track ID = 2
                        val oldDur = buf.getLong(28)
                        val newDur = (oldDur.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong()
                        buf.putLong(28, newDur)
                    }
                    newSub.add(rebuildBox("tkhd", p))
                } else if (btype == "edts") {
                    val elst = findInBox(b.data, 8, "elst")
                    if (elst != null) {
                        val elstP = elst.payload.clone()
                        val elVer = elstP[0].toInt() and 0xFF
                        val elBuf = ByteBuffer.wrap(elstP).order(ByteOrder.BIG_ENDIAN)
                        val cnt = elBuf.getInt(4)
                        for (i in 0 until cnt) {
                            if (elVer == 0) {
                                val offset = 8 + 12 * i
                                val sd = elBuf.getInt(offset).toLong() and 0xFFFFFFFFL
                                val newSd = (sd.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong().toInt()
                                elBuf.putInt(offset, newSd)
                                elBuf.putInt(offset + 4, 0) // media_time = 0
                            } else {
                                val offset = 8 + 20 * i
                                val sd = elBuf.getLong(offset)
                                val newSd = (sd.toDouble() * 1000.0 / safeOrigMovieTs).roundToLong()
                                elBuf.putLong(offset, newSd)
                                elBuf.putLong(offset + 8, 0L) // media_time = 0
                            }
                        }
                        val newEdts = rebuildBox("edts", rebuildBox("elst", elstP))
                        newSub.add(newEdts)
                    } else {
                        newSub.add(b.data)
                    }
                } else if (btype == "mdia") {
                    val mdiaSub = parseBoxes(b.data, 8, b.data.size)
                    val newMdia = mutableListOf<ByteArray>()
                    for (mb in mdiaSub) {
                        if (mb.type == "minf") {
                            val minfSub = parseBoxes(mb.data, 8, mb.data.size)
                            val newMinf = mutableListOf<ByteArray>()
                            for (mib in minfSub) {
                                if (mib.type == "stbl") {
                                    val stblSub = parseBoxes(mib.data, 8, mib.data.size)
                                    val newStbl = mutableListOf<ByteArray>()
                                    for (sb in stblSub) {
                                        if (sb.type == "stco") {
                                            val cnt = ByteBuffer.wrap(sb.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val offsets = LongArray(cnt)
                                            val inBuf = ByteBuffer.wrap(sb.payload, 8, cnt * 4).order(ByteOrder.BIG_ENDIAN)
                                            for (i in 0 until cnt) {
                                                offsets[i] = (inBuf.int.toLong() and 0xFFFFFFFFL) + shiftAmount
                                            }
                                            if (offsets.any { it > 0xFFFFFFFFL }) {
                                                val outBuf = ByteBuffer.allocate(8 + cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(sb.payload, 0, 4)
                                                outBuf.putInt(cnt)
                                                for (o in offsets) outBuf.putLong(o)
                                                newStbl.add(rebuildBox("co64", outBuf.array()))
                                            } else {
                                                val outBuf = ByteBuffer.allocate(8 + cnt * 4).order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(sb.payload, 0, 4)
                                                outBuf.putInt(cnt)
                                                for (o in offsets) outBuf.putInt(o.toInt())
                                                newStbl.add(rebuildBox("stco", outBuf.array()))
                                            }
                                        } else if (sb.type == "co64") {
                                            val cnt = ByteBuffer.wrap(sb.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val inBuf = ByteBuffer.wrap(sb.payload, 8, cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                            val outBuf = ByteBuffer.allocate(8 + cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                            outBuf.put(sb.payload, 0, 4)
                                            outBuf.putInt(cnt)
                                            for (i in 0 until cnt) {
                                                outBuf.putLong(inBuf.long + shiftAmount)
                                            }
                                            newStbl.add(rebuildBox("co64", outBuf.array()))
                                        } else {
                                            newStbl.add(sb.data)
                                        }
                                    }
                                    newMinf.add(rebuildBox("stbl", concatByteArrays(newStbl)))
                                } else {
                                    newMinf.add(mib.data)
                                }
                            }
                            newMdia.add(rebuildBox("minf", concatByteArrays(newMinf)))
                        } else {
                            newMdia.add(mb.data)
                        }
                    }
                    newSub.add(rebuildBox("mdia", concatByteArrays(newMdia)))
                } else {
                    newSub.add(b.data)
                }
            }
            return rebuildBox("trak", concatByteArrays(newSub))
        }

        fun buildGhostTrak(shiftAmount: Long): ByteArray {
            if (audioTrak == null || dummyCount == 0) return ByteArray(0)
            val subBoxes = parseBoxes(audioTrak.data, 8, audioTrak.data.size)
            val newSub = mutableListOf<ByteArray>()
            val newMdhdDur = audioDur + dummyCount
            val ghostDurMs = (newMdhdDur.toDouble() / audioTs * 1000.0).roundToLong()

            for (b in subBoxes) {
                val btype = b.type
                if (btype == "udta" || btype == "meta") {
                    continue
                } else if (btype == "tkhd") {
                    val p = b.payload.clone()
                    val ver = p[0].toInt() and 0xFF
                    val buf = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
                    if (ver == 0) {
                        buf.putInt(12, 3) // Ghost Track ID = 3
                        buf.putInt(20, ghostDurMs.toInt())
                    } else {
                        buf.putInt(20, 3)
                        buf.putLong(28, ghostDurMs)
                    }
                    newSub.add(rebuildBox("tkhd", p))
                } else if (btype == "edts") {
                    val elstPayload = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
                        .put(0.toByte())
                        .put(byteArrayOf(0, 0, 0))
                        .putInt(1) // entry_count = 1
                        .putInt(ghostDurMs.toInt()) // segment_duration
                        .putInt(0) // media_time = 0
                        .putInt(65536) // media_rate = 1.0 (0x00010000)
                        .array()
                    newSub.add(rebuildBox("edts", rebuildBox("elst", elstPayload)))
                } else if (btype == "mdia") {
                    val mdiaSub = parseBoxes(b.data, 8, b.data.size)
                    val newMdia = mutableListOf<ByteArray>()
                    for (mb in mdiaSub) {
                        if (mb.type == "mdhd") {
                            val p = mb.payload.clone()
                            val ver = p[0].toInt() and 0xFF
                            val buf = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
                            if (ver == 0) {
                                buf.putInt(16, newMdhdDur.toInt())
                            } else {
                                buf.putLong(24, newMdhdDur)
                            }
                            newMdia.add(rebuildBox("mdhd", p))
                        } else if (mb.type == "minf") {
                            val minfSub = parseBoxes(mb.data, 8, mb.data.size)
                            val newMinf = mutableListOf<ByteArray>()
                            for (mib in minfSub) {
                                if (mib.type == "stbl") {
                                    val stblSub = parseBoxes(mib.data, 8, mib.data.size)
                                    val newStbl = mutableListOf<ByteArray>()
                                    for (sb in stblSub) {
                                        if (sb.type == "stts") {
                                            val p = sb.payload
                                            val cnt = ByteBuffer.wrap(p, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val outBuf = ByteBuffer.allocate(8 + (cnt + 1) * 8).order(ByteOrder.BIG_ENDIAN)
                                            outBuf.put(p, 0, 4)
                                            outBuf.putInt(cnt + 1)
                                            outBuf.put(p, 8, cnt * 8)
                                            outBuf.putInt(dummyCount)
                                            outBuf.putInt(1)
                                            newStbl.add(rebuildBox("stts", outBuf.array()))
                                        } else if (sb.type == "stsc") {
                                            val p = sb.payload
                                            val cnt = ByteBuffer.wrap(p, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val inBuf = ByteBuffer.wrap(p, 8, cnt * 12).order(ByteOrder.BIG_ENDIAN)
                                            val outBuf = ByteBuffer.allocate(8 + cnt * 12).order(ByteOrder.BIG_ENDIAN)
                                            outBuf.put(p, 0, 4)
                                            outBuf.putInt(cnt)
                                            for (i in 0 until cnt) {
                                                val fc = inBuf.int
                                                var spc = inBuf.int
                                                val di = inBuf.int
                                                if (i == cnt - 1) {
                                                    spc += dummyCount
                                                }
                                                outBuf.putInt(fc)
                                                outBuf.putInt(spc)
                                                outBuf.putInt(di)
                                            }
                                            newStbl.add(rebuildBox("stsc", outBuf.array()))
                                        } else if (sb.type == "stsz") {
                                            val p = sb.payload
                                            val sampleSize = ByteBuffer.wrap(p, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val cnt = ByteBuffer.wrap(p, 8, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val totalCount = cnt + dummyCount
                                            val outBuf = ByteBuffer.allocate(12 + totalCount * 4).order(ByteOrder.BIG_ENDIAN)
                                            outBuf.put(p, 0, 4)
                                            outBuf.putInt(0) // variable sample size
                                            outBuf.putInt(totalCount)
                                            if (sampleSize == 0) {
                                                outBuf.put(p, 12, cnt * 4)
                                            } else {
                                                for (i in 0 until cnt) outBuf.putInt(sampleSize)
                                            }
                                            for (i in 0 until dummyCount) {
                                                outBuf.putInt(8) // 8-byte dummy packet
                                            }
                                            newStbl.add(rebuildBox("stsz", outBuf.array()))
                                        } else if (sb.type == "sbgp") {
                                            val p = sb.payload.clone()
                                            if (p.size >= 20) {
                                                ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN).putInt(12, origAudioSamples + dummyCount)
                                            }
                                            newStbl.add(rebuildBox("sbgp", p))
                                        } else if (sb.type == "stco") {
                                            val cnt = ByteBuffer.wrap(sb.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val offsets = LongArray(cnt)
                                            val inBuf = ByteBuffer.wrap(sb.payload, 8, cnt * 4).order(ByteOrder.BIG_ENDIAN)
                                            for (i in 0 until cnt) {
                                                offsets[i] = (inBuf.int.toLong() and 0xFFFFFFFFL) + shiftAmount
                                            }
                                            if (offsets.any { it > 0xFFFFFFFFL }) {
                                                val outBuf = ByteBuffer.allocate(8 + cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(sb.payload, 0, 4)
                                                outBuf.putInt(cnt)
                                                for (o in offsets) outBuf.putLong(o)
                                                newStbl.add(rebuildBox("co64", outBuf.array()))
                                            } else {
                                                val outBuf = ByteBuffer.allocate(8 + cnt * 4).order(ByteOrder.BIG_ENDIAN)
                                                outBuf.put(sb.payload, 0, 4)
                                                outBuf.putInt(cnt)
                                                for (o in offsets) outBuf.putInt(o.toInt())
                                                newStbl.add(rebuildBox("stco", outBuf.array()))
                                            }
                                        } else if (sb.type == "co64") {
                                            val cnt = ByteBuffer.wrap(sb.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                                            val inBuf = ByteBuffer.wrap(sb.payload, 8, cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                            val outBuf = ByteBuffer.allocate(8 + cnt * 8).order(ByteOrder.BIG_ENDIAN)
                                            outBuf.put(sb.payload, 0, 4)
                                            outBuf.putInt(cnt)
                                            for (i in 0 until cnt) {
                                                outBuf.putLong(inBuf.long + shiftAmount)
                                            }
                                            newStbl.add(rebuildBox("co64", outBuf.array()))
                                        } else {
                                            newStbl.add(sb.data)
                                        }
                                    }
                                    newMinf.add(rebuildBox("stbl", concatByteArrays(newStbl)))
                                } else {
                                    newMinf.add(mib.data)
                                }
                            }
                            newMdia.add(rebuildBox("minf", concatByteArrays(newMinf)))
                        } else {
                            newMdia.add(mb.data)
                        }
                    }
                    newSub.add(rebuildBox("mdia", concatByteArrays(newMdia)))
                } else {
                    newSub.add(b.data)
                }
            }
            return rebuildBox("trak", concatByteArrays(newSub))
        }

        val newUdta = buildCleanBytedanceUdta()

        fun buildMoov(shiftAmount: Long): ByteArray {
            val vt = buildVideoTrak(shiftAmount)
            val at = buildAudioTrak(shiftAmount)
            val gt = buildGhostTrak(shiftAmount)
            return rebuildBox("moov", concatByteArrays(listOf(newMvhd, vt, at, gt, newUdta)))
        }

        // Layout: ftyp + moov (finalMoovSize) + free (8) + mdat
        onLog("[FASTSTART] Pass 1: Tính toán kích thước moov container...")
        var dummyMoov = buildMoov(0L)
        var finalMoovSize = dummyMoov.size

        var newMdatOffset = ftypData.size.toLong() + finalMoovSize + CLEAN_FREE.size
        val origPayloadStart = mdatBoxInfo.pos + mdatBoxInfo.headerLen
        var newPayloadStart = newMdatOffset + newMdatHeader.size
        var shiftAmount = newPayloadStart - origPayloadStart

        onLog("[FASTSTART] Pass 2: Tái định vị chunk offset (shiftAmount = $shiftAmount bytes)...")
        var finalMoov = buildMoov(shiftAmount)
        if (finalMoov.size != finalMoovSize) {
            // Nếu có box nào nhảy size (ví dụ stco -> co64), tính lại một lần nữa
            finalMoovSize = finalMoov.size
            newMdatOffset = ftypData.size.toLong() + finalMoovSize + CLEAN_FREE.size
            newPayloadStart = newMdatOffset + newMdatHeader.size
            shiftAmount = newPayloadStart - origPayloadStart
            finalMoov = buildMoov(shiftAmount)
        }

        onLog("[WRITE] Ghi container [ftyp] (${ftypData.size}B) -> [moov] (${finalMoov.size}B) -> [free] (${CLEAN_FREE.size}B) -> [mdat]...")
        RandomAccessFile(outputFile, "rw").use { out ->
            out.setLength(0)
            out.write(ftypData)
            out.write(finalMoov)
            out.write(CLEAN_FREE)
            out.write(newMdatHeader)

            onLog("[STREAM] Sao chép luồng mdat gốc (${String.format("%.2f", origMdatPayloadLen / (1024.0 * 1024.0))} MB) - 100% Lossless...")
            RandomAccessFile(inputFile, "r").use { inRaf ->
                inRaf.seek(origPayloadStart)
                val buffer = ByteArray(CHUNK_SIZE)
                var remaining = origMdatPayloadLen
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = inRaf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }

            if (hasAudio && dummyCount > 0) {
                onLog("[STREAM] Bơm $dummyCount gói tin Ghost Audio rác vào cuối mdat...")
                val batchCount = 4096
                val batchBytes = ByteBuffer.allocate(batchCount * DUMMY_AUDIO_PACKET.size)
                for (i in 0 until batchCount) {
                    batchBytes.put(DUMMY_AUDIO_PACKET)
                }
                val batchArray = batchBytes.array()

                var remainingPackets = dummyCount
                while (remainingPackets > 0) {
                    val currentBatch = minOf(remainingPackets, batchCount)
                    if (currentBatch == batchCount) {
                        out.write(batchArray)
                    } else {
                        out.write(batchArray, 0, currentBatch * DUMMY_AUDIO_PACKET.size)
                    }
                    remainingPackets -= currentBatch
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        val outSize = outputFile.length()
        onLog("[SUCCESS] Hoàn tất tối ưu hóa V4 trong ${elapsedMs}ms! File: ${outputFile.name} (${String.format("%.2f", outSize / (1024.0 * 1024.0))} MB)")
    }

    private fun parseBoxes(data: ByteArray, offset: Int = 0, end: Int = data.size): List<MP4Box> {
        val boxes = mutableListOf<MP4Box>()
        var pos = offset
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        while (pos + 8 <= end) {
            var size = buf.getInt(pos).toLong() and 0xFFFFFFFFL
            val btype = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            var hdrLen = 8

            if (size == 1L) {
                if (pos + 16 > end) break
                size = buf.getLong(pos + 8)
                hdrLen = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }

            if (size < hdrLen || pos + size > end) break

            val boxData = data.copyOfRange(pos, (pos + size).toInt())
            val payload = data.copyOfRange(pos + hdrLen, (pos + size).toInt())
            boxes.add(MP4Box(btype, pos, size, hdrLen, boxData, payload))
            pos += size.toInt()
        }
        return boxes
    }

    private fun rebuildBox(type: String, payload: ByteArray): ByteArray {
        val totalSize = payload.size.toLong() + 8
        return if (totalSize > 0xFFFFFFFFL) {
            val buf = ByteBuffer.allocate(16 + payload.size).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(1)
            buf.put(type.toByteArray(Charsets.ISO_8859_1))
            buf.putLong(totalSize + 8)
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
        val boxes = parseBoxes(boxData, headerLen, boxData.size)
        return boxes.find { it.type == targetType }
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
