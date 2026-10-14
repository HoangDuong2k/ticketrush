package com.ticketrush.payment;

import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;

/** 401: webhook thiếu header, sai chữ ký hoặc timestamp lệch quá xa. */
public class InvalidWebhookSignatureException extends BusinessException {

    public InvalidWebhookSignatureException(String reason) {
        super(ErrorCode.INVALID_WEBHOOK_SIGNATURE, "Webhook không hợp lệ: " + reason);
    }
}
