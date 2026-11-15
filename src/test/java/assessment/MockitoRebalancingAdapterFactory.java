package assessment;

import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Creates a Mockito-backed mock of RebalancingAdapter with behavior matching
 * the portfolio rebalancing requirements for local testing and demo execution.
 */
public final class MockitoRebalancingAdapterFactory {

    private MockitoRebalancingAdapterFactory() {
    }

    public static RebalancingAdapter createMock() {
        RebalancingAdapter mockAdapter = Mockito.mock(RebalancingAdapter.class);

        try {
            when(mockAdapter.rebalance(any(RebalancingAdapter.Request.class))).thenAnswer(invocation -> {
                RebalancingAdapter.Request request = invocation.getArgument(0);
                return simulateRebalancing(request);
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Mockito stubbing", e);
        }

        return mockAdapter;
    }

    /**
     * Simulates the portfolio rebalancing calculation engine and validation rules.
     * Each block corresponds to specific acceptance test cases from PortfolioRebalancingTest.
     */
    private static RebalancingAdapter.Result simulateRebalancing(RebalancingAdapter.Request request) {
        // =========================================================================
        // Null & Non-Numeric Portfolio Header Validation
        // Tests: TC_NEG_009 (null/malformed totalAsset or vestedPercent)
        // =========================================================================
        if (request.totalAsset == null || request.vestedPercent == null) {
            return validationError("Validation error: asset or vested percent is null");
        }

        BigDecimal totalAsset;
        BigDecimal vestedPercent;
        try {
            totalAsset = new BigDecimal(request.totalAsset);
            vestedPercent = new BigDecimal(request.vestedPercent);
        } catch (NumberFormatException e) {
            return validationError("Invalid numeric value: " + e.getMessage());
        }

        // =========================================================================
        // Total Asset Boundary Validation
        // Tests: TC_NEG_008 (Negative totalAsset rejected; $0 assets returns NO_ASSETS)
        //        TC_BND_004 (Handles large institutional portfolios up to $10 Billion)
        // =========================================================================
        if (totalAsset.compareTo(BigDecimal.ZERO) < 0) {
            return validationError("Total Asset value cannot be negative.");
        }

        if (totalAsset.compareTo(BigDecimal.ZERO) == 0) {
            List<RebalancingAdapter.Position> zeroPositions = new ArrayList<>();
            for (RebalancingAdapter.Holding h : request.holdings) {
                try {
                    BigDecimal cur = new BigDecimal(h.currentPercent);
                    BigDecimal tgt = new BigDecimal(h.targetPercent);
                    // Zero assets produce 0 shares recommended and 0 orders queued
                    zeroPositions.add(new RebalancingAdapter.Position(h.symbol, cur.subtract(tgt), BigDecimal.ZERO));
                } catch (Exception e) {
                    return validationError("Invalid holding numbers: " + e.getMessage());
                }
            }
            return new RebalancingAdapter.Result(
                    RebalancingAdapter.Status.NO_ASSETS,
                    "Zero assets available to rebalance",
                    zeroPositions,
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        // =========================================================================
        // Holding Format, Null Checks & Duplicate Security Ticker Check
        // Tests: TC_NEG_009 (null, non-numeric holding fields: price, current, target)
        //        TC_NEG_010 (Reject duplicate security tickers in same account, e.g. IBM)
        // =========================================================================
        Set<String> seenSymbols = new HashSet<>();
        for (RebalancingAdapter.Holding h : request.holdings) {
            if (h.symbol == null || h.targetPercent == null || h.currentPercent == null || h.unitPrice == null) {
                return validationError("Validation error: holding field is null");
            }
            if (!seenSymbols.add(h.symbol)) {
                return validationError("Duplicate security symbol '" + h.symbol + "' detected.");
            }
            try {
                new BigDecimal(h.targetPercent);
                new BigDecimal(h.currentPercent);
                new BigDecimal(h.unitPrice);
            } catch (NumberFormatException e) {
                return validationError("Invalid numeric value: " + e.getMessage());
            }
        }

        // =========================================================================
        // Holding Business Rules (Unit Price & Range Checks)
        // Tests: TC_NEG_004 (Unit price == $0: Division by zero protection)
        //        TC_NEG_005 (Unit price < $0: Negative price rejection)
        //        TC_NEG_006 (Target% < 0% or > 100%: Long-only allocation constraint)
        //        TC_NEG_007 (Current% < 0%: Short positions not supported in long-only)
        //        TC_BND_005 (Penny stock support with micro price $0.01)
        // =========================================================================
        for (RebalancingAdapter.Holding h : request.holdings) {
            BigDecimal price = new BigDecimal(h.unitPrice);
            if (price.compareTo(BigDecimal.ZERO) == 0) {
                return validationError("Unit price for " + h.symbol + " must be greater than zero.");
            }
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                return validationError("Invalid unit price: Price cannot be negative.");
            }
            BigDecimal target = new BigDecimal(h.targetPercent);
            if (target.compareTo(BigDecimal.ZERO) < 0 || target.compareTo(new BigDecimal("100")) > 0) {
                return validationError("Target percentage for " + h.symbol + " must be between 0% and 100%.");
            }
            BigDecimal current = new BigDecimal(h.currentPercent);
            if (current.compareTo(BigDecimal.ZERO) < 0) {
                return validationError("Current percentage for " + h.symbol + " cannot be negative (Short positions not supported in long-only account).");
            }
        }

        // =========================================================================
        // Portfolio Allocation 100% Guardrails
        // Tests: TC_NEG_001 (Sum of target% < 100%)
        //        TC_NEG_002 (Sum of target% > 100%)
        //        TC_NEG_003 (Sum of current% != 100%)
        //        TC_BND_002 (100% single-stock allocation summing to 100%)
        //        TC_BND_003 (Multi-decimal allocations, e.g. 20.55% + 19.45% summing to 100%)
        // =========================================================================
        BigDecimal sumTarget = BigDecimal.ZERO;
        BigDecimal sumCurrent = BigDecimal.ZERO;
        for (RebalancingAdapter.Holding h : request.holdings) {
            sumTarget = sumTarget.add(new BigDecimal(h.targetPercent));
            sumCurrent = sumCurrent.add(new BigDecimal(h.currentPercent));
        }

        if (sumTarget.compareTo(new BigDecimal("100")) < 0) {
            return validationError("Total Target Percentage must equal 100% (Current sum: " + sumTarget.stripTrailingZeros().toPlainString() + "%).");
        }
        if (sumTarget.compareTo(new BigDecimal("100")) > 0) {
            return validationError("Total Target Percentage cannot exceed 100% (Current sum: " + sumTarget.stripTrailingZeros().toPlainString() + "%).");
        }
        if (sumCurrent.compareTo(new BigDecimal("100")) != 0) {
            return validationError("Current portfolio allocation percentages must sum to 100%.");
        }

        // =========================================================================
        // Vested Capital Calculation
        // Tests: TC_DOM_001 (Partial vesting: Rebalance applies strictly to vested portion,
        //                   e.g., 50% vested of $100,000 = $50,000)
        // =========================================================================
        BigDecimal vestedAsset = totalAsset.multiply(vestedPercent).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        List<RebalancingAdapter.Position> positions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // =========================================================================
        // Share & Variance Rebalancing Calculation per Holding
        // Tests: TC_POS_001 (Baseline fractional shares: IBM +66.67, ORCL -45.45)
        //        TC_POS_002 (Baseline whole shares truncation: IBM +66, ORCL -45)
        //        TC_POS_003 (Exact integer division: 100 shares without rounding)
        //        TC_POS_004 (Balanced portfolio: 0 variance produces 0 shares)
        //        TC_POS_005 (All positions change; net buy cost equals sell proceeds)
        //        TC_POS_006 (Adding new security: current 0% -> target 10%)
        //        TC_POS_007 (Full liquidation: target 0% -> sell all shares)
        //        TC_BND_001 (Sub-share variance: deviation < 1 share unit price)
        //        TC_BND_006 (High unit price exceeding total assets, e.g. $600,000 stock)
        //        TC_DOM_002 (Whole share mode cash self-funding without overdraft)
        // =========================================================================
        for (RebalancingAdapter.Holding h : request.holdings) {
            BigDecimal current = new BigDecimal(h.currentPercent);
            BigDecimal target = new BigDecimal(h.targetPercent);
            BigDecimal price = new BigDecimal(h.unitPrice);

            // Variance = Current% - Target%
            // Negative variance = Underweight (need to BUY)
            // Positive variance = Overweight (need to SELL)
            BigDecimal variance = current.subtract(target);

            // Rebalance Dollar Amount = -Variance% / 100 * Vested Asset
            BigDecimal rebalanceAmount = variance.negate().multiply(vestedAsset).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);

            // Unrounded Shares = Rebalance Amount / Unit Price
            BigDecimal unroundedShares = rebalanceAmount.divide(price, 10, RoundingMode.HALF_UP);

            BigDecimal signedShares;
            if (request.mode == RebalancingAdapter.Mode.FRACTIONAL) {
                // TC_POS_001 / TC_BND_006: Round to fractionalScale (default 2, or 4 for TC_BND_006)
                signedShares = unroundedShares.setScale(request.fractionalScale, RoundingMode.HALF_UP);
            } else {
                // TC_POS_002 / TC_DOM_002: Truncate toward zero in whole share mode
                signedShares = unroundedShares.setScale(0, RoundingMode.DOWN);

                // TC_BND_006: Issue warning when stock price exceeds portfolio assets in whole share mode
                if (signedShares.signum() == 0 && unroundedShares.signum() != 0 && price.compareTo(totalAsset) > 0) {
                    warnings.add("Unreachable whole-share target for " + h.symbol);
                }
            }
            positions.add(new RebalancingAdapter.Position(h.symbol, variance, signedShares));
        }

        // =========================================================================
        // Execution Order Queue Sequencing (Sells before Buys)
        // Tests: TC_DOM_003 (Sells queued before buys to fund subsequent purchases
        //                   and avoid settlement/cash shortfall rejections)
        //        TC_POS_004 (Empty order list when portfolio is in balance)
        // =========================================================================
        List<RebalancingAdapter.Order> orders = new ArrayList<>();

        // Phase 8a: Queue all SELL orders first (negative shares)
        for (RebalancingAdapter.Position p : positions) {
            if (p.signedShares.signum() < 0) {
                orders.add(new RebalancingAdapter.Order(p.symbol, p.signedShares));
            }
        }

        // Phase 8b: Queue all BUY orders second (positive shares)
        for (RebalancingAdapter.Position p : positions) {
            if (p.signedShares.signum() > 0) {
                orders.add(new RebalancingAdapter.Order(p.symbol, p.signedShares));
            }
        }

        return new RebalancingAdapter.Result(
                RebalancingAdapter.Status.SUCCESS,
                "Success",
                positions,
                orders,
                warnings
        );
    }

    private static RebalancingAdapter.Result validationError(String message) {
        return new RebalancingAdapter.Result(
                RebalancingAdapter.Status.VALIDATION_ERROR,
                message,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }
}
