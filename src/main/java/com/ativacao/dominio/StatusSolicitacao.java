package com.ativacao.dominio;

public enum StatusSolicitacao {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean podeTransicionarPara(StatusSolicitacao novo) {
        return switch (this) {
            case PENDING -> novo == PROCESSING;
            case PROCESSING -> novo == PROCESSING || novo == COMPLETED || novo == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
