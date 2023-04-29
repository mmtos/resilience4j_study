# Resilience4j 코어 모듈 설정
## 설정 프로퍼티 구조
- resilience4j.(모듈명)으로 시작한다.
- `configs`는 모듈인스턴스간 공유할 설정들을 지정할 수 있다.
    - default: 라이브러리에서 제공하는 기본설정들을 overide 한다
    - 그외: 추가적인 공유 설정을 만들 수 있다. 이름을 지정해야한다.
- `instances`는 모듈 인스턴스를 정의한다.
    - 이름을 지정하고 그 하위에 설정값들을 쓴다.
    -  baseConfig를 통해 공유 설정을 이용할 수 있다.

## CircuitBreaker
```properties
resilience4j.circuitbreaker:
  configs:
    default:
      slidingWindowSize: 100(초)
      slidingWindowType: TIME_BASED
      minimumNumberOfCalls: 20
      failureRateThreshold: 60
      slowCallRateThreshold: 50
      slowCallDurationThreshold: 1000(ms)
      recordExceptions: java.lang.Exception
      recordFailurePredicate: io.github.robwin.exception.RecordFailurePredicate
      ignoreExceptions:
      permittedNumberOfCallsInHalfOpenState: 10
      waitDurationInOpenState: 10000(ms)
      eventConsumerBufferSize: 10
      registerHealthIndicator: true
    someShareConfig:
      slidingWindowSize: 50 (calls)
      slidingWindowType: COUNT_BASED

  instances:
    backendA:
      baseConfig: default
      waitDurationInOpenState: 5000
    backendB:
      baseConfig: someShareConfig
```

### SlidingWindow
- COUNT_BASED
    - Measurement를 담는 N 크기의 원형 배열을 이용한다.
    - 최근 N개의 응답 결과(total aggregation)를 반영하여 상태판단을 한다.
- TIME_BASED
    - partial aggregation(1초 동안의 응답결과)를 담는 N크기의 원형 배열을 이용한다.
    - 개별 응답결과를 저장하진 않지만 partial aggrement를 업데이트하는 방식으로 간접적으로 반영한다.
    - 최근 N초간 응답결과(total aggregation)를 반영하여 상태판단을 한다.
- N값 설정을 위한 옵션
    - `slidingWindowSize`

### 상태 변경
- CLOSED to OPEN
    - 실패율이 기준값(`failureRateThreshold`) 이상인 경우
        - 실패의 기준을 직접 정의할 수도 있는걸로 보임
    - slow call의 비율이 기준값 이상인 경우
    - 주의 : window size동안 call의 개수가 `minimumNumberOfCalls`를 넘지 못하면 실패율과 관계 없이 OPEN되지 않음.

- OPEN to HALF_OPEN
    - 항상 OPEN을 유지할 수 없음.
    -  `waitDurationInOpenState` 만큼 시간이 지나면 자동 변경됨

- HALF_OPEN to OPEN or HALF_OPEN to CLOSED
    - `permittedNumberOfCallsInHalfOpenState` 개 만큼의 call을 허용해 놓고 OPEN으로 바꿔도될지 간을 봄.
    - 실패율이나 slow call 비율이 기준값 이하이면 다시 CLOSED로, 아니면 OPE으로 변경됨.

### `실패` 기준에 대한 설정
- `recordExceptions`: 해당 예외 리스트가 발생했을때만 실패로 본다.
-  `recordFailurePredicate`: 실패에 대한 기준을 직접 지정한다.
-  `ignoreExceptions`: 해당 예외 리스트가 발생해도 실패로 보지 않는다.

### thread safe
- 동시성 이슈로 인해 circuit이 OPEN되었는데도 불구하고 허용되는 call이 있을 순 없다.
- 기본적으로는 circuitbreaker에 진입하는 모든 thread들을 다 받아들인다.
- 진입하는 thread의 개수 자체를 제한하려면 `BulkHead`를 같이 사용한다.

### more options
- io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigurationProperties.InstanceProperties 참고

## BulkHead
```properties
resilience4j.bulkhead:
    configs:
        default:
            maxWaitDuration: 0
            maxConcurrentCalls: 25
            eventConsumerBufferSize: 10
    instances:
        backendA:
            maxConcurrentCalls: 10
        backendB:
            maxWaitDuration: 1000
            maxConcurrentCalls: 20
resilience4j.thread-pool-bulkhead:
  configs:
    default:
      max-thread-pool-size: 16
      core-thread-pool-size: 15
      queue-capacity: 100
      keep-alive-duration: 20
      writable-stack-trace-enabled: true
  instances:
    backendA:
      max-thread-pool-size: 2
      core-thread-pool-size: 2
```
### resilience4j.Bulkhead와 resilience4j.ThreadPoolBulkhead의 차이
- Bulkhead는 세마포어를 기반으로 하고 ThreadPoolBulkhead는 threadpool을 기반으로 한다.
- 따라서 두 Bulkhead의 설정값도 다르다.

### Bulkhead 설정값
- maxConcurrentCalls : 몇 개의 스레드만 통과시킬지
- maxWaitDuration : 진입이 불가능한경우 기다리는 최대 시간, 지난경우 BulkheadFullException이 발생한다.
- writable-stack-trace-enabled
  - true일때, BulkheadFullException가 발생한 스레드의 stack trace 를 같이 출력한다.
  - false일때는 한줄만 출력된다.

### ThreadPoolBulkhead 설정값
- maxThreadPoolSize, coreThreadPoolSize: 스레드 개수 설정
- keep-alive-duration: core 개수를 초과하는 스레드가 작업이 없을때 대기하는 최대 시간. 이후 종료된다.

### RegistryEvent, BulkheadEvent
- Registry.getEventPublisher를 통해 publisher에 eventConsumer를 등록할 수 있다. 혹은 스프링을 사용한다면 Customizer를 등록하는 방식으로도 가능하다.
- https://resilience4j.readme.io/docs/bulkhead#consume-emitted-registryevents
- https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/#bulkhead-example

# Spring Resillience4J

## Customizer
- 기본 설정 관련 클래스 : Resilience4JAutoConfiguration

## 알아두면 좋을만한 정보들 
- resilience4j-bulkhead is on the classpath, Spring Cloud CircuitBreaker will wrap all methods with a Resilience4j Bulkhead.
- By default, Spring Cloud CircuitBreaker Resilience4j uses FixedThreadPoolBulkhead
- 

## 여러 모듈이 겹치는 경우 적용 순서
- `Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )`
- so Retry is applied at the end (if needed).
- https://resilience4j.readme.io/docs/getting-started-3#aspect-order

## actuator와 연동
-  CircuitBreaker, Retry, RateLimiter, Bulkhead and TimeLimiter에 대한 `Metric`, `Health` 및 `Event` 정보를 actuator를 통해 조회할 수 있다.
- `registerHealthIndicator` : true
- `eventConsumerBufferSize` : 몇개의 이밴트를 저장해 두고 있을지 설정
- https://resilience4j.readme.io/docs/getting-started-3#events-endpoint

## Actuator + Micrometer로 Prometheus 연동
- micrometer는 actuator에서 수집한 정보를 Prometheus가 읽을수 있는 metric으로 제공하는 역할
- https://velog.io/@windsekirun/Spring-Boot-Actuator-Micrometer%EB%A1%9C-Prometheus-%EC%97%B0%EB%8F%99%ED%95%98%EA%B8%B0

# 참고 사이트
## 블로그
- https://www.baeldung.com/spring-boot-resilience4j
- BulkHead
  - https://jydlove.tistory.com/74
  - https://reflectoring.io/bulkhead-with-resilience4j/
- 공식 Demo : https://github.com/resilience4j/resilience4j-spring-boot2-demo
- 단위 테스트 작성 : https://ynovytskyy.medium.com/unit-testing-circuit-breaker-8ed2c9098e11
- 프로메테우스 연동 : https://velog.io/@windsekirun/Spring-Boot-Actuator-Micrometer%EB%A1%9C-Prometheus-%EC%97%B0%EB%8F%99%ED%95%98%EA%B8%B0

## Docs
### Resilience4j
- core module
  - https://resilience4j.readme.io/docs/bulkhead
- with spring boot : https://resilience4j.readme.io/docs/getting-started-3

- spring cloud circuitbreaker : https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/

## 테스트 관련
### 외부 API Mocking
- wireMock : https://wiremock.org/
- spring cloud contract Stub Runner : spring cloud와 wiremock 통합
  - https://docs.spring.io/spring-cloud-contract/docs/current/reference/html/project-features.html#features-stub-runner-rule-spring

### application 내부 endpoint test
- MockMvc : servlet container 띄우지 않고 controller 레이어 테스트(https://www.inflearn.com/blogs/339)
- rest-assured : https://rest-assured.io/
- testRestTemplate vs WebTestClient : WebTestClient가 더 최신버전
  - https://docs.spring.io/spring-boot/docs/2.1.5.RELEASE/reference/html/boot-features-testing.html#boot-features-rest-templates-test-utility
  - https://stackoverflow.com/questions/61318756/testresttemplate-vs-webtestclient-vs-restassured-what-is-the-best-approach-for

## 기타
- vavr : https://www.baeldung.com/vavr