# LichAI-TV — TV #01

Bản Android TV thử nghiệm đầu tiên của Lịch AI.

## TV #01
- Native landscape 16:9, không bê nguyên UI điện thoại lên TV.
- Trái: Lịch ngày, âm lịch, Can Chi năm, giờ hoàng đạo, hướng tốt.
- Phải: Lịch tháng, mỗi ô có ngày dương + ngày âm.
- D-pad để chọn ngày; OK mở chi tiết ngày.
- Sau 60 giây không thao tác tự quay về hôm nay.
- Best-effort Home Channel/Recommendation hiển thị nhanh ngày dương + ngày âm (tùy launcher Android TV/Google TV có cho hiển thị hay không).
- Nút “Mở chi tiết đầy đủ” chuyển sang lich.ai.vn ở TV #01; TV #02 có thể nối sâu vào chi tiết ngày/API riêng.

## Build
Mở bằng Android Studio (JDK 17), Sync Gradle, sau đó Build APK.

Package riêng: `vn.ai.lich.tv` — không xung đột app Android mobile `vn.ai.lich`.
