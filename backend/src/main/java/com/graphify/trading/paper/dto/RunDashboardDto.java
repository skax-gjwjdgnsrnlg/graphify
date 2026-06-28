package com.graphify.trading.paper.dto;

import java.util.List;

/**
 * GET /api/v1/trading/paper/runs/{runId}/dashboard 응답 DTO.
 *
 * D5(단일 계좌 공유) 전제:
 *  - totalEquity / availableCash = 계좌 전체
 *  - realizedPnl / unrealizedPnl / tradeCount / positions = run 기여분
 */
public record RunDashboardDto(
        Long runId,
        int runIndex,           // 동일 전략 내 회차 번호 (1-based)
        String ruleName,
        String status,          // RUNNING | STOPPED
        double totalEquity,     // account 전체 자산
        double availableCash,   // account 가용 현금
        double realizedPnl,     // run 스코프 실현손익
        double unrealizedPnl,   // run 스코프 미실현손익
        int tradeCount,         // run 스코프 진입(BUY) 횟수
        List<PaperPositionItem> positions   // run의 오픈 포지션 (paper_trades 파생)
) {}
