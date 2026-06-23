package com.graphify.trading.engine;

/**
 * 지표 계산 유틸. 모든 메서드는 종가 배열과 평가 인덱스 i 를 받아
 * i 시점의 지표 값을 반환한다. 데이터가 부족하면 Double.NaN.
 */
public final class Indicators {

    private Indicators() {
    }

    /** 단순이동평균: closes[i-period+1 .. i] 평균 */
    public static double sma(double[] closes, int i, int period) {
        if (period <= 0 || i < period - 1) {
            return Double.NaN;
        }
        double sum = 0;
        for (int k = i - period + 1; k <= i; k++) {
            sum += closes[k];
        }
        return sum / period;
    }

    /** 지수이동평균: SMA(period) 로 시드 후 i 까지 누적 */
    public static double ema(double[] closes, int i, int period) {
        if (period <= 0 || i < period - 1) {
            return Double.NaN;
        }
        double k = 2.0 / (period + 1);
        double ema = sma(closes, period - 1, period); // seed at first full window
        for (int t = period; t <= i; t++) {
            ema = closes[t] * k + ema * (1 - k);
        }
        return ema;
    }

    /** Wilder RSI(period) */
    public static double rsi(double[] closes, int i, int period) {
        if (period <= 0 || i < period) {
            return Double.NaN;
        }
        double gain = 0;
        double loss = 0;
        for (int t = i - period + 1; t <= i; t++) {
            double diff = closes[t] - closes[t - 1];
            if (diff >= 0) {
                gain += diff;
            } else {
                loss -= diff;
            }
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        if (avgLoss == 0) {
            return avgGain == 0 ? 50.0 : 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
