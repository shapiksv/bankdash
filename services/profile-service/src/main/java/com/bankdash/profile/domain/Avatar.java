package com.bankdash.profile.domain;

import java.time.Instant;
import java.util.UUID;

public record Avatar(UUID userId, String contentType, byte[] data, Instant updatedAt) {}
