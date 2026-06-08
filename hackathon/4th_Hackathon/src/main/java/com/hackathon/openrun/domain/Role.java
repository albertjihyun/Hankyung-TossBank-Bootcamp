package com.hackathon.openrun.domain;

/**
 * 사용자 역할 (Spring Security 권한 = ROLE_ + name()).
 * TECH_SPEC §4.1: 비회원 / USER / HOST / ADMIN
 */
public enum Role {
    USER,   // 참가자: 선착순 예약, 대기열 등록, 내 예약 관리
    HOST,   // 주최자: 행사 등록, 참가자 현황
    ADMIN   // 관리자: 전체 행사·사용자 관리
}
