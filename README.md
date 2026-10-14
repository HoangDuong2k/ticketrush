# TicketRush

Hệ thống bán vé sự kiện: M1 (nền tảng), M2 (đặt vé đúng), M3 (vé và soát vé) theo
[`lo-trinh-backend-java.md`](../lo-trinh-backend-java.md) và [`ticketrush-m2-m3-huong-dan.md`](../ticketrush-m2-m3-huong-dan.md).

- **app**: ứng dụng TicketRush (Spring Boot 4.1, Java 25, PostgreSQL 17, Kafka 4, Flyway)
- **mock-payment-gateway**: MockPay, cổng thanh toán giả lập có chế độ "chaos" (cổng 9090)

Mới học Spring? Đọc [`docs/huong-dan-hoc-code.md`](docs/huong-dan-hoc-code.md): giải thích code và chức năng từ cơ bản.

## Cần cài

| Công cụ | Ghi chú |
|---|---|
| JDK 25 | Trong STS: Window → Preferences → Java → Installed JREs, chọn JDK 25 |
| Docker + Docker Compose | Chạy Postgres/Kafka/Mailpit và Testcontainers (Ubuntu: `sudo apt install docker.io docker-compose-v2`, rồi `sudo usermod -aG docker $USER` và đăng nhập lại) |
| Spring Tool Suite | Hoặc IDE bất kỳ hỗ trợ Maven |
| curl, jq, uuidgen, openssl | Cho `scripts/demo.sh` và `scripts/init-env.sh` |

Maven không cần cài: dùng `./mvnw`. File `.mvn/maven.config` tắt việc Maven hỏi `/.meta/prefixes.txt` ở các
repository khai báo trong POM phụ thuộc (trên máy này `repository.apache.org` bị timeout, mỗi lần build chậm thêm khoảng 30 giây).

## Chạy lần đầu

```bash
docker compose up -d          # Postgres :5432, Kafka :9092, Mailpit :1025/:8025
scripts/init-env.sh           # tạo .env với secret ngẫu nhiên (API key MockPay, secret webhook, JWT, khoá QR)

# Terminal 1: MockPay
./mvnw -pl mock-payment-gateway spring-boot:run

# Terminal 2: TicketRush với dữ liệu mẫu (profile dev)
./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 3: kịch bản demo E1
scripts/demo.sh
```

Xem email vé ở http://localhost:8025. Cả hai app tự đọc file `.env` ở gốc repo, không cần đặt biến môi trường trong IDE.

### Mở trong Spring Tool Suite

1. File → Import → Maven → Existing Maven Projects → chọn thư mục `ticketrush` → giữ cả 3 `pom.xml`.
2. MockPay: chuột phải `MockPayApplication` → Run As → Spring Boot App.
3. TicketRush: chuột phải `TicketRushApplication` → Run As → Run Configurations… → Spring Boot App → **Profile: `dev`** → Run.

### Tài khoản mẫu (profile dev, mật khẩu `Passw0rd!`)

| Email | Vai trò |
|---|---|
| `admin@test.local` | ADMIN |
| `organizer1@test.local` | ORGANIZER (ban tổ chức "Sky Music Entertainment") |
| `staff1@test.local` | STAFF, được phân công soát vé sự kiện mẫu |
| `buyer1@test.local` … `buyer3@test.local` | CUSTOMER |

Sự kiện mẫu "Sơn Tùng M-TP Live Concert 2026" đang mở bán: 60 ghế VIP (1.500.000đ) và 1.000 vé GA (800.000đ).
Log khi khởi động in ra `EVENT_ID`, `GA_TYPE_ID`, `VIP_TYPE_ID`.

## Test

```bash
./mvnw verify                                             # toàn bộ: unit test + integration test (*IT) + MockPay
./mvnw -pl app test -Dtest=SeatClaimConcurrencyIT         # một class
./mvnw -pl app verify -Dit.test=CheckinIT                 # một integration test qua failsafe
```

Integration test chạy Postgres và Kafka thật bằng Testcontainers, nên cần Docker. Lần đầu tải image mất vài phút.
Lớp cha `IntegrationTest` dọn dữ liệu bằng `TRUNCATE` trước mỗi test (không dùng `@Transactional`, vì sẽ làm test
đồng thời vô nghĩa). Đồng hồ là `MutableClock`, cổng thanh toán là `FakeGateway` (HTTP server của JDK), mail là
`FakeMailSender`.

Dùng Podman thay Docker cũng được: chạy `podman system service --time=0 unix:///tmp/podman.sock &` rồi đặt
`DOCKER_HOST=unix:///tmp/podman.sock` và `TESTCONTAINERS_RYUK_DISABLED=true` trước khi chạy `./mvnw verify`.

## Tiêu chí của từng mốc

| Tiêu chí | Test |
|---|---|
| M1: BTC A sửa sự kiện của BTC B → 403 | `OrganizerCatalogApiIT.organizer_cannot_edit_another_organizers_event` |
| M1: gõ "son tung" ra "Sơn Tùng" | `OrganizerCatalogApiIT.searching_without_diacritics_finds_vietnamese_titles` |
| M2: 200 request cùng lúc giành 1 ghế → đúng 1 thành công | `SeatClaimConcurrencyIT` |
| M2: 10.000 request giành 1.000 vé GA → bán đúng 1.000 | `GaInventoryConcurrencyIT`, `OrderPlacementIT.ten_thousand_orders_for_one_thousand_ga_tickets` |
| M2: gửi song song nhiều tab không vượt 4 vé | `PurchaseQuotaConcurrencyIT` |
| M2: webhook gửi 3 lần → chỉ 1 thanh toán | `PaymentWebhookIT` (lần lượt và song song) |
| M3: 1 QR quét cùng lúc ở 2 cổng → 1 thành công | `CheckinIT.same_qr_scanned_at_two_gates_at_once_succeeds_exactly_once` |
| M3: sửa 1 ký tự QR (kể cả ký tự cuối) → bị từ chối | `QrTokenCodecTest` |
| M3: vé đã chuyển nhượng → QR cũ bị từ chối | `TicketTransferIT` |
| M3: tắt app giữa chừng, khởi động lại → vẫn có vé | `CrashRecoveryIT` |
| M3: message trùng không tạo thêm vé, không gửi thêm email | `TicketIssuanceIT`, `TicketEmailIT` |

Các test quan trọng nằm trong package `acceptance`: không sửa để cho xanh, sửa code.

### Thử tay

- **Chaos MockPay** (Bước 12.2): `curl -X PUT localhost:9090/admin/chaos -H 'Content-Type: application/json' -d '{"duplicateDeliveries":3}'`
  rồi chạy `scripts/demo.sh` → đơn PAID một lần, `payment_webhook_logs` có 3 dòng. Các chế độ khác: `webhookDelayMs`,
  `dropWebhook`, `webhookBeforeRedirect`, `failFirstDeliveries`, `refundFailureRate`. `DELETE /admin/chaos` để về mặc định.
  Chaos áp dụng cho phiên thanh toán tạo **sau** lệnh PUT.
- **App sập** (Bước 20.2): chạy app với `--ticketrush.outbox.relay.enabled=false`, mua và thanh toán, `kill -9`, chạy lại
  bình thường → vài giây sau có vé và email.
- **Kafka sập**: `docker compose stop kafka`, thanh toán vài đơn (đơn đứng ở PAID), `docker compose start kafka` → mọi đơn có vé.
- **Bất biến dữ liệu**: `docker compose exec -T postgres psql -U ticketrush < scripts/invariants.sql`.
- **Xem message Kafka**: `docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ticketrush.order-events --from-beginning --property print.headers=true`

## Cấu trúc

```
ticketrush/
├── app/src/main/java/com/ticketrush/
│   ├── common/        cấu hình, Clock, lỗi (ProblemDetail), bảo mật JWT, event Kafka
│   ├── identity/      đăng ký, đăng nhập, tài khoản, ban tổ chức
│   ├── catalog/       địa điểm, sự kiện, loại vé, sơ đồ ghế, tìm kiếm không dấu
│   ├── inventory/     giữ ghế, tồn kho GA, hạn mức mỗi user, sơ đồ ghế công khai
│   ├── ordering/      đơn hàng, idempotency, hết hạn, huỷ
│   ├── payment/       tạo thanh toán, webhook, hoàn tiền
│   ├── outbox/        ghi và phát outbox event, cấu hình Kafka
│   ├── ticketing/     phát vé, QR, check-in, chuyển nhượng
│   ├── notification/  email vé
│   └── dev/           dữ liệu mẫu và endpoint chỉ có ở profile dev
├── app/src/main/resources/db/migration/   Flyway V1–V2 (M1), V100–V102 (M2), V110–V112 (M3)
├── mock-payment-gateway/                  MockPay (spec: docs/mockpay-spec.md)
├── docs/                                  api.md, schema.md, mockpay-spec.md, adr/
├── scripts/                               init-env.sh, demo.sh, invariants.sql
├── docker-compose.yml
└── CLAUDE.md                              quy ước cho agent
```

## Job chạy nền (khi `ticketrush.jobs.enabled=true`, mặc định bật; test tắt)

| Job | Chu kỳ | Việc |
|---|---|---|
| `OrderExpiryJob` | 5 giây | Dọn đơn quá hạn giữ chỗ + 1 phút, trả ghế, vé GA, hạn mức |
| `RefundJob` | 10 giây | Hoàn tiền payment `REFUND_PENDING`, thử lại theo lịch lùi dần |
| `PaidOrderSweeper` | 1 phút | Lưới an toàn: đơn PAID quá 5 phút chưa có vé thì phát vé trực tiếp |
| `OutboxCleanup` | 1 giờ | Xoá outbox đã gửi quá 7 ngày |
| `OutboxRelay` | 0,5 giây | Đẩy outbox lên Kafka (bật/tắt riêng bằng `ticketrush.outbox.relay.enabled`) |

## Việc để các mốc sau

- Job đối soát với MockPay khi webhook bị mất hẳn (`dropWebhook`): M6.
- Email vé vào DLT (mail server sập quá 10 phút) phải gửi lại tay; chưa có job quét như `PaidOrderSweeper`.
- Rate limit (đăng nhập, chuyển nhượng), phòng chờ, cache: M5.
- Bộ đếm GA một dòng là điểm nghẽn đã biết (ADR-003): đo bằng k6 ở M5.
