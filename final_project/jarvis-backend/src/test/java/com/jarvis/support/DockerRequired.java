package com.jarvis.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Docker가 없는 환경에서는 통합 테스트를 실패가 아니라 건너뛰게 한다 — Docker 없이도 {@code ./gradlew test}로
 * 단위 테스트는 돌릴 수 있어야 한다. 건너뛴 테스트는 리포트에 skipped로 남아 "돌지 않았음"이 드러난다.
 */
public class DockerRequired implements ExecutionCondition {

    private static Boolean available;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return dockerAvailable()
                ? ConditionEvaluationResult.enabled("Docker 사용 가능")
                : ConditionEvaluationResult.disabled("Docker가 없어 실제 DB 통합 테스트를 건너뜀");
    }

    private static synchronized boolean dockerAvailable() {
        if (available == null) {
            available = DockerClientFactory.instance().isDockerAvailable();
        }
        return available;
    }
}
