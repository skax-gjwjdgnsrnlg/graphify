package com.graphify.trading.paper;

import com.graphify.common.dto.ApiResponse;
import com.graphify.history.HistoryService;
import com.graphify.trading.rule.dto.RuleResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trading/paper/rules")
public class PaperLifecycleController {

    private final PaperLifecycleService lifecycleService;

    public PaperLifecycleController(PaperLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /** DRAFT/BACKTESTED → PAPER_LIVE */
    @PostMapping("/{id}/promote")
    public ApiResponse<RuleResponse> promote(@PathVariable Long id) {
        Long userId = HistoryService.requireCurrentUserId();
        return ApiResponse.ok(lifecycleService.promote(userId, id));
    }

    /** PAPER_LIVE → PAUSED */
    @PostMapping("/{id}/pause")
    public ApiResponse<RuleResponse> pause(@PathVariable Long id) {
        Long userId = HistoryService.requireCurrentUserId();
        return ApiResponse.ok(lifecycleService.pause(userId, id));
    }

    /** PAUSED → PAPER_LIVE */
    @PostMapping("/{id}/resume")
    public ApiResponse<RuleResponse> resume(@PathVariable Long id) {
        Long userId = HistoryService.requireCurrentUserId();
        return ApiResponse.ok(lifecycleService.resume(userId, id));
    }

    /** 모든 상태 → DRAFT 복사본 */
    @PostMapping("/{id}/copy")
    public ApiResponse<RuleResponse> copy(@PathVariable Long id) {
        Long userId = HistoryService.requireCurrentUserId();
        return ApiResponse.ok(lifecycleService.copy(userId, id));
    }
}
