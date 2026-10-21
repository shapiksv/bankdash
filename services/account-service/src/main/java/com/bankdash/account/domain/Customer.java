package com.bankdash.account.domain;

import java.time.Instant;
import java.util.UUID;

/** A registered user as account-service knows them: the name printed on their cards. */
public record Customer(UUID userId, String fullName, Instant createdAt) {}
