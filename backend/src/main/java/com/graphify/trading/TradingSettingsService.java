package com.graphify.trading;

import com.graphify.common.dto.ApiResponse;
import com.graphify.common.exception.GraphifyException;
import com.graphify.history.HistoryService;
import com.graphify.trading.dto.TradingSettingsDto;
import com.graphify.user.User;
import com.graphify.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TradingSettingsService {

    private final UserRepository userRepository;

    public TradingSettingsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ApiResponse<TradingSettingsDto> getSettings() {
        User user = currentUser();
        return ApiResponse.ok(toDto(user));
    }

    @Transactional
    public ApiResponse<TradingSettingsDto> setMode(String mode) {
        User user = currentUser();

        if (!user.isTradingEnabled()) {
            throw new GraphifyException(
                    "ERR_TRADING_001",
                    "트레이딩 접근 권한이 없습니다.",
                    HttpStatus.FORBIDDEN
            );
        }

        String normalized = mode == null ? "" : mode.trim().toUpperCase();
        if (!normalized.equals("PAPER") && !normalized.equals("LIVE")) {
            throw new GraphifyException(
                    "ERR_TRADING_002",
                    "지원하지 않는 트레이딩 모드입니다. (PAPER 또는 LIVE)",
                    HttpStatus.BAD_REQUEST
            );
        }

        user.setTradingMode(normalized);
        userRepository.save(user);
        return ApiResponse.ok(toDto(user));
    }

    private User currentUser() {
        Long userId = HistoryService.requireCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new GraphifyException(
                        "ERR_USER_001",
                        "사용자 정보를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private static TradingSettingsDto toDto(User user) {
        return new TradingSettingsDto(user.isTradingEnabled(), user.getTradingMode());
    }
}
