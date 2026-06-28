package com.graphify.trading.rule;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingRuleRepository extends JpaRepository<TradingRule, Long> {

    List<TradingRule> findByUserIdAndModeOrderByUpdatedAtDesc(Long userId, String mode);

    Optional<TradingRule> findByIdAndUserId(Long id, Long userId);

    List<TradingRule> findByModeAndRunStatus(String mode, String runStatus);
}
