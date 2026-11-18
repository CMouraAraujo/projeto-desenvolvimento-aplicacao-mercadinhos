package br.com.mercadinhos.catalogo.internal;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    Optional<Produto> findByCodigoBarrasAndOrganizacaoId(String codigoBarras, Long organizacaoId);

    @Query("select p from Produto p where p.organizacaoId = :org "
            + "and (:ativo is null or p.ativo = :ativo) and (:categoria is null or p.categoriaId = :categoria) "
            + "and (:comImagem is null or (:comImagem = true and p.imagemChave is not null) "
            + "or (:comImagem = false and p.imagemChave is null)) "
            + "and (:busca is null or p.nome like :busca escape '!' or p.codigoBarras like :busca escape '!')")
    Page<Produto> listar(@Param("org") Long org, @Param("ativo") Boolean ativo,
            @Param("categoria") Long categoria, @Param("busca") String busca, @Param("comImagem") Boolean comImagem, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id and p.organizacaoId = :org")
    Optional<Produto> buscarParaAlterar(@Param("id") Long id, @Param("org") Long org);
}
