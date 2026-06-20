# Phase 0: 데이터 인프라 & 동적 유니버스 - Research

**Researched:** 2026-06-20
**Domain:** Spring Boot data ingestion, JPA entity evolution, dynamic universe selection, Flyway migrations
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DATA-01 | companies 테이블에 `in_kospi200` 플래그 추가, KOSPI 200 종목 리스트 관리 | Flyway V30 ALTER TABLE + data seed SQL; Company entity + CompanyRepository 확장 패턴 확인 |
| DATA-02 | KOSPI 200 전체 종목 2년치 일봉 OHLCV 수집·유지 | `ingestDaily(symbol)` 이미 완전 동작; `in_kospi200=true` 종목 루프만 추가하면 됨 |
| DATA-03 | `RuleDefinition.Universe`에 `volume_top_n` 타입 + `additionalSymbols` 지원 | Universe record에 필드 2개 추가, `@JsonIgnoreProperties` 이미 적용돼 하위 호환 무료 |
| DATA-04 | 백테스트 시 날짜별 KOSPI 거래량 상위 10종목 동적 선정 | `MarketBarRepository`에 JPQL 쿼리 1개 추가; BacktestService 진입점 분기만 필요 |
| DATA-05 | BacktestService volume null 전달 버그 수정 | Line 117/128: `null` → `volumes` 배열; DbMarketDataAdapter는 이미 volume 정상 변환 중 |
</phase_requirements>

---

## Summary

Phase 0은 순수 백엔드 작업이며 신규 외부 의존성이 없다. 모든 핵심 인프라(Yahoo 클라이언트, MarketBar 엔티티, PaperLedger, RuleEvaluator)가 이미 존재하고, 이번 phase는 4개의 외과적 변경으로 구성된다.

**DATA-05(volume null 버그)**는 가장 단순하고 즉각적인 수정이다. `BacktestService.run()` 내부에서 `closes` 배열과 동일한 방식으로 `volumes` 배열을 추출한 뒤 `evaluator.entryTriggered(..., null, ...)` 및 `evaluator.exitTriggered(..., null, ...)` 호출에 전달하면 된다. `DbMarketDataAdapter`는 이미 `b.getVolume().doubleValue()`로 올바르게 변환하고 있으므로 데이터 경로는 이미 완전하다.

**DATA-01~02**는 Flyway V30 마이그레이션 한 파일로 처리할 수 있다. `companies` 테이블에 `in_kospi200 BOOLEAN NOT NULL DEFAULT FALSE` 컬럼을 추가하고, KOSPI 200 종목 초기 데이터를 동일 파일에 `UPDATE` 문으로 삽입한다. `MarketDataIngestionService`에는 `ingestDailyForKospi200()` 메서드를 추가하여 `companyRepository.findByInKospi200True()`를 순회하면 된다.

**DATA-03~04**는 `RuleDefinition.Universe` 레코드 확장과 `BacktestService.resolveSymbols()` 메서드 교체로 구현한다. `volume_top_n` 타입 감지 시, 날짜별로 `MarketBarRepository`에서 KOSPI 종목의 거래량 상위 N개를 쿼리한 뒤 `additionalSymbols`와 합집합을 구성한다.

**Primary recommendation:** Plan 순서를 00-05 → 00-01 → 00-02 → 00-03 → 00-04로 실행하되, 각 Plan은 독립적이므로 병렬화 가능하다.

---

## Standard Stack

### Core (이미 프로젝트에 존재)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | 기존 | MarketBarRepository 쿼리 확장 | JPQL 네이티브 쿼리 기반, 프로젝션 지원 |
| Flyway | 기존 | V30 마이그레이션 | 모든 스키마 변경은 버전 파일로 관리 중 |
| YahooFinanceChartClient | 기존 | 일봉 OHLCV 수집 (2y range) | `fetchDailyOhlcv()` 이미 완전 구현됨 |
| Jackson | 기존 | RuleDefinition JSON 역직렬화 | `@JsonIgnoreProperties(ignoreUnknown=true)` 이미 적용 |

### 신규 추가 없음

이번 phase는 외부 라이브러리를 추가하지 않는다. 모든 기능은 기존 스택으로 구현 가능하다.

---

## Architecture Patterns

### 현재 코드 경로 (버그 있는 상태)

```
BacktestService.run()
  → closes[] 추출 ✓
  → volumes[] = null  ← BUG (line 117, 128)
  → RuleEvaluator.entryTriggered(def.entry(), closes, null, i)
  → operand() case "VOLUME": volumes != null ? volumes[i] : NaN  → 항상 NaN
  → 조건 false → VOLUME 룰 전혀 동작 안 함
```

### 수정 후 코드 경로

```
BacktestService.run()
  → closes[] 추출 (기존 유지)
  → volumes[] 추출 (신규: bars에서 volume 필드 읽기)
  → RuleEvaluator.entryTriggered(def.entry(), closes, volumes, i)
  → operand() case "VOLUME": volumes[i] (실제 값)  → 조건 정상 동작
```

### Pattern 1: volumes 배열 추출 (DATA-05)

`closesBySymbol`과 병행하여 동일 루프에서 `volumesBySymbol`을 구성한다.

```java
// BacktestService.run() 내부 — symbol 루프에서 추가
Map<String, Double[]> volumesBySymbol = new LinkedHashMap<>();

// 기존 closes 추출 루프 내부에 추가:
Double[] volumes = new Double[bars.size()];
for (int i = 0; i < bars.size(); i++) {
    closes[i] = bars.get(i).close();
    dates[i] = bars.get(i).date();
    volumes[i] = bars.get(i).volume();  // Bar.volume()은 이미 Double 타입
    idx.put(dates[i], i);
    allDates.add(dates[i]);
}
volumesBySymbol.put(symbol, volumes);

// 평가 루프에서:
Double[] volumes = volumesBySymbol.get(symbol);
evaluator.exitTriggered(def.exit(), closes, volumes, i, entryPrice)
evaluator.entryTriggered(def.entry(), closes, volumes, i)
```

**중요:** `Bar.volume()`은 이미 `Double` 타입(nullable)이다. `RuleEvaluator.operand()`는 `Double[] volumes`를 받으므로 타입 일치. `DbMarketDataAdapter`는 이미 `b.getVolume() == null ? null : b.getVolume().doubleValue()`로 변환 중이므로 어댑터 변경 불필요.

### Pattern 2: Flyway 마이그레이션 (DATA-01)

다음 파일 번호는 **V30**이다 (현재 V29까지 존재).

```sql
-- V30__kospi200_universe.sql
ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS in_kospi200 BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_companies_in_kospi200
    ON companies (in_kospi200) WHERE in_kospi200 = TRUE;

-- KOSPI 200 종목 초기 데이터 (ticker 기준 UPDATE)
-- 옵션 A: 이미 companies 테이블에 있는 KOSPI 종목에 플래그 설정
UPDATE companies SET in_kospi200 = TRUE
WHERE market IN ('KOSPI', 'KRX', 'KSC')
  AND ticker IN (
    '005930', '000660', '207940', -- 삼성전자, SK하이닉스, 삼성바이오로직스 ...
    -- KOSPI 200 전체 종목 코드 목록
  );
```

**KOSPI 200 초기 데이터 전략 (중요 결정 필요):**
- **옵션 A — SQL UPDATE (권장):** KOSPI 200 종목 코드를 Flyway 파일 내 `IN (...)` 리스트로 삽입. 소스는 KRX 공식 홈페이지 또는 한국거래소 API (무료, 공개). 일회성 작업이므로 수동 관리 가능.
- **옵션 B — 외부 API 자동화:** `ApplicationRunner`로 KRX 구성종목 API 호출. 구성 변경 시 자동 반영되나 외부 의존성 추가됨.
- **STATE.md의 blocker:** "CSV import vs API vs 수동" 미결. 현 phase에서는 옵션 A가 가장 단순하고 리스크가 없다.

### Pattern 3: Company 엔티티 확장 (DATA-01)

```java
// Company.java 에 필드 및 getter/setter 추가
@Column(name = "in_kospi200", nullable = false)
private boolean inKospi200 = false;

public boolean isInKospi200() { return inKospi200; }
public void setInKospi200(boolean inKospi200) { this.inKospi200 = inKospi200; }
```

```java
// CompanyRepository.java 에 메서드 추가
List<Company> findByInKospi200True();
```

### Pattern 4: ingestDailyForKospi200() 추가 (DATA-02)

```java
// MarketDataIngestionService.java
/** KOSPI 200 전체 종목 일봉 적재. 적재된 종목 수 반환. */
public int ingestDailyForKospi200() {
    List<Company> kospi200 = companyRepository.findByInKospi200True();
    int count = 0;
    for (Company company : kospi200) {
        if (company.getTicker() == null) continue;
        if (ingestDaily(company.getTicker()) > 0) {
            count++;
        }
    }
    log.info("KOSPI 200 daily ingestion done: {} symbols", count);
    return count;
}
```

**스케줄링 전략:** Phase 0에서는 `@Scheduled` 자동 실행보다 `InternalMarketController`의 HTTP 엔드포인트로 수동 트리거하는 것이 안전하다. Phase 2에서 스케줄러를 추가할 때 함께 자동화한다.

### Pattern 5: RuleDefinition.Universe 확장 (DATA-03)

```java
// RuleDefinition.java — Universe record 교체
@JsonIgnoreProperties(ignoreUnknown = true)
public record Universe(
    String type,                    // "symbols" (기존) | "volume_top_n" (신규)
    List<String> symbols,           // type="symbols"일 때 사용
    String market,                  // type="volume_top_n"일 때: "KOSPI"
    Integer topN,                   // type="volume_top_n"일 때: 10
    List<String> additionalSymbols  // volume_top_n + 수동 추가 종목
) {}
```

`@JsonIgnoreProperties(ignoreUnknown = true)`가 이미 적용되어 있으므로 기존 룰 definition JSON은 역직렬화 시 새 필드를 무시하고 정상 동작한다. **하위 호환 완전 보장.**

### Pattern 6: 동적 유니버스 선정 쿼리 (DATA-04)

```java
// MarketBarRepository.java 에 추가
@Query("""
    SELECT mb.symbol
    FROM MarketBar mb
    JOIN Company c ON c.ticker = mb.symbol
    WHERE mb.tradingDate = :date
      AND c.inKospi200 = TRUE
      AND mb.volume IS NOT NULL
    ORDER BY mb.volume DESC
    LIMIT :topN
    """)
List<String> findTopVolumeSymbols(
    @Param("date") LocalDate date,
    @Param("topN") int topN
);
```

**대안:** 네이티브 SQL 쿼리로 작성할 수도 있으나, JPQL로 작성하면 데이터베이스 독립성이 유지된다. `LIMIT`은 JPQL에서 `Pageable`로 처리하는 것이 더 표준적이다:

```java
// 더 표준적인 방식
@Query("""
    SELECT mb.symbol
    FROM MarketBar mb
    JOIN Company c ON c.ticker = mb.symbol
    WHERE mb.tradingDate = :date
      AND c.inKospi200 = TRUE
      AND mb.volume IS NOT NULL
    ORDER BY mb.volume DESC
    """)
List<String> findTopVolumeSymbolsOnDate(
    @Param("date") LocalDate date,
    Pageable pageable  // PageRequest.of(0, topN) 전달
);
```

### Pattern 7: BacktestService 동적 유니버스 진입점 (DATA-04)

```java
// BacktestService — resolveSymbols() 메서드 제거 후 run() 내부를 분기

private List<String> resolveSymbolsForDate(RuleDefinition def, LocalDate date, Map<String, double[]> closesBySymbol) {
    Universe u = def.universe();
    if (u == null) throw new GraphifyException("ERR_BACKTEST_002", "유니버스가 없습니다.", HttpStatus.BAD_REQUEST);

    if ("volume_top_n".equals(u.type())) {
        int topN = u.topN() != null ? u.topN() : 10;
        List<String> dynamic = barRepository.findTopVolumeSymbolsOnDate(
            date, PageRequest.of(0, topN));
        Set<String> result = new LinkedHashSet<>(dynamic);
        if (u.additionalSymbols() != null) result.addAll(u.additionalSymbols());
        // 동적 종목 중 데이터가 있는 것만 반환 (closesBySymbol에 없으면 해당 날짜에 데이터 없음)
        return result.stream().filter(closesBySymbol::containsKey).toList();
    }

    // 기존 symbols 타입
    if (u.symbols() == null || u.symbols().isEmpty())
        throw new GraphifyException("ERR_BACKTEST_002", "유니버스 종목이 비어 있습니다.", HttpStatus.BAD_REQUEST);
    return u.symbols();
}
```

**백테스트 루프 구조 변경 (DATA-04의 핵심):**

현재 구조: 모든 종목을 미리 로드 → 모든 날짜에 동일 종목 적용  
변경 후 구조: 모든 KOSPI 200 종목을 미리 로드 → 날짜별로 그 날의 거래량 상위 N 종목만 평가

```java
// run() 내부: symbols 결정 시점 변경
// volume_top_n 타입이면 KOSPI 200 전체 종목 데이터를 미리 로드
List<String> initialSymbols = resolveInitialSymbols(def);  // 전처리용

// date loop 내부:
for (LocalDate date : allDates) {
    List<String> activeSymbols = resolveSymbolsForDate(def, date, closesBySymbol);
    for (String symbol : activeSymbols) {
        // ... 기존 평가 로직
    }
}
```

### Anti-Patterns to Avoid

- **Look-ahead bias:** `findTopVolumeSymbolsOnDate(date, ...)` 쿼리는 반드시 해당 `date`의 `market_bars` 데이터만 사용해야 한다. 미래 날짜 데이터를 섞으면 안 된다.
- **volumes null 혼용:** `volumesBySymbol`에 심볼이 없는 경우 null-safe 접근 필수. `volumesBySymbol.getOrDefault(symbol, null)`
- **초기 데이터 없이 동적 쿼리 실행:** `in_kospi200=TRUE` 종목이 `market_bars`에 없으면 쿼리가 빈 리스트를 반환해 백테스트가 무음으로 실패한다. DATA-02 완료 후에야 DATA-04 테스트가 의미 있다.
- **RuleDefinitionValidator 업데이트 누락:** `volume_top_n` 타입 추가 시 Validator에서 새 타입을 허용해야 한다. 현재 validator가 `symbols` 배열 비어있음을 체크한다면 `volume_top_n` 타입일 때는 해당 체크를 건너뛰어야 한다.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 거래량 상위 N 쿼리 | Java에서 전체 로드 후 정렬 | Spring Data JPA Pageable | DB 레벨 ORDER BY + LIMIT이 압도적으로 효율적 |
| Yahoo → KS suffix 변환 | 직접 문자열 조작 | `YahooSymbolResolver.resolve()` | 이미 KOSPI/KOSDAQ/KONEX 모두 처리 |
| OHLCV 파싱 | 새 파서 작성 | `YahooFinanceChartClient.fetchDailyOhlcv()` | 이미 2y range, volume 포함, dedup 처리 완비 |
| Upsert 로직 | 직접 delete+insert | `MarketBar.update()` + `ifPresentOrElse` | 기존 `ingestDaily()` 패턴 그대로 재사용 |
| KOSPI 200 정의 | 자체 분류 로직 | `in_kospi200` 플래그 (DB 진실 소스) | 코드에서 분류하면 유지보수 불가 |

---

## Common Pitfalls

### Pitfall 1: `volumes` 타입 불일치

**What goes wrong:** `RuleEvaluator.entryTriggered()`는 `Double[] volumes`를 받는다. `Bar.volume()`은 `Double`(nullable boxed). `closes[]`는 `double[]`(primitive)이다.
**Why it happens:** 개발자가 `double[]`로 volumes 배열을 선언하면 null volume을 표현할 수 없다.
**How to avoid:** `volumesBySymbol`은 반드시 `Map<String, Double[]>`으로 선언한다. `bar.volume()`이 null이면 `Double[]`의 해당 인덱스도 null이 되어 `RuleEvaluator`에서 NaN 처리된다.

### Pitfall 2: `in_kospi200` 플래그 vs 실제 데이터 불일치

**What goes wrong:** V30 마이그레이션에서 `in_kospi200=TRUE`로 표시했지만, 해당 ticker가 `companies`에 존재하지 않거나 `market_bars`에 데이터가 없으면 ingestion이 0개를 반환한다.
**Why it happens:** `companies` 테이블의 ticker가 실제 KOSPI 200 목록과 다를 수 있다.
**How to avoid:** V30 seed SQL 작성 시 `WHERE ticker IN (...) AND market IN ('KOSPI', ...)` 조건을 AND로 사용한다. `ingestDailyForKospi200()` 실행 후 `SELECT count(*) FROM market_bars WHERE symbol IN (SELECT ticker FROM companies WHERE in_kospi200=TRUE)`로 검증한다.

### Pitfall 3: 동적 유니버스가 날짜별로 비어 있는 경우

**What goes wrong:** 특정 초기 날짜에 KOSPI 200 종목의 market_bars 데이터가 없으면 해당 날짜의 동적 유니버스가 빈 리스트가 된다. 백테스트 루프는 조용히 건너뛰고 수익 곡선에 null을 남길 수 있다.
**Why it happens:** 2년치 적재가 완료되기 전 백테스트를 실행하거나, 특정 날짜의 데이터가 누락된 경우.
**How to avoid:** `findTopVolumeSymbolsOnDate` 결과가 비어 있으면 해당 날짜의 equity point는 이전 날과 동일 값으로 유지한다(거래 없음). 빈 날짜 수를 INFO 로그로 기록한다.

### Pitfall 4: BacktestService에 `MarketBarRepository` 직접 주입

**What goes wrong:** `BacktestService`는 현재 `MarketDataPort`(인터페이스)만 사용한다. `MarketBarRepository`를 직접 주입하면 포트 추상화가 깨진다.
**Why it happens:** 동적 유니버스 쿼리를 위해 Repository에 접근할 방법이 필요하다.
**How to avoid:** `MarketDataPort`에 `topVolumeSymbols(LocalDate date, int topN)` 메서드를 추가하거나, 별도 `UniverseResolver` 서비스를 만들어 `BacktestService`에 주입한다. `DbMarketDataAdapter`에서 구현하면 포트 패턴 유지.

---

## Code Examples

### 현재 버그 위치 (DATA-05) - BacktestService.java line 117, 128

```java
// 현재 (버그): evaluator에 null volumes 전달
if (evaluator.exitTriggered(def.exit(), closes, null, i, entryPrice)) {  // line 117
    ...
}
if (evaluator.entryTriggered(def.entry(), closes, null, i)) {  // line 128
    ...
}
```

```java
// 수정 후: volumes 배열을 함께 전달
// 1. symbol 루프에서 volumes 배열 추출
Double[] volumes = new Double[bars.size()];
for (int i = 0; i < bars.size(); i++) {
    closes[i] = bars.get(i).close();
    dates[i] = bars.get(i).date();
    volumes[i] = bars.get(i).volume();  // already Double from Bar record
    idx.put(dates[i], i);
    allDates.add(dates[i]);
}
volumesBySymbol.put(symbol, volumes);

// 2. 평가 루프에서 volumes 참조
Double[] vols = volumesBySymbol.get(symbol);
if (evaluator.exitTriggered(def.exit(), closes, vols, i, entryPrice)) {
    ...
}
if (evaluator.entryTriggered(def.entry(), closes, vols, i)) {
    ...
}
```

### RuleDefinitionValidator 업데이트 필요 패턴

현재 validator가 `universe.symbols()` 비어있음을 체크한다면:

```java
// RuleDefinitionValidator 내부 (확인 후 적용)
if (def.universe() != null) {
    String type = def.universe().type();
    if (!"volume_top_n".equals(type)) {
        // symbols 타입만 체크
        if (def.universe().symbols() == null || def.universe().symbols().isEmpty()) {
            errors.add("universe.symbols는 비어 있을 수 없습니다");
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| 룰 명시 종목만 수집 | KOSPI 200 전체 수집 | 유니버스 동적 선정 가능 |
| Universe = symbols 리스트만 | Universe = type(symbols/volume_top_n) | 날짜별 동적 유니버스 지원 |
| Volume 항상 null 전달 | Volume DB에서 읽어 전달 | VOLUME 지표 룰 정상 동작 |

---

## Open Questions

1. **KOSPI 200 초기 데이터 소스**
   - What we know: companies 테이블에 KOSPI 종목들이 일부 존재하나 전체 200개 여부 불확실
   - What's unclear: 현재 `companies` 테이블에 KOSPI 200 종목이 몇 개나 있는지, ticker 형식이 일치하는지
   - Recommendation: Plan 00-02 시작 전 `SELECT count(*) FROM companies WHERE market IN ('KOSPI', 'KRX') AND listed = TRUE` 실행하여 현황 파악. 부족하면 KRX 공식 구성종목 CSV를 Flyway seed SQL로 삽입.

2. **RuleDefinitionValidator 현재 구현 확인 필요**
   - What we know: `BacktestService`에서 `validator.validate(def)` 호출 중
   - What's unclear: Validator가 `universe.symbols()` 비어있음을 체크하는지, `volume_top_n` 타입 추가 시 validator 수정이 필요한지
   - Recommendation: Plan 00-04 에서 Validator 파일 반드시 확인 후 수정.

3. **MarketDataPort vs Repository 직접 접근**
   - What we know: BacktestService는 현재 MarketDataPort만 사용. 동적 유니버스 쿼리는 Repository 레벨 기능.
   - What's unclear: `MarketDataPort`에 `topVolumeSymbols()` 추가 vs 별도 서비스 분리 중 무엇이 더 나은가
   - Recommendation: `MarketDataPort`에 `List<String> topVolumeSymbols(LocalDate date, int topN)` 추가. `DbMarketDataAdapter`에서 구현. 포트 패턴 유지 + 테스트 용이.

---

## Validation Architecture

> `nyquist_validation: true` — 이 섹션은 필수.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (기존 사용 중) |
| Config file | `backend/src/test/java/com/graphify/` (기존 테스트 구조) |
| Quick run command | `./gradlew test --tests "com.graphify.trading.*" -x integrationTest` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-05 | BacktestService가 volumes 배열을 RuleEvaluator에 전달한다 | unit | `./gradlew test --tests "com.graphify.trading.backtest.BacktestServiceVolumeTest"` | ❌ Wave 0 |
| DATA-05 | VOLUME 지표 룰이 있는 백테스트에서 volume != null인 bars에서 조건이 평가된다 | unit | `./gradlew test --tests "com.graphify.trading.engine.RuleEvaluatorVolumeTest"` | ❌ Wave 0 |
| DATA-03 | `volume_top_n` 타입의 Universe JSON이 RuleDefinition으로 역직렬화된다 | unit | `./gradlew test --tests "com.graphify.trading.rule.definition.RuleDefinitionUniverseTest"` | ❌ Wave 0 |
| DATA-04 | 날짜별 거래량 상위 N 쿼리가 올바른 종목 순서를 반환한다 | unit/repo | `./gradlew test --tests "com.graphify.market.MarketBarRepositoryTopVolumeTest"` | ❌ Wave 0 |
| DATA-01 | Company 엔티티에 inKospi200 필드가 존재하고 JPA 매핑이 정상이다 | unit | `./gradlew test --tests "com.graphify.company.CompanyEntityTest"` | ❌ Wave 0 |
| DATA-02 | ingestDailyForKospi200()가 in_kospi200=TRUE 종목만 처리한다 | unit | `./gradlew test --tests "com.graphify.market.MarketDataIngestionServiceKospi200Test"` | ❌ Wave 0 |

### 핵심 검증 시나리오

**DATA-05 검증 (가장 중요):**
```
Given: market_bars에 volume=50000인 봉이 있는 종목
When: VOLUME > 30000 진입 룰로 백테스트 실행
Then: 해당 봉에서 진입 신호가 발생해야 함 (현재 버그 상태에서는 발생 안 함)
```

**DATA-04 검증:**
```
Given: 5개 종목의 market_bars 존재, 날짜 2024-01-15, volume 각각 [100, 300, 200, 400, 150]
When: findTopVolumeSymbolsOnDate(2024-01-15, PageRequest.of(0, 3)) 호출
Then: [종목B(400), 종목D(300), 종목C(200)] 순서로 반환
```

**DATA-03 검증:**
```
Given: {"type":"volume_top_n","market":"KOSPI","topN":10,"additionalSymbols":["005930"]} JSON
When: objectMapper.readValue(json, RuleDefinition.class)
Then: universe.type()="volume_top_n", universe.topN()=10, universe.additionalSymbols()=["005930"]
```

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.graphify.trading.backtest.*" --tests "com.graphify.market.*" --tests "com.graphify.trading.rule.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java` — DATA-05
- [ ] `backend/src/test/java/com/graphify/trading/engine/RuleEvaluatorVolumeTest.java` — DATA-05
- [ ] `backend/src/test/java/com/graphify/trading/rule/definition/RuleDefinitionUniverseTest.java` — DATA-03
- [ ] `backend/src/test/java/com/graphify/market/MarketBarRepositoryTopVolumeTest.java` — DATA-04
- [ ] `backend/src/test/java/com/graphify/market/MarketDataIngestionServiceKospi200Test.java` — DATA-02
- [ ] `backend/src/test/java/com/graphify/company/CompanyEntityTest.java` — DATA-01

---

## Sources

### Primary (HIGH confidence)

- 직접 코드 분석: `BacktestService.java` — volumes null 버그 line 117, 128 확인
- 직접 코드 분석: `RuleEvaluator.java` — `Double[] volumes` 파라미터 타입 확인, NaN fallback 확인
- 직접 코드 분석: `Bar.java` — `Double volume` nullable 필드 확인
- 직접 코드 분석: `DbMarketDataAdapter.java` — volume doubleValue() 변환 이미 정상 확인
- 직접 코드 분석: `MarketBar.java` — `Long volume` DB 컬럼 확인
- 직접 코드 분석: `MarketDataIngestionService.java` — `ingestDaily()` 패턴, `activeSymbols()` 구조 확인
- 직접 코드 분석: `RuleDefinition.java` — `Universe` record, `@JsonIgnoreProperties` 확인
- 직접 코드 분석: `YahooFinanceChartClient.java` — `fetchDailyOhlcv()` 2y range, volume 포함 확인
- 직접 코드 분석: `YahooSymbolResolver.java` — KOSPI → `.KS` suffix 변환 확인
- 직접 코드 분석: `Company.java` — `in_kospi200` 필드 없음 확인 (추가 필요)
- 직접 코드 분석: `CompanyRepository.java` — `findByInKospi200True()` 없음 확인 (추가 필요)
- 직접 코드 분석: `V29__market_bars.sql` — `volume BIGINT` 컬럼 존재 확인
- 파일시스템 분석: 마이그레이션 파일 목록 — 다음 번호 V30 확인

### Secondary (MEDIUM confidence)

- Spring Data JPA 공식 문서 (훈련 데이터): `Pageable`로 LIMIT 구현하는 표준 패턴
- Flyway 공식 관례 (훈련 데이터): 순번 파일 명명 규칙 `VX__description.sql`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 모든 라이브러리가 기존 코드베이스에 이미 존재하고 직접 확인됨
- Architecture: HIGH — 버그 위치와 수정 방법이 코드 분석으로 정확히 특정됨
- Pitfalls: HIGH — 실제 코드에서 타입 정보와 포트 패턴을 확인하여 도출

**Research date:** 2026-06-20
**Valid until:** 2026-09-20 (안정적인 Spring Boot 스택, 빠른 변동 없음)
