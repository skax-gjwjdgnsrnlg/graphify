package com.graphify.trading;

import com.graphify.common.dto.ApiResponse;
import com.graphify.trading.dto.TradingModeRequest;
import com.graphify.trading.dto.TradingSettingsDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trading")
public class TradingController {

    private final TradingSettingsService tradingSettingsService;

    public TradingController(TradingSettingsService tradingSettingsService) {
        this.tradingSettingsService = tradingSettingsService;
    }

    @GetMapping("/settings")
    public ApiResponse<TradingSettingsDto> getSettings() {
        return tradingSettingsService.getSettings();
    }

    @PutMapping("/mode")
    public ApiResponse<TradingSettingsDto> setMode(@RequestBody TradingModeRequest request) {
        return tradingSettingsService.setMode(request.mode());
    }
}
