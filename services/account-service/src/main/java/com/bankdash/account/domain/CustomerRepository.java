package com.bankdash.account.domain;

public interface CustomerRepository {

    /** @return false if the customer already existed, which leaves it unchanged */
    boolean insertIfAbsent(Customer customer);
}
