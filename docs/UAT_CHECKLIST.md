# Production UAT Checklist — Badminton Doubles

Checklist này dành cho Host không cần kiến thức kỹ thuật và xác minh đúng một
phiên `BADMINTON` + `DOUBLES` trên production.

## Thông tin lần kiểm thử

| Mục | Giá trị |
| --- | --- |
| Ngày kiểm thử | ______________________________ |
| Host kiểm thử | ______________________________ |
| Frontend production URL | ______________________________ |
| Session ID sau khi tạo | ______________________________ |
| Thiết bị/trình duyệt | ______________________________ |

Đánh dấu đúng một ô `PASS` hoặc `FAIL` ở mỗi bước. Nếu `FAIL`, ghi ngắn gọn
điều đã thấy trong cột Notes và dừng nếu lỗi ngăn luồng tiếp tục.

## 1. Pre-UAT

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 1 | Mở frontend production URL bằng trình duyệt. | Trang “Vận hành phiên chơi” hiển thị, không có trang trắng hoặc lỗi Vercel. | [ ] | [ ] | __________ |
| 2 | Mở `https://sports-session-platform-api.onrender.com/api/health`. Chờ tối đa khoảng 2 phút nếu Render đang cold start. | HTTP 200 và nội dung `{"status":"UP"}`. | [ ] | [ ] | __________ |
| 3 | Tại trang chủ, kiểm tra có thể mở “Tạo phiên mới” và “Quản lý người chơi”. | Cả hai đường dẫn mở được từ UI, không cần Swagger. | [ ] | [ ] | __________ |
| 4 | Kiểm tra dữ liệu test hoặc chuẩn bị tạo mới ít nhất 1 địa điểm, 2 sân và 8 người chơi. | Có đủ dữ liệu để vận hành ít nhất hai trận cầu lông đôi. | [ ] | [ ] | __________ |

## 2. Chuẩn bị phiên

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 5 | Từ Home, mở một Session đang có để kiểm tra chức năng Resume, sau đó quay lại Home. | Session mở đúng trang điều hành và trạng thái hiện tại được hiển thị. | [ ] | [ ] | __________ |
| 6 | Chọn “Tạo phiên mới”. Chọn địa điểm có sẵn hoặc tạo địa điểm mới. | Địa điểm được chọn và tên/địa chỉ hiển thị trong phần xem lại. | [ ] | [ ] | __________ |
| 7 | Chọn ít nhất 2 sân có sẵn hoặc tạo sân mới tại địa điểm đó. | Các sân cầu lông được đánh dấu đã chọn. | [ ] | [ ] | __________ |
| 8 | Tìm người chơi bằng tên hoặc mã Player; tạo thêm nếu cần. Chọn ít nhất 8 người. | Mỗi người được hiển thị bằng mã/tên và danh sách đã chọn đủ cho hai trận đôi. | [ ] | [ ] | __________ |
| 9 | Nhập tiêu đề, ngày, giờ bắt đầu/kết thúc theo giờ Việt Nam và bấm “Tạo và bắt đầu phiên”. | UI lần lượt tạo Session, thêm sân/người chơi, bắt đầu Session và mở phòng điều hành. | [ ] | [ ] | __________ |
| 10 | Kiểm tra phần đầu trang và Bảng sân/Người chơi. | Session là “Đang diễn ra”; sân là “Sẵn sàng”; người chơi ban đầu là “Đã đăng ký”. | [ ] | [ ] | __________ |

## 3. Điểm danh và chuẩn bị người chơi

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 11 | Mở “Bàn điểm danh”; tìm người bằng mã `#participantCode` hoặc tên. | Danh sách và số lượng điểm danh hiển thị đúng; tìm kiếm lọc đúng người. | [ ] | [ ] | __________ |
| 12 | Điểm danh ít nhất 8 người chơi. | Từng người chuyển từ “Đã đăng ký” sang “Đang chờ”; số lượng tổng quan cập nhật từ backend. | [ ] | [ ] | __________ |
| 13 | Quay lại phòng điều hành. Nếu Host muốn giữ đôi, tạo một Buddy Pair. | Hai người hiển thị “Đánh cùng”; đây là bước tùy chọn. | [ ] | [ ] | __________ |

## 4. Đề xuất và hàng chờ

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 14 | Tại một sân, bấm “Tạo đề xuất ghép trận”. | Preview hiển thị đủ Đội A/Đội B; chưa có người hoặc sân nào chuyển sang “Đang chơi”. | [ ] | [ ] | __________ |
| 15 | Bấm “Đưa vào hàng chờ”. | Một MatchPlan xuất hiện trong “Hàng chờ trận”; người/sân vẫn chưa bị giữ để chơi. | [ ] | [ ] | __________ |
| 16 | Ở sân còn lại, bấm “Xếp vào hàng chờ”, tự chọn 4 người và phân đội. | MatchPlan thủ công được tạo; Buddy đã chọn cùng xuất hiện cùng đội nếu cả hai được chọn. | [ ] | [ ] | __________ |
| 17 | Kiểm tra hai hàng chờ. Nếu phù hợp, thử đổi sân hoặc đổi thứ tự một MatchPlan. | MatchPlan chuyển đúng sân/vị trí; danh sách mới nhất được tải lại. | [ ] | [ ] | __________ |

## 5. Vận hành trận

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 18 | Bấm “Bắt đầu trận” cho MatchPlan đầu hàng tại sân sẵn sàng. | Sân và đúng 4 người chuyển sang “Đang chơi”; MatchPlan không còn ở trạng thái chờ. | [ ] | [ ] | __________ |
| 19 | Chọn đội thắng, nhập cả hai tỷ số hoặc để trống cả hai, rồi bấm “Kết thúc trận”. | Trận kết thúc; sân trở lại “Sẵn sàng”; 4 người trở lại “Đang chờ”. | [ ] | [ ] | __________ |
| 20 | Bắt đầu MatchPlan tiếp theo tại sân phù hợp. | Trận kế tiếp chuyển sang “Đang chơi” với đúng sân và đúng 4 người. | [ ] | [ ] | __________ |
| 21 | Kết thúc trận thứ hai để chuẩn bị kết thúc Session. | Không còn Match “Đang chơi”; sân và người chơi được giải phóng. | [ ] | [ ] | __________ |

## 6. Player Session View

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 22 | Tại một người chơi, bấm “Sao chép liên kết người chơi” và mở liên kết trên tab/thiết bị khác. | Trang cá nhân mở bằng opaque token và hiển thị đúng mã, tên, Session và trạng thái. | [ ] | [ ] | __________ |
| 23 | Mở QR người chơi và quét bằng điện thoại. | QR mở đúng cùng Player Session View. QR không tự điểm danh hoặc tạo mutation. | [ ] | [ ] | __________ |
| 24 | Quan sát Player Session View khi người đó `WAITING`, `QUEUED` hoặc `PLAYING`; dùng “Làm mới” nếu cần. | Trạng thái, sân, đồng đội/đối thủ được phản ánh; trang chỉ xem và không có nút tự đổi trạng thái. | [ ] | [ ] | __________ |

## 7. Kết thúc phiên

| # | Host action | Expected visible result | PASS | FAIL | Notes |
| --- | --- | --- | :---: | :---: | --- |
| 25 | Xác nhận không còn Match đang chơi, sau đó bấm “Kết thúc phiên” và xác nhận. | Session chuyển thành “Đã kết thúc”; các thao tác vận hành thông thường không còn hiển thị. | [ ] | [ ] | __________ |
| 26 | Về Home và mở lại Session vừa kết thúc. | Session xuất hiện trong danh sách và mở ở trạng thái read-only phù hợp. | [ ] | [ ] | __________ |

## 8. Tổng kết vấn đề

Chỉ ghi vấn đề quan sát được trong lần UAT này.

| Mức | Định nghĩa | Có vấn đề? | Mô tả ngắn |
| --- | --- | :---: | --- |
| P0 | Không thể hoàn thành Session/UAT. | [ ] | ______________________________ |
| P1 | Hoàn thành được nhưng UX bị hỏng hoặc gây nhầm lẫn nghiêm trọng. | [ ] | ______________________________ |
| P2 | Lỗi nhỏ về UX/chất lượng, không chặn Host. | [ ] | ______________________________ |
| P3 | Ý tưởng/tính năng tương lai, không phải lỗi của luồng hiện tại. | [ ] | ______________________________ |

Kết luận lần UAT: **[ ] PASS  [ ] FAIL**

Ghi chú cuối: ________________________________________________________________
