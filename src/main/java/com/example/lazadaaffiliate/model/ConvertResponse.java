package com.example.lazadaaffiliate.model;

/**
 * Response body returned by the link conversion endpoint.
 *
 * @param success       Whether the conversion was successful.
 * @param affiliateLink The generated affiliate short link (null on failure).
 * @param originalUrl   The original product URL that was submitted.
 * @param errorMessage  Human-readable error description (null on success).
 * @param requestId     The Lazada API request ID for debugging purposes.
 */
public record ConvertResponse(
        boolean success,
        String affiliateLink,
        String originalUrl,
        String errorMessage,
        String requestId
) {

    /** Convenience factory for a successful conversion. */
    public static ConvertResponse ok(String affiliateLink, String originalUrl, String requestId) {
        return new ConvertResponse(true, affiliateLink, originalUrl, null, requestId);
    }

    /** Convenience factory for a failed conversion. */
    public static ConvertResponse fail(String originalUrl, String errorMessage, String requestId) {
        return new ConvertResponse(false, null, originalUrl, errorMessage, requestId);
    }
}
