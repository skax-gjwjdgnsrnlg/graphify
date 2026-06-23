---
status: testing
phase: 06-rule-builder-ui
source: 06-01-SUMMARY.md, 06-02-SUMMARY.md
started: 2026-06-22T00:00:00Z
updated: 2026-06-22T00:00:00Z
---

## Current Test

number: 1
name: New Rule Page Load
expected: |
  Navigate to /trading/paper/rules/new (or click the create button from PaperRulesPage).
  The full Rule Builder form renders with all sections visible:
  룰 이름 (text input), 유니버스 (radio toggle), 진입 조건 (logic + condition rows + add button),
  청산 조건 (takeProfitPct + stopLossPct inputs), 사이징 (type select + value input),
  제약 (cooldownBars + maxPositionsPerSymbol inputs).
  Page uses dark Tailwind card layout.
awaiting: user response

## Tests

### 1. New Rule Page Load
expected: Navigate to /trading/paper/rules/new (or click the create button from PaperRulesPage). The full Rule Builder form renders with all sections visible: 룰 이름 (text input), 유니버스 (radio toggle), 진입 조건 (logic + condition rows + add button), 청산 조건 (takeProfitPct + stopLossPct inputs), 사이징 (type select + value input), 제약 (cooldownBars + maxPositionsPerSymbol inputs). Page uses dark Tailwind card layout.
result: [pending]

### 2. Universe Type Toggle
expected: In the 유니버스 section, clicking the "symbols" radio shows a single comma-separated symbol input. Clicking the "volume_top_n" radio replaces it with a topN number input and an additionalSymbols input. Switching back to "symbols" restores the original input.
result: [pending]

### 3. Entry Conditions: Add and Remove Rows
expected: The 진입 조건 section starts with at least one condition row. Clicking the add button appends a new empty row. Each row has: left indicator select, period input, operator select, right-side type/value inputs. Clicking remove on a row deletes it.
result: [pending]

### 4. Builder → JSON Tab Toggle
expected: Fill in some fields (name, a universe type, one condition). Click the "JSON" tab. A pretty-printed JSON textarea appears showing the serialized RuleDefinition matching what was entered in the form. The Builder tab content is hidden.
result: [pending]

### 5. JSON → Builder Tab Toggle
expected: While on the JSON tab, modify the JSON (or leave valid JSON). Click the "Builder" tab. The form fields reflect the parsed values. If the JSON is malformed/invalid, an error message is shown instead of switching tabs.
result: [pending]

### 6. Save Validation
expected: Try to save with: (a) empty rule name → error shown, (b) no complete entry condition (leftIndicator or operator not set) → error shown, (c) blank or non-numeric sizingValue → error shown. The page does NOT navigate away on validation failure.
result: [pending]

### 7. Create Rule and Navigate
expected: Fill in valid data (name, symbols universe, one valid entry condition with leftIndicator + operator set, sizing value). Click save. The rule is created and the page navigates to /trading/paper/rules. The new rule appears in the list.
result: [pending]

### 8. PaperRulesPage: Create Button Navigates (No Modal)
expected: On /trading/paper/rules, clicking the "새 룰 만들기" (or equivalent create) button navigates to /trading/paper/rules/new. No inline JSON modal appears — it should be a full page navigation.
result: [pending]

### 9. PaperRulesPage: Edit Button Navigates (No Modal)
expected: On /trading/paper/rules, clicking the "편집" button on an existing rule navigates to /trading/paper/rules/edit/:id. The Rule Builder form pre-populates with that rule's existing data.
result: [pending]

### 10. PaperRulesPage: Copy Button
expected: Clicking the "복제" button on a rule creates a DRAFT copy of it. The rules list refreshes and shows the new copied rule (likely with a similar name, in DRAFT status).
result: [pending]

### 11. PaperRulesPage: Cooldown Column
expected: The rules table has a 쿨다운 column. Rules that have cooldownBars set show the format "N봉 (Xm)" (e.g., 3봉 (15m)). Rules with no cooldown (or cooldownBars = 0) show "—".
result: [pending]

## Summary

total: 11
passed: 0
issues: 0
pending: 11
skipped: 0

## Gaps

[none yet]
