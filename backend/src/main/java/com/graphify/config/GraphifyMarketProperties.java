package com.graphify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "graphify.market")
public class GraphifyMarketProperties {

    /** composite | yahoo | krx */
    private String provider = "composite";

    private boolean yahooEnabled = true;
    private String yahooChartBaseUrl = "https://query1.finance.yahoo.com";

    private boolean naverEnabled = true;
    private String naverFinanceBaseUrl = "https://finance.naver.com";
    /** Naver 모바일 증권 JSON API (시장 전체 장중 거래대금 랭킹) — finance.naver.com과 호스트가 다름 */
    private String naverMobileApiBaseUrl = "https://m.stock.naver.com";

    private String krxApiKey = "";
    private String krxApiBaseUrl = "https://data-dbg.krx.co.kr/svc/apis/sto";

    private boolean cnnFearGreedEnabled = true;
    private String cnnFearGreedBaseUrl = "https://production.dataviz.cnn.io";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isYahooEnabled() {
        return yahooEnabled;
    }

    public void setYahooEnabled(boolean yahooEnabled) {
        this.yahooEnabled = yahooEnabled;
    }

    public String getYahooChartBaseUrl() {
        return yahooChartBaseUrl;
    }

    public void setYahooChartBaseUrl(String yahooChartBaseUrl) {
        this.yahooChartBaseUrl = yahooChartBaseUrl;
    }

    public boolean isNaverEnabled() {
        return naverEnabled;
    }

    public void setNaverEnabled(boolean naverEnabled) {
        this.naverEnabled = naverEnabled;
    }

    public String getNaverFinanceBaseUrl() {
        return naverFinanceBaseUrl;
    }

    public void setNaverFinanceBaseUrl(String naverFinanceBaseUrl) {
        this.naverFinanceBaseUrl = naverFinanceBaseUrl;
    }

    public String getNaverMobileApiBaseUrl() {
        return naverMobileApiBaseUrl;
    }

    public void setNaverMobileApiBaseUrl(String naverMobileApiBaseUrl) {
        this.naverMobileApiBaseUrl = naverMobileApiBaseUrl;
    }

    public String getKrxApiKey() {
        return krxApiKey;
    }

    public void setKrxApiKey(String krxApiKey) {
        this.krxApiKey = krxApiKey;
    }

    public boolean hasKrxApiKey() {
        return krxApiKey != null && !krxApiKey.isBlank();
    }

    public String getKrxApiBaseUrl() {
        return krxApiBaseUrl;
    }

    public void setKrxApiBaseUrl(String krxApiBaseUrl) {
        this.krxApiBaseUrl = krxApiBaseUrl;
    }

    public boolean isCnnFearGreedEnabled() {
        return cnnFearGreedEnabled;
    }

    public void setCnnFearGreedEnabled(boolean cnnFearGreedEnabled) {
        this.cnnFearGreedEnabled = cnnFearGreedEnabled;
    }

    public String getCnnFearGreedBaseUrl() {
        return cnnFearGreedBaseUrl;
    }

    public void setCnnFearGreedBaseUrl(String cnnFearGreedBaseUrl) {
        this.cnnFearGreedBaseUrl = cnnFearGreedBaseUrl;
    }
}
