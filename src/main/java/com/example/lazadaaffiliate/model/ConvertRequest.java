package com.example.lazadaaffiliate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the link conversion endpoint.
 *
 * @param productUrl The original Lazada product URL to convert.
 * @param subId1     Optional tracking ID 1 (e.g. campaign name, traffic source).
 * @param subId2     Optional tracking ID 2.
 * @param subId3     Optional tracking ID 3.
 */
public record ConvertRequest(

        @NotBlank(message = "productUrl must not be blank")
        @Pattern(
                regexp = "^https?://([a-zA-Z0-9-]+\\.)?lazada\\.(vn|sg|com\\.my|co\\.th|com\\.ph|co\\.id)/.*",
                message = "productUrl must be a valid Lazada product URL"
        )
        String productUrl,

        String subId1,
        String subId2,
        String subId3
) {}
