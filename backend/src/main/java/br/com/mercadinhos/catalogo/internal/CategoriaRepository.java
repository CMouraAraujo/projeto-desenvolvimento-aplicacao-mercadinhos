package br.com.mercadinhos.catalogo.internal;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query("select c from Categoria c where c.organizacaoId = :organizacaoId "
            + "and (:ativa is null or c.ativa = :ativa) "
            + "and (:busca is null or c.nome like :busca escape '!')")
    Page<Categoria> listar(@Param("organizacaoId") Long organizacaoId, @Param("ativa") Boolean ativa,
            @Param("busca") String busca, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Categoria c where c.id = :id and c.organizacaoId = :organizacaoId")
    Optional<Categoria> buscarParaAlterar(@Param("id") Long id, @Param("organizacaoId") Long organizacaoId);
}
