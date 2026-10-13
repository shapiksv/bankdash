package com.bankdash.gateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param allowedOrigins exact origins allowed to call the API with credentials; never {@code *} */
@ConfigurationProperties("bankdash.cors")
public record CorsProperties(List<String> allowedOrigins) {}
