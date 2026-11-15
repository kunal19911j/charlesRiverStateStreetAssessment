# Portfolio rebalancing automation (Java)

JUnit 5 tests cover all 26 manual case IDs in
[portfolioRebalancingManualTestCases.md](portfolioRebalancingManualTestCases.md).
There is no production source code; test execution defaults to a Mockito-backed adapter
simulating the portfolio rebalancing contract, or can be pointed to an external application adapter.
Expected share quantities are explicit test data; `BigDecimal` comparisons ignore display scale
without tolerating numerical differences. Each successful scenario checks every holding's
signed shares and target variance. Test names retain the manual IDs, priorities are
JUnit tags (`high`, `medium`, `low`), and each test includes descriptive `@DisplayName` annotations.

## Connect the application

The assessment provides no API, UI, or Java entry point. Consequently these are contract
tests awaiting a real application connection. Implement
[`RebalancingAdapter`](src/test/java/assessment/RebalancingAdapter.java) in `src/test/java`
with a public no-argument constructor. Its `rebalance` method must submit the supplied
request to the actual application and map its observed response into `Result`.
Do not calculate expected results in the adapter. The adapter may use a Java call, HTTP,
or UI automation once the real interface is known; no endpoint or selectors are assumed here.
Request objects are fresh per invocation. Use an isolated test account and reset any state
in the adapter before each invocation if the application persists changes.

The adapter contract uses percentage points (50 means 50%), signed share quantities,
and `current - target` variance. It preserves raw numeric inputs so negative tests reach
the application. Return non-null lists, including empty lists. Rejected requests return
`VALIDATION_ERROR`, an observed user-facing message, and no positions/orders. Zero assets
return `NO_ASSETS`, an explanatory message, and no orders. Successful requests return one
position per holding, including zero-share positions. `orders` represents the actual
queue order; it must not be reordered by the adapter. Warnings come from the application.

## Run

Requires JDK 11+ and Gradle (Gradle Wrapper `./gradlew` is included).

```powershell
# 1. Run full test suite with default Mockito mock:
.\gradlew test

# 2. Run Smoke Test Suite (High priority tests only):
.\gradlew smokeTest

# 3. Run Regression Test Suite (Medium and Low priority edge cases):
.\gradlew regressionTest

# 4. Run against a specific external application adapter:
.\gradlew test -Drebalancing.adapter=com.example.ActualApplicationAdapter

# 5. Check test compilation only:
.\gradlew testClasses
```

Reports: `build/reports/tests/test/index.html` (or `build/reports/tests/smokeTest/index.html`).

## Explicit assumptions and manual-case clarifications

- Fractional mode uses two decimal places and HALF_UP rounding. Whole mode truncates
  share magnitude toward zero, subject to available funding (not mathematical floor
  of a negative number). No fees or taxes are included.
- TC_BND_006 explicitly requests four decimal places (`0.0167`), conflicting with the
  general two-decimal assumption. This test requests scale 4 for all holdings in that
  scenario. A real application must support this configuration or the requirement
  must be resolved before using this test as a release gate.
- Abbreviated manual inputs are completed with baseline holdings to keep both
  allocations at 100%. TC_BND_001 uses IBM target 19.9% to offset AAPL target 20.1%.
  TC_POS_003 tests both price options; option B sets MSFT target 27% and price 100.
- TC_NEG_010 selects the documented duplicate-rejection branch. It reuses the MSFT
  row as a conflicting IBM row and keeps allocation totals at 100%, isolating the error.
  A product that merges duplicates needs a separately agreed expected merged result.
- Documented validation messages are checked as substrings; unspecified messages
  must be non-empty. The adapter must preserve observed messages rather than invent them.
- Fractional rounding does not guarantee exact zero variance, exact liquidation, or
  self-funding. Baseline rounded buys cost 10,000.50 while sells yield 9,999.00.
  TC_DOM_003 checks queue sequence only; it does not claim this resolves the 1.50
  shortfall. Cash safety is asserted for whole-share mode in TC_DOM_002, matching the
  manual scope. Fractional funding policy remains an open product requirement.

## Open Questions & Requirements Clarifications for Product / Engineering

1. **Fractional Share Funding & Rounding Discrepancy Policy:**
   - **Context:** When rounding fractional shares to two decimal places using standard `HALF_UP` rounding, total rebalance dollar amounts can diverge from target allocations. In the baseline scenario, selling ORCL generates **$9,999.00** ($45.45 \times \$220$), but buying IBM requires **$10,000.50** ($66.67 \times \$150$), leaving a **$1.50 cash deficit** if no spare cash is available. Conversely, scenarios where buys round down and sells round up could leave unallocated residual cash (e.g. allocating $9,999 instead of $10,000).
   - **Clarification Needed:** If fractional share rebalancing is supported in production, how should the application handle rounded allocation totals that diverge from the target (e.g. total executed buy value equals **$10,001** or **$9,999** instead of **$10,000**)?
     - *Option A (Target-First / Standard HALF_UP):* Prioritize reaching target percentage weightings as closely as possible (as shown in the assessment table with IBM = 66.67), assuming an account cash buffer/sweep vehicle absorbs small penny-level rounding differences.
     - *Option B (Cash-First / Buy Curtailment):* Strictly enforce self-funding so recommendations never exceed sell proceeds plus available cash, truncating or curtailing the last buy order (e.g. buying 66.66 IBM = $9,999.00 to prevent cash overdrafts).
     - *Option C (Residual Cash Sweep):* Sweep any rounding surplus or deficit into a designated default cash holding.
