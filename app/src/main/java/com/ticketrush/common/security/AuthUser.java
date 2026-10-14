package com.ticketrush.common.security;

import java.util.UUID;

/** Người đang gọi API, lấy từ JWT. Khai báo làm tham số controller để Spring tự điền. */
public record AuthUser(UUID id, Role role) {}
