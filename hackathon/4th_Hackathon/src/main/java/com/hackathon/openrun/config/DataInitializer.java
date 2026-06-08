package com.hackathon.openrun.config;

import com.hackathon.openrun.domain.Category;
import com.hackathon.openrun.domain.Event;
import com.hackathon.openrun.domain.EventStatus;
import com.hackathon.openrun.domain.Member;
import com.hackathon.openrun.domain.Role;
import com.hackathon.openrun.repository.EventRepository;
import com.hackathon.openrun.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 더미 시드 (TECH_SPEC §8). 멱등: 이미 데이터가 있으면 스킵.
 *
 * <p>계정: admin/admin123, host1·host2/host123, user1~3/user123 (모두 BCrypt).
 * 행사 8개: 카테고리 분산, 일부 OPEN(즉시 예약 데모)/일부 SCHEDULED(카운트다운 데모),
 * capacity 5/10/30/100 (5짜리로 빠른 마감·대기 데모).
 */
@Component
@Order(1)
@Profile("!test") // 테스트 격리: 통합 테스트(test 프로파일)에서는 시드 미실행
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(MemberRepository memberRepository,
                           EventRepository eventRepository,
                           PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            return; // 멱등 — 이미 시드됨
        }

        // ===== 계정 =====
        save("admin", "admin123", "관리자", Role.ADMIN);
        Member host1 = save("host1", "host123", "성수동 호스트", Role.HOST);
        Member host2 = save("host2", "host123", "연남동 호스트", Role.HOST);
        save("user1", "user123", "얼리버드", Role.USER);
        save("user2", "user123", "오픈러너", Role.USER);
        save("user3", "user123", "대기조", Role.USER);

        LocalDateTime now = LocalDateTime.now();

        // ===== 행사 8개 =====
        // OPEN (즉시 예약 데모): open_at 과거
        eventRepository.saveAll(List.of(
                event(host1, "성수동 한정판 스니커즈 드롭", Category.POPUP, 5,
                        now.minusMinutes(10), EventStatus.OPEN,
                        "100켤레 한정. 선착순 5명 현장 픽업권."),
                event(host1, "바리스타 핸드드립 클래스", Category.CLASS, 10,
                        now.minusMinutes(5), EventStatus.OPEN,
                        "원두 3종 비교 시음 + 실습. 정원 10명."),
                event(host2, "독립서점 시 낭독회", Category.SHOW, 30,
                        now.minusMinutes(2), EventStatus.OPEN,
                        "작가 4인 릴레이 낭독. 노쇼 방지를 위한 선착순."),
                event(host2, "주말 플리마켓 셀러 부스", Category.MARKET, 100,
                        now.minusMinutes(1), EventStatus.OPEN,
                        "셀러 100팀 선착순. 부스 위치는 신청순.")
        ));

        // SCHEDULED (카운트다운 데모): open_at 미래
        eventRepository.saveAll(List.of(
                event(host1, "한정 굿즈 팝업 2차", Category.POPUP, 5,
                        now.plusMinutes(2), EventStatus.SCHEDULED,
                        "2분 뒤 오픈! 선착순 5명."),
                event(host1, "내추럴 와인 클래스", Category.CLASS, 10,
                        now.plusHours(1), EventStatus.SCHEDULED,
                        "소믈리에와 함께하는 테이스팅."),
                event(host2, "재즈 라이브 소극장", Category.SHOW, 30,
                        now.plusHours(3), EventStatus.SCHEDULED,
                        "3중주 라이브. 30석 한정."),
                event(host2, "비건 베이킹 마켓", Category.MARKET, 30,
                        now.plusDays(1), EventStatus.SCHEDULED,
                        "내일 오픈. 디저트 30박스 선착순.")
        ));
    }

    private Member save(String username, String rawPw, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPw))
                .nickname(nickname)
                .role(role)
                .build());
    }

    private Event event(Member host, String title, Category category, int capacity,
                        LocalDateTime openAt, EventStatus status, String desc) {
        return Event.builder()
                .host(host)
                .title(title)
                .description(desc)
                .category(category)
                .capacity(capacity)
                .openAt(openAt)
                .status(status)
                .build();
    }
}
