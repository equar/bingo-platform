package com.bakaroo.bingo.backend.feature.user.entity;

public enum AccountStatus {
    ACTIVE,
    PENDING,
    /** Temporarily blocked; an admin may reactivate the account. */
    SUSPENDED,
    /** Permanently deactivated; the account cannot be reactivated. */
    DISABLED
}
