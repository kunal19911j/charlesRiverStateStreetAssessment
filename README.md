# Portfolio Rebalancing Test Automation Framework

---

## Architecture Overview

```
                      +-----------------------------------+
                      |    PortfolioRebalancingTest       |
                      |   (JUnit 5 Acceptance Suite)      |
                      +-----------------+-----------------+
                                        | calls
                                        v
                      +-----------------------------------+
                      |       RebalancingAdapter          |
                      |   (Java Interface / Contract)     |
                      +--------+-----------------+--------+
                               |                 |
             (default local)   |                 | (via -Drebalancing.adapter)
                               v                 v
+-----------------------------------+   +-----------------------------------+
|  MockitoRebalancingAdapterFactory |   | Real Application Adapter          |
|  (Dynamic Mockito Stub & Engine)  |   | (REST API / OMS / UI Integration) |
+-----------------------------------+   +-----------------------------------+
```

- **Contract-First Design:** The test suite knows only about the [`RebalancingAdapter`](src/test/java/assessment/RebalancingAdapter.java) interface. It can validate any implementation—an in-memory service, a REST microservice, or an institutional Order Management System (OMS) entry point.
- **Out-of-the-Box Execution:** By default, tests run against [`MockitoRebalancingAdapterFactory`](src/test/java/assessment/MockitoRebalancingAdapterFactory.java), allowing immediate zero-configuration verification of all 34 automated scenarios.
- **Pluggable Integration:** Passing `-Drebalancing.adapter=<class_name>` redirects the suite to validate real external systems or staging services.

---

## Test Suites & Coverage

The suite implements 34 automated tests across 26 documented scenarios mapped 1-to-1 to [`portfolioRebalancingManualTestCases.md`](portfolioRebalancingManualTestCases.md):

| Suite / Tag | Scope & Description | Included Test Scenarios |
| :--- | :--- | :--- |
| **`smokeTest`** (`@Tag("high")`) | Critical financial integrity, baseline assessment calculations, $100\%$ allocation guardrails, division-by-zero protection, partial vesting, and cash overdraft checks. Blocks deployment if failed. | `TC_POS_001` - `TC_POS_005`, `TC_NEG_001` - `TC_NEG_004`, `TC_DOM_001`, `TC_DOM_002` |
| **`regressionTest`** (`@Tag("medium")`, `@Tag("low")`) | Secondary workflows (new positions, liquidation, single-stock allocation), decimal percentage precision, negative value checks, trade sequencing, and extreme boundary tests ($10B portfolios, penny stocks, ultra-high share prices). | `TC_POS_006`, `TC_POS_007`, `TC_BND_001` - `TC_BND_006`, `TC_NEG_005` - `TC_NEG_010`, `TC_DOM_003` |
| **`test`** | Full regression run executing all 34 test cases. | All test cases |

---

## Quick Start & Execution

### Prerequisites
- **JDK 11+** installed (e.g., JDK 11 or JDK 17). Ensure `JAVA_HOME` is configured.
- No Gradle installation is needed; the project includes the Gradle Wrapper (`./gradlew` / `gradlew.bat`).

### Running Tests from Terminal

```powershell
# Run the entire test suite (all 34 tests with default Mockito mock):
.\gradlew.bat test

# Run high-priority Smoke Suite only (13 tests):
.\gradlew.bat smokeTest

# Run Regression Suite only (Medium and Low priority boundary/edge cases):
.\gradlew.bat regressionTest

# Run tests against a custom or real application adapter:
.\gradlew.bat test -Drebalancing.adapter=com.example.ActualApplicationAdapter

# Check test compilation without executing:
.\gradlew.bat testClasses
```

*(On macOS/Linux, substitute `./gradlew` for `.\gradlew.bat`)*

### Test Reports
Gradle generates interactive HTML reports after each run:
- **Full Suite Report:** `build/reports/tests/test/index.html`
- **Smoke Suite Report:** `build/reports/tests/smokeTest/index.html`

Open reports directly in PowerShell:
```powershell
Start-Process .\build\reports\tests\test\index.html
```

---

## Connecting a Real Application

To point this regression harness at a real application or microservice:

1. **Implement the Adapter:** Create a class implementing [`RebalancingAdapter`](src/test/java/assessment/RebalancingAdapter.java) with a public no-arg constructor:
   ```java
   package com.company.integration;

   import assessment.RebalancingAdapter;

   public class CustomRestRebalancingAdapter implements RebalancingAdapter {
       @Override
       public Result rebalance(Request request) throws Exception {
           // 1. Serialize Request to JSON payload
           // 2. Call HTTP POST /api/v1/portfolios/rebalance
           // 3. Map HTTP response to RebalancingAdapter.Result
           return new Result(status, message, positions, orders, warnings);
       }
   }
   ```
2. **Execute Against the Target System:**
   ```powershell
   .\gradlew.bat test -Drebalancing.adapter=com.company.integration.CustomRestRebalancingAdapter
   ```

### Contract Rules for Adapter Implementations:
- **Raw Input Preservation:** `Request` and `Holding` keep numeric inputs as `String` so invalid payloads (`null`, `""`, non-numeric) reach the target validation service rather than failing prematurely in Java client setup.
- **Return Non-Null Lists:** If no positions or orders are returned, return empty collections (`Collections.emptyList()`), never `null`.
- **Order Queue Sequence:** Return `orders` in the exact execution queue order emitted by the engine; do not re-sort orders in the adapter.
- **Result Statuses:** Use `Status.VALIDATION_ERROR` for rejected inputs, `Status.NO_ASSETS` for zero-asset portfolios, and `Status.SUCCESS` for valid calculations.

---

## Engineering Assumptions & Domain Specifications

1. **Exact Number Arithmetic:** All assertions use `BigDecimal.compareTo() == 0`. Comparisons evaluate exact mathematical values while ignoring display scale differences (`0` matches `0.00`; `66` matches `66.0`).
2. **Rounding Modes:**
   - **Fractional Mode:** Rounds to 2 decimal places using standard commercial `RoundingMode.HALF_UP` (e.g. IBM $+66.6666... \rightarrow +66.67$, ORCL $-45.4545... \rightarrow -45.45$).
   - **Whole Lot Mode:** Truncates magnitude toward zero using `RoundingMode.DOWN` (not mathematical floor of negative numbers, avoiding accidental overselling).
3. **Execution Sequencing (Sells Before Buys):** In cash-neutral rebalancing with zero available cash, sell orders must be queued before buy orders (`TC_DOM_003`) so sell proceeds settle to fund subsequent buys and prevent broker overdraft rejections.
4. **Self-Funding / Cash Safety:** In whole share mode (`TC_DOM_002`), recommendations must never cause a cash overdraft (`remainingCash >= 0`).

---

## Open Questions & Production Requirements Clarifications

The following business requirements should be aligned with Product Management before deploying to a live Order Management System (OMS):

1. **Fractional Share Funding & Rounding Discrepancy Policy:**
   - **Context:** Standard `HALF_UP` rounding to 2 decimal places can create penny-level allocation divergence. In the baseline portfolio ($100,000 assets, $0 cash), selling ORCL yields **$9,999.00** ($45.45 \times \$220$), whereas buying IBM costs **$10,000.50** ($66.67 \times \$150$). This creates a **$1.50 cash shortfall** if no cash cushion exists. Similarly, other portfolios can yield rounded values totaling $9,999 or $10,001.
   - **Recommended Product Decision:**
     - *Option A (Target-First / Standard HALF_UP):* Prioritize target percentage tracking (IBM = $66.67$), assuming accounts maintain a cash buffer/sweep vehicle to absorb rounding friction.
     - *Option B (Cash-First / Buy Curtailment):* Strictly enforce cash self-funding by truncating the final buy order (e.g. buying $66.66$ IBM = $9,999.00$ to prevent overdrafts).
     - *Option C (Residual Cash Sweep):* Sweep any rounding surplus or deficit into a designated sweep money-market holding.
