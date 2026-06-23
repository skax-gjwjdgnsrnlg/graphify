package com.graphify.trading.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 백테스트/포워드 공용 가상 장부(인메모리, 실행 단위로 생성).
 * 현금/포지션을 관리하고 체결 시 거래 이력을 누적한다.
 */
public class PaperLedger {

    public record Position(double qty, double avgPrice) {
    }

    public record TradeRecord(
            LocalDateTime datetime,
            String symbol,
            String side,
            double qty,
            double price,
            Double pnl,
            String rationaleJson
    ) {
    }

    private final FillSimulator fillSimulator;
    private double cash;
    private final Map<String, Position> positions = new HashMap<>();
    private final List<TradeRecord> trades = new ArrayList<>();

    public PaperLedger(double initialCash, FillSimulator fillSimulator) {
        this.cash = initialCash;
        this.fillSimulator = fillSimulator;
    }

    public double cash() {
        return cash;
    }

    public boolean holds(String symbol) {
        Position p = positions.get(symbol);
        return p != null && p.qty() > 0;
    }

    public Position position(String symbol) {
        return positions.get(symbol);
    }

    /** 매수: qty 주 체결(수수료 차감). 현금 부족 시 무시하고 false. */
    public boolean buy(LocalDateTime datetime, String symbol, double qty, double referencePrice) {
        return buy(datetime, symbol, qty, referencePrice, null);
    }

    /** 매수: qty 주 체결(수수료 차감). 현금 부족 시 무시하고 false. rationaleJson = 매수 근거 JSON. */
    public boolean buy(LocalDateTime datetime, String symbol, double qty, double referencePrice, String rationaleJson) {
        if (qty <= 0) {
            return false;
        }
        double price = fillSimulator.fillPrice(Signal.BUY, referencePrice);
        double cost = price * qty + fillSimulator.fee(price, qty);
        if (cost > cash) {
            return false;
        }
        cash -= cost;
        positions.put(symbol, new Position(qty, price));
        trades.add(new TradeRecord(datetime, symbol, "BUY", qty, price, null, rationaleJson));
        return true;
    }

    /** 매도(전량 청산): 실현손익 기록. */
    public boolean sell(LocalDateTime datetime, String symbol, double referencePrice) {
        return sell(datetime, symbol, referencePrice, null);
    }

    /** 매도(전량 청산): 실현손익 기록. rationaleJson = 청산 근거 JSON. */
    public boolean sell(LocalDateTime datetime, String symbol, double referencePrice, String rationaleJson) {
        Position p = positions.get(symbol);
        if (p == null || p.qty() <= 0) {
            return false;
        }
        double price = fillSimulator.fillPrice(Signal.SELL, referencePrice);
        double proceeds = price * p.qty() - fillSimulator.fee(price, p.qty());
        double pnl = proceeds - p.avgPrice() * p.qty();
        cash += proceeds;
        positions.remove(symbol);
        trades.add(new TradeRecord(datetime, symbol, "SELL", p.qty(), price, pnl, rationaleJson));
        return true;
    }

    /** 현재가 맵으로 평가한 총자산(현금 + 보유 포지션 평가액). */
    public double equity(Map<String, Double> lastPrices) {
        double eq = cash;
        for (Map.Entry<String, Position> e : positions.entrySet()) {
            Double px = lastPrices.get(e.getKey());
            if (px != null) {
                eq += px * e.getValue().qty();
            } else {
                eq += e.getValue().avgPrice() * e.getValue().qty();
            }
        }
        return eq;
    }

    public List<TradeRecord> trades() {
        return trades;
    }
}
