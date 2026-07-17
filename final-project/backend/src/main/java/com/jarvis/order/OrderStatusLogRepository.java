package com.jarvis.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusLogRepository extends JpaRepository<OrderStatusLog, Long> {
}
