---
phase: 04-dashboard-lifecycle-monitor-report
plan: "07"
subsystem: paper-trading-history
tags: [backend, frontend, rest-api, paper-trading, trade-history]
dependency_graph:
  requires: []
  provides: [paper-trade-history-endpoint, paper-history-page]
  affects: [PaperHistoryPage, paperApi, paper-trade-history-view]
tech_stack:
  added: []
  patterns:
    - PaperHistoryController mirrors PaperDashboardController pattern (HistoryService.requireCurrentUserId())
    - PaperHistoryService mirrors PaperDashboardService pattern (@Service @Transactional readOnly)
    - PaperTradeHistoryItem record mirrors TradeItem record shape (Phase 6 reusable)
    - PaperHistoryPage useQuery + refetchInterval=30000 mirrors PaperReportPage pattern
key_files:
  created:
    - backend/src/main/java/com/graphify/trading/paper/dto/PaperTradeHistoryItem.java
    - backend/src/main/java/com/graphify/trading/paper/PaperHistoryService.java
    - backend/src/main/java/com/graphify/trading/paper/PaperHistoryController.java
    - frontend/src/pages/trading/paper/PaperHistoryPage.tsx
  modified:
    - frontend/src/types/paper.ts
    - frontend/src/lib/paperApi.ts
decisions:
  - fee field set null in PaperTradeHistoryItem — paper_trades schema has no fee column; null is documented and avoids a migration; Phase 6 can add fee via migration + DTO update
  - PaperTradeHistoryItem kept as generic-shaped record (no paper-baked naming in columns) so Phase 6 LIVE history reuses same DTO/table structure
metrics:
  duration: 5m
  completed: "2026-06-21"
  tasks: 2
  files: 6
---

# Phase 4 Plan 7: Paper Trade History Endpoint + Page Summary

**One-liner:** REST endpoint GET /api/v1/trading/paper/history returning user-scoped paper_trades newest-first, backed by PaperHistoryService + PaperTradeHistoryItem DTO, wired to a real PaperHistoryPage trades table replacing the 8-line stub.

## What Was Built

Replaced the `PaperHistoryPage` "준비 중" placeholder with a fully working 모의 거래 이력 view. The backend exposes a new user-scoped `GET /api/v1/trading/paper/history` endpoint that loads `paper_trades` via `findByAccountIdOrderByTradedAtDesc`, maps each row to a `PaperTradeHistoryItem` record, and returns them newest-first. The frontend adds `PaperTradeHistoryItem` type, `fetchPaperHistory()` fetcher, and a real table component with empty state, loading state, and error state — refreshing every 30 seconds so fills from the live paper loop appear automatically.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Backend paper trade-history endpoint | b5556e6 | dto/PaperTradeHistoryItem.java, PaperHistoryService.java, PaperHistoryController.java |
| 2 | Frontend paperApi fetcher + types + PaperHistoryPage table | 1f210a0 | types/paper.ts, lib/paperApi.ts, PaperHistoryPage.tsx |

## Deviations from Plan

None - plan executed exactly as written.

Note: The `fee` field is `null` in all mappings as documented in the plan. `paper_trades` has no `fee` column; the field is present in the DTO for Phase 6 forward-compatibility (LIVE trades may have fees).

## Verification

- `./gradlew compileJava` — BUILD SUCCESSFUL
- `npx tsc --noEmit` — clean (no output)
- GET /api/v1/trading/paper/history exists, user-scoped via `HistoryService.requireCurrentUserId()`, returns newest-first
- PaperHistoryPage replaced: contains `useQuery`, trades table (6 columns), empty state, loading/error states

## Self-Check: PASSED

Files exist:
- backend/src/main/java/com/graphify/trading/paper/dto/PaperTradeHistoryItem.java — FOUND
- backend/src/main/java/com/graphify/trading/paper/PaperHistoryService.java — FOUND
- backend/src/main/java/com/graphify/trading/paper/PaperHistoryController.java — FOUND
- frontend/src/pages/trading/paper/PaperHistoryPage.tsx — FOUND (useQuery + table)
- frontend/src/types/paper.ts — FOUND (PaperTradeHistoryItem added)
- frontend/src/lib/paperApi.ts — FOUND (fetchPaperHistory added)

Commits:
- b5556e6 — FOUND (feat(04-07): backend paper trade-history endpoint)
- 1f210a0 — FOUND (feat(04-07): frontend paperApi fetcher + PaperHistoryPage table)
