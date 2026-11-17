package br.com.mercadinhos.catalogo.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import br.com.mercadinhos.identidade.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
class ImagemProdutoService {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private final ProdutoRepository produtos;
    private final ProdutosService servico;
    private final ProdutoMapper mapper;
    private final AcessoAtual acesso;
    private final ArmazenamentoImagens armazenamento;

    @Transactional(readOnly = true)
    public byte[] ler(@Positive Long id) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        var produto = produtos.findByIdAndOrganizacaoId(id, acesso.organizacaoId()).orElseThrow(ProdutosService::naoEncontrado);
        if (produto.getImagemChave() == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto sem imagem"); }
        return armazenamento.ler(produto.getImagemChave());
    }

    @Transactional
    public ProdutoDTO enviar(@Positive Long id, @PositiveOrZero long versao, MultipartFile arquivo) {
        acesso.exigir(Modulo.PRODUTOS, Acao.EDITAR);
        var produto = servico.paraAlterar(id, versao);
        byte[] png = validarImagem(arquivo);
        String anterior = produto.getImagemChave();
        String nova = armazenamento.salvar(png);
        aposTransacao(anterior, nova);
        produto.alterarImagem(nova, acesso.usuarioId());
        produtos.flush();
        return mapper.resposta(produto);
    }

    @Transactional
    public ProdutoDTO remover(@Positive Long id, @PositiveOrZero long versao) {
        acesso.exigir(Modulo.PRODUTOS, Acao.EDITAR);
        var produto = servico.paraAlterar(id, versao);
        String anterior = produto.getImagemChave();
        aposTransacao(anterior, null);
        produto.alterarImagem(null, acesso.usuarioId());
        produtos.flush();
        return mapper.resposta(produto);
    }

    private void aposTransacao(String anterior, String nova) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String excluir = status == STATUS_COMMITTED ? anterior : nova;
                try { armazenamento.excluir(excluir); }
                catch (RuntimeException exception) { log.warn("Limpeza de imagem pendente; verificar armazenamento"); }
            }
        });
    }

    private byte[] validarImagem(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) { throw invalida(); }
        if (arquivo.getSize() > MAX_BYTES) { throw new ResponseStatusException(HttpStatus.valueOf(413), "Imagem excede 5 MiB"); }
        try (var original = arquivo.getInputStream(); var input = ImageIO.createImageInputStream(original)) {
            var leitores = ImageIO.getImageReaders(input);
            if (!leitores.hasNext()) { throw invalida(); }
            var leitor = leitores.next();
            try {
                String formato = leitor.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("png") && !formato.equals("jpeg") && !formato.equals("jpg")) { throw invalida(); }
                leitor.setInput(input, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura <= 0 || altura <= 0 || largura > 4096 || altura > 4096 || (long) largura * altura > 4_000_000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem deve ter no máximo 4 milhões de pixels e 4096 pixels por lado");
                }
                var imagem = leitor.read(0);
                var output = new ByteArrayOutputStream();
                if (imagem == null || !ImageIO.write(imagem, "png", output)) { throw invalida(); }
                if (output.size() > MAX_BYTES) { throw new ResponseStatusException(HttpStatus.valueOf(413), "Imagem processada excede 5 MiB"); }
                return output.toByteArray();
            } finally { leitor.dispose(); }
        } catch (IOException | IllegalArgumentException exception) { throw invalida(); }
    }

    private static ResponseStatusException invalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma imagem PNG ou JPEG válida");
    }
}
