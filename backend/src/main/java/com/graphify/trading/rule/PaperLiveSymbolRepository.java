package com.graphify.trading.rule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperLiveSymbolRepository extends JpaRepository<PaperLiveSymbol, Long> {

    List<PaperLiveSymbol> findByRuleId(Long ruleId);

    /**
     * 룰의 종목 행 일괄 삭제. 벌크 DML로 즉시 실행한다.
     *
     * <p>파생 삭제(derived {@code deleteByRuleId})는 엔티티를 em.remove로 큐잉하므로,
     * assignSymbols()의 delete+insert를 한 트랜잭션에서 수행할 때 Hibernate가
     * insert를 delete보다 먼저 flush하여 {@code uq_paper_live_symbols(rule_id,symbol)}
     * 위반을 일으켰다(매 틱 재선정 실패 → 유니버스 고정). 벌크 DELETE는 호출 시점에
     * DB에 즉시 반영되어 이후 insert와 충돌하지 않는다.
     *
     * <p>{@code flushAutomatically = true} 필수: 같은 트랜잭션에서 이 삭제 직전에 발생한
     * dirty 변경(예: stop()의 {@code rule.setRunStatus("STOPPED")})을 먼저 flush하지 않으면,
     * {@code clearAutomatically = true}가 영속성 컨텍스트를 비우며 그 미반영 UPDATE를 폐기해
     * DB에 커밋되지 않는다(중지가 응답상 STOPPED지만 DB는 RUNNING 잔류하는 버그).</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PaperLiveSymbol p WHERE p.ruleId = :ruleId")
    void deleteByRuleId(@Param("ruleId") Long ruleId);

    @Query("SELECT DISTINCT p.symbol FROM PaperLiveSymbol p WHERE p.ruleId IN :ruleIds")
    List<String> findDistinctSymbolsByRuleIds(@Param("ruleIds") List<Long> ruleIds);
}
