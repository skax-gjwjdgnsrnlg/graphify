---
status: testing
phase: 04-dashboard-lifecycle-monitor-report
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md, 04-05-SUMMARY.md, 04-06-SUMMARY.md, 04-07-SUMMARY.md]
started: 2026-06-21T00:00:00Z
updated: 2026-06-21T00:00:00Z
---

## Current Test

number: 1
name: PAPER 모드 "룰 라이프사이클" 메뉴 도달
expected: |
  앱을 PAPER 모드로 열면 좌측 사이드바에 "룰 라이프사이클" 메뉴가 보인다.
  클릭하면 /trading/paper/rules-lifecycle 로 이동하고,
  룰 목록(상태 배지 + 승격/일시정지/재개/복사 버튼)이 표시된다.
awaiting: user response

## Tests

### 1. PAPER 모드 "룰 라이프사이클" 메뉴 도달
expected: 앱을 PAPER 모드로 열면 좌측 사이드바에 "룰 라이프사이클" 메뉴가 보인다. 클릭하면 /trading/paper/rules-lifecycle 로 이동하고, 룰 목록(상태 배지 + 승격/일시정지/재개/복사 버튼)이 표시된다.
result: [pending]

### 2. 백테스트 미완료 룰 승격 버튼 비활성
expected: 룰 라이프사이클 페이지에서 백테스트를 한 번도 실행하지 않은 DRAFT 룰은 "PAPER_LIVE 승격" 버튼이 회색(disabled) 상태로 표시된다. 클릭해도 반응 없음.
result: [pending]

### 3. 백테스트 완료 룰 PAPER_LIVE 승격
expected: 백테스트를 1회 이상 완료한 룰은 "PAPER_LIVE 승격" 버튼이 활성화된다. 클릭하면 해당 룰의 상태 배지가 PAPER_LIVE로 바뀐다. 에러 없이 성공.
result: [pending]

### 4. 승격 후 모의 대시보드 활성 룰 수 반영
expected: 룰을 PAPER_LIVE로 승격한 뒤 모의 대시보드(/trading/paper/dashboard)로 이동하면 "활성 PAPER_LIVE 룰" 카드의 숫자가 1 이상으로 표시된다.
result: [pending]

### 5. PAPER_LIVE 룰 일시정지 / 재개
expected: PAPER_LIVE 상태인 룰 옆 "일시정지" 버튼 클릭 → 상태 배지가 PAUSED로 변경. 이어서 "재개" 버튼 클릭 → 다시 PAPER_LIVE로 변경. 두 전환 모두 에러 없이 성공.
result: [pending]

### 6. 모의 대시보드 — 현금·포지션·손익 표시
expected: /trading/paper/dashboard에 접속하면 현금, 총 평가금액, 활성 룰 수, 오늘 실현손익 4개 카드가 보인다. 포지션이 있으면 종목 테이블도 표시되고, 없으면 빈 상태 안내가 나온다. 30초마다 자동 갱신.
result: [pending]

### 7. 동작 모니터 페이지
expected: /trading 에서 모니터 메뉴로 이동하면 장중/장외 상태 배지, 스케줄러 마지막 실행 시각, 최근 시그널 로그 테이블, 당일 체결 목록이 표시된다. 데이터가 없으면 각 섹션에 빈 상태 안내가 나온다.
result: [pending]

### 8. 모의 성과 리포트 페이지
expected: /trading/paper/report에 접속하면 equity curve 차트 영역과 MDD·Sharpe·Sortino·승률 등 6개 통계 카드가 보인다. 스냅샷 데이터가 없으면 "데이터 없음" empty state가 표시된다.
result: [pending]

### 9. 모의 거래이력 페이지 — "준비 중" 스텁 교체 확인
expected: /trading/paper/history에 접속하면 더 이상 "모의 체결 내역 — 준비 중" 텍스트가 아닌 실제 거래이력 테이블(또는 거래 없을 때 empty state)이 표시된다.
result: [pending]

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0

## Gaps

[none yet]
