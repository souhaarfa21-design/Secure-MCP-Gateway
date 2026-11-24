DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gateway_readonly') THEN
            CREATE ROLE gateway_readonly LOGIN PASSWORD '${readonly_password}';
        END IF;
    END
$$;

GRANT CONNECT ON DATABASE gateway TO gateway_readonly;
GRANT USAGE ON SCHEMA public TO gateway_readonly;


GRANT SELECT ON customers, tickets, ticket_replies TO gateway_readonly;

ALTER ROLE gateway_readonly SET default_transaction_read_only = on;