package assessment;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static assessment.RebalancingAdapter.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Portfolio Rebalancing Automation Suite")
class PortfolioRebalancingTest {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioRebalancingTest.class);
    private static RebalancingAdapter application;

    @BeforeAll
    static void connectToApplication() throws Exception {
        String adapter = System.getProperty("rebalancing.adapter");
        if (adapter == null || adapter.trim().isEmpty()) {
            logger.info("No external adapter specified; initializing default Mockito mock adapter.");
            application = MockitoRebalancingAdapterFactory.createMock();
        } else {
            logger.info("Connecting to external application adapter: {}", adapter);
            application = (RebalancingAdapter) Class.forName(adapter).getDeclaredConstructor().newInstance();
        }
    }

    private static Request baseline() {
        return new Request(new ArrayList<>(Arrays.asList(
                new Holding("IBM", "20", "10", "150"),
                new Holding("MSFT", "20", "20", "90"),
                new Holding("ORCL", "20", "30", "220"),
                new Holding("AAPL", "20", "20", "450"),
                new Holding("HD", "20", "20", "70")
        )));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void equalNumber(String expected, BigDecimal actual, String context) {
        assertNotNull(actual, context);
        assertEquals(0, decimal(expected).compareTo(actual), context + ": expected " + expected + ", got " + actual);
    }

    private static Result success(Request request, String... shares) throws Exception {
        logger.debug("Executing rebalance request in mode: {}, totalAsset: {}, holdings: {}",
                request.mode, request.totalAsset, request.holdings.size());

        Result result = application.rebalance(request);
        assertNotNull(result, "Application returned a null Result object");
        assertEquals(Status.SUCCESS, result.status, "Expected SUCCESS status but got " + result.status + ": " + result.message);
        assertNotNull(result.positions, "Positions list must not be null");
        assertNotNull(result.orders, "Orders list must not be null");
        assertEquals(request.holdings.size(), shares.length, "Fixture must specify every holding");
        assertEquals(request.holdings.size(), result.positions.size(), "Missing or extra output positions");

        Map<String, Position> positions = new HashMap<>();
        for (Position position : result.positions) {
            assertNull(positions.put(position.symbol, position), "Duplicate output for security: " + position.symbol);
        }

        // Soft assertion grouping across all holdings to report all variances/shares in a single test run
        List<Executable> assertions = new ArrayList<>();
        for (int i = 0; i < shares.length; i++) {
            Holding holding = request.holdings.get(i);
            Position position = positions.get(holding.symbol);
            assertNotNull(position, "Missing recommendation for security " + holding.symbol);

            String expectedShare = shares[i];
            assertions.add(() -> equalNumber(expectedShare, position.signedShares, holding.symbol + " signed shares"));

            String expectedVariance = decimal(holding.currentPercent).subtract(decimal(holding.targetPercent)).toPlainString();
            assertions.add(() -> equalNumber(expectedVariance, position.variance, holding.symbol + " variance"));
        }
        assertAll("Portfolio position shares and variances", assertions);

        logger.debug("Successfully validated {} positions and {} orders", result.positions.size(), result.orders.size());
        return result;
    }

    private static void rejected(Request request, String message) throws Exception {
        logger.debug("Executing negative validation scenario expecting error containing: '{}'", message);

        Result result = application.rebalance(request);
        assertNotNull(result, "Application returned a null Result object");

        assertAll("Input validation failure contract",
                () -> assertEquals(Status.VALIDATION_ERROR, result.status, "Status must be VALIDATION_ERROR"),
                () -> assertNotNull(result.message, "Validation message must not be null"),
                () -> {
                    if (message != null) {
                        assertTrue(result.message.contains(message),
                                "Expected error to contain [" + message + "] but received: [" + result.message + "]");
                    } else {
                        assertFalse(result.message.trim().isEmpty(), "Validation must explain the invalid input");
                    }
                },
                () -> assertNotNull(result.positions, "Positions list must not be null"),
                () -> assertNotNull(result.orders, "Orders list must not be null"),
                () -> assertTrue(result.positions.isEmpty(), "Invalid input must not produce recommendations"),
                () -> assertTrue(result.orders.isEmpty(), "Invalid input must not queue trades")
        );
    }

    @Test
    @Tag("high")
    @DisplayName("TC_POS_001: Baseline Account ABC fractional rebalance (IBM buy 66.67, ORCL sell -45.45)")
    void TC_POS_001_baselineFractional() throws Exception {
        success(baseline(), "66.67", "0", "-45.45", "0", "0");
    }

    @Test
    @Tag("high")
    @DisplayName("TC_POS_002: Baseline Account ABC whole shares truncation (IBM buy 66, ORCL sell -45)")
    void TC_POS_002_baselineWholeShares() throws Exception {
        Request r = baseline();
        r.mode = Mode.WHOLE;
        success(r, "66", "0", "-45", "0", "0");
    }

    @ParameterizedTest(name = "TC_POS_003: Exact division with assessmentPrices={0}")
    @ValueSource(booleans = {false, true})
    @Tag("high")
    @DisplayName("TC_POS_003: Exact integer division isolates math from floating-point rounding")
    void TC_POS_003_exactDivisionBothPriceOptions(boolean assessmentPrices) throws Exception {
        Request r = baseline();
        if (assessmentPrices) {
            r.holdings.get(0).targetPercent = "25";
            r.holdings.get(2).targetPercent = "8";
            r.holdings.get(1).targetPercent = "27";
            r.holdings.get(1).unitPrice = "100";
            success(r, "100", "70", "-100", "0", "0");
        } else {
            r.holdings.get(0).unitPrice = "100";
            r.holdings.get(2).unitPrice = "200";
            success(r, "100", "0", "-50", "0", "0");
        }
    }

    @Test
    @Tag("high")
    @DisplayName("TC_POS_004: Perfectly balanced portfolio produces 0 shares and queues no orders")
    void TC_POS_004_balancedPortfolio() throws Exception {
        Request r = baseline();
        r.holdings.forEach(h -> h.currentPercent = "20");
        assertTrue(success(r, "0", "0", "0", "0", "0").orders.isEmpty(), "Balanced portfolio must not queue orders");
    }

    @Test
    @Tag("high")
    @DisplayName("TC_POS_005: All positions change; validates cash neutrality (Buy cost equals sell proceeds)")
    void TC_POS_005_allPositionsChange() throws Exception {
        Request r = baseline();
        String[] targets = {"25", "11", "8", "29", "27"};
        for (int i = 0; i < targets.length; i++) {
            r.holdings.get(i).targetPercent = targets[i];
        }
        Result result = success(r, "100", "-100", "-100", "20", "100");
        BigDecimal netCost = BigDecimal.ZERO;
        for (Position p : result.positions) {
            Holding h = r.holdings.stream().filter(x -> x.symbol.equals(p.symbol)).findFirst().orElseThrow();
            netCost = netCost.add(p.signedShares.multiply(decimal(h.unitPrice)));
        }
        equalNumber("0", netCost, "Buy cost equals sale proceeds");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_POS_006: Adding brand new position (current% = 0%, target% > 0%)")
    void TC_POS_006_newPosition() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "10";
        r.holdings.get(0).currentPercent = "0";
        r.holdings.get(1).targetPercent = "30";
        r.holdings.get(1).currentPercent = "30";
        success(r, "66.67", "0", "-45.45", "0", "0");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_POS_007: Full security liquidation (current% > 0%, target% = 0%)")
    void TC_POS_007_liquidation() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "40";
        r.holdings.get(2).targetPercent = "0";
        success(r, "200", "0", "-136.36", "0", "0");
    }

    @ParameterizedTest(name = "TC_BND_001: Sub-share variance in wholeMode={0}")
    @ValueSource(booleans = {false, true})
    @Tag("medium")
    @DisplayName("TC_BND_001: Sub-share variance when dollar deviation is smaller than 1 unit price")
    void TC_BND_001_subShareVariance(boolean whole) throws Exception {
        Request r = baseline();
        r.totalAsset = "10000";
        r.holdings.forEach(h -> h.currentPercent = "20");
        r.holdings.get(3).targetPercent = "20.1";
        r.holdings.get(0).targetPercent = "19.9"; // Complete allocations to 100%.
        if (whole) {
            r.mode = Mode.WHOLE;
        }
        success(r, whole ? "0" : "-0.07", "0", "0", whole ? "0" : "0.02", "0");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_BND_002: Single-stock 100% target allocation liquidating all other positions")
    void TC_BND_002_singleStockTarget() throws Exception {
        Request r = baseline();
        r.holdings.forEach(h -> {
            h.currentPercent = "20";
            h.targetPercent = "0";
        });
        r.holdings.get(0).targetPercent = "100";
        success(r, "533.33", "-222.22", "-90.91", "-44.44", "-285.71");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_BND_003: Multi-decimal allocation percentages (e.g. 20.55%, 10.05%)")
    void TC_BND_003_decimalPercentages() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "20.55";
        r.holdings.get(0).currentPercent = "10.05";
        r.holdings.get(2).targetPercent = "19.45";
        r.holdings.get(2).currentPercent = "29.95";
        success(r, "70", "0", "-47.73", "0", "0");
    }

    @Test
    @Tag("low")
    @DisplayName("TC_BND_004: Large institutional portfolio ($10 Billion) without numeric overflow")
    void TC_BND_004_largePortfolio() throws Exception {
        Request r = baseline();
        r.totalAsset = "10000000000";
        success(r, "6666666.67", "0", "-4545454.55", "0", "0");
    }

    @Test
    @Tag("low")
    @DisplayName("TC_BND_005: Penny stock with micro unit price ($0.01)")
    void TC_BND_005_pennyStock() throws Exception {
        Request r = baseline();
        r.holdings.get(0).unitPrice = "0.01";
        success(r, "1000000", "0", "-45.45", "0", "0");
    }

    @ParameterizedTest(name = "TC_BND_006: High stock price in wholeMode={0}")
    @ValueSource(booleans = {false, true})
    @Tag("low")
    @DisplayName("TC_BND_006: High stock price ($600,000) exceeding portfolio asset value")
    void TC_BND_006_priceExceedsAssets(boolean whole) throws Exception {
        Request r = baseline();
        r.holdings.get(0).symbol = "BRK.A";
        r.holdings.get(0).unitPrice = "600000";
        r.fractionalScale = 4; // Explicit exception in manual expected result.
        if (whole) {
            r.mode = Mode.WHOLE;
        }
        Result result = success(r, whole ? "0" : "0.0167", "0", whole ? "-45" : "-45.4545", "0", "0");
        if (whole) {
            assertNotNull(result.warnings, "Warnings list must not be null");
            assertTrue(
                    result.warnings.stream().anyMatch(w -> w != null && !w.trim().isEmpty()),
                    "Unreachable whole-share target needs a warning"
            );
        }
    }

    @Test
    @Tag("high")
    @DisplayName("TC_NEG_001: Reject target percentage sum < 100% (Sum: 90%)")
    void TC_NEG_001_targetBelow100() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "10";
        rejected(r, "Total Target Percentage must equal 100% (Current sum: 90%).");
    }

    @Test
    @Tag("high")
    @DisplayName("TC_NEG_002: Reject target percentage sum > 100% (Sum: 110%)")
    void TC_NEG_002_targetAbove100() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "30";
        rejected(r, "Total Target Percentage cannot exceed 100% (Current sum: 110%).");
    }

    @ParameterizedTest(name = "TC_NEG_003: Invalid current sum with currentPercent={0}")
    @ValueSource(strings = {"5", "15"})
    @Tag("high")
    @DisplayName("TC_NEG_003: Reject current portfolio allocation sum not equal to 100%")
    void TC_NEG_003_invalidCurrentSum(String current) throws Exception {
        Request r = baseline();
        r.holdings.get(0).currentPercent = current;
        rejected(r, "Current portfolio allocation percentages must sum to 100%.");
    }

    @Test
    @Tag("high")
    @DisplayName("TC_NEG_004: Reject unit price = $0 (Division by zero protection)")
    void TC_NEG_004_zeroPrice() throws Exception {
        Request r = baseline();
        r.holdings.get(0).unitPrice = "0";
        rejected(r, "Unit price for IBM must be greater than zero.");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_NEG_005: Reject negative unit price")
    void TC_NEG_005_negativePrice() throws Exception {
        Request r = baseline();
        r.holdings.get(0).unitPrice = "-150";
        rejected(r, "Invalid unit price: Price cannot be negative.");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_NEG_006: Reject negative target percentage")
    void TC_NEG_006_negativeTarget() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "-10";
        r.holdings.get(1).targetPercent = "50";
        rejected(r, "Target percentage for IBM must be between 0% and 100%.");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_NEG_007: Reject negative current percentage (Short positions not supported)")
    void TC_NEG_007_negativeCurrent() throws Exception {
        Request r = baseline();
        r.holdings.get(0).currentPercent = "-10";
        r.holdings.get(1).currentPercent = "40";
        rejected(r, "Current percentage for IBM cannot be negative (Short positions not supported in long-only account).");
    }

    @ParameterizedTest(name = "TC_NEG_008: Non-positive assets={0}")
    @ValueSource(strings = {"0", "-100000"})
    @Tag("medium")
    @DisplayName("TC_NEG_008: Reject negative Total Asset and handle NO_ASSETS on $0")
    void TC_NEG_008_nonPositiveAssets(String assets) throws Exception {
        Request r = baseline();
        r.totalAsset = assets;
        if (assets.startsWith("-")) {
            rejected(r, "Total Asset value cannot be negative.");
        } else {
            Result result = application.rebalance(r);
            assertNotNull(result, "Application returned null Result");
            assertAll("Zero assets validation",
                    () -> assertEquals(Status.NO_ASSETS, result.status),
                    () -> assertNotNull(result.message),
                    () -> assertFalse(result.message.trim().isEmpty(), "Message should explain zero assets"),
                    () -> assertNotNull(result.orders),
                    () -> assertTrue(result.orders.isEmpty(), "Zero assets must not queue orders"),
                    () -> assertNotNull(result.positions),
                    () -> result.positions.forEach(p -> equalNumber("0", p.signedShares, "Zero assets must not trade"))
            );
        }
    }

    @ParameterizedTest(name = "TC_NEG_009: Invalid input payload='{0}'")
    @NullSource
    @ValueSource(strings = {"abc", "$150#", ""})
    @Tag("low")
    @DisplayName("TC_NEG_009: Input sanitization rejecting non-numeric and null values")
    void TC_NEG_009_invalidNumericFields(String invalid) throws Exception {
        // Independent requests ensure each field's validation is reached.
        Request target = baseline();
        target.holdings.get(0).targetPercent = invalid;
        rejected(target, null);

        Request current = baseline();
        current.holdings.get(0).currentPercent = invalid;
        rejected(current, null);

        Request price = baseline();
        price.holdings.get(0).unitPrice = invalid;
        rejected(price, null);

        Request assets = baseline();
        assets.totalAsset = invalid;
        rejected(assets, null);

        Request vested = baseline();
        vested.vestedPercent = invalid;
        rejected(vested, null);
    }

    @Test
    @Tag("low")
    @DisplayName("TC_NEG_010: Reject duplicate security tickers in same portfolio (IBM)")
    void TC_NEG_010_duplicateTicker() throws Exception {
        Request r = baseline();
        r.holdings.get(1).symbol = "IBM";
        r.holdings.get(1).targetPercent = "25";
        r.holdings.get(0).targetPercent = "15"; // Valid totals isolate duplicate validation.
        rejected(r, "Duplicate security symbol 'IBM' detected.");
    }

    @Test
    @Tag("high")
    @DisplayName("TC_DOM_001: 50% partial vesting scales trade amounts proportionally")
    void TC_DOM_001_partialVesting() throws Exception {
        Request r = baseline();
        r.vestedPercent = "50";
        success(r, "33.33", "0", "-22.73", "0", "0");
    }

    @Test
    @Tag("high")
    @DisplayName("TC_DOM_002: Self-funding constraint in whole share mode prevents cash overdraft")
    void TC_DOM_002_wholeSharesSelfFunding() throws Exception {
        Request r = baseline();
        r.mode = Mode.WHOLE;
        Result result = success(r, "66", "0", "-45", "0", "0");
        BigDecimal remainingCash = decimal(r.availableCash);
        for (Position p : result.positions) {
            Holding h = r.holdings.stream().filter(x -> x.symbol.equals(p.symbol)).findFirst().orElseThrow();
            equalNumber(p.signedShares.setScale(0, RoundingMode.DOWN).toPlainString(), p.signedShares, "Whole shares only");
            remainingCash = remainingCash.subtract(p.signedShares.multiply(decimal(h.unitPrice)));
        }
        assertTrue(remainingCash.signum() >= 0, "Recommendations must not overdraw cash");
    }

    @Test
    @Tag("medium")
    @DisplayName("TC_DOM_003: Sells are queued before buys to ensure settlement and cash neutrality")
    void TC_DOM_003_sellsQueuedBeforeBuys() throws Exception {
        Result result = success(baseline(), "66.67", "0", "-45.45", "0", "0");
        assertAll("Sell-before-buy trade queue verification",
                () -> assertEquals(2, result.orders.size(), "Only IBM and ORCL require orders"),
                () -> assertEquals("ORCL", result.orders.get(0).symbol),
                () -> equalNumber("-45.45", result.orders.get(0).signedShares, "First order sells ORCL"),
                () -> assertEquals("IBM", result.orders.get(1).symbol),
                () -> equalNumber("66.67", result.orders.get(1).signedShares, "Second order buys IBM")
        );
    }
}
