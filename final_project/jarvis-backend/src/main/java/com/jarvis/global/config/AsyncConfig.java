package com.jarvis.global.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * {@code @Async} 작업이 호출한 요청의 로그 문맥(요청 ID)을 이어받게 한다. MDC는 스레드에 묶여 있어
     * 그대로 두면 비동기로 넘어간 로그와 AI 서버 호출(I-20 통지)에서 요청 ID가 끊긴다.
     * 스프링 부트가 기본 실행기에 이 데코레이터를 자동으로 건다.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> caller = MDC.getCopyOfContextMap();
            return () -> {
                if (caller != null) {
                    MDC.setContextMap(caller);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
