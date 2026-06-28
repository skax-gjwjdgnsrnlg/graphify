package com.graphify.market;

import com.graphify.trading.paper.PaperLifecycleService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 운영 창(평일 09:00–18:00, 공휴일 제외) 밖이면 RUNNING 중인 모든 PAPER 룰을 자동 중지.
 * 매시 정각 점검 — 개장 중이면 no-op, 폐장(예: 18:00)이면 일괄 중지. ShedLock으로 다중 인스턴스 이중 실행 방지.
 */
@Component
public class MarketCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketCloseScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KrxMarketCalendar calendar;
    private final PaperLifecycleService lifecycleService;

    public MarketCloseScheduler(KrxMarketCalendar calendar, PaperLifecycleService lifecycleService) {
        this.calendar = calendar;
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "marketCloseAutoStop", lockAtMostFor = "4m", lockAtLeastFor = "30s")
    public void autoStopOnClose() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        if (calendar.isOperatingWindowOpen(now)) return; // still open
        int stopped = lifecycleService.stopAllRunningForMarketClose();
        if (stopped > 0) log.info("Market closed at {} — auto-stopped {} running rule(s)", now, stopped);
    }
}
