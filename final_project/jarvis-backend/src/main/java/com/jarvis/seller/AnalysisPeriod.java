package com.jarvis.seller;

import com.jarvis.global.response.BusinessException;
import com.jarvis.global.response.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;

/**
 * 내부 분석 API(I-6 등)의 필수 조회 기간 — 노션 계약상 from/to 누락·형식 오류·역전은
 * 모두 400 INVALID_PERIOD로 통일한다(Spring 바인딩 에러 코드와 분리하려고 String으로 받아 파싱).
 * 조회 API(I-29)는 기간이 선택이라 {@link #optional}을 쓴다 — 04 에러 카탈로그의
 * "기간 필수는 분석 API 한정"(2026-08-06 정리).
 */
public record AnalysisPeriod(LocalDate from, LocalDate to) {

    /** "오늘"의 기준 — 서비스 전역이 KST다(03 규약) */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 한 번에 조회할 수 있는 최대 기간(양 끝 포함 일수). 분석 쿼리는 기간에 비례해 무거워지고
     * 일별 시계열은 날짜 수만큼 행을 만든다 — 상한이 없으면 {@code from=0001-01-01} 한 번으로
     * DB 커넥션 풀(인스턴스당 7)과 메모리를 붙잡을 수 있다. 값은 AI 서버가 이미 쓰는 상한
     * ({@code seller_period_max_days=731}, 2년)과 맞췄다 — 정상 호출은 이 값을 넘지 않는다.
     */
    public static final int MAX_DAYS = 731;

    /**
     * 선택 기간 — 누락은 오류가 아니고 형식 오류·역전만 INVALID_PERIOD다.
     * 한쪽만 준 경우는 그 방향으로만 열린 구간으로 받는다(반대쪽은 null = 무제한).
     */
    public static AnalysisPeriod optional(String from, String to) {
        LocalDate parsedFrom = parseOrNull(from);
        LocalDate parsedTo = parseOrNull(to);
        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        return new AnalysisPeriod(parsedFrom, parsedTo);
    }

    /**
     * 기본 기간이 있는 선택 기간 (I-31) — 누락은 오류가 아니라 기본값이다.
     * 필드별로 채운다: to 없으면 오늘, from 없으면 to에서 (days-1)일 전.
     * 둘 다 없으면 노션 I-31의 "최근 7일(to=오늘, from=6일 전)"과 같은 값이 되고,
     * 한쪽만 줘도 자연스럽다(to만 주면 그 날로 끝나는 7일).
     */
    public static AnalysisPeriod withDefaultDays(String from, String to, int days) {
        LocalDate parsedFrom = parseOrNull(from);
        LocalDate parsedTo = parseOrNull(to);
        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        LocalDate resolvedTo = parsedTo != null ? parsedTo : LocalDate.now(SEOUL);
        LocalDate resolvedFrom = parsedFrom != null ? parsedFrom : resolvedTo.minusDays(days - 1L);
        if (resolvedFrom.isAfter(resolvedTo)) {
            // from만 미래로 준 경우 — 기본값으로 채운 to(오늘)보다 뒤라 역전이 된다
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        requireWithinLimit(resolvedFrom, resolvedTo);
        return new AnalysisPeriod(resolvedFrom, resolvedTo);
    }

    private static LocalDate parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
    }

    public static AnalysisPeriod of(String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        LocalDate parsedFrom;
        LocalDate parsedTo;
        try {
            parsedFrom = LocalDate.parse(from);
            parsedTo = LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        if (parsedFrom.isAfter(parsedTo)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        requireWithinLimit(parsedFrom, parsedTo);
        return new AnalysisPeriod(parsedFrom, parsedTo);
    }

    /** 기간이 상한을 넘으면 역전·형식 오류와 같은 INVALID_PERIOD — 분석 API의 기간 오류는 한 코드로 통일돼 있다 */
    private static void requireWithinLimit(LocalDate from, LocalDate to) {
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD,
                    "조회 기간은 최대 " + MAX_DAYS + "일입니다.");
        }
    }
}
