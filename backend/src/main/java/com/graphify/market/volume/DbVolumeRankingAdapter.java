package com.graphify.market.volume;

import com.graphify.market.MarketBarRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 백테스트용 VolumeRankingProvider 구현체 — market_bars 일봉 기준 거래량 상위 N 반환.
 *
 * <p>NOT @Primary — 라이브 어댑터(KrxMdcVolumeAdapter 또는 Yahoo fallback)와 공존.
 * 백테스트 엔진에서 @Qualifier("dbVolumeRankingAdapter")로 명시 주입 예정.</p>
 *
 * <p>excludeEtf 파라미터는 JPQL이 이미 instrument_type='COMMON_STOCK'으로 필터하므로 무시.</p>
 */
@Component
public class DbVolumeRankingAdapter implements VolumeRankingProvider {

    private final MarketBarRepository repo;

    public DbVolumeRankingAdapter(MarketBarRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<String> topVolume(String market, LocalDate date, int topN, boolean excludeEtf) {
        String mkt = (market != null) ? market : "KOSPI";
        return repo.findTopVolumeByMarketOnDate(mkt, date, PageRequest.of(0, topN));
    }
}
