import os
import flet as ft
from optimizer import optimize_mp4


def main(page: ft.Page):
    page.title = "MP4 Quality Bypass"
    page.theme_mode = ft.ThemeMode.DARK
    page.padding = 20
    page.vertical_alignment = ft.MainAxisAlignment.CENTER
    page.horizontal_alignment = ft.CrossAxisAlignment.CENTER

    fps_input = ft.TextField(
        label="Virtual FPS Target",
        value="400",
        keyboard_type=ft.KeyboardType.NUMBER,
        width=250,
    )
    clean_meta_sw = ft.Switch(label="Xóa sạch Metadata (udta)", value=True)
    status_text = ft.Text(
        "Chọn 1 hoặc nhiều video .mp4 để bypass",
        size=14,
        color=ft.colors.GREY_400,
        text_align=ft.TextAlign.CENTER,
    )
    progress_ring = ft.ProgressRing(visible=False)

    def on_files_picked(e: ft.FilePickerResultEvent):
        if not e.files:
            status_text.value = "Đã hủy chọn file."
            page.update()
            return

        progress_ring.visible = True
        status_text.value = f"Đang xử lý {len(e.files)} file..."
        status_text.color = ft.colors.AMBER
        page.update()

        target_fps = int(fps_input.value) if fps_input.value.isdigit() else 400
        success_count = 0
        out_dir = "/storage/emulated/0/Download"
        if not os.path.exists(out_dir):
            out_dir = None

        logs = []
        for file_obj in e.files:
            in_path = file_obj.path
            if not in_path:
                continue
            try:
                base_name = os.path.splitext(os.path.basename(in_path))[0]
                if out_dir and os.access(out_dir, os.W_OK):
                    out_path = os.path.join(out_dir, f"{base_name}_optimized.mp4")
                else:
                    out_path = None  # Lưu cùng thư mục với file gốc

                res_path = optimize_mp4(
                    input_path=in_path,
                    output_path=out_path,
                    target_fps=target_fps,
                    wipe_metadata=clean_meta_sw.value,
                    verbose=False,
                )
                success_count += 1
                logs.append(f"✔ {os.path.basename(res_path)}")
            except Exception as ex:
                logs.append(f"✘ {file_obj.name}: {ex}")

        progress_ring.visible = False
        status_text.value = (
            f"Hoàn tất ({success_count}/{len(e.files)} file):\n" + "\n".join(logs)
        )
        status_text.color = (
            ft.colors.GREEN_400 if success_count > 0 else ft.colors.RED_400
        )
        page.update()

    file_picker = ft.FilePicker(on_result=on_files_picked)
    page.overlay.append(file_picker)

    pick_btn = ft.ElevatedButton(
        text="Chọn Video (.mp4)",
        icon=ft.icons.VIDEO_FILE,
        width=250,
        height=50,
        on_click=lambda _: file_picker.pick_files(
            allow_multiple=True,
            allowed_extensions=["mp4", "MP4"],
        ),
    )

    page.add(
        ft.Icon(ft.icons.ROCKET_LAUNCH, size=64, color=ft.colors.BLUE_400),
        ft.Text("MP4 Quality Bypass", size=24, weight=ft.FontWeight.BOLD),
        ft.Text("400 FPS Container Injection", size=12, color=ft.colors.GREY_500),
        ft.Divider(height=20, color=ft.colors.TRANSPARENT),
        fps_input,
        clean_meta_sw,
        ft.Divider(height=20, color=ft.colors.TRANSPARENT),
        pick_btn,
        ft.Divider(height=15, color=ft.colors.TRANSPARENT),
        progress_ring,
        status_text,
    )


if __name__ == "__main__":
    ft.app(target=main)