package com.hackathon.openrun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 오픈런(Openrun) — 소규모 호스트를 위한 공정·투명·선착순 예약 도구.
 *
 * <p>핵심 기술 가설: Spring의 핵심 가치는 한정된 공유 자원(좌석)에 대한 안전한 동시 접근 제어이며,
 * 그 진가는 인스턴스가 여러 대인 운영 환경에서 드러난다 → DB 레벨 비관적 락 + Spring Session JDBC.
 */
@EnableScheduling // (선택) 홀드 타임아웃 스케줄러 — TECH_SPEC §4.4
@SpringBootApplication
public class OpenrunApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenrunApplication.class, args);
    }
}
