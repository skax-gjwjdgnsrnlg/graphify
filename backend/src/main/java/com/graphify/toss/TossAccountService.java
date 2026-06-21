package com.graphify.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphify.common.exception.GraphifyException;
import com.graphify.toss.dto.TossAccountDto;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class TossAccountService {

    private static final Logger log = LoggerFactory.getLogger(TossAccountService.class);
    private static final String ACCOUNTS_URL = "https://openapi.tossinvest.com/api/v1/accounts";

    private final TossTokenManager tokenManager;
    private final TossCredentialRepository credentialRepo;
    private final RestClient restClient;

    public TossAccountService(
            TossTokenManager tokenManager,
            TossCredentialRepository credentialRepo,
            RestClient.Builder restClientBuilder) {
        this.tokenManager = tokenManager;
        this.credentialRepo = credentialRepo;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Returns real account list from Toss Securities.
     * Returns empty list if credentials not configured.
     * Retries once on 401 (token refresh then retry).
     */
    public List<TossAccountDto> getAccounts(Long userId) {
        if (credentialRepo.findByUserId(userId).isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String token = tokenManager.ensureValidToken(userId);
            return fetchAccounts(token);
        } catch (UnauthorizedException ex) {
            // Token rejected — force re-issue and retry once
            log.warn("Toss API 401 for userId={}, forcing token re-issue and retry", userId);
            try {
                TossCredential cred = credentialRepo.findByUserId(userId).orElseThrow();
                String freshToken = tokenManager.issueToken(cred);
                return fetchAccounts(freshToken);
            } catch (Exception retryEx) {
                log.error("Toss API retry failed for userId={}: {}", userId, retryEx.getMessage());
                throw new GraphifyException("ERR_TOSS_004",
                        "토스증권 API 연동에 실패했습니다. 자격증명을 확인하세요.", HttpStatus.BAD_GATEWAY);
            }
        } catch (GraphifyException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Toss accounts API error for userId={}: {}", userId, ex.getMessage());
            throw new GraphifyException("ERR_TOSS_004",
                    "토스증권 API 연동 실패", HttpStatus.BAD_GATEWAY);
        }
    }

    private List<TossAccountDto> fetchAccounts(String bearerToken) {
        TossAccountsResponse response = restClient.get()
                .uri(ACCOUNTS_URL)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    if (resp.getStatusCode().value() == 401) {
                        throw new UnauthorizedException();
                    }
                    throw new GraphifyException("ERR_TOSS_004",
                            "토스증권 API 오류: " + resp.getStatusCode(), HttpStatus.BAD_GATEWAY);
                })
                .body(TossAccountsResponse.class);

        if (response == null || response.accounts() == null) {
            return Collections.emptyList();
        }

        return response.accounts().stream()
                .filter(Objects::nonNull)
                .map(a -> new TossAccountDto(
                        a.accountNumber(),
                        Objects.requireNonNullElse(a.accountName(), ""),
                        a.balance() != null ? a.balance() : 0.0,
                        a.availableBalance() != null ? a.availableBalance() : 0.0
                ))
                .toList();
    }

    // Marker exception for 401 — triggers retry
    private static class UnauthorizedException extends RuntimeException {
        UnauthorizedException() { super("401 Unauthorized from Toss API"); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossAccountsResponse(
            @JsonProperty("accounts") List<TossAccountItem> accounts
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossAccountItem(
            @JsonProperty("accountNumber") String accountNumber,
            @JsonProperty("accountName") String accountName,
            @JsonProperty("balance") Double balance,
            @JsonProperty("availableBalance") Double availableBalance
    ) {}
}
