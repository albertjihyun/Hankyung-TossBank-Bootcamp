package com.hackathon.openrun.security;

import com.hackathon.openrun.domain.Member;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 인증 주체. Member 엔티티를 감싸 Spring Security 와 연결한다.
 * memberId / nickname / role 을 컨트롤러·뷰에서 바로 쓸 수 있게 노출한다.
 */
public class MemberPrincipal implements UserDetails {

    private final Long memberId;
    private final String username;
    private final String password;
    private final String nickname;
    private final String role; // "USER" / "HOST" / "ADMIN"

    public MemberPrincipal(Member member) {
        this.memberId = member.getId();
        this.username = member.getUsername();
        this.password = member.getPassword();
        this.nickname = member.getNickname();
        this.role = member.getRole().name();
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
