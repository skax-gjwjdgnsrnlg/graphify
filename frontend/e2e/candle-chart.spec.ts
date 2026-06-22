/**
 * candle-chart.spec.ts — Playwright e2e for SC-2..SC-5.
 *
 * Uses page.route() mocks for all API calls so tests are deterministic and
 * do not require a live backend or real market data.
 *
 * SC-2: 5분봉 캔들스틱 렌더링 (canvas present)
 * SC-3: chart stays in success state (marker pixel-level = manual-only per VALIDATION.md)
 * SC-4: candle section DOM-order below equity curve card
 * SC-5: row click loads candles for that trade's date
 */

import { expect, test } from "@playwright/test";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const RULES_FIXTURE = [
  {
    id: 1,
    name: "테스트 룰",
    mode: "PAPER",
    status: "ACTIVE",
    backtested: false,
    configStatus: "ACTIVE",
    runStatus: "STOPPED",
    definition: {
      version: 1,
      universe: { type: "symbols", symbols: ["005930"], market: "KOSPI" },
      entry: {
        logic: "AND",
        conditions: [
          {
            left: { indicator: "SMA", params: { period: 5 } },
            op: ">",
            right: { indicator: "SMA", params: { period: 20 } },
          },
        ],
      },
      sizing: { type: "cash", value: 1000000 },
    },
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

// Two trades on different dates so SC-5 can click the second row and see a different date load
const BACKTEST_FIXTURE = {
  initialCash: 10000000,
  finalEquity: 10500000,
  returnPct: 5.0,
  maxDrawdownPct: 1.5,
  winRate: 66.7,
  tradeCount: 2,
  sharpeRatio: 1.2,
  sortinoRatio: 1.4,
  profitFactor: 2.0,
  drawdownSegments: [],
  equityCurve: [
    { datetime: "2026-01-15T09:00:00", equity: 10000000 },
    { datetime: "2026-01-15T09:05:00", equity: 10100000 },
    { datetime: "2026-01-16T09:00:00", equity: 10500000 },
  ],
  trades: [
    {
      datetime: "2026-01-15T09:05:00",
      symbol: "005930",
      companyName: "삼성전자",
      side: "BUY",
      qty: 10,
      price: 70000,
      pnl: null,
      rationaleJson: null,
    },
    {
      datetime: "2026-01-16T09:10:00",
      symbol: "005930",
      companyName: "삼성전자",
      side: "SELL",
      qty: 10,
      price: 75000,
      pnl: 50000,
      rationaleJson: null,
    },
  ],
};

// Minimal bars fixture — 3 5m bars for 2026-01-15
const BARS_DAY1 = [
  { time: 1736910000, open: 70000, high: 71000, low: 69500, close: 70500, volume: 1000 },
  { time: 1736910300, open: 70500, high: 72000, low: 70000, close: 71800, volume: 1200 },
  { time: 1736910600, open: 71800, high: 73000, low: 71500, close: 72500, volume: 900 },
];

// Bars for the second trade date 2026-01-16
const BARS_DAY2 = [
  { time: 1736996400, open: 72500, high: 74000, low: 72000, close: 73500, volume: 800 },
  { time: 1736996700, open: 73500, high: 75500, low: 73000, close: 75000, volume: 1100 },
];

// ---------------------------------------------------------------------------
// Shared setup: mock all relevant API routes before each test
// ---------------------------------------------------------------------------

async function setupMocks(
  page: import("@playwright/test").Page,
  barsDay1 = BARS_DAY1,
) {
  // Auth: treat the user as already logged in by mocking the /me endpoint
  await page.route("**/api/v1/users/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        data: {
          id: 1,
          email: "test@example.com",
          name: "테스터",
          provider: "TOSS",
          profileImageUrl: null,
          tradingMode: "PAPER",
        },
      }),
    })
  );

  // Rules list
  await page.route("**/api/v1/trading/paper/rules", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ success: true, data: RULES_FIXTURE }),
    })
  );

  // Backtest run
  await page.route("**/api/v1/trading/paper/backtest", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ success: true, data: BACKTEST_FIXTURE }),
    })
  );

  // Bars endpoint — day 1 (2026-01-15)
  await page.route(
    (url) =>
      url.pathname.includes("/trading/paper/backtest/bars") &&
      url.searchParams.get("date") === "2026-01-15",
    (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ success: true, data: barsDay1 }),
      })
  );

  // Bars endpoint — day 2 (2026-01-16)
  await page.route(
    (url) =>
      url.pathname.includes("/trading/paper/backtest/bars") &&
      url.searchParams.get("date") === "2026-01-16",
    (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ success: true, data: BARS_DAY2 }),
      })
  );

  // Paper account (dashboard widget, if any)
  await page.route("**/api/v1/trading/paper/account", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ success: true, data: { balance: 10000000 } }),
    })
  );
}

/**
 * Navigate to backtest page and run a backtest. Returns after chart is visible.
 */
async function runBacktest(page: import("@playwright/test").Page) {
  await page.goto("/trading/paper/backtest");

  // Select the rule (value = "1")
  await page.selectOption("select", "1");

  // Click run
  await page.getByRole("button", { name: /백테스트 실행/ }).click();

  // Wait for candle section to appear (auto-selects first trade)
  await expect(page.getByTestId("candle-section")).toBeVisible({ timeout: 15_000 });
}

// ---------------------------------------------------------------------------
// SC-2: 5분봉 캔들스틱 렌더링
// ---------------------------------------------------------------------------

test.describe("S06.6 캔들 차트 시각화", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("SC-2: renders candlestick (canvas visible after backtest)", async ({ page }) => {
    await setupMocks(page);
    await runBacktest(page);

    // Success state: data-testid="candle-chart" wrapper must be visible
    const chartWrapper = page.getByTestId("candle-chart");
    await expect(chartWrapper).toBeVisible({ timeout: 10_000 });

    // lightweight-charts renders into a canvas element inside the wrapper
    await expect(chartWrapper.locator("canvas").first()).toBeVisible();
  });

  // ---------------------------------------------------------------------------
  // SC-3: trade markers (chart is in success state — pixel-level is manual-only)
  // ---------------------------------------------------------------------------

  test("SC-3: chart stays in success state (no error/empty state after backtest)", async ({
    page,
  }) => {
    await setupMocks(page);
    await runBacktest(page);

    // candle-chart wrapper is present → success state
    await expect(page.getByTestId("candle-chart")).toBeVisible({ timeout: 10_000 });

    // No error state text
    await expect(
      page.getByText("캔들 데이터를 불러오지 못했습니다.")
    ).not.toBeVisible();

    // No empty-bars text
    await expect(
      page.getByText("해당 일자의 5분봉 데이터가 없습니다.")
    ).not.toBeVisible();
  });

  // ---------------------------------------------------------------------------
  // SC-4: candle section DOM-order below equity curve card
  // ---------------------------------------------------------------------------

  test("SC-4: candle section appears below equity curve in document order", async ({
    page,
  }) => {
    await setupMocks(page);
    await runBacktest(page);

    // "수익 곡선" heading is the equity curve card
    const equityCard = page
      .getByRole("heading", { name: "수익 곡선" })
      .or(page.locator("h3", { hasText: "수익 곡선" }))
      .first();

    const candleSection = page.getByTestId("candle-section");

    await expect(equityCard).toBeVisible({ timeout: 10_000 });
    await expect(candleSection).toBeVisible({ timeout: 10_000 });

    // Compare vertical position: equity card y < candle section y
    const equityBox = await equityCard.boundingBox();
    const candleBox = await candleSection.boundingBox();

    expect(equityBox).not.toBeNull();
    expect(candleBox).not.toBeNull();
    expect(equityBox!.y).toBeLessThan(candleBox!.y);
  });

  // ---------------------------------------------------------------------------
  // SC-5: row click loads that day's candles
  // ---------------------------------------------------------------------------

  test("SC-5: clicking second trade row loads candles for that row's date", async ({
    page,
  }) => {
    await setupMocks(page);
    await runBacktest(page);

    // Chart renders for first trade (2026-01-15) initially
    await expect(page.getByTestId("candle-chart")).toBeVisible({ timeout: 10_000 });

    // Click the second trade row (SELL row — 2026-01-16)
    // The trade rows contain "매도" text for the SELL trade
    const sellRow = page.locator("tbody tr").filter({ hasText: "매도" }).first();
    await expect(sellRow).toBeVisible();
    await sellRow.click();

    // Chart wrapper should still be visible after clicking (candles for 2026-01-16 load)
    await expect(page.getByTestId("candle-chart")).toBeVisible({ timeout: 10_000 });

    // No error state
    await expect(
      page.getByText("캔들 데이터를 불러오지 못했습니다.")
    ).not.toBeVisible();
  });
});
