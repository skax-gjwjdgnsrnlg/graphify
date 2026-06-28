package com.graphify.trading.paper.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/trading/paper/runs 리스트 행 DTO.
 *
 * 컬럼: 전략명·상태·기간·유니버스·수익률·실현손익·거래수·최종자산·회차.
 * Wave 3 PaperRunListController에서 PaperRunListService.RunListItem을 이 DTO로 변환한다.
 */
public record RunSummaryDto(
        Long runId,
        Long ruleId,
        String ruleName,
        int runIndex,        // 동일 전략 내 회차 번호 (1-based)
        String status,       // RUNNING | STOPPED
        Instant startedAt,
        Instant endedAt,     // null = 진행중
        List<String> universe,
        double realizedPnl,
        double returnPct,
        int tradeCount,      // 진입(BUY) 횟수
        double finalEquity
) {}
