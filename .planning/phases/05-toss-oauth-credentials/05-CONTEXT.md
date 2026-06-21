---
phase: "05"
title: "토스증권 OAuth & 자격증명 관리"
---

# Phase 5 Context: 토스증권 OAuth & 자격증명 관리

## Goal

사용자가 토스증권 client_id와 client_secret을 안전하게 등록하고, 시스템이 OAuth 액세스 토큰을 자동 발급·갱신하며, 연동된 실계좌 잔고를 대시보드에서 조회할 수 있다.

## Key Existing Infrastructure

### Encryption
- `SecretEncryptionService` (`com.graphify.common.security`) — AES-256-GCM, IV prepended Base64
  - `encrypt(String plainText)` → Base64(IV + ciphertext)
  - `decrypt(String encrypted)` → plainText
  - Already used by OpenAI settings for API key storage

### Credential pattern (OpenAI model)
- Entity stores `*_encrypted` TEXT columns
- Service calls `secretEncryptionService.encrypt()` before save, `decrypt()` before use
- DTO exposes `boolean hasKey` (never the raw key)
- Singleton row pattern (one settings row per user)

### Migration state
- Latest migration: V33 (rule_backtested_flag)
- Next: V34 for toss_credentials table

### Frontend
- TradingLayout: sidebar nav with `commonItems` + mode-switched `paperItems`/`liveItems`
- Router: `/trading/*` routes under `TradingLayout`
- Settings nav item should be added to `commonItems` (mode-independent)
- Pattern: `fetchPaperDashboard` → `apiGet`, `promoteRule` → `apiPost`

### Toss Securities OAuth (토스증권 Open API)
- OAuth 2.0 Client Credentials flow
- Token URL: `https://openapi.tossinvest.com/api/v1/oauth2/token`
- Grant type: `client_credentials`
- Request: POST with `client_id`, `client_secret`, `grant_type`
- Response: `{ access_token, token_type, expires_in }`
- Token TTL: typically 24 hours; pre-emptive refresh 10 min before expiry

### Balance API
- GET `https://openapi.tossinvest.com/api/v1/accounts`
- Header: `Authorization: Bearer {access_token}`
- Returns account list with `accountNumber`, `balance`, `availableBalance`

## Decisions (from ROADMAP/STATE)
- AES-256-GCM via SecretEncryptionService for Toss token storage (already implemented)
- `toss_credentials` table: one row per userId — stores encrypted client_id, client_secret, access_token
- Token refresh: scheduled task checks expiry, refreshes 10 min before
- No plaintext in DB at any point
