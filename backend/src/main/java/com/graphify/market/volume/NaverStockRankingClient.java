package com.graphify.market.volume;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Naver 모바일 증권 JSON API 클라이언트 — 시장 전체 종목의 장중 누적 거래대금 조회.
 *
 * <p>엔드포인트: {@code GET /api/stocks/marketValue/{market}?page=N&pageSize=100}.
 * marketValue(시가총액) 정렬이지만 각 종목 응답에 당일 누적 거래대금
 * ({@code accumulatedTradingValueRaw})·종목구분({@code stockEndType})이 포함된다.
 * 거래대금 전용 정렬 경로는 비공개라, 전 종목을 받아 호출측에서 거래대금 재정렬한다.</p>
 *
 * <p>pageSize 상한은 100(초과 시 에러). totalCount까지 순회하되 {@link #MAX_PAGES}로 상한.
 * 무인증·UTF-8 JSON. 실패는 빈 리스트로 흡수(예외 비전파).</p>
 */
@Component
public class NaverStockRankingClient {

    private static final Logger log = LoggerFactory.getLogger(NaverStockRankingClient.class);

    static final int PAGE_SIZE = 100;
    /** 안전 상한 — KOSPI/KOSDAQ 전 종목(~2500)이면 25페이지. 폭주 방지용 하드캡. */
    static final int MAX_PAGES = 30;

    private final RestClient naverMobileRestClient;

    public NaverStockRankingClient(RestClient naverMobileRestClient) {
        this.naverMobileRestClient = naverMobileRestClient;
    }

    /** Naver 랭킹 한 행 — itemCode(티커), 종목명, 누적 거래대금(원), ETF 여부. */
    public record RankingRow(String itemCode, String stockName, double tradingValue, boolean etf) {
    }

    /**
     * 지정 시장의 전 종목을 페이징으로 조회해 행 목록 반환(정렬 전, 필터 전).
     *
     * @param market "KOSPI" | "KOSDAQ"
     * @return 종목 행 목록. 실패/빈 응답이면 빈 리스트.
     */
    public List<RankingRow> fetchAll(String market) {
        String mkt = (market != null && !market.isBlank()) ? market.trim().toUpperCase() : "KOSPI";
        List<RankingRow> rows = new ArrayList<>();
        int totalCount = Integer.MAX_VALUE;

        for (int page = 1; page <= MAX_PAGES && rows.size() < totalCount; page++) {
            JsonNode root = fetchPage(mkt, page);
            if (root == null) {
                break;
            }
            if (totalCount == Integer.MAX_VALUE) {
                JsonNode tc = root.path("totalCount");
                if (tc.isNumber()) {
                    totalCount = tc.asInt();
                }
            }
            JsonNode stocks = root.path("stocks");
            if (!stocks.isArray() || stocks.isEmpty()) {
                break;
            }
            for (JsonNode s : stocks) {
                String itemCode = text(s, "itemCode");
                if (itemCode == null) {
                    continue;
                }
                rows.add(new RankingRow(
                        itemCode,
                        text(s, "stockName"),
                        parseTradingValue(s),
                        "etf".equalsIgnoreCase(text(s, "stockEndType"))));
            }
        }
        return rows;
    }

    /**
     * 개별 티커의 종목명 조회 (companies 테이블에 없는 종목 폴백용).
     * {@code GET /api/stock/{code}/basic} → stockName. 실패 시 빈 Optional.
     */
    public Optional<String> fetchStockName(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = naverMobileRestClient.get()
                    .uri("/api/stock/{code}/basic", code.trim())
                    .retrieve()
                    .body(JsonNode.class);
            return root == null ? Optional.empty() : Optional.ofNullable(text(root, "stockName"));
        } catch (RestClientException ex) {
            log.warn("Naver stock-name fetch failed code={}: {}", code, ex.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode fetchPage(String market, int page) {
        try {
            return naverMobileRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/stocks/marketValue/{market}")
                            .queryParam("page", page)
                            .queryParam("pageSize", PAGE_SIZE)
                            .build(market))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("Naver ranking fetch failed market={} page={}: {}", market, page, ex.getMessage());
            return null;
        }
    }

    /** {@code accumulatedTradingValueRaw}(원 단위 정수 문자열) 우선, 없으면 0. */
    private static double parseTradingValue(JsonNode s) {
        String raw = text(s, "accumulatedTradingValueRaw");
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String raw = v.asText().trim();
        return raw.isEmpty() ? null : raw;
    }
}
