package com.bakaroo.bingo.backend.common.idempotency;

public record IdempotentResult<T>(int statusCode, T body) {
}