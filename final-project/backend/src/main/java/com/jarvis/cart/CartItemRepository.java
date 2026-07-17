package com.jarvis.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByMemberIdOrderByIdDesc(Long memberId);

    List<CartItem> findAllByGuestIdOrderByIdDesc(String guestId);

    List<CartItem> findAllByGuestId(String guestId);

    List<CartItem> findAllByMemberId(Long memberId);

    /** optionId NULL(무옵션)을 IS NULL로 매칭 — 파생 쿼리는 null 파라미터를 = NULL로 만들어 불일치 */
    @Query("""
            SELECT c FROM CartItem c
            WHERE c.memberId = :memberId AND c.productId = :productId
              AND ((:optionId IS NULL AND c.optionId IS NULL) OR c.optionId = :optionId)
            """)
    Optional<CartItem> findMemberLine(@Param("memberId") Long memberId,
                                      @Param("productId") Long productId,
                                      @Param("optionId") Long optionId);

    @Query("""
            SELECT c FROM CartItem c
            WHERE c.guestId = :guestId AND c.productId = :productId
              AND ((:optionId IS NULL AND c.optionId IS NULL) OR c.optionId = :optionId)
            """)
    Optional<CartItem> findGuestLine(@Param("guestId") String guestId,
                                     @Param("productId") Long productId,
                                     @Param("optionId") Long optionId);
}
