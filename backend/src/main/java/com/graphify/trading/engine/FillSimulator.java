package com.graphify.trading.engine;

import org.springframework.stereotype.Component;

/**
 * 신호 → 가상 체결가. MVP: 슬리피지 0, 수수료 0.015%(매수/매도 각각).
 * 체결가 자체는 기준가 그대로, 수수료는 PaperLedger 가 현금에서 차감.
 */
@Component
public class FillSimulator {

    public static final double FEE_RATE = 0.00015;

    /** 체결가(현재 기준가 = 종가). 슬리피지 도입 시 side 별 보정 추가 예정. */
    public double fillPrice(Signal side, double referencePrice) {
        return referencePrice;
    }

    public double fee(double price, double qty) {
        return Math.abs(price * qty) * FEE_RATE;
    }
}
