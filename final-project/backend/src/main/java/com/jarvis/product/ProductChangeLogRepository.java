package com.jarvis.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductChangeLogRepository extends JpaRepository<ProductChangeLog, Long> {
}
