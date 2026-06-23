package com.graphify.company.market;

import java.time.Instant;

/** 분봉 OHLCV. */
public record IntradayBar(
        Instant ts,
        Double open,
        Double high,
        Double low,
        double close,
        Long volume
) {
}
