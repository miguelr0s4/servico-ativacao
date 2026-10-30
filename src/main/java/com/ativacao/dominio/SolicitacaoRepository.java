package com.ativacao.dominio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, UUID> {

    Optional<Solicitacao> findByExternalId(String externalId);

    long countByStatus(StatusSolicitacao status);

    @Query("""
            SELECT s.id FROM Solicitacao s
            WHERE s.status = com.ativacao.dominio.StatusSolicitacao.PENDING
               OR (s.status = com.ativacao.dominio.StatusSolicitacao.PROCESSING AND s.proximaTentativaEm <= :agora)
               OR (s.status = com.ativacao.dominio.StatusSolicitacao.PROCESSING AND s.proximaTentativaEm IS NULL
                   AND s.updatedAt <= :limiteReconciliacao)
            ORDER BY s.createdAt ASC
            """)
    List<UUID> buscarIdsElegiveisParaProcessar(@Param("agora") Instant agora,
                                                @Param("limiteReconciliacao") Instant limiteReconciliacao,
                                                Pageable pageable);

    @Query("""
            SELECT s FROM Solicitacao s
            WHERE s.criadoPorSistema = :criadoPorSistema
              AND (:status IS NULL OR s.status = :status)
              AND (:customerId IS NULL OR s.customerId = :customerId)
            """)
    Page<Solicitacao> buscarComFiltros(@Param("criadoPorSistema") String criadoPorSistema,
                                        @Param("status") StatusSolicitacao status,
                                        @Param("customerId") String customerId,
                                        Pageable pageable);
}
