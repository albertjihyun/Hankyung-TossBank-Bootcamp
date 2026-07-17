package com.jarvis.inquiry;

import com.jarvis.inquiry.dto.InquiryListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M-9 조회 전용 (04 §5) — 접수는 internal 콜백(Phase 5), 답변은 고도화 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository inquiryRepository;

    public InquiryListResponse myInquiries(Long memberId, int page, int size) {
        return InquiryListResponse.from(
                inquiryRepository.findAllByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size)));
    }
}
