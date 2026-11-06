# GKC APP — Tiến trình dự án

## Trạng thái hiện tại
- Ứng dụng Android: GKC APP, repo `gkc-3667/terminal`, nhánh `main`.
- Màn PC APP đã chuyển sang giao diện trình duyệt kiểu Chrome nhúng Android WebView.
- Có thanh địa chỉ, tìm kiếm Google, quay lại/tiến tới/tải lại, thêm tab, chọn tab và đóng tab.
- Dùng một WebView dùng chung để giảm mức tiêu thụ RAM; khi đổi tab, trang được tải lại để tiết kiệm bộ nhớ.
- Giới hạn tối đa 5 tab.
- Lưu danh sách tab và tab đang chọn bằng SharedPreferences; khôi phục khi mở lại ứng dụng.
- Tạm dừng WebView và bộ định thời khi ứng dụng xuống nền; tiếp tục khi quay lại.
- Terminal đã được giới hạn lịch sử lệnh và dung lượng đầu ra ở các bản cập nhật trước.

## Mục tiêu tối ưu tiếp theo
1. Kiểm tra APK trên Chromebook cấu hình thấp, theo dõi độ trễ khi mở trang và chuyển tab.
2. Nếu còn giật, giảm hiệu ứng giao diện và các tác vụ nền không cần thiết.
3. Kiểm tra vòng đời WebView, xử lý lỗi tải trang và khôi phục tab sau khi Android thu hồi tiến trình.
4. Chỉ phát hành APK sau khi GitHub Actions build thành công.

## Ghi chú quan trọng
- Đây là trình duyệt dựa trên Android System WebView/Chromium, không phải ứng dụng Google Chrome cài sẵn.
- Một WebView dùng chung và tải lại trang khi đổi tab là lựa chọn tiết kiệm RAM; trạng thái trang đang cuộn hoặc biểu mẫu chưa gửi có thể không được giữ giữa các tab.
