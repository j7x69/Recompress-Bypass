# MP4 Quality Optimizer 🚀

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Python Version](https://img.shields.io/badge/Python-3.7%2B-brightgreen.svg)](https://python.org)
[![Zero Dependencies](https://img.shields.io/badge/Dependencies-Zero%20(Standard%20Lib)-orange.svg)](requirements.txt)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg)]()

> **Bypass aggressive server-side video re-encoding on social media platforms (TikTok, Instagram Reels, YouTube Shorts) without re-encoding, quality loss, or watermarks.**

[Tiếng Việt](README_VI.md) | **English**

---

## 💡 The Problem

When uploading high-fidelity edits (1080p60, 4K, 120 FPS gaming clips from Premiere Pro, DaVinci Resolve, or CapCut) to platforms like TikTok and Reels, the ingestion pipeline automatically transcodes the footage. This often causes:
* Heavy macroblocking and compression artifacts.
* Severe bitrate slashing (from 10-20 Mbps down to 1.5-2.5 Mbps).
* Downscaling from 1080p/4K to 720p.

Commercial web optimizers (such as Haze Method / TikHaze) charge fees or insert proprietary watermarks for a technique that requires **no re-encoding**.

This tool is a completely **free, open-source, zero-dependency, and clean** implementation of that exact container-level bypass.

---

## 🔬 How the Exploit Works

This tool does **NOT** re-encode pixels or audio. Every single video frame and audio sample remains **100% bit-identical** ($PSNR = \infty$, $SSIM = 1.0$). 

Instead, it manipulates the **MP4 (ISO Base Media File Format) atom structure**:

```
+-------------------------------------------------------------------------+
| [ftyp] -> [free] -> [moov (FastStart Header)] -> [mdat (Raw Streams)]   |
+-------------------------------------------------------------------------+
```

1. **Virtual 400 FPS Injection (`stsz` & `stco`):**
   - Platform ingest servers calculate video framerate by inspecting container sample tables:
     $$\text{Reported FPS} = \frac{\text{Sample Count}}{\text{Track Duration}}$$
   - The optimizer calculates the required ghost samples to report **400 FPS** ($100.3\text{s} \times 400 = 40,120\text{ samples}$).
   - It appends a single 8-byte dummy payload (`00 00 00 04 00 00 00 00`) at the end of the `mdat` atom.
   - It populates the sample size table (`stsz`) and chunk offset table (`stco`) with dummy entries pointing to that same 8-byte payload.
2. **Timing Decoupling (`stts`):**
   - In standard players, 40,000 frames would cause playback to freeze or stretch to 10+ minutes.
   - The optimizer modifies the Time-to-Sample table (`stts`): the last real frame's duration delta is reduced to `1 tick`, and **no duration deltas are assigned to ghost samples**.
   - Result: Media players and browsers play the video at normal speed ($100\%$ original duration), but ingestion scanners see a 400 FPS stream.
3. **Bypass Trigger:**
   - Because social media transcode presets do not support extreme framerates ($>120\text{ FPS}$ or $400\text{ FPS}$), the server's transcode profile fails rate-control or triggers a **direct passthrough**, serving the high-bitrate original file to viewers.
4. **FastStart Relocation (`moov` at front):**
   - Moves the `moov` atom ahead of `mdat`, allowing web platforms to begin streaming and inspecting the file progressively without downloading the entire payload first.
5. **Zero Fingerprints:**
   - Cleans third-party watermarks, removes timestamps, and leaves no detectable exploit signature.

---

## ✨ Features

- ⚡ **Blazing Fast:** Processes a 100MB+ video in under **0.5 seconds** (pure binary container restructuring; no CPU/GPU rendering).
- 🎯 **100% Bit-Identical Quality:** Verified with Peak Signal-to-Noise Ratio ($PSNR = \infty$) and mathematical checksums.
- 🪶 **Zero External Dependencies:** Built entirely with Python's standard library (`struct`, `os`, `sys`, `argparse`). No FFmpeg, OpenCV, or external binaries required.
- 🛡️ **Memory Efficient:** Uses a 16MB chunked streaming buffer. Smoothly processes 10GB+ 4K files without eating RAM.
- 🖱️ **Drag-and-Drop Ready:** Includes a Windows `.bat` file for instant 1-click batch optimization.
- 🧹 **Stealth & Clean:** Zero third-party branding or metadata tags injected.

---

## 🚀 Quick Start

### Prerequisites
* [Python 3.7+](https://www.python.org/downloads/) installed and added to PATH.

### Installation
Clone the repository:
```bash
git clone https://github.com/your-username/mp4-quality-bypass.git
cd mp4-quality-bypass
```

---

## 📖 Usage

### Method 1: Windows Drag & Drop (Easiest)
1. Open Windows File Explorer.
2. Drag and drop one or multiple `.mp4` files onto **`drag_and_drop.bat`**.
3. Your optimized files (`[name]_optimized.mp4`) will appear alongside the original files instantly.

### Method 2: Command Line (CLI)

#### Basic Optimization
```bash
python optimizer.py input.mp4
```
*Output: `input_optimized.mp4`*

#### Custom Output File
```bash
python optimizer.py input.mp4 -o final_upload.mp4
```

#### Batch Processing (Wildcards)
```bash
python optimizer.py "C:\path\to\videos\*.mp4"
```

#### Advanced Options
```bash
# Custom virtual FPS (default is 400)
python optimizer.py input.mp4 --fps 400

# Wipe all user metadata tags (udta)
python optimizer.py input.mp4 --clean-metadata

# Verbose debug logging
python optimizer.py input.mp4 -v
```

---

## 📊 Verification & Benchmarks

Verification conducted using `ffprobe` and `ffmpeg`:

| Metric | Original Video | Optimized Video | Difference |
| :--- | :--- | :--- | :--- |
| **Video Resolution** | 1080 × 1920 | 1080 × 1920 | Identical (0%) |
| **Video Stream MD5** | `7310d32be...` | `7310d32be...` | **Bit-for-Bit Identical** |
| **Audio Stream MD5** | `00d51b125...` | `00d51b125...` | **Bit-for-Bit Identical** |
| **PSNR Score** | Base | $\infty$ (Infinity) | Zero compression loss |
| **Processing Time** | — | **0.42 seconds** | Instant |
| **Reported Frame Rate** | 60.00 FPS | ~400.00 FPS (Virtual) | Exploit active |
| **Playback Duration** | 100.30s | 100.28s | Normal playback |

---

## ❓ FAQ

#### Will this make my video lag or play at 400 FPS?
No. Media players follow the Presentation Timestamps (`pts` / `stts`). The video plays at normal 60 FPS playback speed.

#### Does this work on platforms other than TikTok?
Yes, it works on many platforms that employ automated heuristics for video transcoding, including Instagram Reels and YouTube Shorts.

#### Does this work with 4K or 120 FPS videos?
Yes. The script dynamically calculates the required dummy samples based on the exact duration of your file.

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).

---

## ⚠️ Disclaimer

This tool is provided for educational and research purposes only. Platform algorithms, ingestion policies, and container parsing implementations may change over time.
