package com.ticketrush.inventory;

import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;

/** 400: ghế không tồn tại, thuộc sự kiện khác, hoặc danh sách ghế bị trùng. */
public class InvalidSeatException extends BusinessException {

    public InvalidSeatException(String detail) {
        super(ErrorCode.VALIDATION_FAILED, detail);
    }
}
