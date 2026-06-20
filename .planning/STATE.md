# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-20)

**Core value:** 룰 기반 전략을 백테스트로 검증 → 실시간 모의 실행으로 성과 확인 → 토스증권 실계좌로 승격하는 일관된 파이프라인
**Current focus:** Phase 0 — 데이터 인프라 & 동적 유니버스

## Current Position

Phase: 0 of 7 (데이터 인프라 & 동적 유니버스)
Plan: 2 of 4 in current phase
Status: In progress — executing Phase 0 plans
Last activity: 2026-06-20 — 00-01 완료: BacktestService volume null 버그 수정 (DATA-05), 5 tests GREEN (RuleEvaluatorVolumeTest 3 + BacktestServiceVolumeTest 2)

Progress: [██░░░░░░░░] 20%

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

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Phase 0, 00-02]: @DataJpaTest 슬라이스에서 Flyway 비활성화 — @AutoConfigureTestDatabase(replace=ANY) + @TestPropertySource 조합 사용 (application.properties 단독으로는 슬라이스 컨텍스트에서 무시됨)
- [Phase 0, 00-02]: H2 testRuntimeOnly 추가 — PostgreSQL 전용 SQL 때문에 Flyway 마이그레이션 실행 안 함, ddl-auto=create-drop으로 스키마 생성
- [Phase 0]: 유니버스 = KOSPI 거래량 상위 10종목 (자동) + additionalSymbols (수동 추가) 병행 방식 확정
- [Phase 0]: 백테스트 유니버스는 날짜마다 동적으로 재선정 (look-ahead 없이 해당일 기준 상위 10종목)
- [Phase 0]: KOSPI 200 범위에서 거래량 상위 10 선정 (전체 800종목 대비 현실적인 첫 단계)
- [Roadmap]: recharts@2.15.0 for equity curve chart (SVG, React-friendly, 100-500 pts sufficient)
- [Roadmap]: ShedLock mandatory before any LIVE rule activation (multi-instance safety)
- [Roadmap]: AES-256-GCM via JPA AttributeConverter for Toss token storage (no plaintext in DB)
- [Roadmap]: DB write-through pattern for paper account state (load → evaluate → flush per tick)

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 0]: BacktestService volume null 버그 — 즉시 수정 필요 (plan 00-01)
- [Phase 0]: KOSPI 200 종목 리스트 초기 데이터 삽입 방법 결정 필요 (CSV import vs API vs 수동)
- [Phase 2]: 실시간 분봉 소스 확정 필요 — Yahoo Finance polling(5m) PAPER_LIVE 충분, LIVE는 토스증권 REST 시세 사용
- [Phase 5]: 토스증권 Open API client_id/secret 환경변수 키 관리 방식 확정 필요 (AES-256-GCM 마스터 키 출처)

## Session Continuity

Last session: 2026-06-20
Stopped at: Completed 00-02-PLAN.md — in_kospi200 컬럼 + KOSPI 200 마스터 데이터 + CompanyEntityTest 4 tests GREEN
Resume file: None
