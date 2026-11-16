package com.jarvis.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/** @Async 작업이 호출한 요청의 로그 문맥(요청 ID)을 이어받는다 */
class AsyncConfigTest {

    private final TaskDecorator decorator = new AsyncConfig().mdcTaskDecorator();
    private final ExecutorService otherThread = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        MDC.clear();
        otherThread.shutdownNow();
    }

    @Test
    @DisplayName("호출한 스레드의 요청 ID가 비동기 스레드에서도 보인다")
    void carriesCallerContextIntoAsyncThread() throws Exception {
        MDC.put("requestId", "caller-request-0123456789");
        CompletableFuture<String> seen = new CompletableFuture<>();

        otherThread.execute(decorator.decorate(() -> seen.complete(MDC.get("requestId"))));

        assertThat(seen.get(5, TimeUnit.SECONDS)).isEqualTo("caller-request-0123456789");
    }

    @Test
    @DisplayName("작업이 끝나면 지운다 — 풀 스레드가 다음 작업에 앞 요청의 ID를 끌고 가지 않는다")
    void clearsContextAfterTask() throws Exception {
        MDC.put("requestId", "caller-request-0123456789");
        otherThread.submit(decorator.decorate(() -> { })).get(5, TimeUnit.SECONDS);
        MDC.clear();

        String leftover = otherThread.submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS);

        assertThat(leftover).isNull();
    }
}
