package com.graphify.market;

import com.graphify.company.Company;
import com.graphify.company.CompanyRepository;
import com.graphify.market.volume.NaverStockRankingClient;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 티커(종목코드) → 종목명 해석 서비스.
 *
 * <p>해석 순서: ① companies 테이블(CompanyRepository) → ② Naver 개별종목 API 폴백
 * → ③ 둘 다 실패 시 null(호출측이 코드로 폴백 표시).</p>
 *
 * <p>companies에 없는 종목(예: 거래대금 상위로 선정됐으나 마스터 미적재)도 종목명이
 * 뜨도록 한다. 해석 결과는 프로세스 인메모리 캐시에 보관(이름은 사실상 불변).</p>
 */
@Service
public class SymbolNameService {

    private final CompanyRepository companyRepo;
    private final NaverStockRankingClient naverClient;

    /** ticker → name 캐시. miss는 캐시하지 않음(다음에 재시도). */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SymbolNameService(CompanyRepository companyRepo, NaverStockRankingClient naverClient) {
        this.companyRepo = companyRepo;
        this.naverClient = naverClient;
    }

    /** 단일 티커 종목명. 못 찾으면 null. */
    public String resolve(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        String key = ticker.trim();
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String name = companyRepo.findByTicker(key).map(Company::getName)
                .orElseGet(() -> naverClient.fetchStockName(key).orElse(null));
        if (name != null && !name.isBlank()) {
            cache.put(key, name);
        }
        return name;
    }

    /** 여러 티커 배치 해석 (고유 티커별 1회). 값이 null인 항목은 코드 폴백용으로 그대로 둠. */
    public Map<String, String> resolveAll(Collection<String> tickers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String t : tickers) {
            if (t != null && !result.containsKey(t)) {
                result.put(t, resolve(t));
            }
        }
        return result;
    }
}
