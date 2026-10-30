ALTER TABLE solicitacao
    ADD COLUMN criado_por_sistema VARCHAR(100) NOT NULL DEFAULT 'desconhecido';

-- toda consulta autenticada filtra por este campo primeiro (isolamento entre
-- consumidores); customer_id/status vem depois, dentro do escopo do sistema
CREATE INDEX idx_solicitacao_criado_por_sistema
    ON solicitacao (criado_por_sistema, status);
