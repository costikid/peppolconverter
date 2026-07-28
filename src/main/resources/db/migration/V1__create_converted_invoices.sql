CREATE TABLE converted_invoices (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    invoice_number  VARCHAR(128),
    issue_date      DATE,
    due_date        DATE,
    currency        VARCHAR(8),
    total_amount    DECIMAL(14, 2),
    vat_amount      DECIMAL(14, 2),
    due_amount      DECIMAL(14, 2),
    seller_name     VARCHAR(256),
    buyer_name      VARCHAR(256),
    source          VARCHAR(16)  NOT NULL DEFAULT 'oauth',
    peppol_xml      TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_converted_invoices_user_id ON converted_invoices(user_id);
CREATE INDEX idx_converted_invoices_user_created ON converted_invoices(user_id, created_at DESC);
