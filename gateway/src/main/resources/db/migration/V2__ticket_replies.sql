-- V2: replies the agent writes to tickets (through the reply_ticket tool).
-- A reply is sent back to the customer who opened the ticket, so it is a way for data
-- to LEAVE the company: the "exfiltration" leg of the lethal trifecta.
-- Week 2 (Person B) puts an approval queue in front of it.

CREATE TABLE ticket_replies (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_replies_ticket ON ticket_replies (ticket_id);
