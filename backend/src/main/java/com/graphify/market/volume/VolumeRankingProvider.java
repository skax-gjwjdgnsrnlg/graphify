package com.graphify.market.volume;

import java.time.LocalDate;
import java.util.List;

/**
 * 거래량 상위 N 종목 선정 포트 계약.
 *
 * <p>구현체:</p>
 * <ul>
 *   <li>{@link DbVolumeRankingAdapter} — 백테스트: 완결 일봉(market_bars) 기준 date</li>
 *   <li>KrxMdcVolumeAdapter (Plan 02) — 라이브: 당일 누적 인트라데이 거래량 기준 (KRX MDC 또는 Yahoo fallback)</li>
 * </ul>
 *
 * <p><b>@VolumeRankingSemantics:</b> 백테스트와 라이브는 거래량 기준이 다르다.
 * 백테스트는 해당 거래일 완결 일봉 거래량, 라이브는 당일 현재 시점까지 누적 인트라데이 거래량.
 * 이 차이는 수용·문서화됨 — 백테스트 재정렬(인트라데이 스냅샷 기준)은 범위 외 (CONTEXT.md Deferred).</p>
 */
public interface VolumeRankingProvider {

    /**
     * 거래량 상위 N 티커 반환.
     *
     * @param market    시장 코드 ("KOSPI", "KOSDAQ")
     * @param date      백테스트=해당 거래일, 라이브=오늘
     * @param topN      상위 N 종목 수
     * @param excludeEtf true이면 ETF/ETN/우선주 제외 (COMMON_STOCK만 포함)
     * @return 거래량 DESC 정렬된 티커 목록 (최대 topN개)
     */
    List<String> topVolume(String market, LocalDate date, int topN, boolean excludeEtf);
}
