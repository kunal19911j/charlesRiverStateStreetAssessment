# Portfolio Rebalancing Application — Manual Test Cases

## 1. Problem Analysis & Answers to Assessment Questions

### Question: What do you have to do to get to zero target variance?

To bring the portfolio to a $0\%$ target variance across all securities, the application must calculate the dollar amount deviation for each security and convert that value into the corresponding number of shares to **Buy** (when `target Variance` $< 0$) or **Sell** (when `target Variance` $> 0$).

#### Core Formulas

1. **Target Variance ($\%$):**

   $$
   \text{Target Variance} = \text{Current\%} - \text{Target\%}
   $$

2. **Dollar Amount to Rebalance:**

   $$
   \text{Rebalance Amount} = \frac{-\text{Target Variance}}{100} \times \text{Total Vested Asset}
   $$

3. **Number of Shares to Buy / Sell:**

   $$
   \text{Shares} = \frac{\text{Rebalance Amount}}{\text{Unit Price}} = \frac{-\text{Target Variance} \times \text{Total Vested Asset}}{100 \times \text{Unit Price}}
   $$

   *(Note: A positive share output $+N$ indicates **BUY**, a negative share output $-N$ indicates **SELL**, and $0$ indicates **HOLD / NO ACTION**.)*

### Completed Output Table for Account-ABC (Total Asset: $\$100,000$, $100\%$ Vested)

| Security | target% | current% | target Variance | unit price | Dollar Variance | Number of shares to buy/sell (Fractional) | Number of shares to buy/sell (Whole Shares / Round Down) | 
| ----- | ----- | ----- | ----- | ----- | ----- | ----- | ----- | 
| **IBM** | $20$ | $10$ | $-10$ | $\$150$ | $-\$10,000$ | **Buy** $66.67$ ($+66.67$) | **Buy** $66$ ($+66$) | 
| **MSFT** | $20$ | $20$ | $0$ | $\$90$ | $\$0$ | **Hold** $0$ ($0$) | **Hold** $0$ ($0$) | 
| **ORCL** | $20$ | $30$ | $10$ | $\$220$ | $+\$10,000$ | **Sell** $45.45$ ($-45.45$) | **Sell** $45$ ($-45$) | 
| **AAPL** | $20$ | $20$ | $0$ | $\$450$ | $\$0$ | **Hold** $0$ ($0$) | **Hold** $0$ ($0$) | 
| **HD** | $20$ | $20$ | $0$ | $\$70$ | $\$0$ | **Hold** $0$ ($0$) | **Hold** $0$ ($0$) | 

* **IBM Calculation:** $\text{Target Variance} = -10\%$. We need to **buy** $10\%$ of $\$100,000 = \$10,000$. At $\$150\text{/share}$, $\frac{\$10,000}{\$150} = +66.6667\text{ shares}$.

* **ORCL Calculation:** $\text{Target Variance} = +10\%$. We need to **sell** $10\%$ of $\$100,000 = \$10,000$. At $\$220\text{/share}$, $\frac{-\$10,000}{\$220} = -45.4545\text{ shares}$.

* **MSFT, AAPL, HD Calculation:** $\text{Target Variance} = 0\%$. No action is required ($0\text{ shares}$).

## 2. Testing Assumptions

Per the assessment instructions (*"If you make any assumptions, please note that"*), the following assumptions are established for testing the rebalancing engine:

1. **Dynamic vs. Baseline Market Prices (`unit price`):**

   * **Baseline Snapshot Prices:** The assessment table provides baseline unit prices of **IBM =** $\$150$, **MSFT =** $\$90$, **ORCL =** $\$220$, **AAPL =** $\$450$, and **HD =** $\$70$. Notice that dividing $\$10,000$ by $\$150$ ($66.666...$) and $\$220$ ($45.454...$) produces **repeating decimals**.

   * **Configurable / Mock Price Assumption ($\$100$ and $\$200$):** Because real-world market prices fluctuate and an application must accept dynamic price feeds, we assume `unit price` is an input parameter rather than a hardcoded constant. In specific test cases (such as **TC_POS_003**, **TC_POS_005**, **TC_POS_006**, and **TC_POS_007**), unit prices of $\$100$ and $\$200$ are assumed and used as mock test data so that the dollar variance divides evenly into **exact whole numbers**. This isolates testing the core division formula from floating-point rounding logic.

   * *(Alternative using strict Assessment prices: Even if unit prices are strictly locked to the Assessment values of $\$150$ and $\$220$, exact integer division can still be tested by adjusting variance percentages to $-15\%$ for IBM $\rightarrow \frac{\$15,000}{\$150} = 100\text{ shares}$ and $+22\%$ for ORCL $\rightarrow \frac{\$22,000}{\$220} = 100\text{ shares}$, as included in **TC_POS_003**).*

2. **Fractional vs. Whole Shares:**

   * **Primary Assumption (Fractional Shares Supported):** Modern wealth management platforms allow fractional share trading, rounded to $2$ decimal places (e.g., $+66.67$ and $-45.45$).

   * **Secondary Assumption (Whole Lot / Standard Institutional Mode):** If fractional shares are disabled in system configuration, the system must round **down** (`floor` of absolute value) or to the nearest integer while ensuring total **Buy** value does not exceed total **Sell** proceeds plus available cash. Both modes are covered in the test cases below.

3. **Sign Convention for Output:**

   * **Buy** orders are represented as positive values ($+N$ or `BUY N`).

   * **Sell** orders are represented as negative values ($-N$ or `SELL N`).

   * **No Trade** is represented as $0$.

4. **Portfolio Allocation Integrity:**

   * The sum of `target%` across all holdings in an account must equal $100\%$.

   * The sum of `current%` across all holdings (including cash, if applicable) must equal $100\%$.

5. **Vested Asset Calculation:**

   * Rebalancing calculations apply strictly to the **vested** portion of Total Assets ($\text{Vested Asset} = \text{Total Asset} \times \text{Vested\%}$). In the baseline scenario, $100\%$ of $\$100,000$ is vested.

6. **Transaction Costs & Taxes:**

   * Commissions, trading fees, bid-ask spreads, and capital gains taxes are assumed to be $\$0$ for the core calculation engine unless configured otherwise.

7. **Market Prices Validity:**

   * `unit price` must be strictly greater than $\$0$ ($\text{Unit Price} > 0$).

## 3. Test Priority Section (High, Medium, Low)

To optimize test execution during release cycles (Smoke, Sanity, and Full Regression testing), all manual test cases are categorized into **High**, **Medium**, and **Low** priority levels based on business impact, financial risk, and execution frequency:

| Priority Level | Definition & Risk Criteria | Execution Scope | Included Test Cases | 
| ----- | ----- | ----- | ----- | 
| **High** | **Critical Core Business Logic & Financial Integrity:** Validates the primary rebalancing formula, baseline Assessment requirements, $100\%$ allocation guardrails, division-by-zero protection, vested asset calculations, and cash-overdraft prevention. Any failure here blocks release. | Smoke Suite, Sanity Suite, & Every Regression Run | `TC_POS_001`, `TC_POS_002`, `TC_POS_003`, `TC_POS_004`, `TC_POS_005`, `TC_NEG_001`, `TC_NEG_002`, `TC_NEG_003`, `TC_NEG_004`, `TC_DOM_001`, `TC_DOM_002` | 
| **Medium** | **Standard Functional Variations & Common Boundaries:** Validates secondary portfolio workflows (adding new positions, full security liquidation, $100\%$ single-stock concentration), decimal percentage precision, negative input validation, and trade execution ordering. | Full Functional & Pre-Release Regression Suite | `TC_POS_006`, `TC_POS_007`, `TC_BND_001`, `TC_BND_002`, `TC_BND_003`, `TC_NEG_005`, `TC_NEG_006`, `TC_NEG_007`, `TC_NEG_008`, `TC_DOM_003` | 
| **Low** | **Extreme Edge Cases & UI/Data Format Validations:** Validates rare boundary conditions (multi-billion dollar institutional portfolios, $\$0.01$ penny stocks, unit prices exceeding total account value) and non-numeric/duplicate input sanitization. | Extended Regression & Edge-Case Hardening | `TC_BND_004`, `TC_BND_005`, `TC_BND_006`, `TC_NEG_009`, `TC_NEG_010` | 

## 4. Manual Test Cases

### 4.1 Positive / Functional Test Cases (Happy Path)

| Test Case ID | Priority | Test Scenario | Preconditions | Test Data / Input | Test Steps | Expected Result | 
| ----- | ----- | ----- | ----- | ----- | ----- | ----- | 
| **TC_POS_001** | **High** | Verify baseline rebalancing calculation for Account-ABC (Fractional Shares enabled) | System is configured to allow fractional shares (rounded to $2$ decimal places). Total Asset = $\$100,000$ ($100\%$ vested). | **Baseline Assessment Prices:**  **IBM:** Target=$20\%$, Current=$10\%$, Price=$\$150$  **MSFT:** Target=$20\%$, Current=$20\%$, Price=$\$90$  **ORCL:** Target=$20\%$, Current=$30\%$, Price=$\$220$  **AAPL:** Target=$20\%$, Current=$20\%$, Price=$\$450$  **HD:** Target=$20\%$, Current=$20\%$, Price=$\$70$ | 1\. Load Account-ABC portfolio.  2. Input target percentages and unit prices.  3. Trigger "Rebalance Portfolio" calculation. | System calculates target variance and outputs:  - **IBM:** Var = $-10$, Buy $66.67$ shares ($+66.67$)  - **MSFT:** Var = $0$, $0$ shares  - **ORCL:** Var = $+10$, Sell $45.45$ shares ($-45.45$)  - **AAPL:** Var = $0$, $0$ shares  - **HD:** Var = $0$, $0$ shares | 
| **TC_POS_002** | **High** | Verify baseline rebalancing calculation for Account-ABC (Whole Shares Only mode) | System is configured for whole shares only (no fractional shares; cash-safe rounding down). Total Asset = $\$100,000$. | Same baseline Assessment input data as **TC_POS_001**. | 1\. Set account preference to "Whole Shares Only".  2. Trigger "Rebalance Portfolio" calculation. | System outputs whole numbers of shares without creating a negative cash balance:  - **IBM:** Buy $66$ shares ($+66$, cost $\$9,900$)  - **MSFT:** $0$ shares  - **ORCL:** Sell $45$ shares ($-45$, proceeds $\$9,900$)  - **AAPL:** $0$ shares  - **HD:** $0$ shares | 
| **TC_POS_003** | **High** | Verify exact integer share calculation when dollar variance is evenly divisible by unit price (Isolates core math from rounding logic) | Total Asset = $\$100,000$ ($100\%$ vested). | **Option A (Assumed Mock Prices $\$100/\$200$):**  - **IBM:** Target=$20\%$, Current=$10\%$, Price=$\$100$  - **ORCL:** Target=$20\%$, Current=$30\%$, Price=$\$200$  **Option B (Baseline Assessment Prices $\$150/\$220$):**  - **IBM:** Target=$25\%$, Current=$10\%$ (Var=$-15\%$), Price=$\$150$  - **ORCL:** Target=$8\%$, Current=$30\%$ (Var=$+22\%$), Price=$\$220$  - **MSFT:** Target=$27\%$, Current=$20\%$ (Var=$-7\%$), Price=$\$100$ (or $\$70$ HD) | 1\. Input portfolio data where dollar variance is a clean multiple of unit price.  2. Run rebalancing calculation. | System outputs exact whole numbers with zero rounding required:  **Option A:**  - **IBM:** Buy $100$ shares ($+100$)  - **ORCL:** Sell $50$ shares ($-50$)  **Option B:**  - **IBM:** Buy $100$ shares ($\frac{\$15,000}{\$150}$)  - **ORCL:** Sell $100$ shares ($\frac{-\$22,000}{\$220}$) | 
| **TC_POS_004** | **High** | Verify no trades are generated when portfolio is already in perfect balance | Total Asset = $\$100,000$ ($100\%$ vested). | All $5$ securities have `target%` = $20\%$ and `current%` = $20\%$ (using baseline Assessment prices: $\$150, \$90, \$220, \$450, \$70$). | 1\. Load balanced portfolio.  2. Run rebalancing calculation. | `target Variance` is $0$ for all $5$ securities. Output is $0$ shares to buy/sell for every security. | 
| **TC_POS_005** | **High** | Verify multi-security rebalance where all $5$ securities have non-zero variance | Total Asset = $\$100,000$ ($100\%$ vested). | **Using Baseline Assessment Prices:**  **IBM:** Target=$25\%$, Current=$10\%$, Price=$\$150$ (Var=$-15\%$)  **MSFT:** Target=$11\%$, Current=$20\%$, Price=$\$90$ (Var=$+9\%$)  **ORCL:** Target=$8\%$, Current=$30\%$, Price=$\$220$ (Var=$+22\%$)  **AAPL:** Target=$29\%$, Current=$20\%$, Price=$\$450$ (Var=$-9\%$)  **HD:** Target=$27\%$, Current=$20\%$, Price=$\$70$ (Var=$-7\%$) | 1\. Input portfolio data.  2. Run rebalancing calculation. | \- **IBM:** Buy $100$ shares ($+\$15,000 / \$150$)  - **MSFT:** Sell $100$ shares ($-\$9,000 / \$90$)  - **ORCL:** Sell $100$ shares ($-\$22,000 / \$220$)  - **AAPL:** Buy $20$ shares ($+\$9,000 / \$450$)  - **HD:** Buy $100$ shares ($+\$7,000 / \$70$)  Total Buy ($\$31,000$) equals Total Sell ($\$31,000$). | 
| **TC_POS_006** | **Medium** | Verify adding a brand-new security to the portfolio (`current%` = $0\%$, `target%` > $0\%$) | Total Asset = $\$100,000$ ($100\%$ vested). | **IBM:** Target=$10\%$, Current=$0\%$, Price=$\$150$  **ORCL:** Target=$20\%$, Current=$30\%$, Price=$\$220$  **MSFT:** Target=$30\%$, Current=$30\%$, Price=$\$90$  **AAPL:** Target=$20\%$, Current=$20\%$, Price=$\$450$  **HD:** Target=$20\%$, Current=$20\%$, Price=$\$70$ | 1\. Set IBM with $0\%$ current holding and $10\%$ target.  2. Run rebalancing calculation. | \- **IBM:** `target Variance` = $-10\%$, Buy $66.67$ shares ($+66.67$).  - **ORCL:** `target Variance` = $+10\%$, Sell $45.45$ shares ($-45.45$). | 
| **TC_POS_007** | **Medium** | Verify full liquidation of an existing security (`current%` > $0\%$, `target%` = $0\%$) | Total Asset = $\$100,000$ ($100\%$ vested). | **ORCL:** Target=$0\%$, Current=$30\%$, Price=$\$220$  **IBM:** Target=$40\%$, Current=$10\%$, Price=$\$150$  **MSFT, AAPL, HD:** Target=$20\%$, Current=$20\%$ | 1\. Set ORCL `target%` to $0\%$.  2. Set IBM `target%` to $40\%$.  3. Run rebalancing calculation. | \- **ORCL:** `target Variance` = $+30\%$, Sell $136.36$ shares ($\frac{-\$30,000}{\$220}$, full liquidation).  - **IBM:** `target Variance` = $-30\%$, Buy $200$ shares ($\frac{+\$30,000}{\$150}$). | 

### 4.2 Boundary & Edge Case Test Cases

| Test Case ID | Priority | Test Scenario | Preconditions | Test Data / Input | Test Steps | Expected Result | 
| ----- | ----- | ----- | ----- | ----- | ----- | ----- | 
| **TC_BND_001** | **Medium** | Verify rebalancing when dollar variance is smaller than $1$ unit price of the security | Total Asset = $\$10,000$. | **AAPL:** Target=$20.1\%$, Current=$20.0\%$, Var=$-0.1\%$ ($\$10$ change), Price=$\$450$. | 1\. Input portfolio with tiny variance.  2. Run calculation in Fractional and Whole Share modes. | \- **Fractional Mode:** Buy $0.02$ shares ($\frac{\$10}{\$450} = 0.0222$).  - **Whole Share Mode:** $0$ shares (since $\$10 < \$450$, cannot afford $1$ full share). | 
| **TC_BND_002** | **Medium** | Verify rebalancing with a single security having $100\%$ target allocation | Total Asset = $\$100,000$. | **IBM:** Target=$100\%$, Current=$20\%$, Price=$\$150$.  All other $4$ securities: Target=$0\%$, Current=$20\%$, using baseline Assessment prices ($\$90, \$220, \$450, \$70$). | 1\. Set IBM target to $100\%$ and all others to $0\%$.  2. Run calculation. | \- **IBM:** Var = $-80\%$ ($+\$80,000$), Buy $533.33$ shares.  - **MSFT:** Sell $222.22$ shares.  - **ORCL:** Sell $90.91$ shares.  - **AAPL:** Sell $44.44$ shares.  - **HD:** Sell $285.71$ shares. | 
| **TC_BND_003** | **Medium** | Verify precision with decimal percentages in `target%` and `current%` | Total Asset = $\$100,000$. | **IBM:** Target=$20.55\%$, Current=$10.05\%$, Price=$\$150$ (Var=$-10.50\%$)  **ORCL:** Target=$19.45\%$, Current=$29.95\%$, Price=$\$220$ (Var=$+10.50\%$) | 1\. Input decimal percentages.  2. Run calculation. | System handles floating-point arithmetic without precision loss:  - **IBM:** Var = $-10.50\%$ ($+\$10,500$), Buy $70$ shares.  - **ORCL:** Var = $+10.50\%$ ($-\$10,500$), Sell $47.73$ shares. | 
| **TC_BND_004** | **Low** | Verify rebalancing with a high-value institutional portfolio (Large Number boundary) | Total Asset = $\$10,000,000,000$ ($\$10\text{ Billion}$). | Same percentages and unit prices as Account-ABC (**TC_POS_001**). | 1\. Set Total Asset to $\$10,000,000,000$.  2. Run calculation. | No integer overflow occurs:  - **IBM:** Buy $6,666,666.67$ shares.  - **ORCL:** Sell $4,545,454.55$ shares. | 
| **TC_BND_005** | **Low** | Verify rebalancing with a penny stock (very small decimal `unit price` assumption) | Total Asset = $\$100,000$. Assumed dynamic price feed allows penny stock pricing. | **IBM:** Target=$20\%$, Current=$10\%$, Assumed Price=$\$0.01$ | 1\. Set unit price to $\$0.01$.  2. Run calculation. | System accurately calculates:  - **IBM:** Buy $1,000,000$ shares ($\frac{\$10,000}{\$0.01}$). | 
| **TC_BND_006** | **Low** | Verify rebalancing when `unit price` is greater than `Total Asset` | Total Asset = $\$100,000$. Assumed high-value stock price. | **BRK.A:** Target=$20\%$, Current=$10\%$, Var=$-10\%$ ($\$10,000$), Assumed Price=$\$600,000$. | 1\. Input high-priced stock.  2. Run calculation. | \- **Fractional Mode:** Buy $0.0167$ shares ($\frac{\$10,000}{\$600,000}$).  - **Whole Share Mode:** $0$ shares (with warning that target allocation cannot be reached with whole shares). | 

### 4.3 Negative & Input Validation Test Cases

| Test Case ID | Priority | Test Scenario | Preconditions | Test Data / Input | Test Steps | Expected Result | 
| ----- | ----- | ----- | ----- | ----- | ----- | ----- | 
| **TC_NEG_001** | **High** | Verify validation error when sum of `target%` is less than $100\%$ | Total Asset = $\$100,000$. | **IBM:** $10\%$, **MSFT:** $20\%$, **ORCL:** $20\%$, **AAPL:** $20\%$, **HD:** $20\%$ (Sum = $90\%$). | 1\. Input target percentages summing to $90\%$.  2. Attempt to rebalance. | System blocks calculation and displays validation error: *"Total Target Percentage must equal 100% (Current sum: 90%)."* | 
| **TC_NEG_002** | **High** | Verify validation error when sum of `target%` exceeds $100\%$ | Total Asset = $\$100,000$. | **IBM:** $30\%$, **MSFT:** $20\%$, **ORCL:** $20\%$, **AAPL:** $20\%$, **HD:** $20\%$ (Sum = $110\%$). | 1\. Input target percentages summing to $110\%$.  2. Attempt to rebalance. | System blocks calculation and displays validation error: *"Total Target Percentage cannot exceed 100% (Current sum: 110%)."* | 
| **TC_NEG_003** | **High** | Verify validation error when sum of `current%` does not equal $100\%$ | Total Asset = $\$100,000$. | Sum of `current%` across securities equals $95\%$ (or $105\%$). | 1\. Load portfolio with invalid current percentage sum.  2. Attempt to rebalance. | System rejects input and displays error: *"Current portfolio allocation percentages must sum to 100%."* | 
| **TC_NEG_004** | **High** | Verify handling of `unit price` = $\$0$ (Division by Zero protection) | Total Asset = $\$100,000$. | **IBM:** Target=$20\%$, Current=$10\%$, Price=$\$0$. | 1\. Set IBM unit price to $\$0$.  2. Attempt to rebalance. | System prevents division by zero, halts calculation, and throws validation error: *"Unit price for IBM must be greater than zero."* | 
| **TC_NEG_005** | **Medium** | Verify handling of negative `unit price` | Total Asset = $\$100,000$. | **IBM:** Target=$20\%$, Current=$10\%$, Price=$-\$150$. | 1\. Set IBM unit price to $-\$150$.  2. Attempt to rebalance. | System rejects negative price with error: *"Invalid unit price: Price cannot be negative."* | 
| **TC_NEG_006** | **Medium** | Verify handling of negative `target%` (Assuming long-only model allocation) | Total Asset = $\$100,000$. `current%` values are valid and sum to $100\%$. | **Target%:** **IBM:** $-10\%$, **MSFT:** $50\%$, **ORCL:** $20\%$, **AAPL:** $20\%$, **HD:** $20\%$ (Sum = $100\%$).  **Current%:** Valid baseline ($10\%, 20\%, 30\%, 20\%, 20\%$). | 1\. Enter negative `target%` for IBM ($-10\%$) while keeping `current%` valid.  2. Attempt to rebalance. | System rejects negative target weight with validation error: *"Target percentage for IBM must be between 0% and 100%."* | 
| **TC_NEG_007** | **Medium** | Verify handling of negative `current%` (Assuming long-only account with no short positions) | Total Asset = $\$100,000$. `target%` values are valid and sum to $100\%$. | **Current%:** **IBM:** $-10\%$, **MSFT:** $40\%$, **ORCL:** $30\%$, **AAPL:** $20\%$, **HD:** $20\%$ (Sum = $100\%$).  **Target%:** Valid baseline ($20\%$ for all $5$ securities). | 1\. Load portfolio containing a negative `current%` for IBM ($-10\%$) while keeping `target%` valid.  2. Attempt to rebalance. | System rejects negative current holding weight with validation error: *"Current percentage for IBM cannot be negative (Short positions not supported in long-only account)."* | 
| **TC_NEG_008** | **Medium** | Verify handling of zero or negative `Total Asset` value | Account-ABC loaded. | Total Asset = $\$0$ or $-\$100,000$. | 1\. Set Total Asset to $\$0$ or $-\$100,000$.  2. Attempt to rebalance. | If $\$0$: System informs user no assets are available to rebalance.  If negative: System throws error *"Total Asset value cannot be negative."* | 
| **TC_NEG_009** | **Low** | Verify handling of non-numeric / null / special characters in numeric fields | Account-ABC loaded. | `target%` = `"abc"`, `unit price` = `"$150#"` or `null`. | 1\. Input invalid strings or nulls.  2. Attempt to rebalance. | System blocks submission and prompts for valid numeric values. | 
| **TC_NEG_010** | **Low** | Verify handling of duplicate security tickers in the same account | Account-ABC loaded. | Two rows contain ticker **IBM** with conflicting `target%`. | 1\. Add duplicate ticker **IBM**.  2. Attempt to rebalance. | System either merges positions prior to calculation or throws validation error: *"Duplicate security symbol 'IBM' detected."* | 

### 4.4 Financial / Domain-Specific Test Cases (Portfolio Management)

| Test Case ID | Priority | Test Scenario | Preconditions | Test Data / Input | Test Steps | Expected Result | 
| ----- | ----- | ----- | ----- | ----- | ----- | ----- | 
| **TC_DOM_001** | **High** | Verify rebalancing when portfolio is only partially vested (e.g., $50\%$ vested) | Total Asset = $\$100,000$, but only $50\%$ **($\$50,000$) is vested** and eligible for trading. | Same baseline Assessment percentages and prices as Account-ABC (**TC_POS_001**). | 1\. Set Vested Ratio to $50\%$ ($\$50,000$).  2. Run rebalancing calculation. | System calculates shares based only on the $\$50,000$ vested amount:  - **IBM:** Buy $33.33$ shares ($\frac{\$5,000}{\$150}$)  - **ORCL:** Sell $22.73$ shares ($\frac{-\$5,000}{\$220}$) | 
| **TC_DOM_002** | **High** | Verify Cash neutrality / Self-funding constraint in Whole Share mode | Total Asset = $\$100,000$, Cash balance = $\$0$, Whole Share mode enabled, Nearest-Integer rounding attempted. | **IBM:** Var = $-10\%$ ($\$10,000$), Price = $\$150$ ($\frac{10000}{150} = 66.67 \rightarrow 67$ shares = $\$10,050$)  **ORCL:** Var = $+10\%$ ($\$10,000$), Price = $\$220$ ($\frac{10000}{220} = 45.45 \rightarrow 45$ shares = $\$9,900$) | 1\. Run rebalancing in Whole Share mode. | Selling $45$ ORCL generates $\$9,900$, which is insufficient to buy $67$ IBM ($\$10,050$).  - System must **not** recommend buying $67$ shares of IBM because it would cause a $-\$150$ cash overdraft.  - System caps IBM buy at $66$ shares ($\$9,900$) to match the $\$9,900$ sell proceeds of $45$ ORCL shares. | 
| **TC_DOM_003** | **Medium** | Verify order of execution generation (Sell orders generated/queued before Buy orders) | Account has $\$0$ spare cash; Buy orders depend on Sell proceeds. | Account-ABC baseline data. | 1\. Execute rebalance and inspect generated order list. | System flags **ORCL Sell ($45.45$ shares)** to execute prior to **IBM Buy ($66.67$ shares)** to prevent settlement/cash insufficiency rejections. | 