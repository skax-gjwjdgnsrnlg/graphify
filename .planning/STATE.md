---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: in_progress
stopped_at: "Completed 02-02-PLAN.md"
last_updated: "2026-06-21T02:06:00Z"
last_activity: "2026-06-21 — 02-02 완료: PaperLiveSymbol/Repository/Service, LiveDataScheduler(@Scheduled+@SchedulerLock), MarketDataPort.recentIntradayBars(), staleness guard"
progress:
  total_phases: 7
  completed_phases: 2
  total_plans: 9
  completed_plans: 9
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-20)

**Core value:** 룰 기반 전략을 백테스트로 검증 → 실시간 모의 실행으로 성과 확인 → 토스증권 실계좌로 승격하는 일관된 파이프라인
**Current focus:** Phase 0 — 데이터 인프라 & 동적 유니버스

## Current Position

Phase: 2 of 7 (실시간 데이터 스케줄러)
Plan: 2 of 4 in current phase (COMPLETE)
Status: Phase 2, Plan 2 complete — LiveDataScheduler + PaperLiveSymbolService + MarketDataPort.recentIntradayBars() done
Last activity: 2026-06-21 — 02-02 완료: PaperLiveSymbol/Repository/Service, LiveDataScheduler(@Scheduled+@SchedulerLock), MarketDataPort.recentIntradayBars(), staleness guard

Progress: [██████████] 100%

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

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 0]: KOSPI 200 종목 리스트 초기 데이터 삽입 방법 결정 필요 (CSV import vs API vs 수동) [RESOLVED in 00-02]
- [Phase 2]: 실시간 분봉 소스 확정 필요 — Yahoo Finance polling(5m) PAPER_LIVE 충분, LIVE는 토스증권 REST 시세 사용
- [Phase 5]: 토스증권 Open API client_id/secret 환경변수 키 관리 방식 확정 필요 (AES-256-GCM 마스터 키 출처)

## Session Continuity

Last session: 2026-06-21T02:06:00Z
Stopped at: Completed 02-02-PLAN.md
Resume file: .planning/phases/02-realtime-data-scheduler/02-02-SUMMARY.md
