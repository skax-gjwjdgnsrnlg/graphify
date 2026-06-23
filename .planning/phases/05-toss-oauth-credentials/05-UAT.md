---
status: testing
phase: 05-toss-oauth-credentials
source: [05-01-SUMMARY.md, 05-02-SUMMARY.md]
started: 2026-06-21T00:00:00Z
updated: 2026-06-21T00:00:00Z
---

## Current Test

number: 1
name: 토스 설정 페이지 도달
expected: |
  사이드바에서 "토스 설정" 메뉴를 클릭하면 /trading/settings 로 이동한다.
  PAPER 모드와 LIVE 모드 모두에서 메뉴가 보이고 동일하게 접근 가능하다.
  페이지에 client_id / client_secret 입력 폼이 표시된다.
awaiting: user response

## Tests

### 1. 토스 설정 페이지 도달
expected: 사이드바에서 "토스 설정" 메뉴를 클릭하면 /trading/settings 로 이동한다. PAPER 모드와 LIVE 모드 모두에서 메뉴가 보이고 동일하게 접근 가능하다. 페이지에 client_id / client_secret 입력 폼이 표시된다.
result: [pending]

### 2. 자격증명 미등록 상태 배지
expected: 토스 설정 페이지를 처음 열면 자격증명 상태 배지가 "미설정" (회색)으로 표시된다. 토큰 갱신 버튼은 비활성화되어 있거나 자격증명 저장 후에만 활성화된다.
result: [pending]

### 3. 자격증명 저장
expected: client_id와 client_secret을 입력하고 저장 버튼을 클릭하면 에러 없이 성공한다. 저장 직후 상태 배지가 "설정됨" (초록 또는 노랑)으로 바뀐다.
result: [pending]

### 4. 토큰 수동 갱신
expected: 자격증명이 등록된 상태에서 "토큰 수동 갱신" 버튼을 클릭하면 토스 OAuth 토큰 발급이 시도된다. 성공 시 배지가 "토큰 유효" 상태(초록)로 바뀌고 만료 시각(KST)이 표시된다. (실제 토스 API 자격증명이 없으면 실패 응답이 오지만 UI가 오류 메시지를 보여주면 정상)
result: [pending]

### 5. 모의 대시보드 토스 잔고 섹션
expected: 모의 대시보드(/trading/paper/dashboard) 하단에 "토스 실계좌 잔고" 접이식 섹션이 있다. 자격증명·토큰이 없으면 "토스 설정" 링크가 포함된 빈 상태 안내가 표시된다. 자격증명+토큰이 있으면 계좌 목록이 표시된다.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0

## Gaps

[none yet]
