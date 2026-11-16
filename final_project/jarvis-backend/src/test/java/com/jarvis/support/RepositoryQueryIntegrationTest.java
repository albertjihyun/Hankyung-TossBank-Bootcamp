package com.jarvis.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포지토리에 선언된 {@code @Query} 전부를 실제 MariaDB에서 한 번씩 실행한다.
 *
 * <p>목적은 결과 검증이 아니라 <b>"이 SQL을 MariaDB가 받아들이는가"</b>다. 판매자 분석 쿼리는 윈도 함수·
 * JSON 함수·상관 서브쿼리를 쓰는 네이티브 SQL인데, 단위 테스트는 리포지토리를 목으로 바꿔 SQL을 한 글자도
 * 실행하지 않는다. 2026-08-08에는 파생 테이블 별칭 {@code lines}가 MariaDB 예약어라 판매자 대시보드가
 * 전 요청 500이 났고, 배포 후에야 드러났다(docs/troubleshooting.md 1번). 이 테스트가 있었다면 PR에서 잡힌다.
 *
 * <p>쿼리를 추가하면 자동으로 대상에 포함된다 — 이 테스트에 등록할 필요가 없다. 인자는 타입만 보고
 * 무해한 값을 넣는다(빈 테이블이므로 결과는 대부분 비어 있다).
 */
class RepositoryQueryIntegrationTest extends MariaDbIntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("선언된 @Query 전부가 실제 MariaDB에서 실행된다 — 문법 오류·예약어·없는 컬럼이 없다")
    void everyDeclaredQueryRunsOnMariaDb() {
        TreeMap<String, String> failures = new TreeMap<>();
        List<String> executed = new ArrayList<>();

        for (Object repository : context.getBeansOfType(Repository.class).values()) {
            for (Class<?> contract : repository.getClass().getInterfaces()) {
                if (!contract.getName().startsWith("com.jarvis")) {
                    continue;
                }
                for (Method method : contract.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(Query.class)) {
                        continue;
                    }
                    String name = contract.getSimpleName() + "." + method.getName();
                    try {
                        run(repository, method);
                        executed.add(name);
                    } catch (Throwable e) {
                        failures.put(name, rootCauseOf(e));
                    }
                }
            }
        }

        assertThat(failures)
                .as("실제 DB에서 실패한 쿼리 (%d건 중 %d건 실행)", executed.size() + failures.size(), executed.size())
                .isEmpty();
        // 대상을 하나도 못 찾고 통과하는 일이 없도록 — 리플렉션 탐색이 깨지면 여기서 걸린다
        assertThat(executed.size()).isGreaterThanOrEqualTo(90);
    }

    /** 변경 쿼리(@Modifying)와 잠금 쿼리는 트랜잭션이 필요하다. 무엇을 실행했든 롤백한다 */
    private void run(Object repository, Method method) throws Throwable {
        Object[] arguments = sampleArguments(method);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                status.setRollbackOnly();
                try {
                    method.invoke(repository, arguments);
                } catch (InvocationTargetException e) {
                    throw new QueryFailed(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new QueryFailed(e);
                }
            });
        } catch (QueryFailed e) {
            throw e.getCause();
        }
    }

    private static Object[] sampleArguments(Method method) {
        Type[] types = method.getGenericParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            arguments[i] = sampleOf(types[i]);
        }
        return arguments;
    }

    private static Object sampleOf(Type type) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() instanceof Class<?> raw
                && Collection.class.isAssignableFrom(raw)) {
            Object element = sampleOf(parameterized.getActualTypeArguments()[0]);
            return Set.class.isAssignableFrom(raw) ? Set.of(element) : List.of(element);
        }
        if (!(type instanceof Class<?> clazz)) {
            throw new IllegalArgumentException("표본 값을 만들 수 없는 인자 타입: " + type);
        }
        if (clazz == Long.class || clazz == long.class) {
            return 1L;
        }
        if (clazz == Integer.class || clazz == int.class) {
            return 1;
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return false;
        }
        if (clazz == String.class) {
            return "x";
        }
        if (clazz == LocalDateTime.class) {
            return LocalDateTime.of(2026, 1, 1, 0, 0);
        }
        if (clazz == LocalDate.class) {
            return LocalDate.of(2026, 1, 1);
        }
        if (clazz == Pageable.class) {
            return PageRequest.of(0, 5);
        }
        if (clazz.isEnum()) {
            return clazz.getEnumConstants()[0];
        }
        throw new IllegalArgumentException("표본 값을 만들 수 없는 인자 타입: " + clazz.getName());
    }

    private static String rootCauseOf(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static final class QueryFailed extends RuntimeException {
        QueryFailed(Throwable cause) {
            super(cause);
        }
    }
}
