package com.example.myresillience.circuitbreaker;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 9090)
// Since CircuitBreaker state is shared between the threads and each of tests in this class
// relies on specific state, we don't allow to run these tests in parallel
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractResilience4JTest {

    @Autowired
    protected TestRestTemplate testRestTemplate;

    protected void stubExternalAPI(){
        stubFor(get("/api/external/error").willReturn(serverError().withBody("Sorry")));
        stubFor(get("/api/external/success").willReturn(ok("Good")));
        stubFor(get("/api/external/slow").willReturn(aResponse()
            .withStatus(HttpStatus.OK.value())
            .withFixedDelay(3000)));
    }

}
