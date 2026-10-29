# Quy Chuẩn & Bộ Nhớ Dự Án (Workspace Memory) — RecompressBypass

## 1. Quy Chuẩn Xuất Bản File APK (APK Build & Output Rules)
- **Thư mục lưu trữ APK duy nhất của dự án:**  
  `C:\Users\j7x6\AndroidStudioProjects\RecompressBypass\release\`
- **Quy tắc đặt tên file APK chuẩn:**
  - Bản tối ưu Release (R8 / ProGuard): `RecompressBypass-v<versionName>-release.apk`
  - Bản Debug kiểm thử: `RecompressBypass-v<versionName>-debug.apk`
- **Tự động hóa hoàn toàn trong Gradle (`app/build.gradle.kts`):**
  - Task `copyApksToRelease` đã được cấu hình tự động chạy sau cả `assembleRelease` và `assembleDebug`.
  - Bất kể lệnh build nào (`gradlew assembleRelease`, `gradlew assembleDebug` hoặc Android Studio build), file APK luôn được tự động đồng bộ vào thư mục `release/`.
- **Nguyên tắc tổ chức file:**
  - **TUYỆT ĐỐI KHÔNG** lưu hoặc để file `.apk` vương vãi tại thư mục gốc của dự án (`/`) hoặc bất kỳ thư mục tạm nào khác.
  - File `.gitignore` đã chặn toàn bộ `/*.apk` ở thư mục gốc để tránh rác repository.

---

## 2. Lệnh Build Chuẩn
- **Biên dịch Release:**  
  `.\gradlew.bat assembleRelease` ➔ Tự động cập nhật vào `release/RecompressBypass-v<versionName>-release.apk`
- **Biên dịch Debug:**  
  `.\gradlew.bat assembleDebug` ➔ Tự động cập nhật vào `release/RecompressBypass-v<versionName>-debug.apk`

---

## 3. Kiến Trúc & Lưu Trữ Nội Bộ (Internal Storage Guidelines)
- **Android WebView (TikTok Studio):**  
  - Luôn tự động giải phóng WebView, xóa cache, xóa `WebStorage` (IndexedDB / Blob Storage) khi người dùng thoát WebView để tránh phình dữ liệu lên hàng trăm MB.
  - Luôn bảo tồn phiên đăng nhập bằng cách `CookieManager.getInstance().flush()`.
- **Thumbnail video:**  
  - Luôn giới hạn kích thước thumbnail tối đa 320px (~15KB - 25KB) và tự động dọn dẹp các thumbnail mồ côi khi lưu lịch sử.
