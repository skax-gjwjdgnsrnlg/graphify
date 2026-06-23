package com.graphify.trading.engine;

import java.time.LocalDate;

/**
 * 엔진이 평가하는 단일 봉. 현재 데이터 소스(Yahoo 일봉)는 종가만 제공하므로
 * volume 은 null 일 수 있다(VOLUME 지표는 데이터가 있을 때만 평가).
 */
public record Bar(LocalDate date, double close, Double volume) {

    public static Bar ofClose(LocalDate date, double close) {
        return new Bar(date, close, null);
    }
}
