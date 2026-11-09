# RecompressBypass 🚀

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-1.2.0-blue.svg)](https://github.com/j7x69/Recompress-Bypass/releases)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20Jetpack%20Compose-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-red.svg)](./LICENSE)

A lightweight, native Android media companion designed to harmonize digital video containers,
preserving creator-intended visual clarity and frame pacing across modern social streaming workflows
(2K / 4K / 60FPS).

---

## 🌐 Languages / Ngôn ngữ
- [English (Main)](#-english)
- [Tiếng Việt](#-tiếng-việt)

---

## 🇬🇧 English

### 🌟 Key Highlights

* **Zero-Loss Container Harmonizer**: Polishes media envelopes directly at the binary layer without
  re-rendering pixels or introducing generation loss.
* **True-to-Master Visual Fidelity**: Keeps high-frame-rate motion and crisp color gradients intact
  when transitioning from desktop NLEs to mobile distribution.
* **Direct External Browser Handoff**: Launches your preferred system browser cleanly for studio
  uploads with zero embedded WebView overhead.
* **Modern Jetpack Compose UI**: Fluid animations, AMOLED Dark theme, instant remote notices, and
  seamless bilingual switching.

---

### 🎨 The Art of Media Conditioning (How It Works)

Think of a freshly exported video like a vinyl record placed inside a heavy, mislabeled shipping
crate—Automated cloud delivery couriers often repackage and crush the crate if the outer label looks
unfamiliar. **RecompressBypass** acts as a precision acoustic & packaging artisan:

1. **Temporal Horizon Alignment**: Calibrates the internal timeline compass so cloud playback
   engines read a smooth, uninterrupted horizon from the first frame to the last.
2. **Acoustic & Visual Counterbalance**: Introduces a delicate, silent harmonic cushion alongside
   the primary stream so automated transit gates treat the package with studio-grade care.
3. **Baggage Decluttering**: Gently sweeps away leftover editing studio dust, camera serial tags,
   and timeline sticky notes that often trigger heavy-handed cloud repackaging.
4. **Express Lane Arrangement**: Organizes the structural index at the very front of the file so
   streaming gateways can open and preview the artwork instantaneously.
5. **Authentic Studio Seal**: Leaves a clean, standardized courier stamp so receiving platforms
   recognize the file as ready-to-stream master media.

---

### 📱 Additional Features

* **Stream Link Inspector**: Built-in diagnostic utility to inspect public video links and verify
  delivery parameters.
* **Discord Webhook Telemetry**: Optional private webhook integration to keep track of your media
  conditioning history.
* **Instant Notice & Mandatory Version Sync**: Zero-latency status updates from `notice.json` and
  automatic version alignment via GitHub Releases so every user always runs the latest, safest
  build.

---

### 📥 Download & Installation
Pre-built, optimized APKs are available in the [Releases](https://github.com/j7x69/Recompress-Bypass/releases) section:

| Variant | Optimization | Target Architecture |
|---|---|---|
| **Release APK** | R8 / ProGuard Minified, ShrinkResources | ARM64 / universal |

Requirements:
* **Android 8.0 (API 26)** or higher.
* Fully compatible with Android 14 & 15.

---

### 🛠️ Building from Source
Ensure you have **Android Studio** (Hedgehog or newer) with **JDK 17**:

```powershell
# Clone the repository
git clone https://github.com/j7x69/Recompress-Bypass.git
cd Recompress-Bypass

# Build Release APK
.\gradlew.bat assembleRelease

# Output location: release/RecompressBypass-v1.2.0-release.apk
```

---

### 📄 License & Terms
This project is licensed under the **[Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](./LICENSE)** and **PolyForm Noncommercial 1.0.0**:
* **Allowed**: You are free to download, study, use, and modify the software for **personal, educational, and non-commercial** purposes with proper attribution to original author **j7x6**.
* **Strictly Prohibited**: **Commercial exploitation, resale, monetization, and unauthorized rebranding** (re-packaging and distributing under another name) without express written permission.

---

## 🇻🇳 Tiếng Việt

### 🌟 Tổng Quan Dự Án

**RecompressBypass** là công cụ đồng hành gọn nhẹ trên Android, đóng vai trò như một "nghệ nhân đóng
gói" giúp tinh chỉnh lại lớp vỏ bọc của tệp video chất lượng cao (2K / 4K / 60FPS) trước khi đưa lên
các nền tảng mạng xã hội, giữ trọn vẹn tâm huyết màu sắc và độ sắc nét nguyên bản từ bàn dựng.

---

### 🎨 Triết Lý "Đóng Gói & Thông Hành" (Cơ Chế Hoạt Động)

Hãy tưởng tượng một video vừa xuất từ phần mềm dựng phim giống như một bức tranh sơn dầu quý giá
nhưng lại được bọc trong chiếc thùng carton cồng kềnh, dán đầy nhãn mác lộn xộn—khi đi qua trạm kiểm
soát tự động, nhân viên kho vận thường sẽ ép phẳng chiếc thùng đó cho đỡ tốn diện tích.
**RecompressBypass** giải quyết vấn đề một cách tinh tế mà không hề chạm vào từng nét vẽ bên trong:

1. **Đồng điệu nhịp thở thời gian**: Chỉnh lại chiếc đồng hồ cát bên trong tệp tin, giúp các trạm
   phát sóng đọc hiểu hành trình của từng khung hình một cách mượt mà, liền mạch.
2. **Đệm âm thanh cân bằng**: Khéo léo lót thêm một lớp "đệm khí tĩnh lặng" siêu nhỏ bên cạnh dòng
   âm thanh chính, tạo độ êm ái để kiện hàng lướt qua các khâu sàng lọc tự động một cách nhẹ nhàng
   nhất.
3. **Gột rửa dấu vết hậu trường**: Nhẹ nhàng phủi sạch những mảnh giấy nháp, nhãn dán thừa thãi còn
   sót lại từ phòng dựng phim khiến hệ thống phân phối hiểu lầm là tệp tin chưa hoàn thiện.
4. **Sắp xếp lối đi ưu tiên**: Đặt tấm bản đồ chỉ dẫn ngay ở trang bìa của tệp tin, giúp trình phát
   có thể mở xem ngay lập tức mà không cần lục tìm từ đầu đến cuối.
5. **Niêm phong chuẩn phòng thu**: Đóng lại một dấu mộc gọn gàng, sạch sẽ để kiện hàng được ưu tiên
   đi thẳng vào luồng phát hành chất lượng cao.

---

### 📱 Tính Năng Bổ Trợ

* **Chuyển Tiếp Trình Duyệt Hệ Thống**: Mở trực tiếp trang đăng tải bằng trình duyệt mặc định trên
  máy của bạn, nhẹ nhàng, an toàn và không lưu lại bộ nhớ đệm thừa.
* **Thẩm Định Đường Dẫn Video**: Công cụ kiểm tra nhanh thông số phân phối của đường dẫn video công
  khai ngay trong ứng dụng.
* **Nhật Ký Discord Cá Nhân**: Tùy chọn gửi thông báo hoàn tất công việc về kênh Discord riêng tư
  của bạn.
* **Bản Tin Tức Thì & Đồng Bộ Phiên Bản**: Đọc thông báo trực tiếp từ `notice.json` không độ trễ và
  tự động đồng bộ lên phiên bản mới nhất mỗi khi có bản cập nhật, đảm bảo trải nghiệm ổn định nhất
  cho người dùng.

---

### 📄 Bản Quyền & Giấy Phép Sử Dụng
Mã nguồn được phân phối dưới giấy phép **[CC BY-NC 4.0 (Attribution-NonCommercial)](./LICENSE)** kết hợp **PolyForm Noncommercial**:
* **Được phép:** Sử dụng cá nhân, học tập, nghiên cứu và chia sẻ có ghi nguồn tác giả gốc (**j7x6**).
* **Nghiêm cấm:** Mọi hành vi **kinh doanh, thương mại hóa, bán app, hoặc rebrand (đổi tên ứng dụng nhận làm của riêng)** khi chưa có sự đồng ý bằng văn bản của tác giả.
