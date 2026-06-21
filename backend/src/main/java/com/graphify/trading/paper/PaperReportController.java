package com.graphify.trading.paper;

import com.graphify.common.dto.ApiResponse;
import com.graphify.history.HistoryService;
import com.graphify.trading.paper.dto.ReportDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trading/paper/report")
public class PaperReportController {

    private final PaperReportService reportService;

    public PaperReportController(PaperReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<ReportDto> getReport() {
        Long userId = HistoryService.requireCurrentUserId();
        return ApiResponse.ok(reportService.getReport(userId));
    }
}
