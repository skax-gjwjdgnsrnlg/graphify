# 자동매매 Bot 개발 스펙

> 이 문서는 개발 시 참조용입니다. 설계 원칙과 구현 세부사항을 담고 있습니다.

---

## 1. 진입 방식 (이스터에그)

- 메인 홈페이지 기업 검색창에 `/gg` 입력 후 엔터
- 다크모드로 전환 + `/trading` 경로로 navigate
- 구현 위치: `frontend/src/components/shared/GlobalSearchBar.tsx`의 `handleSubmit`
  - `query.trim() === '/gg'` 감지 → 다크모드 store 활성화 → `navigate('/trading')`

---

## 2. 라우팅 구조

```
/trading                    → TradingLayout (사이드바 포함, 다크모드 전용)
  /trading/dashboard        → 봇 상태, 잔고, 수익률 요약
  /trading/history          → 거래 이력
  /trading/rules            → 현재 활성 룰 조회
  /trading/rules/edit       → 룰 수정/튜닝
  /trading/monitor          → 실시간 봇 동작 모니터링
```

- 기존 `AdminLayout` 패턴을 복제하여 `TradingLayout` 생성
- `/trading/*` 전체 다크모드 강제 적용

---

## 3. 프론트엔드 구현 목록

### 레이아웃
- `frontend/src/layouts/TradingLayout.tsx` — 사이드바 + 콘텐츠 영역, 다크모드 강제

### 페이지
- `frontend/src/pages/trading/TradingDashboardPage.tsx`
- `frontend/src/pages/trading/TradingHistoryPage.tsx`
- `frontend/src/pages/trading/TradingRulesPage.tsx`
- `frontend/src/pages/trading/TradingRulesEditPage.tsx`
- `frontend/src/pages/trading/TradingMonitorPage.tsx`

### 컴포넌트
- `frontend/src/components/trading/TradingSidebar.tsx`
- `frontend/src/components/trading/BotStatusCard.tsx` — 봇 ON/OFF, 일손실, 보유종목
- `frontend/src/components/trading/TradeHistoryTable.tsx`
- `frontend/src/components/trading/RuleViewer.tsx`
- `frontend/src/components/trading/RuleEditor.tsx`
- `frontend/src/components/trading/MonitorLogStream.tsx` — 실시간 로그/이벤트 스트림

---

## 4. 백엔드 모듈 구조 (Spring Boot)

### 설계 원칙
> **LLM Agent는 복기/설명 담당. 실제 주문 판단은 룰 엔진이 전담.**

```
Market Data Collector
  → Signal Engine
    → Risk Manager
      → Order Executor
        → Position Monitor
          → Trade Logger
            → LLM Review Agent (복기 전용)
```

### 패키지 구조 (예상)
```
backend/src/main/kotlin/com/graphify/
  trading/
    collector/    MarketDataCollector   실시간 체결·호가·캔들·시장지수
    signal/       SignalEngine          룰 기반 매수/매도 신호 생성
    risk/         RiskManager           손절·일손실·중복진입·제외종목 체크
    order/        OrderExecutor         토스증권 API 주문 실행
    position/     PositionMonitor       체결·잔고·미체결 주문 추적
    logger/       TradeLogger           모든 의사결정 영구 기록
    review/       LlmReviewAgent        매매 후 복기 및 룰 개선안 제안
    api/          TradingController     프론트엔드 REST API
```

### REST API 엔드포인트
```
GET  /api/trading/status          봇 상태 (ON/OFF, 일손실, 보유종목)
GET  /api/trading/history         거래 이력 (페이징)
GET  /api/trading/rules           현재 룰 조회
PUT  /api/trading/rules           룰 수정
GET  /api/trading/monitor/logs    최근 로그 (SSE 또는 폴링)
POST /api/trading/bot/start       봇 시작
POST /api/trading/bot/stop        봇 중단
```

---

## 5. 토스증권 API 연동

활용 가능 항목:
- 실시간 체결/호가
- 캔들 데이터
- 주문 (지정가/시장가/취소/정정)
- 계좌 조회 (잔고, 보유종목)
- 종목·시장정보

---

## 6. 매매 전략 (MVP: 전략 1개로 시작)

### MVP 전략명: KOSPI 거래대금 급증 + VWAP 돌파 전략

#### 종목 후보 필터
| 조건 | 기준 |
|------|------|
| 시장 | KOSPI 보통주 |
| 제외 | ETF/ETN/우선주/스팩/관리종목/거래정지/투자주의·경고·위험/단기과열 |
| 시가총액 | 5,000억 원 이상 |
| 당일 거래대금 | KOSPI 상위 30위 |
| 상대거래량 | 같은 시간 기준 20일 평균 대비 2.5배 이상 |
| 당일 등락률 | +2% ~ +10% |
| 호가 스프레드 | 0.3% 이하 |
| 현재가 위치 | VWAP 위 |

#### 종목 스코어링
```
종목점수 =
  30점 × 상대거래량 점수
+ 25점 × 당일 거래대금 순위 점수
+ 20점 × 가격 모멘텀 점수
+ 15점 × VWAP 상회 점수
+ 10점 × 호가/체결강도 점수
```

#### 매수 조건 (5개 동시 충족)
1. 현재가 > VWAP
2. 5분봉 종가가 장중 직전 고점 돌파
3. 돌파봉 거래량 > 직전 5분봉 평균 × 1.5
4. 현재가가 VWAP보다 0.5% 이상 위
5. KOSPI가 당일 -1.0% 이하 급락 중이 아님

#### 매도 조건
```
손절:
  - 진입가 -1.2%
  - 또는 VWAP 하향 이탈

익절:
  - +2.0% 도달 시 50% 매도
  - +3.5% 도달 시 30% 추가 매도
  - 잔량: 고점 대비 -1.0% 하락 시 전량 매도

시간청산:
  - 14:50 이후 신규 매수 금지
  - 15:10까지 미청산 포지션 정리
```

---

## 7. 리스크 관리 룰 (하드코딩 우선, 이후 UI에서 수정 가능)

### 계좌 단위
| 항목 | 기준 |
|------|------|
| 1일 최대 손실 | 총자산 -1.0% 도달 시 당일 매매 중단 |
| 1일 최대 거래 횟수 | 5회 |
| 동시 보유 종목 | 최대 2개 |
| 종목당 최대 투입 | 총자산의 20~30% |
| 전략별 연속 손실 | 3회 발생 시 해당 전략 자동 중단 |

### 종목 단위
| 항목 | 기준 |
|------|------|
| 1회 거래 손실 한도 | 총자산의 -0.3% ~ -0.5% |
| 기본 손절 | 진입가 -1.0% ~ -1.5% |
| 변동성 큰 종목 | 5분 ATR 기준 손절 |
| 수익 후 손절선 | 진입가 이상으로 상향 (트레일링) |

### 매매 시간 제한
| 시간대 | 동작 |
|--------|------|
| 09:00~09:05 | 신규진입 금지 |
| 09:05~10:30 | 주력 매매 |
| 10:30~13:30 | 신규진입 축소 |
| 13:30~14:50 | 선별 매매 |
| 14:50 이후 | 신규진입 금지 |
| 15:10 이후 | 보유분 정리 우선 |

### 강제 중단 조건
- 하루 손실 -1.0% 도달
- 3연속 손절
- KOSPI 장중 -1.0% 이하 급락
- API 오류 / 체결 지연 / 잔고 불일치 발생

---

## 8. 반드시 제외할 종목 조건
| 제외 조건 | 이유 |
|-----------|------|
| 투자주의/경고/위험 종목 | 급등락·거래정지 리스크 |
| 단기과열 예고/지정 종목 | 30분 단일가 전환 가능성 |
| 관리종목/불성실공시 | 이벤트 리스크 |
| 호가 스프레드 과도 | 슬리피지 손실 |
| 거래대금 부족 | 청산 실패 |
| 장 초반 3분 (09:00~09:03) | 호가 왜곡 심함 |
| 장 마감 10분 전 신규진입 | 변동성·체결 리스크 |
| 상한가 근접 추격 | 리스크 대비 기대수익 낮음 |
| 전일 시간외 급등 후 당일 거래대금 부진 | 갭하락/설거지 가능성 |

---

## 9. 추가 전략 (MVP 이후 단계적 추가)

### 전략 2: Opening Range Breakout
- 관찰: 09:00~09:15 고점/저점 형성
- 진입: 09:15~10:30 사이 고점 돌파 + 거래량 2배 이상 + VWAP 위

### 전략 3: 거래대금 급증 + VWAP 눌림목 재진입
- 당일 +3% 이상 상승 후 1~3% 조정 → VWAP 근처 반등 시 재진입

### 전략 4: 대장주 추종 (섹터 2등주)
- 업종 상승률 1위 섹터 탐색 → 대장주 돌파 확인 → 2등주 눌림목 진입

---

## 10. 개발 단계 (우선순위 순)

1. **Phase 1 - 진입점 + 라우팅**
   - `/gg` 이스터에그 → 다크모드 + `/trading` navigate
   - `TradingLayout` + 사이드바 + 빈 페이지 5개

2. **Phase 2 - 백엔드 기반**
   - 토스증권 API 연동 (시세·캔들·계좌)
   - MarketDataCollector, TradeLogger DB 스키마

3. **Phase 3 - 신호 감지 (읽기 전용)**
   - SignalEngine 구현
   - 매수 신호 탐지 → 알림만 발송 (자동 주문 OFF)
   - 대시보드, 모니터링 페이지 연동

4. **Phase 4 - 자동 주문**
   - RiskManager + OrderExecutor
   - 소액 실거래 테스트

5. **Phase 5 - 룰 수정 UI + 리뷰 Agent**
   - RuleEditor 페이지
   - LlmReviewAgent (매매 복기, 개선안 제안)
