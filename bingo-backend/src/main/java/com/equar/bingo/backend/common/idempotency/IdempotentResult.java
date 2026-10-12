package com.equar.bingo.backend.common.idempotency;

public record IdempotentResult<T>(int statusCode, T body) {
}