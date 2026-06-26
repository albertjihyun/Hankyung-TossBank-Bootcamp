package com.hackathon.olive.repository;

import com.hackathon.olive.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);
}
