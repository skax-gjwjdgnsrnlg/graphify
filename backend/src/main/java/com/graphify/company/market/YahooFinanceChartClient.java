package com.graphify.company.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphify.config.GraphifyMarketProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class YahooFinanceChartClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceChartClient.class);
    private static final ZoneId DEFAULT_EXCHANGE_ZONE = ZoneId.of("Asia/Seoul");

    private final GraphifyMarketProperties properties;
    private final RestClient yahooRestClient;

    public YahooFinanceChartClient(GraphifyMarketProperties properties, RestClient yahooRestClient) {
        this.properties = properties;
        this.yahooRestClient = yahooRestClient;
    }

    public Optional<YahooChartData> fetchDailyChart(String yahooSymbol) {
        if (!properties.isYahooEnabled()) {
            return Optional.empty();
        }
        try {
            JsonNode root = yahooRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1d")
                            .queryParam("range", "2y")
                            .queryParam("includePrePost", "false")
                            .build(yahooSymbol))
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                return Optional.empty();
            }
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = result.get(0);
            JsonNode meta = first.path("meta");
            double price = meta.path("regularMarketPrice").asDouble(Double.NaN);
            long quoteEpoch = meta.path("regularMarketTime").asLong(0);
            if (Double.isNaN(price) || quoteEpoch <= 0) {
                return Optional.empty();
            }
            String currency = textOrNull(meta, "currency");
            ZoneId exchangeZone = resolveExchangeZone(meta);
            Instant quoteTime = Instant.ofEpochSecond(quoteEpoch);

            List<DailyBar> bars = parseDailyBars(first, exchangeZone);
            bars = mergeLatestQuote(bars, price, quoteTime, exchangeZone);
            if (bars.size() < 20) {
                log.warn("Yahoo chart insufficient bars symbol={} count={}", yahooSymbol, bars.size());
                return Optional.empty();
            }

            double previousClose = bars.size() >= 2
                    ? bars.get(bars.size() - 2).close()
                    : Double.NaN;

            return Optional.of(new YahooChartData(
                    yahooSymbol,
                    currency,
                    price,
                    quoteTime,
                    exchangeZone,
                    previousClose,
                    bars
            ));
        } catch (RestClientException ex) {
            log.warn("Yahoo chart fetch failed symbol={}: {}", yahooSymbol, ex.getMessage());
            return Optional.empty();
        }
    }

  /**
   * 장중 최신가 보강: 일봉 히스토리(2y)의 meta 시세가 지연될 때 5분봉 당일 구간으로 재확인.
   */
    private Optional<YahooChartData> enrichWithIntradayQuote(YahooChartData base) {
        try {
            JsonNode root = yahooRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "5m")
                            .queryParam("range", "1d")
                            .queryParam("includePrePost", "false")
                            .build(base.yahooSymbol()))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return Optional.of(base);
            }
            JsonNode first = root.path("chart").path("result").get(0);
            JsonNode meta = first.path("meta");
            double livePrice = meta.path("regularMarketPrice").asDouble(Double.NaN);
            long liveEpoch = meta.path("regularMarketTime").asLong(0);
            if (Double.isNaN(livePrice) || liveEpoch <= 0) {
                return Optional.of(base);
            }
            Instant liveTime = Instant.ofEpochSecond(liveEpoch);
            if (!liveTime.isAfter(base.quoteTime())) {
                return Optional.of(base);
            }
            List<DailyBar> bars = mergeLatestQuote(
                    base.dailyBars(),
                    livePrice,
                    liveTime,
                    base.exchangeZone()
            );
            double previousClose = bars.size() >= 2
                    ? bars.get(bars.size() - 2).close()
                    : base.previousClose();
            return Optional.of(new YahooChartData(
                    base.yahooSymbol(),
                    base.currency(),
                    livePrice,
                    liveTime,
                    base.exchangeZone(),
                    previousClose,
                    bars
            ));
        } catch (RestClientException ex) {
            log.debug("Yahoo intraday enrich skipped symbol={}: {}", base.yahooSymbol(), ex.getMessage());
            return Optional.of(base);
        }
    }

    public Optional<YahooChartData> fetchDailyChartWithLiveQuote(String yahooSymbol) {
        return fetchDailyChart(yahooSymbol).flatMap(this::enrichWithIntradayQuote);
    }

    /** 일봉 OHLCV 전체 적재용. interval=1d, range=2y. */
    public List<OhlcvBar> fetchDailyOhlcv(String yahooSymbol) {
        if (!properties.isYahooEnabled()) {
            return List.of();
        }
        try {
            JsonNode root = yahooRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1d")
                            .queryParam("range", "2y")
                            .queryParam("includePrePost", "false")
                            .build(yahooSymbol))
                    .retrieve()
                    .body(JsonNode.class);
            return parseOhlcv(root, true).stream()
                    .map(r -> new OhlcvBar(
                            Instant.ofEpochSecond(r.epoch).atZone(r.zone).toLocalDate(),
                            r.open, r.high, r.low, r.close, r.volume))
                    .toList();
        } catch (RestClientException ex) {
            log.warn("Yahoo daily OHLCV fetch failed symbol={}: {}", yahooSymbol, ex.getMessage());
            return List.of();
        }
    }

    /** 분봉 OHLCV 적재용. 예: interval=5m, range=1d. */
    public List<IntradayBar> fetchIntraday(String yahooSymbol, String interval, String range) {
        if (!properties.isYahooEnabled()) {
            return List.of();
        }
        try {
            JsonNode root = yahooRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", interval)
                            .queryParam("range", range)
                            .queryParam("includePrePost", "false")
                            .build(yahooSymbol))
                    .retrieve()
                    .body(JsonNode.class);
            return parseOhlcv(root, false).stream()
                    .map(r -> new IntradayBar(
                            Instant.ofEpochSecond(r.epoch), r.open, r.high, r.low, r.close, r.volume))
                    .toList();
        } catch (RestClientException ex) {
            log.warn("Yahoo intraday fetch failed symbol={} interval={}: {}", yahooSymbol, interval, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 날짜 범위 지정 5분봉 수집. period1/period2 epoch 방식으로 Yahoo에 요청.
     * from ~ to는 최대 60일 이내여야 한다.
     */
    public List<IntradayBar> fetchIntradayForDateRange(
            String yahooSymbol, LocalDate from, LocalDate to) {
        if (!properties.isYahooEnabled()) {
            return List.of();
        }
        ZoneId kst = ZoneId.of("Asia/Seoul");
        long period1 = from.atStartOfDay(kst).toEpochSecond();
        long period2 = to.atTime(23, 59, 59).atZone(kst).toEpochSecond();
        try {
            JsonNode root = yahooRestClient.get()
                    .uri(u -> u.path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .queryParam("interval", "5m")
                            .queryParam("includePrePost", "false")
                            .build(yahooSymbol))
                    .retrieve()
                    .body(JsonNode.class);
            return parseOhlcv(root, false).stream()
                    .map(r -> new IntradayBar(
                            Instant.ofEpochSecond(r.epoch), r.open, r.high, r.low, r.close, r.volume))
                    .toList();
        } catch (RestClientException ex) {
            log.warn("Yahoo intraday range fetch failed symbol={}: {}", yahooSymbol, ex.getMessage());
            return List.of();
        }
    }

    private record RawBar(long epoch, ZoneId zone, Double open, Double high, Double low, double close, Long volume) {
    }

    private static List<RawBar> parseOhlcv(JsonNode root, boolean dailyDedup) {
        if (root == null) {
            return List.of();
        }
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }
        JsonNode first = result.get(0);
        ZoneId zone = resolveExchangeZone(first.path("meta"));
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return List.of();
        }
        JsonNode q = quote.get(0);
        JsonNode closeArr = q.path("close");
        JsonNode openArr = q.path("open");
        JsonNode highArr = q.path("high");
        JsonNode lowArr = q.path("low");
        JsonNode volArr = q.path("volume");
        if (!closeArr.isArray()) {
            return List.of();
        }
        int len = Math.min(timestamps.size(), closeArr.size());
        List<RawBar> out = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            JsonNode cNode = closeArr.get(i);
            if (cNode == null || cNode.isNull()) {
                continue;
            }
            double close = cNode.asDouble(Double.NaN);
            if (Double.isNaN(close) || close <= 0) {
                continue;
            }
            long epoch = timestamps.get(i).asLong(0);
            if (epoch <= 0) {
                continue;
            }
            RawBar bar = new RawBar(
                    epoch, zone,
                    numOrNull(openArr, i), numOrNull(highArr, i), numOrNull(lowArr, i),
                    close, longOrNull(volArr, i));
            if (dailyDedup && !out.isEmpty()
                    && Instant.ofEpochSecond(out.get(out.size() - 1).epoch).atZone(zone).toLocalDate()
                       .equals(Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate())) {
                out.set(out.size() - 1, bar);
            } else {
                out.add(bar);
            }
        }
        return out;
    }

    private static Double numOrNull(JsonNode arr, int i) {
        if (!arr.isArray() || i >= arr.size()) {
            return null;
        }
        JsonNode n = arr.get(i);
        if (n == null || n.isNull()) {
            return null;
        }
        double v = n.asDouble(Double.NaN);
        return Double.isNaN(v) ? null : v;
    }

    private static Long longOrNull(JsonNode arr, int i) {
        if (!arr.isArray() || i >= arr.size()) {
            return null;
        }
        JsonNode n = arr.get(i);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asLong();
    }

    private static List<DailyBar> mergeLatestQuote(
            List<DailyBar> bars,
            double price,
            Instant quoteTime,
            ZoneId exchangeZone
    ) {
        if (bars.isEmpty()) {
            return List.of(new DailyBar(quoteTime.atZone(exchangeZone).toLocalDate(), price));
        }
        LocalDate quoteDate = quoteTime.atZone(exchangeZone).toLocalDate();
        List<DailyBar> merged = new ArrayList<>(bars);
        DailyBar last = merged.get(merged.size() - 1);
        if (last.tradingDate().equals(quoteDate)) {
            merged.set(merged.size() - 1, new DailyBar(quoteDate, price));
        } else if (quoteDate.isAfter(last.tradingDate())) {
            merged.add(new DailyBar(quoteDate, price));
        }
        return merged;
    }

    private static List<DailyBar> parseDailyBars(JsonNode result, ZoneId exchangeZone) {
        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return List.of();
        }
        JsonNode closeArray = quote.get(0).path("close");
        if (!closeArray.isArray()) {
            return List.of();
        }
        int len = Math.min(timestamps.size(), closeArray.size());
        List<DailyBar> bars = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            JsonNode closeNode = closeArray.get(i);
            if (closeNode == null || closeNode.isNull()) {
                continue;
            }
            double close = closeNode.asDouble(Double.NaN);
            if (Double.isNaN(close) || close <= 0) {
                continue;
            }
            long epoch = timestamps.get(i).asLong(0);
            if (epoch <= 0) {
                continue;
            }
            LocalDate tradingDate = Instant.ofEpochSecond(epoch).atZone(exchangeZone).toLocalDate();
            if (!bars.isEmpty() && bars.get(bars.size() - 1).tradingDate().equals(tradingDate)) {
                bars.set(bars.size() - 1, new DailyBar(tradingDate, close));
            } else {
                bars.add(new DailyBar(tradingDate, close));
            }
        }
        return bars;
    }

    private static ZoneId resolveExchangeZone(JsonNode meta) {
        String tz = textOrNull(meta, "exchangeTimezoneName");
        if (tz == null || tz.isBlank()) {
            return DEFAULT_EXCHANGE_ZONE;
        }
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            return DEFAULT_EXCHANGE_ZONE;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText().trim();
        return raw.isEmpty() ? null : raw;
    }
}
