package com.graphify.market;

import com.graphify.trading.paper.LiveEvaluationService;
import com.graphify.trading.paper.VolumeRankRefresher;
import com.graphify.trading.rule.PaperLiveSymbolService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KRX 장 중(09:00–15:30 KST, 거래일) 5분마다 PAPER_LIVE 활성 종목 분봉 수집.
 * ShedLock으로 다중 인스턴스 이중 실행 방지 (LIVE-03).
 *
 * 설계 원칙:
 * - 얇은 어댑터 레이어 — 비즈니스 로직은 서비스로 위임
 * - 15:30 이후 guard (cron "9-15"는 15:00–15:59를 포함하므로 내부 절사 필요)
 * - @Transactional 없음 — 서비스 레이어에서 트랜잭션 관리 (@SchedulerLock이 외부 래퍼)
 */
@Component
public class LiveDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(LiveDataScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);
    // Yahoo 5분봉(라이브 시세 소스)이 KRX 대비 ~15분 지연되므로 경고 임계도 25분으로
    // 맞춘다 (LiveEvaluationService.STALENESS_MINUTES와 동일, ISSUE-2).
    private static final long STALENESS_MINUTES = 25L;

    private final KrxMarketCalendar calendar;
    private final PaperLiveSymbolService symbolService;
    private final MarketDataIngestionService ingestionService;
    private final MarketBarIntradayRepository intradayRepository;
    private final LiveEvaluationService evaluationService;
    private final VolumeRankRefresher volumeRankRefresher;

    public LiveDataScheduler(
            KrxMarketCalendar calendar,
            PaperLiveSymbolService symbolService,
            MarketDataIngestionService ingestionService,
            MarketBarIntradayRepository intradayRepository,
            LiveEvaluationService evaluationService,
            VolumeRankRefresher volumeRankRefresher) {
        this.calendar = calendar;
        this.symbolService = symbolService;
        this.ingestionService = ingestionService;
        this.intradayRepository = intradayRepository;
        this.evaluationService = evaluationService;
        this.volumeRankRefresher = volumeRankRefresher;
    }

    /**
     * 5분마다 실행 (KST 기준 월~금 09:00–15:59). 15:30 이후는 내부 guard로 절사.
     * zone = "Asia/Seoul": 서버 TZ와 무관하게 KST 기준 평가.
     * lockAtMostFor = "4m": 틱 간격(5m)보다 짧아 이전 잠금이 다음 틱을 막지 않음.
     * lockAtLeastFor = "1m": 짧은 실행도 최소 1분 잠금 유지 (빠른 중복 트리거 방지).
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Seoul")
    @SchedulerLock(name = "liveDataIngestion", lockAtMostFor = "4m", lockAtLeastFor = "1m")
    public void collectLiveData() {
        ZonedDateTime now = ZonedDateTime.now(KST);

        // Guard 1: 15:30 이후 절사 (cron은 15:55까지 실행)
        if (now.toLocalTime().isAfter(MARKET_CLOSE)) {
            log.debug("Market closed (after 15:30 KST), skipping tick at {}", now.toLocalTime());
            return;
        }

        // Guard 2: 비거래일(주말/공휴일) 스킵
        if (!calendar.isTradingDay(now.toLocalDate())) {
            log.debug("Non-trading day {}, skipping tick", now.toLocalDate());
            return;
        }

        // volume_top_n 룰 재선정 + 보유 포지션 union (SC3/SC4) — activeSymbolsUnion() 직전에 갱신
        volumeRankRefresher.refreshIfVolumeTopN(now.toLocalDate());

        // Guard 3: 활성 종목 없으면 스킵
        Set<String> symbols = symbolService.activeSymbolsUnion();
        if (symbols.isEmpty()) {
            log.debug("No active PAPER_LIVE symbols, skipping tick");
            return;
        }

        int ingested = 0;
        for (String symbol : symbols) {
            ingestionService.ingestIntraday(symbol, "5m", "1d");
            checkStaleness(symbol, now.toInstant());
            ingested++;
        }
        log.info("Live intraday collection done: {} symbols at {}", ingested, now);

        // Phase 3: evaluate all PAPER_LIVE rules against freshly ingested bars
        evaluationService.evaluateTick(now.toInstant());
    }

    /**
     * 수집 후 최신 봉 타임스탬프 확인. STALENESS_MINUTES(25분) 이상 오래됐으면 WARNING (LIVE-04).
     * 경고 발생 시 해당 틱 평가는 Phase 3 평가 엔진이 staleness를 재확인하여 건너뜀.
     */
    private void checkStaleness(String symbol, Instant now) {
        Instant threshold = now.minus(STALENESS_MINUTES, ChronoUnit.MINUTES);
        Optional<Instant> maxTs = intradayRepository.findMaxTsBySymbolAndInterval(symbol, "5m");
        maxTs.filter(ts -> ts.isBefore(threshold))
            .ifPresent(ts -> log.warn(
                "STALE data for {}: latest bar at {} is >{}m old — skipping evaluation for this symbol",
                symbol, ts, STALENESS_MINUTES));
    }
}
