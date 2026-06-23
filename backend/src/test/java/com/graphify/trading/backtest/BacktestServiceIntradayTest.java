package com.graphify.trading.backtest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.trading.backtest.dto.BacktestResult;
import com.graphify.trading.engine.EvalResult;
import com.graphify.trading.engine.EvalResult.ConditionResult;
import com.graphify.trading.engine.EvalResult.ExitReason;
import com.graphify.trading.engine.FillSimulator;
import com.graphify.trading.engine.PaperLedger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Phase 1 인트라데이 백테스트 엔진 단위 테스트.
 * IntradayBacktestEngine의 package-private static helpers를 직접 호출.
 */
class BacktestServiceIntradayTest {

    @Test
    void testDrawdownSegments() {
        // CHART-02: 드로우다운 구간 계산 정확성
        // Equity curve: 100 → 90 (dd1 start) → 80 → 100 (dd1 end/recovery) → 70 (dd2 start)
        List<BacktestResult.EquityPoint> curve = List.of(
            new BacktestResult.EquityPoint(LocalDateTime.of(2026, 1, 2, 9, 0),  100.0),
            new BacktestResult.EquityPoint(LocalDateTime.of(2026, 1, 2, 9, 5),   90.0),
            new BacktestResult.EquityPoint(LocalDateTime.of(2026, 1, 2, 9, 10),  80.0),
            new BacktestResult.EquityPoint(LocalDateTime.of(2026, 1, 2, 9, 15), 100.0),
            new BacktestResult.EquityPoint(LocalDateTime.of(2026, 1, 2, 9, 20),  70.0)
        );

        List<BacktestResult.DrawdownSegment> segments =
                IntradayBacktestEngine.computeDrawdownSegments(curve);

        assertEquals(2, segments.size(), "Expected 2 drawdown segments");
        // Segment 1: starts at 9:05 (first dip below peak 100), ends at 9:15 (recovery to 100)
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 5),  segments.get(0).start());
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 15), segments.get(0).end());
        // Segment 2: starts at 9:20 (new low), never recovers → ends at last bar
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 20), segments.get(1).start());
    }

    @Test
    void testStatsCalculation() {
        // CHART-03: Sharpe/Sortino/Profit Factor 계산값 검증
        // Build a curve with strictly increasing equity (all positive returns → sharpe > 0)
        List<BacktestResult.EquityPoint> curve = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 2, 9, 0);
        for (int i = 0; i < 10; i++) {
            curve.add(new BacktestResult.EquityPoint(
                    base.plusMinutes(5L * i),
                    10_000_000.0 + i * 10_000.0));
        }

        // A single profitable SELL trade
        List<BacktestResult.TradeDto> trades = List.of(
            new BacktestResult.TradeDto(
                    base.plusMinutes(5), "005930.KS", null, "SELL", 1.0, 75000.0, 5000.0, null)
        );

        double sharpe   = IntradayBacktestEngine.computeSharpeRatio(curve);
        double sortino  = IntradayBacktestEngine.computeSortinoRatio(curve);
        double pf       = IntradayBacktestEngine.computeProfitFactor(trades);

        assertTrue(sharpe > 0,  "Sharpe must be positive for monotonically increasing equity");
        assertTrue(sortino >= 0, "Sortino must be >= 0 (no downside returns in this curve)");
        assertTrue(pf > 0,      "PF must be positive when only profit trades exist");
    }

    // ── RULE-09: rationaleJson carrier tests ───────────────────────────────────

    /**
     * PaperLedger.buy() with rationaleJson → TradeRecord.rationaleJson() non-null.
     * TradeDto constructed with rationaleJson → parseable JSON with side=BUY + conditions.
     */
    @Test
    void buyTradeRecord_rationaleJson_isPreservedAndParseable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Build a minimal BUY rationale JSON matching RESEARCH Discretion 2 schema
        ConditionResult cond = new ConditionResult(
                "RSI(14) < 30", "RSI(14)", 28.57, "<", "30.0", 30.0, true);
        EvalResult entryResult = new EvalResult(true, List.of(cond), null, null);

        String rationaleJson = buildRationaleJson(mapper, entryResult, "BUY");

        // Carrier test: PaperLedger preserves rationaleJson through buy()
        FillSimulator fill = new FillSimulator();
        PaperLedger ledger = new PaperLedger(10_000_000.0, fill);
        LocalDate date = LocalDate.of(2026, 1, 2);
        ledger.buy(date.atStartOfDay(), "005930.KS", 10.0, 75000.0, rationaleJson);

        PaperLedger.TradeRecord record = ledger.trades().get(0);
        assertNotNull(record.rationaleJson(), "TradeRecord.rationaleJson must be non-null after buy()");
        assertEquals(rationaleJson, record.rationaleJson());

        // TradeDto carrier test: rationaleJson survives mapping to DTO
        LocalDateTime dt = date.atStartOfDay();
        BacktestResult.TradeDto dto = new BacktestResult.TradeDto(
                dt, "005930.KS", null, "BUY", 10.0, 75000.0, null, record.rationaleJson());
        assertNotNull(dto.rationaleJson(), "TradeDto.rationaleJson must be non-null");

        // JSON parse test: side=BUY, conditions array has one entry with expr/leftValue/passed
        JsonNode root = mapper.readTree(dto.rationaleJson());
        assertEquals("BUY", root.get("side").asText());
        assertNull(root.get("exitReason").isNull() ? null : root.get("exitReason"));
        JsonNode conditions = root.get("conditions");
        assertNotNull(conditions);
        assertTrue(conditions.isArray() && conditions.size() == 1,
                "conditions must be array with 1 element");
        JsonNode c0 = conditions.get(0);
        assertEquals("RSI(14) < 30", c0.get("expr").asText());
        assertEquals(28.57, c0.get("leftValue").asDouble(), 0.01);
        assertTrue(c0.get("passed").asBoolean());
    }

    /**
     * PaperLedger.sell() with SELL rationaleJson → exitReason is STOP_LOSS or TAKE_PROFIT.
     */
    @Test
    void sellTradeRecord_rationaleJson_containsExitReason() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // STOP_LOSS scenario: exitReason=STOP_LOSS, exitPct=-3.5, conditions=[]
        EvalResult exitResult = new EvalResult(true, List.of(), ExitReason.STOP_LOSS, -3.5);
        String rationaleJson = buildRationaleJson(mapper, exitResult, "SELL");

        FillSimulator fill = new FillSimulator();
        PaperLedger ledger = new PaperLedger(10_000_000.0, fill);
        LocalDate date = LocalDate.of(2026, 1, 2);
        // Buy first so we can sell
        ledger.buy(date.atStartOfDay(), "005930.KS", 10.0, 70000.0, null);
        ledger.sell(date.atStartOfDay(), "005930.KS", 72000.0, rationaleJson);

        // Find the SELL record
        PaperLedger.TradeRecord sellRecord = ledger.trades().stream()
                .filter(r -> "SELL".equals(r.side()))
                .findFirst()
                .orElseThrow();

        assertNotNull(sellRecord.rationaleJson(), "SELL TradeRecord.rationaleJson must be non-null");

        JsonNode root = mapper.readTree(sellRecord.rationaleJson());
        assertEquals("SELL", root.get("side").asText());
        assertEquals("STOP_LOSS", root.get("exitReason").asText(),
                "exitReason must be STOP_LOSS");
        assertEquals(-3.5, root.get("exitPct").asDouble(), 0.001);
        assertTrue(root.get("conditions").isArray());
        assertEquals(0, root.get("conditions").size(), "TP/SL conditions list must be empty");
    }

    /**
     * INDICATOR exit: rationaleJson has exitReason=INDICATOR and conditions non-empty.
     */
    @Test
    void sellTradeRecord_rationaleJson_indicatorExitReason_hasConditions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ConditionResult exitCond = new ConditionResult(
                "PRICE < 980.0", "PRICE", 975.0, "<", "980.0", 980.0, true);
        EvalResult exitResult = new EvalResult(true, List.of(exitCond), ExitReason.INDICATOR, null);
        String rationaleJson = buildRationaleJson(mapper, exitResult, "SELL");

        FillSimulator fill = new FillSimulator();
        PaperLedger ledger = new PaperLedger(10_000_000.0, fill);
        LocalDate date = LocalDate.of(2026, 1, 2);
        ledger.buy(date.atStartOfDay(), "005930.KS", 10.0, 990.0, null);
        ledger.sell(date.atStartOfDay(), "005930.KS", 975.0, rationaleJson);

        PaperLedger.TradeRecord sellRecord = ledger.trades().stream()
                .filter(r -> "SELL".equals(r.side()))
                .findFirst()
                .orElseThrow();

        JsonNode root = mapper.readTree(sellRecord.rationaleJson());
        assertEquals("INDICATOR", root.get("exitReason").asText());
        assertTrue(root.get("exitPct").isNull(), "exitPct must be null for INDICATOR exits");
        JsonNode conditions = root.get("conditions");
        assertEquals(1, conditions.size());
        assertEquals("PRICE < 980.0", conditions.get(0).get("expr").asText());
    }

    // ── Helper: replicate buildRationale logic (mirrors IntradayBacktestEngine) ─

    private static String buildRationaleJson(ObjectMapper mapper, EvalResult r, String side)
            throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("side", side);
        map.put("exitReason", r.exitReason() != null ? r.exitReason().name() : null);
        map.put("exitPct", r.exitPct());
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (ConditionResult c : r.conditions()) {
            Map<String, Object> cMap = new LinkedHashMap<>();
            cMap.put("expr", c.expr());
            cMap.put("leftLabel", c.leftLabel());
            cMap.put("leftValue", c.leftValue());
            cMap.put("op", c.op());
            cMap.put("rightLabel", c.rightLabel());
            cMap.put("rightValue", c.rightValue());
            cMap.put("passed", c.passed());
            conditions.add(cMap);
        }
        map.put("conditions", conditions);
        return mapper.writeValueAsString(map);
    }
}
