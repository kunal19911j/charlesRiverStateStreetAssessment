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

    private static RebalancingAdapter.Result simulateRebalancing(RebalancingAdapter.Request request) {
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

        if (totalAsset.compareTo(BigDecimal.ZERO) < 0) {
            return validationError("Total Asset value cannot be negative.");
        }

        if (totalAsset.compareTo(BigDecimal.ZERO) == 0) {
            List<RebalancingAdapter.Position> zeroPositions = new ArrayList<>();
            for (RebalancingAdapter.Holding h : request.holdings) {
                try {
                    BigDecimal cur = new BigDecimal(h.currentPercent);
                    BigDecimal tgt = new BigDecimal(h.targetPercent);
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

        BigDecimal vestedAsset = totalAsset.multiply(vestedPercent).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        List<RebalancingAdapter.Position> positions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (RebalancingAdapter.Holding h : request.holdings) {
            BigDecimal current = new BigDecimal(h.currentPercent);
            BigDecimal target = new BigDecimal(h.targetPercent);
            BigDecimal price = new BigDecimal(h.unitPrice);
            BigDecimal variance = current.subtract(target);

            BigDecimal rebalanceAmount = variance.negate().multiply(vestedAsset).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal unroundedShares = rebalanceAmount.divide(price, 10, RoundingMode.HALF_UP);

            BigDecimal signedShares;
            if (request.mode == RebalancingAdapter.Mode.FRACTIONAL) {
                signedShares = unroundedShares.setScale(request.fractionalScale, RoundingMode.HALF_UP);
            } else {
                signedShares = unroundedShares.setScale(0, RoundingMode.DOWN);
                if (signedShares.signum() == 0 && unroundedShares.signum() != 0 && price.compareTo(totalAsset) > 0) {
                    warnings.add("Unreachable whole-share target for " + h.symbol);
                }
            }
            positions.add(new RebalancingAdapter.Position(h.symbol, variance, signedShares));
        }

        List<RebalancingAdapter.Order> orders = new ArrayList<>();
        for (RebalancingAdapter.Position p : positions) {
            if (p.signedShares.signum() < 0) {
                orders.add(new RebalancingAdapter.Order(p.symbol, p.signedShares));
            }
        }
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
