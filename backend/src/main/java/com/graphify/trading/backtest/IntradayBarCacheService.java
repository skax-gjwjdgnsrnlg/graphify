package com.graphify.trading.backtest;

import com.graphify.market.MarketBarIntraday;
import com.graphify.market.MarketBarIntradayRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yahoo Finance 응답을 DB에 저장할 때 REQUIRES_NEW 트랜잭션으로 격리.
 * 중복 키 충돌 시 메인 백테스트 트랜잭션이 오염되지 않도록 한다.
 */
@Component
public class IntradayBarCacheService {

    private static final Logger log = LoggerFactory.getLogger(IntradayBarCacheService.class);

    private final MarketBarIntradayRepository intradayRepository;

    public IntradayBarCacheService(MarketBarIntradayRepository intradayRepository) {
        this.intradayRepository = intradayRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveQuietly(String symbol, LocalDate date, List<MarketBarIntraday> bars) {
        if (bars.isEmpty()) {
            return;
        }
        try {
            intradayRepository.saveAll(bars);
        } catch (Exception ex) {
            log.warn("Failed to cache intraday bars symbol={} date={}: {}", symbol, date, ex.getMessage());
        }
    }
}
