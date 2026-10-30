-- usado pelo GET /api/v1/requests?status=&customerId= (listagem paginada).
-- customer_id primeiro: mais seletivo que status (que tem só 4 valores
-- possiveis), entao filtra a maior parte dos candidatos antes de aplicar
-- o filtro de status.
CREATE INDEX idx_solicitacao_customer_id_status
    ON solicitacao (customer_id, status);
