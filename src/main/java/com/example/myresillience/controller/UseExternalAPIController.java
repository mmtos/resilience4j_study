package com.example.myresillience.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.myresillience.api.ExternalAPICaller;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
public class UseExternalAPIController {

    private final ExternalAPICaller externalAPICaller;

    @GetMapping("/external/error")
    @CircuitBreaker(name = "backendA")
    public String externalError(){
        return externalAPICaller.callErrorAPI();
    }

    @GetMapping("/external/error/fallback")
    @CircuitBreaker(name = "backendA", fallbackMethod = "fallbacked")
    public String externalErrorWithFallback(){
        return externalAPICaller.callErrorAPI();
    }

    /*
        Circuit이 Closed 상태라도 500에러 발생시 fallback은 동작하는걸로 보임
     */
    public String fallbacked(Throwable throwable){
        log.error("fallbacked : {}", throwable.toString());
        return "fallbacked";
    }

    @GetMapping("/external/error/timebase")
    @CircuitBreaker(name = "backendB")
    public String externalErrorTimebase(){
        return externalAPICaller.callErrorAPI();
    }

    @GetMapping("/external/success")
    @CircuitBreaker(name = "backendA")
    public String externalSuccess(){
        return externalAPICaller.callSuccessAPI();
    }

    @GetMapping("/external/success/timebase")
    @CircuitBreaker(name = "backendB")
    public String externalSuccessTimebase(){
        return externalAPICaller.callSuccessAPI();
    }

    @GetMapping("/external/slow")
    @Bulkhead(name="backendA",type= Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackBulkhead")
    public String externalSlow(){
        return externalAPICaller.callSlowAPI();
    }

    public String fallbackBulkhead(Throwable e){
        log.error("fallback by bulkhead");
        return "fail";
    }
}
