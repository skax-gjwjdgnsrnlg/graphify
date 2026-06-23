package com.graphify.market;

import com.graphify.common.dto.ApiResponse;
import com.graphify.common.exception.GraphifyException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cloud Scheduler 전용 시세 적재 트리거. 공유 시크릿 헤더(X-Internal-Token)로 보호.
 * graphify.internal.token 미설정 시 호출을 거부(운영 안전).
 */
@RestController
@RequestMapping("/internal/market")
public class InternalMarketController {

    private final MarketDataIngestionService ingestionService;
    private final String internalToken;

    public InternalMarketController(
            MarketDataIngestionService ingestionService,
            @Value("${graphify.internal.token:}") String internalToken
    ) {
        this.ingestionService = ingestionService;
        this.internalToken = internalToken;
    }

    @PostMapping("/ingest")
    public ApiResponse<Map<String, Object>> ingest(
            @RequestParam(defaultValue = "EOD") String interval,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        authorize(token);
        int symbols;
        if ("MINUTE".equalsIgnoreCase(interval)) {
            symbols = ingestionService.ingestIntradayForActiveSymbols("5m", "1d");
        } else {
            symbols = ingestionService.ingestDailyForActiveSymbols();
        }
        return ApiResponse.ok(Map.of("interval", interval.toUpperCase(), "symbols", symbols));
    }

    private void authorize(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new GraphifyException(
                    "ERR_INTERNAL_001",
                    "내부 토큰이 설정되지 않았습니다.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!internalToken.equals(token)) {
            throw new GraphifyException(
                    "ERR_INTERNAL_002",
                    "내부 호출 인증 실패.",
                    HttpStatus.UNAUTHORIZED);
        }
    }
}
