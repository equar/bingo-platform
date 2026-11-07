package com.bakaroo.bingo.backend.common.exception;

import org.springframework.http.HttpStatus;

public final class AccountSuspendedException extends ApiException {
    public static final String CODE = "ACCOUNT_SUSPENDED";
    public static final String MESSAGE = "Your account has been suspended, please reach out to Sunset Bingo support team at 0956047345";

    public AccountSuspendedException() {
        super(HttpStatus.FORBIDDEN, CODE, MESSAGE);
    }
}
