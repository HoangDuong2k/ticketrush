package com.ticketrush.ordering;

import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;

/** 422: cùng Idempotency-Key nhưng nội dung request khác lần trước. */
public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key này đã được dùng cho một request có nội dung khác");
    }
}
