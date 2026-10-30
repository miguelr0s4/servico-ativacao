package com.ativacao.dominio;

import com.ativacao.excecao.TransicaoDeStatusInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "solicitacao")
public class Solicitacao {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "criado_por_sistema", nullable = false)
    private String criadoPorSistema;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSolicitacao status;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Solicitacao() {
    }

    private Solicitacao(UUID id, String externalId, String customerId, String productCode,
                         String criadoPorSistema, Instant agora) {
        this.id = id;
        this.externalId = externalId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.criadoPorSistema = criadoPorSistema;
        this.status = StatusSolicitacao.PENDING;
        this.tentativas = 0;
        this.createdAt = agora;
        this.updatedAt = agora;
    }

    public static Solicitacao nova(String externalId, String customerId, String productCode,
                                    String criadoPorSistema, Instant agora) {
        return new Solicitacao(UUID.randomUUID(), externalId, customerId, productCode, criadoPorSistema, agora);
    }

    public void iniciarProcessamento(Instant agora) {
        exigirTransicaoValida(StatusSolicitacao.PROCESSING);
        this.status = StatusSolicitacao.PROCESSING;
        this.tentativas++;
        this.proximaTentativaEm = null;
        this.updatedAt = agora;
    }

    public void concluir(Instant agora) {
        exigirTransicaoValida(StatusSolicitacao.COMPLETED);
        this.status = StatusSolicitacao.COMPLETED;
        this.updatedAt = agora;
    }

    public void registrarFalhaTemporaria(Instant proximaTentativaEm, Instant agora) {
        exigirTransicaoValida(StatusSolicitacao.PROCESSING);
        this.proximaTentativaEm = proximaTentativaEm;
        this.updatedAt = agora;
    }

    public void falharDefinitivamente(Instant agora) {
        exigirTransicaoValida(StatusSolicitacao.FAILED);
        this.status = StatusSolicitacao.FAILED;
        this.proximaTentativaEm = null;
        this.updatedAt = agora;
    }

    private void exigirTransicaoValida(StatusSolicitacao novo) {
        if (!status.podeTransicionarPara(novo)) {
            throw new TransicaoDeStatusInvalidaException(
                    "Nao e possivel transicionar de %s para %s".formatted(status, novo));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getCriadoPorSistema() {
        return criadoPorSistema;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Instant getProximaTentativaEm() {
        return proximaTentativaEm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Solicitacao outra)) return false;
        return Objects.equals(id, outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
