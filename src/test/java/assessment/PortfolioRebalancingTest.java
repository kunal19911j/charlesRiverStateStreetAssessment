package assessment;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static assessment.RebalancingAdapter.*;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioRebalancingTest {
    private static RebalancingAdapter application;

    @BeforeAll
    static void connectToApplication() throws Exception {
        String adapter = System.getProperty("rebalancing.adapter");
        assertNotNull(adapter, "No application connected. Supply -Drebalancing.adapter=<adapter class>; see README.md.");
        application = (RebalancingAdapter) Class.forName(adapter).getDeclaredConstructor().newInstance();
    }

    private static Request baseline() {
        return new Request(new ArrayList<>(Arrays.asList(
                new Holding("IBM", "20", "10", "150"),
                new Holding("MSFT", "20", "20", "90"),
                new Holding("ORCL", "20", "30", "220"),
                new Holding("AAPL", "20", "20", "450"),
                new Holding("HD", "20", "20", "70"))));
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }

    private static void equalNumber(String expected, BigDecimal actual, String context) {
        assertNotNull(actual, context);
        assertEquals(0, decimal(expected).compareTo(actual), context + ": expected " + expected + ", got " + actual);
    }

    private static Result success(Request request, String... shares) throws Exception {
        Result result = application.rebalance(request);
        assertNotNull(result);
        assertEquals(Status.SUCCESS, result.status, result.message);
        assertNotNull(result.positions);
        assertNotNull(result.orders);
        assertEquals(request.holdings.size(), shares.length, "Fixture must specify every holding");
        assertEquals(request.holdings.size(), result.positions.size(), "Missing or extra output positions");
        Map<String, Position> positions = new HashMap<>();
        for (Position position : result.positions) {
            assertNull(positions.put(position.symbol, position), "Duplicate output: " + position.symbol);
        }
        for (int i = 0; i < shares.length; i++) {
            Holding holding = request.holdings.get(i);
            Position position = positions.get(holding.symbol);
            assertNotNull(position, "Missing " + holding.symbol);
            equalNumber(shares[i], position.signedShares, holding.symbol + " signed shares");
            equalNumber(decimal(holding.currentPercent).subtract(decimal(holding.targetPercent)).toPlainString(),
                    position.variance, holding.symbol + " variance");
        }
        return result;
    }

    private static void rejected(Request request, String message) throws Exception {
        Result result = application.rebalance(request);
        assertNotNull(result);
        assertEquals(Status.VALIDATION_ERROR, result.status);
        assertNotNull(result.message);
        if (message != null) assertTrue(result.message.contains(message), result.message);
        else assertFalse(result.message.trim().isEmpty(), "Validation must explain the invalid input");
        assertNotNull(result.positions);
        assertNotNull(result.orders);
        assertTrue(result.positions.isEmpty(), "Invalid input must not produce recommendations");
        assertTrue(result.orders.isEmpty(), "Invalid input must not queue trades");
    }

    @Test @Tag("high")
    void TC_POS_001_baselineFractional() throws Exception {
        success(baseline(), "66.67", "0", "-45.45", "0", "0");
    }

    @Test @Tag("high")
    void TC_POS_002_baselineWholeShares() throws Exception {
        Request r = baseline(); r.mode = Mode.WHOLE;
        success(r, "66", "0", "-45", "0", "0");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Tag("high")
    void TC_POS_003_exactDivisionBothPriceOptions(boolean assessmentPrices) throws Exception {
        Request r = baseline();
        if (assessmentPrices) {
            r.holdings.get(0).targetPercent = "25";
            r.holdings.get(2).targetPercent = "8";
            r.holdings.get(1).targetPercent = "27";
            r.holdings.get(1).unitPrice = "100";
            success(r, "100", "70", "-100", "0", "0");
        } else {
            r.holdings.get(0).unitPrice = "100"; r.holdings.get(2).unitPrice = "200";
            success(r, "100", "0", "-50", "0", "0");
        }
    }

    @Test @Tag("high")
    void TC_POS_004_balancedPortfolio() throws Exception {
        Request r = baseline(); r.holdings.forEach(h -> h.currentPercent = "20");
        assertTrue(success(r, "0", "0", "0", "0", "0").orders.isEmpty(), "Balanced portfolio must not queue orders");
    }

    @Test @Tag("high")
    void TC_POS_005_allPositionsChange() throws Exception {
        Request r = baseline();
        String[] targets = {"25", "11", "8", "29", "27"};
        for (int i = 0; i < targets.length; i++) r.holdings.get(i).targetPercent = targets[i];
        Result result = success(r, "100", "-100", "-100", "20", "100");
        BigDecimal netCost = BigDecimal.ZERO;
        for (Position p : result.positions) {
            Holding h = r.holdings.stream().filter(x -> x.symbol.equals(p.symbol)).findFirst().orElseThrow();
            netCost = netCost.add(p.signedShares.multiply(decimal(h.unitPrice)));
        }
        equalNumber("0", netCost, "Buy cost equals sale proceeds");
    }

    @Test @Tag("medium")
    void TC_POS_006_newPosition() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "10"; r.holdings.get(0).currentPercent = "0";
        r.holdings.get(1).targetPercent = "30"; r.holdings.get(1).currentPercent = "30";
        success(r, "66.67", "0", "-45.45", "0", "0");
    }

    @Test @Tag("medium")
    void TC_POS_007_liquidation() throws Exception {
        Request r = baseline(); r.holdings.get(0).targetPercent = "40"; r.holdings.get(2).targetPercent = "0";
        success(r, "200", "0", "-136.36", "0", "0");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Tag("medium")
    void TC_BND_001_subShareVariance(boolean whole) throws Exception {
        Request r = baseline(); r.totalAsset = "10000";
        r.holdings.forEach(h -> h.currentPercent = "20");
        r.holdings.get(3).targetPercent = "20.1";
        r.holdings.get(0).targetPercent = "19.9"; // Complete allocations to 100%.
        if (whole) r.mode = Mode.WHOLE;
        success(r, whole ? "0" : "-0.07", "0", "0", whole ? "0" : "0.02", "0");
    }

    @Test @Tag("medium")
    void TC_BND_002_singleStockTarget() throws Exception {
        Request r = baseline();
        r.holdings.forEach(h -> { h.currentPercent = "20"; h.targetPercent = "0"; });
        r.holdings.get(0).targetPercent = "100";
        success(r, "533.33", "-222.22", "-90.91", "-44.44", "-285.71");
    }

    @Test @Tag("medium")
    void TC_BND_003_decimalPercentages() throws Exception {
        Request r = baseline();
        r.holdings.get(0).targetPercent = "20.55"; r.holdings.get(0).currentPercent = "10.05";
        r.holdings.get(2).targetPercent = "19.45"; r.holdings.get(2).currentPercent = "29.95";
        success(r, "70", "0", "-47.73", "0", "0");
    }

    @Test @Tag("low")
    void TC_BND_004_largePortfolio() throws Exception {
        Request r = baseline(); r.totalAsset = "10000000000";
        success(r, "6666666.67", "0", "-4545454.55", "0", "0");
    }

    @Test @Tag("low")
    void TC_BND_005_pennyStock() throws Exception {
        Request r = baseline(); r.holdings.get(0).unitPrice = "0.01";
        success(r, "1000000", "0", "-45.45", "0", "0");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Tag("low")
    void TC_BND_006_priceExceedsAssets(boolean whole) throws Exception {
        Request r = baseline(); r.holdings.get(0).symbol = "BRK.A"; r.holdings.get(0).unitPrice = "600000";
        r.fractionalScale = 4; // Explicit exception in manual expected result.
        if (whole) r.mode = Mode.WHOLE;
        Result result = success(r, whole ? "0" : "0.0167", "0", whole ? "-45" : "-45.4545", "0", "0");
        if (whole) {
            assertNotNull(result.warnings);
            assertTrue(result.warnings.stream().anyMatch(w -> w != null && !w.trim().isEmpty()), "Unreachable whole-share target needs a warning");
        }
    }

    @Test @Tag("high")
    void TC_NEG_001_targetBelow100() throws Exception {
        Request r = baseline(); r.holdings.get(0).targetPercent = "10";
        rejected(r, "Total Target Percentage must equal 100% (Current sum: 90%).");
    }

    @Test @Tag("high")
    void TC_NEG_002_targetAbove100() throws Exception {
        Request r = baseline(); r.holdings.get(0).targetPercent = "30";
        rejected(r, "Total Target Percentage cannot exceed 100% (Current sum: 110%).");
    }

    @ParameterizedTest @ValueSource(strings = {"5", "15"}) @Tag("high")
    void TC_NEG_003_invalidCurrentSum(String current) throws Exception {
        Request r = baseline(); r.holdings.get(0).currentPercent = current;
        rejected(r, "Current portfolio allocation percentages must sum to 100%.");
    }

    @Test @Tag("high")
    void TC_NEG_004_zeroPrice() throws Exception {
        Request r = baseline(); r.holdings.get(0).unitPrice = "0";
        rejected(r, "Unit price for IBM must be greater than zero.");
    }

    @Test @Tag("medium")
    void TC_NEG_005_negativePrice() throws Exception {
        Request r = baseline(); r.holdings.get(0).unitPrice = "-150";
        rejected(r, "Invalid unit price: Price cannot be negative.");
    }

    @Test @Tag("medium")
    void TC_NEG_006_negativeTarget() throws Exception {
        Request r = baseline(); r.holdings.get(0).targetPercent = "-10"; r.holdings.get(1).targetPercent = "50";
        rejected(r, "Target percentage for IBM must be between 0% and 100%.");
    }

    @Test @Tag("medium")
    void TC_NEG_007_negativeCurrent() throws Exception {
        Request r = baseline(); r.holdings.get(0).currentPercent = "-10"; r.holdings.get(1).currentPercent = "40";
        rejected(r, "Current percentage for IBM cannot be negative (Short positions not supported in long-only account).");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-100000"}) @Tag("medium")
    void TC_NEG_008_nonPositiveAssets(String assets) throws Exception {
        Request r = baseline(); r.totalAsset = assets;
        if (assets.startsWith("-")) rejected(r, "Total Asset value cannot be negative.");
        else {
            Result result = application.rebalance(r);
            assertNotNull(result);
            assertEquals(Status.NO_ASSETS, result.status);
            assertNotNull(result.message); assertFalse(result.message.trim().isEmpty());
            assertNotNull(result.orders); assertTrue(result.orders.isEmpty());
            assertNotNull(result.positions);
            result.positions.forEach(p -> equalNumber("0", p.signedShares, "Zero assets must not trade"));
        }
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"abc", "$150#", ""}) @Tag("low")
    void TC_NEG_009_invalidNumericFields(String invalid) throws Exception {
        // Independent requests ensure each field's validation is reached.
        Request target = baseline(); target.holdings.get(0).targetPercent = invalid; rejected(target, null);
        Request current = baseline(); current.holdings.get(0).currentPercent = invalid; rejected(current, null);
        Request price = baseline(); price.holdings.get(0).unitPrice = invalid; rejected(price, null);
        Request assets = baseline(); assets.totalAsset = invalid; rejected(assets, null);
        Request vested = baseline(); vested.vestedPercent = invalid; rejected(vested, null);
    }

    @Test @Tag("low")
    void TC_NEG_010_duplicateTicker() throws Exception {
        Request r = baseline(); r.holdings.get(1).symbol = "IBM"; r.holdings.get(1).targetPercent = "25";
        r.holdings.get(0).targetPercent = "15"; // Valid totals isolate duplicate validation.
        rejected(r, "Duplicate security symbol 'IBM' detected.");
    }

    @Test @Tag("high")
    void TC_DOM_001_partialVesting() throws Exception {
        Request r = baseline(); r.vestedPercent = "50";
        success(r, "33.33", "0", "-22.73", "0", "0");
    }

    @Test @Tag("high")
    void TC_DOM_002_wholeSharesSelfFunding() throws Exception {
        Request r = baseline(); r.mode = Mode.WHOLE;
        Result result = success(r, "66", "0", "-45", "0", "0");
        BigDecimal remainingCash = decimal(r.availableCash);
        for (Position p : result.positions) {
            Holding h = r.holdings.stream().filter(x -> x.symbol.equals(p.symbol)).findFirst().orElseThrow();
            equalNumber(p.signedShares.setScale(0, java.math.RoundingMode.DOWN).toPlainString(), p.signedShares, "Whole shares only");
            remainingCash = remainingCash.subtract(p.signedShares.multiply(decimal(h.unitPrice)));
        }
        assertTrue(remainingCash.signum() >= 0, "Recommendations must not overdraw cash");
    }

    @Test @Tag("medium")
    void TC_DOM_003_sellsQueuedBeforeBuys() throws Exception {
        Result result = success(baseline(), "66.67", "0", "-45.45", "0", "0");
        assertEquals(2, result.orders.size(), "Only IBM and ORCL require orders");
        assertEquals("ORCL", result.orders.get(0).symbol);
        equalNumber("-45.45", result.orders.get(0).signedShares, "First order sells ORCL");
        assertEquals("IBM", result.orders.get(1).symbol);
        equalNumber("66.67", result.orders.get(1).signedShares, "Second order buys IBM");
    }
}
