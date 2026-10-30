CREATE TABLE solicitacao (
    id                    UUID NOT NULL,
    external_id           VARCHAR(100) NOT NULL,
    customer_id           VARCHAR(100) NOT NULL,
    product_code          VARCHAR(100) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    tentativas            INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa_em  TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_solicitacao PRIMARY KEY (id),
    CONSTRAINT uk_solicitacao_external_id UNIQUE (external_id)
);

-- usado pelo worker para buscar o proximo lote a processar
-- (status pendente, ou em processamento com retry ja liberado)
CREATE INDEX idx_solicitacao_status_proxima_tentativa
    ON solicitacao (status, proxima_tentativa_em);

-- usado pelo GET /api/v1/requests?customerId=
CREATE INDEX idx_solicitacao_customer_id
    ON solicitacao (customer_id);
