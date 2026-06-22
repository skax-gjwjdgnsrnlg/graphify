package com.graphify.trading.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.graphify.trading.engine.EvalResult.ExitReason;
import com.graphify.trading.rule.definition.RuleDefinition.Condition;
import com.graphify.trading.rule.definition.RuleDefinition.ConditionGroup;
import com.graphify.trading.rule.definition.RuleDefinition.ExitSpec;
import com.graphify.trading.rule.definition.RuleDefinition.Operand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * RuleEvaluator 리치 반환 타입(EvalResult) 검증 — RULE-09.
 *
 * <p>조건별 실제 지표값 + passed 보존, 청산 사유 TAKE_PROFIT/STOP_LOSS/INDICATOR 구분,
 * 기존 boolean 시그니처 하위 호환을 단언한다.
 */
class RuleEvaluatorRichTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /**
     * RSI(14) < 30 단일 조건 그룹을 생성한다.
     */
    private ConditionGroup rsiLt30Group() {
        Operand left = new Operand("RSI", null, Map.of("period", 14));
        Operand right = new Operand(null, 30.0, null);
        Condition cond = new Condition(left, "<", right);
        return new ConditionGroup("AND", List.of(cond));
    }

    /**
     * RSI(14) < 30 가 충족되는 종가 배열을 생성한다.
     *
     * <p>인덱스 14(i=14)에서 RSI ≈ 28.57: 최근 14 봉 중 10 봉 하락(각 1.0),
     * 4 봉 상승(각 1.0) → avgGain=4/14, avgLoss=10/14 → rs≈0.4 → RSI≈28.57.
     * 시드 가격을 1000으로 설정하고 0~14 구간을 패딩한다.
     */
    private double[] closesForRsiBelow30() {
        // 총 16개 요소: index 0 은 기준가, index 1~15 에서 diff 발생, 평가 i=15
        // → rsi() 는 i - period + 1 ~ i 범위의 diff를 사용하므로 i=14+1=15가 필요
        // diffs at indices 2..15 (14개):
        //   indices 2,3,4,5 → +1.0 (4 gains)
        //   indices 6..15  → -1.0 (10 losses)
        double[] closes = new double[16];
        closes[0] = 1000.0;
        closes[1] = 1000.0;  // padding (diff at index 1 is neutral — not used by rsi at i=15)
        closes[2] = 1001.0;  // gain
        closes[3] = 1002.0;  // gain
        closes[4] = 1003.0;  // gain
        closes[5] = 1004.0;  // gain
        closes[6] = 1003.0;  // loss
        closes[7] = 1002.0;  // loss
        closes[8] = 1001.0;  // loss
        closes[9] = 1000.0;  // loss
        closes[10] = 999.0;  // loss
        closes[11] = 998.0;  // loss
        closes[12] = 997.0;  // loss
        closes[13] = 996.0;  // loss
        closes[14] = 995.0;  // loss
        closes[15] = 994.0;  // loss
        return closes;
    }

    /**
     * 두 조건을 AND 로 묶는 그룹: RSI(14) < 30 (충족) AND PRICE > 5000 (미충족, price≈994).
     */
    private ConditionGroup twoConditionAnd() {
        Operand rsiLeft = new Operand("RSI", null, Map.of("period", 14));
        Operand rsiRight = new Operand(null, 30.0, null);
        Condition rsiCond = new Condition(rsiLeft, "<", rsiRight);

        Operand priceLeft = new Operand("PRICE", null, null);
        Operand priceRight = new Operand(null, 5000.0, null);
        Condition priceCond = new Condition(priceLeft, ">", priceRight);

        return new ConditionGroup("AND", List.of(rsiCond, priceCond));
    }

    /**
     * ExitSpec: takeProfitPct=5.0, stopLossPct=-3.0, 지표 조건 없음.
     */
    private ExitSpec tpSlExit() {
        return new ExitSpec(null, null, 5.0, -3.0);
    }

    /**
     * ExitSpec: TP/SL 없음, PRICE < 980 지표 조건.
     */
    private ExitSpec indicatorExit() {
        Operand left = new Operand("PRICE", null, null);
        Operand right = new Operand(null, 980.0, null);
        Condition cond = new Condition(left, "<", right);
        return new ExitSpec("AND", List.of(cond), null, null);
    }

    /**
     * EMA(9) crossAbove SMA(20) 조건 그룹 (크로스 방향).
     * SMA(20) 을 초과하기 위해 충분한 길이의 배열이 필요하다.
     */
    private ConditionGroup crossAboveGroup() {
        Operand left = new Operand("EMA", null, Map.of("period", 9));
        Operand right = new Operand("SMA", null, Map.of("period", 20));
        Condition cond = new Condition(left, "crossAbove", right);
        return new ConditionGroup("AND", List.of(cond));
    }

    // -----------------------------------------------------------------------
    // evalEntry: 단일 조건 — RSI(14) < 30 충족
    // -----------------------------------------------------------------------

    @Test
    void evalEntry_rsiBelow30_triggeredWithRichConditionResult() {
        double[] closes = closesForRsiBelow30();
        int i = 15;

        EvalResult result = evaluator.evalEntry(rsiLt30Group(), closes, null, i);

        assertThat(result.triggered()).isTrue();
        assertThat(result.conditions()).hasSize(1);

        EvalResult.ConditionResult cr = result.conditions().get(0);
        assertThat(cr.leftLabel()).isEqualTo("RSI(14)");
        assertThat(cr.leftValue()).isCloseTo(28.57, within(1.0)); // RSI ≈ 28.57
        assertThat(cr.op()).isEqualTo("<");
        assertThat(cr.rightLabel()).isEqualTo("30.0");
        assertThat(cr.rightValue()).isEqualTo(30.0);
        assertThat(cr.passed()).isTrue();
        assertThat(cr.expr()).contains("RSI(14)").contains("<");

        // exitReason 과 exitPct 는 진입 시 null
        assertThat(result.exitReason()).isNull();
        assertThat(result.exitPct()).isNull();
    }

    // -----------------------------------------------------------------------
    // evalEntry: 다중 조건(AND) — 개별 passed 보존, 그룹 false
    // -----------------------------------------------------------------------

    @Test
    void evalEntry_andGroup_oneFailsOnePasses_individualPassedPreserved() {
        double[] closes = closesForRsiBelow30();
        int i = 15;

        EvalResult result = evaluator.evalEntry(twoConditionAnd(), closes, null, i);

        // AND 그룹이므로 전체 triggered = false (PRICE > 5000 미충족, price≈994)
        assertThat(result.triggered()).isFalse();
        assertThat(result.conditions()).hasSize(2);

        EvalResult.ConditionResult rsiCr = result.conditions().get(0);
        EvalResult.ConditionResult priceCr = result.conditions().get(1);

        // RSI 조건은 개별로 충족
        assertThat(rsiCr.passed()).isTrue();
        // PRICE > 5000 은 미충족
        assertThat(priceCr.passed()).isFalse();
        assertThat(priceCr.leftLabel()).isEqualTo("PRICE");
    }

    // -----------------------------------------------------------------------
    // evalExit: 익절(TAKE_PROFIT)
    // -----------------------------------------------------------------------

    @Test
    void evalExit_takeProfitReached_exitReasonTakeProfit() {
        // entryPrice=1000, 현재가=1060 → +6% ≥ takeProfitPct(5%)
        double[] closes = {1060.0};
        double entryPrice = 1000.0;

        EvalResult result = evaluator.evalExit(tpSlExit(), closes, null, 0, entryPrice);

        assertThat(result.triggered()).isTrue();
        assertThat(result.exitReason()).isEqualTo(ExitReason.TAKE_PROFIT);
        assertThat(result.exitPct()).isNotNull();
        assertThat(result.exitPct()).isCloseTo(6.0, within(0.01));
    }

    // -----------------------------------------------------------------------
    // evalExit: 손절(STOP_LOSS)
    // -----------------------------------------------------------------------

    @Test
    void evalExit_stopLossReached_exitReasonStopLoss() {
        // entryPrice=1000, 현재가=965 → -3.5% ≤ stopLossPct(-3%)
        double[] closes = {965.0};
        double entryPrice = 1000.0;

        EvalResult result = evaluator.evalExit(tpSlExit(), closes, null, 0, entryPrice);

        assertThat(result.triggered()).isTrue();
        assertThat(result.exitReason()).isEqualTo(ExitReason.STOP_LOSS);
        assertThat(result.exitPct()).isNotNull();
        assertThat(result.exitPct()).isCloseTo(-3.5, within(0.01));
    }

    // -----------------------------------------------------------------------
    // evalExit: 지표 조건(INDICATOR) — TP/SL 미충족 + 지표 조건 충족
    // -----------------------------------------------------------------------

    @Test
    void evalExit_indicatorCondition_exitReasonIndicator() {
        // PRICE=975 < 980 → 지표 청산 조건 충족, TP/SL 없음
        double[] closes = {975.0};
        double entryPrice = 1000.0;

        EvalResult result = evaluator.evalExit(indicatorExit(), closes, null, 0, entryPrice);

        assertThat(result.triggered()).isTrue();
        assertThat(result.exitReason()).isEqualTo(ExitReason.INDICATOR);
        assertThat(result.exitPct()).isNull();
    }

    // -----------------------------------------------------------------------
    // evalExit: TP/SL 미충족 + 지표 미충족 → triggered=false
    // -----------------------------------------------------------------------

    @Test
    void evalExit_nothingTriggered_returnsFalse() {
        // PRICE=1020 → TP(+5%) 미충족, PRICE < 980 미충족
        double[] closes = {1020.0};
        double entryPrice = 1000.0;

        ExitSpec exit = new ExitSpec("AND", List.of(
            new Condition(new Operand("PRICE", null, null), "<", new Operand(null, 980.0, null))
        ), 5.0, -3.0);

        EvalResult result = evaluator.evalExit(exit, closes, null, 0, entryPrice);

        assertThat(result.triggered()).isFalse();
        assertThat(result.exitReason()).isNull();
    }

    // -----------------------------------------------------------------------
    // crossAbove: ConditionResult에 현재 인덱스 값 기록, expr에 crossAbove 포함
    // -----------------------------------------------------------------------

    @Test
    void evalEntry_crossAboveCondition_recordsCurrentIndexValues() {
        // EMA(9) crossAbove SMA(20): 최소 21개 봉 + 이전 봉이 필요
        // 간단한 데이터: 처음 20개는 상수(100), 이후 급등으로 EMA가 SMA를 상향 돌파
        double[] closes = new double[22];
        for (int k = 0; k < 20; k++) {
            closes[k] = 100.0;
        }
        closes[20] = 100.0;  // i=20: EMA(9)≈SMA(20)≈100, 아직 크로스 없음
        closes[21] = 200.0;  // i=21: EMA(9) 급등, SMA(20)은 천천히 상승 → crossAbove 가능

        ConditionGroup group = crossAboveGroup();
        EvalResult result = evaluator.evalEntry(group, closes, null, 21);

        assertThat(result.conditions()).hasSize(1);
        EvalResult.ConditionResult cr = result.conditions().get(0);

        // expr에 crossAbove 포함
        assertThat(cr.expr()).containsIgnoringCase("crossAbove");
        // leftLabel, rightLabel 존재
        assertThat(cr.leftLabel()).isNotBlank();
        assertThat(cr.rightLabel()).isNotBlank();
        // leftValue, rightValue: 현재 인덱스(i=21) 값
        assertThat(cr.leftValue()).isGreaterThan(0);
        assertThat(cr.rightValue()).isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // 하위 호환: entryTriggered == evalEntry().triggered()
    // -----------------------------------------------------------------------

    @Test
    void backwardCompat_entryTriggered_matchesEvalEntryTriggered() {
        double[] closes = closesForRsiBelow30();
        int i = 15;
        ConditionGroup group = rsiLt30Group();

        boolean legacy = evaluator.entryTriggered(group, closes, null, i);
        boolean rich = evaluator.evalEntry(group, closes, null, i).triggered();

        assertThat(rich).isEqualTo(legacy);
    }

    @Test
    void backwardCompat_exitTriggered_matchesEvalExitTriggered() {
        double[] closes = {1060.0};
        double entryPrice = 1000.0;
        ExitSpec exit = tpSlExit();

        boolean legacy = evaluator.exitTriggered(exit, closes, null, 0, entryPrice);
        boolean rich = evaluator.evalExit(exit, closes, null, 0, entryPrice).triggered();

        assertThat(rich).isEqualTo(legacy);
    }
}
