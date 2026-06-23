<!-- OMC:START -->
<!-- OMC:VERSION:4.9.1 -->

# oh-my-claudecode - Intelligent Multi-Agent Orchestration

You are running with oh-my-claudecode (OMC), a multi-agent orchestration layer for Claude Code.
Coordinate specialized agents, tools, and skills so work is completed accurately and efficiently.

<operating_principles>
- Delegate specialized work to the most appropriate agent.
- Prefer evidence over assumptions: verify outcomes before final claims.
- Choose the lightest-weight path that preserves quality.
- Consult official docs before implementing with SDKs/frameworks/APIs.
</operating_principles>

<delegation_rules>
Delegate for: multi-file changes, refactors, debugging, reviews, planning, research, verification.
Work directly for: trivial ops, small clarifications, single commands.
Route code to `executor` (use `model=opus` for complex work). Uncertain SDK usage → `document-specialist` (repo docs first; Context Hub / `chub` when available, graceful web fallback otherwise).
</delegation_rules>

<model_routing>
`haiku` (quick lookups), `sonnet` (standard), `opus` (architecture, deep analysis).
Direct writes OK for: `~/.claude/**`, `.omc/**`, `.claude/**`, `CLAUDE.md`, `AGENTS.md`.
</model_routing>

<skills>
Invoke via `/oh-my-claudecode:<name>`. Trigger patterns auto-detect keywords.
Tier-0 workflows include `autopilot`, `ultrawork`, `ralph`, `team`, and `ralplan`.
Keyword triggers: `"autopilot"→autopilot`, `"ralph"→ralph`, `"ulw"→ultrawork`, `"ccg"→ccg`, `"ralplan"→ralplan`, `"deep interview"→deep-interview`, `"deslop"`/`"anti-slop"`→ai-slop-cleaner, `"deep-analyze"`→analysis mode, `"tdd"`→TDD mode, `"deepsearch"`→codebase search, `"ultrathink"`→deep reasoning, `"cancelomc"`→cancel.
Team orchestration is explicit via `/team`.
Detailed agent catalog, tools, team pipeline, commit protocol, and full skills registry live in the native `omc-reference` skill when skills are available, including reference for `explore`, `planner`, `architect`, `executor`, `designer`, and `writer`; this file remains sufficient without skill support.
</skills>

<verification>
Verify before claiming completion. Size appropriately: small→haiku, standard→sonnet, large/security→opus.
If verification fails, keep iterating.
</verification>

<execution_protocols>
Broad requests: explore first, then plan. 2+ independent tasks in parallel. `run_in_background` for builds/tests.
Keep authoring and review as separate passes: writer pass creates or revises content, reviewer/verifier pass evaluates it later in a separate lane.
Never self-approve in the same active context; use `code-reviewer` or `verifier` for the approval pass.
Before concluding: zero pending tasks, tests passing, verifier evidence collected.
</execution_protocols>

<hooks_and_context>
Hooks inject `<system-reminder>` tags. Key patterns: `hook success: Success` (proceed), `[MAGIC KEYWORD: ...]` (invoke skill), `The boulder never stops` (ralph/ultrawork active).
Persistence: `<remember>` (7 days), `<remember priority>` (permanent).
Kill switches: `DISABLE_OMC`, `OMC_SKIP_HOOKS` (comma-separated).
</hooks_and_context>

<cancellation>
`/oh-my-claudecode:cancel` ends execution modes. Cancel when done+verified or blocked. Don't cancel if work incomplete.
</cancellation>

<worktree_paths>
State: `.omc/state/`, `.omc/state/sessions/{sessionId}/`, `.omc/notepad.md`, `.omc/project-memory.json`, `.omc/plans/`, `.omc/research/`, `.omc/logs/`
</worktree_paths>

## Setup

Say "setup omc" or run `/oh-my-claudecode:omc-setup`.

<!-- OMC:END -->

<!-- User customizations (migrated from previous CLAUDE.md) -->
# Global Development Guidelines

> 이 파일은 모든 프로젝트에 적용되는 개인 개발 원칙과 워크플로우를 정의합니다.

## 🎯 Core Development Philosophy

### 1. Research → Plan → Implement 워크플로우

**항상 코드 작성 전에:**
- 기존 코드베이스를 먼저 분석
- 저장소 내 패턴과 관례 파악
- 유사한 구현 사례 검색
- Training knowledge보다 저장소 우선

**Plan-First 접근:**
- 복잡한 기능은 Plan Mode 사용 (`/plan`)
- 단계별 구현 계획 수립
- 성공 기준을 명확히 정의
- 각 단계마다 검증

**점진적 구현:**
- 작은 단위로 나누어 구현
- 각 단계마다 테스트 실행
- 작동 확인 후 다음 단계 진행
- 의미 있는 단위로 커밋

## 🤖 Multi-Agent Orchestration (oh-my-claude-sisyphus)

### 주요 에이전트 활용

**코드베이스 분석:**
- `Oracle`: 아키텍처 분석, 디버깅
- `Librarian`: 문서 조사, 패턴 연구
- `analyze: <주제>`: 심화 분석 실행

**계획 및 설계:**
- `Prometheus`: 전략적 계획 수립
- `/deep-interview "<아이디어>"`: 요구사항 명확화
- `Momus`: 계획 검토 및 비판적 평가

**구현:**
- `Sisyphus`: 메인 조정자 (전체 워크플로우 관리)
- `autopilot: <작업>`: 완전 자동 실행
- `ultrawork: <작업>`: 병렬 실행 (여러 에이전트 동시 작동)
- `ralph: <작업>`: 지속성 모드 (끝까지 완료)

### Team 기반 파이프라인

```
/team-plan      # 계획 수립
→ /team-prd     # 요구사항 문서화
→ /team-exec    # 구현 실행
→ /team-verify  # 검증
→ /team-fix     # 수정
```

**개별 팀원 호출:**
```bash
/team 3:executor "특정 작업"
omc team 2:codex "코드 리뷰"
```

## 📋 Code Quality Principles

### 일관성 우선
- 기존 코드 스타일을 존중
- 개인 선호보다 프로젝트 일관성 우선
- 새로운 패턴 도입 전에 기존 패턴 확인

### 보안 및 성능
- API 키, 토큰, 비밀번호를 코드에 절대 작성 금지
- 민감한 정보는 `.env` 파일 사용
- 성능에 영향을 주는 변경은 벤치마크 필수

### 테스트
- 새 기능 추가 시 테스트 작성
- 리팩토링 전 기존 테스트 확인
- 테스트 실패 시 구현 중단

## 🔄 Context Management

### 효율적인 컨텍스트 사용
- 컨텍스트 50% 도달 시 `/compact` 실행
- 복잡한 작업은 여러 세션으로 분리
- 연구 결과를 문서화하여 재사용

### 세션 관리
- 작업 중단 전 `/save-progress` (sisyphus)
- 재개 시 `/resume-work`
- 중요한 결정은 CLAUDE.md 또는 별도 문서에 기록

## 🎨 Communication Style

### 명확하고 간결하게
- 불필요한 설명 지양
- 코드 위치는 `file_path:line_number` 형식
- 작업 완료 시 간단히 요약

### 에러 처리
- 같은 시도를 반복하지 않음
- 실패 시 대안 접근법 고려
- 막히면 사용자에게 질문

## 🚫 Anti-Patterns (절대 금지)

- ❌ 코드를 읽지 않고 제안
- ❌ CLAUDE.md에 80줄 이상 작성 (글로벌)
- ❌ "senior engineer처럼 행동하라" 같은 성격 지시
- ❌ 불필요한 파일 생성
- ❌ 요청하지 않은 리팩토링
- ❌ 린터 작업을 LLM에 맡기기
- ❌ 브루트포스 재시도 (API 실패 시 같은 요청 반복)

## 🔧 Essential Commands & Tools

### CLI 도구 우선 사용
- **MCP 서버보다 CLI 도구 선호** - 컨텍스트 효율적
- `gh` CLI로 PR, 이슈, 코멘트 처리 (설치됨)
- `jq`, `curl` 등 표준 CLI 도구 적극 활용
- 모르는 도구는 `--help` 출력 읽고 학습 후 실행

### 분석 및 계획
- `/plan` - Plan Mode 진입 (읽기 전용)
- `/deep-interview "<주제>"` - 요구사항 명확화
- `analyze: <대상>` - 심화 분석

### 멀티 에이전트 실행
- `autopilot: <작업>` - 자동 실행
- `ultrawork: <작업>` - 병렬 실행
- `ralph: <작업>` - 지속 실행

### 유틸리티
- `/compact` - 컨텍스트 압축
- `/help` - 도움말
- `cc` - Claude Code 빠른 시작 (권한 프롬프트 스킵)

## 📊 Model Selection Guidelines

**자동 라우팅 (sisyphus):**
- Haiku: 단순 작업 (파일 읽기, 간단한 수정)
- Sonnet: 일반 작업 (기능 구현, 리팩토링)
- Opus: 복잡한 작업 (아키텍처 설계, 디버깅)

**수동 지정 (필요시):**
- 빠른 반복: Haiku
- 균형: Sonnet (기본)
- 최고 품질: Opus

## 📄 Documentation Management Rules (프로젝트 규칙)

문서는 종류별로 **단일 파일**에 통합 관리한다. 기능/주제별로 새 md를 만들지 않는다.

- **설계 문서**: `DESIGN.md` 하나로 관리. 새 기능 설계는 최신 항목을 위로 추가하고 `## [vX.Y.Z] 제목` + Status 형식으로 작성.
- **릴리즈 이력**: `RELEASE_NOTES.md` 하나로 관리. 버전별 항목을 최신이 위로 오도록 누적.
- 설계가 구현·배포되면 `DESIGN.md`의 Status를 갱신하고 `RELEASE_NOTES.md`에 반영한다.
- 별도 분리 문서(`DESIGN_*.md` 등)를 만들지 말 것.

## 🎨 Design-in-the-Loop GSD (프로젝트 규칙)

UI가 있는 기능은 GSD 게이트마다 아래 디자인 활동을 **반드시** 수행한다. 새 워크플로를 만들지 말고 기존 게이트에 끼워 넣는다.

**디자인 SoT는 이미 로컬에 있다**: `frontend/src/components/shared/`(프리미티브 카탈로그) + `tailwind.config.js`(토큰). 새 디자인 시스템을 만들지 말고 이 둘을 단일 진실 공급원으로 쓴다. claude.ai/design 연동(DesignSync)은 현재 불필요.

**핵심 규칙 — shared 우선**: 새 화면은 raw `<button>`/직접 스타일 대신 `shared/`의 컴포넌트를 먼저 쓴다. 색/간격/타이포/그림자는 `tailwind.config.js` 토큰만 사용(하드코딩 hex 금지). 필수 재사용: `PrimaryButton`·`GhostButton`(버튼), `TextField`·`PasswordField`(입력), `PageState`/`EmptyState`/`ErrorBanner`/`InlineError`/`SkeletonBlock`(빈·로딩·에러 상태), `Pagination`.

게이트별 활동:
- **`plan-phase`**: PLAN.md에 `## UI/UX` 절 추가 — 화면 인벤토리(페이지 + 빈/로딩/에러/성공 상태), shared 재사용 매핑(재사용 vs 신규, 신규는 사유), 반응형/키보드·포커스 기준. 설계 본문은 `DESIGN.md` 해당 `[vX.Y.Z]` 항목 아래 "UI/UX" 하위 절에 기록. 복잡한 신규 컴포넌트는 `oh-my-claudecode:designer`에게 위임.
- **`execute-phase`**: shared 카탈로그에서 가져다 구현. 신규 공통 컴포넌트는 `shared/`에 추가(기능 폴더에 묻지 않는다). 작성과 리뷰는 별도 패스.
- **`verify-work`**: 기능 UAT에 더해 시각 검증을 게이트로 둔다 — `browse`로 빈/로딩/에러/성공 4개 상태를 전수 확인하고, `visual-verdict`로 PLAN.md `## UI/UX` 인벤토리와 실제 화면을 대조. shared 미사용·토큰 일탈이 보이면 불합격. UAT.md 수용 기준에 시각 항목을 명시한다.

## 📚 Learning & Improvement

### 자가 개선 루프
- 작업 완료 후 "CLAUDE.md 업데이트" 제안
- 반복되는 패턴 발견 시 문서화
- 실수나 비효율 발견 시 원칙에 반영

### 프로젝트 학습
- 각 프로젝트의 CLAUDE.md는 별도 관리
- 프로젝트별 특수 규칙은 로컬 파일에만 작성
- 범용적인 원칙만 이 파일에 포함

---

**Version:** 1.0.0
**Last Updated:** 2026-03-24
**Framework:** oh-my-claude-sisyphus v4.5+
