package com.bankdash.account.domain;

/** A PIN broke one of the {@link PinRule}s. */
public class WeakPinException extends RuntimeException {

    private final PinRule rule;

    public WeakPinException(PinRule rule) {
        super(rule.message());
        this.rule = rule;
    }

    public PinRule rule() {
        return rule;
    }
}
