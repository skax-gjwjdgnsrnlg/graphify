package com.graphify.trading.paper;

import com.graphify.common.exception.GraphifyException;
import com.graphify.company.Company;
import com.graphify.company.CompanyRepository;
import com.graphify.market.KrxMarketCalendar;
import com.graphify.market.MarketDataIngestionService;
import com.graphify.market.volume.VolumeRankingProvider;
import com.graphify.trading.rule.PaperLiveSymbolService;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import com.graphify.trading.rule.definition.RuleDefinition;
import com.graphify.trading.rule.dto.RuleResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 룰 생애주기 상태 머신 (Phase 6.5: 2축 상태 모델).
 * config_status: DRAFT ↔ ACTIVE  (설정축)
 * run_status:    STOPPED ↔ RUNNING (운영축)
 *
 * 전이 규칙:
 *   activate:   DRAFT → ACTIVE (백테스트 게이트 없음)
 *   deactivate: ACTIVE/STOPPED → DRAFT (RUNNING이면 차단)
 *   start:      ACTIVE/STOPPED → ACTIVE/RUNNING (paperLiveSymbols 재할당)
 *   stop:       ACTIVE/RUNNING → ACTIVE/STOPPED (config ACTIVE 유지)
 *   copy:        모든 상태 → DRAFT 복사본 생성 (변경 없음)
 */
@Service
@Transactional
public class PaperLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PaperLifecycleService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 시작 시 동기 수집 상한. 초과 시(예: KOSPI200 폴백) 스케줄러 틱에 위임. */
    private static final int EAGER_INGEST_LIMIT = 30;

    private final TradingRuleRepository ruleRepo;
    private final ObjectMapper objectMapper;
    private final PaperLiveSymbolService paperLiveSymbolService;
    private final CompanyRepository companyRepo;
    private final VolumeRankingProvider liveRanking;
    private final MarketDataIngestionService ingestionService;
    private final PaperRunRepository runRepo;
    private final KrxMarketCalendar marketCalendar;

    public PaperLifecycleService(
            TradingRuleRepository ruleRepo,
            ObjectMapper objectMapper,
            PaperLiveSymbolService paperLiveSymbolService,
            CompanyRepository companyRepo,
            @Qualifier("naverTradingValueRankingAdapter") VolumeRankingProvider liveRanking,
            MarketDataIngestionService ingestionService,
            PaperRunRepository runRepo,
            KrxMarketCalendar marketCalendar) {
        this.ruleRepo = ruleRepo;
        this.objectMapper = objectMapper;
        this.paperLiveSymbolService = paperLiveSymbolService;
        this.companyRepo = companyRepo;
        this.liveRanking = liveRanking;
        this.ingestionService = ingestionService;
        this.runRepo = runRepo;
        this.marketCalendar = marketCalendar;
    }

    // ─── 신규 2축 메서드 ────────────────────────────────────────────────────────

    /** config축: DRAFT → ACTIVE. 백테스트 게이트 없음 (RULE-01 폐지). */
    public RuleResponse activate(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"DRAFT".equals(rule.getConfigStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_001",
                "DRAFT 상태인 룰만 ACTIVE로 전환할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        rule.setConfigStatus("ACTIVE");
        return toResponse(ruleRepo.save(rule));
    }

    /** config축: ACTIVE/STOPPED → DRAFT. RUNNING이면 먼저 stop 필요. */
    public RuleResponse deactivate(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"ACTIVE".equals(rule.getConfigStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_001",
                "ACTIVE 상태인 룰만 DRAFT로 하향할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if ("RUNNING".equals(rule.getRunStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_006",
                "RUNNING 중인 룰은 하향할 수 없습니다. 먼저 중지하세요.", HttpStatus.BAD_REQUEST);
        }
        rule.setConfigStatus("DRAFT");
        return toResponse(ruleRepo.save(rule));
    }

    /** run축: ACTIVE/STOPPED → ACTIVE/RUNNING. paperLiveSymbols 재할당. (override 없음 호환용) */
    public RuleResponse start(Long userId, Long ruleId) {
        return start(userId, ruleId, null);
    }

    /**
     * run축: ACTIVE/STOPPED → ACTIVE/RUNNING. paperLiveSymbols 재할당.
     * overrideSymbols가 비어있지 않으면 유니버스 해석(resolveSymbols) 대신 사용한다 —
     * 실시간 거래대금 랭킹을 못 가져올 때 사용자가 직접 선택한 종목으로 시작.
     */
    public RuleResponse start(Long userId, Long ruleId, List<String> overrideSymbols) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"ACTIVE".equals(rule.getConfigStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_007",
                "ACTIVE 상태인 룰만 시작할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if ("RUNNING".equals(rule.getRunStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_008",
                "이미 RUNNING 상태인 룰입니다.", HttpStatus.BAD_REQUEST);
        }
        if (!marketCalendar.isOperatingWindowOpen(ZonedDateTime.now(KST))) {
            throw new GraphifyException("ERR_MARKET_CLOSED",
                "현재는 폐장입니다. 평일 09:00–18:00에만 전략을 시작할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        List<String> override = normalizeOverride(overrideSymbols);
        List<String> symbols = override != null ? override : resolveSymbols(rule);
        if (symbols.isEmpty()) {
            throw new GraphifyException("ERR_LIFECYCLE_005",
                "실시간 거래대금 순위를 가져오지 못했습니다. 종목을 직접 선택하세요.", HttpStatus.BAD_REQUEST);
        }
        eagerIngest(symbols);
        rule.setRunStatus("RUNNING");
        TradingRule saved = ruleRepo.save(rule);
        paperLiveSymbolService.assignSymbols(saved.getId(), symbols);
        String universeJson = serializeSymbols(symbols);
        runRepo.save(new PaperRun(saved.getId(), userId, Instant.now(), universeJson));
        return toResponse(saved);
    }

    /**
     * 시작 시점에 선정된 종목의 시세(일봉·5분봉)를 즉시 수집해 market_bars(_intraday)에 upsert한다.
     * 첫 평가 틱이 즉시 가능하도록 동기 수집. 각 종목은 REQUIRES_NEW 트랜잭션으로 격리되어
     * 한 종목 실패(예: 외부 API 오류)가 start() 트랜잭션을 오염시키지 않는다 — 실패는 로깅 후 continue.
     * 후보 수가 많으면(예: KOSPI200 폴백) 동기 수집을 건너뛰고 스케줄러 틱의 self-healing에 맡긴다.
     */
    private void eagerIngest(List<String> symbols) {
        if (symbols.size() > EAGER_INGEST_LIMIT) {
            log.info("Eager ingest skipped: {} symbols exceed limit {} — deferring to scheduler tick",
                symbols.size(), EAGER_INGEST_LIMIT);
            return;
        }
        for (String symbol : symbols) {
            try {
                ingestionService.ingestDailyInNewTx(symbol);
                ingestionService.ingestIntradayInNewTx(symbol, "5m", "1d");
            } catch (Exception e) {
                log.warn("Eager ingest failed for {}: {}", symbol, e.getMessage());
            }
        }
    }

    /** run축: ACTIVE/RUNNING → ACTIVE/STOPPED. config ACTIVE 유지 (PAUSED 없음). */
    public RuleResponse stop(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"RUNNING".equals(rule.getRunStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_009",
                "RUNNING 상태인 룰만 중지할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        rule.setRunStatus("STOPPED");
        TradingRule saved = ruleRepo.save(rule);
        paperLiveSymbolService.deactivateRule(saved.getId());
        runRepo.findFirstByRuleIdAndStatus(saved.getId(), "RUNNING")
            .ifPresent(run -> { run.stop(Instant.now()); runRepo.save(run); });
        return toResponse(saved);
    }

    /** 폐장 자동 중지: RUNNING 중인 모든 PAPER 룰을 STOPPED로 전환하고 열린 run을 종료. 중지된 룰 수 반환. */
    public int stopAllRunningForMarketClose() {
        List<TradingRule> running = ruleRepo.findByModeAndRunStatus("PAPER", "RUNNING");
        Instant nowTs = Instant.now();
        for (TradingRule rule : running) {
            rule.setRunStatus("STOPPED");
            ruleRepo.save(rule);
            paperLiveSymbolService.deactivateRule(rule.getId());
            runRepo.findFirstByRuleIdAndStatus(rule.getId(), "RUNNING")
                .ifPresent(run -> { run.stop(nowTs); runRepo.save(run); });
        }
        return running.size();
    }

    /**
     * 전략 운영 화면용: configStatus=ACTIVE 룰만 반환 (DRAFT 제외).
     * run 배지(RUNNING/STOPPED) + 시작/중지 버튼 노출을 위한 목록.
     */
    @Transactional(readOnly = true)
    public List<RuleResponse> listActive(Long userId) {
        return ruleRepo.findByUserIdAndModeOrderByUpdatedAtDesc(userId, "PAPER")
            .stream()
            .filter(r -> "ACTIVE".equals(r.getConfigStatus()))
            .map(this::toResponse)
            .toList();
    }

    // ─── 기존 메서드 (deprecated — 신규 메서드로 위임) ──────────────────────────

    /**
     * @deprecated promote → activate 사용 권장.
     */
    @Deprecated
    public RuleResponse promote(Long userId, Long ruleId) {
        return activate(userId, ruleId);
    }

    /**
     * @deprecated pause → stop 사용 권장.
     */
    @Deprecated
    public RuleResponse pause(Long userId, Long ruleId) {
        return stop(userId, ruleId);
    }

    /**
     * @deprecated resume → start 사용 권장.
     */
    @Deprecated
    public RuleResponse resume(Long userId, Long ruleId) {
        return start(userId, ruleId);
    }

    /** 모든 상태 → DRAFT 복사본 생성. 원본 symbols는 유지 (copy는 비파괴적). */
    public RuleResponse copy(Long userId, Long ruleId) {
        TradingRule original = findOwned(userId, ruleId);
        TradingRule copy = new TradingRule(
            userId,
            "복사본 - " + original.getName(),
            original.getMode(),
            "DRAFT",
            original.getDefinition()
        );
        return toResponse(ruleRepo.save(copy));
    }

    /**
     * 룰의 유니버스 정의로부터 종목 목록을 결정한다. mode/status 무관 (SEAM 3).
     * Phase 8 LIVE 승격도 이 메서드를 재사용한다.
     *
     * volume_top_n: 라이브 거래대금 상위 topN(VolumeRankingProvider) ∪ additionalSymbols 반환.
     *   VolumeRankRefresher와 동일 소스(naver)로 선정해 start와 틱 재선정이 일관된다(SC5).
     *   라이브 랭킹이 비면(장외/조회 실패) companies(in_kospi200) 후보군으로 폴백 —
     *   장외에도 시작 가능하고, 실제 top-N 재선정은 다음 장중 틱에서 보정된다.
     * symbols/watchlist: u.symbols() 그대로 반환
     */
    /** symbols 목록을 JSON 문자열로 직렬화한다. 실패 시 null 반환(universe_snapshot 컬럼은 nullable). */
    private String serializeSymbols(List<String> symbols) {
        try {
            return objectMapper.writeValueAsString(symbols);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize universe snapshot: {}", e.getMessage());
            return null;
        }
    }

    /** overrideSymbols를 정규화한다. 비어있으면 null(기존 유니버스 해석). */
    private List<String> normalizeOverride(List<String> overrideSymbols) {
        if (overrideSymbols == null || overrideSymbols.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String s : overrideSymbols) {
            if (s != null && !s.isBlank()) {
                cleaned.add(s.trim());
            }
        }
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }

    private List<String> resolveSymbols(TradingRule rule) {
        try {
            RuleDefinition def = objectMapper.readValue(rule.getDefinition(), RuleDefinition.class);
            RuleDefinition.Universe u = def.universe();
            if (u == null) return List.of();
            if ("volume_top_n".equals(u.type())) {
                String market = u.market() != null ? u.market() : "KOSPI";
                int topN = u.topN() != null ? u.topN() : 10;
                LinkedHashSet<String> all = new LinkedHashSet<>(resolveLiveTopN(market, topN));
                if (u.additionalSymbols() != null) {
                    all.addAll(u.additionalSymbols());
                }
                return List.copyOf(all);
            }
            // type="symbols" or "watchlist"
            return u.symbols() != null ? u.symbols() : List.of();
        } catch (Exception e) {
            log.warn("Cannot parse rule definition for rule {}: {}", rule.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 라이브 거래대금 상위 topN 티커를 반환한다. 조회 실패/빈 응답(장외 등) 시
     * companies(in_kospi200=true) 후보군으로 폴백 — 장외에도 시작은 되게 하고
     * 실제 top-N 재선정은 다음 장중 틱(VolumeRankRefresher)에서 보정한다.
     */
    private List<String> resolveLiveTopN(String market, int topN) {
        try {
            List<String> top = liveRanking.topVolume(market, LocalDate.now(KST), topN, true);
            if (top != null && !top.isEmpty()) {
                return top;
            }
        } catch (Exception e) {
            log.warn("Live volume ranking failed for {} — falling back to KOSPI200 pool: {}",
                market, e.getMessage());
        }
        return kospi200Pool();
    }

    /** companies 테이블의 in_kospi200=true 종목 티커 (폴백 후보군). */
    private List<String> kospi200Pool() {
        LinkedHashSet<String> pool = new LinkedHashSet<>();
        for (Company c : companyRepo.findByInKospi200True()) {
            if (c.getTicker() != null && !c.getTicker().isBlank()) {
                pool.add(c.getTicker().trim());
            }
        }
        return List.copyOf(pool);
    }

    private TradingRule findOwned(Long userId, Long ruleId) {
        return ruleRepo.findByIdAndUserId(ruleId, userId)
            .orElseThrow(() -> new GraphifyException(
                "ERR_RULE_002", "룰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private RuleResponse toResponse(TradingRule rule) {
        com.fasterxml.jackson.databind.JsonNode definition;
        try {
            definition = objectMapper.readTree(rule.getDefinition());
        } catch (JsonProcessingException e) {
            definition = objectMapper.createObjectNode();
        }
        return new RuleResponse(
            rule.getId(), rule.getName(), rule.getMode(), rule.getStatus(),
            rule.isBacktested(), definition, rule.getPromotedFrom(),
            rule.getCreatedAt(), rule.getUpdatedAt(),
            rule.getConfigStatus(), rule.getRunStatus()
        );
    }
}
