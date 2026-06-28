package com.graphify.trading.paper;

import com.graphify.common.dto.ApiResponse;
import com.graphify.history.HistoryService;
import com.graphify.trading.paper.dto.RunSummaryDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/trading/paper/runs — 실행 이력 리스트.
 *
 * 위임: PaperRunListService.listRuns(userId) → List<RunListItem> → List<RunSummaryDto> 변환.
 */
@RestController
@RequestMapping("/api/v1/trading/paper/runs")
public class PaperRunListController {

    private final PaperRunListService listService;

    public PaperRunListController(PaperRunListService listService) {
        this.listService = listService;
    }

    @GetMapping
    public ApiResponse<List<RunSummaryDto>> getRuns() {
        Long userId = HistoryService.requireCurrentUserId();
        List<RunSummaryDto> runs = listService.listRuns(userId).stream()
                .map(item -> new RunSummaryDto(
                        item.runId(),
                        item.ruleId(),
                        item.ruleName(),
                        item.runIndex(),
                        item.status(),
                        item.startedAt(),
                        item.endedAt(),
                        item.universe(),
                        item.realizedPnl(),
                        item.returnPct(),
                        item.tradeCount(),
                        item.finalEquity()))
                .toList();
        return ApiResponse.ok(runs);
    }
}
