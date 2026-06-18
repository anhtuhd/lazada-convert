package com.example.lazadaaffiliate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LazadaAffiliateApplication {

    public static void main(String[] args) {
        SpringApplication.run(LazadaAffiliateApplication.class, args);
    }
}
