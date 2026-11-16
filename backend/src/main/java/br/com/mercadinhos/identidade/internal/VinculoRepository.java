package br.com.mercadinhos.identidade.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface VinculoRepository extends JpaRepository<VinculoOrganizacao, Long> {
    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    Optional<VinculoOrganizacao> findByUsuarioEmail(String email);

    @EntityGraph(attributePaths = {"usuario", "organizacao"})
    Optional<VinculoOrganizacao> findByUsuarioId(Long usuarioId);
}
