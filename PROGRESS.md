# GKC APP — Tiến trình dự án

## Trạng thái hiện tại
- Ứng dụng Android: GKC APP, repo `gkc-3667/terminal`, nhánh `main`.
- Màn PC APP đã chuyển sang giao diện trình duyệt kiểu Chrome nhúng Android WebView.
- Có thanh địa chỉ, tìm kiếm Google, quay lại/tiến tới/tải lại, thêm tab, chọn tab và đóng tab.
- Dùng một WebView dùng chung để giảm mức tiêu thụ RAM; khi đổi tab, trang được tải lại để tiết kiệm bộ nhớ.
- Giới hạn tối đa 5 tab.
- Lưu danh sách tab và tab đang chọn bằng SharedPreferences; khôi phục khi mở lại ứng dụng.
- Tạm dừng WebView và bộ định thời khi ứng dụng xuống nền; tiếp tục khi quay lại.
- Terminal giới hạn lịch sử còn 60 lệnh và đầu ra còn 6.000 ký tự để giảm tải giao diện.
- Lưu và khôi phục thư mục làm việc cùng lịch sử lệnh; kiểm tra thư mục còn tồn tại trước khi khôi phục.
- Giảm đổ bóng/elevation trên thẻ giao diện để hạn chế vẽ lại trên thiết bị yếu.

## Tối ưu Discord PC (2026-11-07)
- Giữ giao diện desktop Discord và cập nhật chuỗi Chrome trong User-Agent theo phiên bản Chromium thực tế của Android System WebView, thay vì cố định Chrome/120.
- Mục tiêu là tránh khai báo trình duyệt cũ hơn WebView thực tế; thay đổi này không thể tự loại bỏ độ trễ mạng hoặc giới hạn CPU/RAM.
- Cần build APK và đo thực tế trên Chromebook cấu hình thấp trước khi kết luận mức cải thiện.

## Mục tiêu tối ưu tiếp theo
1. Kiểm tra APK trên Chromebook cấu hình thấp, theo dõi độ trễ khi mở trang và chuyển tab.
2. Nếu còn giật, giảm hiệu ứng giao diện và các tác vụ nền không cần thiết.
3. Kiểm tra vòng đời WebView, xử lý lỗi tải trang và khôi phục tab sau khi Android thu hồi tiến trình.
4. Chỉ phát hành APK sau khi GitHub Actions build thành công.

## Ghi chú quan trọng
- Đây là trình duyệt dựa trên Android System WebView/Chromium, không phải ứng dụng Google Chrome cài sẵn.
- Một WebView dùng chung và tải lại trang khi đổi tab là lựa chọn tiết kiệm RAM; trạng thái trang đang cuộn hoặc biểu mẫu chưa gửi có thể không được giữ giữa các tab.
