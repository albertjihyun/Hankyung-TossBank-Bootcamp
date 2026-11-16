package com.jarvis.global.response;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 예외 → 응답 매핑 (03 D2). 호출자가 잘못 보낸 요청은 4xx여야 한다 — 500으로 나가면 클라이언트의
 * 실수가 서버 장애로 집계되고, 에러 로그가 스택트레이스로 부풀며, 호출자는 무엇을 고칠지 알 수 없다.
 */
// 서블릿 필터(@Component)는 인증·Redis 의존성을 끌고 오는데 여기서 볼 것은 예외 매핑뿐이라 뺀다
@WebMvcTest(controllers = GlobalExceptionHandlerTest.Probe.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Filter.class))
@Import(GlobalExceptionHandlerTest.Probe.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @RestController
    static class Probe {

        @GetMapping("/probe")
        String withParam(@RequestParam String required) {
            return required;
        }

        @GetMapping("/probe/header")
        String withHeader(@RequestHeader("X-Need") String need) {
            return need;
        }

        @PostMapping(value = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        String withBody(@RequestBody Map<String, Object> body) {
            return "ok";
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("예상하지 못한 서버 오류");
        }
    }

    @Test
    @DisplayName("허용하지 않는 메서드 — 405 METHOD_NOT_ALLOWED (500 아님)")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(delete("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type — 415 UNSUPPORTED_MEDIA_TYPE (500 아님)")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락 — 400 VALIDATION_ERROR + 어느 파라미터인지 (500 아님)")
    void missingRequestParam() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("required"));
    }

    @Test
    @DisplayName("필수 헤더 누락 — 400 VALIDATION_ERROR + 어느 헤더인지 (500 아님)")
    void missingRequestHeader() throws Exception {
        mockMvc.perform(get("/probe/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("X-Need"));
    }

    @Test
    @DisplayName("진짜 서버 오류만 500 INTERNAL_ERROR — 내부 메시지는 응답에 싣지 않는다")
    void unexpectedErrorIs500WithoutLeak() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."));
    }
}
