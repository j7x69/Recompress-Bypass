# MP4 Quality Optimizer (Tiếng Việt) 🚀

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Python Version](https://img.shields.io/badge/Python-3.7%2B-brightgreen.svg)](https://python.org)
[![Zero Dependencies](https://img.shields.io/badge/Dependencies-Zero%20(Standard%20Lib)-orange.svg)](requirements.txt)

> **Công cụ tối ưu hóa video chống nén vỡ hình khi đăng TikTok, Instagram Reels, YouTube Shorts — Hoàn toàn miễn phí, mã nguồn mở, không nén lại video, giữ 100% chất lượng gốc và không chứa watermark.**

**Tiếng Việt** | [English](README.md)

---

## 💡 Vấn đề thực tế

Khi bạn xuất các video chất lượng cao (1080p60, 4K, clip game 120 FPS dựng từ Premiere Pro, DaVinci Resolve, After Effects, CapCut) và tải lên TikTok hay Reels, máy chủ của các nền tảng này sẽ tự động nén (transcode) lại video, dẫn đến:
* Video bị vỡ hạt, răng cưa (macroblocking).
* Bitrate bị bóp nghẹt từ 10-20 Mbps xuống chỉ còn 1.5-2.5 Mbps.
* Video bị ép độ phân giải xuống 720p.

Nhiều dịch vụ web thương mại (như Haze Method / TikHaze) thu phí bản quyền hoặc chèn watermark/quảng cáo chỉ để thực hiện một thủ thuật cấu trúc file cơ bản.

Dự án này là bản cài đặt **mã nguồn mở, hoàn toàn miễn phí, không phụ thuộc thư viện ngoài (zero-dependency)**, giải phóng video của bạn khỏi mọi dấu vết thương mại.

---

## 🔬 Cơ chế hoạt động (Giải mã Exploit)

Công cụ này **KHÔNG** hề render hay encode lại hình ảnh hay âm thanh (tiết kiệm thời gian, không tốn tài nguyên GPU/CPU). Mọi khung hình video và âm thanh đều giữ nguyên **100% từng bit** ($PSNR = \infty$, $SSIM = 1.0$).

Công cụ can thiệp trực tiếp vào cấu trúc **Atom của định dạng MP4 (ISO Base Media File Format)**:

```
+-------------------------------------------------------------------------+
| [ftyp] -> [free] -> [moov (Header FastStart)] -> [mdat (Dữ liệu gốc)]   |
+-------------------------------------------------------------------------+
```

1. **Bơm 400 FPS ảo (`stsz` & `stco`):**
   - Máy chủ TikTok/Reels ước tính tốc độ khung hình (FPS) qua công thức:
     $$\text{FPS hiển thị} = \frac{\text{Tổng số sample}}{\text{Thời lượng video}}$$
   - Công cụ tự động tính toán số sample ma cần thiết để đạt **400 FPS** ($100.3\text{s} \times 400 = 40,120\text{ samples}$).
   - Đính kèm duy nhất một khối dữ liệu 8 byte (`00 00 00 04 00 00 00 00`) vào cuối atom `mdat`.
   - Khai báo hàng chục ngàn sample giả trong bảng kích thước (`stsz`) và bảng offset (`stco`), tất cả đều trỏ về 8 byte này.
2. **Tách biệt thời gian thực tế (`stts`):**
   - Thông thường, nếu chèn 40,000 frame thì trình phát video sẽ bị đứng hình hoặc video bị kéo dài thành 10 phút.
   - Công cụ thay đổi bảng `stts`: frame video thật cuối cùng có thời lượng giảm về `1 tick`, và **không cấp thời lượng cho các frame ma**.
   - Kết quả: Các trình phát video (VLC, điện thoại, trình duyệt) vẫn phát video ở tốc độ bình thường 60 FPS ($100\%$ thời lượng gốc), nhưng máy quét nén của TikTok thì nhận diện video là 400 FPS.
3. **Kích hoạt chế độ Bypass:**
   - Vì hệ thống nén tự động của TikTok/Reels không có cấu hình nén dành cho các mức FPS dị biệt như 400 FPS, máy chủ sẽ kích hoạt **cơ chế bỏ qua nén (direct passthrough)** và phục vụ luồng video chất lượng cao nguyên bản cho người xem.
4. **FastStart Relocation (Đưa `moov` lên đầu):**
   - Đưa header `moov` lên trước `mdat`, giúp nền tảng mạng xã hội và trình duyệt có thể stream dữ liệu ngay lập tức mà không cần đợi tải hết cả file.
5. **Xóa sạch dấu vết:**
   - Không chứa bất kỳ watermark hay nhãn mác bên thứ 3 nào, đảm bảo file hoàn toàn sạch sẽ như xuất trực tiếp từ phần mềm dựng phim.

---

## ✨ Điểm nổi bật

- ⚡ **Siêu nhanh:** Xử lý video 100MB+ chỉ trong **chưa đầy 0.5 giây**.
- 🎯 **Chất lượng nguyên bản 100%:** Kiểm chứng bằng chỉ số PSNR = $\infty$ (vô cực).
- 🪶 **Không cần cài đặt thư viện:** Chỉ dùng thư viện có sẵn của Python (`struct`, `os`, `sys`, `argparse`). Không cần cài FFmpeg hay OpenCV.
- 🛡️ **Tối ưu RAM:** Bộ đệm đọc ghi 16MB stream liên tục, chạy mượt video 4K dung lượng 10GB+ mà không lo tràn RAM.
- 🖱️ **Kéo-thả tiện lợi:** Có sẵn file `.bat` cho Windows để kéo thả xử lý hàng loạt chỉ với 1 cú click.

---

## 🚀 Hướng dẫn nhanh

### Yêu cầu
* Máy tính đã cài [Python 3.7 trở lên](https://www.python.org/downloads/) (nhớ tích chọn *Add Python to PATH* khi cài).

---

## 📖 Cách sử dụng

### Cách 1: Kéo thả trên Windows (Đơn giản nhất)
1. Mở File Explorer.
2. Kéo một hoặc nhiều file video `.mp4` và **thả thẳng vào file `drag_and_drop.bat`**.
3. File tối ưu hóa (`[tên]_optimized.mp4`) sẽ xuất hiện ngay lập tức bên cạnh file gốc.

### Cách 2: Dùng dòng lệnh (CLI)

#### Tối ưu 1 file
```bash
python optimizer.py video.mp4
```
*File xuất ra sẽ là `video_optimized.mp4`*

#### Tùy chỉnh tên file xuất
```bash
python optimizer.py video.mp4 -o video_xuat.mp4
```

#### Xử lý hàng loạt toàn bộ thư mục
```bash
python optimizer.py "C:\duong_dan_thu_muc\*.mp4"
```

#### Tùy chọn nâng cao
```bash
# Thay đổi mức FPS ảo (mặc định: 400)
python optimizer.py video.mp4 --fps 400

# Xóa sạch toàn bộ metadata người dùng (udta)
python optimizer.py video.mp4 --clean-metadata

# Xem log chi tiết từng khối atom
python optimizer.py video.mp4 -v
```

---

## 📊 Bảng đối chiếu thực tế

| Tiêu chí | Video gốc | Video sau khi xử lý | Đánh giá |
| :--- | :--- | :--- | :--- |
| **Độ phân giải** | 1080 × 1920 | 1080 × 1920 | Giống nhau 100% |
| **Mã băm Video Stream** | `7310d32be...` | `7310d32be...` | **Giống nhau từng bit** |
| **Mã băm Audio Stream** | `00d51b125...` | `00d51b125...` | **Giống nhau từng bit** |
| **Chỉ số PSNR** | Gốc | $\infty$ (Vô cực) | Không suy hao chất lượng |
| **Thời gian xử lý** | — | **0.42 giây** | Gần như tức thì |
| **FPS hiển thị** | 60.00 FPS | ~400.00 FPS (Ảo) | Exploit kích hoạt |
| **Thời lượng xem thực tế** | 100.30 giây | 100.28 giây | Phát bình thường |

---

## 📜 Giấy phép (License)

Dự án phát hành theo giấy phép [MIT License](LICENSE).
