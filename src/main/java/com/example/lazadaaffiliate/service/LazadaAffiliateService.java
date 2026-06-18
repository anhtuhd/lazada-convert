package com.example.lazadaaffiliate.service;

import com.example.lazadaaffiliate.config.LazadaConfig;
import com.example.lazadaaffiliate.model.ConvertRequest;
import com.example.lazadaaffiliate.model.ConvertResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Core service that:
 * 1. Builds Lazada Open Platform system + business params
 * 2. Signs the request with HMAC-SHA256 (per Lazada signing spec)
 * 3. Calls POST /affiliate/generateShortLink
 * 4. Parses and returns the affiliate short link
 */
@Service
public class LazadaAffiliateService {

    private static final Logger log = LoggerFactory.getLogger(LazadaAffiliateService.class);

    private final LazadaConfig config;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public LazadaAffiliateService(LazadaConfig config) {
        this.config = config;
    }


    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public ConvertResponse convertToAffiliateLink(ConvertRequest request) {
        String originalUrl = request.productUrl();
        log.info("Converting product URL: {}", originalUrl);

        try {
            // Resolve short link to get the original product URL
            String resolvedUrl = resolveLazadaShortLink(originalUrl);
            log.info("Resolved product URL: {}", resolvedUrl);

            // 1. Collect all params (system + business)
            Map<String, String> params = buildParams(resolvedUrl, request);

            // 2. Compute signature and add to params
            String sign = computeSign(config.affiliateApiPath(), params, config.appSecret());
            params.put("sign", sign);

            // 3. Build query string and call Lazada API
            String queryString = buildQueryString(params);
            String apiUrl = config.gatewayUrl() + config.affiliateApiPath() + "?" + queryString;
            log.debug("Calling Lazada API: {}", apiUrl);

            String responseBody = callLazadaApi(apiUrl);
            log.debug("Lazada raw response: {}", responseBody);

            // 4. Parse response
            return parseResponse(responseBody, originalUrl);

        } catch (Exception e) {
            log.error("Failed to convert link: {}", e.getMessage(), e);
            return ConvertResponse.fail(originalUrl, "Internal error: " + e.getMessage(), null);
        }
    }

    /**
     * Resolves short links (like s.lazada.vn/s.XXXX or c.lazada.vn) to their original destination.
     * Lazada short links usually return status 200 with HTML containing redirection scripts and meta-refresh tags.
     */
    private String resolveLazadaShortLink(String url) {
        if (!isShortLink(url)) {
            return url;
        }

        log.info("Resolving short link: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                String html = response.body();

                // 1. Try to parse REDIRECTURL = new URL('...')
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("REDIRECTURL\\s*=\\s*new\\s+URL\\('([^']+)'\\)");
                java.util.regex.Matcher m = p.matcher(html);
                if (m.find()) {
                    String destination = m.group(1);
                    log.info("Found destination in REDIRECTURL: {}", destination);
                    return destination;
                }

                // 2. Try to parse url=... in meta-refresh tag
                p = java.util.regex.Pattern.compile("url=([^\"' >]+)");
                m = p.matcher(html);
                if (m.find()) {
                    String destination = m.group(1);
                    log.info("Found destination in meta refresh: {}", destination);
                    return destination;
                }
            } else if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location != null && !location.isBlank()) {
                    log.info("Found destination in HTTP Location header: {}", location);
                    return location;
                }
            }
        } catch (Exception e) {
            log.error("Failed to resolve short link: {}", e.getMessage(), e);
        }

        return url;
    }

    private boolean isShortLink(String url) {
        if (url == null) return false;
        // Check if the URL belongs to a Lazada shortener or mobile share domain/pattern
        return url.contains("lazada.") && (url.contains("/s.") || url.contains("/t/c.") || url.contains("/sl.") || (!url.contains("/products/") && !url.contains("/shop/")));
    }

    // ---------------------------------------------------------------
    // Param Building
    // ---------------------------------------------------------------

    /**
     * Builds a map of all request parameters (system + business).
     * The map uses TreeMap (sorted by key) to simplify signing.
     */
    private Map<String, String> buildParams(String productUrl, ConvertRequest request) {
        Map<String, String> params = new TreeMap<>();

        // System parameters
        params.put("app_key", config.appKey());
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("sign_method", "sha256");
        params.put("access_token", config.accessToken());

        // Business parameters
        params.put("promotion_link", productUrl);

        // Optional tracking sub-IDs
        if (request.subId1() != null && !request.subId1().isBlank()) {
            params.put("sub_id1", request.subId1());
        }
        if (request.subId2() != null && !request.subId2().isBlank()) {
            params.put("sub_id2", request.subId2());
        }
        if (request.subId3() != null && !request.subId3().isBlank()) {
            params.put("sub_id3", request.subId3());
        }

        return params;
    }

    // ---------------------------------------------------------------
    // Signing — Lazada HMAC-SHA256 spec
    // ---------------------------------------------------------------

    /**
     * Computes the HMAC-SHA256 signature per the Lazada Open Platform spec:
     *
     * <ol>
     *   <li>Sort all params by key (ASCII order) — already done via TreeMap</li>
     *   <li>Concatenate as: key1value1key2value2...</li>
     *   <li>Prepend the API path: /affiliate/generateShortLink + concat string</li>
     *   <li>HMAC-SHA256 with appSecret as key, UTF-8 encoding</li>
     *   <li>Uppercase hex output</li>
     * </ol>
     *
     * @param apiPath   e.g. "/affiliate/generateShortLink"
     * @param params    sorted map of all params (excluding "sign")
     * @param appSecret the app secret used as HMAC key
     * @return uppercase hex HMAC-SHA256 signature
     */
    private String computeSign(String apiPath, Map<String, String> params, String appSecret) {
        // Step 1 + 2: Build concatenated string (TreeMap already sorted)
        StringBuilder sb = new StringBuilder(apiPath);
        params.forEach((k, v) -> sb.append(k).append(v));
        String stringToSign = sb.toString();
        log.debug("String to sign: {}", stringToSign);

        // Step 3: HMAC-SHA256
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

            // Step 4: Uppercase hex
            return HexFormat.of().formatHex(hmacBytes).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    // ---------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------

    /**
     * Builds a URL-encoded query string from the params map.
     * Values are percent-encoded using UTF-8.
     */
    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * Sends a GET request to the fully built Lazada API URL.
     * Lazada's /affiliate/generateShortLink accepts GET with params in query string.
     */
    private String callLazadaApi(String url) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Lazada API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    // ---------------------------------------------------------------
    // Response Parsing
    // ---------------------------------------------------------------

    /**
     * Parses the Lazada API JSON response and extracts the affiliate short link.
     *
     * <p>Expected success shape:
     * <pre>
     * {
     *   "code": "0",
     *   "request_id": "...",
     *   "result": {
     *     "short_link": "https://s.lazada.vn/s.xxxxx"
     *   }
     * }
     * </pre>
     *
     * <p>Expected error shape:
     * <pre>
     * {
     *   "code": "...",
     *   "message": "...",
     *   "request_id": "..."
     * }
     * </pre>
     */
    private ConvertResponse parseResponse(String responseBody, String originalUrl) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        String code = root.path("code").asText("unknown");
        String requestId = root.path("request_id").asText(null);

        if ("0".equals(code)) {
            // Success
            JsonNode result = root.path("result");
            String shortLink = result.path("short_link").asText(null);

            if (shortLink == null || shortLink.isBlank()) {
                log.warn("Lazada returned code=0 but short_link is missing. Full response: {}", responseBody);
                return ConvertResponse.fail(originalUrl,
                        "Lazada returned success but no short_link in response", requestId);
            }

            log.info("Successfully generated affiliate link: {}", shortLink);
            return ConvertResponse.ok(shortLink, originalUrl, requestId);
        } else {
            // API-level error (e.g. invalid token, invalid URL)
            String message = root.path("message").asText("Unknown Lazada API error");
            log.warn("Lazada API error code={} message={} requestId={}", code, message, requestId);
            return ConvertResponse.fail(originalUrl,
                    "Lazada error [" + code + "]: " + message, requestId);
        }
    }
}
