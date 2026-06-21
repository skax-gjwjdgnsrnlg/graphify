# Phase 6: 룰 빌더 UI - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning

<domain>
## Phase Boundary

JSON textarea 직접 입력 방식을 드롭다운/폼 기반 시각적 빌더로 교체한다.
유니버스·진입 조건·청산 조건·사이징·쿨다운을 모두 UI 컨트롤로 설정할 수 있으며,
기존 JSON 룰도 빌더 폼에 로드(역직렬화)하여 편집할 수 있다.

**인-스코프:** RuleBuilderPage(신규), PaperRulesPage 편집 링크 변경, 빌더↔JSON 탭 전환
**아웃-스코프:** 룰 생애주기 변경 없음, 백테스트 엔진 변경 없음, 새 지표 추가 없음

</domain>

<decisions>
## Implementation Decisions

### 빌더 진입 방식
- **전용 페이지**로 구현: `/trading/paper/rules/new`, `/trading/paper/rules/edit/:id`
- PaperRulesPage의 `+ 새 룰` 버튼 → `/trading/paper/rules/new` navigate
- PaperRulesPage의 `편집` 버튼 → `/trading/paper/rules/edit/:id` navigate
- 저장 성공 후 PaperRulesPage(`/trading/paper/rules`)로 자동 리다이렉트
- `TradingRulesEditPage.tsx`(현재 stub) → `RuleBuilderPage`로 교체

### 조건 복수성
- **진입 조건**: AND/OR 로직 드롭다운 + 다중 조건 지원
  - 각 조건 행: `[지표 드롭다운] [파라미터 입력] [연산자 드롭다운] [우측값 입력]`
  - `+ 조건 추가` 버튼이 마지막 조건 행 바로 아래 인라인 제공
  - 조건이 2개 이상일 때 각 행 오른쪽에 `×` 삭제 버튼 표시
- **청산 조건**: 익절%/손절% 고정 입력 필드 + 다중 지표 조건 추가 가능
  - 익절%·손절% 필드는 항상 상단에 고정 (빈칸 허용)
  - `+ 청산 지표 조건 추가` 버튼으로 진입 조건과 동일한 행 구조 추가
  - 청산 조건 logic(AND/OR)도 별도 드롭다운으로 제어

### JSON 토글
- 페이지 상단에 **`빌더 | JSON`** 탭 바 (Tailwind 탭 버튼 스타일)
- **빌더 → JSON**: 빌더 폼 상태를 즉시 직렬화하여 JSON textarea에 반영
- **JSON → 빌더**: JSON 파싱 성공 시 빌더 폼 자동 업데이트; 파싱 실패 시 JSON 탭 유지 + 에러 메시지 표시
- JSON 탭은 쓰기 가능 (수정 후 빌더로 전환 시 위 규칙 적용)

### Claude's Discretion
- 유니버스 타입 UI 세부 레이아웃 (라디오 버튼 vs 드롭다운)
- 지표별 파라미터 입력 가시성 (SMA/EMA/RSI: period 입력 표시, PRICE/VOLUME: 숨김)
- 사이징 타입 UI (드롭다운 + 값 입력 인접 배치)
- 쿨다운 봉 수 입력 위치 (사이징 아래 독립 행)
- 에러/검증 메시지 스타일

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RuleDefinition` 타입 (`frontend/src/types/trading.ts`) — universe, entry, exit, sizing, constraints 전부 정의됨, 변경 불필요
- `createPaperRule / updatePaperRule` (`frontend/src/lib/ruleApi.ts`) — 그대로 사용
- `TradingRulesEditPage.tsx` — 현재 stub ("준비 중"), RuleBuilderPage로 내용 교체
- `PaperRulesPage.tsx` — 편집 버튼 onClick을 navigate로 교체, + 새 룰 버튼도 navigate로 교체; JSON textarea 모달은 제거
- 모달 스타일 토큰 (`border border-white/10 bg-gray-900`, `rounded-md bg-emerald-600`) — 빌더 페이지 카드/버튼에 그대로 적용
- `STATUS_CONFIG` 배지 패턴 (`TradingRulesPage.tsx`) — 빌더 페이지 상태 표시에 재사용 가능

### Established Patterns
- 입력 필드 스타일: `rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50`
- 드롭다운: `<select>` 동일 스타일
- 에러 텍스트: `text-xs text-red-400`
- Tailwind Dark 테마 전반 (`bg-gray-900/50`, `text-gray-300`, `text-gray-400`)
- `useMutation` + `onSuccess invalidate` 패턴 (`PaperRulesPage.tsx`)
- `useNavigate` (React Router) 사용 패턴 — 기존 라우터 구조에 `/paper/rules/new`, `/paper/rules/edit/:id` 추가

### Integration Points
- `frontend/src/router/index.tsx` — `/trading/paper/rules/new`, `/trading/paper/rules/edit/:id` 라우트 추가 (ModeGuard mode="PAPER" 하위)
- `frontend/src/pages/trading/TradingRulesEditPage.tsx` — RuleBuilderPage로 교체 (파일명 변경 또는 내용 교체)
- `PaperRulesPage.tsx` — 편집/삭제 버튼 영역만 수정, 나머지 테이블 그대로 유지
- `RuleDefinition` 직렬화: `JSON.stringify(builderState)` → `createPaperRule / updatePaperRule`에 전달

### Supported Options (빌더가 커버해야 하는 전체 범위)
- **Universe types**: `volume_top_n` (market=KOSPI, topN 입력, additionalSymbols 선택), `symbols` (직접 종목 코드 입력)
- **Indicators (left/right)**: `PRICE`, `SMA(period)`, `EMA(period)`, `RSI(period)`, `VOLUME`
- **Operators**: `>`, `>=`, `<`, `<=`, `==`, `crossAbove`, `crossBelow`
- **Right operand**: 숫자 value 또는 다른 indicator
- **Exit**: `takeProfitPct`, `stopLossPct`, indicator conditions (동일 지표/연산자 구조)
- **Sizing types**: `cash` (고정금액), `percent` (비율), `qty` (수량)
- **Constraints**: `cooldownBars` (정수), `maxPositionsPerSymbol` (정수)

</code_context>

<specifics>
## Specific Ideas

- 드롭다운으로 골라서 세팅하는 방식 (사용자 직접 요청)
- JSON과 빌더를 탭으로 전환 — 개발자/고급 사용자는 JSON으로 세밀하게 조정 가능
- 기존 룰(JSON)을 빌더에서 로드해 편집 가능해야 함 (역직렬화 필수)
- 빌더에서 저장한 룰이 백테스트·PAPER_LIVE 파이프라인에서 그대로 동작해야 함

</specifics>

<deferred>
## Deferred Ideas

- 없음 — 논의가 Phase 6 스코프 내에서 진행됨

</deferred>

---

*Phase: 06-rule-builder-ui*
*Context gathered: 2026-06-22*
