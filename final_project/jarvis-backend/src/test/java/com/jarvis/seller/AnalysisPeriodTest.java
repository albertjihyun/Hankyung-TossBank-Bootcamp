package com.jarvis.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jarvis.global.response.BusinessException;
import com.jarvis.global.response.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 분석 API 조회 기간 — 누락·형식 오류·역전·상한 초과는 모두 400 INVALID_PERIOD */
class AnalysisPeriodTest {

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("정상 기간은 그대로 받는다")
    void acceptsNormalPeriod() {
        AnalysisPeriod period = AnalysisPeriod.of("2026-07-01", "2026-07-31");

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("상한(731일)에 딱 맞는 기간은 허용한다 — AI 서버가 보내는 최대 기간과 같다")
    void acceptsExactlyMaxDays() {
        LocalDate from = LocalDate.of(2024, 8, 1);
        LocalDate to = from.plusDays(AnalysisPeriod.MAX_DAYS - 1L);

        AnalysisPeriod period = AnalysisPeriod.of(from.toString(), to.toString());

        assertThat(period.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("상한을 하루라도 넘으면 INVALID_PERIOD — 기간에 비례하는 집계가 풀과 메모리를 붙잡지 못한다")
    void rejectsPeriodLongerThanMax() {
        LocalDate from = LocalDate.of(2024, 8, 1);
        LocalDate to = from.plusDays(AnalysisPeriod.MAX_DAYS);

        assertInvalid(() -> AnalysisPeriod.of(from.toString(), to.toString()));
    }

    @Test
    @DisplayName("수천 년짜리 기간 — 일별 시계열이 수백만 행을 만들기 전에 거절한다")
    void rejectsAbsurdPeriod() {
        assertInvalid(() -> AnalysisPeriod.of("0001-01-01", "9999-12-31"));
    }

    @Test
    @DisplayName("누락·형식 오류·역전은 종전대로 INVALID_PERIOD")
    void rejectsMissingMalformedReversed() {
        assertInvalid(() -> AnalysisPeriod.of(null, "2026-07-31"));
        assertInvalid(() -> AnalysisPeriod.of("2026-07-01", " "));
        assertInvalid(() -> AnalysisPeriod.of("2026/07/01", "2026-07-31"));
        assertInvalid(() -> AnalysisPeriod.of("2026-08-01", "2026-07-31"));
    }

    @Test
    @DisplayName("기본 기간(I-31) — 한쪽만 줘도 상한을 넘으면 거절한다")
    void defaultedPeriodAlsoRespectsMax() {
        assertInvalid(() -> AnalysisPeriod.withDefaultDays("2000-01-01", null, 7));
    }

    @Test
    @DisplayName("기본 기간(I-31) — 둘 다 없으면 오늘로 끝나는 7일")
    void defaultedPeriodFillsBothEnds() {
        AnalysisPeriod period = AnalysisPeriod.withDefaultDays(null, null, 7);

        assertThat(period.from()).isEqualTo(period.to().minusDays(6));
    }

    @Test
    @DisplayName("선택 기간(I-29) — 한쪽이 열린 조회는 상한 대상이 아니다(목록 조회라 limit이 따로 있다)")
    void optionalPeriodStaysOpenEnded() {
        AnalysisPeriod period = AnalysisPeriod.optional("2000-01-01", null);

        assertThat(period.from()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(period.to()).isNull();
    }
}
