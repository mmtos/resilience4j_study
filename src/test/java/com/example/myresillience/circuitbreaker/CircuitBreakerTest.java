package com.example.myresillience.circuitbreaker;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;


public class CircuitBreakerTest extends AbstractResilience4JTest {

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker backendA;

    private CircuitBreaker backendB;


    @BeforeEach
    void setUp(){
        super.stubExternalAPI();
        backendA = circuitBreakerRegistry.circuitBreaker("backendA");
        backendB = circuitBreakerRegistry.circuitBreaker("backendB");
    }

    @AfterEach
    void tearDown(){
        backendA.reset();
        backendB.reset();
    }

    @Test
    @DisplayName("SlidingWindowType 확인")
    void t0(){
        assertThat(backendA.getCircuitBreakerConfig().getSlidingWindowType()).isSameAs(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(backendB.getCircuitBreakerConfig().getSlidingWindowType()).isSameAs(CircuitBreakerConfig.SlidingWindowType.TIME_BASED);
    }

    @Test
    @DisplayName("open 전환여부 확인 - 실패율")
    void t1(){
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.CLOSED);
        final int minimumNumberOfCalls = backendA.getCircuitBreakerConfig().getMinimumNumberOfCalls();

        for(int i=0;i<minimumNumberOfCalls+1;i++){
            testRestTemplate.getForEntity("/external/error", String.class);
        }

        // 실패율 100퍼센트로 유지되고 있다는 점에 유의
        WireMock.verify(minimumNumberOfCalls, RequestPatternBuilder.newRequestPattern().withUrl("/api/external/error"));
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("open 전환여부 확인 - slow call 비율")
    void t2(){
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.CLOSED);
        final int minimumNumberOfCalls = backendA.getCircuitBreakerConfig().getMinimumNumberOfCalls();
        final Duration slowCallDurationThreshold = backendA.getCircuitBreakerConfig().getSlowCallDurationThreshold();

        for(int i=0;i<minimumNumberOfCalls+1;i++){
            testRestTemplate.getForEntity("/local/slow/" + (slowCallDurationThreshold.get(ChronoUnit.SECONDS) + 1), String.class);
        }

        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("open 전환여부 확인 - timeBased")
    void t3() throws Exception{
        final int minimumNumberOfCalls = backendB.getCircuitBreakerConfig().getMinimumNumberOfCalls();

        for(int i=0;i<minimumNumberOfCalls + 1;i++){
            testRestTemplate.getForEntity("/external/error/timebase", String.class);
            Thread.sleep(1000);
        }
        assertThat(backendB.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("open 전환여부 확인 : slidingWindowSize 당 1번만 실패하는 경우 ")
    void t3_1() throws Exception{
        final int minimumNumberOfCalls = backendB.getCircuitBreakerConfig().getMinimumNumberOfCalls();
        final int slidingWindowSize = backendB.getCircuitBreakerConfig().getSlidingWindowSize();

        for(int i=0;i<minimumNumberOfCalls + 1;i++){
            testRestTemplate.getForEntity("/external/error/timebase", String.class);
            Thread.sleep(1000 * (slidingWindowSize + 1));
        }
        assertThat(backendB.getState()).isSameAs(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("open 전환여부 확인 - countBased인 경우와 비교 ")
    void t3_2() throws Exception{
        final int minimumNumberOfCalls = backendA.getCircuitBreakerConfig().getMinimumNumberOfCalls();
        final int slidingWindowSize = backendA.getCircuitBreakerConfig().getSlidingWindowSize();

        for(int i=0;i<minimumNumberOfCalls + 1;i++){
            testRestTemplate.getForEntity("/external/error", String.class);
            Thread.sleep(1000 * (slidingWindowSize + 1));
        }
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("open 일때 요청이 들어온 경우 CallNotPermittedException를 던진다.")
    void t4() {
        backendA.transitionToOpenState();
        assertThatThrownBy(()-> backendA.acquirePermission()).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("circuitbreaker 공유여부 확인")
    void t5(){
        final int minimumNumberOfCalls = backendA.getCircuitBreakerConfig().getMinimumNumberOfCalls();
        for(int i=0;i<minimumNumberOfCalls+1;i++){
            testRestTemplate.getForEntity("/external/error", String.class);
        }

        ResponseEntity<String> response = testRestTemplate.getForEntity("/local/slow/3", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("half open 전환 확인")
    void t6() throws Exception{
        makeHalfOpenState(backendA);
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    @DisplayName("half open 전환 확인 - permission 획득 요청이 없다면 half open으로 바뀌지 않음.")
    void t6_1() throws Exception{
        backendA.transitionToOpenState();
        final Duration waitDurationInOpenState = backendA.getCircuitBreakerConfig().getWaitDurationInOpenState();
        Thread.sleep(waitDurationInOpenState.toMillis() + 1000);

        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("half open To Closed")
    void t7(){
        makeHalfOpenState(backendA);
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.HALF_OPEN);
        final int permittedNumberOfCallsInHalfOpenState = backendA.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState();

        for(int i=0;i< permittedNumberOfCallsInHalfOpenState;i++){
            testRestTemplate.getForEntity("/external/success", String.class);
        }

        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.CLOSED);
    }

    private void makeHalfOpenState(CircuitBreaker circuitBreaker) {
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
    }

    @Test
    @DisplayName("half open To Closed Fail")
    void t7_1() throws Exception{
        makeHalfOpenState(backendA);
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.HALF_OPEN);

        final Duration maxWaitDurationInHalfOpenState = backendA.getCircuitBreakerConfig().getMaxWaitDurationInHalfOpenState();
        Thread.sleep(maxWaitDurationInHalfOpenState.toMillis() + 1000);

        final int permittedNumberOfCallsInHalfOpenState = backendA.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState();
        for(int i=0;i<permittedNumberOfCallsInHalfOpenState;i++){
            testRestTemplate.getForEntity("/external/success", String.class);
        }
        assertThat(backendA.getState()).isSameAs(CircuitBreaker.State.OPEN);
    }
}
