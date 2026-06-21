---
phase: 6
slug: rule-builder-ui
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-22
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Playwright ^1.51.0 (E2E only) |
| **Config file** | `frontend/e2e/` 디렉토리 (기존 spec 존재) |
| **Quick run command** | `cd frontend && npx tsc --noEmit` |
| **Full suite command** | `cd frontend && npm run test:e2e` |
| **Estimated runtime** | ~60 seconds (E2E) |

---

## Sampling Rate

- **After every task commit:** Run `cd /Users/heojeonghun/Desktop/New_Graph/frontend && npx tsc --noEmit`
- **After every plan wave:** 수동 브라우저 검증 (dev server 기동 후 flows 확인)
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 6-01-01 | 01 | 1 | RULE-06 | E2E | `cd frontend && npm run test:e2e -- --grep "rule-builder"` | ❌ W0 | ⬜ pending |
| 6-01-02 | 01 | 1 | RULE-07 | E2E | `cd frontend && npm run test:e2e -- --grep "cooldownBars"` | ❌ W0 | ⬜ pending |
| 6-01-03 | 01 | 1 | RULE-06 | type-check | `cd frontend && npx tsc --noEmit` | ✅ | ⬜ pending |
| 6-02-01 | 02 | 2 | RULE-06 | E2E | `cd frontend && npm run test:e2e -- --grep "rule-builder"` | ❌ W0 | ⬜ pending |
| 6-02-02 | 02 | 2 | RULE-07 | E2E | `cd frontend && npm run test:e2e -- --grep "edit-deserialize"` | ❌ W0 | ⬜ pending |
| 6-02-03 | 02 | 2 | RULE-06 | type-check | `cd frontend && npx tsc --noEmit` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/e2e/rule-builder.spec.ts` — 빌더 생성/편집/저장 flows 커버 (RULE-06, RULE-07)
- [ ] Playwright 기존 설정으로 실행 가능 여부 확인 (`playwright.config.ts` 존재 확인)

*Wave 0 must be completed before plan wave execution begins.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 빌더→JSON 탭 전환 시 JSON 동기화 | (빌더 핵심) | 직렬화 정확성 육안 확인이 효율적 | 1) 빌더에서 룰 구성 2) JSON 탭 클릭 3) JSON 내용이 빌더 폼과 일치하는지 확인 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
