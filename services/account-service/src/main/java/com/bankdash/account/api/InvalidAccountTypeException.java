package com.bankdash.account.api;

/** {@code POST /cards} without an accountType or with one that is not CHECKING or SAVINGS. */
class InvalidAccountTypeException extends RuntimeException {

    static final String DETAIL = "accountType must be CHECKING or SAVINGS";

    InvalidAccountTypeException() {
        super(DETAIL);
    }
}
