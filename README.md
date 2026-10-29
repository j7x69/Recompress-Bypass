# RecompressBypass 🚀

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-1.1.4-blue.svg)](https://github.com/j7x69/Recompress-Bypass/releases)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20Jetpack%20Compose-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-red.svg)](./LICENSE)

A powerful, native Android utility engineered to optimize and restructure MP4 container atoms, effectively bypassing aggressive recompression and visual quality degradation when uploading high-fidelity videos (2K / 4K / 60FPS) to **TikTok** and **TikTok Studio**.

---

## 🌐 Languages / Ngôn ngữ
- [English (Main)](#-english)
- [Tiếng Việt](#-tiếng-việt)

---

## 🇬🇧 English

### 🌟 Key Highlights
* **Native MP4 Container Engine**: Direct low-level binary atom manipulation (`RandomAccessFile` / buffered chunks) with zero quality re-encoding latency.
* **Aggressive Recompression Bypass**: Bypasses server-side downsampling and preserves true 2K/4K/60fps playback quality.
* **Integrated TikTok Studio WebView**: Upload directly inside the app with automated memory trimming, cache wiping, and persistent login session preservation.
* **Modern Jetpack Compose UI**: Fast, responsive, fluid animations with AMOLED Dark theme support and bilingual switching on the fly.

---

### ⚙️ Deep Technical Optimization Vectors
1. **64-bit Indeterminate Duration Normalization**: Re-aligns movie and track header durations (`mvhd` / `tkhd`) to prevent ingestion pipeline truncations.
2. **Audio Stream Sample Synchronization**: Calibrates secondary/ghost audio streams to enforce container integrity against automated audio filters.
3. **Container Normalization & Atom Stripping**: Strips toxic `tmcd`, `tref`, and proprietary camera metadata while normalizing timescales to 1000 Hz.
4. **ByteDance Ingest Tagging**: Emits internal ingest identifiers (`te_is_reencode = 1`, `Lavf63.7.100`) to signal pre-optimized streams.
5. **FastStart Stream Layout**: Reorganizes atom order into `ftyp` ➔ `moov` ➔ `free` ➔ `mdat` for instant cloud stream parsing and web preview playback.
6. **Watermark & Residue Sanitizer**: Cleans known trace signatures from third-party editors (Haze, Vague, Nova Normal) ensuring authentic stream signatures.

---

### 📱 Additional Features
* **TikTok Link Auditor**: In-app inspection tool to analyze TikTok video stream URLs and audit video metadata.
* **Discord Webhook Logging**: Optional integration to log optimization tasks and upload metrics to your private Discord server.
* **In-App Notice & Auto-Update Engine**: Real-time notifications and painless OTA updates backed by GitHub Releases.

---

### 📥 Download & Installation
Pre-built, optimized APKs are available in the [Releases](https://github.com/j7x69/Recompress-Bypass/releases) section:

| Variant | Optimization | Target Architecture |
|---|---|---|
| **Release APK** | R8 / ProGuard Minified, ShrinkResources | ARM64 / universal |
| **Debug APK** | Standard build with logging tools | All debug devices |

Requirements:
* **Android 8.0 (API 26)** or higher.
* Compatible with Android 14 & 15.

---

### 🛠️ Building from Source
Ensure you have **Android Studio** (Hedgehog or newer) with **JDK 17**:

```powershell
# Clone the repository
git clone https://github.com/j7x69/Recompress-Bypass.git
cd Recompress-Bypass

# Build Release APK
.\gradlew.bat assembleRelease

# Output location: release/RecompressBypass-v1.1.4-release.apk
```

---

### 📄 License & Terms
This project is licensed under the **[Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](./LICENSE)** and **PolyForm Noncommercial 1.0.0**:
* **Allowed**: You are free to download, study, use, and modify the software for **personal, educational, and non-commercial** purposes with proper attribution to original author **j7x6**.
* **Strictly Prohibited**: **Commercial exploitation, resale, monetization, and unauthorized rebranding** (re-packaging and distributing under another name) without express written permission.

---

## 🇻🇳 Tiếng Việt

### 🌟 Tổng Quan Dự Án
**RecompressBypass** là ứng dụng Android chuyên dụng giúp xử lý và tái cấu trúc cấu trúc file MP4 (box/atom) ở tầng nhị phân, loại bỏ tình trạng TikTok và TikTok Studio nén giảm độ nét (bệt màu, mất chi tiết, giảm bitrate) đối với video chất lượng cao (2K / 4K / 60FPS).

---

### ⚙️ Các Cơ Chế Tối Ưu Container
1. **Chuẩn hóa thời lượng 64-bit**: Đồng bộ header `mvhd` và `tkhd` giúp hệ thống render của TikTok không cắt vụn hoặc hạ cấp chất lượng.
2. **Đồng bộ luồng âm thanh thứ cấp**: Cân chỉnh stream audio, chống hiện tượng lệch tiếng hoặc kích hoạt bộ lọc âm thanh tự động.
3. **Loại bỏ atom rác (`tmcd`, `tref`)**: Dọn sạch các metadata độc hại từ phần mềm dựng phim, chuẩn hóa timescale về 1000 Hz.
4. **Gắn ByteDance Ingest Tag**: Bổ sung cờ nhận diện luồng (`te_is_reencode = 1`, `Lavf63.7.100`) giúp hệ thống kiểm duyệt TikTok hiểu video đã qua tối ưu hóa.
5. **Cấu trúc FastStart chuẩn quốc tế**: Đưa cấu trúc container về dạng `ftyp` ➔ `moov` ➔ `free` ➔ `mdat` giúp tải nhanh tức thì và không bị lỗi buffer.
6. **Khử dấu vết metadata**: Tự động dọn sạch các chữ ký watermark từ các công cụ cũ (Haze, Vague, Nova Normal).

---

### 📱 Tính Năng Bổ Trợ
* **TikTok Studio WebView Tích Hợp**: Đăng tải trực tiếp trong app, có cơ chế tự động dọn dẹp cache / bộ nhớ đệm và bảo tồn phiên đăng nhập an toàn.
* **Soi Link Video TikTok**: Kiểm tra chất lượng và nguồn phát link TikTok trực tiếp trong app.
* **Gửi Log Discord Webhook**: Báo cáo lịch sử tối ưu video về máy chủ Discord cá nhân.
* **Giao Diện Jetpack Compose Hiện Đại**: Hỗ trợ Dark Mode AMOLED siêu tiết kiệm pin, chuyển đổi ngôn ngữ Anh - Việt linh hoạt.

---

### 📄 Bản Quyền & Giấy Phép Sử Dụng
Mã nguồn được phân phối dưới giấy phép **[CC BY-NC 4.0 (Attribution-NonCommercial)](./LICENSE)** kết hợp **PolyForm Noncommercial**:
* **Được phép:** Sử dụng cá nhân, học tập, nghiên cứu và chia sẻ có ghi nguồn tác giả gốc (**j7x6**).
* **Nghiêm cấm:** Mọi hành vi **kinh doanh, thương mại hóa, bán app, hoặc rebrand (đổi tên ứng dụng nhận làm của riêng)** khi chưa có sự đồng ý bằng văn bản của tác giả.
