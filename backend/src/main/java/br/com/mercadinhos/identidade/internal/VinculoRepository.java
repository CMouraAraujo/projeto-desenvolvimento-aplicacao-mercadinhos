package br.com.mercadinhos.identidade.internal;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface VinculoRepository extends JpaRepository<VinculoOrganizacao, Long> {
    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    Page<VinculoOrganizacao> findByOrganizacaoId(Long organizacaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    Optional<VinculoOrganizacao> findByUsuarioIdAndOrganizacaoId(Long usuarioId, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    @Query("select v from VinculoOrganizacao v where v.usuario.id = :usuarioId and v.organizacao.id = :organizacaoId")
    Optional<VinculoOrganizacao> buscarParaAlterar(@Param("usuarioId") Long usuarioId,
            @Param("organizacaoId") Long organizacaoId);
    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    Optional<VinculoOrganizacao> findByUsuarioEmail(String email);

    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    Optional<VinculoOrganizacao> findByUsuarioId(Long usuarioId);
}
