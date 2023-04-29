package com.example.myresillience.circuitbreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;

public class BulkHeadTest extends AbstractResilience4JTest{

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    private Bulkhead backendA;

    @BeforeEach
    void setUp(){
        super.stubExternalAPI();
        // 세마포어기반 bulkhead
        backendA = bulkheadRegistry.bulkhead("backendA");
        backendA.getEventPublisher().onCallPermitted(e -> System.out.println("permitted " + e));
        backendA.getEventPublisher().onCallRejected(e -> System.out.println("rejected " + e));
        backendA.getEventPublisher().onCallFinished(e -> System.out.println("finished " + e));
    }

    @Test
    @DisplayName("slow api로 동시진입 제한 여부 확인")
    void t1(){
        // BulkheadFullException은 발생하지 않음
        // semapore가 여유 있는지 확인 후 permittion 요청을 하는걸로 보임.
        final int maxConcurrentCalls =  backendA.getBulkheadConfig().getMaxConcurrentCalls();
        List<CompletableFuture<ResponseEntity<String>>> futures = new ArrayList<>();
        for(int i=0;i<maxConcurrentCalls*3;i++){
            CompletableFuture<ResponseEntity<String>> result = CompletableFuture
                .supplyAsync(() -> testRestTemplate.getForEntity("/external/slow",String.class));
            futures.add(result);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
    }

    @Test
    @DisplayName("slow api로 동시진입 제한 여부 확인")
    void t2() {
        // 블로그 처럼 BulkheadFullException은 발생하도록 decorate 추가함.
        final int maxConcurrentCalls =  backendA.getBulkheadConfig().getMaxConcurrentCalls();
        List<Throwable> captured = new ArrayList<>();
        List<CompletableFuture<ResponseEntity<String>>> futures = new ArrayList<>();
        for(int i=0;i<maxConcurrentCalls*3;i++){
            CompletableFuture<ResponseEntity<String>> result = CompletableFuture
                .supplyAsync(Bulkhead.decorateSupplier(backendA,() -> testRestTemplate.getForEntity("/external/slow",String.class)))
                .whenComplete((res,e) -> {
                    if(e != null){
                        captured.add(e.getCause());
                    }
                });
            futures.add(result);
        }
        Assertions.assertThatThrownBy(() -> CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join()).isInstanceOf(CompletionException.class);
        for(Throwable t : captured){
            Assertions.assertThat(t).isInstanceOf(BulkheadFullException.class);
        }
     }

}
