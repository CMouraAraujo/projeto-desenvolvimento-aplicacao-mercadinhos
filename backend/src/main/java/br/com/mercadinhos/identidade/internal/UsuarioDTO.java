package br.com.mercadinhos.identidade.internal;

import java.util.List;

record UsuarioDTO(Long id, String nome, String email, String papel, boolean ativo,
                  long versao, List<PermissaoDTO> permissoes) { }
