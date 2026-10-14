package com.ticketrush.catalog;

import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;

public class SaleNotOpenException extends BusinessException {

    public SaleNotOpenException() {
        super(ErrorCode.SALE_NOT_OPEN, "Sự kiện chưa mở bán hoặc đã đóng bán");
    }
}
