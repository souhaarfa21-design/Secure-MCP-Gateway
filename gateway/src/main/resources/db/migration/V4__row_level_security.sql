ALTER TABLE customers ADD COLUMN IF NOT EXISTS assigned_to TEXT;

-- Demo assignment: replace the usernames with the ones your tokens use
UPDATE customers SET assigned_to = CASE WHEN id % 2 = 0 THEN 'alice' ELSE 'bob' END
WHERE assigned_to IS NULL;

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets   ENABLE ROW LEVEL SECURITY;

CREATE POLICY customers_by_user ON customers
    FOR SELECT TO gateway_readonly
    USING (assigned_to = current_setting('app.user', true));

CREATE POLICY tickets_by_user ON tickets
    FOR SELECT TO gateway_readonly
    USING (customer_id IN (SELECT id FROM customers));