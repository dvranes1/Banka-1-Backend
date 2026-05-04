-- liquibase formatted sql

-- changeset client-service:11
-- comment: Promote a few seed clients to CLIENT_TRADING (with MARGIN_TRADE permission) so that
-- end-to-end exchange testing for GHI #199 has obvious BUY-capable clients beyond Mateja Subin.
-- Without this, the only seeded trading-capable client is id=9 (subin.mateja@gmail.com), which is
-- not obvious to a tester following the project README and leads to confusing 403s on POST
-- /orders/buy when logging in as the more "default" Marko/Ana clients. The change is seed-only;
-- production deploys can reuse the migration, but the data is fixture-style.

UPDATE clients SET role = 'CLIENT_TRADING' WHERE email IN (
    'marko.markovic@banka.com',
    'ana.anic@banka.com'
);

INSERT INTO client_permissions (client_id, permission)
SELECT c.id, 'MARGIN_TRADE'
FROM clients c
WHERE c.email IN ('marko.markovic@banka.com', 'ana.anic@banka.com')
ON CONFLICT DO NOTHING;
