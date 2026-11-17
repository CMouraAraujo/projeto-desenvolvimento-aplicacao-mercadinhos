package br.com.mercadinhos.catalogo.internal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ProdutoMapper {
    @Mapping(target = "imagemUrl", expression = "java(produto.getImagemChave() == null ? null : \"/api/produtos/\" + produto.getId() + \"/imagem?versao=\" + produto.getVersao())")
    ProdutoDTO resposta(Produto produto);
    DadosFiscaisDTO fiscal(DadosFiscais fiscal);
}
