# Module 02B float reconciliation

## Read-only daily QA summary

The current endpoint `GET /api/v1/admin/float-reconciliation/daily?date=YYYY-MM-DD` aggregates explicitly synthetic settled statement rows and simulated journals for the requested date. It returns statement/posting counts and amounts, debit and credit totals, unmatched settled statement count, verified-but-unposted requests, non-matched verification attempts, statement-to-posting difference, and a balanced summary flag. It does not change balances, create adjustments, or delete history.

The current QA view is date-level and totals-only; it does not yet provide per-bank-account drilldown or itemized discrepancy rows for duplicate references, posting-without-verification, and float snapshot/current-balance differences. Those are production finance-control gaps and must be addressed before calling this a full operational reconciliation tool.

## Production boundary

Production reconciliation is unavailable until trusted bank statement ingestion is approved and implemented. QA reconciliation reads only synthetic rows and labels the result as simulated. Synthetic rows validate matching and balanced journal mechanics; they do not prove bank settlement or platform funds and must be excluded from real bank reconciliation. No bank or payment gateway is called by Module 02B tests.
