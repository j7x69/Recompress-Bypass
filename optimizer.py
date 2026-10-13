#!/usr/bin/env python3
"""
MP4 Quality Bypass Optimizer
High-FPS Container Injection & FastStart Optimizer for Social Media Uploads.

Bypasses aggressive server-side re-encoding and compression on platforms like
TikTok, Reels, and Shorts by injecting sample table entries to trigger high-FPS
passthrough while keeping original audio and video streams 100% bit-identical.

Zero external dependencies - Uses Python standard library only.
"""

import os
import sys
import glob
import struct
import argparse
from typing import List, Dict, Optional, Tuple

# Ensure safe UTF-8 output on all platforms (especially Windows CMD / PowerShell)
try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

CHUNK_SIZE = 16 * 1024 * 1024  # 16 MB streaming buffer


class MP4BoxParser:
    """Lightweight MP4 ISO base media file format box parser."""

    @staticmethod
    def parse_boxes(data: bytes, offset: int = 0, end: Optional[int] = None) -> List[Dict]:
        if end is None:
            end = len(data)
        boxes = []
        pos = offset
        while pos + 8 <= end:
            size, btype = struct.unpack(">I4s", data[pos : pos + 8])
            btype = btype.decode("latin1", errors="replace")
            header_len = 8
            if size == 1:
                size = struct.unpack(">Q", data[pos + 8 : pos + 16])[0]
                header_len = 16
            elif size == 0:
                size = end - pos
            boxes.append({
                "type": btype,
                "pos": pos,
                "size": size,
                "header_len": header_len,
                "data": data[pos : pos + size],
            })
            pos += size
        return boxes

    @staticmethod
    def rebuild_box(btype: str, payload: bytes) -> bytes:
        return struct.pack(">I4s", len(payload) + 8, btype.encode("latin1")) + payload

    @staticmethod
    def find_in_box(box_data: bytes, target_type: str) -> Optional[Dict]:
        boxes = MP4BoxParser.parse_boxes(box_data, 8)
        for b in boxes:
            if b["type"] == target_type:
                return b
        return None


def optimize_mp4(
    input_path: str,
    output_path: Optional[str] = None,
    target_fps: int = 400,
    wipe_metadata: bool = False,
    verbose: bool = False,
) -> str:
    """
    Optimizes an MP4 video file using the high-FPS container injection technique.

    Args:
        input_path: Path to the source MP4 video.
        output_path: Destination path for the optimized file.
        target_fps: Virtual target framerate (default 400 FPS).
        wipe_metadata: If True, strips all user tags (udta).
        verbose: Enable detailed logging.

    Returns:
        The path of the created file.
    """
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
        free_data = b"\x00\x00\x00\x08free"

        while pos < file_size:
            f.seek(pos)
            hdr = f.read(8)
            if len(hdr) < 8:
                break
            size, btype = struct.unpack(">I4s", hdr)
            btype = btype.decode("latin1", errors="replace")
            hdr_len = 8
            if size == 1:
                size = struct.unpack(">Q", f.read(8))[0]
                hdr_len = 16
            elif size == 0:
                size = file_size - pos

            if btype == "ftyp":
                f.seek(pos)
                ftyp_data = f.read(size)
            elif btype == "free":
                f.seek(pos)
                free_data = f.read(size)
            elif btype == "moov":
                f.seek(pos)
                moov_box_info = {
                    "pos": pos,
                    "size": size,
                    "hdr_len": hdr_len,
                    "data": f.read(size),
                }
            elif btype == "mdat":
                mdat_box_info = {"pos": pos, "size": size, "hdr_len": hdr_len}

            pos += size

    if not moov_box_info or not mdat_box_info or not ftyp_data:
        raise ValueError("Invalid MP4 file: Missing moov, mdat, or ftyp atom.")

    # Prepare mdat container and 8-byte dummy sample
    orig_mdat_payload_len = mdat_box_info["size"] - mdat_box_info["hdr_len"]
    dummy_payload = b"\x00\x00\x00\x04\x00\x00\x00\x00"
    new_mdat_payload_len = orig_mdat_payload_len + len(dummy_payload)
    new_mdat_header = struct.pack(">I4s", new_mdat_payload_len + 8, b"mdat")

    # Parse and modify moov
    moov_data = moov_box_info["data"]
    moov_boxes = MP4BoxParser.parse_boxes(moov_data, 8)

    # 1. Reset creation/modification timestamps in mvhd
    mvhd_box = [b for b in moov_boxes if b["type"] == "mvhd"][0]
    mvhd_raw = bytearray(mvhd_box["data"])
    ver = mvhd_raw[8]
    if ver == 0:
        mvhd_raw[12:20] = b"\x00" * 8
        timescale, duration = struct.unpack(">II", mvhd_raw[20:28])
    else:
        mvhd_raw[12:28] = b"\x00" * 16
        timescale, duration = struct.unpack(">IQ", mvhd_raw[28:40])

    duration_sec = duration / timescale

    # 2. Handle metadata tags (udta)
    orig_udta = [b for b in moov_boxes if b["type"] == "udta"]
    udta_box_data = b"" if wipe_metadata else (orig_udta[0]["data"] if orig_udta else b"")

    # 3. Locate video track
    traks = [b for b in moov_boxes if b["type"] == "trak"]
    video_trak_idx = 0
    for idx, trak_box in enumerate(traks):
        mdia = MP4BoxParser.find_in_box(trak_box["data"], "mdia")
        if mdia:
            hdlr = MP4BoxParser.find_in_box(mdia["data"], "hdlr")
            if hdlr and len(hdlr["data"]) >= 24:
                subtype = hdlr["data"][16:20].decode("latin1", errors="replace")
                if subtype == "vide":
                    video_trak_idx = idx
                    break

    # Calculate required dummy sample count
    trak0_data = traks[video_trak_idx]["data"]
    mdia0 = MP4BoxParser.find_in_box(trak0_data, "mdia")
    minf0 = MP4BoxParser.find_in_box(mdia0["data"], "minf")
    stbl0 = MP4BoxParser.find_in_box(minf0["data"], "stbl")
    stsz0 = MP4BoxParser.find_in_box(stbl0["data"], "stsz")
    orig_sample_count = struct.unpack(">I", stsz0["data"][16:20])[0]

    total_target_samples = int(round(duration_sec * target_fps))
    dummy_count = max(0, total_target_samples - orig_sample_count)

    if verbose:
        print(f"    - Duration: {duration_sec:.2f}s")
        print(f"    - Original video frames: {orig_sample_count} (~{orig_sample_count/duration_sec:.1f} fps)")
        print(f"    - Target virtual framerate: {target_fps} fps")
        print(f"    - Injected dummy samples: {dummy_count}")

    def build_moov(dummy_chunk_offset: int, shift_amount: int) -> bytes:
        rebuilt_traks = []
        for idx, trak_box in enumerate(traks):
            trak_sub = MP4BoxParser.parse_boxes(trak_box["data"], 8)
            new_trak_children = []

            for tb in trak_sub:
                if tb["type"] == "tkhd":
                    tk_data = bytearray(tb["data"])
                    tk_ver = tk_data[8]
                    if tk_ver == 0:
                        tk_data[12:20] = b"\x00" * 8
                    else:
                        tk_data[12:28] = b"\x00" * 16
                    new_trak_children.append(tk_data)
                elif tb["type"] == "mdia":
                    mdia_sub = MP4BoxParser.parse_boxes(tb["data"], 8)
                    new_mdia_children = []
                    for mb in mdia_sub:
                        if mb["type"] == "mdhd":
                            md_data = bytearray(mb["data"])
                            md_ver = md_data[8]
                            if md_ver == 0:
                                md_data[12:20] = b"\x00" * 8
                            else:
                                md_data[12:28] = b"\x00" * 16
                            new_mdia_children.append(md_data)
                        elif mb["type"] == "minf":
                            minf_sub = MP4BoxParser.parse_boxes(mb["data"], 8)
                            new_minf_children = []
                            for mib in minf_sub:
                                if mib["type"] == "stbl":
                                    stbl_sub = MP4BoxParser.parse_boxes(mib["data"], 8)
                                    new_stbl_children = []
                                    for sb in stbl_sub:
                                        sb_type = sb["type"]
                                        sb_data = bytearray(sb["data"])
                                        if idx == video_trak_idx:
                                            if sb_type == "stts":
                                                # Set delta of the final sample to 1
                                                first_count, first_delta = struct.unpack(">II", sb_data[16:24])
                                                new_stts_payload = (
                                                    sb_data[8:12]
                                                    + struct.pack(">I", 2)
                                                    + struct.pack(">II", first_count - 1, first_delta)
                                                    + struct.pack(">II", 1, 1)
                                                )
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stts", new_stts_payload)
                                                )
                                            elif sb_type == "stsc":
                                                # Add chunk reference for dummy frames
                                                orig_stsc_count = struct.unpack(">I", sb_data[12:16])[0]
                                                orig_stco = MP4BoxParser.find_in_box(mib["data"], "stco")
                                                orig_chunk_count = struct.unpack(">I", orig_stco["data"][12:16])[0]
                                                new_entry = struct.pack(">III", orig_chunk_count + 1, 1, 1)
                                                new_stsc_payload = (
                                                    sb_data[8:12]
                                                    + struct.pack(">I", orig_stsc_count + 1)
                                                    + sb_data[16:]
                                                    + new_entry
                                                )
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stsc", new_stsc_payload)
                                                )
                                            elif sb_type == "stsz":
                                                # Append dummy size entries
                                                orig_sz_count = struct.unpack(">I", sb_data[16:20])[0]
                                                dummy_sizes = struct.pack(f">{dummy_count}I", *([8] * dummy_count))
                                                new_stsz_payload = (
                                                    sb_data[8:16]
                                                    + struct.pack(">I", orig_sz_count + dummy_count)
                                                    + sb_data[20:]
                                                    + dummy_sizes
                                                )
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stsz", new_stsz_payload)
                                                )
                                            elif sb_type == "stco":
                                                # Shift original chunk offsets and append dummy offsets
                                                orig_co_count = struct.unpack(">I", sb_data[12:16])[0]
                                                offsets = [
                                                    struct.unpack(">I", sb_data[16 + i * 4 : 20 + i * 4])[0]
                                                    for i in range(orig_co_count)
                                                ]
                                                shifted_offsets = [off + shift_amount for off in offsets]
                                                all_offsets = shifted_offsets + [dummy_chunk_offset] * dummy_count
                                                new_stco_payload = (
                                                    sb_data[8:12]
                                                    + struct.pack(">I", len(all_offsets))
                                                    + b"".join(struct.pack(">I", off) for off in all_offsets)
                                                )
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stco", new_stco_payload)
                                                )
                                            else:
                                                new_stbl_children.append(sb_data)
                                        else:
                                            # Audio / metadata track: shift chunk offsets
                                            if sb_type == "stco":
                                                orig_co_count = struct.unpack(">I", sb_data[12:16])[0]
                                                offsets = [
                                                    struct.unpack(">I", sb_data[16 + i * 4 : 20 + i * 4])[0]
                                                    for i in range(orig_co_count)
                                                ]
                                                shifted_offsets = [off + shift_amount for off in offsets]
                                                new_stco_payload = (
                                                    sb_data[8:12]
                                                    + struct.pack(">I", len(shifted_offsets))
                                                    + b"".join(struct.pack(">I", off) for off in shifted_offsets)
                                                )
                                                new_stbl_children.append(
                                                    MP4BoxParser.rebuild_box("stco", new_stco_payload)
                                                )
                                            else:
                                                new_stbl_children.append(sb_data)
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
            rebuilt_traks.append(MP4BoxParser.rebuild_box("trak", b"".join(new_trak_children)))

        new_moov_payload = mvhd_raw + b"".join(rebuilt_traks) + udta_box_data
        return MP4BoxParser.rebuild_box("moov", new_moov_payload)

    # Two-pass calculation to resolve cyclic offset dependency
    moov_dummy = build_moov(dummy_chunk_offset=0, shift_amount=0)
    final_moov_size = len(moov_dummy)

    header_offset = len(ftyp_data) + len(free_data)
    shift_amount = final_moov_size

    total_file_size = header_offset + final_moov_size + 8 + new_mdat_payload_len
    dummy_offset = total_file_size - 8

    final_moov = build_moov(dummy_chunk_offset=dummy_offset, shift_amount=shift_amount)
    assert len(final_moov) == final_moov_size, "Internal error: Box size mismatch during build."

    # Write output stream with chunked buffer
    with open(output_path, "wb") as out_f, open(input_path, "rb") as in_f:
        out_f.write(ftyp_data)
        out_f.write(free_data)
        out_f.write(final_moov)
        out_f.write(new_mdat_header)

        in_f.seek(mdat_box_info["pos"] + mdat_box_info["hdr_len"])
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
        epilog=(
            "Examples:\n"
            "  python optimizer.py video.mp4\n"
            "  python optimizer.py video.mp4 -o optimized.mp4\n"
            "  python optimizer.py *.mp4 --fps 400\n"
            "  python optimizer.py *.mp4 --clean-metadata\n"
        ),
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
        help="Custom output file path or directory (when processing single file)",
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

    # Expand glob patterns
    expanded_files = []
    for pattern in args.inputs:
        matched = glob.glob(pattern)
        if matched:
            expanded_files.extend(matched)
        else:
            expanded_files.append(pattern)

    # Filter to files only
    valid_files = [f for f in expanded_files if os.path.isfile(f)]
    if not valid_files:
        print("[!] No valid video files found matching the specified pattern(s).")
        sys.exit(1)

    print(f"=== MP4 Quality Bypass Optimizer ===")
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
