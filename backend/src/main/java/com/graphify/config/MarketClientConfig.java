package com.graphify.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GraphifyMarketProperties.class)
public class MarketClientConfig {

    @Bean
    RestClient yahooRestClient(GraphifyMarketProperties marketProperties) {
        return RestClient.builder()
                .baseUrl(marketProperties.getYahooChartBaseUrl())
                .defaultHeader("User-Agent", "graphify/0.1 (yahoo-chart)")
                .build();
    }

    @Bean
    RestClient naverRestClient(GraphifyMarketProperties marketProperties) {
        return RestClient.builder()
                .baseUrl(marketProperties.getNaverFinanceBaseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; graphify/0.1)")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9")
                .build();
    }

    @Bean
    RestClient naverMobileRestClient(GraphifyMarketProperties marketProperties) {
        // 거래대금 랭킹은 페이지당 1콜 × 최대 30콜을 순차 호출하므로, 무응답 시
        // 스케줄러 스레드가 무한 대기하지 않도록 connect/read 타임아웃 필수.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(marketProperties.getNaverMobileApiBaseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; graphify/0.1)")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9")
                .build();
    }

    @Bean
    RestClient krxRestClient(GraphifyMarketProperties marketProperties) {
        return RestClient.builder()
                .baseUrl(marketProperties.getKrxApiBaseUrl())
                .defaultHeader("User-Agent", "graphify/0.1 (krx-openapi)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    RestClient cnnFearGreedRestClient(GraphifyMarketProperties marketProperties) {
        return RestClient.builder()
                .baseUrl(marketProperties.getCnnFearGreedBaseUrl())
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
                .defaultHeader("Referer", "https://edition.cnn.com/markets/fear-and-greed")
                .defaultHeader("Origin", "https://edition.cnn.com")
                .build();
    }
}
