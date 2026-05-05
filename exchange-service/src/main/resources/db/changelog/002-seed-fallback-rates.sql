-- liquibase formatted sql

-- changeset exchange-service:2
-- comment: Bootstrap a daily snapshot of approximate FX rates so the service can serve
-- /exchange/rates and currency-conversion calls when the upstream provider (TwelveData)
-- key is missing or rate-limited. The scheduled refresh + manual /rates/fetch endpoint
-- still overwrite these values once a real upstream is reachable. Without this seed the
-- service cold-starts to an empty table, /rates returns 404, and order-service cross-
-- currency BUY/SELL paths blow up with no rate available -- which then made the GHI #199
-- end-to-end manual test impossible (issue surfaced while validating the trade-leg fix).
-- Rates are anchored to 2026-05-04 (project current date) and use mid-market values that
-- are realistic enough for fixture trading; replace with live data via /rates/fetch.

INSERT INTO exchange_rate (currency_code, buying_rate, selling_rate, rate_date)
SELECT seed.currency_code, seed.buying_rate, seed.selling_rate, seed.rate_date
FROM (
    SELECT 'EUR' AS currency_code, 117.00000000 AS buying_rate, 117.50000000 AS selling_rate, DATE '2026-05-04' AS rate_date
    UNION ALL
    SELECT 'CHF', 119.00000000, 119.50000000, DATE '2026-05-04'
    UNION ALL
    SELECT 'USD', 108.00000000, 108.50000000, DATE '2026-05-04'
    UNION ALL
    SELECT 'GBP', 137.00000000, 137.50000000, DATE '2026-05-04'
    UNION ALL
    SELECT 'JPY', 0.69000000, 0.70000000, DATE '2026-05-04'
    UNION ALL
    SELECT 'CAD', 79.00000000, 79.50000000, DATE '2026-05-04'
    UNION ALL
    SELECT 'AUD', 72.00000000, 72.50000000, DATE '2026-05-04'
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM exchange_rate existing
    WHERE existing.currency_code = seed.currency_code
      AND existing.rate_date = seed.rate_date
);
