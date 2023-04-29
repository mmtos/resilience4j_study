package com.example.myresillience.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class InternalController {
    @GetMapping("/local/slow/{second}")
    @CircuitBreaker(name = "backendA")
    public String slow(@PathVariable String second) throws Exception{
        Thread.sleep(TimeUnit.SECONDS.toMillis(Integer.parseInt(second)));
        return "success";
    }

    @GetMapping("/local/slow/{second}/timebase")
    @CircuitBreaker(name = "backendA")
    public String slowtimebase(@PathVariable String second) throws Exception{
        Thread.sleep(TimeUnit.SECONDS.toMillis(Integer.parseInt(second)));
        return "success";
    }
}
