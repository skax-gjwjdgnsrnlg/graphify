package com.graphify.trading.paper;

import com.graphify.trading.engine.Signal;
import com.graphify.trading.rule.TradingRule;
import java.time.Instant;

/**
 * 신호 → 가상 체결 포트. PaperExecutor가 구현. Phase 6에서 LiveExecutor가 추가 구현 예정.
 */
public interface OrderExecutorPort {

    /**
     * 신호를 받아 가상 체결 실행.
     * @param signal              BUY | SELL | HOLD
     * @param rule                평가 대상 룰 (userId, ruleId 참조)
     * @param symbol              종목 코드
     * @param price               현재가 (마지막 5분봉 종가)
     * @param ts                  평가 시각
     * @param indicatorSnapshotJson 지표 스냅샷 JSON (null 허용)
     */
    TradeResult execute(Signal signal, TradingRule rule, String symbol,
                        double price, Instant ts, String indicatorSnapshotJson);

    /**
     * 이 실행기가 해당 룰을 처리할 수 있는지. mode 기반 라우팅.
     * Phase 6: TossOrderExecutor가 supports(rule.getMode()=="LIVE")를 구현하고 Spring이 자동 주입 →
     * LiveEvaluationService 변경 불필요.
     */
    boolean supports(TradingRule rule);
}
