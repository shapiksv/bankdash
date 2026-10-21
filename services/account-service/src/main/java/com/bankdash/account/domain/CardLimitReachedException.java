package com.bankdash.account.domain;

public class CardLimitReachedException extends RuntimeException {

    public CardLimitReachedException(int limit) {
        super("At most " + limit + " cards that are not blocked are allowed");
    }
}
