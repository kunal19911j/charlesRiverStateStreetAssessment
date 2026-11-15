package assessment;

import java.math.BigDecimal;
import java.util.List;

/** Test-side boundary only. Implement this against the real application's API, UI, or Java entry point. */
public interface RebalancingAdapter {
    Result rebalance(Request request) throws Exception;

    enum Mode { FRACTIONAL, WHOLE }
    enum Status { SUCCESS, VALIDATION_ERROR, NO_ASSETS }

    final class Request {
        public String totalAsset = "100000";
        public String vestedPercent = "100";
        public String availableCash = "0";
        public Mode mode = Mode.FRACTIONAL;
        public int fractionalScale = 2;
        public List<Holding> holdings;

        public Request(List<Holding> holdings) { this.holdings = holdings; }
    }

    /** Strings intentionally retain malformed and null inputs for validation tests. */
    final class Holding {
        public String symbol, targetPercent, currentPercent, unitPrice;

        public Holding(String symbol, String target, String current, String price) {
            this.symbol = symbol;
            this.targetPercent = target;
            this.currentPercent = current;
            this.unitPrice = price;
        }
    }

    final class Position {
        public final String symbol;
        public final BigDecimal variance, signedShares;

        public Position(String symbol, BigDecimal variance, BigDecimal signedShares) {
            this.symbol = symbol;
            this.variance = variance;
            this.signedShares = signedShares;
        }
    }

    /** Orders are returned in the actual queue/execution sequence. Positive shares buy; negative sell. */
    final class Order {
        public final String symbol;
        public final BigDecimal signedShares;

        public Order(String symbol, BigDecimal signedShares) {
            this.symbol = symbol;
            this.signedShares = signedShares;
        }
    }

    final class Result {
        public final Status status;
        public final String message;
        public final List<Position> positions;
        public final List<Order> orders;
        public final List<String> warnings;

        public Result(Status status, String message, List<Position> positions,
                      List<Order> orders, List<String> warnings) {
            this.status = status;
            this.message = message;
            this.positions = positions;
            this.orders = orders;
            this.warnings = warnings;
        }
    }
}
