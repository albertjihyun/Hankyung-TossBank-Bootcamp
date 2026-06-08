package com.hackathon.openrun.dto;

import com.hackathon.openrun.domain.Category;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * 행사 등록 요청 (TECH_SPEC §5 POST /host/events).
 */
@Getter
@Setter
public class EventCreateRequest {

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "카테고리를 선택하세요.")
    private Category category;

    @Min(value = 1, message = "정원은 1 이상이어야 합니다.")
    private int capacity;

    @NotNull(message = "오픈 시각을 입력하세요.")
    @Future(message = "오픈 시각은 미래여야 합니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime openAt;
}
