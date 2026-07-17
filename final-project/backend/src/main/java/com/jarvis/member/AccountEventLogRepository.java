package com.jarvis.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEventLogRepository extends JpaRepository<AccountEventLog, Long> {
}
