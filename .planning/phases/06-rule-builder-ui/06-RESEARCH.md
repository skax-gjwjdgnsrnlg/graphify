# Phase 6: 룰 빌더 UI - Research

**Researched:** 2026-06-22
**Domain:** React form-based rule builder (TypeScript, Tailwind, React Router v6, TanStack Query v5)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **빌더 진입 방식**: 전용 페이지 `/trading/paper/rules/new`, `/trading/paper/rules/edit/:id`
- PaperRulesPage의 `+ 새 룰` 버튼 → navigate to `/trading/paper/rules/new`
- PaperRulesPage의 `편집` 버튼 → navigate to `/trading/paper/rules/edit/:id`
- 저장 성공 후 `/trading/paper/rules`로 자동 리다이렉트
- `TradingRulesEditPage.tsx`(현재 stub) → `RuleBuilderPage`로 교체
- **진입 조건**: AND/OR 로직 드롭다운 + 다중 조건 지원
  - 각 조건 행: `[지표 드롭다운] [파라미터 입력] [연산자 드롭다운] [우측값 입력]`
  - `+ 조건 추가` 버튼 인라인
  - 조건 2개 이상 시 `×` 삭제 버튼 표시
- **청산 조건**: 익절%/손절% 고정 입력 필드 상단 고정 + 다중 지표 조건 추가 가능
  - 청산 조건 logic(AND/OR) 별도 드롭다운
- **JSON 토글**: 페이지 상단 `빌더 | JSON` 탭 바
  - 빌더→JSON: 즉시 직렬화
  - JSON→빌더: 파싱 성공 시 폼 업데이트, 실패 시 JSON 탭 유지 + 에러 메시지
  - JSON 탭은 쓰기 가능

### Claude's Discretion
- 유니버스 타입 UI 세부 레이아웃 (라디오 버튼 vs 드롭다운)
- 지표별 파라미터 입력 가시성 (SMA/EMA/RSI: period 입력 표시, PRICE/VOLUME: 숨김)
- 사이징 타입 UI (드롭다운 + 값 입력 인접 배치)
- 쿨다운 봉 수 입력 위치 (사이징 아래 독립 행)
- 에러/검증 메시지 스타일

### Deferred Ideas (OUT OF SCOPE)
- 없음 — 논의가 Phase 6 스코프 내에서 진행됨
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| RULE-06 | 룰 복제 (파라미터 실험용 DRAFT 복사) | `copyRule()` API 이미 존재 (`paperApi.ts`). `TradingRulesPage`에 복사 버튼 이미 구현. Phase 6에서 빌더 페이지 네비게이션과 연동 필요. |
| RULE-07 | 쿨다운 UI 표시 ("다음 진입 가능: X분 후") | `constraints.cooldownBars` 정수 필드가 `RuleDefinition`에 이미 있음. 빌더 폼에서 입력 + 룰 목록에서 현재 쿨다운 상태 표시 필요. 쿨다운 남은 시간은 프론트에서 계산하거나 백엔드 API 추가 필요. |
</phase_requirements>

---

## Summary

Phase 6는 순수 프론트엔드 작업이다. 백엔드 API(`createPaperRule`, `updatePaperRule`, `fetchPaperRule`)는 이미 완성되어 있고 `RuleDefinition` 타입도 프론트엔드와 백엔드 양쪽에서 완전히 정의되어 있다. 신규 파일은 `RuleBuilderPage.tsx` 한 개이며, 나머지는 기존 파일(`PaperRulesPage.tsx`, `TradingLayout.tsx`, `router/index.tsx`)의 부분 수정이다.

핵심 구현 과제는 빌더 폼 상태(builder state)와 `RuleDefinition` JSON 사이의 양방향 직렬화/역직렬화다. 빌더 탭에서 폼을 편집하면 실시간으로 `RuleDefinition` 구조로 직렬화되고, JSON 탭에서 수정한 텍스트를 빌더 탭으로 전환할 때 파싱 성공 여부를 검증하여 폼에 반영해야 한다. 기존 `RuleDefinitionValidator`(백엔드)가 정의한 제약 조건을 프론트엔드에서도 동일하게 적용하면 불필요한 서버 왕복을 줄일 수 있다.

**Primary recommendation:** `RuleBuilderPage` 컴포넌트에서 builder form state를 단일 `useState` 객체로 관리하고, `useMemo`로 `RuleDefinition`을 즉시 파생시키며, `createPaperRule`/`updatePaperRule` 호출 전에 클라이언트 사이드 검증을 수행한다.

---

## Standard Stack

### Core (이미 설치됨 — 추가 설치 불필요)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | ^18.3.1 | UI 컴포넌트 | 프로젝트 기반 |
| TypeScript | ~5.6.3 | 타입 안전성 | 프로젝트 기반 |
| react-router-dom | ^6.28.0 | 페이지 라우팅, useNavigate, useParams | 프로젝트 기반 |
| @tanstack/react-query | ^5.62.8 | 서버 상태 관리, useMutation | 프로젝트 기반 |
| Tailwind CSS | ^3.4.16 | 스타일링 | 프로젝트 기반 |
| zustand | ^5.0.2 | 전역 상태(mode) | 프로젝트 기반 |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| 없음 | - | 폼 라이브러리 불필요 | 빌더 상태가 일반 `useState`로 충분히 관리 가능 |

**react-hook-form, zod 미설치 — 도입 불필요.** 빌더 폼은 구조화된 객체 상태이며 필드 수가 동적이므로 일반 `useState` + 커스텀 검증이 더 단순하다.

---

## Architecture Patterns

### Recommended Project Structure

```
frontend/src/
├── pages/trading/
│   ├── TradingRulesEditPage.tsx     # 내용 전체 교체 → RuleBuilderPage 렌더
│   └── paper/
│       └── PaperRulesPage.tsx       # 편집/새 룰 버튼 onClick → navigate 교체
├── components/trading/
│   └── rule-builder/                # (선택) 하위 컴포넌트 분리 시
│       ├── ConditionRow.tsx
│       ├── UniverseSection.tsx
│       └── SizingSection.tsx
└── router/index.tsx                 # 두 라우트 추가
```

**단순한 구성:** `RuleBuilderPage` 단일 파일에 모든 로직을 담아도 충분하다. 200줄 초과 시에만 하위 컴포넌트 분리를 고려한다.

### Pattern 1: Builder Form State

빌더 상태를 `RuleDefinition`과 1:1 대응하는 타입으로 정의하고 `useState`로 관리한다.

```typescript
// 빌더 내부 상태 타입 — RuleDefinition과 평행하게 유지
interface ConditionRowState {
  leftIndicator: RuleIndicator | "";
  leftPeriod: string;         // SMA/EMA/RSI 전용, 빈 문자열 허용
  op: RuleOperator | "";
  rightType: "value" | "indicator";
  rightValue: string;         // 숫자 입력, 빈 문자열 허용
  rightIndicator: RuleIndicator | "";
  rightPeriod: string;
}

interface BuilderState {
  name: string;
  // Universe
  universeType: "symbols" | "volume_top_n";
  symbolsInput: string;       // 쉼표 구분 문자열 "005930,000660"
  topN: string;
  additionalSymbols: string;
  // Entry
  entryLogic: RuleLogic;
  entryConditions: ConditionRowState[];
  // Exit
  takeProfitPct: string;
  stopLossPct: string;
  exitLogic: RuleLogic;
  exitConditions: ConditionRowState[];
  // Sizing
  sizingType: SizingType;
  sizingValue: string;
  // Constraints
  cooldownBars: string;
  maxPositionsPerSymbol: string;
}
```

### Pattern 2: Bidirectional Serialization

```typescript
// 빌더 상태 → RuleDefinition (저장/JSON탭 전환 시)
function toDefinition(s: BuilderState): RuleDefinition { ... }

// RuleDefinition → 빌더 상태 (편집 로드/JSON탭→빌더탭 전환 시)
function fromDefinition(def: RuleDefinition, name: string): BuilderState { ... }
```

두 함수는 순수 함수로 작성하여 단위 테스트 가능하게 유지한다.

### Pattern 3: Tab Toggle

```typescript
type TabMode = "builder" | "json";
const [tab, setTab] = useState<TabMode>("builder");
const [jsonText, setJsonText] = useState("");
const [jsonError, setJsonError] = useState<string | null>(null);

// 빌더→JSON 탭 전환 시
function switchToJson() {
  setJsonText(JSON.stringify(toDefinition(builderState), null, 2));
  setJsonError(null);
  setTab("json");
}

// JSON→빌더 탭 전환 시
function switchToBuilder() {
  try {
    const parsed = JSON.parse(jsonText) as RuleDefinition;
    setBuilderState(fromDefinition(parsed, builderState.name));
    setJsonError(null);
    setTab("builder");
  } catch {
    setJsonError("JSON 형식이 올바르지 않습니다.");
    // 탭 전환 안 함 — JSON 탭 유지
  }
}
```

### Pattern 4: Route & Navigation (기존 패턴 재사용)

```typescript
// router/index.tsx에 추가 (ModeGuard mode="PAPER" 하위)
{
  path: "paper/rules/new",
  element: (
    <ModeGuard mode="PAPER">
      <RuleBuilderPage mode="create" />
    </ModeGuard>
  ),
},
{
  path: "paper/rules/edit/:id",
  element: (
    <ModeGuard mode="PAPER">
      <RuleBuilderPage mode="edit" />
    </ModeGuard>
  ),
},
```

```typescript
// PaperRulesPage.tsx 버튼 변경
const navigate = useNavigate();
// + 새 룰
onClick={() => navigate("/trading/paper/rules/new")}
// 편집
onClick={() => navigate(`/trading/paper/rules/edit/${rule.id}`)}
```

편집 모드에서는 `useParams()` → `fetchPaperRule(id)` → `fromDefinition()` 순서로 초기화한다.

### Pattern 5: 지표별 파라미터 가시성

```typescript
const PERIOD_INDICATORS: RuleIndicator[] = ["SMA", "EMA", "RSI"];

function needsPeriod(indicator: RuleIndicator | ""): boolean {
  return PERIOD_INDICATORS.includes(indicator as RuleIndicator);
}

// ConditionRow에서
{needsPeriod(row.leftIndicator) && (
  <input
    type="number"
    placeholder="기간"
    value={row.leftPeriod}
    ...
    className="w-16 rounded-md border border-white/10 bg-gray-800 px-2 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
  />
)}
```

### Pattern 6: Cooldown UI (RULE-07)

`constraints.cooldownBars`는 빌더 폼에서 정수 입력 필드로 노출한다. 쿨다운 "다음 진입 가능: X분 후" 표시는 룰 목록(PaperRulesPage 또는 TradingRulesPage)에서 보조 텍스트로 표시한다.

현재 백엔드는 쿨다운 잔여 봉 수를 반환하는 엔드포인트가 없다. Phase 6 스코프에서는 **빌더 폼에서 cooldownBars 값을 설정하는 것**이 RULE-07의 핵심이며, 실시간 "X분 후" 계산은 백엔드 지원이 필요하므로 별도 API 없이 구현 가능한 방식(봉 수 × 5분으로 프론트에서 계산)으로 대응한다.

### Anti-Patterns to Avoid

- **단일 JSON string 상태로 전체 폼 관리**: 필드별 오류 표시 불가, 조건 추가/삭제 불가
- **`useEffect`로 JSON↔빌더 동기화**: 무한 루프 위험. 탭 전환 이벤트에서만 변환한다
- **`RuleDefinition` 타입 수정**: 백엔드와 동일하게 유지. 빌더 내부 상태만 별도 타입 사용
- **폼 라이브러리 도입(react-hook-form 등)**: 동적 조건 행 관리가 오히려 복잡해짐
- **`/trading/rules/edit` 기존 라우트 수정**: LIVE 모드 라우트는 건드리지 않음

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP 요청 | 직접 fetch | `createPaperRule`, `updatePaperRule`, `fetchPaperRule` (ruleApi.ts) | 이미 완성 |
| 룰 복제 | 복사 로직 직접 구현 | `copyRule()` (paperApi.ts) | 이미 완성 |
| 서버 상태 캐시 무효화 | 직접 refetch | `useQueryClient().invalidateQueries({ queryKey: ["trading", "paper", "rules"] })` | 기존 패턴 |
| 라우트 보호 | 직접 mode 체크 | `<ModeGuard mode="PAPER">` | 기존 패턴 |
| JSON → 백엔드 검증 | 직접 검증 로직 | 서버의 `RuleDefinitionValidator`가 400 반환 | onError 핸들러로 메시지 표시 |

---

## Common Pitfalls

### Pitfall 1: UniverseType 불일치
**What goes wrong:** 프론트엔드 `trading.ts`의 `UniverseType`은 `"symbols" | "watchlist"`로 정의되어 있다. 하지만 백엔드 validator는 `"volume_top_n"`도 허용한다. 빌더가 `volume_top_n`을 전송하면 TypeScript 타입 오류가 발생한다.
**Why it happens:** `trading.ts` 타입이 아직 업데이트되지 않음.
**How to avoid:** `trading.ts`의 `UniverseType`을 `"symbols" | "watchlist" | "volume_top_n"`으로 확장하고 `RuleDefinition.universe`에 `market?`, `topN?`, `additionalSymbols?` 필드를 추가한다. 백엔드 `RuleDefinition.java`의 `Universe` record와 일치시킨다.
**Warning signs:** TypeScript 컴파일 에러 "Type 'volume_top_n' is not assignable to type UniverseType"

### Pitfall 2: 편집 모드 초기화 타이밍
**What goes wrong:** `useParams().id`로 `fetchPaperRule(id)` 호출 후 데이터 로딩 중에 빌더가 빈 상태로 렌더링되어 사용자가 빈 폼을 볼 수 있다.
**Why it happens:** `useQuery`는 비동기이므로 초기 렌더 시 `undefined`.
**How to avoid:** 편집 모드에서는 로딩 중일 때 스피너나 skeleton을 표시하고, `data`가 정의된 후에만 빌더 폼을 렌더링한다. `useState`의 초기값을 `useQuery` 데이터로 직접 설정하지 말고, `useEffect`로 데이터 로드 완료 후 `setBuilderState(fromDefinition(data))`를 호출한다.

### Pitfall 3: 빈 조건 배열 전송
**What goes wrong:** 사용자가 진입 조건을 모두 삭제한 상태로 저장하면 백엔드 validator가 "entry.conditions 가 비어 있습니다" 400 에러를 반환한다.
**How to avoid:** 저장 버튼 클릭 시 클라이언트 사이드 검증으로 `entryConditions.length > 0` 확인 후 `text-red-400` 에러 메시지를 표시한다.

### Pitfall 4: 숫자 입력 문자열 변환
**What goes wrong:** `sizing.value`에 `""` 또는 `"abc"` 전송 시 백엔드 JSON 파싱 오류.
**How to avoid:** `toDefinition()` 함수에서 `parseFloat(state.sizingValue)` 후 `isNaN` 체크. NaN이면 저장 차단.

### Pitfall 5: TradingLayout 사이드바 nav 누락
**What goes wrong:** `/trading/paper/rules/new` 라우트를 추가해도 사이드바 `paperItems`에 nav 링크가 없으면 사용자가 해당 페이지에서 다른 페이지로 이동 불가.
**How to avoid:** `paperItems`에 새 링크를 추가하지 않아도 된다 — 빌더는 PaperRulesPage에서 진입하는 전용 페이지이며, 저장 후 자동 리다이렉트되므로 직접 nav 링크 불필요. 단, 브라우저 뒤로가기는 동작해야 한다.

### Pitfall 6: 기존 `paper/rules` 모달 제거 누락
**What goes wrong:** `PaperRulesPage.tsx`에 남아 있는 인라인 모달(`editor` 상태, `openCreate`/`openEdit` 함수, `TEMPLATE` 상수)을 제거하지 않으면 코드 중복과 혼란.
**How to avoid:** PaperRulesPage에서 `editor`, `formError`, `saveMutation`, `openCreate`, `openEdit`, `emptyEditor`, `TEMPLATE`, `EditorState`, `STATUS_OPTIONS` 전부 제거. 버튼 onClick만 navigate로 교체.

---

## Code Examples

Verified patterns from existing codebase:

### Input field 스타일 (프로젝트 표준)
```typescript
// 모든 입력 필드에 이 클래스 사용
className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"

// select 동일 스타일
// 에러 텍스트
className="text-xs text-red-400"
```

### useMutation + onSuccess invalidate + navigate 패턴
```typescript
const navigate = useNavigate();
const queryClient = useQueryClient();
const saveMutation = useMutation({
  mutationFn: (payload: RuleUpsertRequest) =>
    isEdit ? updatePaperRule(id, payload) : createPaperRule(payload),
  onSuccess: () => {
    void queryClient.invalidateQueries({ queryKey: ["trading", "paper", "rules"] });
    navigate("/trading/paper/rules");
  },
  onError: (err) => {
    setFormError(err instanceof ApiRequestError ? err.message : "저장에 실패했습니다.");
  },
});
```

### 편집 모드 데이터 로드
```typescript
const { id } = useParams<{ id: string }>();
const isEdit = id !== undefined;

const { data: rule, isLoading } = useQuery({
  queryKey: ["trading", "paper", "rules", id],
  queryFn: () => fetchPaperRule(Number(id)),
  enabled: isEdit,
});

useEffect(() => {
  if (rule?.data) {
    setBuilderState(fromDefinition(rule.data.definition, rule.data.name));
  }
}, [rule]);
```

### 탭 바 스타일 (Tailwind, 프로젝트 패턴 기반)
```typescript
<div className="mb-6 flex gap-2 border-b border-white/10">
  {(["builder", "json"] as const).map((t) => (
    <button
      key={t}
      type="button"
      onClick={() => t === "json" ? switchToJson() : switchToBuilder()}
      className={`px-4 py-2 text-sm font-medium transition-colors ${
        tab === t
          ? "border-b-2 border-emerald-500 text-white"
          : "text-gray-400 hover:text-white"
      }`}
    >
      {t === "builder" ? "빌더" : "JSON"}
    </button>
  ))}
</div>
```

### 조건 행 추가/삭제 패턴
```typescript
const addCondition = () =>
  setBuilderState((s) => ({
    ...s,
    entryConditions: [...s.entryConditions, emptyConditionRow()],
  }));

const removeCondition = (idx: number) =>
  setBuilderState((s) => ({
    ...s,
    entryConditions: s.entryConditions.filter((_, i) => i !== idx),
  }));

const updateCondition = (idx: number, patch: Partial<ConditionRowState>) =>
  setBuilderState((s) => ({
    ...s,
    entryConditions: s.entryConditions.map((c, i) => i === idx ? { ...c, ...patch } : c),
  }));
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| JSON textarea 직접 입력 | 드롭다운/폼 빌더 | Phase 6 | 비개발자도 룰 생성 가능 |
| 모달 기반 편집 | 전용 페이지 | Phase 6 | 복잡한 폼에 더 넓은 레이아웃 |

**Deprecated/outdated:**
- `PaperRulesPage`의 인라인 모달 편집기: Phase 6에서 navigate 기반으로 교체

---

## Type Updates Required

`frontend/src/types/trading.ts`에서 다음을 업데이트해야 한다:

```typescript
// 현재 (불완전)
export type UniverseType = "symbols" | "watchlist";
export interface RuleDefinition {
  universe: { type: UniverseType; symbols?: string[] };
  ...
}

// Phase 6 후 (백엔드 RuleDefinition.java와 일치)
export type UniverseType = "symbols" | "watchlist" | "volume_top_n";
export interface RuleDefinition {
  version: 1;
  universe: {
    type: UniverseType;
    symbols?: string[];
    market?: string;
    topN?: number;
    additionalSymbols?: string[];
  };
  ...
}
```

---

## Open Questions

1. **RULE-07 쿨다운 "X분 후" 표시 데이터 소스**
   - What we know: `cooldownBars`는 정수(봉 수). 장중 5분봉 기준이면 1봉 = 5분.
   - What's unclear: 현재 쿨다운 잔여 봉 수를 백엔드가 반환하는 API가 없다. 백엔드에서 마지막 체결 시각 + cooldownBars를 계산해야 정확한 잔여 시간을 알 수 있다.
   - Recommendation: Phase 6 스코프에서는 빌더에서 `cooldownBars` 값 설정만 구현하고, "다음 진입 가능" 표시는 `cooldownBars × 5분` 정적 레이블(예: "쿨다운: 3봉(15분)")로 대체. 동적 잔여 시간 표시는 Phase 7 이후로 자연스럽게 defer.

2. **`watchlist` universe 타입 지원 범위**
   - What we know: 백엔드 validator가 `"watchlist"`를 허용하지만 빌더 CONTEXT.md에는 `volume_top_n`과 `symbols`만 명시됨.
   - Recommendation: Phase 6 빌더에서는 `watchlist` 타입 UI를 구현하지 않는다. JSON 탭에서는 여전히 전송 가능하므로 하위 호환성 유지됨.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Playwright ^1.51.0 (E2E only) |
| Config file | `frontend/e2e/` 디렉토리 (기존 spec 존재) |
| Quick run command | `cd frontend && npm run test:e2e` |
| Full suite command | `cd frontend && npm run test:e2e` |

**단위 테스트 프레임워크 미설치** (vitest, jest 없음). 프로젝트에는 Playwright E2E만 존재한다.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| RULE-06 | 룰 복제 버튼 클릭 시 DRAFT 복사본 생성 | E2E | Playwright spec | ❌ Wave 0 |
| RULE-07 | 빌더에서 cooldownBars 입력 후 저장 시 RuleDefinition에 반영 | E2E | Playwright spec | ❌ Wave 0 |
| (빌더 핵심) | 새 룰 생성 → 저장 → rules 페이지 리다이렉트 | E2E | Playwright spec | ❌ Wave 0 |
| (빌더 핵심) | 기존 룰 편집 → 폼 역직렬화 → 저장 | E2E | Playwright spec | ❌ Wave 0 |
| (탭 전환) | 빌더→JSON 탭 전환 시 JSON 동기화 | manual | N/A | manual-only |

**manual-only 항목 이유:** JSON 탭의 textarea 내용 검증은 Playwright로 가능하지만, 직렬화 정확성은 수동 육안 확인이 더 효율적.

### Sampling Rate
- **Per task commit:** TypeScript 컴파일 확인 `cd /Users/heojeonghun/Desktop/New_Graph/frontend && npx tsc --noEmit`
- **Per wave merge:** 수동 브라우저 검증 (dev server 기동 후 flows 확인)
- **Phase gate:** 모든 성공 기준 4개 수동 검증 완료

### Wave 0 Gaps
- [ ] `frontend/e2e/rule-builder.spec.ts` — 빌더 생성/편집/저장 flows 커버
- [ ] Playwright 기존 설정으로 실행 가능 여부 확인 (`playwright.config.ts` 존재 확인 필요)

---

## Sources

### Primary (HIGH confidence)
- 직접 코드 분석: `frontend/src/types/trading.ts` — RuleDefinition, RuleIndicator, RuleOperator 타입 전체
- 직접 코드 분석: `frontend/src/lib/ruleApi.ts` — CRUD API 함수 시그니처
- 직접 코드 분석: `frontend/src/lib/paperApi.ts` — copyRule 포함 lifecycle API
- 직접 코드 분석: `frontend/src/pages/trading/paper/PaperRulesPage.tsx` — 기존 모달 패턴, saveMutation
- 직접 코드 분석: `frontend/src/pages/trading/TradingRulesPage.tsx` — STATUS_CONFIG, StatusBadge, RuleActions
- 직접 코드 분석: `frontend/src/router/index.tsx` — 현재 라우트 구조, ModeGuard 사용 패턴
- 직접 코드 분석: `frontend/src/layouts/TradingLayout.tsx` — paperItems nav, 사이드바 구조
- 직접 코드 분석: `backend/src/main/java/com/graphify/trading/rule/definition/RuleDefinition.java` — 백엔드 스키마
- 직접 코드 분석: `backend/src/main/java/com/graphify/trading/rule/definition/RuleDefinitionValidator.java` — 검증 규칙 전체
- 직접 코드 분석: `.planning/phases/06-rule-builder-ui/06-CONTEXT.md` — 잠금 결정 사항 전체
- 직접 코드 분석: `frontend/package.json` — 설치된 의존성 목록

### Secondary (MEDIUM confidence)
- Playwright docs 기반: E2E 테스트 접근법

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — package.json 직접 확인
- Architecture: HIGH — 기존 코드베이스 패턴 직접 분석
- Type Updates: HIGH — trading.ts vs RuleDefinition.java 직접 비교
- Pitfalls: HIGH — 기존 코드에서 직접 식별
- RULE-07 cooldown 동적 표시: MEDIUM — 백엔드 API 현황 기반이나 설계 결정 필요

**Research date:** 2026-06-22
**Valid until:** 2026-07-22 (stable stack, 30일)
