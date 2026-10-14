#!/usr/bin/env python3
"""
MP4 Quality Bypass Optimizer (Refactored & Hardened)
High-FPS Container Injection & FastStart Optimizer for Social Media Uploads.
Zero external dependencies - Uses Python standard library only.
"""

import os
import sys
import glob
import struct
import argparse
from typing import List, Dict, Optional

# Ensure safe UTF-8 output on all platforms
try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

CHUNK_SIZE = 16 * 1024 * 1024  # 16 MB streaming buffer


class MP4BoxParser:
    """Robust MP4 ISO base media file format box parser."""

    @staticmethod
    def parse_boxes(data: bytes, offset: int = 0, end: Optional[int] = None) -> List[Dict]:
        if end is None:
            end = len(data)
        boxes = []
        pos = offset
        while pos + 8 <= end:
            size, btype = struct.unpack(">I4s", data[pos : pos + 8])
            btype_str = btype.decode("latin1", errors="replace")
            header_len = 8
            if size == 1:
                if pos + 16 > end:
                    break
                size = struct.unpack(">Q", data[pos + 8 : pos + 16])[0]
                header_len = 16
            elif size == 0:
                size = end - pos

            if size < header_len or pos + size > end:
                break

            boxes.append({
                "type": btype_str,
                "pos": pos,
                "size": size,
                "header_len": header_len,
                "data": data[pos : pos + size],
                "payload": data[pos + header_len : pos + size],
            })
            pos += size
        return boxes

    @staticmethod
    def rebuild_box(btype: str, payload: bytes) -> bytes:
        total_size = len(payload) + 8
        if total_size > 0xFFFFFFFF:
            return struct.pack(">I4sQ", 1, btype.encode("latin1"), len(payload) + 16) + payload
        return struct.pack(">I4s", total_size, btype.encode("latin1")) + payload

    @staticmethod
    def find_in_box(box_data: bytes, header_len: int, target_type: str) -> Optional[Dict]:
        boxes = MP4BoxParser.parse_boxes(box_data, header_len)
        for b in boxes:
            if b["type"] == target_type:
                return b
        return None


def patch_stts(stts_payload: bytes) -> bytes:
    """
    Preserves all original VFR/CFR timing entries and only decouples the very last real frame
    by setting its delta to 1 tick.
    """
    if len(stts_payload) < 8:
        return stts_payload
    version_flags = stts_payload[:4]
    entry_count = struct.unpack(">I", stts_payload[4:8])[0]
    if entry_count == 0 or len(stts_payload) < 8 + entry_count * 8:
        return stts_payload

    entries = []
    for i in range(entry_count):
        offset = 8 + i * 8
        count, delta = struct.unpack(">II", stts_payload[offset : offset + 8])
        entries.append((count, delta))

    last_count, last_delta = entries[-1]
    if last_count > 1:
        entries[-1] = (last_count - 1, last_delta)
        entries.append((1, 1))
    else:
        entries[-1] = (1, 1)

    new_payload = bytearray(version_flags)
    new_payload += struct.pack(">I", len(entries))
    for c, d in entries:
        new_payload += struct.pack(">II", c, d)
    return bytes(new_payload)


def patch_stsz(stsz_payload: bytes, dummy_count: int, dummy_size: int = 8) -> bytes:
    """
    Handles both uniform (sample_size > 0) and variable (sample_size == 0) sample size tables.
    """
    version_flags = stsz_payload[:4]
    sample_size, sample_count = struct.unpack(">II", stsz_payload[4:12])

    if sample_size == 0:
        existing_entries = stsz_payload[12 : 12 + sample_count * 4]
    else:
        # Expand uniform size into explicit table so dummy samples can have size 8
        existing_entries = struct.pack(f">{sample_count}I", *([sample_size] * sample_count))

    dummy_entries = struct.pack(f">{dummy_count}I", *([dummy_size] * dummy_count))
    total_count = sample_count + dummy_count

    return (
        version_flags
        + struct.pack(">II", 0, total_count)
        + existing_entries
        + dummy_entries
    )


def optimize_mp4(
    input_path: str,
    output_path: Optional[str] = None,
    target_fps: int = 400,
    wipe_metadata: bool = False,
    verbose: bool = False,
) -> str:
    if not os.path.isfile(input_path):
        raise FileNotFoundError(f"Input file not found: {input_path}")

    if not output_path:
        base, ext = os.path.splitext(input_path)
        output_path = f"{base}_optimized{ext}"

    if os.path.abspath(input_path) == os.path.abspath(output_path):
        raise ValueError("Input and output file paths must be different.")

    file_size = os.path.getsize(input_path)
    print(f"[*] Processing: {os.path.basename(input_path)} ({file_size / (1024*1024):.2f} MB)")

    with open(input_path, "rb") as f:
        pos = 0
        mdat_box_info = None
        moov_box_info = None
        ftyp_data = None

        while pos < file_size:
            f.seek(pos)
            hdr = f.read(8)
            if len(hdr) < 8:
                break
            size, btype = struct.unpack(">I4s", hdr)
            btype_str = btype.decode("latin1", errors="replace")
            hdr_len = 8
            if size == 1:
                ext_hdr = f.read(8)
                if len(ext_hdr) < 8:
                    break
                size = struct.unpack(">Q", ext_hdr)[0]
                hdr_len = 16
            elif size == 0:
                size = file_size - pos

            if size < hdr_len:
                raise ValueError(f"Malformed box '{btype_str}' at offset {pos}.")

            if btype_str == "ftyp" and ftyp_data is None:
                f.seek(pos)
                ftyp_data = f.read(size)
            elif btype_str == "moov" and moov_box_info is None:
                f.seek(pos)
                moov_box_info = {
                    "pos": pos,
                    "size": size,
                    "hdr_len": hdr_len,
                    "data": f.read(size),
                }
            elif btype_str == "mdat":
                # Keep the largest mdat if multiple exist
                if mdat_box_info is None or size > mdat_box_info["size"]:
                    mdat_box_info = {"pos": pos, "size": size, "hdr_len": hdr_len}

            pos += size

    if not moov_box_info or not mdat_box_info or not ftyp_data:
        raise ValueError("Invalid MP4 file: Missing moov, mdat, or ftyp atom.")

    free_data = b"\x00\x00\x00\x08free"

    # Calculate mdat payload and header (32-bit vs 64-bit largesize)
    orig_mdat_data_offset = mdat_box_info["pos"] + mdat_box_info["hdr_len"]
    orig_mdat_payload_len = mdat_box_info["size"] - mdat_box_info["hdr_len"]
    dummy_payload = b"\x00\x00\x00\x04\x00\x00\x00\x00"
    new_mdat_payload_len = orig_mdat_payload_len + len(dummy_payload)

    if new_mdat_payload_len + 8 > 0xFFFFFFFF:
        new_mdat_header = struct.pack(">I4sQ", 1, b"mdat", new_mdat_payload_len + 16)
    else:
        new_mdat_header = struct.pack(">I4s", new_mdat_payload_len + 8, b"mdat")

    new_mdat_hdr_len = len(new_mdat_header)

    # Parse top-level children of moov
    moov_boxes = MP4BoxParser.parse_boxes(moov_box_info["data"], moov_box_info["hdr_len"])

    # Locate mvhd for duration calculation
    mvhd_boxes = [b for b in moov_boxes if b["type"] == "mvhd"]
    if not mvhd_boxes:
        raise ValueError("Invalid MP4 file: Missing mvhd atom inside moov.")
    mvhd_payload = bytearray(mvhd_boxes[0]["payload"])
    ver = mvhd_payload[0]
    if ver == 0:
        timescale, duration = struct.unpack(">II", mvhd_payload[12:20])
    else:
        timescale, duration = struct.unpack(">IQ", mvhd_payload[20:32])

    if timescale == 0:
        raise ValueError("Invalid MP4 file: mvhd timescale is 0.")
    duration_sec = duration / timescale

    # Locate video track and inspect sample count
    video_trak_idx = None
    orig_sample_count = 0

    for idx, b in enumerate(moov_boxes):
        if b["type"] != "trak":
            continue
        mdia = MP4BoxParser.find_in_box(b["data"], b["header_len"], "mdia")
        if not mdia:
            continue
        hdlr = MP4BoxParser.find_in_box(mdia["data"], mdia["header_len"], "hdlr")
        if hdlr and len(hdlr["payload"]) >= 12:
            subtype = hdlr["payload"][8:12].decode("latin1", errors="replace")
            if subtype == "vide":
                video_trak_idx = idx
                minf = MP4BoxParser.find_in_box(mdia["data"], mdia["header_len"], "minf")
                stbl = MP4BoxParser.find_in_box(minf["data"], minf["header_len"], "stbl") if minf else None
                stsz = MP4BoxParser.find_in_box(stbl["data"], stbl["header_len"], "stsz") if stbl else None
                if not stsz:
                    raise ValueError("Video track is missing stsz atom.")
                orig_sample_count = struct.unpack(">I", stsz["payload"][8:12])[0]
                break

    if video_trak_idx is None:
        raise ValueError("No video track ('vide') found in MP4 file.")

    total_target_samples = int(round(duration_sec * target_fps))
    dummy_count = max(0, total_target_samples - orig_sample_count)

    if verbose:
        print(f"    - Duration: {duration_sec:.2f}s")
        print(f"    - Original video frames: {orig_sample_count} (~{orig_sample_count/duration_sec:.2f} fps)")
        print(f"    - Target virtual framerate: {target_fps} fps")
        print(f"    - Injected dummy samples: {dummy_count}")

    def build_moov(dummy_chunk_offset: int, shift_amount: int, force_co64: bool = False) -> bytes:
        new_moov_children = []

        for idx, b in enumerate(moov_boxes):
            btype = b["type"]
            if btype == "udta" and wipe_metadata:
                continue
            elif btype == "mvhd":
                p = bytearray(b["payload"])
                if p[0] == 0:
                    p[4:12] = b"\x00" * 8
                else:
                    p[4:20] = b"\x00" * 16
                new_moov_children.append(MP4BoxParser.rebuild_box("mvhd", bytes(p)))
            elif btype == "trak":
                is_video = (idx == video_trak_idx)
                trak_sub = MP4BoxParser.parse_boxes(b["data"], b["header_len"])
                new_trak_children = []

                for tb in trak_sub:
                    if tb["type"] == "tkhd":
                        tp = bytearray(tb["payload"])
                        if tp[0] == 0:
                            tp[4:12] = b"\x00" * 8
                        else:
                            tp[4:20] = b"\x00" * 16
                        new_trak_children.append(MP4BoxParser.rebuild_box("tkhd", bytes(tp)))
                    elif tb["type"] == "mdia":
                        mdia_sub = MP4BoxParser.parse_boxes(tb["data"], tb["header_len"])
                        new_mdia_children = []

                        for mb in mdia_sub:
                            if mb["type"] == "mdhd":
                                mp = bytearray(mb["payload"])
                                if mp[0] == 0:
                                    mp[4:12] = b"\x00" * 8
                                else:
                                    mp[4:20] = b"\x00" * 16
                                new_mdia_children.append(MP4BoxParser.rebuild_box("mdhd", bytes(mp)))
                            elif mb["type"] == "minf":
                                minf_sub = MP4BoxParser.parse_boxes(mb["data"], mb["header_len"])
                                new_minf_children = []

                                for mib in minf_sub:
                                    if mib["type"] == "stbl":
                                        stbl_sub = MP4BoxParser.parse_boxes(mib["data"], mib["header_len"])
                                        new_stbl_children = []

                                        # Pre-extract original chunk count for stsc update
                                        orig_chunk_count = 0
                                        for sb_check in stbl_sub:
                                            if sb_check["type"] in ("stco", "co64"):
                                                orig_chunk_count = struct.unpack(">I", sb_check["payload"][4:8])[0]
                                                break

                                        for sb in stbl_sub:
                                            sb_type = sb["type"]
                                            sb_payload = sb["payload"]

                                            if is_video and dummy_count > 0 and sb_type == "stts":
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stts", patch_stts(sb_payload))
                                                )
                                            elif is_video and dummy_count > 0 and sb_type == "stsc":
                                                orig_stsc_count = struct.unpack(">I", sb_payload[4:8])[0]
                                                new_entry = struct.pack(">III", orig_chunk_count + 1, 1, 1)
                                                new_stsc_payload = (
                                                    sb_payload[:4]
                                                    + struct.pack(">I", orig_stsc_count + 1)
                                                    + sb_payload[8:]
                                                    + new_entry
                                                )
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stsc", new_stsc_payload)
                                                )
                                            elif is_video and dummy_count > 0 and sb_type == "stsz":
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box(
                                                        "stsz", patch_stsz(sb_payload, dummy_count, 8)
                                                    )
                                                )
                                            elif sb_type in ("stco", "co64"):
                                                count = struct.unpack(">I", sb_payload[4:8])[0]
                                                if sb_type == "stco":
                                                    offsets = [
                                                        struct.unpack(">I", sb_payload[8 + i * 4 : 12 + i * 4])[0]
                                                        for i in range(count)
                                                    ]
                                                else:
                                                    offsets = [
                                                        struct.unpack(">Q", sb_payload[8 + i * 8 : 16 + i * 8])[0]
                                                        for i in range(count)
                                                    ]

                                                shifted = [off + shift_amount for off in offsets]
                                                if is_video and dummy_count > 0:
                                                    shifted.extend([dummy_chunk_offset] * dummy_count)

                                                use_64 = force_co64 or (sb_type == "co64") or any(o > 0xFFFFFFFF for o in shifted)
                                                if use_64:
                                                    co_payload = (
                                                        sb_payload[:4]
                                                        + struct.pack(">I", len(shifted))
                                                        + b"".join(struct.pack(">Q", max(0, o)) for o in shifted)
                                                    )
                                                    new_stbl_children.append(MP4BoxParser.rebuild_box("co64", co_payload))
                                                else:
                                                    co_payload = (
                                                        sb_payload[:4]
                                                        + struct.pack(">I", len(shifted))
                                                        + b"".join(struct.pack(">I", max(0, o)) for o in shifted)
                                                    )
                                                    new_stbl_children.append(MP4BoxParser.rebuild_box("stco", co_payload))
                                            else:
                                                new_stbl_children.append(sb["data"])

                                        new_minf_children.append(
                                            MP4BoxParser.rebuild_box("stbl", b"".join(new_stbl_children))
                                        )
                                    else:
                                        new_minf_children.append(mib["data"])
                                new_mdia_children.append(
                                    MP4BoxParser.rebuild_box("minf", b"".join(new_minf_children))
                                )
                            else:
                                new_mdia_children.append(mb["data"])
                        new_trak_children.append(
                            MP4BoxParser.rebuild_box("mdia", b"".join(new_mdia_children))
                        )
                    else:
                        new_trak_children.append(tb["data"])
                new_moov_children.append(
                    MP4BoxParser.rebuild_box("trak", b"".join(new_trak_children))
                )
            else:
                # Preserve other top-level moov boxes (e.g., meta, iods, mvex)
                new_moov_children.append(b["data"])

        return MP4BoxParser.rebuild_box("moov", b"".join(new_moov_children))

    # Determine whether output file exceeds 32-bit offset limits
    est_size = len(ftyp_data) + len(free_data) + moov_box_info["size"] + new_mdat_hdr_len + new_mdat_payload_len
    force_64 = est_size > 0xFFFFFFFF

    # Pass 1: Measure exact rebuilt moov size
    moov_pass1 = build_moov(dummy_chunk_offset=0, shift_amount=0, force_co64=force_64)
    final_moov_size = len(moov_pass1)

    # Calculate exact new mdat payload start offset and exact shift delta
    header_offset = len(ftyp_data) + len(free_data)
    new_mdat_data_offset = header_offset + final_moov_size + new_mdat_hdr_len
    shift_amount = new_mdat_data_offset - orig_mdat_data_offset
    dummy_offset = new_mdat_data_offset + orig_mdat_payload_len

    if dummy_offset > 0xFFFFFFFF and not force_64:
        force_64 = True
        moov_pass1 = build_moov(dummy_chunk_offset=0, shift_amount=0, force_co64=True)
        final_moov_size = len(moov_pass1)
        new_mdat_data_offset = header_offset + final_moov_size + new_mdat_hdr_len
        shift_amount = new_mdat_data_offset - orig_mdat_data_offset
        dummy_offset = new_mdat_data_offset + orig_mdat_payload_len

    # Pass 2: Build final moov with exact offsets
    final_moov = build_moov(
        dummy_chunk_offset=dummy_offset,
        shift_amount=shift_amount,
        force_co64=force_64,
    )
    assert len(final_moov) == final_moov_size, "Internal error: Box size mismatch during two-pass build."

    # Write output stream with 16MB chunked buffer
    with open(output_path, "wb") as out_f, open(input_path, "rb") as in_f:
        out_f.write(ftyp_data)
        out_f.write(free_data)
        out_f.write(final_moov)
        out_f.write(new_mdat_header)

        in_f.seek(orig_mdat_data_offset)
        remaining = orig_mdat_payload_len
        while remaining > 0:
            to_read = min(remaining, CHUNK_SIZE)
            chunk = in_f.read(to_read)
            if not chunk:
                break
            out_f.write(chunk)
            remaining -= len(chunk)

        out_f.write(dummy_payload)

    print(f"[+] Output ready: {output_path}\n")
    return output_path


def main():
    parser = argparse.ArgumentParser(
        description="MP4 Quality Bypass Optimizer - Social Media Compression Bypass Tool",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "inputs",
        nargs="+",
        help="Input video file path(s) or glob patterns (e.g., input.mp4, *.mp4)",
    )
    parser.add_argument(
        "-o",
        "--output",
        default=None,
        help="Custom output file path (when processing a single file)",
    )
    parser.add_argument(
        "--fps",
        type=int,
        default=400,
        help="Virtual target framerate to fool platform ingestion (default: 400)",
    )
    parser.add_argument(
        "--clean-metadata",
        action="store_true",
        help="Strip all existing user metadata tags (udta)",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Print detailed box structures and calculations",
    )

    args = parser.parse_args()

    expanded_files = []
    for pattern in args.inputs:
        matched = glob.glob(pattern)
        if matched:
            expanded_files.extend(matched)
        else:
            expanded_files.append(pattern)

    valid_files = [f for f in expanded_files if os.path.isfile(f)]
    if not valid_files:
        print("[!] No valid video files found matching the specified pattern(s).")
        sys.exit(1)

    print("=== MP4 Quality Bypass Optimizer ===")
    print(f"Files to process: {len(valid_files)}\n")

    for file_path in valid_files:
        out_path = args.output if (len(valid_files) == 1 and args.output) else None
        try:
            optimize_mp4(
                input_path=file_path,
                output_path=out_path,
                target_fps=args.fps,
                wipe_metadata=args.clean_metadata,
                verbose=args.verbose,
            )
        except Exception as e:
            print(f"[!] Error processing {file_path}: {e}")

    print("[*] All tasks completed.")


if __name__ == "__main__":
    main()