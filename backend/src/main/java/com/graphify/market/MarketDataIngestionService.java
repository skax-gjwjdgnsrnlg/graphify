package com.graphify.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.company.Company;
import com.graphify.company.CompanyRepository;
import com.graphify.company.market.IntradayBar;
import com.graphify.company.market.OhlcvBar;
import com.graphify.company.market.YahooFinanceChartClient;
import com.graphify.company.market.YahooSymbolResolver;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시세 적재. 룰 유니버스에 등장하는 종목만 수집해 market_bars(_intraday)에 upsert.
 * Cloud Scheduler 가 InternalMarketController 를 통해 주기별로 호출한다.
 */
@Service
public class MarketDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataIngestionService.class);

    private final TradingRuleRepository ruleRepository;
    private final CompanyRepository companyRepository;
    private final YahooFinanceChartClient yahooClient;
    private final MarketBarRepository barRepository;
    private final MarketBarIntradayRepository intradayRepository;
    private final ObjectMapper objectMapper;

    public MarketDataIngestionService(
            TradingRuleRepository ruleRepository,
            CompanyRepository companyRepository,
            YahooFinanceChartClient yahooClient,
            MarketBarRepository barRepository,
            MarketBarIntradayRepository intradayRepository,
            ObjectMapper objectMapper
    ) {
        this.ruleRepository = ruleRepository;
        this.companyRepository = companyRepository;
        this.yahooClient = yahooClient;
        this.barRepository = barRepository;
        this.intradayRepository = intradayRepository;
        this.objectMapper = objectMapper;
    }

    /** 룰에 등장하는 전 종목 일봉 적재. 적재된 종목 수 반환. */
    public int ingestDailyForActiveSymbols() {
        Set<String> symbols = activeSymbols();
        int count = 0;
        for (String symbol : symbols) {
            if (ingestDaily(symbol) > 0) {
                count++;
            }
        }
        log.info("Daily ingestion done: {} symbols", count);
        return count;
    }

    /**
     * KOSPI 200 전체 종목 일봉 적재.
     * companies 테이블의 in_kospi200=TRUE 종목을 순회하며 ingestDaily() 호출.
     * 적재된 종목 수 반환 (ingestDaily() > 0인 종목만 카운트).
     * 스케줄 자동화는 Phase 2에서 추가 예정 — 현재는 HTTP 엔드포인트로 수동 트리거.
     */
    public int ingestDailyForKospi200() {
        List<Company> kospi200 = companyRepository.findByInKospi200True();
        int count = 0;
        for (Company company : kospi200) {
            if (company.getTicker() == null) {
                log.debug("Skip KOSPI 200 company without ticker: {}", company.getId());
                continue;
            }
            if (ingestDaily(company.getTicker()) > 0) {
                count++;
            }
        }
        log.info("KOSPI 200 daily ingestion done: {} / {} symbols ingested",
                count, kospi200.size());
        return count;
    }

    /** 룰에 등장하는 전 종목 분봉 적재(interval/range). 적재된 종목 수 반환. */
    public int ingestIntradayForActiveSymbols(String interval, String range) {
        Set<String> symbols = activeSymbols();
        int count = 0;
        for (String symbol : symbols) {
            if (ingestIntraday(symbol, interval, range) > 0) {
                count++;
            }
        }
        log.info("Intraday({}) ingestion done: {} symbols", interval, count);
        return count;
    }

    /** 단일 종목 일봉 적재(없는 봉 insert, 있으면 update). 적재 행 수 반환. */
    @Transactional
    public int ingestDaily(String symbol) {
        Optional<String> yahoo = resolveYahoo(symbol);
        if (yahoo.isEmpty()) {
            return 0;
        }
        List<OhlcvBar> bars = yahooClient.fetchDailyOhlcv(yahoo.get());
        int n = 0;
        for (OhlcvBar b : bars) {
            barRepository.findBySymbolAndTradingDate(symbol, b.tradingDate())
                    .ifPresentOrElse(
                            existing -> existing.update(b.open(), b.high(), b.low(), b.close(), b.volume()),
                            () -> barRepository.save(new MarketBar(
                                    symbol, b.tradingDate(), b.open(), b.high(), b.low(),
                                    b.close(), b.volume(), "YAHOO")));
            n++;
        }
        return n;
    }

    @Transactional
    public int ingestIntraday(String symbol, String interval, String range) {
        Optional<String> yahoo = resolveYahoo(symbol);
        if (yahoo.isEmpty()) {
            return 0;
        }
        List<IntradayBar> bars = yahooClient.fetchIntraday(yahoo.get(), interval, range);
        int n = 0;
        for (IntradayBar b : bars) {
            intradayRepository.findBySymbolAndIntervalAndTs(symbol, interval, b.ts())
                    .ifPresentOrElse(
                            existing -> existing.update(b.open(), b.high(), b.low(), b.close(), b.volume()),
                            () -> intradayRepository.save(new MarketBarIntraday(
                                    symbol, b.ts(), interval, b.open(), b.high(), b.low(),
                                    b.close(), b.volume(), "YAHOO")));
            n++;
        }
        return n;
    }

    private Optional<String> resolveYahoo(String symbol) {
        Optional<Company> company = companyRepository.findByTicker(symbol);
        String market = company.map(Company::getMarket).orElse(null);
        return YahooSymbolResolver.resolve(symbol, market);
    }

    /** 모든 룰 definition 의 universe.symbols 합집합. */
    private Set<String> activeSymbols() {
        Set<String> symbols = new LinkedHashSet<>();
        for (TradingRule rule : ruleRepository.findAll()) {
            try {
                JsonNode arr = objectMapper.readTree(rule.getDefinition())
                        .path("universe").path("symbols");
                if (arr.isArray()) {
                    arr.forEach(n -> {
                        String s = n.asText(null);
                        if (s != null && !s.isBlank()) {
                            symbols.add(s.trim());
                        }
                    });
                }
            } catch (Exception e) {
                log.debug("Skip rule {} symbol parse: {}", rule.getId(), e.getMessage());
            }
        }
        return symbols;
    }
}
