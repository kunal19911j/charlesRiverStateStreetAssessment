# Technical Assessment

---

## Problem Statement

**Account ABC** holds these securities: `IBM`, `MSFT`, `ORCL`, `AAPL`, `HD`

* **Total Asset:** $100,000 (100% is vested)
* **target%:** Indicates what % of total assets you should hold in your account.
* **current%:** Shows what % of total assets you currently have in your account.
* **target Variance:** Shows how much deviation you have:
  * `(-ve)` variance means you need to **buy**
  * `(+ve)` variance means you need to **sell**

### Portfolio Breakdown

| Security | target% | current% | target Variance | unit price | Output - Number of shares to buy/sell |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **IBM** | 20 | 10 | -10 | 150 | |
| **MSFT** | 20 | 20 | 0 | 90 | |
| **ORCL** | 20 | 30 | 10 | 220 | |
| **AAPL** | 20 | 20 | 0 | 450 | |
| **HD** | 20 | 20 | 0 | 70 | |

> **Question 1:** What do you have to do to get to zero target variance?

---

## Testing Assignment

Assume that this rebalancing problem is your team's application that you are going to test. Please write your **manual test cases** and **automated test cases** to test the application.

1. You can use any language/testing tool to code your automated test cases for this scenario and make any reasonable assumptions.
2. The tests should validate that the total number of shares to buy and sell for each security is correct, as this is the application's output.

