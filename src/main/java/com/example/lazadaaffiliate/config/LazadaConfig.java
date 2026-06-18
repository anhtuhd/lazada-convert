package com.example.lazadaaffiliate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holds Lazada Open Platform credentials loaded from application.properties
 * or environment variables.
 *
 * @param appKey          App Key from Lazada App Console
 * @param appSecret       App Secret used for HMAC-SHA256 signing
 * @param accessToken     OAuth access token for the affiliate account
 * @param gatewayUrl      Lazada REST gateway URL (region-specific)
 * @param affiliateApiPath API path for generating affiliate short link
 */
@ConfigurationProperties(prefix = "lazada")
public record LazadaConfig(
        String appKey,
        String appSecret,
        String accessToken,
        String gatewayUrl,
        String affiliateApiPath
) {}
