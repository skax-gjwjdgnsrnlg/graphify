package com.graphify.trading.paper;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperRunRepository extends JpaRepository<PaperRun, Long> {

    List<PaperRun> findByUserIdOrderByStartedAtDesc(Long userId);

    List<PaperRun> findByRuleIdOrderByStartedAtDesc(Long ruleId);

    Optional<PaperRun> findFirstByRuleIdAndStatus(Long ruleId, String status);
}
