package com.jarvis.support;

import com.jarvis.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 실제 MariaDB에 붙는 통합 테스트의 공통 기반.
 *
 * <p>왜 필요한가 — 이 저장소의 단위 테스트는 리포지토리를 전부 목으로 대체한다. 그래서 ① 네이티브 SQL의
 * 문법 ② UNIQUE·CHECK 같은 DB 제약 ③ 조건부 UPDATE가 동시 요청에서 정말 하나만 통과시키는지를 한 번도
 * 실행해 보지 못한다. 운영에서 예약어 하나로 대시보드가 전면 500이 난 사고(docs/troubleshooting.md 1번)가
 * 정확히 이 구멍에서 나왔다.
 *
 * <p>스키마는 운영과 같은 원본({@code docs/backend/schema.sql})을 컨테이너 초기화 스크립트로 싣는다.
 * {@code ddl-auto=validate}가 그대로 켜져 있으므로 <b>컨텍스트가 뜬다는 것 자체가 엔티티와 스키마가
 * 일치한다는 검증</b>이다.
 *
 * <p>트랜잭션을 걸지 않는다({@code NOT_SUPPORTED}) — 동시성 테스트는 여러 스레드가 각자 커밋한 결과를
 * 서로 봐야 하는데, 테스트 전체를 롤백 트랜잭션으로 감싸면 그게 불가능하다. 대신 테스트가 끝날 때마다
 * 전 테이블을 비운다.
 *
 * <p>Docker가 없으면 실패가 아니라 건너뛴다({@link DockerRequired}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ExtendWith(DockerRequired.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class MariaDbIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> Database.get().getJdbcUrl());
        registry.add("spring.datasource.username", () -> Database.get().getUsername());
        registry.add("spring.datasource.password", () -> Database.get().getPassword());
    }

    @AfterEach
    void truncateAll() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", String.class);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        tables.forEach(table -> jdbc.execute("TRUNCATE TABLE `" + table + "`"));
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /**
     * 컨테이너는 테스트 클래스마다 새로 띄우지 않고 JVM당 하나를 공유한다(기동에 수 초가 든다).
     * 첫 사용 시점에 띄우는 건 Docker 유무 판정({@link DockerRequired})이 먼저 돌게 하기 위해서다 —
     * 클래스 로딩 때 띄우면 Docker 없는 환경에서 건너뛰기 전에 예외로 죽는다.
     */
    private static final class Database {

        private static MariaDBContainer<?> container;

        private static synchronized MariaDBContainer<?> get() {
            if (container == null) {
                container = new MariaDBContainer<>("mariadb:11.4")
                        .withDatabaseName("jarvis")
                        .withCommand("--character-set-server=utf8mb4",
                                "--collation-server=utf8mb4_unicode_ci")
                        .withCopyFileToContainer(
                                MountableFile.forHostPath("docs/backend/schema.sql"),
                                "/docker-entrypoint-initdb.d/01-schema.sql");
                container.start();
            }
            return container;
        }
    }
}
