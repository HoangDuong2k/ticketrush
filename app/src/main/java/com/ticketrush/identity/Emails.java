package com.ticketrush.identity;

import java.util.Locale;

public final class Emails {

    /** Email lưu và so sánh ở dạng chữ thường, bỏ khoảng trắng hai đầu. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private Emails() {}
}
