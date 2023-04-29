package com.example.myresillience.api;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExternalAPICaller {
    private final RestTemplate restTemplate;

    public String callErrorAPI(){
        return restTemplate.getForObject("/api/external/error", String.class);
    }

    public String callSuccessAPI(){
        return restTemplate.getForObject("/api/external/success", String.class);
    }

    public String callSlowAPI(){
        return restTemplate.getForObject("/api/external/slow", String.class);
    }
}
