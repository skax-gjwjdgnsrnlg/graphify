package com.graphify.trading.paper;

import com.graphify.trading.paper.dto.ReportDto;
import com.graphify.trading.paper.dto.ReportDto.EquityPoint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaperReportService {

    private static final int MIN_RATIO_POINTS = 5;
    private static final double ANNUALIZE = Math.sqrt(252.0);

    private final PaperAccountRepository accountRepo;
    private final PaperEquitySnapshotRepository snapshotRepo;
    private final PaperTradeRepository tradeRepo;

    public PaperReportService(
            PaperAccountRepository accountRepo,
            PaperEquitySnapshotRepository snapshotRepo,
            PaperTradeRepository tradeRepo) {
        this.accountRepo = accountRepo;
        this.snapshotRepo = snapshotRepo;
        this.tradeRepo = tradeRepo;
    }

    public ReportDto getReport(Long userId) {
        Optional<PaperAccount> accountOpt = accountRepo.findByUserId(userId);
        if (accountOpt.isEmpty()) {
            return ReportDto.empty();
        }
        Long accountId = accountOpt.get().getId();

        // Equity curve: snapshots in ascending ts order, last 30 days
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<PaperEquitySnapshot> rawSnapshots =
                snapshotRepo.findByAccountIdOrderByTsDesc(accountId);

        // Reverse to ascending, filter last 30 days
        List<PaperEquitySnapshot> snapshots = new ArrayList<>();
        for (int i = rawSnapshots.size() - 1; i >= 0; i--) {
            PaperEquitySnapshot s = rawSnapshots.get(i);
            if (!s.getTs().isBefore(cutoff)) {
                snapshots.add(s);
            }
        }

        if (snapshots.isEmpty()) {
            return buildTradeStatsOnly(accountId);
        }

        // Build equity curve
        List<EquityPoint> equityCurve = snapshots.stream()
                .map(s -> new EquityPoint(s.getTs().toString(), s.getEquity().doubleValue()))
                .toList();

        double[] equities = snapshots.stream()
                .mapToDouble(s -> s.getEquity().doubleValue())
                .toArray();

        double firstEquity = equities[0];
        double lastEquity = equities[equities.length - 1];
        double totalReturn = firstEquity > 0 ? (lastEquity - firstEquity) / firstEquity * 100.0 : 0.0;
        double maxDrawdownPct = computeMdd(equities);

        // Daily returns for Sharpe/Sortino
        double[] dailyReturns = computeDailyReturns(equities);
        double sharpeRatio = dailyReturns.length >= MIN_RATIO_POINTS ? computeSharpe(dailyReturns) : 0.0;
        double sortinoRatio = dailyReturns.length >= MIN_RATIO_POINTS ? computeSortino(dailyReturns) : 0.0;

        // Trade stats from SELL trades
        int[] tradeStats = computeTradeStats(accountId);
        int totalTrades = tradeStats[0];
        int winTrades = tradeStats[1];
        double winRate = totalTrades > 0 ? (double) winTrades / totalTrades * 100.0 : 0.0;

        Instant periodFrom = snapshots.get(0).getTs();
        Instant periodTo = snapshots.get(snapshots.size() - 1).getTs();

        return new ReportDto(equityCurve, totalReturn, maxDrawdownPct, winRate,
                totalTrades, winTrades, sharpeRatio, sortinoRatio, periodFrom, periodTo);
    }

    // Build report with only trade stats (no snapshots yet)
    private ReportDto buildTradeStatsOnly(Long accountId) {
        int[] tradeStats = computeTradeStats(accountId);
        int totalTrades = tradeStats[0];
        int winTrades = tradeStats[1];
        double winRate = totalTrades > 0 ? (double) winTrades / totalTrades * 100.0 : 0.0;
        return new ReportDto(List.of(), 0.0, 0.0, winRate, totalTrades, winTrades, 0.0, 0.0, null, null);
    }

    /** Max drawdown as percentage from peak */
    private double computeMdd(double[] equities) {
        double peak = equities[0];
        double maxDd = 0.0;
        for (double eq : equities) {
            if (eq > peak) peak = eq;
            double dd = peak > 0 ? (peak - eq) / peak * 100.0 : 0.0;
            if (dd > maxDd) maxDd = dd;
        }
        return maxDd;
    }

    /** Simple daily returns: (eq[i] - eq[i-1]) / eq[i-1] */
    private double[] computeDailyReturns(double[] equities) {
        if (equities.length < 2) return new double[0];
        double[] returns = new double[equities.length - 1];
        for (int i = 1; i < equities.length; i++) {
            returns[i - 1] = equities[i - 1] > 0
                    ? (equities[i] - equities[i - 1]) / equities[i - 1]
                    : 0.0;
        }
        return returns;
    }

    private double computeSharpe(double[] returns) {
        double mean = mean(returns);
        double std = std(returns);
        return std > 0 ? mean / std * ANNUALIZE : 0.0;
    }

    private double computeSortino(double[] returns) {
        double mean = mean(returns);
        // Downside std: only negative returns
        double sumSq = 0.0;
        int count = 0;
        for (double r : returns) {
            if (r < 0) { sumSq += r * r; count++; }
        }
        double downsideStd = count > 0 ? Math.sqrt(sumSq / count) : 0.0;
        return downsideStd > 0 ? mean / downsideStd * ANNUALIZE : 0.0;
    }

    private double mean(double[] arr) {
        if (arr.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    private double std(double[] arr) {
        if (arr.length < 2) return 0.0;
        double m = mean(arr);
        double sumSq = 0.0;
        for (double v : arr) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / arr.length);
    }

    /** Returns [totalSellTrades, winTrades] */
    private int[] computeTradeStats(Long accountId) {
        List<PaperTrade> trades = tradeRepo.findByAccountIdOrderByTradedAtDesc(accountId);
        int total = 0;
        int win = 0;
        for (PaperTrade t : trades) {
            if ("SELL".equals(t.getSide())) {
                total++;
                if (t.getPnl() != null && t.getPnl().doubleValue() > 0) win++;
            }
        }
        return new int[]{total, win};
    }
}
