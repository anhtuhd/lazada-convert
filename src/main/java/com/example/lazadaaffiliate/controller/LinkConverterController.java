package com.example.lazadaaffiliate.controller;

import com.example.lazadaaffiliate.model.ConvertRequest;
import com.example.lazadaaffiliate.model.ConvertResponse;
import com.example.lazadaaffiliate.service.LazadaAffiliateService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing the Lazada affiliate link conversion API.
 *
 * <pre>
 * POST /api/convert
 * Content-Type: application/json
 *
 * {
 *   "productUrl": "https://www.lazada.vn/products/...",
 *   "subId1": "my-campaign",   // optional
 *   "subId2": "facebook",      // optional
 *   "subId3": "video-ad"       // optional
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class LinkConverterController {

    private static final Logger log = LoggerFactory.getLogger(LinkConverterController.class);

    private final LazadaAffiliateService affiliateService;

    public LinkConverterController(LazadaAffiliateService affiliateService) {
        this.affiliateService = affiliateService;
    }

    /**
     * Converts a Lazada product URL into an affiliate short link.
     *
     * @param request the conversion request containing the product URL
     * @return HTTP 200 with conversion result (success or failure details)
     */
    @PostMapping("/convert")
    public ResponseEntity<ConvertResponse> convert(@Valid @RequestBody ConvertRequest request) {
        log.info("Received convert request for: {}", request.productUrl());
        ConvertResponse response = affiliateService.convertToAffiliateLink(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     *
     * @return HTTP 200 with a simple status message
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
