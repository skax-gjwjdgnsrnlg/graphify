---
phase: "05"
plan: "02"
subsystem: "toss-accounts"
tags: [toss, rest-client, react, settings, dashboard-integration]
dependency_graph:
  requires: [05-01]
  provides: [TossAccountService, TossAccountController, TossSettingsPage, tossApi.ts]
  affects: [06-01]
tech_stack:
  added: []
  patterns: [401 retry-once pattern, collapsible section with useState, RestClient 401 onStatus handler]
key_files:
  created:
    - backend/src/main/java/com/graphify/toss/dto/TossAccountDto.java
    - backend/src/main/java/com/graphify/toss/TossAccountService.java
    - backend/src/main/java/com/graphify/toss/TossAccountController.java
    - frontend/src/lib/tossApi.ts
    - frontend/src/pages/trading/TossSettingsPage.tsx
  modified:
    - frontend/src/layouts/TradingLayout.tsx
    - frontend/src/router/index.tsx
    - frontend/src/pages/trading/paper/PaperDashboardPage.tsx
decisions:
  - "TossAccountService returns empty list (not error) when credentials not configured — dashboard never breaks"
  - "401 retry-once via custom UnauthorizedException marker — avoids nested try/catch complexity"
  - "TossBalanceSection collapsible by default — doesn't push main dashboard content for unconfigured users"
  - "apiPost<T,B> type order is <ResponseType, RequestBody> — fixed reversed type params from initial draft"
metrics:
  duration: "7m"
  completed_date: "2026-06-21"
  tasks_completed: 2
  files_created: 5
  files_modified: 3
---

# Phase 05 Plan 02: Toss Account Balance & Settings Page Summary

**One-liner:** Toss Securities real-account balance API with 401 retry-once pattern, TossSettingsPage with credential form + status badge + manual token refresh, and collapsible Toss balance section in PaperDashboardPage.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | TossAccountService + TossAccountController | d82260d | TossAccountDto + TossAccountService + TossAccountController |
| 2 | Frontend — tossApi.ts + TossSettingsPage + TradingLayout + router + PaperDashboard | d82260d | tossApi.ts + TossSettingsPage + TradingLayout + router + PaperDashboardPage |

## What Was Built

**`TossAccountService.getAccounts(userId)`**:
- Returns empty list if credentials not configured (graceful)
- Calls `ensureValidToken(userId)` → Bearer token
- GET `https://openapi.tossinvest.com/api/v1/accounts` with Authorization header
- On 401: throws `UnauthorizedException` marker → caught → `issueToken()` force-refresh → retry once
- On second failure: throws `GraphifyException` BAD_GATEWAY
- Parses `{ accounts: [...] }` JSON response via private `TossAccountsResponse` record

**`TossAccountController`** — `GET /api/v1/toss/accounts` → `ApiResponse<List<TossAccountDto>>`

**`tossApi.ts`** — `fetchTossStatus`, `saveTossCredentials`, `refreshTossToken`, `fetchTossAccounts`

**`TossSettingsPage`**:
- `useQuery` for status with `?? undefined` null coercion fix
- Status badge: 미설정 (gray) | 설정됨·토큰유효 (green) | 설정됨·토큰만료 (yellow)
- Two `type="password"` inputs for client_id and client_secret
- Submit → `saveTossCredentials` mutation → invalidates status query
- Manual "토큰 수동 갱신" button → `refreshTossToken` mutation
- Success/error message state

**`TradingLayout`** — `commonItems` now includes `{ to: "/trading/settings", label: "토스 설정" }` — visible in both PAPER and LIVE modes

**`router/index.tsx`** — `{ path: "settings", element: <TossSettingsPage /> }` under `/trading` (no ModeGuard)

**`PaperDashboardPage`** — `TossBalanceSection` component added at bottom:
- Collapsed by default (toggle with useState)
- Empty state: "토스증권 미연동" + link to /trading/settings
- Connected state: account table with 계좌번호 | 계좌명 | 잔고 | 출금가능

## Deviations from Plan

**[Rule 1 - Bug] apiPost type params reversed in tossApi.ts**
- **Found during:** Task 2 TypeScript check
- **Issue:** `apiPost<RequestBody, ResponseType>` was wrong — signature is `apiPost<ResponseType, RequestBody>`
- **Fix:** Swapped type params in `saveTossCredentials` and `refreshTossToken`; changed `refreshTossToken` body to `null` instead of `undefined`

**[Rule 1 - Bug] useQuery data possibly null causing TS2322**
- **Found during:** Task 2 TypeScript check  
- **Issue:** `(await fetchTossStatus()).data` returns `T | null`, passed to `StatusBadge` expecting `T | undefined`
- **Fix:** Added `?? undefined` coercion in queryFn

## Self-Check

Files exist:
- backend/src/main/java/com/graphify/toss/TossAccountService.java — FOUND
- backend/src/main/java/com/graphify/toss/TossAccountController.java — FOUND
- frontend/src/pages/trading/TossSettingsPage.tsx — FOUND
- frontend/src/lib/tossApi.ts — FOUND

Commits: d82260d — feat(05-02): Toss account balance API + TossSettingsPage + dashboard integration

Full test suite: BUILD SUCCESSFUL (7s)
TypeScript: clean (no errors)

## Self-Check: PASSED
