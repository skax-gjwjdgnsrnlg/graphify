---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Phase 6.5 Plan 05 완료 — 프론트엔드 2축 역할 분리 + 매매 근거 아코디언
stopped_at: Phase 6.6 context gathered
last_updated: "2026-06-22T11:08:03.208Z"
last_activity: "2026-06-22 — 06.5-05 완료: PaperRulesPage config축, TradingRulesPage run축, TradeRationaleRow 아코디언 (tsc+build 통과)"
progress:
  total_phases: 11
  completed_phases: 8
  total_plans: 28
  completed_plans: 28
  percent: 71
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-20)

**Core value:** 룰 기반 전략을 백테스트로 검증 → 실시간 모의 실행으로 성과 확인 → 토스증권 실계좌로 승격하는 일관된 파이프라인
**Current focus:** Phase 0 — 데이터 인프라 & 동적 유니버스

## Current Position

Phase: 6.5 (룰 설정/라이프사이클 역할 분리 & 매매 근거)
Plan: 06.5-05 완료 → Phase 6.5 전체 완료 (Task 4 checkpoint:human-verify 대기)
Status: Phase 6.5 Plan 05 완료 — 프론트엔드 2축 역할 분리 + 매매 근거 아코디언
Last activity: 2026-06-22 — 06.5-05 완료: PaperRulesPage config축, TradingRulesPage run축, TradeRationaleRow 아코디언 (tsc+build 통과)

Progress: [███████░░░] 71% (8/11 phases complete, 28/28 plans complete)

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: none yet
- Trend: -

*Updated after each plan completion*
| Phase 01 P04 | 8 | 2 tasks | 1 files |
| Phase 03 P01 | 3m | 2 tasks | 15 files |
| Phase 03 P02 | 2.5m | 1 tasks | 4 files |
| Phase 04 P01 | 1.5m | 2 tasks | 7 files |
| Phase 04 P02 | 3m | 2 tasks | 9 files |
| Phase 04 P03 | 4m | 2 tasks | 6 files |
| Phase 04 P04 | 5m | 2 tasks | 4 files |
| Phase 05 P01 | 6m | 2 tasks | 8 files |
| Phase 05 P02 | 7m | 2 tasks | 8 files |
| Phase 04 P07 | 5m | 2 tasks | 6 files |
| Phase 04 P05 | 4m | 3 tasks | 6 files |
| Phase 06 P01 | 3m | 2 tasks | 2 files |
| Phase 06 P02 | 2m | 2 tasks | 2 files |
| Phase 06.5-role-split-trade-rationale P01 | 6m | 3 tasks | 9 files |
| Phase 06.5 P02 | 8m | 2 tasks | 7 files |
| Phase 06.5 P04 | 6m | 3 tasks | 7 files |
| Phase 06.5 P03 | 4m | 3 tasks | 5 files |
| Phase 06.5 P05 | 12m | 3 tasks | 7 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Phase 0, 00-01]: volumesBySymbol 타입은 Map<String, Double[]> (nullable boxed) — Bar.volume()이 Double nullable이므로 primitive double[]는 NPE 위험
- [Phase 0, 00-01]: 평가 루프에서 getOrDefault(symbol, null) 패턴 사용 — volumes 없는 봉은 null 전달해 RuleEvaluator NaN fallback 유지
- [Phase 0, 00-02]: @DataJpaTest 슬라이스에서 Flyway 비활성화 — @AutoConfigureTestDatabase(replace=ANY) + @TestPropertySource 조합 사용 (application.properties 단독으로는 슬라이스 컨텍스트에서 무시됨)
- [Phase 0, 00-02]: H2 testRuntimeOnly 추가 — PostgreSQL 전용 SQL 때문에 Flyway 마이그레이션 실행 안 함, ddl-auto=create-drop으로 스키마 생성
- [Phase 0, 00-03]: @Scheduled 어노테이션 미추가 — Phase 2에서 스케줄러 추가 예정, 현재는 InternalMarketController HTTP 엔드포인트로 수동 트리거
- [Phase 0, 00-03]: Mockito Spy + doReturn 패턴 — ingestDaily()가 외부 Yahoo API 호출하므로 count 집계 로직 검증 시 Spy로 stub
- [Phase 0, 00-04]: MarketDataPort에 default 메서드로 topVolumeSymbols()/symbolsByMarket() 추가 — 기존 구현체 깨짐 없이 확장
- [Phase 0, 00-04]: resolveInitialSymbols()로 전체 KOSPI 200 후보군 사전 로드, resolveSymbolsForDate()로 날짜별 동적 선정 분리 — look-ahead bias 방지
- [Phase 0, 00-04]: LinkedHashSet으로 dynamic + additionalSymbols 합산 후 closesBySymbol 필터링 — 데이터 없는 종목 자동 제외
- [Phase 0]: 유니버스 = KOSPI 거래량 상위 10종목 (자동) + additionalSymbols (수동 추가) 병행 방식 확정
- [Phase 0]: 백테스트 유니버스는 날짜마다 동적으로 재선정 (look-ahead 없이 해당일 기준 상위 10종목)
- [Phase 0]: KOSPI 200 범위에서 거래량 상위 10 선정 (전체 800종목 대비 현실적인 첫 단계)
- [Phase 1, 01-01]: BacktestRequest record extended with nullable String timeFrom/timeTo at end of parameter list — service applies defaults "09:00"/"12:00" when null
- [Phase 1, 01-01]: fetchIntradayForDateRange uses period1/period2 epoch approach (KST zone) not range string — enables arbitrary date ranges up to 60 days
- [Phase 1, 01-01]: findBySymbolAndRange hardcodes interval='5m' in JPQL — plan 01 scope is 5m only; other intervals served by findBySymbolAndIntervalOrderByTsAsc
- [Phase 1, 01-02]: computeDrawdownSegments uses >= peak (not >) for recovery detection — equity returning to previous peak correctly closes the drawdown segment
- [Phase 1, 01-02]: package-private static computeSharpeRatio/Sortino/ProfitFactor/DrawdownSegments in IntradayBacktestEngine — avoids Spring context in unit tests
- [Phase 1, 01-02]: BacktestService retains daily-bar load for volume_top_n symbolResolver — engine receives BiFunction lambda, not raw maps
- [Phase 1, 01-03]: CustomTooltip uses inline interface (not recharts TooltipProps generic) to avoid recharts v2 TypeScript generic complexity
- [Phase 1, 01-03]: ReferenceArea x1/x2 receive ISO datetime strings directly matching XAxis dataKey=datetime values for domain-based positioning
- [Phase 1, 01-03]: XAxis tick labels shown only at 09:00 (session open) to avoid label crowding on 5m bars
- [Roadmap]: recharts@2.15.0 for equity curve chart (SVG, React-friendly, 100-500 pts sufficient)
- [Roadmap]: ShedLock mandatory before any LIVE rule activation (multi-instance safety)
- [Roadmap]: AES-256-GCM via JPA AttributeConverter for Toss token storage (no plaintext in DB)
- [Roadmap]: DB write-through pattern for paper account state (load → evaluate → flush per tick)
- [Phase 01]: [Phase 1, 01-04]: drawdownSegments ?? [] null-guard applied — server may return result with no drawdown segments
- [Phase 01]: [Phase 1, 01-04]: StatCard component defined inline in PaperBacktestPage — no need for separate file at this scale
- [Phase 01]: [Phase 1, 01-04]: lg:grid-cols-6 form layout with lg:col-span-1 on rule select — fits 6 inputs without overflow
- [Phase 02, 02-01]: ShedLock 7.7.0 with JdbcTemplateLockProvider + usingDbTime() — uses DB clock to avoid clock skew between instances
- [Phase 02, 02-01]: V31 migration seeds 2026 KRX holidays with ON CONFLICT DO NOTHING — safe for re-runs
- [Phase 02, 02-01]: KrxMarketCalendar short-circuits on weekend before hitting DB — avoids unnecessary repository calls
- [Phase 02, 02-02]: MarketBarIntraday @Column(name="\"interval\"") — H2 reserved word fix; PostgreSQL production column name unchanged
- [Phase 02, 02-02]: LiveDataScheduler 15:30 guard test uses isTradingDay(any())=false pattern; Clock injection deferred to Phase 3 if needed
- [Phase 02, 02-02]: MarketDataPort.recentIntradayBars() added as default method for Phase 3 engine consumption; DbMarketDataAdapter overrides it
- [Phase 03]: PaperExecutor auto-creates 10M KRW default account on first execute
- [Phase 03]: OrderExecutorPort strategy interface allows LiveExecutor swap in Phase 6 without touching evaluation logic
- [Phase 03]: LiveEvaluationService swallows per-symbol exceptions so one bad symbol never blocks the full tick evaluation
- [Phase 04]: paper.ts centralizes all Phase 4 types so later plans import without new files
- [Phase 04]: backtested flag set lazily on first BacktestService.run() — no separate backtest_count table needed
- [Phase 04]: schedulerLastRun derived from max(ts) of paper_signal_log — avoids separate scheduler_run table
- [Phase 04]: equity curve built from last 30 days of paper_equity_snapshots reversed to ascending — no separate date-range param needed for initial version
- [Phase 05]: reuse SecretEncryptionService (AES-256-GCM) for Toss credentials — no new crypto code needed
- [Phase 05]: TossAccountService returns empty list when unconfigured — dashboard never errors on Toss call
- [Phase 04, 04-07]: fee field set null in PaperTradeHistoryItem — paper_trades schema has no fee column; null avoids a migration; Phase 6 can add fee via migration + DTO update
- [Phase 04, 04-07]: PaperTradeHistoryItem kept as generic-shaped record so Phase 6 LIVE history reuses same DTO/table structure with minimal additions
- [Phase 04, 04-06]: Reuse TradingRulesPage unchanged under paper/rules-lifecycle route — promote/pause/resume/copy already wired to paperApi endpoints; only routing + nav needed
- [Phase 04]: paper_live_symbols is the single canonical symbol source for both scheduler ingestion (activeSymbolsUnion) and live evaluation (resolveSymbols via findByRuleId) — eliminates the two-source divergence that caused scheduler Guard 3 short-circuit
- [Phase 04]: RuleStatus.isLiveActive is the single edit point for live-loop status filtering; Phase 6 extends with one added clause for LIVE status
- [Phase 04]: OrderExecutorPort.supports(TradingRule) enables per-rule executor routing; PaperExecutor handles PAPER-mode; Phase 6 TossOrderExecutor auto-joins via Spring for LIVE-mode
- [Phase 06, 06-01]: toDefinition/fromDefinition defined outside component as pure functions — no React dependencies, trivially testable
- [Phase 06, 06-01]: universe.market hardcoded to "KOSPI" in toDefinition — only KOSPI supported in v1; can be promoted to BuilderState field in Phase 7
- [Phase 06, 06-01]: ConditionRow extracted as sub-component to avoid inline JSX repetition for entry/exit condition rows
- [Phase 06, 06-02]: copyMutation uses per-rule id only — no optimistic update; invalidateQueries re-fetches list after copy
- [Phase 06, 06-02]: cooldown display formula: cooldownBars * 5m (5-minute bar assumption, consistent with backtest engine)
- [Phase 06, 06-02]: PaperRulesPage table column order: 이름 | 상태 | 쿨다운 | 수정일 | 관리 (5 columns, thead/tbody aligned)
- [Phase 06.5, 06.5-01]: V35 신규 컬럼 방식 — status 문자열 파싱 대신 config_status/run_status 2축 컬럼 추가. WHERE run_status='RUNNING' 직접 쿼리 가능, RuleStatus 단일 편집 지점 유지
- [Phase 06.5, 06.5-01]: status 컬럼 보존 (Phase 7에서 제거 옵션) — 기존 getStatus() 호출자 파손 방지
- [Phase 06.5, 06.5-01]: promote/pause/resume @Deprecated 위임 — Wave 1에서 프론트 API 교체 전 브레이킹 변경 방지
- [Phase 06.5, 06.5-01]: listActive() PaperLifecycleService 배치 — ACTIVE 필터는 lifecycle 서비스 책임, CRUD 서비스 아님
- [Phase 06.5, 06.5-01]: STATUSES whitelist DRAFT|ACTIVE only — PAUSED 제거; run_status는 lifecycle 엔드포인트 전용
- [Phase 06.5, 06.5-01]: RuleResponse에 configStatus/runStatus 추가 — 프론트가 두 화면 역할 분리에 즉시 소비 가능
- [Phase 06.5]: operandLabel 포맷: 상수=String.valueOf(Double), 지표=IND(period) — crossAbove expr에 방향 포함
- [Phase 06.5]: evalExit 우선순위: TP→SL→INDICATOR, TP/SL은 exitPct 반환, INDICATOR는 exitPct=null
- [Phase 06.5]: 기존 entryTriggered/exitTriggered → evalEntry/evalExit 위임, 별도 boolean 로직 제거로 단일 편집 지점 유지
- [Phase 06.5, 06.5-03]: PaperLedger.buy()/sell() 오버로드 위임 — no-arg rationale 기존 호출자 하위 호환 유지
- [Phase 06.5, 06.5-03]: ObjectMapper Spring 빈 주입으로 buildRationale 직렬화 — JsonProcessingException은 log.warn+null 반환으로 백테스트 중단 방지
- [Phase 06.5, 06.5-03]: 백테스트 rationale = 인라인 운반 (Discretion 4) — TradeRecord → TradeDto 동일 객체 경로, DB 조인 불필요
- [Phase 06.5, 06.5-04]: mergeRationale()은 Jackson ObjectNode.set("rationale")로 기존 indicatorSnapshot에 병합 — Plan 03 백테스트와 동일 스키마
- [Phase 06.5, 06.5-04]: JOIN 키 rule_id+symbol+ts+signal(=side) — 동일 ts BUY+SELL 로그 충돌 방어 (Pitfall 2)
- [Phase 06.5, 06.5-04]: MON-05 prevDate = tickDate(KST) - 1 calendar day — 주말/공휴일 market_bar 없으면 false(conservative), 체결 허용
- [Phase 06.5, 06.5-04]: PENDING 7자 = VARCHAR(8) 제약 충족, DB 마이그레이션 불필요
- [Phase 06.5, 06.5-05]: configStatus ?? 'DRAFT' fallback — V35 마이그레이션 이전 레거시 룰 방어
- [Phase 06.5, 06.5-05]: parseRationale() 이중 포맷 — 백테스트 직접 root vs 모의 indicatorSnapshot 래퍼 양쪽 처리
- [Phase 06.5, 06.5-05]: expandedId: 백테스트=배열인덱스(BacktestTrade에 id 없음), 모의이력=t.id
- [Phase 06.5, 06.5-05]: 마지막 신호 시각 컬럼 생략 — listActive()가 max(signal_log.ts) JOIN 미반환; Phase 7에서 추가
- [Phase 06.5, 06.5-05]: deactivate guard UI — runStatus=RUNNING 시 비활성화 + tooltip, 백엔드 ERR_LIFECYCLE_006과 동일 의미

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 0]: KOSPI 200 종목 리스트 초기 데이터 삽입 방법 결정 필요 (CSV import vs API vs 수동) [RESOLVED in 00-02]
- [Phase 2]: 실시간 분봉 소스 확정 필요 — Yahoo Finance polling(5m) PAPER_LIVE 충분, LIVE는 토스증권 REST 시세 사용
- [Phase 5]: 토스증권 Open API client_id/secret 환경변수 키 관리 방식 확정 필요 (AES-256-GCM 마스터 키 출처)

## Session Continuity

Last session: 2026-06-22T11:08:03.198Z
Stopped at: Phase 6.6 context gathered
Resume file: .planning/phases/06.6-candle-chart-visualization/06.6-CONTEXT.md
