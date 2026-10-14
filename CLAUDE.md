# TicketRush

Hệ thống bán vé sự kiện. Spring Boot 4, Java 25, PostgreSQL, Kafka. Maven multi-module: `app`, `mock-payment-gateway`.
Hướng dẫn chi tiết từng bước: `../ticketrush-m2-m3-huong-dan.md` (M2–M3) và `../lo-trinh-backend-java.md` (lộ trình, M1).

## Lệnh
- Build và chạy toàn bộ test: `./mvnw verify` (cần Docker cho Testcontainers)
- Chỉ test một class: `./mvnw -pl app test -Dtest=SeatClaimConcurrencyIT`
- Hạ tầng local: `docker compose up -d`; secret local: `scripts/init-env.sh` (tạo `.env`)
- Chạy app với dữ liệu mẫu: `./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev`
- Chạy MockPay: `./mvnw -pl mock-payment-gateway spring-boot:run` (cổng 9090)
- Demo end-to-end: `scripts/demo.sh`

## Quy ước bắt buộc
- Thời gian: inject `Clock`, truyền `Instant` vào SQL (qua `Times.utc`). Không dùng `now()` của DB trong logic.
- Tiền: `BigDecimal`, so sánh bằng `compareTo`.
- Đổi trạng thái seat/ticket/quota/GA: SQL có điều kiện, kiểm tra số dòng bị ảnh hưởng.
  Order/payment: khoá bằng `FOR UPDATE` trước khi đổi. KHÔNG dùng `findById` → `if` → `save`.
- Thứ tự khoá: order → payment → purchase_quotas → ticket_types → seats (theo id tăng dần) → tickets.
  Mọi câu chạm nhiều ghế phải khoá `ORDER BY id FOR UPDATE` trước (`SeatHoldService.lockHeldSeats`), không UPDATE trần theo `order_id`.
- Khoá payment sau khi đã khoá order: lấy `order_id` bằng projection (`findOrderIdById`), không load entity trước khi khoá.
- Không gọi HTTP/gửi email/gửi Kafka trong transaction. Event đi qua `OutboxWriter` (MANDATORY).
- Job xử lý theo lô: mỗi phần tử một transaction và try/catch riêng; một phần tử lỗi không chặn cả lô.
- Consumer Kafka phải idempotent. Payload thiếu trường → `InvalidEventPayloadException` (vào DLT ngay).
- Test: đơn vừa PAID có thể đã FULFILLED (relay + consumer chạy nền); assert `isIn("PAID", "FULFILLED")`, hoặc tắt relay như `CrashRecoveryIT`.
- Exception nghiệp vụ kế thừa `BusinessException` (RuntimeException), có `ErrorCode` (mã + HTTP status). Lỗi API là ProblemDetail có trường `code`.
- Migration Flyway: không sửa file đã merge, chỉ thêm file mới. Đặt tên cho mọi constraint.
- Không log QR token, secret, header Authorization.

## Cấm
- Không sửa hoặc xoá test trong `src/test/**/acceptance/` nếu chưa hỏi. Test đỏ thì sửa code, không sửa test.
- Không thêm dependency mới nếu chưa hỏi.
- Không đổi thiết kế bảng trong `docs/schema.md` nếu chưa hỏi.

## Cấu trúc
Package theo nghiệp vụ dưới `com.ticketrush`: common, identity, catalog, inventory, ordering, payment, outbox, ticketing, notification, dev.
Module không gọi repository của module khác; gọi qua service public của module đó (`CatalogQueries`, `UserDirectory`, `OrderService`, `TicketQueries`...).
Test dùng chung hạ tầng ở `app/src/test/java/com/ticketrush/support` (IntegrationTest, TestData, MutableClock, Concurrency, FakeGateway, FakeMailSender).
