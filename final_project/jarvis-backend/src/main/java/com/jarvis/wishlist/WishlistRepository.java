package com.jarvis.wishlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /** 탈퇴 (02 D55) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Wishlist w where w.memberId = :memberId")
    int deleteAllOfMember(@Param("memberId") Long memberId);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<Wishlist> findByMemberIdAndProductId(Long memberId, Long productId);

    /** M-4 — 최근 찜 순 */
    List<Wishlist> findAllByMemberIdOrderByIdDesc(Long memberId);
}
