package com.graphify.company.market;

import java.time.LocalDate;

/** 일봉 OHLCV. open/high/low/volume 은 소스가 제공하지 않으면 null. */
public record OhlcvBar(
        LocalDate tradingDate,
        Double open,
        Double high,
        Double low,
        double close,
        Long volume
) {
}
