# Thiết kế bảng

Nguồn sự thật là các file Flyway trong `app/src/main/resources/db/migration`. Tài liệu này tóm tắt ý đồ; muốn đổi bảng thì thêm migration mới.

| Migration | Nội dung |
|---|---|
| `V1__m1_identity.sql` | `organizers`, `users` (một vai trò mỗi tài khoản; ORGANIZER gắn `organizer_id`) |
| `V2__m1_catalog.sql` | `venues`, `events` (cửa sổ bán vé, `search_text` đã bỏ dấu, index trigram), `ticket_types`, `seats` |
| `V100__m2_orders.sql` | `events.max_tickets_per_user`, `orders`, `order_items`, `purchase_quotas` |
| `V101__m2_inventory.sql` | Bộ đếm GA trên `ticket_types`, trạng thái giữ/bán của `seats` |
| `V102__m2_payments.sql` | `payments`, `payment_webhook_logs` |
| `V110__m3_orders_fulfilled.sql` | Trạng thái `FULFILLED`, `fulfilled_at` |
| `V111__m3_tickets.sql` | `event_staff`, `tickets`, `ticket_transfers`, `checkin_logs` |
| `V112__m3_outbox_notifications.sql` | `outbox_events`, `notification_deliveries` |

## Ràng buộc là lớp chặn cuối cùng

| Quy tắc | Ràng buộc |
|---|---|
| Email không trùng, lưu chữ thường | `uq_users_email`, `users_email_lowercase` |
| Tài khoản ORGANIZER thuộc một ban tổ chức | `users_organizer_role_check` |
| Đóng bán trước giờ diễn | `events_sale_window_check` |
| Ghế thuộc loại vé của chính sự kiện đó | `fk_seats_ticket_type_same_event` → `uq_ticket_types_id_event` |
| Không trùng vị trí ghế | `uq_seats_position` |
| Một request mua (theo key) chỉ tạo một đơn | `uq_orders_user_idempotency_key` |
| Trạng thái ghế nhất quán với đơn giữ/bán | `seats_state_check` |
| Không âm, không vượt tổng vé GA | `ticket_types_quantity_check` |
| Hạn mức không âm | `purchase_quotas_non_negative` |
| Mỗi giao dịch của cổng chỉ ghi nhận một lần | `uq_payments_gateway_transaction`, `uq_payments_gateway_payment` |
| Phát vé lặp lại không tạo thêm vé | `uq_tickets_order_item_seq` |
| Một ghế tối đa một vé còn hiệu lực | `uq_tickets_active_seat` (partial, loại trừ `VOID`) |
| Email chỉ gửi một lần theo khoá | `uq_notification_dedupe` |

## Bảng nào đổi bằng gì

| Bảng | Cách đổi trạng thái |
|---|---|
| `orders`, `payments` | JPA entity; khoá `SELECT ... FOR UPDATE` rồi gọi method của entity |
| `seats`, `ticket_types` (GA), `purchase_quotas`, `tickets` | SQL có điều kiện qua `NamedParameterJdbcTemplate`, kiểm tra số dòng |
| `users` | JPA entity (không có tranh chấp) |
| `events`, `venues` | SQL (`CatalogStore`) |
| `outbox_events`, `notification_deliveries`, `payment_webhook_logs`, `checkin_logs` | SQL |
