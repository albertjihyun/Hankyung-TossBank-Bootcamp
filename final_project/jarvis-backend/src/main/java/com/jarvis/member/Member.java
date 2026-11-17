package com.jarvis.member;

import com.jarvis.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "agreed_terms_at", nullable = false)
    private LocalDateTime agreedTermsAt;

    @Column(name = "agreed_privacy_at", nullable = false)
    private LocalDateTime agreedPrivacyAt;

    /** 탈퇴 시각 (02 D55). 값이 있으면 개인정보가 지워진 행이다 */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    static final String WITHDRAWN_NICKNAME = "탈퇴회원";

    /**
     * 탈퇴 — 행은 지우지 않고 사람을 알아볼 수 있는 값만 지운다 (02 D55). 주문·리뷰가 이 행을 FK로 물고 있고,
     * 거래 기록은 법정 보존 대상이라 행을 지울 수 없다.
     * <ul>
     *   <li>이메일은 자리표시자로 바꿔 원래 주소를 비운다 — 같은 주소로 다시 가입할 수 있다.</li>
     *   <li>비밀번호는 아무도 모르는 값으로 바꾼다 — 자리표시자 이메일로 로그인할 길이 없다.</li>
     *   <li>생일은 연도만 남긴다 — 판매자 분석의 연령대 집계(과거 주문)가 깨지지 않을 만큼만.</li>
     * </ul>
     */
    public void withdraw(String unusablePassword, LocalDateTime now) {
        this.email = "withdrawn-" + id + "@deleted.invalid";
        this.password = unusablePassword;
        this.nickname = WITHDRAWN_NICKNAME;
        this.birthDate = birthDate.withDayOfYear(1);
        this.withdrawnAt = now;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /** 가입 경로는 USER 고정 — SELLER/ADMIN은 시드 전용 (02 member) */
    public static Member signup(String email, String encodedPassword, String nickname,
                                Gender gender, LocalDate birthDate, LocalDateTime agreedAt) {
        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.nickname = nickname;
        member.role = Role.USER;
        member.gender = gender;
        member.birthDate = birthDate;
        member.agreedTermsAt = agreedAt;
        member.agreedPrivacyAt = agreedAt;
        return member;
    }
}
